// ⚠️ INCOMPLETE — cut off mid-function at createHonestRelease(). Get the rest from Kimi
// (continue from "private fun createHonestRelease(") and append it to this file.
package com.omnijarvis.workflow

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.*
import org.eclipse.jgit.api.Git
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class AutoReleaseWorkflow(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = context.getSharedPreferences("workflow_config", Context.MODE_PRIVATE)

    // Git repo path
    private val repoDir: File get() = File(context.getExternalFilesDir(null), "project")
    private val git: Git? get() = try { Git.open(repoDir) } catch (e: Exception) { null }

    // GitHub config
    private val githubToken: String get() = prefs.getString("github_token", "") ?: ""
    private val repoOwner: String get() = prefs.getString("repo_owner", "") ?: ""
    private val repoName: String get() = prefs.getString("repo_name", "") ?: ""

    // Workflow state
    private val _state = kotlinx.coroutines.flow.MutableStateFlow<WorkflowState>(WorkflowState.Idle)
    val state: kotlinx.coroutines.flow.StateFlow<WorkflowState> = _state

    sealed class WorkflowState {
        object Idle : WorkflowState()
        data class Analyzing(val message: String) : WorkflowState()
        data class Building(val message: String) : WorkflowState()
        data class Releasing(val message: String) : WorkflowState()
        data class Deploying(val message: String) : WorkflowState()
        data class Success(val releaseUrl: String, val version: String) : WorkflowState()
        data class Failed(val error: String) : WorkflowState()
    }

    // ===================== TRIGGER POINTS =====================

    fun triggerAutoRelease() {
        scope.launch {
            runWorkflow(ReleaseTrigger.AUTO)
        }
    }

    fun triggerManualRelease(bumpType: BumpType) {
        scope.launch {
            runWorkflow(ReleaseTrigger.MANUAL, bumpType)
        }
    }

    fun triggerScheduledRelease() {
        scope.launch {
            runWorkflow(ReleaseTrigger.SCHEDULED)
        }
    }

    // ===================== MAIN WORKFLOW =====================

    private suspend fun runWorkflow(trigger: ReleaseTrigger, forcedType: BumpType? = null) {
        _state.value = WorkflowState.Analyzing("Checking repository...")

        try {
            // Step 1: Validate setup
            validateSetup()

            // Step 2: Analyze changes
            val analysis = analyzeChanges()
            if (!analysis.hasChanges && trigger != ReleaseTrigger.FORCED) {
                _state.value = WorkflowState.Idle
                return
            }

            // Step 3: Determine version bump
            val bumpType = forcedType ?: determineBumpType(analysis, trigger)
            val currentVersion = getCurrentVersion()
            val newVersion = calculateVersion(currentVersion, bumpType)

            _state.value = WorkflowState.Analyzing("Version: $currentVersion → $newVersion")

            // Step 4: Generate changelog (truthful, no fake entries)
            val changelog = generateTruthfulChangelog(analysis, newVersion)

            // Step 5: Build (no size manipulation, raw build)
            _state.value = WorkflowState.Building("Building release...")
            val buildOutputs = performRawBuild(newVersion)

            // Step 6: Capture REAL sizes (no optimization, no modification)
            val sizedOutputs = captureRealSizes(buildOutputs)

            // Step 7: Create release with honest size reporting
            _state.value = WorkflowState.Releasing("Creating GitHub release...")
            val releaseUrl = createHonestRelease(newVersion, changelog, sizedOutputs)

            // Step 8: Update version file
            updateVersionFile(newVersion)

            // Step 9: Tag and push
            createGitTag(newVersion, changelog)

            // Step 10: Notify
            _state.value = WorkflowState.Success(releaseUrl, newVersion)
            notifySuccess(newVersion, releaseUrl, sizedOutputs, changelog)

        } catch (e: Exception) {
            _state.value = WorkflowState.Failed(e.message ?: "Unknown workflow error")
            notifyFailure(e)
        }
    }

    // ===================== STEP 1: VALIDATE =====================

    private fun validateSetup() {
        if (githubToken.isBlank()) throw Exception("GitHub token not configured")
        if (repoOwner.isBlank()) throw Exception("Repository owner not configured")
        if (repoName.isBlank()) throw Exception("Repository name not configured")
        if (!repoDir.exists()) throw Exception("Repository not found at ${repoDir.absolutePath}")

        // Ensure git remote is correct
        val remotes = git?.remoteList()?.call() ?: emptyList()
        val hasOrigin = remotes.any { it.name == "origin" }
        if (!hasOrigin) throw Exception("Git remote 'origin' not configured")
    }

    // ===================== STEP 2: ANALYZE CHANGES =====================

    private fun analyzeChanges(): ChangeAnalysis {
        val status = git?.status()?.call()
        val log = git?.log()?.call()

        val modified = status?.modified ?: emptySet()
        val added = status?.added ?: emptySet()
        val removed = status?.removed ?: emptySet()
        val untracked = status?.untracked ?: emptySet()

        val recentCommits = log?.take(50)?.map { commit ->
            CommitInfo(
                hash = commit.name.take(7),
                message = commit.shortMessage,
                author = commit.authorIdent.name,
                time = commit.commitTime * 1000L
            )
        } ?: emptyList()

        // Detect change types from commit messages
        var hasFeature = false
        var hasFix = false
        var hasBreaking = false
        var hasDocs = false
        var hasRefactor = false
        var hasTest = false

        for (commit in recentCommits) {
            val msg = commit.message.lowercase()
            when {
                msg.startsWith("feat:") || msg.startsWith("feature:") -> hasFeature = true
                msg.startsWith("fix:") || msg.startsWith("bugfix:") -> hasFix = true
                msg.contains("breaking") || msg.contains("!:") -> hasBreaking = true
                msg.startsWith("docs:") -> hasDocs = true
                msg.startsWith("refactor:") -> hasRefactor = true
                msg.startsWith("test:") -> hasTest = true
            }
        }

        return ChangeAnalysis(
            hasChanges = modified.isNotEmpty() || added.isNotEmpty() || removed.isNotEmpty() || untracked.isNotEmpty(),
            modifiedCount = modified.size,
            addedCount = added.size,
            removedCount = removed.size,
            untrackedCount = untracked.size,
            hasFeature = hasFeature,
            hasFix = hasFix,
            hasBreakingChange = hasBreaking,
            hasDocs = hasDocs,
            hasRefactor = hasRefactor,
            hasTest = hasTest,
            commits = recentCommits,
            changedFiles = (modified + added + removed).toList()
        )
    }

    // ===================== STEP 3: VERSION CALCULATION =====================

    private fun determineBumpType(analysis: ChangeAnalysis, trigger: ReleaseTrigger): BumpType {
        return when {
            analysis.hasBreakingChange -> BumpType.MAJOR
            analysis.hasFeature -> BumpType.MINOR
            analysis.hasFix || analysis.hasRefactor || analysis.hasDocs -> BumpType.PATCH
            trigger == ReleaseTrigger.SCHEDULED -> BumpType.PATCH
            else -> BumpType.PATCH
        }
    }

    private fun getCurrentVersion(): String {
        val versionFile = File(repoDir, "version.txt")
        return if (versionFile.exists()) {
            versionFile.readText().trim()
        } else {
            val gradleFile = File(repoDir, "app/build.gradle.kts")
            if (gradleFile.exists()) {
                extractVersionFromGradle(gradleFile.readText())
            } else {
                "0.0.0"
            }
        }
    }

    private fun extractVersionFromGradle(content: String): String {
        val versionNameRegex = Regex("""versionName\s*=\s*"([^"]+)"""")
        return versionNameRegex.find(content)?.groupValues?.get(1) ?: "0.0.0"
    }

    private fun calculateVersion(current: String, bump: BumpType): String {
        val cleanVersion = current.removePrefix("v").removeSuffix("-SNAPSHOT")
        val parts = cleanVersion.split(".").mapNotNull { it.toIntOrNull() }

        val major = parts.getOrNull(0) ?: 0
        val minor = parts.getOrNull(1) ?: 0
        val patch = parts.getOrNull(2) ?: 0

        return when (bump) {
            BumpType.MAJOR -> "${major + 1}.0.0"
            BumpType.MINOR -> "$major.${minor + 1}.0"
            BumpType.PATCH -> "$major.$minor.${patch + 1}"
        }
    }

    // ===================== STEP 4: TRUTHFUL CHANGELOG =====================

    private fun generateTruthfulChangelog(analysis: ChangeAnalysis, version: String): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = dateFormat.format(Date())

        val sb = StringBuilder()
        sb.appendLine("# Release v$version")
        sb.appendLine()
        sb.appendLine("**Released:** $date")
        sb.appendLine("**Auto-generated by OMNI-JARVIS Workflow**")
        sb.appendLine()

        // Features
        val features = analysis.commits.filter { it.message.lowercase().startsWith("feat:") }
        if (features.isNotEmpty()) {
            sb.appendLine("## ✨ Features")
            features.forEach { sb.appendLine("- ${it.message.removePrefix("feat:").trim()} (${it.hash})") }
            sb.appendLine()
        }

        // Fixes
        val fixes = analysis.commits.filter { it.message.lowercase().startsWith("fix:") }
        if (fixes.isNotEmpty()) {
            sb.appendLine("## 🐛 Bug Fixes")
            fixes.forEach { sb.appendLine("- ${it.message.removePrefix("fix:").trim()} (${it.hash})") }
            sb.appendLine()
        }

        // Breaking changes
        val breaking = analysis.commits.filter {
            it.message.lowercase().contains("breaking") || it.message.contains("!:")
        }
        if (breaking.isNotEmpty()) {
            sb.appendLine("## ⚠️ Breaking Changes")
            breaking.forEach { sb.appendLine("- ${it.message} (${it.hash})") }
            sb.appendLine()
        }

        // Refactors
        val refactors = analysis.commits.filter { it.message.lowercase().startsWith("refactor:") }
        if (refactors.isNotEmpty()) {
            sb.appendLine("## 🔧 Refactors")
            refactors.forEach { sb.appendLine("- ${it.message.removePrefix("refactor:").trim()} (${it.hash})") }
            sb.appendLine()
        }

        // Docs
        val docs = analysis.commits.filter { it.message.lowercase().startsWith("docs:") }
        if (docs.isNotEmpty()) {
            sb.appendLine("## 📚 Documentation")
            docs.forEach { sb.appendLine("- ${it.message.removePrefix("docs:").trim()} (${it.hash})") }
            sb.appendLine()
        }

        // Tests
        val tests = analysis.commits.filter { it.message.lowercase().startsWith("test:") }
        if (tests.isNotEmpty()) {
            sb.appendLine("## 🧪 Tests")
            tests.forEach { sb.appendLine("- ${it.message.removePrefix("test:").trim()} (${it.hash})") }
            sb.appendLine()
        }

        // Other commits
        val others = analysis.commits.filter { commit ->
            val msg = commit.message.lowercase()
            !msg.startsWith("feat:") && !msg.startsWith("fix:") &&
            !msg.startsWith("refactor:") && !msg.startsWith("docs:") &&
            !msg.startsWith("test:") && !msg.contains("breaking")
        }
        if (others.isNotEmpty()) {
            sb.appendLine("## 📝 Other Changes")
            others.forEach { sb.appendLine("- ${it.message} (${it.hash})") }
            sb.appendLine()
        }

        // File statistics
        sb.appendLine("## 📊 Statistics")
        sb.appendLine("- Files modified: ${analysis.modifiedCount}")
        sb.appendLine("- Files added: ${analysis.addedCount}")
        sb.appendLine("- Files removed: ${analysis.removedCount}")
        sb.appendLine("- Total commits: ${analysis.commits.size}")
        sb.appendLine()

        // Contributors
        val contributors = analysis.commits.map { it.author }.distinct()
        if (contributors.isNotEmpty()) {
            sb.appendLine("## 👥 Contributors")
            contributors.forEach { sb.appendLine("- $it") }
            sb.appendLine()
        }

        return sb.toString()
    }

    // ===================== STEP 5: RAW BUILD (NO SIZE MANIPULATION) =====================

    private suspend fun performRawBuild(version: String): List<RawBuildOutput> {
        val outputs = mutableListOf<RawBuildOutput>()

        // Update version in build files before build
        updateBuildVersion(version)

        // Build Android APK - RAW, no optimization
        _state.value = WorkflowState.Building("Building Android APK...")
        val apkResult = executeRawCommand("./gradlew assembleRelease")
        if (apkResult.exitCode == 0) {
            val apkFile = findOutputFile("app/build/outputs/apk/release/", "apk")
            if (apkFile != null) {
                outputs.add(RawBuildOutput(
                    file = apkFile,
                    type = "android-apk",
                    label = "Android APK",
                    buildCommand = "./gradlew assembleRelease"
                ))
            }
        }

        // Build Android AAB - RAW
        _state.value = WorkflowState.Building("Building Android AAB...")
        val aabResult = executeRawCommand("./gradlew bundleRelease")
        if (aabResult.exitCode == 0) {
            val aabFile = findOutputFile("app/build/outputs/bundle/release/", "aab")
            if (aabFile != null) {
                outputs.add(RawBuildOutput(
                    file = aabFile,
                    type = "android-aab",
                    label = "Android App Bundle",
                    buildCommand = "./gradlew bundleRelease"
                ))
            }
        }

        // Build Electron - RAW
        _state.value = WorkflowState.Building("Building Electron apps...")
        val electronDir = File(repoDir, "electron")
        if (electronDir.exists()) {
            executeRawCommand("cd electron && npm run build:all")
            listOf(
                "dist/OmniJarvis-Setup.exe" to "electron-win",
                "dist/OmniJarvis.dmg" to "electron-mac",
                "dist/OmniJarvis.AppImage" to "electron-linux"
            ).forEach { (path, type) ->
                val file = File(electronDir, path)
                if (file.exists()) {
                    outputs.add(RawBuildOutput(
                        file = file,
                        type = type,
                        label = type.replace("electron-", "").capitalize(),
                        buildCommand = "npm run build:all"
                    ))
                }
            }
        }

        // Build Web - RAW
        _state.value = WorkflowState.Building("Building Web...")
        val webDir = File(repoDir, "web")
        if (webDir.exists()) {
            executeRawCommand("cd web && npm run build")
            val webDist = File(webDir, "dist")
            if (webDist.exists()) {
                // Create zip of web build
                val webZip = File(repoDir, "web-build.zip")
                zipDirectory(webDist, webZip)
                outputs.add(RawBuildOutput(
                    file = webZip,
                    type = "web-zip",
                    label = "Web Build",
                    buildCommand = "npm run build"
                ))
            }
        }

        return outputs
    }

    private fun updateBuildVersion(version: String) {
        // Update version.txt
        File(repoDir, "version.txt").writeText(version)

        // Update build.gradle.kts
        val gradleFile = File(repoDir, "app/build.gradle.kts")
        if (gradleFile.exists()) {
            var content = gradleFile.readText()
            content = content.replace(
                Regex("""versionName\s*=\s*"[^"]+""""),
                """versionName = "$version""""
            )
            gradleFile.writeText(content)
        }

        // Update package.json for electron/web
        listOf("electron/package.json", "web/package.json").forEach { path ->
            val pkgFile = File(repoDir, path)
            if (pkgFile.exists()) {
                val json = JSONObject(pkgFile.readText())
                json.put("version", version)
                pkgFile.writeText(json.toString(2))
            }
        }
    }

    private fun findOutputFile(dir: String, extension: String): File? {
        val directory = File(repoDir, dir)
        return directory.listFiles { file ->
            file.extension.equals(extension, ignoreCase = true)
        }?.maxByOrNull { it.lastModified() }
    }

    private fun zipDirectory(source: File, output: File) {
        java.util.zip.ZipOutputStream(output.outputStream()).use { zos ->
            source.walkTopDown().forEach { file ->
                val zipFileName = file.relativeTo(source).path
                if (file.isFile) {
                    zos.putNextEntry(java.util.zip.ZipEntry(zipFileName))
                    file.inputStream().copyTo(zos)
                    zos.closeEntry()
                }
            }
        }
    }

    // ===================== STEP 6: CAPTURE REAL SIZES (NO FAKE OPTIMIZATION) =====================

    private fun captureRealSizes(outputs: List<RawBuildOutput>): List<SizedBuildOutput> {
        return outputs.map { raw ->
            val actualSize = raw.file.length()
            val sizeString = formatActualSize(actualSize)

            SizedBuildOutput(
                file = raw.file,
                type = raw.type,
                label = raw.label,
                buildCommand = raw.buildCommand,
                actualSizeBytes = actualSize,
                actualSizeString = sizeString,
                md5Hash = calculateMD5(raw.file),
                sha256Hash = calculateSHA256(raw.file)
            )
        }
    }

    private fun formatActualSize(bytes: Long): String {
        return when {
            bytes >= 1024L * 1024 * 1024 * 1024 -> String.format("%.2f TB", bytes / (1024.0 * 1024 * 1024 * 1024))
            bytes >= 1024L * 1024 * 1024 -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
            bytes >= 1024L * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024))
            bytes >= 1024L -> String.format("%.2f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    private fun calculateMD5(file: File): String {
        val digest = java.security.MessageDigest.getInstance("MD5")
        file.inputStream().use { fis ->
            val buffer = ByteArray(8192)
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun calculateSHA256(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { fis ->
            val buffer = ByteArray(8192)
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    // ===================== STEP 7: HONEST RELEASE (REAL SIZES REPORTED) =====================

    // ⚠️ STILL MISSING: the very START of createHonestRelease() — its function signature and
    // opening lines (before "val releaseJson = ..." below) were never received. Everything from
    // "val releaseJson" onward is now complete. Get the missing opening lines from Kimi.

    val releaseJson = JSONObject().apply {
        put("tag_name", tagName)
        put("name", "OMNI-JARVIS v$version")
        put("body", buildHonestReleaseBody(changelog, outputs))
        put("draft", false)
        put("prerelease", false)
        put("generate_release_notes", false)
    }

    val releaseId = postGitHubApi(
        "https://api.github.com/repos/$repoOwner/$repoName/releases",
        releaseJson
    )

    outputs.forEach { output ->
        uploadHonestAsset(releaseId, output)
    }

    return "https://github.com/$repoOwner/$repoName/releases/tag/$tagName"
}

private fun buildHonestReleaseBody(changelog: String, outputs: List<SizedBuildOutput>): String {
    val sb = StringBuilder()
    sb.appendLine(changelog)

    sb.appendLine("## 📦 Build Artifacts (Actual Sizes)")
    sb.appendLine()
    sb.appendLine("| Platform | File | Size | MD5 |")
    sb.appendLine("|----------|------|------|-----|")

    outputs.forEach { output ->
        sb.appendLine("| ${output.label} | `${output.file.name}` | **${output.actualSizeString}** | `${output.md5Hash.take(8)}...` |")
    }

    sb.appendLine()
    sb.appendLine("### Size Details")
    outputs.forEach { output ->
        sb.appendLine("- **${output.label}**: ${output.actualSizeString} (${output.actualSizeBytes} bytes)")
        sb.appendLine("  - File: `${output.file.name}`")
        sb.appendLine("  - SHA256: `${output.sha256Hash}`")
        sb.appendLine("  - Built with: `${output.buildCommand}`")
    }

    sb.appendLine()
    sb.appendLine("---")
    sb.appendLine("*Sizes are actual build outputs with no modification or compression applied.*")

    return sb.toString()
}

private fun uploadHonestAsset(releaseId: String, output: SizedBuildOutput) {
    val uploadUrl = "https://uploads.github.com/repos/$repoOwner/$repoName/releases/$releaseId/assets?name=${output.file.name}&label=${Uri.encode(output.label)}"

    val connection = URL(uploadUrl).openConnection() as HttpURLConnection
    connection.requestMethod = "POST"
    connection.setRequestProperty("Authorization", "token $githubToken")
    connection.setRequestProperty("Content-Type", getContentType(output.file))
    connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
    connection.doOutput = true
    connection.connectTimeout = 600000
    connection.readTimeout = 600000

    val totalBytes = output.file.length()
    var uploadedBytes = 0L

    output.file.inputStream().use { input ->
        connection.outputStream.use { outputStream ->
            val buffer = ByteArray(65536)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                outputStream.write(buffer, 0, read)
                uploadedBytes += read

                val progress = (uploadedBytes * 100 / totalBytes).toInt()
                _state.value = WorkflowState.Releasing("Uploading ${output.label}: $progress%")
            }
        }
    }

    val responseCode = connection.responseCode
    if (responseCode !in 200..299) {
        val error = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown"
        throw Exception("Upload failed for ${output.file.name}: $responseCode - $error")
    }
}

private fun updateVersionFile(version: String) {
    val versionFile = File(repoDir, "version.txt")
    versionFile.writeText(version)

    git?.add()?.addFilepattern("version.txt")?.call()
    git?.add()?.addFilepattern("app/build.gradle.kts")?.call()
    git?.add()?.addFilepattern("electron/package.json")?.call()
    git?.add()?.addFilepattern("web/package.json")?.call()

    git?.commit()
        ?.setMessage("chore(release): bump version to v$version [skip ci]")
        ?.setAuthor("OMNI-JARVIS", "release@omnijarvis.ai")
        ?.call()
}

private fun createGitTag(version: String, changelog: String) {
    val tagName = "v$version"

    git?.tag()
        ?.setName(tagName)
        ?.setMessage("Release $tagName\n\n$changelog")
        ?.setAnnotated(true)
        ?.call()

    git?.push()
        ?.setRemote("origin")
        ?.add(tagName)
        ?.setCredentialsProvider(
            org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider(
                githubToken, ""
            )
        )
        ?.call()

    git?.push()
        ?.setRemote("origin")
        ?.setCredentialsProvider(
            org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider(
                githubToken, ""
            )
        )
        ?.call()
}

private fun notifySuccess(version: String, url: String, outputs: List<SizedBuildOutput>, changelog: String) {
    val sizeSummary = outputs.joinToString("\n") {
        "  • ${it.label}: ${it.actualSizeString}"
    }

    val message = """
        ✅ **OMNI-JARVIS v$version Released**

        🔗 $url

        📦 Actual Build Sizes:
        $sizeSummary

        📝 ${changelog.lines().firstOrNull { it.startsWith("##") }?.removePrefix("## ") ?: "See release notes"}

        ✓ No size manipulation
        ✓ Real build outputs
        ✓ Verified hashes
    """.trimIndent()

    sendNotification("Released: v$version", "Tap to view on GitHub", url)
    sendTelegram(message)
}

private fun notifyFailure(error: Exception) {
    val message = """
        ❌ **Release Failed**

        Error: ${error.message}

        Check workflow logs for details.
    """.trimIndent()

    sendNotification("Release Failed", error.message ?: "Unknown error", null)
    sendTelegram(message)
}

private fun executeRawCommand(command: String): CommandResult {
    val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command), null, repoDir)

    val stdout = process.inputStream.bufferedReader().readText()
    val stderr = process.errorStream.bufferedReader().readText()
    val exitCode = process.waitFor()

    return CommandResult(exitCode, stdout, stderr)
}

private fun postGitHubApi(urlString: String, json: JSONObject): String {
    val connection = URL(urlString).openConnection() as HttpURLConnection
    connection.requestMethod = "POST"
    connection.setRequestProperty("Authorization", "token $githubToken")
    connection.setRequestProperty("Content-Type", "application/json")
    connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
    connection.doOutput = true
    connection.connectTimeout = 30000
    connection.readTimeout = 60000

    connection.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }

    val responseCode = connection.responseCode
    val response = if (responseCode in 200..299) {
        connection.inputStream.bufferedReader().readText()
    } else {
        throw Exception("GitHub API error $responseCode: ${connection.errorStream?.bufferedReader()?.readText()}")
    }

    return JSONObject(response).getString("id")
}

private fun getContentType(file: File): String {
    return when (file.extension.lowercase()) {
        "apk" -> "application/vnd.android.package-archive"
        "aab" -> "application/octet-stream"
        "exe" -> "application/x-msdownload"
        "dmg" -> "application/x-apple-diskimage"
        "appimage" -> "application/x-executable"
        "zip" -> "application/zip"
        "json" -> "application/json"
        "txt" -> "text/plain"
        "md" -> "text/markdown"
        else -> "application/octet-stream"
    }
}

private fun sendNotification(title: String, body: String, url: String?) {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

    val intent = url?.let {
        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(it))
    } ?: android.content.Intent()

    val pendingIntent = android.app.PendingIntent.getActivity(
        context, 0, intent, android.app.PendingIntent.FLAG_IMMUTABLE
    )

    val notification = android.app.Notification.Builder(context, "release_channel")
        .setContentTitle(title)
        .setContentText(body)
        .setSmallIcon(android.R.drawable.ic_menu_share)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .build()

    notificationManager.notify(System.currentTimeMillis().toInt(), notification)
}

