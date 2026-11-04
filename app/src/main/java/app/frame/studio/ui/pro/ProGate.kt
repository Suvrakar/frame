package app.frame.studio.ui.pro

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.FilterVintage
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.OndemandVideo
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.frame.studio.ads.AdsService
import app.frame.studio.ads.RewardedAds
import app.frame.studio.pro.ProFeature
import app.frame.studio.pro.ProStore
import app.frame.studio.ui.theme.LocalGlass

/**
 * Gate for Pro features: `gate.require(feature) { ... }` runs the action straight away for Pro
 * users (or with an ad pass) and otherwise opens the Frame Pro sheet. Buying, or watching a short
 * ad, continues the action automatically.
 */
@Stable
class ProGate(val store: ProStore) {
    internal class Pending(val feature: ProFeature, val action: () -> Unit)

    internal var pending by mutableStateOf<Pending?>(null)

    fun require(feature: ProFeature, action: () -> Unit) {
        if (store.hasAccess(feature)) action() else pending = Pending(feature, action)
    }

    /** Opens the sheet just to show what Pro includes (e.g. from Settings). */
    fun showPaywall(feature: ProFeature = ProFeature.MAGIC_MOVIE) {
        pending = Pending(feature) {}
    }
}

@Composable
fun rememberProGate(store: ProStore): ProGate {
    val gate = remember(store) { ProGate(store) }
    ProSheet(gate)
    return gate
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProSheet(gate: ProGate) {
    val pending = gate.pending ?: return
    val store = gate.store
    val isPro by store.isPro.collectAsStateWithLifecycle()
    val price by store.price.collectAsStateWithLifecycle()
    val adsAllowed by AdsService.canShowAds.collectAsStateWithLifecycle()
    val adReady by RewardedAds.ready.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(adsAllowed) { if (adsAllowed && activity != null) RewardedAds.preload(activity) }
    // A purchase completes outside the app's flow; continue as soon as Play confirms it.
    LaunchedEffect(isPro) {
        if (isPro) {
            gate.pending = null
            pending.action()
        }
    }

    ModalBottomSheet(
        onDismissRequest = { gate.pending = null },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF5B63F0), Color(0xFFB45BF0)))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.WorkspacePremium, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
            }
            Text("Frame Pro", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "${pending.feature.title} is part of Frame Pro.\nOne-time payment · Lifetime access · No ads, ever.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(LocalGlass.current.fill)
                    .border(1.dp, LocalGlass.current.border, RoundedCornerShape(20.dp))
                    .padding(vertical = 6.dp),
            ) {
                ProFeature.entries.forEach { f ->
                    FeatureRow(iconFor(f), f.title, f.pitch, highlighted = f == pending.feature)
                }
                FeatureRow(Icons.Outlined.OndemandVideo, "No ads for life", "One payment removes every ad. No subscription.", highlighted = false)
            }
            Button(
                onClick = { activity?.let(store::purchase) },
                enabled = price != null && activity != null,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    if (price != null) "Get lifetime Pro · $price once" else "Frame Pro isn't available right now",
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (adsAllowed) {
                OutlinedButton(
                    onClick = {
                        val a = activity ?: return@OutlinedButton
                        var earned = false
                        RewardedAds.show(
                            a,
                            onReward = {
                                earned = true
                                store.grantPass(pending.feature)
                            },
                            onClosed = {
                                if (earned) {
                                    gate.pending = null
                                    pending.action()
                                }
                            },
                        )
                    },
                    enabled = adReady,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(if (adReady) "Watch a short ad to use it once" else "Loading ad…")
                }
            }
            TextButton(onClick = { gate.pending = null }) { Text("Not now") }
        }
    }
}

private fun iconFor(feature: ProFeature): ImageVector = when (feature) {
    ProFeature.MAGIC_MOVIE -> Icons.Outlined.AutoAwesome
    ProFeature.ANIMATED_CAPTIONS -> Icons.Outlined.ClosedCaption
    ProFeature.HIGH_RES_EXPORT -> Icons.Outlined.HighQuality
    ProFeature.PREMIUM_TEMPLATES -> Icons.Outlined.Dashboard
    ProFeature.PREMIUM_FILTERS -> Icons.Outlined.FilterVintage
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, subtitle: String, highlighted: Boolean) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = if (highlighted) 0.22f else 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Medium,
                color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
