package com.meshcart.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshcart.sync.domain.ItemId
import com.meshcart.sync.domain.ShoppingItem
import com.meshcart.ui.components.*
import com.meshcart.ui.theme.*
import com.meshcart.ui.viewmodel.ListDetailViewModel

@Composable
fun ListDetailScreen(
    onBack: () -> Unit,
    onShareList: () -> Unit,
    viewModel: ListDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var newItemName by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .statusBarsPadding()
    ) {
        // Top bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextSecondary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(state.list?.name ?: "…", style = MaterialTheme.typography.titleLarge.copy(color = TextPrimary))
                    Text(
                        "${state.items.count { !it.checked }} remaining · ${state.items.size} total",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                    )
                }
                IconButton(onClick = onShareList) {
                    Icon(Icons.Default.Share, contentDescription = "Invite peer", tint = Accent)
                }
            }

            // Add item row
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MeshInput(
                    value = newItemName,
                    onValueChange = { newItemName = it },
                    placeholder = "Add an item…",
                    modifier = Modifier.weight(1f),
                    onDone = { viewModel.addItem(newItemName); newItemName = "" }
                )
                MeshButton("Add", onClick = { viewModel.addItem(newItemName); newItemName = "" })
            }
        }

        Spacer(Modifier.height(8.dp))

        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Accent, strokeWidth = 2.dp)
            }
        } else if (state.items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🧺", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("Your list is empty", style = MaterialTheme.typography.titleMedium.copy(color = TextSecondary))
                    Spacer(Modifier.height(4.dp))
                    Text("Add items using the field above", style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted))
                }
            }
        } else {
            val unchecked = state.items.filter { !it.checked }
            val checked   = state.items.filter { it.checked }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(unchecked, key = { it.id.value }) { item ->
                    ItemRow(item = item, onToggle = { viewModel.toggleItem(item.id, !item.checked) }, onRemove = { viewModel.removeItem(item.id) })
                }
                if (checked.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            HorizontalDivider(modifier = Modifier.weight(1f), color = Border)
                            Text("Done", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted))
                            HorizontalDivider(modifier = Modifier.weight(1f), color = Border)
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                    items(checked, key = { it.id.value }) { item ->
                        ItemRow(item = item, onToggle = { viewModel.toggleItem(item.id, !item.checked) }, onRemove = { viewModel.removeItem(item.id) })
                    }
                }
            }
        }

        // Peers strip
        state.list?.let { list ->
            MeshDivider()
            Row(
                modifier = Modifier.fillMaxWidth().background(Surface).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Peers", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted))
                NodeIdBadge(list.ownerId.value)
                list.members.forEach { NodeIdBadge(it.value) }
            }
        }
    }
}

@Composable
private fun ItemRow(item: ShoppingItem, onToggle: () -> Unit, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .clickable { onToggle() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Round checkbox
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(if (item.checked) SuccessLight else SurfaceWarm),
            contentAlignment = Alignment.Center
        ) {
            if (item.checked) {
                Text("✓", style = MaterialTheme.typography.labelSmall.copy(color = Success, fontSize = 11.sp))
            }
        }

        Text(
            text = item.name,
            style = MaterialTheme.typography.bodyLarge.copy(
                color = if (item.checked) TextMuted else TextPrimary,
                textDecoration = if (item.checked) TextDecoration.LineThrough else TextDecoration.None
            ),
            modifier = Modifier.weight(1f)
        )

        IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Remove", tint = TextMuted, modifier = Modifier.size(16.dp))
        }
    }
}