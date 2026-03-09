package com.meshcart.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshcart.identity.domain.Identity
import com.meshcart.list.domain.*
import com.meshcart.sync.domain.ListId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ListsUiState(
    val lists: List<ShoppingList> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class ListsViewModel @Inject constructor(
    private val listRepository: ShoppingListRepository,
    private val identity: Identity
) : ViewModel() {

    val uiState: StateFlow<ListsUiState> = listRepository.observeAll()
        .map { ListsUiState(lists = it, isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ListsUiState())

    val myNodeId get() = identity.nodeId

    fun createList(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            listRepository.create(name.trim(), identity.nodeId)
        }
    }

    fun deleteList(listId: ListId) {
        viewModelScope.launch {
            runCatching { listRepository.delete(listId, identity.nodeId) }
        }
    }
}