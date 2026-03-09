package com.meshcart.ui.screens

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
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
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshcart.ui.components.*
import com.meshcart.ui.theme.*
import com.meshcart.ui.viewmodel.ShareUiState
import com.meshcart.ui.viewmodel.ShareViewModel

@Composable
fun ShareScreen(
    onBack: () -> Unit,
    viewModel: ShareViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(Background).statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Surface)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, tint = TextSecondary)
            }
            Text("Share list", style = MaterialTheme.typography.titleLarge.copy(color = TextPrimary))
        }
        MeshDivider()

        when (val s = state) {
            is ShareUiState.Generating -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Accent, strokeWidth = 2.dp)
                }
            }
            is ShareUiState.Ready -> ReadyPane(s, viewModel::retry, onBack)
            is ShareUiState.Error -> {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(s.message, style = MaterialTheme.typography.bodyMedium.copy(color = Danger),
                            textAlign = TextAlign.Center)
                        MeshButton("Retry", onClick = viewModel::retry)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadyPane(state: ShareUiState.Ready, onRetry: () -> Unit, onBack: () -> Unit) {
    val context   = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(12.dp))
        Text("Let them scan this QR",
            style = MaterialTheme.typography.headlineSmall.copy(color = TextPrimary))
        Text("Or share the link if they're not nearby. Expires once used.",
            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp, bottom = 24.dp))

        Box(
            modifier = Modifier.size(260.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Surface)
                .border(1.dp, Border, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = state.qr.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(220.dp).clip(RoundedCornerShape(8.dp))
            )
        }

        Spacer(Modifier.height(20.dp))

        Box(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceWarm)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(state.shareUrl,
                style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis)
        }

        Spacer(Modifier.height(16.dp))

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
                    ContextCompat.startActivity(context, Intent.createChooser(intent, null), null)
                },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(12.dp))

        TextButton(onClick = onRetry) {
            Icon(Icons.Default.Refresh, contentDescription = null, tint = TextSecondary,
                modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Generate new invite", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
        }

        Spacer(Modifier.weight(1f))

        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PulsingDot()
            Text("Waiting for peer to scan…",
                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary))
        }
    }
}