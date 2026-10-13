package com.example.vani.ui.overlay

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vani.ui.theme.VaniTheme

// ---------- State ----------

enum class ListeningPhase { Listening, Processing, Success, Error }

data class ListeningUiState(
    val phase: ListeningPhase = ListeningPhase.Listening,
    val transcript: String = "",
    val message: String = "",
)

// ---------- Floating card ----------

@Composable
fun ListeningOverlay(
    state: ListeningUiState,
    onClose: () -> Unit,
    onRetry: () -> Unit = {},
) {
    val c = VaniTheme.colors
    val shape = RoundedCornerShape(28.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(16.dp, shape)
                .background(c.card, shape)
                .border(1.dp, c.cardBorder, shape)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // header
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            Brush.linearGradient(listOf(c.primaryLight, c.primaryDark)),
                            RoundedCornerShape(10.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.GraphicEq, null, tint = Color.White, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text("VANI", color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Close, "Close", tint = c.textSecondary, modifier = Modifier.size(20.dp))
                }
            }

            Orb(state.phase, onRetry)

            // waveform area keeps its height so the card doesn't jump between phases
            Box(Modifier.height(34.dp), contentAlignment = Alignment.Center) {
                if (state.phase == ListeningPhase.Listening) Waveform()
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = when (state.phase) {
                    ListeningPhase.Listening -> "Listening…"
                    ListeningPhase.Processing -> "Working on it…"
                    ListeningPhase.Success -> state.message.ifBlank { "Done" }
                    ListeningPhase.Error -> state.message.ifBlank { "Didn't catch that" }
                },
                color = c.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = when {
                    state.transcript.isNotBlank() -> "“${state.transcript}”"
                    state.phase == ListeningPhase.Error -> "Tap the mic to try again"
                    else -> "Try saying “Open YouTube”"
                },
                color = if (state.transcript.isNotBlank()) c.textPrimary else c.textSecondary,
                fontSize = if (state.transcript.isNotBlank()) 16.sp else 14.sp,
                fontWeight = if (state.transcript.isNotBlank()) FontWeight.Medium else FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}

// ---------- Orb ----------

@Composable
private fun Orb(phase: ListeningPhase, onRetry: () -> Unit) {
    val c = VaniTheme.colors
    val transition = rememberInfiniteTransition(label = "orb")
    val ring by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label = "ring",
    )

    val brush = when (phase) {
        ListeningPhase.Success -> Brush.linearGradient(listOf(Color(0xFF34D399), Color(0xFF059669)))
        ListeningPhase.Error -> Brush.linearGradient(listOf(Color(0xFFFB7185), Color(0xFFE11D48)))
        else -> Brush.linearGradient(listOf(c.primaryLight, c.primaryDark))
    }

    Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
        if (phase == ListeningPhase.Listening) {
            repeat(2) { i ->
                val p = (ring + i * 0.5f) % 1f
                Box(
                    Modifier
                        .size(88.dp)
                        .scale(1f + p * 0.6f)
                        .alpha((1f - p) * 0.35f)
                        .background(c.primary, CircleShape),
                )
            }
        }
        Box(
            modifier = Modifier
                .size(88.dp)
                .shadow(18.dp, CircleShape, ambientColor = c.primary, spotColor = c.primary)
                .clip(CircleShape)
                .background(brush)
                .clickable(enabled = phase == ListeningPhase.Error, onClick = onRetry),
            contentAlignment = Alignment.Center,
        ) {
            when (phase) {
                ListeningPhase.Processing -> CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(34.dp),
                )
                ListeningPhase.Success -> Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(42.dp))
                else -> Icon(Icons.Filled.Mic, "Microphone", tint = Color.White, modifier = Modifier.size(40.dp))
            }
        }
    }
}

// ---------- Waveform ----------

@Composable
private fun Waveform() {
    val c = VaniTheme.colors
    val transition = rememberInfiniteTransition(label = "wave")
    Row(
        modifier = Modifier.height(34.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(9) { i ->
            val h by transition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(450 + (i % 4) * 110, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(i * 60),
                ),
                label = "bar$i",
            )
            Box(
                Modifier
                    .width(5.dp)
                    .fillMaxHeight(h)
                    .background(
                        Brush.verticalGradient(listOf(c.primaryLight, c.primaryDark)),
                        RoundedCornerShape(50),
                    ),
            )
        }
    }
}

// ---------- Previews ----------

@Preview(showBackground = true, widthDp = 393)
@Composable
private fun ListeningLightPreview() {
    VaniTheme(darkTheme = false) {
        ListeningOverlay(ListeningUiState(ListeningPhase.Listening, "Open YouTube"), onClose = {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0B18, widthDp = 393)
@Composable
private fun ListeningDarkPreview() {
    VaniTheme(darkTheme = true) {
        ListeningOverlay(ListeningUiState(ListeningPhase.Listening), onClose = {})
    }
}

@Preview(showBackground = true, widthDp = 393)
@Composable
private fun SuccessPreview() {
    VaniTheme(darkTheme = false) {
        ListeningOverlay(ListeningUiState(ListeningPhase.Success, "Turn on torch"), onClose = {})
    }
}

@Preview(showBackground = true, widthDp = 393)
@Composable
private fun ErrorPreview() {
    VaniTheme(darkTheme = false) {
        ListeningOverlay(ListeningUiState(ListeningPhase.Error), onClose = {})
    }
}