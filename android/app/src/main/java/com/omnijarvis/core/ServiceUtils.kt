// ⚠️ UNCLEAR ORIGIN — this was a standalone floating function in what was pasted, with no
// surrounding class or file context. It's a small helper to start OmniService safely across
// Android versions. Placed here as a guess; move it wherever it's actually called from.

private fun startOmniService(context: Context) {
    val serviceIntent = Intent(context, OmniService::class.java)
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        context.startForegroundService(serviceIntent)
    } else {
        context.startService(serviceIntent)
    }
}
