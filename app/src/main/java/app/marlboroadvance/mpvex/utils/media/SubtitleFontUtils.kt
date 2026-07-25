package app.marlboroadvance.mpvex.utils.media

import android.content.Context
import android.util.Log
import com.yubyf.truetypeparser.TTFFile
import `is`.xyz.mpv.MPVLib
import java.io.File
import java.util.Locale

/**
 * Utilities for bundled/imported subtitle fonts and font-weight resolution.
 *
 * The app ships Inter (all 9 static weights) and Momo Trust Display in
 * assets/fonts and extracts them to filesDir/fonts on startup, where mpv's
 * `sub-fonts-dir` option picks them up.
 *
 * Font formats supported by libass (and therefore by this app):
 * TTF, OTF and TTC. Web formats (WOFF/WOFF2/EOT) are NOT supported.
 */
object SubtitleFontUtils {
  private const val TAG = "SubtitleFontUtils"

  /** Default subtitle font family bundled with the app. */
  const val DEFAULT_FONT_FAMILY = "Inter 18pt"

  /** Bundled alternative font (nice for anime). */
  const val MOMO_FONT_FAMILY = "Momo Trust Display"

  /** Extensions accepted when importing external font files. */
  val SUPPORTED_FONT_EXTENSIONS = setOf("ttf", "otf", "ttc", "otc")

  /** Regex matching supported font files (used for directory listings). */
  val FONT_FILE_REGEX = ".*\\.(ttf|otf|ttc|otc)$".toRegex()

  /**
   * CSS-style weight -> subfamily name used by most font vendors.
   * 400 maps to the base family itself and 700 maps to the family's Bold face
   * (handled via libass' bold attribute), so both are absent here.
   */
  private val WEIGHT_SUFFIXES =
    mapOf(
      100 to "Thin",
      200 to "ExtraLight",
      300 to "Light",
      500 to "Medium",
      600 to "SemiBold",
      800 to "ExtraBold",
      900 to "Black",
    )

  /** Human-readable label for a slider value (used as valueText). */
  fun weightLabel(weight: Int): String =
    when (weight) {
      400 -> "Regular"
      700 -> "Bold"
      else -> WEIGHT_SUFFIXES[weight] ?: weight.toString()
    }

  @Volatile
  private var cachedFamilies: Set<String>? = null

  /**
   * Extracts the fonts bundled in assets/fonts to filesDir/fonts so mpv's
   * sub-fonts-dir can use them. Cheap no-op when files already exist with the
   * same size. Must be called off the main thread.
   */
  fun extractBundledFonts(context: Context) {
    runCatching {
      val destDir = File(context.filesDir, "fonts").apply { mkdirs() }
      val assets = context.assets
      val bundled = assets.list("fonts").orEmpty()
      for (name in bundled) {
        val dest = File(destDir, name)
        // openFd() only works for assets stored uncompressed in the APK; .ttf is
        // DEFLATE-compressed by default, so fall back to the stream's available()
        // (AssetInputStream reports the *uncompressed* length). Without this
        // fallback the size check never matched and every launch re-copied ~3MB.
        val assetSize =
          runCatching { assets.openFd("fonts/$name").use { it.length } }
            .recoverCatching { assets.open("fonts/$name").use { it.available().toLong() } }
            .getOrDefault(-1L)
        if (dest.exists() && assetSize > 0 && dest.length() == assetSize) continue
        assets.open("fonts/$name").use { input ->
          dest.outputStream().use { output -> input.copyTo(output) }
        }
      }
    }.onFailure { e ->
      Log.e(TAG, "Failed to extract bundled fonts", e)
    }
    // Always (re)build the family cache off the main thread so that the
    // player's font resolution never has to parse font files on the UI thread.
    refreshFamilyCache(context)
  }

  /**
   * Parses every font in filesDir/fonts and caches the family names.
   *
   * This opens and parses each font file, so it MUST be called from a
   * background thread. [installedFamilies] only ever reads the cache.
   */
  fun refreshFamilyCache(context: Context): Set<String> {
    val fontsDir = File(context.filesDir, "fonts")

    val families =
      fontsDir
        .listFiles()
        ?.filter { it.isFile && it.name.lowercase(Locale.ROOT).matches(FONT_FILE_REGEX) }
        ?.mapNotNull { file ->
          runCatching {
            file.inputStream().use { TTFFile.open(it).families.values.firstOrNull() }
          }.getOrNull()
        }
        ?.toSet()
        .orEmpty()

    cachedFamilies = families
    return families
  }

