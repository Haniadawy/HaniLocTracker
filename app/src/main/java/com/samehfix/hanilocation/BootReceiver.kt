package com.samehfix.hanilocation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * SmsReceiver متسجل في AndroidManifest، فهو بيشتغل تلقائيًا بعد إعادة التشغيل
 * طالما التطبيق مش متقفل بالكامل (Force Stop) من إعدادات النظام.
 * الكلاس ده موجود فقط لتسجيل حالة الإقلاع في الـ log لو احتجت تتابع.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "تم إعادة تشغيل الهاتف - مراقبة الرسائل شغالة")
        }
    }
}
