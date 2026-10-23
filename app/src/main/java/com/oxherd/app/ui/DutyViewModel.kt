package com.oxherd.app.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.oxherd.app.data.CheckItemWithSubItems
import com.oxherd.app.data.DutyRepository
import com.oxherd.app.data.DutyTask
import com.oxherd.app.data.TaskStation
import com.oxherd.app.data.UserSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * UI State representing current duty inspection shift, station checklist, and progress.
 */
data class DutyUiState(
    val activeTask: DutyTask? = null,
    val currentStation: TaskStation? = null,
    val stations: List<TaskStation> = emptyList(),
    val linearItems: List<CheckItemWithSubItems> = emptyList(),
    val nonLinearItems: List<CheckItemWithSubItems> = emptyList(),
    val progressPercentage: Float = 0.0f,
    val isAllCompleted: Boolean = false,
    val showCompletionDialog: Boolean = false,
    val userSettings: UserSettings = UserSettings()
)

/**
 * Duty inspection ViewModel connecting DutyRepository with Compose UI screens.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DutyViewModel(
    application: Application,
    private val repository: DutyRepository = DutyRepository.getInstance(application)
) : AndroidViewModel(application) {

    private val _showCompletionDialog = MutableStateFlow(false)
    private var previousAllCompleted = false

    val uiState: StateFlow<DutyUiState> = combine(
        repository.getActiveTaskFlow(),
        repository.getUserSettingsFlow(),
        _showCompletionDialog
    ) { activeTask, settings, showDialog ->
        Triple(activeTask, settings, showDialog)
    }.flatMapLatest { (activeTask, settings, showDialog) ->
        if (activeTask == null) {
            flowOf(
                DutyUiState(
                    activeTask = null,
                    userSettings = settings,
                    showCompletionDialog = showDialog
                )
            )
        } else {
            repository.getStationsForTask(activeTask.id).flatMapLatest { stations ->
                val targetStation = stations.find { it.id == settings.activeStationId }
                    ?: stations.firstOrNull()

                if (targetStation == null) {
                    flowOf(
                        DutyUiState(
                            activeTask = activeTask,
                            currentStation = null,
                            stations = stations,
                            linearItems = emptyList(),
                            nonLinearItems = emptyList(),
                            progressPercentage = 0f,
                            isAllCompleted = false,
                            showCompletionDialog = showDialog,
                            userSettings = settings
                        )
                    )
                } else {
                    repository.getItemsForStation(targetStation.id).flatMapLatest { items ->
                        if (items.isEmpty()) {
                            flowOf(
                                DutyUiState(
                                    activeTask = activeTask,
                                    currentStation = targetStation,
                                    stations = stations,
                                    linearItems = emptyList(),
                                    nonLinearItems = emptyList(),
                                    progressPercentage = 0f,
                                    isAllCompleted = false,
                                    showCompletionDialog = showDialog,
                                    userSettings = settings
                                )
                            )
                        } else {
                            val subItemFlows = items.map { item ->
                                repository.getSubItemsForItem(item.id).map { subs ->
                                    CheckItemWithSubItems(item, subs)
                                }
                            }
                            combine(subItemFlows) { itemsWithSubsArray ->
                                val allItemsWithSubs = itemsWithSubsArray.toList()
                                val linear = allItemsWithSubs
                                    .filter { it.item.isTimeLinear }
                                    .sortedBy { it.item.timeStart ?: "" }
                                val nonLinear = allItemsWithSubs
                                    .filter { !it.item.isTimeLinear }
                                    .sortedBy { it.item.sortOrder }

                                val total = allItemsWithSubs.size
                                val completedCount = allItemsWithSubs.count { it.item.isCompleted }
                                val progress = if (total > 0) completedCount.toFloat() / total.toFloat() else 0f
                                val isCompleted = total > 0 && completedCount == total

                                if (isCompleted && !previousAllCompleted) {
                                    _showCompletionDialog.value = true
                                }
                                previousAllCompleted = isCompleted

                                DutyUiState(
                                    activeTask = activeTask,
                                    currentStation = targetStation,
                                    stations = stations,
                                    linearItems = linear,
                                    nonLinearItems = nonLinear,
                                    progressPercentage = progress,
                                    isAllCompleted = isCompleted,
                                    showCompletionDialog = showDialog,
                                    userSettings = settings
                                )
                            }
                        }
                    }
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DutyUiState()
    )

    fun switchStation(stationId: String) {
        val id = stationId.toLongOrNull() ?: return
        switchStation(id)
    }

    fun switchStation(stationId: Long) {
        viewModelScope.launch {
            val stationName = uiState.value.stations.find { it.id == stationId }?.name ?: ""
            repository.updateActiveStation(stationId, stationName)
        }
    }

    fun toggleCheckItem(itemId: String, isCompleted: Boolean) {
        val id = itemId.toLongOrNull() ?: return
        toggleCheckItem(id, isCompleted)
    }

    fun toggleCheckItem(itemId: Long, isCompleted: Boolean) {
        viewModelScope.launch {
            repository.toggleCheckItem(itemId, isCompleted)
        }
    }

    fun toggleSubItem(subItemId: String, isCompleted: Boolean) {
        val id = subItemId.toLongOrNull() ?: return
        toggleSubItem(id, isCompleted)
    }

    fun toggleSubItem(subItemId: Long, isCompleted: Boolean) {
        viewModelScope.launch {
            repository.toggleSubItem(subItemId, isCompleted)
        }
    }

    fun recordEvidence(itemId: String, photoUri: String?, note: String?, isException: Boolean) {
        val id = itemId.toLongOrNull() ?: return
        recordEvidence(id, photoUri, note, isException)
    }

    fun recordEvidence(itemId: Long, photoUri: String?, note: String?, isException: Boolean) {
        viewModelScope.launch {
            repository.recordEvidence(itemId, photoUri, note, isException)
        }
    }

    fun confirmAndArchive(context: Context, onArchived: (zipPath: String) -> Unit) {
        val taskId = uiState.value.activeTask?.id ?: return
        viewModelScope.launch {
            try {
                val zipPath = repository.archiveAndResetForNextCycle(taskId)
                _showCompletionDialog.value = false
                previousAllCompleted = false
                onArchived(zipPath)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateUserSettings(settings: UserSettings) {
        viewModelScope.launch {
            repository.updateUserSettings(settings)
        }
    }

    fun openCompletionDialog() {
        _showCompletionDialog.value = true
    }

    fun dismissCompletionDialog() {
        _showCompletionDialog.value = false
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DutyViewModel(application)
            }
        }
    }
}
