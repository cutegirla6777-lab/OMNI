// ⚠️ INCOMPLETE FRAGMENT — package statement, imports, and the class declaration line
// (e.g. "class OmniNotificationListener : NotificationListenerService() {") were not
// included in what was pasted. Body below is otherwise complete as received.

private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

override fun onListenerConnected() {
    super.onListenerConnected()
}

override fun onNotificationPosted(sbn: StatusBarNotification) {
    val packageName = sbn.packageName
    val title = sbn.notification.extras.getString("android.title") ?: ""
    val text = sbn.notification.extras.getCharSequence("android.text")?.toString() ?: ""

    scope.launch {
        processNotification(packageName, title, text, sbn)
    }
}

override fun onNotificationRemoved(sbn: StatusBarNotification) {
    // Notification dismissed
}

private suspend fun processNotification(
    packageName: String,
    title: String,
    text: String,
    sbn: StatusBarNotification
) {
    // Send to consciousness for decision
    val shouldReply = com.omnijarvis.core.OmniService.instance?.consciousness?.shouldAutoReply(
        app = packageName,
        sender = title,
        message = text
    ) ?: false

    if (shouldReply) {
        val reply = com.omnijarvis.core.OmniService.instance?.consciousness?.generateSmartReply(
            message = text,
            context = com.omnijarvis.core.OmniService.instance?.let {
                // Build context
            } ?: return,
            emotion = com.omnijarvis.perception.Emotion.NEUTRAL
        ) ?: return

        sendReply(sbn, reply)
    }
}

private fun sendReply(sbn: StatusBarNotification, reply: String) {
    val wearableExt = sbn.notification.extras.getParcelable<android.app.Notification.WearableExtender>("android.wearable.EXTENSIONS")
    val actions = sbn.notification.actions ?: return

    val replyAction = actions.find { it.remoteInputs != null } ?: return

    for (remoteInput in replyAction.remoteInputs ?: emptyArray()) {
        val intent = android.content.Intent().apply {
            putExtra(remoteInput.resultKey, reply)
        }

        val results = android.app.RemoteInput.addResultsToIntent(
            arrayOf(remoteInput),
            intent,
            android.os.Bundle().apply {
                putString(remoteInput.resultKey, reply)
            }
        )

        try {
            replyAction.actionIntent.send(this, 0, intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
