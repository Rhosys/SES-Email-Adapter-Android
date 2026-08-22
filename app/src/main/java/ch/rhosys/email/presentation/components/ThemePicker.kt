package ch.rhosys.email.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.rhosys.email.ui.theme.CatppuccinColors
import ch.rhosys.email.ui.theme.CatppuccinFlavor
import ch.rhosys.email.ui.theme.Latte
import ch.rhosys.email.ui.theme.Mocha
import ch.rhosys.email.ui.theme.palette

/**
 * Theme picker.
 *
 * Every tile paints itself from the flavour it represents rather than from the
 * active theme, so the row is a set of previews instead of five identically
 * coloured chips. Each one shows the surface, the text and subtext ramp, the
 * accent colours, and a miniature mail row, which is what actually changes when
 * the flavour is applied.
 *
 * `null` means follow the system setting; that tile previews both halves it can
 * resolve to.
 */
@Composable
fun ThemePicker(
    selected: CatppuccinFlavor?,
    onSelect: (CatppuccinFlavor?) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Two per row, laid out manually so this can live inside a scrolling Column
    // without nesting a lazy grid.
    val options: List<CatppuccinFlavor?> = listOf(null) + CatppuccinFlavor.entries

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        options.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEach { flavor ->
                    ThemeTile(
                        flavor = flavor,
                        isSelected = selected == flavor,
                        onClick = { onSelect(flavor) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // Keeps a lone trailing tile at half width instead of stretching it.
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ThemeTile(
    flavor: CatppuccinFlavor?,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // System has no palette of its own: it previews both halves it can resolve to,
    // and borrows Mocha's ramp for the footer so the label stays legible.
    val palette = flavor?.palette() ?: Mocha
    val name = flavor?.label ?: "System"
    val mode = when {
        flavor == null -> "Follows device"
        flavor.isDark -> "Dark"
        else -> "Light"
    }

    Box(
        modifier = modifier
            .height(184.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(palette.base)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) palette.mauve else palette.surface1,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(role = Role.RadioButton, onClick = onClick),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (flavor == null) {
                    SystemSplitPreview()
                } else {
                    FlavorPreview(palette)
                }

                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(20.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.mauve),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "Selected",
                            tint = palette.crust,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.mantle)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        name,
                        color = palette.text,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(mode, color = palette.subtext0, fontSize = 11.sp)
                }
                if (flavor != null) {
                    // Concrete proof the flavours are actually different colors,
                    // not just the same palette re-skinned: the base color swatch
                    // plus its hex value, which differs for every flavour.
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(palette.base)
                                .border(1.dp, palette.overlay0, RoundedCornerShape(3.dp)),
                        )
                        Text(
                            palette.base.toHexLabel(),
                            color = palette.subtext0,
                            fontSize = 10.sp,
                        )
                    }
                }
            }
        }
    }
}

/** e.g. "#1E1E2E" — proof-of-difference label under each theme tile's swatch. */
private fun Color.toHexLabel(): String {
    val argb = toArgb()
    return "#" + (argb and 0x00FFFFFF).toString(16).padStart(6, '0').uppercase()
}

/** A miniature mail row plus the accent ramp — the parts a flavour actually changes. */
@Composable
private fun FlavorPreview(palette: CatppuccinColors) {
    Column(
        modifier = Modifier.fillMaxSize().padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        MiniMailRow(palette, accent = palette.mauve, emphasised = true)
        MiniMailRow(palette, accent = palette.blue, emphasised = false)
        Spacer(Modifier.weight(1f))
        AccentRamp(palette)
    }
}

@Composable
private fun MiniMailRow(palette: CatppuccinColors, accent: Color, emphasised: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.size(14.dp).clip(RoundedCornerShape(7.dp)).background(accent))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
            // Sender line: brighter and wider when the row is "urgent".
            Bar(color = if (emphasised) palette.text else palette.subtext1, widthFraction = if (emphasised) 0.7f else 0.5f)
            Bar(color = palette.subtext0, widthFraction = 0.9f, height = 4.dp)
        }
    }
}

@Composable
private fun Bar(color: Color, widthFraction: Float, height: androidx.compose.ui.unit.Dp = 5.dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(3.dp))
            .background(color),
    )
}

@Composable
private fun AccentRamp(palette: CatppuccinColors) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(
            palette.mauve, palette.blue, palette.teal,
            palette.green, palette.yellow, palette.peach, palette.red,
        ).forEach { swatch ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(swatch),
            )
        }
    }
}

/** Splits the preview between the two palettes the system setting resolves to. */
@Composable
private fun SystemSplitPreview() {
    Row(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxSize().background(Latte.base)) {
            Column(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MiniMailRow(Latte, accent = Latte.mauve, emphasised = true)
                Spacer(Modifier.weight(1f))
                Text("Light", color = Latte.subtext0, fontSize = 10.sp)
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxSize().background(Mocha.base)) {
            Column(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MiniMailRow(Mocha, accent = Mocha.mauve, emphasised = true)
                Spacer(Modifier.weight(1f))
                Text("Dark", color = Mocha.subtext0, fontSize = 10.sp)
            }
        }
    }
}
