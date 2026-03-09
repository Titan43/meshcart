package com.meshcart.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshcart.identity.domain.Identity
import com.meshcart.list.domain.*
import com.meshcart.sync.domain.ItemId
import com.meshcart.sync.domain.ListId
import com.meshcart.sync.domain.ShoppingItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ListDetailUiState(
    val list: ShoppingList? = null,
    val items: List<ShoppingItem> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class ListDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val listRepository: ShoppingListRepository,
    private val itemRepository: ShoppingItemRepository,
    private val identity: Identity
) : ViewModel() {

    private val listId = ListId(checkNotNull(savedStateHandle["listId"]))

    val uiState: StateFlow<ListDetailUiState> = combine(
        listRepository.observe(listId),
        itemRepository.observe(listId)
    ) { list, items ->
        ListDetailUiState(list = list, items = items, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ListDetailUiState())

    val myNodeId get() = identity.nodeId

    fun addItem(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            runCatching { itemRepository.add(listId, name.trim(), identity.nodeId) }
        }
    }

    fun toggleItem(itemId: ItemId, checked: Boolean) {
        viewModelScope.launch {
            runCatching { itemRepository.check(listId, itemId, checked, identity.nodeId) }
        }
    }

    fun removeItem(itemId: ItemId) {
        viewModelScope.launch {
            runCatching { itemRepository.remove(listId, itemId, identity.nodeId) }
        }
    }

    fun renameList(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            runCatching { listRepository.rename(listId, name.trim(), identity.nodeId) }
        }
    }
}