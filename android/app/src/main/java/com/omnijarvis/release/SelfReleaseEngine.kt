package com.omnijarvis.release

import android.content.Context
import android.os.Build
import kotlinx.coroutines.*
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.RepositoryBuilder
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class SelfReleaseEngine(private val context: Context) {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val git: Git? = try {
        val repoDir = File(context.getExternalFilesDir(null), "project")
        Git.open(repoDir)
    } catch (e: Exception) { null }
    
    // GitHub credentials (encrypted storage)
    private val githubToken: String get() = getSecureToken()
    private val repoOwner = "your-username"
    private val repoName = "omni-jarvis"
    
    data class ReleaseConfig(
        val autoRelease: Boolean = true,
        val autoDeploy: Boolean = true,
        val notifyChannels: List<String> = listOf("notification", "telegram"),
        val sizeLimitMB: Int = 100,
        val retryAttempts: Int = 3,
        val runTests: Boolean = true,
        val optimizeAssets: Boolean = true
    )
    
    // ==================== MAIN ENTRY ====================
    
    fun triggerRelease(mode: ReleaseMode = ReleaseMode.AUTO) {
        scope.launch {
            try {
                when (mode) {
                    ReleaseMode.AUTO -> executeAutoRelease()
                    ReleaseMode.PATCH -> executeRelease("patch")
                    ReleaseMode.MINOR -> executeRelease("minor")
                    ReleaseMode.MAJOR -> executeRelease("major")
                    ReleaseMode.HOTFIX -> executeHotfix()
                }
            } catch (e: Exception) {
                notifyFailure(e)
            }
        }
    }
    
    // ==================== PHASE 1: ANALYZE ====================
    
    private suspend fun executeAutoRelease() {
        notifyProgress("🔍 Analyzing changes...")
        
        val changes = analyzeChanges()
        
        if (!changes.hasChanges) {
            notifyUser("No changes detected since last release")
            return
        }
        
        // Decide version bump
        val bumpType = decideVersionBump(changes)
        executeRelease(bumpType)
    }
    
    private fun analyzeChanges(): ChangeAnalysis {
        val status = git?.status()?.call() ?: return ChangeAnalysis(false)
        
        val modified = status.modified.size
        val added = status.added.size
        val removed = status.removed.size
        
        // Analyze commit messages for breaking changes
        val commits = git?.log()?.call()?.take(10) ?: emptyList()
        val hasBreakingChange = commits.any { 
            it.fullMessage.contains("BREAKING") || 
            it.fullMessage.contains("!:") 
        }
        val hasFeature = commits.any { 
            it.fullMessage.startsWith("feat:") 
        }
        val hasFix = commits.any { 
            it.fullMessage.startsWith("fix:") 
        }
        
        return ChangeAnalysis(
            hasChanges = modified + added + removed > 0,
            modified = modified,
            added = added,
            removed = removed,
            hasBreakingChange = hasBreakingChange,
            hasFeature = hasFeature,
            hasFix = hasFix,
            commitMessages = commits.map { it.fullMessage }
        )
    }
    
    // ==================== PHASE 2: VERSION ====================
    
    private fun decideVersionBump(changes: ChangeAnalysis): String {
        return when {
            changes.hasBreakingChange -> "major"
            changes.hasFeature -> "minor"
            changes.hasFix -> "patch"
            else -> "patch"
        }
    }
    
    private suspend fun executeRelease(bumpType: String) {
        // Get current version
        val currentVersion = getCurrentVersion()
        val newVersion = bumpVersion(currentVersion, bumpType)
        
        notifyProgress("📦 Version: $currentVersion → $newVersion")
        
        // Phase 3: Changelog
        val changelog = generateChangelog(newVersion)
        
        // Phase 4: Quality Check
        if (!runQualityChecks()) {
            throw Exception("Quality checks failed")
        }
        
        // Phase 5: Build
        val buildOutputs = buildProject(newVersion)
        
        // Phase 6: Size optimize
        val optimizedOutputs = optimizeSize(buildOutputs)
        
        // Phase 7: Create release
        val releaseUrl = createGitHubRelease(newVersion, changelog, optimizedOutputs)
        
        // Phase 8: Deploy
        if (ReleaseConfig().autoDeploy) {
            deployAll(optimizedOutputs, newVersion)
        }
        
        // Phase 9: Notify
        notifySuccess(newVersion, releaseUrl, changelog)
        
        // Phase 10: Monitor
        startMonitoring(newVersion)
    }
    
    // ==================== PHASE 3: CHANGELOG ====================
    
    private fun generateChangelog(version: String): String {
        val commits = git?.log()?.call()?.take(50) ?: emptyList()
        val lastTag = getLastTag()
        
        // Categorize changes
        val features = mutableListOf<String>()
        val fixes = mutableListOf<String>()
        breakingChanges = mutableListOf<String>()
        val others = mutableListOf<String>()
        
        for (commit in commits) {
            val msg = commit.fullMessage
            when {
                msg.startsWith("feat:") -> features.add(msg.removePrefix("feat:").trim())
                msg.startsWith("fix:") -> fixes.add(msg.removePrefix("fix:").trim())
                msg.contains("BREAKING") -> breakingChanges.add(msg)
                else -> others.add(msg)
            }
        }
        
        return buildString {
            appendLine("# Release v$version")
            appendLine()
            appendLine("**Released:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}")
            appendLine()
            
            if (features.isNotEmpty()) {
                appendLine("## ✨ New Features")
                features.forEach { appendLine("- $it") }
                appendLine()
            }
            
            if (fixes.isNotEmpty()) {
                appendLine("## 🐛 Bug Fixes")
                fixes.forEach { appendLine("- $it") }
                appendLine()
            }
            
            if (breakingChanges.isNotEmpty()) {
                appendLine("## ⚠️ Breaking Changes")
                breakingChanges.forEach { appendLine("- $it") }
                appendLine()
            }
            
            appendLine("## 📊 Stats")
            appendLine("- Files changed: ${analyzeChanges().modified + analyzeChanges().added + analyzeChanges().removed}")
            appendLine("- Commits: ${commits.size}")
            appendLine("- Size: ${getTotalSize()}")
        }
    }
    
    // ==================== PHASE 4: QUALITY ====================
    
    private suspend fun runQualityChecks(): Boolean {
        notifyProgress("🔬 Running quality checks...")
        
        val checks = listOf(
            runUnitTests(),
            runIntegrationTests(),
            runLintCheck(),
            runSecurityScan(),
            runPerformanceBenchmark()
        )
        
        return checks.all { it }
    }
    
    private suspend fun runUnitTests(): Boolean {
        notifyProgress("Running unit tests...")
        // Execute: ./gradlew test
        return executeCommand("./gradlew test") == 0
    }
    
    private suspend fun runIntegrationTests(): Boolean {
        notifyProgress("Running integration tests...")
        return executeCommand("./gradlew connectedAndroidTest") == 0
    }
    
    private suspend fun runLintCheck(): Boolean {
        notifyProgress("Running lint...")
        return executeCommand("./gradlew lint") == 0
    }
    
    private suspend fun runSecurityScan(): Boolean {
        notifyProgress("Running security scan...")
        // Check for hardcoded keys, vulnerable dependencies
        val dependencyCheck = checkVulnerableDependencies()
        val keyCheck = checkHardcodedKeys()
        return dependencyCheck && keyCheck
    }
    
    private suspend fun runPerformanceBenchmark(): Boolean {
        notifyProgress("Running performance benchmark...")
        // Check startup time, memory usage, APK size
        val startupTime = measureStartupTime()
        val memoryUsage = measureMemoryUsage()
        val apkSize = getApkSize()
        
        return startupTime < 3000 && // 3 seconds
               memoryUsage < 200 &&  // 200MB
               apkSize < ReleaseConfig().sizeLimitMB * 1024 * 1024
    }
    
    // ==================== PHASE 5: BUILD ====================
    
    private suspend fun buildProject(version: String): List<BuildOutput> {
        notifyProgress("🔨 Building project...")
        
        val outputs = mutableListOf<BuildOutput>()
        
        // Android APK
        notifyProgress("Building Android APK...")
        executeCommand("./gradlew assembleRelease")
        outputs.add(BuildOutput(
            file = File("app/build/outputs/apk/release/app-release.apk"),
            type = "android-apk",
            label = "Android APK"
        ))
        
        // Android AAB
        notifyProgress("Building Android AAB...")
        executeCommand("./gradlew bundleRelease")
        outputs.add(BuildOutput(
            file = File("app/build/outputs/bundle/release/app-release.aab"),
            type = "android-aab",
            label = "Android Bundle"
        ))
        
        // Electron
        notifyProgress("Building Electron apps...")
        executeCommand("cd electron && npm run build:all")
        outputs.add(BuildOutput(
            file = File("electron/dist/OmniJarvis-Setup.exe"),
            type = "electron-win",
            label = "Windows"
        ))
        outputs.add(BuildOutput(
            file = File("electron/dist/OmniJarvis.dmg"),
            type = "electron-mac",
            label = "macOS"
        ))
        outputs.add(BuildOutput(
            file = File("electron/dist/OmniJarvis.AppImage"),
            type = "electron-linux",
            label = "Linux"
        ))
        
        // Web
        notifyProgress("Building Web...")
        executeCommand("cd web && npm run build")
        outputs.add(BuildOutput(
            file = File("web/dist"),
            type = "web-static",
            label = "Web Build"
        ))
        
        // Docker
        notifyProgress("Building Docker image...")
        executeCommand("docker build -t omni-jarvis:$version .")
        outputs.add(BuildOutput(
            file = File("Dockerfile"),
            type = "docker",
            label = "Docker Image"
        ))
        
        return outputs
    }
    
    // ==================== PHASE 6: SIZE OPTIMIZE ====================
    
    private suspend fun optimizeSize(outputs: List<BuildOutput>): List<BuildOutput> {
        notifyProgress("📉 Optimizing size...")
        
        return outputs.map { output ->
            when (output.type) {
                "android-apk" -> optimizeApk(output)
                "android-aab" -> optimizeAab(output)
                "web-static" -> optimizeWeb(output)
                else -> output
            }
        }
    }
    
    private fun optimizeApk(output: BuildOutput): BuildOutput {
        // Run APK optimization
        executeCommand("zipalign -v -p 4 ${output.file.path} ${output.file.path}.aligned")
        
        // Sign with debug key (or release key)
        executeCommand("apksigner sign --ks keystore.jks ${output.file.path}.aligned")
        
        // Compress images inside APK
        val tempDir = File(context.cacheDir, "apk_extract")
        executeCommand("unzip ${output.file.path} -d ${tempDir.path}")
        
        // Optimize PNGs
        tempDir.walkTopDown().filter { it.extension == "png" }.forEach { png ->
            executeCommand("pngquant --quality=65-80 --speed 1 ${png.path}")
        }
        
        // Optimize WebPs
        tempDir.walkTopDown().filter { it.extension == "webp" }.forEach { webp ->
            executeCommand("cwebp -q 75 ${webp.path} -o ${webp.path}")
        }
        
        // Rebuild APK
        executeCommand("cd ${tempDir.path} && zip -r ${output.file.path}.optimized .")
        
        return output.copy(
            file = File("${output.file.path}.optimized"),
            originalSize = output.file.length(),
            optimizedSize = File("${output.file.path}.optimized").length()
        )
    }
    
    private fun optimizeWeb(output: BuildOutput): BuildOutput {
        val distDir = output.file
        
        // Minify JS further
        distDir.walkTopDown().filter { it.extension == "js" }.forEach { js ->
            executeCommand("terser ${js.path} -o ${js.path} --compress --mangle")
        }
        
        // Optimize CSS
        distDir.walkTopDown().filter { it.extension == "css" }.forEach { css ->
            executeCommand("csso ${css.path} --output ${css.path}")
        }
        
        // Compress images
        distDir.walkTopDown().filter { 
            it.extension in listOf("png", "jpg", "jpeg") 
        }.forEach { img ->
            when (img.extension) {
                "png" -> executeCommand("pngquant --quality=65-80 ${img.path}")
                "jpg", "jpeg" -> executeCommand("jpegoptim --size=80% ${img.path}")
            }
        }
        
        // Gzip static assets
        distDir.walkTopDown().filter { 
            it.extension in listOf("js", "css", "html", "svg") 
        }.forEach { file ->
            executeCommand("gzip -k ${file.path}")
        }
        
        return output
    }
    
    // ==================== PHASE 7: GITHUB RELEASE ====================
    
    private suspend fun createGitHubRelease(
        version: String,
        changelog: String,
        outputs: List<BuildOutput>
    ): String {
        notifyProgress("🚀 Creating GitHub release...")
        
        // Create tag
        git?.tag()?.setName("v$version")?.setMessage("Release v$version")?.call()
        git?.push()?.setPushTags()?.call()
        
        // Create release via API
        val releaseJson = JSONObject().apply {
            put("tag_name", "v$version")
            put("name", "OMNI JARVIS v$version")
            put("body", changelog)
            put("draft", false)
            put("prerelease", false)
        }
        
        val releaseUrl = "https://api.github.com/repos/$repoOwner/$repoName/releases"
        val releaseId = postGitHubApi(releaseUrl, releaseJson)
        
        // Upload assets
        for (output in outputs) {
            if (output.file.isFile) {
                uploadReleaseAsset(releaseId, output.file, output.label)
            }
        }
        
        return "https://github.com/$repoOwner/$repoName/releases/tag/v$version"
    }
    
    // ==================== PHASE 8: DEPLOY ====================
    
    private suspend fun deployAll(outputs: List<BuildOutput>, version: String) {
        notifyProgress("🌐 Deploying...")
        
        // Deploy web to GitHub Pages
        deployToGitHubPages(outputs.find { it.type == "web-static" })
        
        // Deploy to Firebase
        deployToFirebase(outputs.find { it.type == "android-apk" }, version)
        
        // Deploy Docker
        deployDocker(version)
        
        // Update website download links
        updateDownloadPage(version, outputs)
    }
    
    // ==================== PHASE 9: NOTIFY ====================
    // (notifySuccess is fully defined further below, near the notification helpers)
    
    // ==================== PHASE 10: MONITOR ====================
    
    private fun startMonitoring(version: String) {
        scope.launch {
            while (isActive) {
                delay(3600000) // Every hour
                
                // Check crash reports
                val crashes = checkCrashlytics(version)
                if (crashes > 0) {
                    notifyUser("⚠️ $crashes crashes detected in v$version")
                    
                    // Auto-create hotfix if critical
                    if (crashes > 10) {
                        triggerRelease(ReleaseMode.HOTFIX)
                    }
                }
                
                // Check performance
                val perf = checkPerformanceMetrics(version)
                if (perf.startupTime > 5000) {
                    notifyUser("⚠️ Startup time degraded: ${perf.startupTime}ms")
                }
            }
        }
    }
    
    // ==================== UTILITY FUNCTIONS ====================
    
    private fun getCurrentVersion(): String {
        return context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
    }
    
    private fun bumpVersion(current: String, type: String): String {
        val parts = current.split(".").map { it.toIntOrNull() ?: 0 }
        val major = parts.getOrElse(0) { 0 }
        val minor = parts.getOrElse(1) { 0 }
        val patch = parts.getOrElse(2) { 0 }

        return when (type) {
            "major" -> "${major + 1}.0.0"
            "minor" -> "$major.${minor + 1}.0"
            "patch" -> "$major.$minor.${patch + 1}"
            else -> "$major.$minor.${patch + 1}"
        }
    }

    private fun getLastTag(): String {
        return git?.tagList()?.call()?.lastOrNull()?.name?.removePrefix("refs/tags/") ?: "v0.0.0"
    }

    private fun getApkSize(): Long {
        val apk = File("app/build/outputs/apk/release/app-release.apk")
        return if (apk.exists()) apk.length() else 0
    }

    private fun getTotalSize(): String {
        val bytes = File(context.getExternalFilesDir(null), "project").walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }

        return when {
            bytes > 1024 * 1024 * 1024 -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
            bytes > 1024 * 1024 -> "%.2f MB".format(bytes / (1024.0 * 1024))
            else -> "%.2f KB".format(bytes / 1024.0)
        }
    }

    private fun executeCommand(command: String): Int {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            process.waitFor()
            val exitCode = process.exitValue()

            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()

            if (exitCode != 0 && error.isNotBlank()) {
                lastError = error.take(500)
            }

            exitCode
        } catch (e: Exception) {
            lastError = e.message ?: "Command execution failed"
            -1
        }
    }

    private var lastError: String? = null

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
            connection.errorStream?.bufferedReader()?.readText() ?: "{\"message\":\"HTTP $responseCode\"}"
        }

        if (responseCode !in 200..299) {
            throw Exception("GitHub API error: $responseCode - $response")
        }

        return JSONObject(response).optString("id", JSONObject(response).optString("url", ""))
    }

    private fun uploadReleaseAsset(releaseId: String, file: File, label: String) {
        val uploadUrl = "https://uploads.github.com/repos/$repoOwner/$repoName/releases/$releaseId/assets?name=${file.name}&label=${android.net.Uri.encode(label)}"

        val connection = URL(uploadUrl).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Authorization", "token $githubToken")
        connection.setRequestProperty("Content-Type", getContentType(file))
        connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
        connection.doOutput = true
        connection.connectTimeout = 300000
        connection.readTimeout = 600000

        file.inputStream().use { input ->
            connection.outputStream.use { output ->
                input.copyTo(output)
            }
        }

        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            val error = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
            throw Exception("Asset upload failed: $responseCode - $error")
        }
    }

    private fun getContentType(file: File): String {
        return when (file.extension.lowercase()) {
            "apk" -> "application/vnd.android.package-archive"
            "aab" -> "application/x-authorware-bin"
            "exe" -> "application/x-msdownload"
            "dmg" -> "application/x-apple-diskimage"
            "appimage" -> "application/x-executable"
            "zip" -> "application/zip"
            "tar" -> "application/x-tar"
            "gz", "tgz" -> "application/gzip"
            "js" -> "application/javascript"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "pdf" -> "application/pdf"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "mp3" -> "audio/mpeg"
            "ogg" -> "audio/ogg"
            "txt" -> "text/plain"
            "md" -> "text/markdown"
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            else -> "application/octet-stream"
        }
    }

    private fun executeHotfix() {
        scope.launch {
            try {
                notifyProgress("🔥 HOTFIX MODE")

                val changes = analyzeChanges()
                if (!changes.hasChanges) {
                    notifyUser("No changes to hotfix")
                    return@launch
                }

                val currentVersion = getCurrentVersion()
                val hotfixVersion = "$currentVersion-hotfix-${System.currentTimeMillis()}"

                notifyProgress("Creating hotfix: $hotfixVersion")

                val changelog = "## HOTFIX\n\nEmergency fix for critical issue.\n\nChanges:\n${changes.commitMessages.take(3).joinToString("\n") { "- $it" }}"

                val buildOutputs = buildProject(hotfixVersion)
                val optimizedOutputs = optimizeSize(buildOutputs)

                val releaseUrl = createGitHubRelease(hotfixVersion, changelog, optimizedOutputs)

                notifySuccess(hotfixVersion, releaseUrl, changelog)

            } catch (e: Exception) {
                notifyFailure(e)
            }
        }
    }

    private fun checkVulnerableDependencies(): Boolean {
        notifyProgress("Checking dependencies...")
        val result = executeCommand("./gradlew dependencyCheckAnalyze")
        return result == 0
    }

    private fun checkHardcodedKeys(): Boolean {
        val projectDir = File(context.getExternalFilesDir(null), "project")
        var hasIssues = false

        projectDir.walkTopDown()
            .filter { it.isFile && it.extension in listOf("kt", "java", "xml", "gradle", "properties") }
            .forEach { file ->
                val content = file.readText()
                val suspiciousPatterns = listOf(
                    Regex("""api[_-]?key\s*[=:]\s*["'][^"']{10,}["']""", RegexOption.IGNORE_CASE),
                    Regex("""password\s*[=:]\s*["'][^"']+["']""", RegexOption.IGNORE_CASE),
                    Regex("""secret\s*[=:]\s*["'][^"']{10,}["']""", RegexOption.IGNORE_CASE),
                    Regex("""token\s*[=:]\s*["'][^"']{10,}["']""", RegexOption.IGNORE_CASE)
                )

                for (pattern in suspiciousPatterns) {
                    if (pattern.containsMatchIn(content)) {
                        notifyUser("⚠️ Potential hardcoded secret in ${file.name}")
                        hasIssues = true
                    }
                }
            }

        return !hasIssues
    }

    private fun measureStartupTime(): Long {
        notifyProgress("Measuring startup time...")
        val start = System.currentTimeMillis()

        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)

        Thread.sleep(5000)

        return System.currentTimeMillis() - start
    }

    private fun measureMemoryUsage(): Long {
        val runtime = Runtime.getRuntime()
        runtime.gc()
        Thread.sleep(1000)
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
    }

    private fun checkCrashlytics(version: String): Int {
        return try {
            val url = "https://firebase.googleapis.com/v1beta1/projects/$repoName/crashlytics/releases/com.omnijarvis/$version"
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.setRequestProperty("Authorization", "Bearer $githubToken")

            val response = connection.inputStream.bufferedReader().readText()
            val json = JSONObject(response)
            json.optInt("crashCount", 0)
        } catch (e: Exception) {
            0
        }
    }

    private fun checkPerformanceMetrics(version: String): PerformanceMetrics {
        return PerformanceMetrics(
            startupTime = measureStartupTime(),
            memoryUsage = measureMemoryUsage(),
            apkSize = getApkSize(),
            installSize = getInstallSize()
        )
    }

    private fun getInstallSize(): Long {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return File(packageInfo.applicationInfo.sourceDir).length()
    }

    private fun deployToGitHubPages(webOutput: BuildOutput?) {
        webOutput ?: return

        notifyProgress("Deploying to GitHub Pages...")

        val webBuildDir = webOutput.file

        executeCommand("""
            cd ${webBuildDir.absolutePath} &&
            rm -rf .git &&
            git init &&
            git checkout -b gh-pages &&
            git add . &&
            git -c user.email="omni@jarvis.ai" -c user.name="OMNI JARVIS" commit -m "Deploy to GitHub Pages" &&
            git push --force https://$githubToken@github.com/$repoOwner/$repoName.git gh-pages
        """.trimIndent())
    }

    private fun deployToFirebase(apkOutput: BuildOutput?, version: String) {
        apkOutput ?: return

        notifyProgress("Deploying to Firebase...")

        executeCommand("""
            firebase appdistribution:distribute ${apkOutput.file.absolutePath} \
                --app 1:1234567890:android:abcdef \
                --release-notes "OMNI JARVIS v$version" \
                --groups "testers,alpha"
        """.trimIndent())
    }

    private fun deployDocker(version: String) {
        notifyProgress("Pushing Docker image...")

        executeCommand("docker tag omni-jarvis:$version $repoOwner/omni-jarvis:$version")
        executeCommand("docker push $repoOwner/omni-jarvis:$version")
        executeCommand("docker tag omni-jarvis:$version $repoOwner/omni-jarvis:latest")
        executeCommand("docker push $repoOwner/omni-jarvis:latest")
    }

    private fun updateDownloadPage(version: String, outputs: List<BuildOutput>) {
        val downloadPage = File(context.getExternalFilesDir(null), "project/docs/downloads.md")

        val content = buildString {
            appendLine("# Downloads")
            appendLine()
            appendLine("## Latest: v$version")
            appendLine()
            appendLine("| Platform | File | Size |")
            appendLine("|----------|------|------|")

            for (output in outputs) {
                if (output.file.isFile) {
                    val size = formatFileSize(output.optimizedSize.takeIf { it > 0 } ?: output.file.length())
                    appendLine("| ${output.label} | [${output.file.name}](https://github.com/$repoOwner/$repoName/releases/download/v$version/${output.file.name}) | $size |")
                }
            }
        }

        downloadPage.parentFile?.mkdirs()
        downloadPage.writeText(content)

        executeCommand("""
            cd ${downloadPage.parentFile?.absolutePath} &&
            git add downloads.md &&
            git -c user.email="omni@jarvis.ai" -c user.name="OMNI JARVIS" commit -m "Update downloads for v$version" &&
            git push https://$githubToken@github.com/$repoOwner/$repoName.git
        """.trimIndent())
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes > 1024 * 1024 * 1024 -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
            bytes > 1024 * 1024 -> "%.2f MB".format(bytes / (1024.0 * 1024))
            bytes > 1024 -> "%.2f KB".format(bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    private fun optimizeAab(output: BuildOutput): BuildOutput {
        notifyProgress("Optimizing AAB...")

        executeCommand("bundletool build-apks --bundle=${output.file.absolutePath} --output=${output.file.absolutePath}.apks --mode=universal")

        return output.copy(
            originalSize = output.file.length(),
            optimizedSize = output.file.length()
        )
    }

    private fun getSecureToken(): String {
        val masterKey = androidx.security.crypto.MasterKey.Builder(context)
            .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
            .build()

        val encryptedPrefs = androidx.security.crypto.EncryptedSharedPreferences.create(
            context,
            "secure_tokens",
            masterKey,
            androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        return encryptedPrefs.getString("github_token", "") ?: ""
    }

    private fun notifyProgress(message: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        val notification = android.app.Notification.Builder(context, "release_channel")
            .setContentTitle("OMNI Release")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .build()

        notificationManager.notify(9998, notification)

        android.util.Log.d("SelfRelease", message)
    }

    private fun notifyUser(message: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        val notification = android.app.Notification.Builder(context, "release_channel")
            .setContentTitle("OMNI JARVIS")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(9997, notification)
    }

    private fun notifySuccess(version: String, url: String, changelog: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
        val pendingIntent = android.app.PendingIntent.getActivity(context, 0, intent, android.app.PendingIntent.FLAG_IMMUTABLE)

        val notification = android.app.Notification.Builder(context, "release_channel")
            .setContentTitle("✅ Released: v$version")
            .setContentText("Tap to view on GitHub")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setStyle(android.app.Notification.BigTextStyle().bigText(changelog.take(200)))
            .build()

        notificationManager.notify(9996, notification)

        sendTelegram("""
            ✅ **OMNI JARVIS v$version Released!**

            📥 $url

            ${changelog.take(800)}
        """.trimIndent())

        sendWhatsAppToUser("New version v$version released! Check: $url")
    }

    private fun notifyFailure(error: Exception) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        val notification = android.app.Notification.Builder(context, "release_channel")
            .setContentTitle("❌ Release Failed")
            .setContentText(error.message ?: "Unknown error")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setStyle(android.app.Notification.BigTextStyle().bigText(lastError ?: error.stackTraceToString().take(500)))
            .setAutoCancel(true)
            .build()

        notificationManager.notify(9995, notification)

        sendTelegram("""
            ❌ **Release Failed**

            Error: ${error.message}
            Details: ${lastError ?: "None"}
        """.trimIndent())
    }

    private fun sendTelegram(message: String) {
        scope.launch {
            try {
                val botToken = getSecureToken() // Or separate telegram token
                val chatId = context.getSharedPreferences("config", Context.MODE_PRIVATE).getString("telegram_chat_id", "") ?: return@launch

                val url = "https://api.telegram.org/bot$botToken/sendMessage"
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true

                val json = JSONObject().apply {
                    put("chat_id", chatId)
                    put("text", message)
                    put("parse_mode", "Markdown")
                }

                connection.outputStream.use { it.write(json.toString().toByteArray()) }
            } catch (e: Exception) {
                android.util.Log.e("SelfRelease", "Telegram failed: ${e.message}")
            }
        }
    }

    private fun sendWhatsAppToUser(message: String) {
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            `package` = "com.whatsapp"
            putExtra(android.content.Intent.EXTRA_TEXT, message)
        }

        try {
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("SelfRelease", "WhatsApp not available: ${e.message}")
        }
    }

    private fun isAppVisible(): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val runningTasks = activityManager.getRunningTasks(1)
        return runningTasks.firstOrNull()?.topActivity?.packageName == context.packageName
    }
}

data class ChangeAnalysis(
    val hasChanges: Boolean,
    val modified: Int = 0,
    val added: Int = 0,
    val removed: Int = 0,
    val hasBreakingChange: Boolean = false,
    val hasFeature: Boolean = false,
    val hasFix: Boolean = false,
    val commitMessages: List<String> = emptyList()
)

data class BuildOutput(
    val file: File,
    val type: String,
    val label: String,
    val originalSize: Long = 0,
    val optimizedSize: Long = 0
)

data class PerformanceMetrics(
    val startupTime: Long,
    val memoryUsage: Long,
    val apkSize: Long,
    val installSize: Long
)

enum class ReleaseMode { AUTO, PATCH, MINOR, MAJOR, HOTFIX }
