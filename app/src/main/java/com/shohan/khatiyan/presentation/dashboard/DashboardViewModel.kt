package com.shohan.khatiyan.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shohan.khatiyan.di.AppContainer
import com.shohan.khatiyan.domain.model.DashboardSnapshot
import com.shohan.khatiyan.utilities.DataBus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** Reactive dashboard state: reloads on launch, resume-triggered poke and any data change. */
class DashboardViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val snapshot: DashboardSnapshot? = null,
        val loading: Boolean = true,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
        DataBus.changes
            .onEach { refresh() }
            .launchIn(viewModelScope)
    }

    fun refresh() {
        viewModelScope.launch {
            val snap = runCatching { container.dashboardRepo.snapshot() }.getOrNull()
            _state.value = _state.value.copy(snapshot = snap, loading = false)
        }
    }
}
