package com.aicode.feature.agent.domain.tool.semantic

import com.aicode.core.util.FileLogger
import com.aicode.feature.workspace.data.repository.WorkspaceRepository
import com.aicode.feature.workspace.domain.FileAccessProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 语义索引的编排者：懒构建 + 失效重建。
 *
 * - 首次查询触发全量构建；之后每次查询前对比已索引文件快照（mtime/size，含新增/删除），
 *   有变化则重建。索引放内存、进程内复用、不落盘（见 specs/semantic-index/design.md）。
 * - 通过 [FileAccessProvider] 遍历：本地 / SFTP 后端由 DI 统一注入，两模式一个实现。
 * - 索引根用 [WorkspaceRepository.currentPath]（本地为宿主工作区绝对路径，远程为远程路径），
 *   快照与索引都用 fileAccess 的 display 路径（本地即 `~/workspace/...`）作 docId，与 `list`/`readFile` 一致。
 * - 并发安全：构建用 [Mutex] 串行；构建中再查询排队等待，不并行构建。
 * - 防护：二进制 / 超大文件跳过；索引文件数达 [MAX_FILES] 截断并标注。
 */
@Singleton
class SemanticIndexer @Inject constructor(
    private val fileAccess: FileAccessProvider,
    private val workspaceRepository: WorkspaceRepository
) {

    private companion object {
        const val TAG = "SemanticIndexer"
        const val MAX_FILES = 8000
        const val MAX_FILE_BYTES = 1_048_576L // 1 MiB
        // 二进制文件扩展名黑名单（小写，无点）。
        val BINARY_EXT = setOf(
            "png", "jpg", "jpeg", "gif", "webp", "ico", "bmp", "svg", "tiff",
            "pem", "key", "jks", "keystore", "apk", "aab", "dex", "jar", "aar",
            "so", "a", "o", "class", "dll", "exe", "bin", "zip", "gz", "xz", "bz2", "tar", "7z", "rar"
        )
        // 跳过目录（精确名匹配）。
        val SKIP_DIR = setOf(".git", "build", ".gradle", "node_modules", ".idea", ".kotlin")
    }

    private data class DocSnapshot(val mtime: Long, val size: Long)

    /** 查询入口的返回：索引 + 元信息。 */
    data class Readable(
        val index: InvertedIndex,
        val indexedFiles: Int,
        val truncated: Boolean
    )

    private data class Built(
        val index: InvertedIndex,
        val snapshots: Map<String, DocSnapshot>,
        val indexedFiles: Int
    )

    private var current: Built? = null
    private val mutex = Mutex()

    /**
     * 确保索引新鲜。工作区不可达 / 构建失败抛 [SemanticIndexUnavailableException]；
     * 首次调用或检测到变化时串行重建。
     */
    suspend fun ensureIndex(): Readable = mutex.withLock {
        val root = workspaceRepository.currentPath()

        val snapshots = try {
            collectSnapshots(root)
        } catch (e: Exception) {
            current = null
            FileLogger.w(TAG, "收集工作区快照失败: ${e.message}")
            throw SemanticIndexUnavailableException("无法读取工作区: ${e.message}", e)
        }

        val cached = current
        if (cached == null || cached.snapshots != snapshots) {
            val built = try {
                build(root, snapshots)
            } catch (e: Exception) {
                FileLogger.e(TAG, "索引构建失败", e)
                throw SemanticIndexUnavailableException("索引构建失败: ${e.message}", e)
            }
            current = built
            return@withLock Readable(built.index, built.indexedFiles, snapshots.size > MAX_FILES)
        }
        Readable(cached.index, cached.indexedFiles, snapshots.size > MAX_FILES)
    }

    /** 只遍历一次文件树，收集「应索引文件」的快照（不读内容，用于失效检测）。 */
    private suspend fun collectSnapshots(root: String): Map<String, DocSnapshot> {
        val out = LinkedHashMap<String, DocSnapshot>()
        walkCounted(root, out)
        // 完整路径 key。collect 阶段对每个目录独立计数不可靠，改为统一的全局计数。
        return out
    }

    private suspend fun walkCounted(dir: String, acc: MutableMap<String, DocSnapshot>) {
        if (!fileAccess.exists(dir) || !fileAccess.isDirectory(dir)) return
        val children = try {
            fileAccess.listFiles(dir)
        } catch (e: Exception) {
            return
        }
        for (child in children) {
            if (acc.size >= MAX_FILES) return
            val path = "$dir/${child.name}"
            if (child.isDirectory) {
                if (child.name in SKIP_DIR) continue
                walkCounted(path, acc)
            } else {
                if (!shouldIndexFile(child.name, child.size)) continue
                acc[path] = DocSnapshot(child.lastModified, child.size)
            }
        }
    }

    private fun shouldIndexFile(name: String, size: Long): Boolean {
        if (size > MAX_FILE_BYTES) return false
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext !in BINARY_EXT
    }

    /** 第二次遍历，读文本内容（词元化）入倒排。 */
    private suspend fun build(root: String, snapshots: Map<String, DocSnapshot>): Built {
        val index = InvertedIndex()
        var indexed = 0
        val seen = HashSet<String>()

        suspend fun walk(dir: String) {
            if (indexed >= MAX_FILES) return
            if (!fileAccess.exists(dir) || !fileAccess.isDirectory(dir)) return
            val children = try {
                fileAccess.listFiles(dir)
            } catch (e: Exception) {
                return
            }
            for (child in children) {
                if (!seen.add(child.name)) continue
                if (indexed >= MAX_FILES) return
                val path = "$dir/${child.name}"
                if (child.isDirectory) {
                    if (child.name in SKIP_DIR) continue
                    walk(path)
                } else {
                    if (!shouldIndexFile(child.name, child.size)) continue
                    // 只处理仍在「快照集合」中的文件：快照比较后新增的文件也在其中。
                    if (snapshots[path] == null) continue
                    val content = try {
                        fileAccess.readFile(path)
                    } catch (e: Exception) {
                        continue
                    }
                    val displayPath = fileAccess.toDisplayPath(path)
                    index.addDocument(
                        docId = displayPath,
                        contentTerms = SemanticTokenizer.tokenize(content),
                        pathTerms = SemanticTokenizer.tokenize(displayPath)
                    )
                    indexed++
                }
            }
        }

        walk(root)
        FileLogger.i(TAG, "索引构建完成 files=$indexed root=$root")
        return Built(index, snapshots, indexed)
    }
}

/** 工作区不可达或索引构建失败时抛出，由调用方转为工具错误码 [INDEX_UNAVAILABLE]。 */
class SemanticIndexUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)