package com.meshcart.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
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
import com.meshcart.list.domain.ShoppingList
import com.meshcart.sync.domain.ListId
import com.meshcart.ui.components.*
import com.meshcart.ui.theme.*
import com.meshcart.ui.viewmodel.ListsViewModel

@Composable
fun ListsScreen(
    onListClick: (ListId) -> Unit,
    onSettings: () -> Unit,
    viewModel: ListsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var newListName by remember { mutableStateOf("") }
    var showInput by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .statusBarsPadding()
    ) {
        // Header card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface)
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("My Lists", style = MaterialTheme.typography.displayMedium.copy(color = TextPrimary))
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PulsingDot(size = 7.dp)
                        Text("End-to-end encrypted", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
                    }
                }
                NodeIdBadge(viewModel.myNodeId.value)
                IconButton(onClick = onSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextSecondary)
                }
            }

            AnimatedVisibility(visible = showInput, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column {
                    Spacer(Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        MeshInput(value = newListName, onValueChange = { newListName = it }, placeholder = "List name…", modifier = Modifier.weight(1f),
                            onDone = { viewModel.createList(newListName); newListName = ""; showInput = false })
                        MeshButton("Add", onClick = { viewModel.createList(newListName); newListName = ""; showInput = false })
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Accent, strokeWidth = 2.dp)
            }
        } else if (state.lists.isEmpty()) {
            EmptyState(onCreateClick = { showInput = true })
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.lists, key = { it.id.value }) { list ->
                    ListRow(list = list, isOwner = list.isOwner(viewModel.myNodeId), onClick = { onListClick(list.id) }, onDelete = { viewModel.deleteList(list.id) })
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth().background(Surface).navigationBarsPadding().padding(16.dp), contentAlignment = Alignment.CenterEnd) {
            MeshButton(text = if (showInput) "Cancel" else "+ New list", onClick = { showInput = !showInput }, danger = showInput)
        }
    }
}

@Composable
private fun ListRow(list: ShoppingList, isOwner: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    MeshCard(onClick = onClick) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(list.name, style = MaterialTheme.typography.titleMedium.copy(color = TextPrimary))
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(text = if (isOwner) "Owner" else "Member", textColor = if (isOwner) Accent else TextSecondary, bgColor = if (isOwner) AccentLight else SurfaceWarm)
                    Chip(text = "${list.members.size + 1} peers", textColor = TextSecondary, bgColor = SurfaceWarm)
                }
            }
            if (isOwner) {
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = TextMuted) }
            }
        }
    }
}

@Composable
private fun Chip(text: String, textColor: Color, bgColor: Color) {
    Box(modifier = Modifier.background(bgColor, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(text, style = MaterialTheme.typography.labelSmall.copy(color = textColor))
    }
}

@Composable
private fun EmptyState(onCreateClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🛒", fontSize = 56.sp)
        Spacer(Modifier.height(16.dp))
        Text("No lists yet", style = MaterialTheme.typography.titleLarge.copy(color = TextPrimary))
        Spacer(Modifier.height(8.dp))
        Text(
            "Create a new list, or connect to a peer\nwho has invited you to theirs.",
            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        // Explain restore behaviour clearly
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(AccentLight)
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text("ℹ️", fontSize = 16.sp)
            Text(
                "Restored your identity? Your lists will re-appear automatically once you connect to a peer who has them.",
                style = MaterialTheme.typography.bodyMedium.copy(color = AccentDark)
            )
        }
        Spacer(Modifier.height(32.dp))
        MeshButton("Create your first list", onClick = onCreateClick)
    }
}