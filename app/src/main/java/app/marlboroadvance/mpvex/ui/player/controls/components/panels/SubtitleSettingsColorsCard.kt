package app.marlboroadvance.mpvex.ui.player.controls.components.panels

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.FormatColorReset
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.preferences.SubtitlesPreferences
import app.marlboroadvance.mpvex.preferences.preference.Preference
import app.marlboroadvance.mpvex.preferences.preference.deleteAndGet
import app.marlboroadvance.mpvex.presentation.components.ExpandableCard
import app.marlboroadvance.mpvex.ui.player.controls.CARDS_MAX_WIDTH
import app.marlboroadvance.mpvex.ui.player.controls.panelCardsColors
import app.marlboroadvance.mpvex.ui.theme.spacing
import `is`.xyz.mpv.MPVLib
import org.koin.compose.koinInject

@Composable
fun SubtitleSettingsColorsCard(modifier: Modifier = Modifier) {
  val preferences = koinInject<SubtitlesPreferences>()
  var isExpanded by remember { mutableStateOf(true) }
  ExpandableCard(
    isExpanded = isExpanded,
    onExpand = { isExpanded = !isExpanded },
    title = {
      Row(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
      ) {
        Icon(Icons.Default.Palette, null)
        Text(stringResource(R.string.player_sheets_sub_colors_card_title))
      }
    },
    modifier = modifier.widthIn(max = CARDS_MAX_WIDTH),
    colors = panelCardsColors(),
  ) {
    Column {
      var currentColorType by remember { mutableStateOf(SubColorType.Text) }
      var currentColor by remember { mutableIntStateOf(getCurrentMPVColor(currentColorType)) }
      LaunchedEffect(currentColorType) {
        currentColor = getCurrentMPVColor(currentColorType)
      }
      Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier =
          Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = MaterialTheme.spacing.extraSmall, end = MaterialTheme.spacing.medium),
      ) {
        SubColorType.entries.forEach { type ->
          IconToggleButton(
            checked = currentColorType == type,
            onCheckedChange = { currentColorType = type },
          ) {
            Icon(
              when (type) {
                SubColorType.Text -> Icons.Default.FormatColorText
                SubColorType.Border -> Icons.Default.BorderColor
                SubColorType.Background -> Icons.Default.FormatColorFill
              },
              null,
            )
          }
        }
        Text(stringResource(currentColorType.titleRes))
        Spacer(Modifier.weight(1f))
        TextButton(
          onClick = {
            resetColors(preferences, currentColorType)
            currentColor = getCurrentMPVColor(currentColorType)
          },
        ) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(Icons.Default.FormatColorReset, null)
            Text(stringResource(R.string.generic_reset))
          }
        }
      }
      SubtitlesColorPalette(
        colors = currentColorType.palette,
        selected = currentColor,
        onSelect = {
          currentColor = it
          currentColorType.preference(preferences).set(it)
          MPVLib.setPropertyString(currentColorType.property, it.toColorHexString())
        },
      )
    }
  }
}

@OptIn(ExperimentalStdlibApi::class)
fun Int.toColorHexString() = "#" + this.toHexString().uppercase()

/** Curated swatches suitable for subtitle TEXT (bright, readable colors). */
private val TEXT_PALETTE: List<Int> =
  listOf(
    0xFFFFFFFF, // White (default)
    0xFFE0E0E0, // Light gray
    0xFFFFF9C4, // Cream
    0xFFFFEE00, // Classic yellow
    0xFFFFD54F, // Amber
    0xFFFFA726, // Orange
    0xFF80DEEA, // Light cyan
    0xFF4FC3F7, // Light blue
    0xFFA5D6A7, // Light green
    0xFF7CFC00, // Lawn green
    0xFFF48FB1, // Pink
    0xFFEF5350, // Red
    0xFFCE93D8, // Lavender
    0xFF000000, // Black
  ).map { it.toInt() }

/** Curated swatches for subtitle BORDER/outline (dark, high-contrast colors). */
private val BORDER_PALETTE: List<Int> =
  listOf(
    0xFF000000, // Black (default)
    0xFF212121, // Near black
    0xFF424242, // Dark gray
    0xFF757575, // Gray
    0xFFFFFFFF, // White
    0xFF1A237E, // Navy
    0xFF0D47A1, // Dark blue
    0xFF1B5E20, // Dark green
    0xFF4E342E, // Dark brown
    0xFF7F0000, // Dark red
    0xFF4A148C, // Dark purple
    0xFF37474F, // Blue gray
    0xFFFFEE00, // Yellow (inverted style)
    0xFF006064, // Dark cyan
  ).map { it.toInt() }

