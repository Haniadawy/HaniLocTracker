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

        // الرقم الثابت اللي هيوصله رد الموقع دايمًا
        private const val TARGET_NUMBER = "+201099422975"

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
        val senderNumber = intent?.getStringExtra(EXTRA_PHONE_NUMBER) ?: "غير معروف"

        startForeground(NOTIF_ID, buildNotification("تم استلام رسالة من $senderNumber\nجاري تحديد الموقع..."))
        fetchLocationAndReply(senderNumber)
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

    private fun fetchLocationAndReply(senderNumber: String) {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "لا يوجد إذن وصول للموقع")
            updateNotification("لا يوجد إذن وصول للموقع، لم يتم إرسال أي رد")
            sendUnavailableSms()
            stopSelf()
            return
        }

        val fusedClient = LocationServices.getFusedLocationProviderClient(this)

        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMaxUpdateAgeMillis(60_000)
            .build()

        fusedClient.getCurrentLocation(request, null)
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    handleLocationFound(senderNumber, location)
                } else {
                    Log.w(TAG, "تعذر تحديد موقع حالي، جاري تجربة آخر موقع معروف")
                    fallbackToLastKnownLocation(senderNumber)
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "فشل تحديد الموقع الحالي: ${e.message}، جاري تجربة آخر موقع معروف")
                fallbackToLastKnownLocation(senderNumber)
            }
    }

    private fun fallbackToLastKnownLocation(senderNumber: String) {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            updateNotification("لا يوجد إذن وصول للموقع، لم يتم إرسال أي رد")
            sendUnavailableSms()
            stopSelf()
            return
        }

        updateNotification("جاري تجربة آخر موقع معروف...")

        val fusedClient = LocationServices.getFusedLocationProviderClient(this)
        fusedClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    handleLocationFound(senderNumber, location)
                } else {
                    Log.e(TAG, "لا يوجد أي موقع معروف على الإطلاق")
                    updateNotification("الموقع غير متاح، جاري إرسال رسالة بذلك إلى $TARGET_NUMBER")
                    sendUnavailableSms()
                    updateNotification("تم إرسال رسالة \"الموقع غير متاح\" إلى $TARGET_NUMBER")
                    stopSelf()
                }
            }
            .addOnFailureListener {
                Log.e(TAG, "فشل جلب آخر موقع معروف: ${it.message}")
                updateNotification("الموقع غير متاح، جاري إرسال رسالة بذلك إلى $TARGET_NUMBER")
                sendUnavailableSms()
                updateNotification("تم إرسال رسالة \"الموقع غير متاح\" إلى $TARGET_NUMBER")
                stopSelf()
            }
    }

    private fun handleLocationFound(senderNumber: String, location: Location) {
        updateNotification("تم تحديد الموقع\nجاري إرسال الرسالة إلى $TARGET_NUMBER ...")
        sendLocationSms(location)
        updateNotification("تم إرسال الموقع بنجاح إلى $TARGET_NUMBER")
        stopSelf()
    }

    private fun getSmsManager(): SmsManager {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
    }

    private fun sendLocationSms(location: Location) {
        val message = "${location.latitude},${location.longitude}"

        try {
            val smsManager = getSmsManager()
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(TARGET_NUMBER, null, parts, null, null)
            Log.d(TAG, "تم إرسال الموقع إلى $TARGET_NUMBER")
        } catch (e: Exception) {
            Log.e(TAG, "فشل إرسال الرسالة: ${e.message}")
            updateNotification("فشل إرسال الرسالة إلى $TARGET_NUMBER: ${e.message}")
        }
    }

    private fun sendUnavailableSms() {
        try {
            val smsManager = getSmsManager()
            smsManager.sendTextMessage(TARGET_NUMBER, null, "الموقع غير متاح", null, null)
            Log.d(TAG, "تم إرسال رسالة (الموقع غير متاح) إلى $TARGET_NUMBER")
        } catch (e: Exception) {
            Log.e(TAG, "فشل إرسال رسالة (الموقع غير متاح): ${e.message}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
