package com.oxherd.app.data

/**
 * Granular sub-check item belonging to a CheckItem.
 *
 * @property id Unique sub-item ID
 * @property parentId ID of the parent CheckItem
 * @property title Detailed sub-task description
 * @property isCompleted Whether sub-item is completed
 * @property sortOrder Sorting order within parent item
 */
data class SubItem(
    val id: Long = 0L,
    val parentId: Long,
    val title: String,
    val isCompleted: Boolean = false,
    val sortOrder: Int = 0
)
