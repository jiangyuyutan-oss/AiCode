package com.aicode.feature.git.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GitMergeConflictsTest {

    @Test
    fun noConflict_returnsEmpty() {
        assertEquals(emptyList<String>(), mergeConflictsFromPorcelain(" M a.txt\n?? b.txt\n"))
    }

    @Test
    fun uuFiles_captured() {
        val conflicts = mergeConflictsFromPorcelain("UU main.kt\n M readme.md\n")
        assertEquals(listOf("main.kt"), conflicts)
    }

    @Test
    fun allUnmergedCodes_captured() {
        val raw = "UU a.kt\nAA b.kt\nDD c.kt\nUD d.kt\nAU e.kt\nUA f.kt\nDU g.kt\n"
        val conflicts = mergeConflictsFromPorcelain(raw)
        assertEquals(listOf("a.kt", "b.kt", "c.kt", "d.kt", "e.kt", "f.kt", "g.kt"), conflicts)
    }

    @Test
    fun stagedAndUntracked_notConflicts() {
        val raw = "M  staged.txt\n M unstaged.txt\n?? untracked.txt\n"
        assertTrue(mergeConflictsFromPorcelain(raw).isEmpty())
    }

    @Test
    fun renameConflict_usesNewPath() {
        val conflicts = mergeConflictsFromPorcelain("UU old.txt -> new.txt\n")
        assertEquals(listOf("new.txt"), conflicts)
    }

    @Test
    fun quotedPath_unquoted() {
        val conflicts = mergeConflictsFromPorcelain("UU \"my file.txt\"\n")
        assertEquals(listOf("my file.txt"), conflicts)
    }
}