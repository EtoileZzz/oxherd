package com.oxherd.app.data

/**
 * Inspection station or area checkpoint under a specific DutyTask.
 *
 * @property id Unique station ID
 * @property taskId Associated duty task ID
 * @property name Station name (e.g. "3楼自习区", "4楼阅览室", "5楼特藏室")
 * @property sortOrder Display sorting order
 * @property isCompleted Whether all items under this station have been completed
 */
data class TaskStation(
    val id: Long = 0L,
    val taskId: Long,
    val name: String,
    val sortOrder: Int = 0,
    val isCompleted: Boolean = false
)
