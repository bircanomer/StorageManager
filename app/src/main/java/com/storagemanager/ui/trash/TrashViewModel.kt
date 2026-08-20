package com.storagemanager.ui.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storagemanager.domain.model.TrashItem
import com.storagemanager.domain.repository.StorageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val repository: StorageRepository
) : ViewModel() {

    val trashItems: StateFlow<List<TrashItem>> = repository.getTrashItems()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun restoreItem(trashId: Long) {
        viewModelScope.launch {
            repository.restoreFromTrash(trashId)
        }
    }

    fun deletePermanently(trashId: Long) {
        viewModelScope.launch {
            repository.deletePermanentlyFromTrash(trashId)
        }
    }

    fun clearAllTrash() {
        viewModelScope.launch {
            repository.clearTrash()
        }
    }
}
