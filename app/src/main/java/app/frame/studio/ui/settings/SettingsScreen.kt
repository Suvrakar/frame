package app.frame.studio.ui.settings

import androidx.activity.compose.LocalActivity
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Switch
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import app.frame.studio.ui.pro.rememberProGate
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.frame.studio.BuildConfig
import app.frame.studio.FrameApp
import app.frame.studio.R
import app.frame.studio.ads.AdsService
import app.frame.studio.data.ThemeMode
import app.frame.studio.ui.common.GlassCard
import app.frame.studio.ui.common.openUrl

@Composable
fun SettingsScreen(app: FrameApp) {
    val mode by app.settings.themeMode.collectAsStateWithLifecycle()
    val privacyRequired by AdsService.privacyOptionsRequired.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val isPro by app.pro.isPro.collectAsStateWithLifecycle()
    val price by app.pro.price.collectAsStateWithLifecycle()
    val gate = rememberProGate(app.pro)

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 8.dp))

        ProCard(isPro, price) { gate.showPaywall() }
        if (!isPro || BuildConfig.DEBUG) {
            GlassCard(Modifier.fillMaxWidth()) {
                SettingsRow(Icons.Outlined.Restore, "Restore purchase", "Already bought Frame Pro on this account?") {
                    app.pro.restore()
                }
                if (BuildConfig.DEBUG) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))
                    Row(Modifier.fillMaxWidth().padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            SettingsRow(Icons.Outlined.BugReport, "Pro (debug)", "Debug builds only: unlock without buying", onClick = null)
                        }
                        Switch(checked = isPro, onCheckedChange = app.pro::setDebugPro)
                    }
                }
            }
        }

        Label("Appearance")
        GlassCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeOption(ThemeMode.LIGHT, Icons.Outlined.LightMode, mode, Modifier.weight(1f)) { app.settings.setThemeMode(it) }
                ThemeOption(ThemeMode.DARK, Icons.Outlined.DarkMode, mode, Modifier.weight(1f)) { app.settings.setThemeMode(it) }
                ThemeOption(ThemeMode.SYSTEM, Icons.Outlined.SettingsSuggest, mode, Modifier.weight(1f)) { app.settings.setThemeMode(it) }
            }
        }

        Label("Privacy")
        GlassCard(Modifier.fillMaxWidth()) {
            SettingsRow(Icons.Outlined.Policy, "Privacy policy", "How Frame handles your data") {
                context.openUrl(context.getString(R.string.privacy_policy_url))
            }
            if (privacyRequired) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))
                SettingsRow(Icons.Outlined.PrivacyTip, "Privacy choices", "Change your ad consent") {
                    activity?.let(AdsService::showPrivacyOptions)
                }
            }
        }

        Label("About")
        GlassCard(Modifier.fillMaxWidth()) {
            SettingsRow(Icons.Outlined.Info, "Frame ${BuildConfig.VERSION_NAME}", "Exports never carry a watermark", onClick = null)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))
            SettingsRow(
                Icons.Outlined.Speed,
                if (app.device.isLowEnd) "Lite mode" else "Standard mode",
                if (app.device.isLowEnd) {
                    "Preview and caches are tuned for this phone. Exports keep full quality."
                } else {
                    "Full-resolution preview"
                },
                onClick = null,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ProCard(isPro: Boolean, price: String?, onUpgrade: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF5B63F0), Color(0xFF8E5BF0))))
            .then(if (isPro) Modifier else Modifier.clickable(onClick = onUpgrade))
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.WorkspacePremium, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                if (isPro) "Frame Pro is active" else "Get Frame Pro",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
            Text(
                if (isPro) "Magic Movie, animated captions, 4K & 60 fps, no ads. Thank you!"
                else "Magic Movie, animated captions, 4K & 60 fps, no ads" + (price?.let { " · $it once" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
        if (!isPro) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = Color.White)
    }
}

@Composable
private fun Label(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
}

@Composable
private fun ThemeOption(option: ThemeMode, icon: ImageVector, selected: ThemeMode, modifier: Modifier, onSelect: (ThemeMode) -> Unit) {
    val active = option == selected
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent)
            .border(1.dp, if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent, shape)
            .clickable { onSelect(option) }
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            option.label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, subtitle: String, onClick: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onClick != null) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
