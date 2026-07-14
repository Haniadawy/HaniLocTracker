package com.samehfix.hanilocation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class LocationService : Service() {

    companion object {
        const val EXTRA_PHONE_NUMBER = "phone_number"
        private const val CHANNEL_ID = "hani_loc_channel"
        private const val NOTIF_ID = 101
        private const val TAG = "LocationService"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundWithNotification()

        val phoneNumber = intent?.getStringExtra(EXTRA_PHONE_NUMBER)
        if (phoneNumber.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        fetchLocationAndReply(phoneNumber)
        return START_NOT_STICKY
    }

    private fun startForegroundWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "تتبع الموقع",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("HaniLoc")
            .setContentText("جاري تحديد الموقع وإرساله...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIF_ID, notification)
    }

    private fun fetchLocationAndReply(phoneNumber: String) {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "لا يوجد إذن وصول للموقع")
            stopSelf()
            return
        }

        val fusedClient = LocationServices.getFusedLocationProviderClient(this)

        // بنطلب أحدث موقع ممكن (بديل عن الموقع الأخير المخزن اللي ممكن يكون قديم)
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMaxUpdateAgeMillis(60_000) // اقبل موقع مخزن لو عمره أقل من دقيقة
            .build()

        fusedClient.getCurrentLocation(request, null)
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    sendLocationSms(phoneNumber, location)
                } else {
                    Log.e(TAG, "تعذر الحصول على الموقع")
                }
                stopSelf()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "فشل جلب الموقع: ${e.message}")
                stopSelf()
            }
    }

    private fun sendLocationSms(phoneNumber: String, location: Location) {
        val mapsLink = "https://maps.google.com/?q=${location.latitude},${location.longitude}"
        val message = "موقع السيارة الحالي:\n$mapsLink"

        try {
            val smsManager: SmsManager =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }

            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)

            Log.d(TAG, "تم إرسال الموقع إلى $phoneNumber")
        } catch (e: Exception) {
            Log.e(TAG, "فشل إرسال الرسالة: ${e.message}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
