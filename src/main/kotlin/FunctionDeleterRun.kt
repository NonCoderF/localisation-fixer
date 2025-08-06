package org.example

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun main() {
    val dryRun = true // change to false if you want actual deletion

    val projectDir = File("/Users/nizamuddinahmed/AndroidProject/vantagecircle-android-cleanup")
    val excludedFunctions = setOf("main", "onCreate", "onStart", "onResume", "onDestroy", "toString", "equals", "hashCode")

    val allFiles = projectDir.walkTopDown()
        .filter { file ->
            file.isFile &&
                    file.extension in listOf("kt", "java") &&
                    file.name != "AndroidManifest.xml" &&
                    !file.inTestDirectory()
        }
        .toList()

    val fileContents = allFiles.associateWith { file ->
        try {
            file.readText()
        } catch (e: Exception) {
            ""
        }
    }

    val total = allFiles.size
    var currentIndex = 0
    val deletedFunctions = mutableListOf<String>()

    allFiles.forEach { file ->
        currentIndex++
        val content = fileContents[file] ?: return@forEach
        val lines = content.lines().toMutableList()
        var modified = false

        val updatedContent = StringBuilder()
        val fileName = file.name
        var i = 0

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            val functionMatch = Regex("""^(?!.*override)(fun|void)\s+([a-zA-Z0-9_]+)\s*\(""").find(trimmed)
            val isFunction = functionMatch != null
            val functionName = functionMatch?.groupValues?.get(2)

            if (isFunction && functionName !in excludedFunctions) {
                val percent = ((currentIndex * 100.0) / total).toInt()
                val isUsed = fileContents.any { (otherFile, otherContent) ->
                    if (otherFile == file) return@any false
                    Regex("""\b$functionName\s*\(""").containsMatchIn(otherContent)
                }

                if (!isUsed) {
                    println("[${"%3d".format(percent)}%] ❌ Unused: $fileName → fun $functionName()")
                    deletedFunctions.add("$fileName → fun $functionName()")
                    modified = true

                    if (dryRun) {
                        // Keep original block, just log
                        var braceCount = 0
                        while (i < lines.size) {
                            val currentLine = lines[i]
                            updatedContent.appendLine(currentLine)
                            braceCount += currentLine.count { it == '{' }
                            braceCount -= currentLine.count { it == '}' }

                            i++
                            if (braceCount <= 0 && currentLine.contains("}")) break
                        }
                        continue
                    } else {
                        // Delete block
                        var braceCount = 0
                        while (i < lines.size) {
                            val currentLine = lines[i]
                            braceCount += currentLine.count { it == '{' }
                            braceCount -= currentLine.count { it == '}' }
                            i++
                            if (braceCount <= 0 && currentLine.contains("}")) break
                        }
                        continue
                    }
                } else {
                    println("[${"%3d".format(percent)}%] ➤ Checking: $fileName → fun $functionName() (USED)")
                }
            }

            updatedContent.appendLine(line)
            i++
        }

        if (!dryRun && modified) {
            file.writeText(updatedContent.toString())
        }
    }

    // Save report
    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
    val reportFile = File(projectDir, "unused_functions_${if (dryRun) "dryrun" else "deleted"}_report_$timestamp.txt")

    reportFile.printWriter().use { out ->
        out.println("🧾 Unused Function Report (${if (dryRun) "DRY RUN" else "DELETION"})")
        out.println("Generated: $timestamp")
        out.println("Project: ${projectDir.absolutePath}")
        out.println("Total unused functions found: ${deletedFunctions.size}\n")

        if (deletedFunctions.isEmpty()) {
            out.println("✅ No unused functions found.")
        } else {
            out.println("Functions:")
            deletedFunctions.forEach { out.println(" - $it") }
        }
    }

    println("\n✅ Scan complete! ${deletedFunctions.size} unused functions ${if (dryRun) "found" else "deleted"}.")
    println("📄 Report saved to: ${reportFile.absolutePath}")
}

// Extension function to check if file is in test or androidTest
fun File.inTestDirectory(): Boolean {
    val pathLower = this.absolutePath.lowercase()
    return pathLower.contains("/test/") || pathLower.contains("/androidtest/")
}
