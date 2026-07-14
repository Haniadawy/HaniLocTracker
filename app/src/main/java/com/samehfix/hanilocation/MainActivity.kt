package com.samehfix.hanilocation

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val requiredPermissions: Array<String>
        get() {
            val perms = mutableListOf(
                android.Manifest.permission.RECEIVE_SMS,
                android.Manifest.permission.SEND_SMS,
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                perms.add(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                perms.add(android.Manifest.permission.POST_NOTIFICATIONS)
            }
            return perms.toTypedArray()
        }

    private val permissionLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val allGranted = results.values.all { it }
            updateStatus(allGranted)
            if (allGranted) {
                requestIgnoreBatteryOptimizations()
            } else {
                Toast.makeText(
                    this,
                    "لازم توافق على كل الأذونات عشان التطبيق يشتغل صح",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<android.widget.Button>(R.id.btnGrantPermissions).setOnClickListener {
            checkAndRequestPermissions()
        }

        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            updateStatus(true)
            requestIgnoreBatteryOptimizations()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun requestIgnoreBatteryOptimizations() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                // بعض الأجهزة (خصوصًا الصينية زي شاومي وهواوي) محتاجة
                // إعدادات إضافية يدوية من شاشة "إدارة البطارية" الخاصة بيهم
            }
        }
    }

    private fun updateStatus(granted: Boolean) {
        findViewById<TextView>(R.id.tvStatus).text = if (granted) {
            "✅ التطبيق جاهز ويراقب الرسائل في الخلفية\nابعت رسالة فيها كلمة \"haniloc\" لأي رقم في التليفون ده وهيرد عليك بالموقع"
        } else {
            "⚠️ لازم تمنح كل الأذونات المطلوبة عشان التطبيق يشتغل"
        }
    }
}
