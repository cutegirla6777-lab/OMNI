// ⚠️ INCOMPLETE FRAGMENT — package statement, imports, and the class declaration line
// (e.g. "class OmniService : Service() {") were not included in what was pasted.
// Body below is otherwise complete as received.

companion object {
    const val NOTIFICATION_ID = 9999
    const val CHANNEL_ID = "omni_core"

    var isRunning = false
        private set
}

private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

lateinit var wakeEngine: UltimateWakeEngine
    private set
lateinit var consciousness: ConsciousnessEngine
    private set
lateinit var spatialAwareness: SpatialAwareness
    private set
lateinit var tts: UltraTTS
    private set

private var hologramView: android.view.View? = null
private var windowManager: WindowManager? = null

inner class OmniBinder : Binder() {
    fun getService(): OmniService = this@OmniService
}

private val binder = OmniBinder()

override fun onCreate() {
    super.onCreate()
    isRunning = true

    initEngines()
    startForeground()
    startWakeEngine()
    startConsciousness()
    startSpatialAwareness()
    showHologramOverlay()
}

private fun initEngines() {
    wakeEngine = UltimateWakeEngine(this)
    consciousness = ConsciousnessEngine(this)
    spatialAwareness = SpatialAwareness(this)
    tts = UltraTTS(this)
}

private fun startForeground() {
    val notification = buildNotification(
        title = "OMNI-JARVIS Active",
        text = "Listening and monitoring..."
    )

    startForeground(NOTIFICATION_ID, notification)
}

private fun buildNotification(title: String, text: String): Notification {
    val intent = Intent(this, HologramActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    val pendingIntent = PendingIntent.getActivity(
        this, 0, intent, PendingIntent.FLAG_IMMUTABLE
    )

    return NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle(title)
        .setContentText(text)
        .setSmallIcon(android.R.drawable.ic_menu_mylocation)
        .setContentIntent(pendingIntent)
        .setOngoing(true)
        .setSilent(true)
        .addAction(android.R.drawable.ic_btn_speak_now, "Listen", createActionIntent("LISTEN"))
        .addAction(android.R.drawable.ic_menu_preferences, "Settings", createActionIntent("SETTINGS"))
        .build()
}

private fun createActionIntent(action: String): PendingIntent {
    val intent = Intent(this, OmniService::class.java).apply {
        this.action = action
    }
    return PendingIntent.getService(
        this, action.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE
    )
}

private fun startWakeEngine() {
    wakeEngine.apply {
        onWakeWordDetected = { confidence, voiceMatch ->
            serviceScope.launch {
                handleWakeWord(confidence, voiceMatch)
            }
        }
        onProximityWake = {
            serviceScope.launch {
                handleProximityWake()
            }
        }
        onGestureWake = {
            serviceScope.launch {
                handleGestureWake()
            }
        }
        onTheftDetected = {
            serviceScope.launch {
                handleTheftDetected()
            }
        }
    }
    wakeEngine.start()
}

private fun startConsciousness() {
    consciousness.startConsciousness()
}

private fun startSpatialAwareness() {
    spatialAwareness.startMapping()
}

private fun showHologramOverlay() {
    windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

    val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = 20
        y = 100
    }

    val hologram = android.widget.ImageView(this).apply {
        setImageResource(android.R.drawable.ic_menu_mylocation)
        alpha = 0.7f
    }

    windowManager?.addView(hologram, params)
    hologramView = hologram
}

private suspend fun handleWakeWord(confidence: Float, voiceMatch: Float) {
    updateNotification("Wake word detected!", "Confidence: ${(confidence * 100).toInt()}%")
    tts.speak("Yes? I'm listening.", com.omnijarvis.perception.Emotion.NEUTRAL)

    val intent = Intent(this, HologramActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
        putExtra("MODE", "LISTENING")
    }
    startActivity(intent)
}

private suspend fun handleProximityWake() {
    updateNotification("Proximity wake", "Phone picked up")
}

private suspend fun handleGestureWake() {
    updateNotification("Gesture wake", "Motion detected")
}

private suspend fun handleTheftDetected() {
    updateNotification("⚠️ THEFT ALERT", "Unauthorized access detected!")

    tts.speak("Security alert! This device is locked.", com.omnijarvis.perception.Emotion.ANGRY)

    val devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
    devicePolicyManager.lockNow()

    val location = spatialAwareness.getLastKnownLocation()
    // Send alert with location
}

private fun updateNotification(title: String, text: String) {
    val notification = buildNotification(title, text)
    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
    notificationManager.notify(NOTIFICATION_ID, notification)
}

override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
        "LISTEN" -> {
            serviceScope.launch {
                handleWakeWord(0.95f, 1.0f)
            }
        }
        "SETTINGS" -> {
            val settingsIntent = Intent(this, com.omnijarvis.ui.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(settingsIntent)
        }
    }
    return START_STICKY
}

override fun onBind(intent: Intent?): IBinder = binder

override fun onDestroy() {
    super.onDestroy()
    isRunning = false

    hologramView?.let { windowManager?.removeView(it) }
    wakeEngine.stop()
    serviceScope.cancel()

    // Restart if killed
    val restartIntent = Intent(this, OmniService::class.java)
    sendBroadcast(restartIntent)
}
