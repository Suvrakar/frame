package app.frame.studio.ui.magic

import app.frame.studio.media.ImportMessages
import app.frame.studio.ui.common.bounceClick
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.frame.studio.FrameApp
import app.frame.studio.domain.CanvasAspect
import app.frame.studio.magic.MagicLength
import app.frame.studio.magic.MagicMovieBuilder
import app.frame.studio.magic.MagicVibe
import app.frame.studio.pro.ProFeature
import app.frame.studio.ui.common.GlassBackground
import app.frame.studio.ui.common.VideoThumbnail
import app.frame.studio.ui.pro.rememberProGate
import app.frame.studio.ui.theme.LocalGlass
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val SHAPES = listOf(CanvasAspect.R9_16, CanvasAspect.R1_1, CanvasAspect.R16_9, CanvasAspect.ORIGINAL)

/** Pick media, choose a vibe; Frame cuts the movie on the device. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MagicMovieScreen(
    frameApp: FrameApp,
    onBack: () -> Unit,
    onCreated: (projectId: String) -> Unit,
) {
    val media = remember { mutableStateListOf<Uri>() }
    var music by remember { mutableStateOf<Uri?>(null) }
    var title by rememberSaveable { mutableStateOf("") }
    var vibe by rememberSaveable { mutableStateOf(MagicVibe.UPBEAT) }
    var length by rememberSaveable { mutableStateOf(MagicLength.MEDIUM) }
    var shape by rememberSaveable { mutableStateOf(CanvasAspect.R9_16) }
    var progress by remember { mutableStateOf<MagicMovieBuilder.Progress?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val gate = rememberProGate(frameApp.pro)

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(maxItems = 60)) { uris ->
        uris.forEach { if (it !in media) media += it }
    }
    val pickMusic = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) music = uri
    }
    val musicName by produceState<String?>(null, music) {
        val picked = music ?: return@produceState run { value = null }
        val info = frameApp.mediaProbe.probeAudio(picked)
        if (info == null || !info.supported) {
            // Say so now rather than silently making a movie without the song.
            music = null
            snackbar.showSnackbar("That song can't be played. Pick another one.")
        } else {
            value = info.displayName
        }
    }

    fun make() {
        gate.require(ProFeature.MAGIC_MOVIE) {
            frameApp.pro.consumePass(ProFeature.MAGIC_MOVIE)
            job = scope.launch {
                try {
                    val skipped = mutableListOf<String>()
                    val project = frameApp.magicMovie.create(media.toList(), music, title, vibe, length, shape, skipped) { p -> progress = p }
                    ImportMessages.summary(skipped)?.let { msg ->
                        // Show it before leaving; the snackbar belongs to this screen.
                        scope.launch { snackbar.showSnackbar(msg) }
                        kotlinx.coroutines.delay(2200)
                    }
                    onCreated(project.id)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    snackbar.showSnackbar(e.message ?: "Couldn't make the movie")
                } finally {
                    progress = null
                    job = null
                }
            }
        }
    }

    val busy = progress != null
    BackHandler(enabled = busy) { job?.cancel() }

    GlassBackground {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    title = { Text("Magic Movie") },
                    navigationIcon = {
                        IconButton(onClick = { if (busy) job?.cancel() else onBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
            bottomBar = {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp)) {
                    val p = progress
                    if (p != null) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(LocalGlass.current.fill)
                                .border(1.dp, LocalGlass.current.border, RoundedCornerShape(18.dp))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(p.stage + "…", style = MaterialTheme.typography.titleSmall)
                            LinearProgressIndicator(progress = { p.fraction }, modifier = Modifier.fillMaxWidth())
                            TextButton(onClick = { job?.cancel() }, modifier = Modifier.align(Alignment.End)) { Text("Cancel") }
                        }
                    } else {
                        Button(
                            onClick = ::make,
                            enabled = media.isNotEmpty(),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().height(58.dp),
                        ) {
                            Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                            Spacer(Modifier.width(10.dp))
                            Text(if (media.isEmpty()) "Pick photos to start" else "Make my movie", fontWeight = FontWeight.SemiBold)
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
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Hero()

                Section("Photos & videos") {
                    if (media.isEmpty()) {
                        PickTile(
                            icon = Icons.Outlined.AddPhotoAlternate,
                            title = "Pick photos & videos",
                            subtitle = "Up to 60. Frame keeps the best and skips blurry shots and duplicates.",
                            enabled = !busy,
                        ) { pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${media.size} picked", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            TextButton(enabled = !busy, onClick = { pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }) { Text("Add more") }
                            TextButton(enabled = !busy, onClick = { media.clear() }) { Text("Clear") }
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(media, key = { _, u -> u.toString() }) { _, uri ->
                                Box {
                                    VideoThumbnail(
                                        probe = frameApp.mediaProbe,
                                        uri = uri.toString(),
                                        atMs = 0,
                                        isImage = frameApp.mediaProbe.isImage(uri),
                                        maxSize = 200,
                                        modifier = Modifier.size(76.dp).clip(RoundedCornerShape(14.dp)),
                                    )
                                    if (!busy) {
                                        Box(
                                            Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(4.dp)
                                                .size(22.dp)
                                                .clip(RoundedCornerShape(11.dp))
                                                .background(Color.Black.copy(alpha = 0.55f))
                                                .clickable { media.remove(uri) },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(Icons.Outlined.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Section("Music · optional") {
                    PickTile(
                        icon = Icons.Outlined.MusicNote,
                        title = musicName ?: if (music != null) "Song picked" else "Add a song",
                        subtitle = if (music != null) "Cuts will land on the beat" else "Frame finds the beat and cuts to it",
                        enabled = !busy,
                        trailing = if (music != null) ({
                            IconButton(onClick = { music = null }, enabled = !busy) { Icon(Icons.Outlined.Close, contentDescription = "Remove song") }
                        }) else null,
                    ) { pickMusic.launch(arrayOf("audio/*")) }
                }

                Section("Title") {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it.take(40) },
                        placeholder = { Text("Summer in Lisbon") },
                        singleLine = true,
                        enabled = !busy,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    )
                }

                Section("Vibe") {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MagicVibe.entries.forEach { v -> VibeChip(v, v == vibe, enabled = !busy) { vibe = v } }
                    }
                }

                Section("Length") {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        MagicLength.entries.forEachIndexed { i, l ->
                            SegmentedButton(
                                selected = l == length,
                                onClick = { length = l },
                                enabled = !busy,
                                shape = SegmentedButtonDefaults.itemShape(i, MagicLength.entries.size),
                                icon = {},
                            ) { Text(l.label, maxLines = 1) }
                        }
                    }
                }

                Section("Shape") {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SHAPES.forEachIndexed { i, s ->
                            SegmentedButton(
                                selected = s == shape,
                                onClick = { shape = s },
                                enabled = !busy,
                                shape = SegmentedButtonDefaults.itemShape(i, SHAPES.size),
                                icon = {},
                            ) { Text(s.label, maxLines = 1) }
                        }
                    }
                }
                Text(
                    "Everything happens on your phone. Your photos never leave it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun Hero() {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF5B63F0), Color(0xFFB45BF0), Color(0xFFF07A5B))))
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Your movie, made for you", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(
                "Frame picks the best moments, puts them in order, cuts to the beat and adds a title.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.padding(start = 12.dp).size(40.dp))
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        content()
    }
}

@Composable
private fun PickTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(LocalGlass.current.fill)
            .border(1.dp, LocalGlass.current.border, shape)
            .bounceClick(enabled = enabled, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}

@Composable
private fun VibeChip(vibe: MagicVibe, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .width(84.dp)
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else LocalGlass.current.fill)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else LocalGlass.current.border, shape)
            .bounceClick(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(vibe.emoji, style = MaterialTheme.typography.headlineSmall)
        Text(
            vibe.label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
