package com.oxherd.app.data

/**
 * Inspection check item under a specific TaskStation.
 *
 * @property id Unique check item ID
 * @property stationId ID of the owning TaskStation
 * @property title Item description / inspection instruction
 * @property isTimeLinear True if this check has strict chronological window (e.g. 20:00~20:30), false if routine
 * @property timeStart Start time in "HH:mm" (e.g. "20:00") when isTimeLinear is true
 * @property timeEnd End time in "HH:mm" (e.g. "20:30") when isTimeLinear is true
 * @property isCompleted Whether this check item has been checked off
 * @property isException Whether an exception/abnormality was flagged during check
 * @property photoUri URI of attached photographic evidence if captured
 * @property note Descriptive observation or exception notes
 * @property completedAt Timestamp in ms when checked
 * @property sortOrder Sorting order within station
 */
data class CheckItem(
    val id: Long = 0L,
    val stationId: Long,
    val title: String,
    val isTimeLinear: Boolean = false,
    val timeStart: String? = null,
    val timeEnd: String? = null,
    val isCompleted: Boolean = false,
    val isException: Boolean = false,
    val photoUri: String? = null,
    val note: String? = null,
    val completedAt: Long? = null,
    val sortOrder: Int = 0
)