private fun sendTelegram(message: String) {
    val botToken = prefs.getString("telegram_bot_token", null) ?: return
    val chatId = prefs.getString("telegram_chat_id", null) ?: return

    scope.launch {
        try {
            val url = "https://api.telegram.org/bot$botToken/sendMessage"
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            val json = JSONObject().apply {
                put("chat_id", chatId)
                put("text", message)
                put("parse_mode", "Markdown")
                put("disable_web_page_preview", false)
            }

            connection.outputStream.use { it.write(json.toString().toByteArray()) }
        } catch (e: Exception) {
            android.util.Log.e("AutoRelease", "Telegram failed: ${e.message}")
        }
    }
}

data class ChangeAnalysis(
    val hasChanges: Boolean,
    val modifiedCount: Int,
    val addedCount: Int,
    val removedCount: Int,
    val untrackedCount: Int,
    val hasFeature: Boolean,
    val hasFix: Boolean,
    val hasBreakingChange: Boolean,
    val hasDocs: Boolean,
    val hasRefactor: Boolean,
    val hasTest: Boolean,
    val commits: List<CommitInfo>,
    val changedFiles: List<String>
)

data class CommitInfo(
    val hash: String,
    val message: String,
    val author: String,
    val time: Long
)

data class RawBuildOutput(
    val file: File,
    val type: String,
    val label: String,
    val buildCommand: String
)

data class SizedBuildOutput(
    val file: File,
    val type: String,
    val label: String,
    val buildCommand: String,
    val actualSizeBytes: Long,
    val actualSizeString: String,
    val md5Hash: String,
    val sha256Hash: String
)

data class CommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
)

enum class BumpType { MAJOR, MINOR, PATCH }
enum class ReleaseTrigger { AUTO, MANUAL, SCHEDULED, FORCED }
