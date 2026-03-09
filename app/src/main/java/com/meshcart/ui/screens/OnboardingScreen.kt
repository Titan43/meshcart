package com.meshcart.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshcart.ui.components.*
import com.meshcart.ui.theme.*
import com.meshcart.ui.viewmodel.OnboardingUiState
import com.meshcart.ui.viewmodel.OnboardingViewModel

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        if (state is OnboardingUiState.Complete) onComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Use key= so panes are not recomposed into each other, preserving local state
        when (val s = state) {
            is OnboardingUiState.Loading  -> LoadingPane()
            is OnboardingUiState.ShowNew  -> NewPhrasePane(
                state = s,
                onConfirm = viewModel::confirmBackup,
                onRestore = viewModel::switchToRestore
            )
            is OnboardingUiState.Restore  -> RestorePane(
                error = s.error,
                isLoading = s.isLoading,
                onSubmit = viewModel::submitRestore,
                onBack = viewModel::switchToNew
            )
            is OnboardingUiState.Complete -> LoadingPane()
        }
    }
}

@Composable
private fun LoadingPane() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Accent, strokeWidth = 2.dp)
    }
}

// ─── New phrase pane ──────────────────────────────────────────────────────────

@Composable
private fun NewPhrasePane(
    state: OnboardingUiState.ShowNew,
    onConfirm: () -> Unit,
    onRestore: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    var checkedBackup by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(12.dp))
        Text(
            "Your secret phrase",
            style = MaterialTheme.typography.displayMedium.copy(color = TextPrimary),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "24 words · BIP39 · Never leaves this device",
            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))

        WarningBanner("Write these words down in order. Anyone with this phrase controls your identity.")
        Spacer(Modifier.height(24.dp))

        WordGrid(words = state.words)
        Spacer(Modifier.height(16.dp))

        MeshButton(
            text = if (copied) "✓ Copied!" else "Copy to clipboard",
            onClick = {
                clipboard.setText(AnnotatedString(state.words.joinToString(" ")))
                copied = true
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(32.dp))
        MeshDivider()
        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { checkedBackup = !checkedBackup }
                .background(if (checkedBackup) SuccessLight else SurfaceWarm)
                .border(1.5.dp, if (checkedBackup) Success else Border, RoundedCornerShape(12.dp))
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            CheckBox(checked = checkedBackup)
            Text(
                "I've written down my phrase and stored it somewhere safe. I understand it can't be recovered if lost.",
                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(16.dp))

        MeshButton(
            text = "Continue →",
            onClick = onConfirm,
            enabled = checkedBackup,
            modifier = Modifier.fillMaxWidth()
        )

        if (!checkedBackup) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Confirm you've backed up your phrase first",
                style = MaterialTheme.typography.labelSmall.copy(color = TextMuted),
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(32.dp))
        MeshDivider()
        Spacer(Modifier.height(20.dp))

        TextButton(onClick = onRestore) {
            Text(
                "Restore from existing phrase",
                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
            )
        }
    }
}

// ─── Restore pane ─────────────────────────────────────────────────────────────
// Local text state is intentional — avoids the focus-loss bug where hoisting
// every keystroke through the ViewModel's sealed state triggers AnimatedContent
// to tear down and rebuild the composable tree.

@Composable
private fun RestorePane(
    error: String?,
    isLoading: Boolean,
    onSubmit: (String) -> Unit,
    onBack: () -> Unit
) {
    // Local state — NOT driven from ViewModel until submit
    var input by remember { mutableStateOf("") }

    val wordCount = remember(input) {
        if (input.isBlank()) 0 else input.trim().split(Regex("\\s+")).size
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(12.dp))
        Text(
            "Restore identity",
            style = MaterialTheme.typography.displayMedium.copy(color = TextPrimary),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Enter your 24 BIP39 words, separated by spaces",
            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(40.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Phrase", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted))
            Text(
                "$wordCount / 24",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = if (wordCount == 24) Success else TextMuted,
                    fontWeight = if (wordCount == 24) FontWeight.Bold else FontWeight.Normal
                )
            )
        }
        Spacer(Modifier.height(8.dp))

        BasicTextField(
            value = input,
            onValueChange = { input = it },
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = TextPrimary,
                lineHeight = 26.sp
            ),
            cursorBrush = SolidColor(Accent),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrect = false,
                keyboardType = KeyboardType.Text
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp)
                .background(Surface, RoundedCornerShape(12.dp))
                .border(
                    1.5.dp,
                    when {
                        error != null  -> Danger
                        wordCount == 24 -> Success
                        else           -> Border
                    },
                    RoundedCornerShape(12.dp)
                )
                .padding(16.dp),
            decorationBox = { inner ->
                if (input.isEmpty()) {
                    Text(
                        "word1 word2 word3 … word24",
                        style = MaterialTheme.typography.bodyLarge.copy(color = TextMuted)
                    )
                }
                inner()
            }
        )

        AnimatedVisibility(visible = error != null) {
            Column {
                Spacer(Modifier.height(8.dp))
                Text(
                    error ?: "",
                    style = MaterialTheme.typography.bodyMedium.copy(color = Danger)
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        if (isLoading) {
            CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(36.dp))
        } else {
            MeshButton(
                text = "Restore identity",
                onClick = { onSubmit(input) },
                enabled = wordCount == 24,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(20.dp))

        TextButton(onClick = onBack) {
            Text("← Back", style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary))
        }
    }
}

// ─── Sub-components ───────────────────────────────────────────────────────────

@Composable
private fun WordGrid(words: List<String>) {
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 5f), 0f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .drawBehind {
                drawRect(color = Border, style = Stroke(width = 1.dp.toPx(), pathEffect = dashEffect))
            }
            .background(Surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        words.chunked(4).forEachIndexed { rowIndex, rowWords ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowWords.forEachIndexed { colIndex, word ->
                    WordChip(index = rowIndex * 4 + colIndex + 1, word = word, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun WordChip(index: Int, word: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(SurfaceWarm, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text("%02d".format(index), style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 9.sp))
        Text(word, style = MaterialTheme.typography.bodyMedium.copy(color = AccentDark, fontWeight = FontWeight.Bold), maxLines = 1)
    }
}

@Composable
private fun WarningBanner(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFFFF4ED))
            .border(1.dp, Accent.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text("⚠️", style = MaterialTheme.typography.bodyMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium.copy(color = AccentDark))
    }
}

@Composable
private fun CheckBox(checked: Boolean) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (checked) Success else Surface)
            .border(1.5.dp, if (checked) Success else Border, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (checked) Text("✓", style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontSize = 11.sp))
    }
}