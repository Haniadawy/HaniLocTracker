package com.samehfix.hanilocation

import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.BatteryManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.TimeUnit

class LowBatteryWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    companion object {
        const val PREFS_NAME = "hani_loc_prefs"
        const val KEY_ALERT_SENT = "low_battery_alert_sent"
        private const val TARGET_NUMBER = "+201099422975"
        private const val LOW_BATTERY_THRESHOLD = 20
        private const val TAG = "LowBatteryWorker"
    }

    override fun doWork(): Result {
        val context = applicationContext
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        Log.d(TAG, "فحص دوري: نسبة البطارية الحالية $batteryLevel%")

        if (batteryLevel < 0) {
            return Result.success()
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val alertAlreadySent = prefs.getBoolean(KEY_ALERT_SENT, false)

        if (batteryLevel > LOW_BATTERY_THRESHOLD) {
            if (alertAlreadySent && batteryLevel > LOW_BATTERY_THRESHOLD + 5) {
                prefs.edit().putBoolean(KEY_ALERT_SENT, false).apply()
            }
            return Result.success()
        }

        if (alertAlreadySent) {
            return Result.success()
        }

        Log.w(TAG, "البطارية منخفضة ($batteryLevel%)، جاري تحديد الموقع لإرسال التحذير")

        val location = getLocationBlocking(context)
        sendLowBatteryAlert(context, batteryLevel, location)

        prefs.edit().putBoolean(KEY_ALERT_SENT, true).apply()
        return Result.success()
    }

    private fun getLocationBlocking(context: Context): Location? {
        if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }

        return try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMaxUpdateAgeMillis(120_000)
                .build()

            val current = Tasks.await(fusedClient.getCurrentLocation(request, null), 20, TimeUnit.SECONDS)
            current ?: Tasks.await(fusedClient.lastLocation, 10, TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.e(TAG, "تعذر تحديد الموقع لتحذير البطارية: ${e.message}")
            null
        }
    }

    private fun sendLowBatteryAlert(context: Context, batteryLevel: Int, location: Location?) {
        val message = if (location != null) {
            "تحذير: البطارية منخفضة ($batteryLevel%)\n${location.latitude},${location.longitude}"
        } else {
            "تحذير: البطارية منخفضة ($batteryLevel%) - تعذر تحديد الموقع الحالي"
        }

        try {
            val smsManager: SmsManager =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }

            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(TARGET_NUMBER, null, parts, null, null)
            Log.d(TAG, "تم إرسال تحذير البطارية المنخفضة إلى $TARGET_NUMBER")
        } catch (e: Exception) {
            Log.e(TAG, "فشل إرسال تحذير البطارية: ${e.message}")
        }
    }
}
