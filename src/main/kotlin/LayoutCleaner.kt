package org.example

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun main() {
    val dryRun = false // Change to false to actually delete files
    val projectDir = File("/Users/nizamuddinahmed/AndroidProject/vantagecircle-android-cleanup")
    val layoutDir = File(projectDir, "app/src/main/res/layout")

    var phase = 1
    var totalDeleted = 0

    while (true) {
        println("\n🔁 PHASE-$phase: Scanning & Deleting Unused Layouts...")
        val deletedThisRound = runPhase(layoutDir, projectDir, dryRun)
        totalDeleted += deletedThisRound

        if (deletedThisRound == 0) {
            println("🛑 No more deletions. Cleanup complete.")
            break
        }

        phase++
    }

    println("\n✅ Total layouts deleted: $totalDeleted")
}

fun runPhase(layoutDir: File, projectDir: File, dryRun: Boolean): Int {
    val allLayoutFiles = layoutDir.walkTopDown()
        .filter { it.isFile && it.extension == "xml" }
        .toList()

    val codeFiles = projectDir.walkTopDown()
        .filter {
            it.isFile &&
                    it.extension in listOf("kt", "java", "xml") &&
                    !it.name.equals("AndroidManifest.xml", ignoreCase = true) &&
                    !it.inTestDirectory()
        }
        .toList()

    val sourceTextMap = codeFiles.associateWith { it.readTextOrEmpty() }

    var deletedCount = 0
    val usedLayouts = mutableSetOf<String>()
    val unusedLayouts = mutableListOf<File>()

    println("🔍 Total layouts: ${allLayoutFiles.size}")
    println("📄 Scanning files: ${codeFiles.size}")

    allLayoutFiles.forEachIndexed { index, layoutFile ->
        val layoutName = layoutFile.nameWithoutExtension
        val percent = ((index + 1) * 100.0 / allLayoutFiles.size).toInt()

        val isUsed = sourceTextMap.any { (_, content) ->
            content.contains("R.layout.$layoutName") || content.contains("""@layout/$layoutName""")
        }

        if (isUsed) {
            println("[${"%3d".format(percent)}%] ✅ Used: ${layoutFile.name}")
            usedLayouts.add(layoutFile.name)
        } else {
            println("[${"%3d".format(percent)}%] 🗑️ Unused: ${layoutFile.name} → Deleted")
            unusedLayouts.add(layoutFile)
            deletedCount++
            if (!dryRun) layoutFile.delete()
        }
    }

    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
    val reportFile = File(projectDir, "layout_cleanup_phase_report_$timestamp.txt")

    reportFile.printWriter().use { out ->
        out.println("🧾 Layout Cleanup Phase Report (${if (dryRun) "DRY RUN" else "DELETION"})")
        out.println("Generated: $timestamp\n")

        out.println("📊 Summary:")
        out.println(" - Total scanned: ${allLayoutFiles.size}")
        out.println(" - Used: ${usedLayouts.size}")
        out.println(" - Deleted: ${unusedLayouts.size}\n")

        out.println("✅ USED Layouts:")
        usedLayouts.sorted().forEach { out.println(" - $it") }

        out.println("\n🗑️ DELETED Layouts:")
        unusedLayouts.map { it.name }.sorted().forEach { out.println(" - $it") }
    }

    println("📄 Report saved: ${reportFile.absolutePath}")
    return deletedCount
}



