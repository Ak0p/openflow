package ro.akop.openflow.feature.home

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import ro.akop.openflow.AppContainer
import ro.akop.openflow.core.data.Transcript
import ro.akop.openflow.ui.engineLabel
import ro.akop.openflow.ui.formatDuration
import java.text.DateFormat
import java.util.Date

private enum class PermissionPrompt { None, Rationale, Denied, Blocked }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    container: AppContainer,
    onDictate: () -> Unit,
    onOpenTranscript: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var hasMicrophone by remember { mutableStateOf(context.hasMicrophone()) }
    var prompt by rememberSaveable { mutableStateOf(PermissionPrompt.None) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasMicrophone = context.hasMicrophone()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasMicrophone = granted
        if (!granted) {
            val canAskAgain = context.findActivity()
                ?.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) == true
            prompt = if (canAskAgain) PermissionPrompt.Denied else PermissionPrompt.Blocked
        }
    }

    fun startDictation() {
        when {
            hasMicrophone -> onDictate()
            else -> prompt = PermissionPrompt.Rationale
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.GraphicEq,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(text = "OpenFlow", style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { startDictation() },
                icon = { Icon(Icons.Outlined.Mic, contentDescription = null) },
                text = { Text("Dictate") },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (state.transcripts.isEmpty() && !state.isLoading) {
                EmptyState(modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Text(
                            text = if (state.transcripts.isEmpty()) "Loading…" else "Recent dictations",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    items(items = state.transcripts, key = { it.id }) { transcript ->
                        TranscriptCard(
                            transcript = transcript,
                            onClick = { onOpenTranscript(transcript.id) },
                        )
                    }
                }
            }
        }
    }

    when (prompt) {
        PermissionPrompt.None -> Unit

        PermissionPrompt.Rationale -> AlertDialog(
            onDismissRequest = { prompt = PermissionPrompt.None },
            title = { Text("Microphone access") },
            text = {
                Text(
                    "OpenFlow records only while you are dictating, and the audio is " +
                        "transcribed on this device. Nothing is uploaded.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        prompt = PermissionPrompt.None
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                ) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = { prompt = PermissionPrompt.None }) { Text("Not now") }
            },
        )

        PermissionPrompt.Denied -> AlertDialog(
            onDismissRequest = { prompt = PermissionPrompt.None },
            title = { Text("Microphone is off") },
            text = { Text("Without the microphone there is nothing to transcribe. You can allow it now.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        prompt = PermissionPrompt.None
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                ) { Text("Try again") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        prompt = PermissionPrompt.None
                        context.openAppSettings()
                    },
                ) { Text("App settings") }
            },
        )

        PermissionPrompt.Blocked -> AlertDialog(
            onDismissRequest = { prompt = PermissionPrompt.None },
            title = { Text("Enable the microphone") },
            text = {
                Text(
                    "The microphone is blocked for OpenFlow. Open the app settings and " +
                        "allow Microphone to start dictating.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        prompt = PermissionPrompt.None
                        context.openAppSettings()
                    },
                ) { Text("Open settings") }
            },
            dismissButton = {
                TextButton(onClick = { prompt = PermissionPrompt.None }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(104.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.GraphicEq,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(46.dp),
            )
        }
        Spacer(Modifier.height(28.dp))
        Text(
            text = "Your voice, on the page",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Tap Dictate, speak naturally, and the text lands here. " +
                "Audio is processed on the phone and never uploaded.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        AssistChip(
            onClick = {},
            label = { Text("On-device · no account") },
        )
    }
}

@Composable
private fun TranscriptCard(transcript: Transcript, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text(
                text = transcript.text,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MetadataText(relativeTimestamp(transcript.createdAt))
                MetadataDot()
                MetadataText(formatDuration(transcript.durationMs))
                MetadataDot()
                MetadataText(engineLabel(transcript.engineId))
            }
        }
    }
}

@Composable
private fun MetadataText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun MetadataDot() {
    Text(
        text = "·",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 6.dp),
    )
}

private fun relativeTimestamp(createdAt: Long): String {
    val now = System.currentTimeMillis()
    val sameDay = (now - createdAt) < 24 * 60 * 60 * 1000L
    return if (sameDay) {
        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(createdAt))
    } else {
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(createdAt))
    }
}

private fun Context.hasMicrophone(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun Context.openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}
