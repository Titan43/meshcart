package com.meshcart.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshcart.identity.domain.Identity
import com.meshcart.identity.domain.IdentityRepository
import com.meshcart.identity.domain.InvalidMnemonicException
import com.meshcart.identity.mnemonic.MnemonicPhrase
import com.meshcart.persistence.domain.IdentityStoragePort
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface OnboardingUiState {
    data object Loading : OnboardingUiState
    data object Complete : OnboardingUiState
    data class ShowNew(val words: List<String>) : OnboardingUiState
    data class Restore(
        val error: String? = null,
        val isLoading: Boolean = false
    ) : OnboardingUiState
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val repository: IdentityRepository,
    private val storage: IdentityStoragePort
) : ViewModel() {

    private val _state = MutableStateFlow<OnboardingUiState>(OnboardingUiState.Loading)
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    private var pendingIdentity: Identity? = null

    init {
        viewModelScope.launch {
            if (storage.load() != null) {
                _state.value = OnboardingUiState.Complete
            } else {
                val identity = repository.create()
                pendingIdentity = identity
                _state.value = OnboardingUiState.ShowNew(identity.mnemonic.value.split(" "))
            }
        }
    }

    fun confirmBackup() {
        val identity = pendingIdentity ?: return
        viewModelScope.launch {
            storage.save(identity.mnemonic)
            _state.value = OnboardingUiState.Complete
        }
    }

    fun switchToRestore() {
        _state.value = OnboardingUiState.Restore()
    }

    fun switchToNew() {
        val identity = pendingIdentity ?: repository.create().also { pendingIdentity = it }
        _state.value = OnboardingUiState.ShowNew(identity.mnemonic.value.split(" "))
    }

    // phrase is passed in directly from local UI state — no keystroke hoisting
    fun submitRestore(phrase: String) {
        val normalized = phrase.trim().replace(Regex("\\s+"), " ")
        _state.value = OnboardingUiState.Restore(isLoading = true)
        viewModelScope.launch {
            try {
                val identity = repository.restore(MnemonicPhrase(normalized))
                storage.save(identity.mnemonic)
                _state.value = OnboardingUiState.Complete
            } catch (e: InvalidMnemonicException) {
                _state.value = OnboardingUiState.Restore(error = "Invalid phrase — check all 24 words and their order.")
            }
        }
    }
}