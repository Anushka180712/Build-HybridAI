package app.chaukas.guard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

/**
 * Direct OS-level SMS broadcast receiver.
 * When the app is closed, Android wakes up this receiver immediately upon SMS arrival,
 * ensuring incoming scam SMS are intercepted and warned about with zero latency even if
 * the app is not running.
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Guard.isOn(context) || !Guard.watches(context, "sms")) return
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = runCatching { Telephony.Sms.Intents.getMessagesFromIntent(intent) }.getOrNull()
        if (messages.isNullOrEmpty()) return

        val from = messages[0].originatingAddress.orEmpty().trim()
        val body = messages.joinToString("") { it.messageBody.orEmpty() }.trim()
        if (body.isBlank()) return

        val alert = MessageGuard.assess("sms", from, body)
        Guard.record(context, "Messages", "sms", alert)
        if (alert != null) {
            Log.i("ChaukasSmsReceiver", "Scam SMS detected from $from: score=${alert.score}")
            val isUnsaved = MessageGuard.isUnsavedSender(from) { Guard.isContactSaved(context, it) }
            val id = (from + body).hashCode()
            Guard.warn(context, alert, id, isKnownContact = !isUnsaved)
            Guard.onAlertCallback?.invoke(alert, "Messages", !isUnsaved)
            if (alert.score >= Guard.SCAM_AT && isUnsaved) {
                Guard.onScamDetected(context, "Messages", "sms", alert)
            }
        }
    }
}
