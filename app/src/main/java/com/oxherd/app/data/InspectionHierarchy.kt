package com.oxherd.app.data

/**
 * CheckItem bundled with its child sub-items.
 */
data class CheckItemWithSubItems(
    val item: CheckItem,
    val subItems: List<SubItem> = emptyList()
)

/**
 * TaskStation bundled with its check items and sub-items.
 */
data class StationWithItems(
    val station: TaskStation,
    val items: List<CheckItemWithSubItems> = emptyList()
)

/**
 * DutyTask bundled with all stations and their items.
 */
data class TaskWithStations(
    val task: DutyTask,
    val stations: List<StationWithItems> = emptyList()
)
