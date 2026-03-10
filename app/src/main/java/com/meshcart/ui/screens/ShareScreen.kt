package com.meshcart.ui.screens

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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

        // Top bar
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
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(color = Accent, strokeWidth = 2.dp)
                        Text("Preparing invite…",
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary))
                    }
                }
            }

            is ShareUiState.Ready -> {
                val clipboard = LocalClipboardManager.current
                var copied by remember { mutableStateOf(false) }

                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(16.dp))
                    Text("Share this code",
                        style = MaterialTheme.typography.headlineSmall.copy(color = TextPrimary))
                    Text("The other person taps Join and enters this code.",
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp, bottom = 28.dp))

                    // Big bold code
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Surface)
                            .border(1.dp, Border, RoundedCornerShape(16.dp))
                            .padding(horizontal = 32.dp, vertical = 20.dp)
                    ) {
                        Text(
                            text = s.code,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 8.sp,
                            color = Accent
                        )
                    }

                    Spacer(Modifier.height(24.dp))

                    // QR alternative
                    Box(
                        modifier = Modifier.size(180.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Surface)
                            .border(1.dp, Border, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = s.qr.asImageBitmap(),
                            contentDescription = "QR code",
                            modifier = Modifier.size(140.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("or scan QR",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))

                    Spacer(Modifier.weight(1f))

                    MeshButton(
                        text = if (copied) "✓ Copied" else "Copy code",
                        onClick = { clipboard.setText(AnnotatedString(s.code)); copied = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PulsingDot()
                        Text("Waiting for them to enter the code…",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
                    }
                }
            }

            is ShareUiState.Connecting -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(color = Accent, strokeWidth = 2.dp)
                        Text("Connecting…",
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary))
                    }
                }
            }

            is ShareUiState.Connected -> {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("✓ Connected!",
                            style = MaterialTheme.typography.headlineSmall.copy(color = Accent))
                        Text("They now have access to this list.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
                            textAlign = TextAlign.Center)
                        MeshButton("Done", onClick = onBack)
                    }
                }
            }

            is ShareUiState.Error -> {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(s.message,
                            style = MaterialTheme.typography.bodyMedium.copy(color = Danger),
                            textAlign = TextAlign.Center)
                        MeshButton("Try again", onClick = viewModel::retry)
                        TextButton(onClick = onBack) {
                            Text("Cancel",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
                        }
                    }
                }
            }
        }
    }
}