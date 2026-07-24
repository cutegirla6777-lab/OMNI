// ⚠️ INCOMPLETE FRAGMENT — package statement, imports, and the class declaration line
// (e.g. "class PhoneStateReceiver : BroadcastReceiver() {") were not included in what was
// pasted. Body below is otherwise complete as received.

private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
private var lastState = TelephonyManager.CALL_STATE_IDLE
private var callStartTime: Long = 0

override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

    val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
    val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

    when (state) {
        TelephonyManager.EXTRA_STATE_RINGING -> {
            onCallRinging(number)
        }
        TelephonyManager.EXTRA_STATE_OFFHOOK -> {
            onCallAnswered(number)
        }
        TelephonyManager.EXTRA_STATE_IDLE -> {
            onCallEnded(number)
        }
    }
}

private fun onCallRinging(number: String?) {
    scope.launch {
        // Check if should auto-answer or block
        val shouldBlock = checkSpam(number)
        if (shouldBlock) {
            disconnectCall()
            return@launch
        }

        // Announce caller
        val contactName = getContactName(number)
        com.omnijarvis.core.OmniService.instance?.tts?.speak(
            "Incoming call from ${contactName ?: number}",
            com.omnijarvis.perception.Emotion.NEUTRAL
        )
    }
}

private fun onCallAnswered(number: String?) {
    callStartTime = System.currentTimeMillis()
    scope.launch {
        // Start recording if configured
        startCallRecording(number)
    }
}

private fun onCallEnded(number: String?) {
    val duration = System.currentTimeMillis() - callStartTime
    scope.launch {
        stopCallRecording()
        // Save call log with transcription
    }
}

private fun checkSpam(number: String?): Boolean {
    // Check against spam database
    return false
}

private fun getContactName(number: String?): String? {
    // Query contacts
    return null
}

private fun disconnectCall() {
    try {
        val telephony = Class.forName("android.telephony.TelephonyManager")
        val method = telephony.getDeclaredMethod("getITelephony")
        method.isAccessible = true
        val telephonyService = method.invoke(null)

        val endCall = telephonyService.javaClass.getDeclaredMethod("endCall")
        endCall.invoke(telephonyService)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun startCallRecording(number: String?) {
    // Start recording
}

private fun stopCallRecording() {
    // Stop and save
}
