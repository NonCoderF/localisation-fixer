package org.example

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import java.io.FileFilter
import java.io.*
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.*

fun main() {
    val projectDir = File("/Users/nizamuddinahmed/AndroidProject/vantagecircle-android-cleanup")
    val resDir = File(projectDir, "app/src/main/res")

    //Find and delete used string
    findAndDeleteUnusedStrings(resDir, projectDir, dryRun = true)

    // Fixing `%@` using base english language
    fixPlaceholdersPhase1Smart(resDir)

    //Consistency check
    fixPlaceholdersPhase2Smart(resDir, dryRun = true)

    //Untranslated strings
    fixPlaceholdersPhase3Smart(resDir, dryRun = true)

}

enum class EntryType { STRING, PLURALS }

data class StringEntry(
    val key: String,
    val type: EntryType,
    val content: Element,
    val pluralItems: List<Element> = emptyList()
)

fun parseStringsList(file: File): List<StringEntry> {
    val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
    val root = doc.documentElement
    val entries = mutableListOf<StringEntry>()

    val children = root.childNodes
    for (i in 0 until children.length) {
        val node = children.item(i)
        if (node.nodeType != Node.ELEMENT_NODE) continue
        val elem = node as Element
        val name = elem.getAttribute("name") ?: continue

        when (elem.tagName) {
            "string" -> entries.add(StringEntry(name, EntryType.STRING, elem))
            "plurals" -> {
                val pluralItems = mutableListOf<Element>()
                val items = elem.getElementsByTagName("item")
                for (j in 0 until items.length) {
                    val item = items.item(j)
                    if (item is Element) pluralItems.add(item)
                }
                entries.add(StringEntry(name, EntryType.PLURALS, elem, pluralItems))
            }
        }
    }
    return entries
}

fun buildXmlContent(entries: List<StringEntry>): String {
    val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
    val resources = doc.createElement("resources")
    doc.appendChild(resources)

    for (entry in entries) {
        val node = when (entry.type) {
            EntryType.STRING -> doc.importNode(entry.content, true)
            EntryType.PLURALS -> {
                val plurals = doc.createElement("plurals")
                plurals.setAttribute("name", entry.key)
                entry.pluralItems.forEach { item ->
                    val newItem = doc.createElement("item")
                    newItem.setAttribute("quantity", item.getAttribute("quantity"))
                    newItem.textContent = item.textContent
                    plurals.appendChild(newItem)
                }
                plurals
            }
        }
        resources.appendChild(node)
    }

    val tf = TransformerFactory.newInstance().newTransformer()
    tf.setOutputProperty(OutputKeys.INDENT, "yes")
    tf.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4")
    val sw = StringWriter()
    tf.transform(DOMSource(doc), StreamResult(sw))
    return sw.toString()
}

fun findAndDeleteUnusedStrings(resDir: File, projectRoot: File, dryRun: Boolean = true) {
    println("🧼 Phase 4: Detecting and deleting unused strings & plurals")

    val valuesDirs = resDir.listFiles(FileFilter { it.isDirectory && it.name.startsWith("values") }) ?: return
    val baseDir = valuesDirs.find { it.name == "values" } ?: return
    val baseStringsFile = File(baseDir, "strings.xml")
    if (!baseStringsFile.exists()) {
        println("❌ Base strings.xml not found.")
        return
    }

    val allEntries = parseStringsList(baseStringsFile)
    val allKeys = allEntries.map { it.key }.toSet()

    val allFiles = projectRoot.walkTopDown()
        .filter {
            it.isFile &&
                    (it.extension == "kt" || it.extension == "java" || it.extension == "xml") &&
                    !it.path.contains("/res/values") &&
                    !it.path.contains("/build/") &&
                    !it.name.equals("AndroidManifest.xml")
        }.toList()

    val usedKeys = mutableSetOf<String>()
    val regexMap = allKeys.associateWith { key ->
        listOf(
            Regex("""R\.string\.$key\b"""),
            Regex("""@string/$key\b"""),
            Regex("""R\.plurals\.$key\b"""),
            Regex("""@plurals/$key\b""")
        )
    }

    for ((index, file) in allFiles.withIndex()) {
        val content = file.readText()
        for ((key, regexes) in regexMap) {
            if (regexes.any { it.containsMatchIn(content) }) {
                usedKeys.add(key)
            }
        }
        val progress = (index + 1) * 100 / allFiles.size
        print("\r🔍 Progress: $progress% - Scanning: ${file.name}")
    }

    println("\n✅ Scan complete. Used: ${usedKeys.size}, Unused: ${allKeys.size - usedKeys.size}")

    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
    val reportFile = File(resDir, "unused_strings_report_$timestamp.txt")

    val unusedKeys = allKeys - usedKeys
    val usedEntries = allEntries.filter { it.key in usedKeys }
    val unusedEntries = allEntries.filter { it.key in unusedKeys }

    reportFile.printWriter().use { out ->
        out.println("📄 Unused Strings Report (Phase 4)")
        out.println("Generated on: $timestamp\n")

        out.println("🗑️ Unused keys (${unusedEntries.size}):")
        for (entry in unusedEntries.sortedBy { it.key }) {
            out.println("- ${entry.key} [${entry.type}]")
        }

        out.println("\n✅ Used keys (${usedEntries.size}):")
        for (entry in usedEntries.sortedBy { it.key }) {
            out.println("+ ${entry.key} [${entry.type}]")
        }
    }

    if (!dryRun) {
        println("🧽 Cleaning unused keys from all translations...")
        for (dir in valuesDirs) {
            val stringsFile = File(dir, "strings.xml")
            if (!stringsFile.exists()) continue

            val entries = parseStrings(stringsFile)
            val cleaned = entries.filter { it.key in usedKeys }
            val content = buildXmlContent(cleaned)
            stringsFile.writeText(content)
        }
        println("✅ Unused entries removed from all translations.")
    } else {
        println("🧪 Dry run complete. No files were modified.")
    }

    println("📄 Report saved to: ${reportFile.absolutePath}")
}