  /**
   * Font family names installed in filesDir/fonts.
   *
   * Non-blocking: returns the cache populated by [refreshFamilyCache] (called
   * from App startup and after every import). Returns an empty set when the
   * cache is cold, in which case [resolveFontForWeight] falls back to the base
   * family + synthetic bold instead of blocking the caller with font parsing.
   */
  fun installedFamilies(): Set<String> = cachedFamilies.orEmpty()

  /**
   * Result of resolving a base family + CSS weight against installed fonts.
   *
   * @param family the family name to give to mpv's `sub-font`
   * @param bold whether mpv's `sub-bold` should be enabled (real Bold face is
   *   selected by libass when the family has one; synthetic bold otherwise)
   */
  data class ResolvedFont(val family: String, val bold: Boolean)

  /**
   * Resolves the effective font for a requested weight.
   *
   * - 400 -> base family, no bold
   * - 700 -> base family with the bold attribute (libass picks the real Bold
   *   face when present, e.g. Inter 18pt Bold; otherwise emboldens)
   * - other weights -> "<base> <SubfamilyName>" when such a family is installed
   *   (Google-Fonts static naming, e.g. "Inter 18pt SemiBold"); otherwise falls
   *   back to the base family with bold enabled for weights >= 600.
   */
  fun resolveFontForWeight(
    baseFamily: String,
    weight: Int,
  ): ResolvedFont {
    if (baseFamily.isBlank()) return ResolvedFont("", weight >= 600)
    return when (weight) {
      400 -> ResolvedFont(baseFamily, false)
      700 -> ResolvedFont(baseFamily, true)
      else -> {
        val suffix = WEIGHT_SUFFIXES[weight]
        val candidate = suffix?.let { "$baseFamily $it" }
        if (candidate != null && candidate in installedFamilies()) {
          ResolvedFont(candidate, false)
        } else {
          ResolvedFont(baseFamily, weight >= 600)
        }
      }
    }
  }

  /**
   * Applies the given base family + weight to mpv (primary and secondary subs).
   */
  fun applyFontWithWeight(
    baseFamily: String,
    weight: Int,
  ) {
    val resolved = resolveFontForWeight(baseFamily, weight)
    MPVLib.setPropertyString("sub-font", resolved.family)
    MPVLib.setPropertyString("secondary-sub-font", resolved.family)
    MPVLib.setPropertyBoolean("sub-bold", resolved.bold)
    MPVLib.setPropertyBoolean("secondary-sub-bold", resolved.bold)
  }

  /**
   * Reduces an untrusted display name (e.g. from a SAF `DocumentFile`) to a
   * plain file name inside the fonts directory. Returns null when nothing
   * usable is left, so a crafted name like `../../databases/x.ttf` can never
   * escape filesDir/fonts.
   */
  fun sanitizeFontFileName(fileName: String): String? {
    val base =
      fileName
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .trim()
        .trimStart('.')
    if (base.isBlank() || base == "." || base == "..") return null
    val ext = base.substringAfterLast('.', "").lowercase(Locale.ROOT)
    if (ext !in SUPPORTED_FONT_EXTENSIONS) return null
    return base
  }

  /**
   * Copies a single font file into filesDir/fonts, refreshes the family cache
   * and returns the detected family name (null on failure).
   *
   * Parses the font file, so it must be called from a background thread.
   */
  fun importFontFile(
    context: Context,
    fileName: String,
    open: () -> java.io.InputStream?,
  ): String? =
    runCatching {
      val safeName = sanitizeFontFileName(fileName) ?: return null

      val destDir = File(context.filesDir, "fonts").apply { mkdirs() }
      val dest = File(destDir, safeName)
      open()?.use { input ->
        dest.outputStream().use { output -> input.copyTo(output) }
      } ?: return null

      // Best-effort family detection for feedback / immediate use
      val family =
        runCatching {
          dest.inputStream().use { TTFFile.open(it).families.values.firstOrNull() }
        }.getOrNull() ?: safeName.substringBeforeLast('.')

      refreshFamilyCache(context)
      family
    }.onFailure { e ->
      Log.e(TAG, "Failed to import font $fileName", e)
    }.getOrNull()
}
