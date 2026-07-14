package com.samehfix.hanilocation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * يستقبل كل رسائل SMS الواردة.
 * لو الرسالة فيها كلمة المفتاح TRIGGER_WORD (بأي حالة أحرف)،
 * يبدأ LocationService عشان يبعت الموقع لنفس الرقم اللي بعت الرسالة.
 */
class SmsReceiver : BroadcastReceiver() {

    companion object {
        const val TRIGGER_WORD = "haniloc"
        private const val TAG = "SmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        // ممكن الرسالة توصل في أكتر من جزء (multipart)، فبنجمعها كلها
        val fullBody = messages.joinToString(separator = "") { it.messageBody ?: "" }
        val sender = messages[0].originatingAddress ?: return

        Log.d(TAG, "رسالة واردة من: $sender")

        if (fullBody.lowercase().contains(TRIGGER_WORD)) {
            Log.d(TAG, "تم رصد كلمة التتبع، جاري تجهيز الموقع للرد على $sender")

            val serviceIntent = Intent(context, LocationService::class.java).apply {
                putExtra(LocationService.EXTRA_PHONE_NUMBER, sender)
            }
            ContextCompat.startForegroundService(context, serviceIntent)
        }
    }
}
