package com.aicode.feature.agent.domain.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aicode.core.util.FileLogger
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 通知快捷操作入口：把通知按钮点击路由回 [AgentInteractionNotificationManager]，
 * 由其分发到注册的 resolver（ViewModel 解决权限请求/批准计划）；resolver 失效时仅取消通知。
 */
@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var interactionNotificationManager: AgentInteractionNotificationManager

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AgentInteractionNotificationManager.ACTION_PERMISSION_APPROVE ->
                interactionNotificationManager.handlePermissionAction(approve = true)
            AgentInteractionNotificationManager.ACTION_PERMISSION_DENY ->
                interactionNotificationManager.handlePermissionAction(approve = false)
            AgentInteractionNotificationManager.ACTION_PLAN_APPROVE ->
                interactionNotificationManager.handlePlanApprove()
            else -> FileLogger.w(TAG, "未知通知操作: ${intent.action}")
        }
    }

    companion object {
        private const val TAG = "NotificationActionReceiver"
    }
}
