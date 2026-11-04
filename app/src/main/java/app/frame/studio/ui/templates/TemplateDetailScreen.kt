package app.frame.studio.ui.templates

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.frame.studio.FrameApp
import app.frame.studio.templates.TemplateField
import app.frame.studio.templates.Templates
import app.frame.studio.ui.common.formatDuration
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateDetailScreen(
    app: FrameApp,
    templateId: String,
    onBack: () -> Unit,
    onCreated: (projectId: String) -> Unit,
) {
    val template = Templates.byId(templateId)
    if (template == null) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onBack() }
        return
    }
    val values = remember { mutableStateMapOf<TemplateField, String>() }
    var creating by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val pick = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = template.slots.coerceAtLeast(2)),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        creating = true
        scope.launch {
            try {
                val project = app.templateBuilder.create(template, values.toMap(), uris)
                onCreated(project.id)
            } catch (e: Exception) {
                snackbar.showSnackbar(e.message ?: "Couldn't create the video")
            } finally {
                creating = false
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(template.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Box(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp)) {
                Button(
                    onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                    enabled = !creating,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                ) {
                    if (creating) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.size(10.dp))
                        Text("Creating your video…")
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Use template", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Pick up to ${template.slots} photos or videos",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val ratio = template.aspect.ratio ?: (9f / 16f)
            Box(Modifier.fillMaxWidth().heightIn(max = 380.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .aspectRatio(ratio, matchHeightConstraintsFirst = ratio < 1f)
                        .clip(RoundedCornerShape(24.dp)),
                ) {
                    TemplateArt(
                        template,
                        Modifier.fillMaxSize(),
                        values = Templates.previewValues + values.filterValues { it.isNotBlank() },
                        animated = app.device.animationsEnabled,
                    )
                }
            }
            Column {
                Text(template.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(template.tagline, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetaPill(template.aspect.label)
                MetaPill("${template.slots} clips")
                MetaPill(formatDuration(template.totalMs))
                template.badge?.let { MetaPill(it) }
            }
            if (template.fields.isNotEmpty()) {
                Text("Make it yours", style = MaterialTheme.typography.titleMedium)
                template.fields.forEach { field ->
                    OutlinedTextField(
                        value = values[field].orEmpty(),
                        onValueChange = { if (it.length <= if (field == TemplateField.AGE) 3 else 40) values[field] = it },
                        label = { Text(field.label) },
                        placeholder = { Text(field.hint) },
                        singleLine = true,
                        keyboardOptions = if (field == TemplateField.AGE) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Text(
                "Everything stays editable afterwards — swap clips, change text, add music or a voiceover.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun MetaPill(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

