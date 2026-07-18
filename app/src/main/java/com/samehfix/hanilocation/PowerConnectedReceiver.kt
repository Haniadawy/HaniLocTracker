package com.samehfix.hanilocation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class PowerConnectedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_POWER_CONNECTED) {
            val prefs = context.getSharedPreferences(LowBatteryWorker.PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(LowBatteryWorker.KEY_ALERT_SENT, false).apply()
            Log.d("PowerConnectedReceiver", "تم توصيل الشاحن، تصفير علامة تحذير البطارية")
        }
    }
}
