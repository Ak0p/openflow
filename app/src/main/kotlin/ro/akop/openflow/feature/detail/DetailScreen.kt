package ro.akop.openflow.feature.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import ro.akop.openflow.AppContainer
import ro.akop.openflow.core.data.Transcript
import ro.akop.openflow.ui.engineLabel
import ro.akop.openflow.ui.formatDuration
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val viewModel: DetailViewModel = viewModel(factory = DetailViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.deleted.collect { onBack() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Dictation") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val transcript = state.transcript
                    if (transcript != null) {
                        IconButton(onClick = { context.share(transcript.text) }) {
                            Icon(Icons.Outlined.Share, contentDescription = "Share")
                        }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)

        val transcript = state.transcript
        when {
            transcript != null -> DetailContent(
                transcript = transcript,
                modifier = modifier,
                onCopy = {
                    context.copyToClipboard(transcript.text)
                    scope.launch { snackbarHostState.showSnackbar("Copied to clipboard") }
                },
                onShare = { context.share(transcript.text) },
            )

            state.isLoading -> Unit

            else -> Box(modifier.padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "This dictation is gone.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this dictation?") },
            text = { Text("The transcript and its audio will be removed. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.delete()
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun DetailContent(
    transcript: Transcript,
    modifier: Modifier = Modifier,
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MetaPill(formatTimestamp(transcript.createdAt))
            MetaPill(formatDuration(transcript.durationMs))
            MetaPill(engineLabel(transcript.engineId))
        }

        Spacer(Modifier.height(16.dp))

        Card(
            colors = CardDefaults.cardColors(
                containerColor = colors.surfaceVariant.copy(alpha = 0.45f),
            ),
            shape = RoundedCornerShape(20.dp),
        ) {
            SelectionContainer {
                Text(
                    text = transcript.text,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(20.dp),
                )
            }
        }

        transcript.inferenceMs?.let { inferenceMs ->
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Transcribed in ${"%.1f".format(inferenceMs / 1000.0)}s · " +
                    "${formatDuration(transcript.durationMs)} of audio",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        Spacer(Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(onClick = onCopy, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Copy")
            }
            OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Share")
            }
        }

        if (transcript.audioPath != null) {
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.GraphicEq,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = colors.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Audio kept on this device",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun MetaPill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatTimestamp(createdAt: Long): String {
    val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(createdAt))
    val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(createdAt))
    return "$date · $time"
}

private fun Context.copyToClipboard(text: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("OpenFlow transcript", text))
}

private fun Context.share(text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    startActivity(Intent.createChooser(intent, "Share transcript"))
}
