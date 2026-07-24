// ⚠️ INCOMPLETE FRAGMENT — package statement, imports, and the class declaration line
// (e.g. "class OmniAccessibilityService : AccessibilityService() {") were not included in
// what was pasted. Body below is otherwise complete as received.

companion object {
    var instance: OmniAccessibilityService? = null
        private set
}

private val nodeMap = mutableMapOf<String, AccessibilityNodeInfo>()

override fun onServiceConnected() {
    super.onServiceConnected()
    instance = this

    serviceInfo = AccessibilityServiceInfo().apply {
        eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED or
                AccessibilityEvent.TYPE_VIEW_FOCUSED or
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED or
                AccessibilityEvent.TYPE_VIEW_SCROLLED
        feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        notificationTimeout = 100
    }
}

override fun onAccessibilityEvent(event: AccessibilityEvent) {
    when (event.eventType) {
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
            val packageName = event.packageName?.toString() ?: return
            val className = event.className?.toString() ?: return
            analyzeWindow(packageName, className)
        }
        AccessibilityEvent.TYPE_VIEW_CLICKED -> {
            recordInteraction(event)
        }
        AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
            handleNotification(event)
        }
    }
}

private fun analyzeWindow(packageName: String, className: String) {
    val rootNode = rootInActiveWindow ?: return
    nodeMap.clear()
    traverseNode(rootNode, packageName)
}

private fun traverseNode(node: AccessibilityNodeInfo, packageName: String, depth: Int = 0) {
    val viewId = node.viewIdResourceName ?: "node_${System.nanoTime()}"
    nodeMap[viewId] = node

    // Extract text and metadata
    val text = node.text?.toString()
    val contentDesc = node.contentDescription?.toString()
    val className = node.className?.toString()

    // Store for AI analysis
    if (!text.isNullOrBlank() || !contentDesc.isNullOrBlank()) {
        // Send to consciousness engine
    }

    for (i in 0 until node.childCount) {
        node.getChild(i)?.let { traverseNode(it, packageName, depth + 1) }
    }
}

private fun recordInteraction(event: AccessibilityEvent) {
    val node = event.source ?: return
    val text = event.text.joinToString()
    val bounds = Rect().apply { node.getBoundsInScreen(this) }

    // Learn from user interactions
}

private fun handleNotification(event: AccessibilityEvent) {
    val notification = event.parcelableData as? android.app.Notification ?: return
    val packageName = event.packageName?.toString() ?: return

    // Intercept for auto-reply
    if (shouldIntercept(packageName)) {
        val actions = notification.actions ?: emptyArray()
        // Find reply action
        val replyAction = actions.find { it.title.toString().contains("Reply", true) }
        replyAction?.let {
            // Auto-reply logic
        }
    }
}

private fun shouldIntercept(packageName: String): Boolean {
    return packageName in listOf(
        "com.whatsapp",
        "com.facebook.orca",
        "com.telegram.messenger",
        "com.google.android.apps.messaging"
    )
}

// Public API for automation

fun clickAt(x: Int, y: Int): Boolean {
    val path = android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(
        AccessibilityNodeInfo.ACTION_CLICK,
        null
    )
    val point = android.graphics.Path().apply { moveTo(x.toFloat(), y.toFloat()) }
    return performGlobalAction(GLOBAL_ACTION_CLICK)
}

fun typeText(nodeId: String, text: String): Boolean {
    val node = nodeMap[nodeId] ?: return false
    val arguments = Bundle().apply {
        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
    }
    return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
}

fun swipe(startX: Int, startY: Int, endX: Int, endY: Int): Boolean {
    val gesture = android.accessibilityservice.GestureDescription.Builder()
        .addStroke(
            android.accessibilityservice.GestureDescription.StrokeDescription(
                android.graphics.Path().apply {
                    moveTo(startX.toFloat(), startY.toFloat())
                    lineTo(endX.toFloat(), endY.toFloat())
                },
                0,
                300
            )
        )
        .build()
    return dispatchGesture(gesture, null, null)
}

fun getScreenText(): String {
    val rootNode = rootInActiveWindow ?: return ""
    return extractTextRecursive(rootNode)
}

private fun extractTextRecursive(node: AccessibilityNodeInfo): String {
    val sb = StringBuilder()
    node.text?.let { sb.append(it).append(" ") }
    for (i in 0 until node.childCount) {
        node.getChild(i)?.let { sb.append(extractTextRecursive(it)) }
    }
    return sb.toString()
}

fun findNodeByText(text: String): AccessibilityNodeInfo? {
    return nodeMap.values.find { it.text?.toString()?.contains(text, true) == true }
}

override fun onInterrupt() {}

override fun onUnbind(intent: Intent?): Boolean {
    instance = null
    return super.onUnbind(intent)
}