fun fixPlaceholdersPhase1Smart(resDir: File) {
    println("🛠️ Phase 1 (Smart): Fixing `%@` using base language placeholders")

    val valuesDirs = resDir.listFiles(FileFilter { file ->
        file.isDirectory && file.name.startsWith("values")
    }) ?: return

    val baseDir = valuesDirs.find { it.name == "values" } ?: return
    val baseStrings = parseStrings(File(baseDir, "strings.xml"))

    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
    val reportFile = File(resDir, "smart_placeholder_fix_report_$timestamp.txt")

    var totalFixes = 0
    var filesModified = 0

    reportFile.printWriter().use { out ->
        out.println("📄 Phase 1 Report – Smart Placeholder Fix (%@ → correct %s/%d/etc.)")
        out.println("Generated: $timestamp\n")

        valuesDirs.filter { it.name != "values" }.forEach { langDir ->
            val stringsFile = File(langDir, "strings.xml")
            if (!stringsFile.exists()) return@forEach

            val strings = parseStrings(stringsFile)
            var changed = false

            val updatedContent = strings.mapValues { (key, value) ->
                if ("%@" !in value) return@mapValues value
                val baseValue = baseStrings[key] ?: return@mapValues value

                val basePlaceholders = Regex("%[sdfe]").findAll(baseValue).map { it.value }.toList()
                val translatedParts = value.split("%@")
                val rebuilt = StringBuilder()
                for (i in translatedParts.indices) {
                    rebuilt.append(translatedParts[i])
                    if (i < basePlaceholders.size) {
                        rebuilt.append(basePlaceholders[i])
                        totalFixes++
                        changed = true
                        out.println("✅ Fixed [$key] in ${langDir.name}: %@ → ${basePlaceholders[i]}")
                    }
                }
                rebuilt.toString()
            }

            if (changed) {
                filesModified++
                // Backup
                val backupFile = File(langDir, "strings_backup_phase1.xml")
                if (!backupFile.exists()) stringsFile.copyTo(backupFile)

                // Rewrite updated XML
                val newContent = buildXmlContent(updatedContent)
                stringsFile.writeText(newContent)
            }
        }

        out.println("\n📊 Summary:")
        out.println(" - Files modified: $filesModified")
        out.println(" - Total replacements: $totalFixes")
    }

    println("✅ Done! $totalFixes replacements made in $filesModified files.")
    println("📄 Report saved to: ${reportFile.absolutePath}")
}

