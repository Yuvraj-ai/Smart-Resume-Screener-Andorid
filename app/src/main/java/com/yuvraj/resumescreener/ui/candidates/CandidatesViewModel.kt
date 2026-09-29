package com.yuvraj.resumescreener.ui.candidates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuvraj.resumescreener.data.local.ScreeningEntity
import com.yuvraj.resumescreener.data.repository.CandidateSort
import com.yuvraj.resumescreener.data.repository.ScreeningRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class CandidatesUiState(
    val query: String = "",
    val sort: CandidateSort = CandidateSort.RECENT,
    val rows: List<ScreeningEntity> = emptyList(),
    val loading: Boolean = true,
) {
    val isEmpty: Boolean get() = !loading && rows.isEmpty()
    val isFiltered: Boolean get() = query.isNotBlank()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CandidatesViewModel @Inject constructor(
    repository: ScreeningRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val sort = MutableStateFlow(CandidateSort.RECENT)

    // flatMapLatest on the combined filters, so a new query re-queries rather
    // than reusing a list captured when the ViewModel was constructed.
    private val rows = combine(query, sort, ::Pair)
        .flatMapLatest { (q, s) -> repository.search(q, s) }

    val state: StateFlow<CandidatesUiState> =
        combine(query, sort, rows) { q, s, list ->
            CandidatesUiState(query = q, sort = s, rows = list, loading = false)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CandidatesUiState(),
        )

    val sortState: StateFlow<CandidateSort> = sort.asStateFlow()

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onSortChange(value: CandidateSort) {
        sort.value = value
    }
}
