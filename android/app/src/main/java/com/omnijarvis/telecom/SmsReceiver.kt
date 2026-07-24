// ⚠️ INCOMPLETE FRAGMENT — package statement, imports, and the class declaration line
// (e.g. "class SmsReceiver : BroadcastReceiver() {") were not included in what was pasted.
// Body below is otherwise complete as received.

private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

    val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return

    for (message in messages) {
        val sender = message.displayOriginatingAddress ?: continue
        val body = message.displayMessageBody ?: continue
        val timestamp = message.timestampMillis

        scope.launch {
            processSMS(context, sender, body, timestamp)
        }
    }
}

private suspend fun processSMS(context: Context, sender: String, body: String, timestamp: Long) {
    // Check if Vodafone/USSD related
    if (isVodafoneMessage(sender)) {
        com.omnijarvis.core.OmniService.instance?.let { service ->
            // Parse balance, data, offers
            val parsed = parseVodafoneSMS(body)
            // Store and notify
        }
        return
    }

    // Regular message - send to consciousness
    com.omnijarvis.core.OmniService.instance?.onIncomingMessage(
        app = "sms",
        sender = sender,
        message = body
    )
}

private fun isVodafoneMessage(sender: String): Boolean {
    return sender in listOf("Vi", "VODAFONE", "IDEA", "121", "199", "5555")
}

private fun parseVodafoneSMS(body: String): VodafoneInfo {
    // Parse balance, validity, data usage
    return VodafoneInfo(
        balance = extractBalance(body),
        dataUsage = extractDataUsage(body),
        validity = extractValidity(body)
    )
}

private fun extractBalance(body: String): String? {
    val regex = Regex("""Rs\.?\s*(\d+\.?\d*)""")
    return regex.find(body)?.groupValues?.get(1)
}

private fun extractDataUsage(body: String): String? {
    val regex = Regex("""(\d+\.?\d*)\s*(MB|GB)""")
    return regex.find(body)?.value
}

private fun extractValidity(body: String): String? {
    val regex = Regex("""valid (?:till|until|up to) ([\d-]+)""")
    return regex.find(body)?.groupValues?.get(1)
}

data class VodafoneInfo(
    val balance: String?,
    val dataUsage: String?,
    val validity: String?
)