fun fixPlaceholdersPhase2Smart(resDir: File, dryRun: Boolean = true) {
    println("⚙️ Phase 2 (Consistency): Checking placeholder inconsistencies against base translations...")

    val valuesDirs = resDir.listFiles(FileFilter { it.isDirectory && it.name.startsWith("values") }) ?: return
    val baseDir = valuesDirs.find { it.name == "values" } ?: return
    val baseStrings = parseStrings(File(baseDir, "strings.xml"))

    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
    val reportFile = File(resDir, "placeholder_inconsistency_report_$timestamp.txt")

    var totalIssues = 0

    reportFile.printWriter().use { out ->
        out.println("⚠️ Phase 2 Report – Placeholder Inconsistencies")
        out.println("Generated on: $timestamp\n")

        valuesDirs.filter { it.name != "values" }.forEach { langDir ->
            val stringsFile = File(langDir, "strings.xml")
            if (!stringsFile.exists()) return@forEach

            val strings = parseStrings(stringsFile)
            var foundIssue = false

            for ((key, translated) in strings) {
                val base = baseStrings[key] ?: continue
                val basePlaceholders = extractPlaceholders(base)
                val transPlaceholders = extractPlaceholders(translated)

                if (basePlaceholders != transPlaceholders) {
                    if (!foundIssue) {
                        out.println("🌐 Language: ${langDir.name}")
                        foundIssue = true
                    }
                    out.println("🔸 Mismatch in key: \"$key\"")
                    out.println("   Base : ${basePlaceholders.joinToString(", ")}")
                    out.println("   Trans: ${transPlaceholders.joinToString(", ")}\n")
                    totalIssues++

                    // No fix in dryRun mode
                    if (!dryRun) {
                        // Later we can add logic to sync placeholder pattern if needed
                    }
                }
            }
        }

        out.println("\nTotal inconsistent entries: $totalIssues")
    }

    println("\n✅ Done! Inconsistency report saved to ${reportFile.absolutePath}")
}

fun fixPlaceholdersPhase3Smart(resDir: File, dryRun: Boolean = true) {
    println("⚙️ Phase 3 (Translation Check): Detecting untranslated (copied) strings...")

    val valuesDirs = resDir.listFiles(FileFilter { it.isDirectory && it.name.startsWith("values") }) ?: return
    val baseDir = valuesDirs.find { it.name == "values" } ?: return
    val baseStrings = parseStrings(File(baseDir, "strings.xml"))

    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
    val reportFile = File(resDir, "untranslated_strings_report_$timestamp.txt")

    var totalIssues = 0

    reportFile.printWriter().use { out ->
        out.println("⚠️ Phase 3 Report – Untranslated Strings (Copied English)")
        out.println("Generated on: $timestamp\n")

        valuesDirs.filter { it.name != "values" }.forEach { langDir ->
            val stringsFile = File(langDir, "strings.xml")
            if (!stringsFile.exists()) return@forEach

            val strings = parseStrings(stringsFile)
            var foundIssue = false

            for ((key, translated) in strings) {
                val base = baseStrings[key] ?: continue
                if (translated.trim() == base.trim()) {
                    if (!foundIssue) {
                        out.println("🌐 Language: ${langDir.name}")
                        foundIssue = true
                    }
                    out.println("🔸 Copied key: \"$key\"")
                    out.println("   Value: \"$translated\"\n")
                    totalIssues++

                    // If not dryRun, we can remove or comment it
                    if (!dryRun) {
                        // Optional fix: mark for manual review, clear value, or remove key
                        // Add to a list of keys to strip if needed
                    }
                }
            }
        }

        out.println("\nTotal untranslated entries: $totalIssues")
    }

    println("\n✅ Done! Copied-string report saved to ${reportFile.absolutePath}")
}

fun parseStrings(file: File): Map<String, String> {
    val result = mutableMapOf<String, String>()
    if (!file.exists()) return result

    try {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodeList = doc.getElementsByTagName("string")
        for (i in 0 until nodeList.length) {
            val node = nodeList.item(i)
            if (node is Element) {
                val key = node.getAttribute("name")
                val value = node.textContent
                result[key] = value
            }
        }
    } catch (e: Exception) {
        println("⚠️ Failed to parse: ${file.absolutePath}")
    }

    return result
}

fun buildXmlContent(strings: Map<String, String>): String {
    val builder = StringBuilder()
    builder.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n")
    strings.forEach { (key, value) ->
        val escaped = value
            .replace("\"", "\\\"")
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
        builder.append("    <string name=\"$key\">$escaped</string>\n")
    }
    builder.append("</resources>\n")
    return builder.toString()
}

fun extractPlaceholders(text: String): List<String> {
    val regex = Regex("%[\\d$]*[sdfeEgG]")
    return regex.findAll(text).map { it.value }.toList()
}