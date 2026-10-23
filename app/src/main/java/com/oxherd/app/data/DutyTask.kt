package com.oxherd.app.data

/**
 * Duty inspection task representing a scheduled work shift.
 *
 * @property id Unique database ID (auto-increment)
 * @property title Task display title (e.g. "周四图书馆晚班巡检")
 * @property scheduledDate Task execution date in "YYYY-MM-DD" format
 * @property scheduledTime Task start time in "HH:mm" format (e.g. "20:00")
 * @property recurrenceRule Recurrence pattern, e.g. "WEEKLY:4" (Thursday every week)
 * @property remindAdvanceMinutes Advance minutes before scheduledTime to trigger reminder alarm
 * @property handoffUrl Shift report handoff webhook or web URL
 * @property handoffTargetApp Target app package name for quick report sharing (e.g. "com.tencent.tim")
 * @property status Current status: "NOT_STARTED", "IN_PROGRESS", "COMPLETED", "ARCHIVED"
 * @property archiveZipPath Path to the generated ZIP archive once completed and archived
 * @property completedAt Timestamp in ms when the task was completed
 * @property createdAt Timestamp in ms when the task was created
 */
data class DutyTask(
    val id: Long = 0L,
    val title: String,
    val scheduledDate: String,
    val scheduledTime: String = "20:00",
    val recurrenceRule: String = "WEEKLY:4",
    val remindAdvanceMinutes: Int = 15,
    val handoffUrl: String = "",
    val handoffTargetApp: String = "com.tencent.tim",
    val status: String = TaskStatus.NOT_STARTED,
    val archiveZipPath: String? = null,
    val completedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

object TaskStatus {
    const val NOT_STARTED = "NOT_STARTED"
    const val IN_PROGRESS = "IN_PROGRESS"
    const val COMPLETED = "COMPLETED"
    const val ARCHIVED = "ARCHIVED"
}
