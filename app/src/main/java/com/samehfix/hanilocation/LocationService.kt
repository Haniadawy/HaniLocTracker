package com.samehfix.hanilocation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
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

    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "تتبع الموقع",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val phoneNumber = intent?.getStringExtra(EXTRA_PHONE_NUMBER)

        if (phoneNumber.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIF_ID, buildNotification("تم استلام رسالة من $phoneNumber\nجاري تحديد الموقع..."))
        fetchLocationAndReply(phoneNumber)
        return START_NOT_STICKY
    }

    private fun buildNotification(text: String): android.app.Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("HaniLoc")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        notificationManager.notify(NOTIF_ID, buildNotification(text))
    }

    private fun fetchLocationAndReply(phoneNumber: String) {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "لا يوجد إذن وصول للموقع")
            updateNotification("لا يوجد إذن وصول للموقع، لم يتم إرسال أي رد")
            stopSelf()
            return
        }

        val fusedClient = LocationServices.getFusedLocationProviderClient(this)

        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMaxUpdateAgeMillis(60_000)
            .build()

        // المحاولة الأولى: تحديد موقع حالي ودقيق
        fusedClient.getCurrentLocation(request, null)
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    handleLocationFound(phoneNumber, location)
                } else {
                    Log.w(TAG, "تعذر تحديد موقع حالي، جاري تجربة آخر موقع معروف")
                    fallbackToLastKnownLocation(phoneNumber)
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "فشل تحديد الموقع الحالي: ${e.message}، جاري تجربة آخر موقع معروف")
                fallbackToLastKnownLocation(phoneNumber)
            }
    }

    private fun fallbackToLastKnownLocation(phoneNumber: String) {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            updateNotification("لا يوجد إذن وصول للموقع، لم يتم إرسال أي رد")
            stopSelf()
            return
        }

        val fusedClient = LocationServices.getFusedLocationProviderClient(this)
        fusedClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    handleLocationFound(phoneNumber, location)
                } else {
                    Log.e(TAG, "لا يوجد أي موقع معروف على الإطلاق، لن يتم إرسال رسالة")
                    updateNotification("تعذر تحديد الموقع نهائيًا، لم يتم إرسال أي رد إلى $phoneNumber")
                    stopSelf()
                }
            }
            .addOnFailureListener {
                Log.e(TAG, "فشل جلب آخر موقع معروف: ${it.message}")
                updateNotification("تعذر تحديد الموقع نهائيًا، لم يتم إرسال أي رد إلى $phoneNumber")
                stopSelf()
            }
    }

    private fun handleLocationFound(phoneNumber: String, location: Location) {
        updateNotification("تم تحديد الموقع\nجاري إرسال الرسالة إلى $phoneNumber ...")
        sendLocationSms(phoneNumber, location)
        updateNotification("تم إرسال الموقع بنجاح إلى $phoneNumber")
        stopSelf()
    }

    private fun sendLocationSms(phoneNumber: String, location: Location) {
        val message = "${location.latitude},${location.longitude}"
        try {
            val smsManager: SmsManager =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }

            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage($phoneNumber, null, parts, null, null)

            Log.d(TAG, "تم إرسال الموقع إلى $phoneNumber")
        } catch (e: Exception) {
            Log.e(TAG, "فشل إرسال الرسالة: ${e.message}")
            updateNotification("فشل إرسال الرسالة إلى $phoneNumber: ${e.message}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
