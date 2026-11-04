package app.frame.studio.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.frame.studio.ui.theme.LocalGlass

/**
 * App background: base colour plus three soft colour glows. The glows are what the translucent
 * glass surfaces sit on; radial gradients look blurred already, so no real-time blur is needed
 * (cheap on low-end phones).
 */
@Composable
fun GlassBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val glass = LocalGlass.current
    val base = MaterialTheme.colorScheme.background
    Box(
        modifier
            .fillMaxSize()
            .background(base)
            .drawBehind {
                val r = size.maxDimension
                drawCircle(
                    Brush.radialGradient(listOf(glass.glowA, Color.Transparent), center = Offset(size.width * 0.05f, size.height * 0.08f), radius = r * 0.55f),
                    radius = r * 0.55f, center = Offset(size.width * 0.05f, size.height * 0.08f),
                )
                drawCircle(
                    Brush.radialGradient(listOf(glass.glowB, Color.Transparent), center = Offset(size.width * 1.0f, size.height * 0.42f), radius = r * 0.5f),
                    radius = r * 0.5f, center = Offset(size.width * 1.0f, size.height * 0.42f),
                )
                drawCircle(
                    Brush.radialGradient(listOf(glass.glowC, Color.Transparent), center = Offset(size.width * 0.15f, size.height * 0.95f), radius = r * 0.45f),
                    radius = r * 0.45f, center = Offset(size.width * 0.15f, size.height * 0.95f),
                )
            },
        content = content,
    )
}

@Composable
private fun Modifier.shadowCompat(elevation: Dp, shape: Shape): Modifier {
    val glass = LocalGlass.current
    return if (elevation > 0.dp) shadow(elevation, shape, clip = false, ambientColor = glass.shadow, spotColor = glass.shadow) else this
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    strong: Boolean = false,
    elevation: Dp = 10.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val glass = LocalGlass.current
    Column(
        modifier
            .shadowCompat(elevation, shape)
            .clip(shape)
            .background(if (strong) glass.fillStrong else glass.fill)
            .border(1.dp, glass.border, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        content = content,
    )
}

/** Square-ish tile with an icon and a label, like a launcher shortcut. */
@Composable
fun GlassTile(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    GlassCard(modifier = modifier, shape = RoundedCornerShape(20.dp), elevation = 6.dp, onClick = onClick) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = androidx.compose.ui.text.style.TextOverflow.Visible,
            )
        }
    }
}

/** Round translucent icon button used in headers. */
@Composable
fun GlassIconButton(icon: ImageVector, contentDescription: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val glass = LocalGlass.current
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(glass.fill)
            .border(1.dp, glass.border, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
    }
}

data class NavTab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

/** Floating glass bottom navigation. */
@Composable
fun GlassBottomBar(tabs: List<NavTab>, current: String?, onSelect: (NavTab) -> Unit, modifier: Modifier = Modifier) {
    val glass = LocalGlass.current
    val shape = RoundedCornerShape(26.dp)
    Row(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .fillMaxWidth()
            .shadow(14.dp, shape, ambientColor = glass.shadow, spotColor = glass.shadow)
            .clip(shape)
            .background(glass.fillStrong)
            .border(1.dp, glass.border, shape)
            .padding(6.dp),
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        tabs.forEach { tab ->
            val selected = tab.route == current
            val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                    .clickable { onSelect(tab) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
                Text(
                    tab.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = color,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** Section title row with an optional trailing action. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(
                "$action  →",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(6.dp),
            )
        }
    }
}