/** Swatches for subtitle BACKGROUND box, including transparency presets. */
private val BACKGROUND_PALETTE: List<Int> =
  listOf(
    0x00000000, // Transparent (default)
    0x40000000, // Black 25%
    0x80000000, // Black 50%
    0xBF000000, // Black 75%
    0xFF000000, // Black 100%
    0x80FFFFFF, // White 50%
    0xFFFFFFFF, // White
    0x80212121, // Dark gray 50%
    0xFF212121, // Dark gray
    0x801A237E, // Navy 50%
    0x801B5E20, // Green 50%
    0x807F0000, // Red 50%
    0xFFFFF9C4, // Cream
    0xFFFFEE00, // Yellow
  ).map { it.toInt() }

enum class SubColorType(
  @StringRes val titleRes: Int,
  val property: String,
  val preference: (SubtitlesPreferences) -> Preference<Int>,
  val palette: List<Int>,
) {
  Text(
    R.string.player_sheets_subtitles_color_text,
    "sub-color",
    preference = SubtitlesPreferences::textColor,
    palette = TEXT_PALETTE,
  ),
  Border(
    R.string.player_sheets_subtitles_color_border,
    "sub-border-color",
    preference = SubtitlesPreferences::borderColor,
    palette = BORDER_PALETTE,
  ),
  Background(
    R.string.player_sheets_subtitles_color_background,
    "sub-back-color",
    preference = SubtitlesPreferences::backgroundColor,
    palette = BACKGROUND_PALETTE,
  ),
}

fun resetColors(
  preferences: SubtitlesPreferences,
  type: SubColorType,
) {
  when (type) {
    SubColorType.Text -> {
      MPVLib.setPropertyString("sub-color", preferences.textColor.deleteAndGet().toColorHexString())
    }

    SubColorType.Border -> {
      MPVLib.setPropertyString("sub-border-color", preferences.borderColor.deleteAndGet().toColorHexString())
    }

    SubColorType.Background -> {
      MPVLib.setPropertyString("sub-back-color", preferences.backgroundColor.deleteAndGet().toColorHexString())
    }
  }
}

// toColorInt() throws IllegalArgumentException on anything it can't parse, and
// mpv is free to hand back a format we don't expect (or nothing at all when the
// property is unset), so fall back to opaque white instead of crashing the panel.
val getCurrentMPVColor: (SubColorType) -> Int = { type ->
  runCatching { MPVLib.getPropertyString(type.property)?.uppercase()?.toColorInt() }
    .getOrNull() ?: 0xFFFFFFFF.toInt()
}

/**
 * A grid of small clickable color swatches (replaces the old RGBA sliders).
 * The selected swatch is highlighted with a primary ring and a check mark.
 * Fully transparent swatches are rendered with a "block" icon.
 */
@Composable
fun SubtitlesColorPalette(
  colors: List<Int>,
  selected: Int,
  onSelect: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val swatchesPerRow = 7
  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .padding(horizontal = MaterialTheme.spacing.medium),
    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
  ) {
    colors.chunked(swatchesPerRow).forEach { row ->
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        row.forEach { argb ->
          val isSelected = argb == selected
          val swatchColor = Color(argb)
          val isTransparent = (argb ushr 24) == 0
          Box(
            modifier =
              Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(swatchColor)
                .border(
                  width = if (isSelected) 3.dp else 1.dp,
                  color =
                    if (isSelected) {
                      MaterialTheme.colorScheme.primary
                    } else {
                      MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    },
                  shape = RoundedCornerShape(8.dp),
                )
                .clickable { onSelect(argb) },
            contentAlignment = Alignment.Center,
          ) {
            when {
              isSelected -> {
                Icon(
                  Icons.Default.Check,
                  contentDescription = null,
                  tint = if (swatchColor.luminance() > 0.5f) Color.Black else Color.White,
                  modifier = Modifier.size(20.dp),
                )
              }
              isTransparent -> {
                Icon(
                  Icons.Default.Block,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.outline,
                  modifier = Modifier.size(20.dp),
                )
              }
            }
          }
        }
        // Pad the last row so spacing stays consistent
        repeat(swatchesPerRow - row.size) {
          Spacer(Modifier.size(38.dp))
        }
      }
    }
  }
}
