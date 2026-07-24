// ⚠️ INCOMPLETE FRAGMENT — package statement, imports, and the class declaration line
// (e.g. "class HologramActivity : AppCompatActivity() {") were not included in what was
// pasted. Body below is otherwise complete as received.

private var omniService: OmniService? = null
private var serviceBound = false

private lateinit var hologramRing: ImageView
private lateinit var coreOrb: ImageView
private lateinit var statusText: TextView
private lateinit var transcriptText: TextView

private var ringAnimator: ValueAnimator? = null
private var isListening = false

private val scope = CoroutineScope(Dispatchers.Main + Job())

private val serviceConnection = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        val binder = service as OmniService.OmniBinder
        omniService = binder.getService()
        serviceBound = true
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        omniService = null
        serviceBound = false
    }
}

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_hologram)

    window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

    initViews()
    bindService()
    handleIntent(intent)
}

private fun initViews() {
    hologramRing = findViewById(R.id.hologram_ring)
    coreOrb = findViewById(R.id.core_orb)
    statusText = findViewById(R.id.status_text)
    transcriptText = findViewById(R.id.transcript_text)

    hologramRing.setOnTouchListener { _, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (!isListening) startListening()
                true
            }
            MotionEvent.ACTION_UP -> {
                if (isListening) stopListening()
                true
            }
            else -> false
        }
    }
}

private fun bindService() {
    val intent = Intent(this, OmniService::class.java)
    bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
}

private fun handleIntent(intent: Intent?) {
    when (intent?.getStringExtra("MODE")) {
        "LISTENING" -> startListening()
    }
}

private fun startListening() {
    isListening = true
    statusText.text = "Listening..."
    startRingAnimation()
    pulseCore()

    scope.launch {
        // Start voice recognition
        omniService?.wakeEngine?.startListening()

        // Simulate transcript for now
        delay(2000)
        transcriptText.text = "Say something..."

        // Process result
        // omniService?.consciousness?.processUserInput(...)
    }
}

private fun stopListening() {
    isListening = false
    statusText.text = "Tap to speak"
    stopRingAnimation()
    omniService?.wakeEngine?.stopListening()
}

private fun startRingAnimation() {
    ringAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 3000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { animator ->
            hologramRing.rotation = animator.animatedValue as Float
        }
        start()
    }
}

private fun stopRingAnimation() {
    ringAnimator?.cancel()
    hologramRing.rotation = 0f
}

private fun pulseCore() {
    val pulse = ValueAnimator.ofFloat(0.8f, 1.2f).apply {
        duration = 1000
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        addUpdateListener { animator ->
            val scale = animator.animatedValue as Float
            coreOrb.scaleX = scale
            coreOrb.scaleY = scale
        }
        start()
    }
}

fun showResponse(text: String) {
    runOnUiThread {
        transcriptText.text = text
        statusText.text = "OMNI"
    }
}

fun showThinking() {
    runOnUiThread {
        statusText.text = "Thinking..."
        transcriptText.text = "..."
    }
}

override fun onNewIntent(intent: Intent?) {
    super.onNewIntent(intent)
    handleIntent(intent)
}

override fun onDestroy() {
    super.onDestroy()
    scope.cancel()
    ringAnimator?.cancel()
    if (serviceBound) unbindService(serviceConnection)
}
