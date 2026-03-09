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
import com.meshcart.ui.viewmodel.JoinUiState
import com.meshcart.ui.viewmodel.JoinViewModel
import com.meshcart.ui.viewmodel.ListsViewModel

@Composable
fun ListsScreen(
    onListClick: (ListId) -> Unit,
    onSettings: () -> Unit,
    autoJoinUri: String? = null,
    listsViewModel: ListsViewModel = hiltViewModel(),
    joinViewModel: JoinViewModel = hiltViewModel()
) {
    val listsState by listsViewModel.uiState.collectAsStateWithLifecycle()
    val joinState  by joinViewModel.state.collectAsStateWithLifecycle()

    var newListName  by remember { mutableStateOf("") }
    var showNewList  by remember { mutableStateOf(false) }
    var showJoin     by remember { mutableStateOf(false) }
    var joinInput    by remember { mutableStateOf("") }

    LaunchedEffect(autoJoinUri) {
        if (autoJoinUri != null) {
            joinInput = autoJoinUri
            showJoin = true
            joinViewModel.submit(autoJoinUri)
        }
    }

    LaunchedEffect(joinState) {
        if (joinState is JoinUiState.Connected) {
            showJoin = false
            joinInput = ""
            joinViewModel.reset()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Background).statusBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().background(Surface)
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
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PulsingDot(size = 7.dp)
                        Text("End-to-end encrypted",
                            style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
                    }
                }
                NodeIdBadge(listsViewModel.myNodeId.value)
                IconButton(onClick = onSettings) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = TextSecondary)
                }
            }

            AnimatedVisibility(showNewList, enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()) {
                Column {
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        MeshInput(
                            value = newListName,
                            onValueChange = { newListName = it },
                            placeholder = "List name…",
                            modifier = Modifier.weight(1f),
                            onDone = {
                                listsViewModel.createList(newListName)
                                newListName = ""
                                showNewList = false
                            }
                        )
                        MeshButton("Add", onClick = {
                            listsViewModel.createList(newListName)
                            newListName = ""
                            showNewList = false
                        })
                    }
                }
            }

            AnimatedVisibility(showJoin, enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()) {
                Column {
                    Spacer(Modifier.height(16.dp))
                    Text("Paste an invite link from the list owner",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        MeshInput(
                            value = joinInput,
                            onValueChange = { joinInput = it },
                            placeholder = "meshcart://sdp?…",
                            modifier = Modifier.weight(1f),
                            onDone = { joinViewModel.submit(joinInput) }
                        )
                        when (joinState) {
                            is JoinUiState.Connecting -> {
                                CircularProgressIndicator(color = Accent, strokeWidth = 2.dp,
                                    modifier = Modifier.size(24.dp))
                            }
                            else -> MeshButton("Join", onClick = { joinViewModel.submit(joinInput) })
                        }
                    }
                    if (joinState is JoinUiState.Error) {
                        Spacer(Modifier.height(6.dp))
                        Text((joinState as JoinUiState.Error).message,
                            style = MaterialTheme.typography.bodySmall.copy(color = Danger))
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (listsState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Accent, strokeWidth = 2.dp)
            }
        } else if (listsState.lists.isEmpty()) {
            EmptyState(
                onCreateClick = { showNewList = true; showJoin = false },
                onJoinClick = { showJoin = true; showNewList = false }
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(listsState.lists, key = { it.id.value }) { list ->
                    ListRow(
                        list = list,
                        isOwner = list.isOwner(listsViewModel.myNodeId),
                        onClick = { onListClick(list.id) },
                        onDelete = { listsViewModel.deleteList(list.id) }
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().background(Surface)
                .navigationBarsPadding().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MeshButton(
                text = if (showJoin) "Cancel" else "Join list",
                onClick = {
                    showJoin = !showJoin
                    showNewList = false
                    joinViewModel.reset()
                },
                modifier = Modifier.weight(1f),
                danger = showJoin
            )
            MeshButton(
                text = if (showNewList) "Cancel" else "+ New list",
                onClick = { showNewList = !showNewList; showJoin = false },
                modifier = Modifier.weight(1f),
                danger = showNewList
            )
        }
    }
}

@Composable
private fun ListRow(
    list: ShoppingList,
    isOwner: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    MeshCard(onClick = onClick) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(list.name, style = MaterialTheme.typography.titleMedium.copy(color = TextPrimary))
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(
                        text = if (isOwner) "Owner" else "Member",
                        textColor = if (isOwner) Accent else TextSecondary,
                        bgColor = if (isOwner) AccentLight else SurfaceWarm
                    )
                    Chip("${list.members.size + 1} peers", TextSecondary, SurfaceWarm)
                }
            }
            if (isOwner) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = TextMuted)
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String, textColor: Color, bgColor: Color) {
    Box(Modifier.background(bgColor, RoundedCornerShape(6.dp))
        .padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(text, style = MaterialTheme.typography.labelSmall.copy(color = textColor))
    }
}

@Composable
private fun EmptyState(onCreateClick: () -> Unit, onJoinClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🛒", fontSize = 56.sp)
        Spacer(Modifier.height(16.dp))
        Text("No lists yet", style = MaterialTheme.typography.titleLarge.copy(color = TextPrimary))
        Spacer(Modifier.height(8.dp))
        Text("Create a new list, or join one from a peer who invited you.",
            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(AccentLight).padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text("ℹ️", fontSize = 16.sp)
            Text("Restored your identity? Your lists will re-appear once you connect to a peer who has them.",
                style = MaterialTheme.typography.bodyMedium.copy(color = AccentDark))
        }
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MeshButton("Join list", onClick = onJoinClick)
            MeshButton("Create list", onClick = onCreateClick)
        }
    }
}