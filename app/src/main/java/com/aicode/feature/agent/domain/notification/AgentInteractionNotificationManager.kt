package com.aicode.feature.agent.domain.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.aicode.R
import com.aicode.core.util.FileLogger
import com.aicode.feature.agent.domain.permission.PermissionChoice
import com.aicode.feature.agent.domain.tool.PendingToolPermission
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 交互请求的系统通知：App 在后台时 AI 等待工具权限/计划批准，发通知并带快捷操作按钮，
 * 点击经 [NotificationActionReceiver] 路由回注册的 resolver 直接解决请求。
 * 通知 id：权限 101、计划 102；复用 `agent_complete` 通道（IMPORTANCE_HIGH）。
 */
@Singleton
class AgentInteractionNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** 当前待决请求的 resolver 回调，由 ViewModel 注册；App 内已解决或进程重启后为 null。 */
    private var permissionResolver: ((PermissionChoice) -> Unit)? = null
    private var planApprover: (() -> Unit)? = null

    fun notifyPermission(request: PendingToolPermission, onChoice: (PermissionChoice) -> Unit) {
        permissionResolver = onChoice
        val notification = NotificationCompat.Builder(context, CHANNEL_AGENT_INTERACTION)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle(context.getString(R.string.notification_permission_title))
            .setContentText(request.title.ifBlank { request.toolName })
            .setStyle(NotificationCompat.BigTextStyle().bigText(request.title.ifBlank { request.toolName }))
            .setContentIntent(openAppIntent())
            .addAction(0, context.getString(R.string.notification_permission_approve), permissionActionIntent(ACTION_PERMISSION_APPROVE, 1))
            .addAction(0, context.getString(R.string.notification_permission_deny), permissionActionIntent(ACTION_PERMISSION_DENY, 2))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_PERMISSION, notification)
        }.onFailure { FileLogger.e(TAG, "发送权限请求通知失败", it) }
    }

    /** @param contentText 批准理由（AI 的切换说明），作为通知正文展示。 */
    fun notifyPlan(contentText: String, onApprove: () -> Unit) {
        planApprover = onApprove
        val notification = NotificationCompat.Builder(context, CHANNEL_AGENT_INTERACTION)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle(context.getString(R.string.notification_plan_title))
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setContentIntent(openAppIntent())
            .addAction(0, context.getString(R.string.notification_plan_approve), planActionIntent())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_PLAN, notification)
        }.onFailure { FileLogger.e(TAG, "发送计划批准通知失败", it) }
    }

    fun cancelPermission() {
        permissionResolver = null
        runCatching {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_PERMISSION)
        }.onFailure { FileLogger.e(TAG, "取消权限请求通知失败", it) }
    }

    fun cancelPlan() {
        planApprover = null
        runCatching {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_PLAN)
        }.onFailure { FileLogger.e(TAG, "取消计划批准通知失败", it) }
    }

    /** 清空两类 resolver 回调（App 退前台或通知随面板处理时调用）。 */
    fun clearResolvers() {
        permissionResolver = null
        planApprover = null
    }

    /**
     * 通知快捷操作路由：resolver 回调有效则直接解决请求并取消通知，返回 true；
     * 回调已失效（App 内已解决/进程重启）时仅取消通知并返回 false。
     */
    fun handlePermissionAction(approve: Boolean): Boolean {
        val resolver = permissionResolver
        val choice = if (approve) PermissionChoice.ONCE else PermissionChoice.REJECT
        return if (resolver != null) {
            resolver(choice)
            cancelPermission()
            true
        } else {
            FileLogger.w(TAG, "权限通知操作被忽略：resolver 已失效")
            cancelPermission()
            false
        }
    }

    /** 计划批准通知操作路由，语义同 [handlePermissionAction]。 */
    fun handlePlanApprove(): Boolean {
        val approver = planApprover
        return if (approver != null) {
            approver()
            cancelPlan()
            true
        } else {
            FileLogger.w(TAG, "计划通知操作被忽略：approver 已失效")
            cancelPlan()
            false
        }
    }

    /** 点击通知打开 App：launcher intent 按包名解析，不依赖具体 Activity 类引用。 */
    private fun openAppIntent(): PendingIntent {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setPackage(context.packageName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun permissionActionIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, NotificationActionReceiver::class.java).apply {
                this.action = action
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun planActionIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        3,
        Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_PLAN_APPROVE
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    companion object {
        private const val TAG = "AgentInteractionNotification"

        /** 与 AIEditorApp 创建的 `agent_complete` 通道一致，避免为交互通知单独建渠道。 */
        private const val CHANNEL_AGENT_INTERACTION = "agent_complete"

        const val NOTIFICATION_ID_PERMISSION = 101
        const val NOTIFICATION_ID_PLAN = 102

        const val ACTION_PERMISSION_APPROVE = "com.aicode.notification.PERMISSION_APPROVE"
        const val ACTION_PERMISSION_DENY = "com.aicode.notification.PERMISSION_DENY"
        const val ACTION_PLAN_APPROVE = "com.aicode.notification.PLAN_APPROVE"
    }
}
