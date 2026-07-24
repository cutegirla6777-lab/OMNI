// ⚠️ INCOMPLETE FRAGMENT — package statement, imports, and the class declaration line
// (e.g. "class OmniApplication : Application() {") were not included in what was pasted.
// Body below is otherwise complete as received.

companion object {
    lateinit var instance: OmniApplication
        private set
}

lateinit var masterKey: MasterKey
    private set

override fun onCreate() {
    super.onCreate()
    instance = this

    initMasterKey()
    createNotificationChannels()
}

private fun initMasterKey() {
    masterKey = MasterKey.Builder(this)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
}

private fun createNotificationChannels() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channels = listOf(
            NotificationChannel(
                "omni_core",
                "OMNI Core",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Core OMNI-JARVIS notifications"
                setShowBadge(true)
            },
            NotificationChannel(
                "omni_alerts",
                "OMNI Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Important alerts and warnings"
                setShowBadge(true)
            },
            NotificationChannel(
                "omni_release",
                "OMNI Release",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Release and deployment notifications"
            },
            NotificationChannel(
                "wake_word",
                "Wake Word",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Wake word detection status"
                setSound(null, null)
            }
        )

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        channels.forEach { notificationManager.createNotificationChannel(it) }
    }
}

fun getEncryptedPrefs(name: String): EncryptedSharedPreferences {
    return EncryptedSharedPreferences.create(
        this,
        name,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    ) as EncryptedSharedPreferences
}
