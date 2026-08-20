package com.storagemanager.ui.junk

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storagemanager.domain.model.SystemJunk
import com.storagemanager.domain.repository.StorageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.storagemanager.R

sealed class SystemJunkState {
    data object Loading : SystemJunkState()
    data class Success(val junks: List<SystemJunk>) : SystemJunkState()
    data class Error(val message: String) : SystemJunkState()
}

@HiltViewModel
class SystemJunkViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val repository: StorageRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<SystemJunkState>(SystemJunkState.Loading)
    val uiState: StateFlow<SystemJunkState> = _uiState.asStateFlow()

    init {
        loadSystemJunk()
    }

    fun loadSystemJunk() {
        viewModelScope.launch {
            _uiState.value = SystemJunkState.Loading
            try {
                val junks = repository.scanSystemJunk()
                _uiState.value = SystemJunkState.Success(junks)
            } catch (e: Exception) {
                _uiState.value = SystemJunkState.Error(e.localizedMessage ?: context.getString(R.string.unknown_error))
            }
        }
    }

    fun cleanAllSystemJunk() {
        val currentState = _uiState.value as? SystemJunkState.Success ?: return
        viewModelScope.launch {
            repository.deleteSystemJunk(currentState.junks)
            loadSystemJunk()
        }
    }
}
