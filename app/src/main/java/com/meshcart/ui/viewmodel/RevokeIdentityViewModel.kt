package com.meshcart.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshcart.persistence.domain.IdentityStoragePort
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface RevokeUiState {
    data object Idle : RevokeUiState
    data object Confirming : RevokeUiState
    data object Done : RevokeUiState
}

@HiltViewModel
class RevokeIdentityViewModel @Inject constructor(
    private val storage: IdentityStoragePort
) : ViewModel() {

    private val _state = MutableStateFlow<RevokeUiState>(RevokeUiState.Idle)
    val state: StateFlow<RevokeUiState> = _state.asStateFlow()

    fun requestRevoke() {
        _state.value = RevokeUiState.Confirming
    }

    fun cancelRevoke() {
        _state.value = RevokeUiState.Idle
    }

    fun confirmRevoke() {
        viewModelScope.launch {
            storage.clear()
            _state.value = RevokeUiState.Done
        }
    }
}