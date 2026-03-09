package com.meshcart.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshcart.ui.components.*
import com.meshcart.ui.theme.*
import com.meshcart.ui.viewmodel.InviteUiState
import com.meshcart.ui.viewmodel.InviteViewModel

@Composable
fun InviteScreen(
    onBack: () -> Unit,
    onConnected: () -> Unit = onBack,
    viewModel: InviteViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        if (state is InviteUiState.Connected) onConnected()
    }

    Column(Modifier.fillMaxSize().background(Background).statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Surface)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextSecondary)
            }
            Text("Invite a peer",
                style = MaterialTheme.typography.titleLarge.copy(color = TextPrimary))
        }
        MeshDivider()

        AnimatedContent(
            targetState = state,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "invite"
        ) { s ->
            when (s) {
                is InviteUiState.Idle            -> RolePane(viewModel::startAsOwner, viewModel::startAsJoiner)
                is InviteUiState.GeneratingOffer -> LoadingPane("Creating invite…")
                is InviteUiState.WaitingForAnswer -> OwnerPane(s, viewModel::onAnswerInput, viewModel::submitAnswer)
                is InviteUiState.EnterOffer      -> JoinerOfferPane(s, viewModel::onOfferInput, viewModel::submitOffer)
                is InviteUiState.WaitingForOwner -> JoinerAnswerPane(s)
                is InviteUiState.Connected       -> LoadingPane("Connected! Syncing…")
                is InviteUiState.Error           -> ErrorPane(s.message, viewModel::retry)
            }
        }
    }
}

// ── Role selection ────────────────────────────────────────────────────────────

@Composable
private fun RolePane(onOwner: () -> Unit, onJoiner: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Connect with a peer",
            style = MaterialTheme.typography.headlineSmall.copy(color = TextPrimary),
            textAlign = TextAlign.Center)
        Text("End-to-end encrypted · Works on any network",
            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 40.dp))

        MeshCard(onClick = onOwner) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("📤", fontSize = 32.sp)
                Column {
                    Text("Invite someone",
                        style = MaterialTheme.typography.titleMedium.copy(color = TextPrimary))
                    Text("Share a link or QR code",
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        MeshCard(onClick = onJoiner) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("📥", fontSize = 32.sp)
                Column {
                    Text("Join a list",
                        style = MaterialTheme.typography.titleMedium.copy(color = TextPrimary))
                    Text("Paste an invite link you received",
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary))
                }
            }
        }
    }
}

// ── Owner: show offer QR + wait for answer ─────────────────────────────────────

@Composable
private fun OwnerPane(
    state: InviteUiState.WaitingForAnswer,
    onAnswerInput: (String) -> Unit,
    onSubmit: () -> Unit
) {
    val context   = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))
        StepLabel(1, "Share your invite")
        Spacer(Modifier.height(12.dp))
        Text("Let the other person scan this QR or paste the link into their app.",
            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))

        QrCard(state.qr)
        Spacer(Modifier.height(16.dp))
        UrlPill(state.shareUrl)
        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MeshButton(
                text = if (copied) "✓ Copied" else "Copy link",
                onClick = { clipboard.setText(AnnotatedString(state.shareUrl)); copied = true },
                modifier = Modifier.weight(1f)
            )
            MeshButton(
                text = "Share",
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, state.shareUrl)
                    }
                    ContextCompat.startActivity(context, Intent.createChooser(intent, "Share invite"), null)
                },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(28.dp))
        MeshDivider()
        Spacer(Modifier.height(24.dp))

        StepLabel(2, "Paste their answer")
        Spacer(Modifier.height(8.dp))
        Text("Once they open your link, they'll get an answer link. Paste it here.",
            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))

        MeshInput(
            value = state.answerInput,
            onValueChange = onAnswerInput,
            placeholder = "meshcart://sdp?t=a&…",
            onDone = { if (state.answerInput.isNotBlank()) onSubmit() }
        )

        state.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium.copy(color = Danger),
                textAlign = TextAlign.Center)
        }

        Spacer(Modifier.height(12.dp))

        if (state.isConnecting) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(color = Accent, strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp))
                Text("Connecting…",
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary))
            }
        } else {
            MeshButton(
                text = "Complete connection",
                onClick = onSubmit,
                enabled = state.answerInput.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ── Joiner step 1: paste offer link ───────────────────────────────────────────

@Composable
private fun JoinerOfferPane(
    state: InviteUiState.EnterOffer,
    onInput: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Paste the invite link",
            style = MaterialTheme.typography.headlineSmall.copy(color = TextPrimary))
        Text("Ask the list owner to share their invite link with you.",
            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp))

        MeshInput(
            value = state.input,
            onValueChange = onInput,
            placeholder = "meshcart://sdp?t=o&…",
            onDone = { if (state.input.isNotBlank()) onSubmit() }
        )

        state.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium.copy(color = Danger),
                textAlign = TextAlign.Center)
        }

        Spacer(Modifier.height(20.dp))

        if (state.isProcessing) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(color = Accent, strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp))
                Text("Processing…",
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary))
            }
        } else {
            MeshButton(
                text = "Connect",
                onClick = onSubmit,
                enabled = state.input.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ── Joiner step 2: show answer link for owner ─────────────────────────────────

@Composable
private fun JoinerAnswerPane(state: InviteUiState.WaitingForOwner) {
    val context   = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))
        StepLabel(2, "Share your answer")
        Spacer(Modifier.height(12.dp))
        Text("Send this to the list owner. Once they paste it, you're connected.",
            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))

        QrCard(state.qr)
        Spacer(Modifier.height(16.dp))
        UrlPill(state.shareUrl)
        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MeshButton(
                text = if (copied) "✓ Copied" else "Copy link",
                onClick = { clipboard.setText(AnnotatedString(state.shareUrl)); copied = true },
                modifier = Modifier.weight(1f)
            )
            MeshButton(
                text = "Share",
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, state.shareUrl)
                    }
                    ContextCompat.startActivity(context, Intent.createChooser(intent, "Share answer"), null)
                },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(color = Accent, strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp))
            Text("Waiting for the owner to complete the connection…",
                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary))
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun LoadingPane(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(color = Accent, strokeWidth = 2.dp)
            Text(message, style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary))
        }
    }
}

@Composable
private fun ErrorPane(message: String, onRetry: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Text("⚠️", fontSize = 40.sp)
        Spacer(Modifier.height(12.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium.copy(color = Danger),
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        MeshButton("Try again", onClick = onRetry, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun QrCard(bitmap: Bitmap?) {
    Box(
        modifier = Modifier.size(240.dp).clip(RoundedCornerShape(20.dp))
            .background(Surface).border(1.dp, Border, RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap == null) {
            CircularProgressIndicator(color = Accent, strokeWidth = 2.dp,
                modifier = Modifier.size(32.dp))
        } else {
            Image(bitmap = bitmap.asImageBitmap(), contentDescription = "QR code",
                modifier = Modifier.size(200.dp).clip(RoundedCornerShape(8.dp)))
        }
    }
}

@Composable
private fun UrlPill(url: String) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(SurfaceWarm).padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(url, style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary),
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StepLabel(step: Int, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier.size(28.dp).clip(RoundedCornerShape(14.dp)).background(AccentLight),
            contentAlignment = Alignment.Center
        ) {
            Text("$step", style = MaterialTheme.typography.labelLarge.copy(color = AccentDark))
        }
        Text(label, style = MaterialTheme.typography.titleMedium.copy(color = TextPrimary))
    }
}