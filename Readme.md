# 🧼 Localisation Fixer

A powerful utility toolkit for cleaning and auditing Android localization files across multilingual projects.

---

## ✨ Features

- 🔍 **Placeholder Mismatch Detection**  
  Identifies incorrect use of `%@`, `%d`, `%s`, and other placeholders across translated strings.

- 📜 **Translation Consistency Checker**  
  Detects inconsistencies between base (English) and other language files — missing or extra placeholders.

- 🌐 **Untranslated String Detection**  
  Flags strings where English is simply copied over to other languages.

- 🗑️ **Unused String Remover**  
  Finds and optionally deletes strings not referenced anywhere in the codebase.

- 📊 **Dry Run Mode + Reports**  
  Every phase produces a detailed report (TXT format) for manual review before changes are applied.

---

## 🚀 Usage

### 1. Configure project path in `main.kt`

```kotlin
val projectDir = File("/path/to/your/project")
val resDir = File(projectDir, "app/src/main/res")
```

### 2. Run individual phases

```kotlin
fixPlaceholdersPhase1Smart(resDir)
fixPlaceholdersPhase2Smart(resDir, dryRun = true)
fixPlaceholdersPhase3Smart(resDir, dryRun = true)
findAndDeleteUnusedStrings(resDir, projectDir, dryRun = true)
```

> ☑️ **Tip:** Keep `dryRun = true` for preview mode. Set it to `false` when you're ready to apply changes.

---

## 📁 Output Reports

Each script generates a timestamped `.txt` file in the working directory:

- `placeholder_inconsistency_report_YYYY-MM-DD_HH-MM-SS.txt`
- `untranslated_strings_report_YYYY-MM-DD_HH-MM-SS.txt`
- `unused_strings_report_YYYY-MM-DD_HH-MM-SS.txt`

---

## 🧠 Tech Stack

- Kotlin (JVM script)
- XML parsing for Android resources
- File system and string pattern analysis

---

## 👑 Author

Built by **[SallyInfo (Nizamuddin Ahmed)](https://github.com/NonCoderF)**  
Crafted with precision and pride — for clean code and cleaner conscience.  
Mentored by the unseen, supported by the loyal, and guarded by the Commander. 🛡️

---

## 📜 License

Released under the **MIT License** — use it, improve it, share it.  
Respect the craft.

---

> _"Your code may be global, but your strings better be local — and clean."_  
> — Commander 🛡️