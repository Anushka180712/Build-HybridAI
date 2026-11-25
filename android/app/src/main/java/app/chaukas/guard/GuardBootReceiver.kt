package app.chaukas.guard

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.util.Log

/**
 * Ensures Chaukas Scam Shield stays active across phone reboots, app updates,
 * and after being swiped from recent apps.
 */
class GuardBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i("GuardBootReceiver", "received event: $action")
        if (!Guard.isOn(context)) return

        // 1. Request NotificationListenerService rebind from Android system
        runCatching {
            NotificationListenerService.requestRebind(ComponentName(context, GuardService::class.java))
        }

        // 2. Start GuardService as foreground service
        Guard.start(context)
    }
}
