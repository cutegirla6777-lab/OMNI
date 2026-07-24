// ⚠️ INCOMPLETE FRAGMENT — package statement, imports, and the class declaration line
// (e.g. "class OmniDeviceAdmin : DeviceAdminReceiver() {") were not included in what was
// pasted. Body below is otherwise complete as received.

companion object {
    fun getComponentName(context: Context): ComponentName {
        return ComponentName(context, OmniDeviceAdmin::class.java)
    }

    fun isActive(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isAdminActive(getComponentName(context))
    }

    fun lockDevice(context: Context) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (isActive(context)) {
            dpm.lockNow()
        }
    }

    fun wipeData(context: Context) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (isActive(context)) {
            dpm.wipeData(DevicePolicyManager.WIPE_EXTERNAL_STORAGE)
        }
    }
}

override fun onEnabled(context: Context, intent: Intent) {
    super.onEnabled(context, intent)
}

override fun onDisabled(context: Context, intent: Intent) {
    super.onDisabled(context, intent)
}

override fun onPasswordFailed(context: Context, intent: Intent) {
    super.onPasswordFailed(context, intent)
    // Failed unlock attempt - could be theft
    val attempts = getFailedAttempts(context) + 1
    setFailedAttempts(context, attempts)

    if (attempts >= 5) {
        // Trigger theft protection
        com.omnijarvis.core.OmniService.instance?.let {
            // Alert and lock
        }
    }
}

override fun onPasswordSucceeded(context: Context, intent: Intent) {
    super.onPasswordSucceeded(context, intent)
    setFailedAttempts(context, 0)
}

private fun getFailedAttempts(context: Context): Int {
    return context.getSharedPreferences("security", Context.MODE_PRIVATE)
        .getInt("failed_attempts", 0)
}

private fun setFailedAttempts(context: Context, attempts: Int) {
    context.getSharedPreferences("security", Context.MODE_PRIVATE)
        .edit()
        .putInt("failed_attempts", attempts)
        .apply()
}
