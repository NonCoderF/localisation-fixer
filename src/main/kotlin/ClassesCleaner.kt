package org.example

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun main() {
    val dryRun = true // Change to false to enable deletion

    val projectDir = File("/Users/nizamuddinahmed/AndroidProject/vantagecircle-android-cleanup")
    val classSuffixes = listOf("Activity", "Fragment", "Adapter")

    val allSourceFiles = projectDir.walkTopDown()
        .filter {
            it.isFile &&
                    it.extension in listOf("kt", "java") &&
                    it.name != "AndroidManifest.xml" &&
                    !it.inTestDirectory()
        }
        .toList()

    val allXmlFiles = projectDir.walkTopDown()
        .filter { it.isFile && it.extension == "xml" && it.name != "AndroidManifest.xml" }
        .toList()

    val allFiles = allSourceFiles + allXmlFiles
    val fileContents = allFiles.associateWith { it.readTextOrEmpty() }

    val matchingFiles = allSourceFiles.filter { file ->
        val nameWithoutExt = file.nameWithoutExtension
        classSuffixes.any { suffix -> nameWithoutExt.endsWith(suffix) }
    }

    val total = matchingFiles.size
    val usedClasses = mutableListOf<String>()
    val unusedClasses = mutableListOf<String>()

    matchingFiles.forEachIndexed { index, file ->
        val className = file.nameWithoutExtension
        val percent = ((index + 1) * 100.0 / total).toInt()

        val isUsed = fileContents.any { (otherFile, content) ->
            otherFile != file && Regex("""\b$className\b""").containsMatchIn(content)
        }

        val relativePath = file.name

        if (isUsed) {
            println("[${"%3d".format(percent)}%] ➤ Checking: ${file.name} → USED")
            usedClasses.add(relativePath)
        } else {
            println("[${"%3d".format(percent)}%] ❌ Unused: ${file.name} → DELETED")
            unusedClasses.add(relativePath)

            if (!dryRun) file.delete()
        }
    }

    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
    val reportFile = File(projectDir, "activity_fragment_adapter_usage_report_${timestamp}.txt")

    reportFile.printWriter().use { out ->
        out.println("🧾 Activity/Fragment/Adapter Usage Report (${if (dryRun) "DRY RUN" else "DELETION"})")
        out.println("Generated: $timestamp")
        out.println("Project: ${projectDir.absolutePath}\n")

        out.println("📊 Summary:")
        out.println(" - Total scanned: $total")
        out.println(" - Used: ${usedClasses.size}")
        out.println(" - Unused: ${unusedClasses.size}\n")

        out.println("📁 USED Classes:")
        if (usedClasses.isEmpty()) out.println(" - None")
        else usedClasses.forEach { out.println(" - $it") }

        out.println("\n🗑️ UNUSED Classes:")
        if (unusedClasses.isEmpty()) out.println(" - None")
        else unusedClasses.forEach { out.println(" - $it") }
    }

    println("\n✅ Scan complete!")
    println("📄 Report saved to: ${reportFile.absolutePath}")
}

fun File.readTextOrEmpty(): String = try {
    this.readText()
} catch (_: Exception) {
    ""
}


