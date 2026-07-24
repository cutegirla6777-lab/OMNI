package com.omnijarvis.workflow

import android.content.Context
import java.io.File

object SizeReporter {

    data class SizeReport(
        val fileName: String,
        val bytes: Long,
        val readable: String,
        val breakdown: Map<String, Long>?
    )

    fun analyzeApkSize(apkFile: File, context: Context): SizeReport {
        // Extract APK and analyze real contents
        val extractDir = File(context.cacheDir, "apk_analysis_${System.currentTimeMillis()}")
        extractDir.mkdirs()

        // Unzip APK
        java.util.zip.ZipFile(apkFile).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val outFile = File(extractDir, entry.name)
                outFile.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input ->
                    outFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }

        // Analyze by category
        val breakdown = mutableMapOf<String, Long>()

        extractDir.walkTopDown().filter { it.isFile }.forEach { file ->
            val category = when {
                file.extension == "dex" -> "DEX (Code)"
                file.extension == "so" -> "Native Libs (${file.parentFile?.name ?: "unknown"})"
                file.extension in listOf("png", "jpg", "jpeg", "webp", "gif") -> "Images"
                file.extension in listOf("xml") && file.path.contains("res") -> "Resources"
                file.extension == "xml" -> "XML Config"
                file.extension == "json" -> "JSON Assets"
                file.extension == "ttf" || file.extension == "otf" -> "Fonts"
                file.name == "AndroidManifest.xml" -> "Manifest"
                file.extension == "arsc" -> "Resources Table"
                else -> "Other (${file.extension})"
            }

            breakdown[category] = (breakdown[category] ?: 0) + file.length()
        }

        // Cleanup
        extractDir.deleteRecursively()

        return SizeReport(
            fileName = apkFile.name,
            bytes = apkFile.length(),
            readable = formatBytes(apkFile.length()),
            breakdown = breakdown.toList().sortedByDescending { it.second }.toMap()
        )
    }

    fun generateSizeReport(outputs: List<AutoReleaseWorkflow.SizedBuildOutput>): String {
        val sb = StringBuilder()

        sb.appendLine("## 📊 Honest Size Report")
        sb.appendLine()
        sb.appendLine("| File | Actual Size |")
        sb.appendLine("|------|-------------|")

        outputs.forEach { output ->
            sb.appendLine("| ${output.file.name} | **${output.actualSizeString}** |")
        }

        sb.appendLine()

        // Detailed breakdown for APK
        outputs.find { it.type == "android-apk" }?.let { apk ->
            sb.appendLine("### APK Breakdown")
            sb.appendLine("```")
            // Would need to run analyzeApkSize here if context available
            sb.appendLine("Run with context for detailed breakdown")
            sb.appendLine("```")
        }

        sb.appendLine()
        sb.appendLine("*All sizes are actual build outputs. No compression or optimization applied.*")

        return sb.toString()
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024L * 1024 * 1024 -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
            bytes >= 1024L * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024))
            bytes >= 1024L -> String.format("%.2f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}
