package com.yuvraj.resumescreener.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuvraj.resumescreener.data.local.DashboardStats
import com.yuvraj.resumescreener.data.local.ScreeningEntity
import com.yuvraj.resumescreener.data.repository.ScreeningRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class DashboardUiState(
    val stats: DashboardStats = DashboardStats(0, null, null, 0, IntArray(10)),
    val top: List<ScreeningEntity> = emptyList(),
    val recent: List<ScreeningEntity> = emptyList(),
    val loading: Boolean = true,
) {
    val isEmpty: Boolean get() = !loading && stats.total == 0
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    repository: ScreeningRepository,
) : ViewModel() {

    val state: StateFlow<DashboardUiState> = combine(
        repository.observeStats(),
        repository.observeTop(3),
        repository.observeAll(),
    ) { stats, top, all ->
        DashboardUiState(
            stats = stats,
            top = top,
            recent = all.take(5),
            loading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState(),
    )
}
