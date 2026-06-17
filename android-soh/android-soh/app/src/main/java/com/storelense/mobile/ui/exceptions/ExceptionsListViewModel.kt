package com.storelense.mobile.ui.exceptions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storelense.mobile.data.remote.dto.ExceptionItemDto
import com.storelense.mobile.data.repository.AuthRepository
import com.storelense.mobile.data.repository.ExceptionRepository
import com.storelense.mobile.data.repository.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val PAGE_SIZE = 50

data class ExceptionsListState(
    val type: String                   = "",
    val items: List<ExceptionItemDto>  = emptyList(),
    val isLoading: Boolean             = false,
    val isLoadingMore: Boolean         = false,
    val hasMore: Boolean               = true,
    val error: String?                 = null
)

@HiltViewModel
class ExceptionsListViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: ExceptionRepository,
    private val auth: AuthRepository
) : ViewModel() {

    private val type: String = savedState["type"] ?: ""
    private var page = 0

    private val _state = MutableStateFlow(ExceptionsListState(type = type))
    val state = _state.asStateFlow()

    init { load(reset = true) }

    fun refresh() = load(reset = true)

    fun loadMore() {
        if (_state.value.isLoadingMore || !_state.value.hasMore) return
        load(reset = false)
    }

    private fun load(reset: Boolean) {
        val storeId = auth.storeId ?: return
        if (reset) {
            page = 0
            _state.update { it.copy(isLoading = true, error = null, items = emptyList(), hasMore = true) }
        } else {
            _state.update { it.copy(isLoadingMore = true) }
        }
        viewModelScope.launch {
            when (val r = repo.listByType(storeId, type, page)) {
                is Result.Success -> {
                    val newItems = r.data
                    page++
                    _state.update { s ->
                        s.copy(
                            items         = if (reset) newItems else s.items + newItems,
                            isLoading     = false,
                            isLoadingMore = false,
                            hasMore       = newItems.size >= PAGE_SIZE
                        )
                    }
                }
                is Result.Error -> _state.update {
                    it.copy(isLoading = false, isLoadingMore = false, error = r.message)
                }
            }
        }
    }
}
