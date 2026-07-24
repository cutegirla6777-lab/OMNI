package com.omnijarvis

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.omnijarvis.chat.HumanLikeChat
import com.omnijarvis.connect.QRPhoneConnector
import com.omnijarvis.core.OmniService
import com.omnijarvis.social.AutoSocialManager
import com.omnijarvis.ui.HologramActivity
import com.omnijarvis.ui.SettingsActivity
import com.omnijarvis.video.VideoEditor
import com.omnijarvis.voice.JarvisVoice
import com.omnijarvis.workflow.AutoReleaseWorkflow

class MainActivity : AppCompatActivity() {

    // ==================== SERVICE BINDING (from ui/MainActivity.kt) ====================

    private var omniService: OmniService? = null
    private var serviceBound = false

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

    // ==================== FEATURE MANAGERS (from root MainActivity.kt) ====================

    private lateinit var videoEditor: VideoEditor
    private lateinit var qrConnector: QRPhoneConnector
    private lateinit var humanChat: HumanLikeChat
    private lateinit var jarvisVoice: JarvisVoice
    private lateinit var socialManager: AutoSocialManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindOmniService()
        initFeatures()
        setupUI()
        checkPermissions()
    }

    private fun bindOmniService() {
        val intent = Intent(this, OmniService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun initFeatures() {
        videoEditor = VideoEditor(this)
        qrConnector = QRPhoneConnector(this)
        humanChat = HumanLikeChat(this)
        jarvisVoice = JarvisVoice(this)
        socialManager = AutoSocialManager(this)

        // Enable auto-reply
        humanChat.enableAutoReply(HumanLikeChat.AutoReplyConfig(
            enabled = true,
            delaySeconds = 5,
            busyMessage = "Hey! Tehzeeb here, was busy. What's up? 😊",
            humanLike = true,
            matchTone = true,
            useEmojis = true
        ))
    }

    // ==================== UI SETUP ====================
    // ⚠️ NOTE: button IDs below come from both original files combined
    // (fab_voice, btn_release, btn_settings, btn_tools from ui/MainActivity.kt,
    // and btn_video_edit, btn_qr_connect, btn_chat, btn_voice, btn_auto_post from
    // root MainActivity.kt). activity_main.xml needs ALL of these ids defined —
    // it wasn't provided, so this hasn't been checked against the actual layout.

    private fun setupUI() {
        findViewById<FloatingActionButton>(R.id.fab_voice).setOnClickListener {
            startActivity(Intent(this, HologramActivity::class.java).apply {
                putExtra("MODE", "LISTENING")
            })
        }

        findViewById<Button>(R.id.btn_release).setOnClickListener {
            triggerRelease()
        }

        findViewById<Button>(R.id.btn_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<Button>(R.id.btn_tools).setOnClickListener {
            showToolsDialog()
        }

        findViewById<Button>(R.id.btn_video_edit).setOnClickListener {
            startVideoEdit()
        }

        findViewById<Button>(R.id.btn_qr_connect).setOnClickListener {
            startQRConnect()
        }

        findViewById<Button>(R.id.btn_chat).setOnClickListener {
            startChat()
        }

        findViewById<Button>(R.id.btn_voice).setOnClickListener {
            testJarvisVoice()
        }

        findViewById<Button>(R.id.btn_auto_post).setOnClickListener {
            testAutoPost()
        }
    }

    // ==================== RELEASE (from ui/MainActivity.kt) ====================

    private fun triggerRelease() {
        val workflow = AutoReleaseWorkflow(this)
        workflow.triggerAutoRelease()

        Toast.makeText(this, "Release workflow started", Toast.LENGTH_SHORT).show()

        workflow.state.collectIn(this) { state ->
            when (state) {
                is AutoReleaseWorkflow.WorkflowState.Success -> {
                    Toast.makeText(this, "Released: ${state.version}", Toast.LENGTH_LONG).show()
                }
                is AutoReleaseWorkflow.WorkflowState.Failed -> {
                    Toast.makeText(this, "Failed: ${state.error}", Toast.LENGTH_LONG).show()
                }
                else -> {}
            }
        }
    }

    // ==================== PERMISSIONS (from ui/MainActivity.kt) ====================

    private fun checkPermissions() {
        val needed = mutableListOf<String>()

        if (!Settings.canDrawOverlays(this)) {
            needed.add("Draw over other apps")
        }

        if (!isAccessibilityServiceEnabled()) {
            needed.add("Accessibility Service")
        }

        if (needed.isNotEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Permissions Required")
                .setMessage(needed.joinToString("\n"))
                .setPositiveButton("Grant") { _, _ ->
                    requestPermissions(needed)
                }
                .setCancelable(false)
                .show()
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices.contains(packageName)
    }

    private fun requestPermissions(needed: List<String>) {
        if (needed.contains("Draw over other apps")) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        if (needed.contains("Accessibility Service")) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }
    }

    // ==================== TOOLS DIALOG (from ui/MainActivity.kt) ====================

    private fun showToolsDialog() {
        val tools = arrayOf(
            "Build Website",
            "Build App",
            "Edit Video",
            "Generate Image",
            "Code Assistant",
            "System Control"
        )

        AlertDialog.Builder(this)
            .setTitle("OMNI Tools")
            .setItems(tools) { _, which ->
                when (which) {
                    0 -> startCreation("website")
                    1 -> startCreation("app")
                    2 -> startCreation("video")
                    3 -> startCreation("image")
                    4 -> startCreation("code")
                    5 -> startSystemControl()
                }
            }
            .show()
    }

    private fun startCreation(type: String) {
        Toast.makeText(this, "Starting $type creation...", Toast.LENGTH_SHORT).show()
        // Delegate to SelfCreationEngine via service
    }

    private fun startSystemControl() {
        // Show system control panel
    }

    // ==================== FEATURE ACTIONS (from root MainActivity.kt) ====================

    private fun startVideoEdit() {
        jarvisVoice.speakJarvis("Initiating video editing sequence", JarvisVoice.Emotion.NEUTRAL)
    }

    private fun startQRConnect() {
        val qrBitmap = qrConnector.generateQRCode()
        // Display QR
    }

    private fun startChat() {
        humanChat.sendMessage("Hello!", isFromUser = false)
    }

    private fun testJarvisVoice() {
        jarvisVoice.speakGreeting()
    }

    private fun testAutoPost() {
        // Test auto-post
    }

    override fun onDestroy() {
        super.onDestroy()
        if (serviceBound) {
            unbindService(serviceConnection)
        }
    }
}
