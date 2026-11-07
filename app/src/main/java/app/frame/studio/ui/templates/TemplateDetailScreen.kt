package app.frame.studio.ui.templates

import app.frame.studio.telemetry.AppEvents
import app.frame.studio.telemetry.Telemetry
import app.frame.studio.media.ImportMessages
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asImageBitmap
import app.frame.studio.pro.ProFeature
import app.frame.studio.ui.pro.rememberProGate
import app.frame.studio.FrameApp
import app.frame.studio.ui.common.GlassBackground
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

    val gate = rememberProGate(app.pro)
    // Frame / collage templates: photos are framed on a live preview before the video is made.
    var adjust by remember { mutableStateOf<Pair<List<android.net.Uri>, app.frame.studio.templates.PhotoAdjustSession>?>(null) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { adjust?.second?.close() } }

    fun make(uris: List<android.net.Uri>, fits: Map<String, app.frame.studio.templates.PhotoFit>) {
        creating = true
        scope.launch {
            try {
                val skipped = mutableListOf<String>()
                val project = app.templateBuilder.create(template, values.toMap(), uris, skipped, fits)
                if (template.pro) app.pro.consumePass(ProFeature.PREMIUM_TEMPLATES)
                AppEvents.projectCreated("template")
                AppEvents.templateUsed(template.id, skipped.size)
                ImportMessages.summary(skipped)?.let { msg ->
                    // Show it before leaving; the snackbar belongs to this screen.
                    scope.launch { snackbar.showSnackbar(msg) }
                    kotlinx.coroutines.delay(2200)
                }
                adjust?.second?.close()
                adjust = null
                onCreated(project.id)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Telemetry.nonFatal("template", e)
                snackbar.showSnackbar(e.message ?: "Couldn't create the video")
            } finally {
                creating = false
            }
        }
    }

    val pick = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = template.slots.coerceAtLeast(2)),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        if (template.collage == null) {
            make(uris, emptyMap())
            return@rememberLauncherForActivityResult
        }
        creating = true
        scope.launch {
            try {
                val skipped = mutableListOf<String>()
                val session = app.templateBuilder.openAdjust(template, values.toMap(), uris, skipped)
                if (session == null) make(uris, emptyMap()) else adjust = uris to session
                ImportMessages.summary(skipped)?.let { snackbar.showSnackbar(it) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Telemetry.nonFatal("template_adjust", e)
                snackbar.showSnackbar(e.message ?: "Couldn't open those photos")
            } finally {
                creating = false
            }
        }
    }

    adjust?.let { (uris, session) ->
        AdjustPhotosScreen(
            session = session,
            creating = creating,
            onCancel = {
                session.close()
                adjust = null
            },
            onCreate = { fits -> make(uris, fits) },
        )
        return
    }

    GlassBackground {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    title = { Text(template.name) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
            bottomBar = {
                Box(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp)) {
                    Button(
                        onClick = {
                            val launch = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }
                            if (template.pro) gate.require(ProFeature.PREMIUM_TEMPLATES, launch) else launch()
                        },
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
                        val typed = Templates.previewValues + values.filterValues { it.isNotBlank() }
                        if (template.collage != null) {
                            // The real renderer, live with what the user types.
                            val rendered by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, typed) {
                                kotlinx.coroutines.delay(200)
                                value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                                    runCatching { app.templatePreviews.render(template, 540, typed) }.getOrNull()
                                }
                            }
                            val shown = rendered
                            if (shown != null) {
                                androidx.compose.foundation.Image(
                                    shown.asImageBitmap(),
                                    contentDescription = template.name,
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                TemplateArt(template, Modifier.fillMaxSize(), values = typed, showSlots = false)
                            }
                        } else {
                            TemplateArt(
                                template,
                                Modifier.fillMaxSize(),
                                values = typed,
                                animated = app.device.animationsEnabled,
                            )
                        }
                        if (template.pro) ProBadge(Modifier.align(Alignment.BottomEnd).padding(10.dp))
                    }
                }
                Column {
                    Text(template.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(template.tagline, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetaPill(template.aspect.label)
                    MetaPill(Templates.mediaLabel(template))
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
                    if (template.collage != null) {
                        "Photos are arranged into the collage and pop in one by one. Videos are used as a still. Add music or a voiceover afterwards."
                    } else {
                        "Everything stays editable afterwards — swap clips, change text, add music or a voiceover."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
            }
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

