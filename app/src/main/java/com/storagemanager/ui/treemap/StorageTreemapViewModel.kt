package com.storagemanager.ui.treemap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storagemanager.domain.model.StorageTreeNode
import com.storagemanager.domain.repository.StorageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.storagemanager.R

sealed class TreemapState {
    data object Loading : TreemapState()
    data class Success(val rootNode: StorageTreeNode, val currentNode: StorageTreeNode) : TreemapState()
    data class Error(val message: String) : TreemapState()
}

@HiltViewModel
class StorageTreemapViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val repository: StorageRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<TreemapState>(TreemapState.Loading)
    val uiState: StateFlow<TreemapState> = _uiState.asStateFlow()

    private var rootNode: StorageTreeNode? = null

    init {
        loadTreemap()
    }

    fun loadTreemap() {
        viewModelScope.launch {
            _uiState.value = TreemapState.Loading
            try {
                val node = repository.generateTreemap(maxDepth = 3)
                rootNode = node
                _uiState.value = TreemapState.Success(rootNode = node, currentNode = node)
            } catch (e: Exception) {
                _uiState.value = TreemapState.Error(e.localizedMessage ?: context.getString(R.string.unknown_error))
            }
        }
    }

    fun selectNode(node: StorageTreeNode) {
        val root = rootNode ?: return
        if (node.isDirectory && node.children.isNotEmpty()) {
            _uiState.value = TreemapState.Success(rootNode = root, currentNode = node)
        }
    }

    fun navigateUp(): Boolean {
        val currentState = _uiState.value as? TreemapState.Success ?: return false
        val root = rootNode ?: return false

        if (currentState.currentNode.path == root.path) {
            return false // Already at root
        }

        // Find parent of current node
        val parent = findParentNode(root, currentState.currentNode.path) ?: root
        _uiState.value = TreemapState.Success(rootNode = root, currentNode = parent)
        return true
    }

    private fun findParentNode(current: StorageTreeNode, targetPath: String): StorageTreeNode? {
        for (child in current.children) {
            if (child.path == targetPath) return current
            if (child.isDirectory) {
                val found = findParentNode(child, targetPath)
                if (found != null) return found
            }
        }
        return null
    }
}
