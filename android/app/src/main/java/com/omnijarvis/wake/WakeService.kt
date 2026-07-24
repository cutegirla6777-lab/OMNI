// ⚠️ INCOMPLETE FRAGMENT — package statement, imports, and the class declaration line
// (e.g. "class WakeService : Service() {") were not included in what was pasted.
// Body below is otherwise complete as received.

companion object {
    const val NOTIFICATION_ID = 8888
    const val CHANNEL_ID = "wake_word"
}

private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
private lateinit var wakeEngine: UltimateWakeEngine

inner class WakeBinder : Binder() {
    fun getService(): WakeService = this@WakeService
}

private val binder = WakeBinder()

override fun onCreate() {
    super.onCreate()
    startForeground()
    initWakeEngine()
}

private fun startForeground() {
    val intent = Intent(this, HologramActivity::class.java)
    val pendingIntent = PendingIntent.getActivity(
        this, 0, intent, PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("OMNI Wake Word")
        .setContentText("Say 'Hey Omni' to activate")
        .setSmallIcon(R.drawable.ic_wake_word)
        .setContentIntent(pendingIntent)
        .setOngoing(true)
        .setSilent(true)
        .build()

    startForeground(NOTIFICATION_ID, notification)
}

private fun initWakeEngine() {
    wakeEngine = UltimateWakeEngine(this).apply {
        onWakeWordDetected = { confidence, voiceMatch ->
            scope.launch {
                activateOmni(confidence, voiceMatch)
            }
        }
    }
    wakeEngine.start()
}

private suspend fun activateOmni(confidence: Float, voiceMatch: Float) {
    // Start main OmniService if not running
    if (!OmniService.isRunning) {
        val intent = Intent(this, OmniService::class.java)
        startForegroundService(intent)
    }

    // Show hologram
    val intent = Intent(this, HologramActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
        putExtra("MODE", "LISTENING")
        putExtra("CONFIDENCE", confidence)
        putExtra("VOICE_MATCH", voiceMatch)
    }
    startActivity(intent)
}

override fun onBind(intent: Intent?): IBinder = binder

override fun onDestroy() {
    super.onDestroy()
    wakeEngine.stop()
    scope.cancel()
}
