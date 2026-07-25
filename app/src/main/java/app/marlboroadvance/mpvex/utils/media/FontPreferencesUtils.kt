package app.marlboroadvance.mpvex.utils.media

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Log
import com.github.k1rakishou.fsaf.FileManager
import com.yubyf.truetypeparser.TTFFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/** Copies font files from the selected directory to the app's internal storage. */
@SuppressLint("UseKtx")
fun copyFontsFromDirectory(
  context: Context,
  fileManager: FileManager,
  uriString: String,
) {
  runCatching {
    val destinationPath = context.filesDir.path + "/fonts"
    val destinationDir = fileManager.fromPath(destinationPath)

    if (!fileManager.exists(destinationDir)) {
      File(destinationPath).mkdirs()
    }

    val sourceDir = fileManager.fromUri(Uri.parse(uriString))
    if (sourceDir != null && fileManager.exists(sourceDir)) {
      fileManager.listFiles(sourceDir).forEach { file ->
        if (fileManager.isFile(file)) {
          // Names come from a user-picked SAF tree, so never trust them as a
          // path — sanitizeFontFileName() reduces them to a plain file name
          // inside filesDir/fonts and rejects unsupported extensions.
          val fileName = SubtitleFontUtils.sanitizeFontFileName(fileManager.getName(file)) ?: return@forEach
          val inputStream = fileManager.getInputStream(file) ?: return@forEach
          val outputFile = File(destinationPath, fileName)
          outputFile.outputStream().use { outputStream ->
            inputStream.use { it.copyTo(outputStream) }
          }
        }
      }
    }
  }.onFailure { e ->
    Log.e("SubtitlesPreferences", "Error copying fonts", e)
  }

  // Keep the family cache in sync — this runs on a background thread, so it is
  // safe to reparse here rather than on the UI thread during playback.
  SubtitleFontUtils.refreshFamilyCache(context)
}

// getSimplifiedPathFromUri is defined in AdvancedPreferencesScreen.kt within this package.

/** Represents a custom font discovered in the app's internal fonts directory. */
data class CustomFontEntry(
  val familyName: String,
  val file: File,
)

/**
 * Loads custom fonts (family name + file) from the app's internal fonts directory for previews.
 * Returns one entry per family name (first match kept if duplicates exist).
 */
suspend fun loadCustomFontEntries(context: Context): List<CustomFontEntry> =
  withContext(Dispatchers.IO) {
    val fontsDir = File(context.filesDir, "fonts")
    if (!fontsDir.exists()) return@withContext emptyList()

    val fontFiles =
      fontsDir
        .listFiles()
        ?.filter { it.isFile && it.name.lowercase(Locale.ROOT).matches(SubtitleFontUtils.FONT_FILE_REGEX) }
        .orEmpty()

    val entries = mutableListOf<CustomFontEntry>()
    val seenFamilies = mutableSetOf<String>()

    for (fontFile in fontFiles) {
      val familyName =
        runCatching {
          fontFile.inputStream().use { input ->
            TTFFile
              .open(input)
              .families
              .values
              .firstOrNull()
          }
        }.getOrNull()

      if (!familyName.isNullOrBlank() && seenFamilies.add(familyName)) {
        entries += CustomFontEntry(familyName, fontFile)
      }
    }

    entries.sortedBy { it.familyName }
  }
