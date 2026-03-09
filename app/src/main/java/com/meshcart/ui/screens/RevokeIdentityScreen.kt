package com.meshcart.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshcart.ui.components.*
import com.meshcart.ui.theme.*
import com.meshcart.ui.viewmodel.RevokeIdentityViewModel
import com.meshcart.ui.viewmodel.RevokeUiState

@Composable
fun RevokeIdentityScreen(
    onBack: () -> Unit,
    onRevoked: () -> Unit,
    viewModel: RevokeIdentityViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        if (state is RevokeUiState.Done) onRevoked()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .statusBarsPadding()
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextSecondary)
            }
            Text("Identity", style = MaterialTheme.typography.titleLarge.copy(color = TextPrimary))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFFFFF0F0)),
                contentAlignment = Alignment.Center
            ) {
                Text("🔑", fontSize = 36.sp)
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "Revoke identity",
                style = MaterialTheme.typography.displayMedium.copy(color = TextPrimary),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "This will permanently erase your identity from this device. You can restore it later using your 24-word phrase.",
                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(40.dp))

            // Consequence list
            ConsequenceItem("🚫", "You will be signed out immediately")
            Spacer(Modifier.height(10.dp))
            ConsequenceItem("💬", "Shared lists will remain on peers' devices")
            Spacer(Modifier.height(10.dp))
            ConsequenceItem("🔄", "Restore anytime with your secret phrase")

            Spacer(Modifier.height(48.dp))

            when (state) {
                is RevokeUiState.Idle -> {
                    MeshButton(
                        text = "Revoke this identity",
                        onClick = viewModel::requestRevoke,
                        danger = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                        Text("Cancel", style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary))
                    }
                }

                is RevokeUiState.Confirming -> {
                    // Second confirmation — separate tap required
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFFFF0F0))
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Are you absolutely sure?",
                            style = MaterialTheme.typography.titleMedium.copy(color = Danger)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "This device will forget your identity. Make sure you have your 24-word backup phrase before continuing.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(20.dp))
                        MeshButton(
                            text = "Yes, erase my identity",
                            onClick = viewModel::confirmRevoke,
                            danger = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        TextButton(onClick = viewModel::cancelRevoke, modifier = Modifier.fillMaxWidth()) {
                            Text("No, keep it", style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary))
                        }
                    }
                }

                else -> {
                    CircularProgressIndicator(color = Danger, strokeWidth = 2.dp)
                }
            }
        }
    }
}

@Composable
private fun ConsequenceItem(emoji: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceWarm)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 20.sp)
        Text(text, style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary))
    }
}