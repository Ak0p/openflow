package ro.akop.openflow.feature.record

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.Canvas
import ro.akop.openflow.AppContainer
import ro.akop.openflow.ui.formatElapsed
import kotlin.math.sin

@Composable
fun RecordScreen(
    container: AppContainer,
    onSaved: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val viewModel: RecordViewModel = viewModel(factory = RecordViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.start() }

    LaunchedEffect(Unit) {
        viewModel.saved.collect { id -> onSaved(id) }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.interrupt() }

    BackHandler {
        viewModel.cancel()
        onDismiss()
    }

    RecordContent(
        state = state,
        onStop = viewModel::stop,
        onRetry = viewModel::start,
        onClose = {
            viewModel.cancel()
            onDismiss()
        },
    )
}

@Composable
private fun RecordContent(
    state: RecordUiState,
    onStop: () -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to colors.background,
                    1f to colors.surfaceVariant.copy(alpha = 0.35f),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Close, contentDescription = "Discard dictation")
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "New dictation",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                AssistChip(
                    onClick = {},
                    label = { Text(state.engineName, style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.GraphicEq,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                    },
                )
            }

            Spacer(Modifier.weight(1f))

            MicOrb(state = state)

            Spacer(Modifier.height(28.dp))

            Text(
                text = formatElapsed(state.elapsedMs),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Light,
                letterSpacing = 2.sp,
            )

            Spacer(Modifier.height(16.dp))

            LevelBars(
                level = state.level,
                active = state.phase == RecordPhase.Recording,
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(44.dp),
            )

            Spacer(Modifier.height(24.dp))

            AnimatedVisibility(
                visible = state.previewText.isNotBlank() && state.phase != RecordPhase.Failed,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Text(
                    text = state.previewText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }

            Spacer(Modifier.weight(1f))

            BottomControls(
                state = state,
                onStop = onStop,
                onRetry = onRetry,
                onClose = onClose,
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun MicOrb(state: RecordUiState) {
    val colors = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "mic-pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    val recording = state.phase == RecordPhase.Recording
    val scale = if (recording) pulse + state.level * 0.10f else 1f
    val failed = state.phase == RecordPhase.Failed

    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(210.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(
                    (if (failed) colors.errorContainer else colors.primaryContainer)
                        .copy(alpha = 0.55f),
                ),
        )

        Box(
            modifier = Modifier
                .size(132.dp)
                .clip(CircleShape)
                .background(if (failed) colors.errorContainer else colors.primary),
            contentAlignment = Alignment.Center,
        ) {
            when {
                state.phase == RecordPhase.Transcribing || state.phase == RecordPhase.Preparing -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(42.dp),
                        color = colors.onPrimary,
                        strokeWidth = 3.dp,
                    )
                }

                failed -> Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = colors.onErrorContainer,
                    modifier = Modifier.size(54.dp),
                )

                else -> Icon(
                    imageVector = Icons.Outlined.Mic,
                    contentDescription = null,
                    tint = colors.onPrimary,
                    modifier = Modifier.size(54.dp),
                )
            }
        }
    }
}

@Composable
private fun LevelBars(level: Float, active: Boolean, modifier: Modifier = Modifier) {
    val animatedLevel by animateFloatAsState(
        targetValue = if (active) level else 0f,
        animationSpec = tween(durationMillis = 120),
        label = "level",
    )
    val barColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier) {
        val barWidth = 3.dp.toPx()
        val gap = 5.dp.toPx()
        val bars = 28
        val totalWidth = bars * barWidth + (bars - 1) * gap
        var x = (size.width - totalWidth) / 2f

        repeat(bars) { index ->
            val shape = (sin(index * 0.75f) + 1f) / 2f
            val barHeight = (size.height * (0.08f + 0.92f * animatedLevel * (0.25f + 0.75f * shape)))
                .coerceAtLeast(size.height * 0.08f)
            drawRoundRect(
                color = barColor.copy(alpha = 0.85f),
                topLeft = Offset(x, (size.height - barHeight) / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
            x += barWidth + gap
        }
    }
}

@Composable
private fun BottomControls(
    state: RecordUiState,
    onStop: () -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    when (state.phase) {
        RecordPhase.Recording -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(colors.primary)
                        .clickable(onClick = onStop),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Stop,
                        contentDescription = "Stop recording",
                        tint = colors.onPrimary,
                        modifier = Modifier.size(32.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Tap to stop",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }

        RecordPhase.Transcribing -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Transcribing on device…",
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "No audio leaves the phone.",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }

        RecordPhase.Failed -> {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = state.error ?: "Something went wrong.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surfaceVariant.copy(alpha = 0.5f))
                        .padding(16.dp),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onClose) { Text("Close") }
                    FilledTonalButton(onClick = onRetry) { Text("Try again") }
                }
            }
        }

        else -> {
            Text(
                text = "Getting ready…",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
        }
    }
}
