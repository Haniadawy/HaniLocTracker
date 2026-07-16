package com.samehfix.hanilocation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Handler
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

        // الرقم الثابت اللي هيوصله رد الموقع دايمًا
        private const val TARGET_NUMBER = "+201099422975"

        private const val CHANNEL_ID = "hani_loc_channel"
        private const val NOTIF_ID = 101
        private const val TAG = "LocationService"

        // أقصى وقت ننتظره لتحديد الموقع قبل ما نعتبره عالق ونبعت تذكير بدل ما التطبيق "يهنج"
        private const val TIMEOUT_MS = 20_000L
    }

    private lateinit var notificationManager: NotificationManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isFinished = false
    private var timeoutRunnable: Runnable? = null

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
        isFinished = false
        val senderNumber = intent?.getStringExtra(EXTRA_PHONE_NUMBER) ?: "غير معروف"

        startForeground(NOTIF_ID, buildNotification("تم استلام رسالة من $senderNumber\nجاري تحديد الموقع..."))

        timeoutRunnable = Runnable {
            if (!isFinished) {
                Log.w(TAG, "انتهت المهلة قبل تحديد الموقع، جاري إرسال تذكير")
                finishWithLocationOff()
            }
        }
        mainHandler.postDelayed(timeoutRunnable!!, TIMEOUT_MS)

        if (!isLocationServiceEnabled()) {
            Log.w(TAG, "خدمة الموقع مقفولة في إعدادات الهاتف")
            finishWithLocationOff()
            return START_NOT_STICKY
        }

        fetchLocationAndReply(senderNumber)
        return START_NOT_STICKY
    }

    private fun isLocationServiceEnabled(): Boolean {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        val gpsEnabled = try {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        } catch (e: Exception) {
            false
        }
        val networkEnabled = try {
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) {
            false
        }
        return gpsEnabled || networkEnabled
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
            finishWithLocationOff()
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
                    handleLocationFound(location)
                } else {
                    Log.w(TAG, "تعذر تحديد موقع حالي، جاري تجربة آخر موقع معروف")
                    fallbackToLastKnownLocation()
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "فشل تحديد الموقع الحالي: ${e.message}، جاري تجربة آخر موقع معروف")
                fallbackToLastKnownLocation()
            }
    }

    private fun fallbackToLastKnownLocation() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            finishWithLocationOff()
            return
        }

        updateNotification("جاري تجربة آخر موقع معروف...")

        val fusedClient = LocationServices.getFusedLocationProviderClient(this)
        fusedClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    handleLocationFound(location)
                } else {
                    Log.e(TAG, "لا يوجد أي موقع معروف على الإطلاق")
                    finishWithLocationOff()
                }
            }
            .addOnFailureListener {
                Log.e(TAG, "فشل جلب آخر موقع معروف: ${it.message}")
                finishWithLocationOff()
            }
    }

    private fun handleLocationFound(location: Location) {
        if (isFinished) return
        updateNotification("تم تحديد الموقع\nجاري إرسال الرسالة إلى $TARGET_NUMBER ...")
        sendLocationSms(location)
        updateNotification("تم إرسال الموقع بنجاح إلى $TARGET_NUMBER")
        finish()
    }

    private fun finishWithLocationOff() {
        if (isFinished) return
        updateNotification("خدمة الموقع غير مفعّلة، جاري إرسال تذكير إلى $TARGET_NUMBER")
        sendReminderSms()
        updateNotification("تم إرسال رسالة تذكير بتشغيل الموقع إلى $TARGET_NUMBER")
        finish()
    }

    private fun finish() {
        isFinished = true
        timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
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

    private fun sendReminderSms() {
        try {
            val smsManager = getSmsManager()
            smsManager.sendTextMessage(TARGET_NUMBER, null, "برجاء تشغيل خدمة الموقع (GPS) في هاتف السيارة", null, null)
            Log.d(TAG, "تم إرسال رسالة تذكير بتشغيل الموقع إلى $TARGET_NUMBER")
        } catch (e: Exception) {
            Log.e(TAG, "فشل إرسال رسالة التذكير: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
