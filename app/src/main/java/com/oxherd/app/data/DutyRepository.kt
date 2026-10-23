package com.oxherd.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Main repository for Duty inspection workflows, reactive queries, and state management.
 */
interface DutyRepository {
    /** Reactive observation of the currently active (non-archived) duty task. */
    fun getActiveTaskFlow(): Flow<DutyTask?>

    /** Reactive observation of all stations under a duty task. */
    fun getStationsForTask(taskId: Long): Flow<List<TaskStation>>

    /** Reactive observation of all check items under a station. */
    fun getItemsForStation(stationId: Long): Flow<List<CheckItem>>

    /** Reactive observation of all sub-items under a check item. */
    fun getSubItemsForItem(itemId: Long): Flow<List<SubItem>>

    /** Reactive observation of current user settings and active navigation point. */
    fun getUserSettingsFlow(): Flow<UserSettings>

    /** Updates the currently active station in user settings. */
    suspend fun updateActiveStation(stationId: Long, stationName: String)

    /** Toggles completion state of a check item and cascades to sub-items and stations. */
    suspend fun toggleCheckItem(itemId: Long, isCompleted: Boolean)

    /** Toggles completion state of an individual sub-item and updates parent check item. */
    suspend fun toggleSubItem(subItemId: Long, isCompleted: Boolean)

    /** Records photographic evidence, notes, or flags an exception for an item. */
    suspend fun recordEvidence(
        itemId: Long,
        photoUri: String?,
        note: String?,
        isException: Boolean
    )

    /**
     * Completes and packages the duty shift into a ZIP archive, marks the current task as ARCHIVED,
     * generates a fresh task for the next cycle with clean checklist items, and schedules the next alarm.
     *
     * @param taskId ID of task to archive
     * @return Absolute path of the created ZIP archive
     */
    suspend fun archiveAndResetForNextCycle(taskId: Long): String

    /** Fetches the full inspection hierarchy (Task -> Stations -> Items -> SubItems). */
    suspend fun getTaskWithStations(taskId: Long): TaskWithStations?

    /** Synchronously queries active task snapshot. */
    suspend fun getActiveTask(): DutyTask?

    /** Synchronously queries current user settings snapshot. */
    suspend fun getUserSettings(): UserSettings

    /** Updates user settings. */
    suspend fun updateUserSettings(settings: UserSettings)

    /** Updates application theme mode ("SYSTEM", "LIGHT", "DARK"). */
    suspend fun updateThemeMode(themeMode: String)

    /** Schedules pre-shift alarm reminder for a duty task. */
    fun scheduleTaskAlarm(task: DutyTask): Boolean

    /** Cancels pre-shift reminder alarm. */
    fun cancelTaskAlarm(taskId: Long)

    // --- Dynamic Customization: Check Items CRUD ---
    suspend fun createCheckItem(
        stationId: Long,
        title: String,
        isTimeLinear: Boolean,
        timeStart: String?,
        timeEnd: String?,
        sortOrder: Int = 0
    ): Long

    suspend fun updateCheckItem(
        itemId: Long,
        title: String,
        isTimeLinear: Boolean,
        timeStart: String?,
        timeEnd: String?
    )

    suspend fun deleteCheckItem(itemId: Long)

    // --- Dynamic Customization: Sub-Items CRUD ---
    suspend fun createSubItem(
        parentId: Long,
        title: String,
        sortOrder: Int = 0
    ): Long

    suspend fun updateSubItem(subItemId: Long, title: String)

    suspend fun deleteSubItem(subItemId: Long)

    // --- Dynamic Customization: Stations CRUD ---
    suspend fun createStation(
        taskId: Long,
        name: String,
        sortOrder: Int = 0
    ): Long

    suspend fun updateStation(stationId: Long, name: String)

    suspend fun deleteStation(stationId: Long)

    // --- Check Items Ordering & Timing Mutations ---
    suspend fun getAllCheckItemsByStation(stationId: Long): List<CheckItem>

    suspend fun swapCheckItemSortOrder(firstId: Long, secondId: Long)

    suspend fun updateCheckItemSortOrder(itemId: Long, newOrder: Int)

    suspend fun updateCheckItemTiming(
        itemId: Long,
        isTimeLinear: Boolean,
        timeStart: String?,
        timeEnd: String?
    )

    // --- Configuration Import & Export ---
    suspend fun exportDutyConfigJson(): String

    suspend fun importDutyConfig(
        dutyTask: DutyTask,
        stations: List<TaskStation>,
        items: List<CheckItem>,
        subItems: List<SubItem>
    )

    suspend fun importDutyConfig(config: DutyConfigExport)

    companion object {
        fun getInstance(context: Context): DutyRepository = DefaultDutyRepository.getInstance(context)
    }
}

class DefaultDutyRepository(
    private val database: OxherdDatabase,
    private val alarmScheduler: DutyAlarmScheduler,
    private val archivePackager: DutyArchivePackager
) : DutyRepository {

    constructor(context: Context) : this(
        OxherdDatabase.getInstance(context),
        DutyAlarmScheduler(context.applicationContext),
        DutyArchivePackager(context.applicationContext)
    )

    override fun getActiveTaskFlow(): Flow<DutyTask?> =
        database.observeActiveTask()

    override fun getStationsForTask(taskId: Long): Flow<List<TaskStation>> =
        database.observeStationsForTask(taskId)

    override fun getItemsForStation(stationId: Long): Flow<List<CheckItem>> =
        database.observeItemsForStation(stationId)

    override fun getSubItemsForItem(itemId: Long): Flow<List<SubItem>> =
        database.observeSubItemsForItem(itemId)

    override fun getUserSettingsFlow(): Flow<UserSettings> =
        database.observeUserSettings()

    override suspend fun updateActiveStation(stationId: Long, stationName: String) =
        database.updateActiveStation(stationId, stationName)

    override suspend fun toggleCheckItem(itemId: Long, isCompleted: Boolean) =
        database.updateCheckItemCompleted(itemId, isCompleted)

    override suspend fun toggleSubItem(subItemId: Long, isCompleted: Boolean) =
        database.updateSubItemCompleted(subItemId, isCompleted)

    override suspend fun recordEvidence(
        itemId: Long,
        photoUri: String?,
        note: String?,
        isException: Boolean
    ) = database.updateCheckItemEvidence(itemId, photoUri, note, isException)

    override suspend fun getTaskWithStations(taskId: Long): TaskWithStations? =
        database.getTaskWithStations(taskId)

    override suspend fun getActiveTask(): DutyTask? =
        database.getActiveTask()

    override suspend fun getUserSettings(): UserSettings =
        database.getUserSettings()

    override suspend fun updateUserSettings(settings: UserSettings) =
        database.updateUserSettings(settings)

    override suspend fun updateThemeMode(themeMode: String) =
        database.updateThemeMode(themeMode)

    override fun scheduleTaskAlarm(task: DutyTask): Boolean =
        alarmScheduler.schedulePreShiftAlarm(task)

    override fun cancelTaskAlarm(taskId: Long) =
        alarmScheduler.cancelPreShiftAlarm(taskId)

    override suspend fun createCheckItem(
        stationId: Long,
        title: String,
        isTimeLinear: Boolean,
        timeStart: String?,
        timeEnd: String?,
        sortOrder: Int
    ): Long = database.insertCheckItem(stationId, title, isTimeLinear, timeStart, timeEnd, sortOrder)

    override suspend fun updateCheckItem(
        itemId: Long,
        title: String,
        isTimeLinear: Boolean,
        timeStart: String?,
        timeEnd: String?
    ) = database.updateCheckItemDetails(itemId, title, isTimeLinear, timeStart, timeEnd)

    override suspend fun deleteCheckItem(itemId: Long) =
        database.deleteCheckItem(itemId)

    override suspend fun createSubItem(
        parentId: Long,
        title: String,
        sortOrder: Int
    ): Long = database.insertSubItem(parentId, title, sortOrder)

    override suspend fun updateSubItem(subItemId: Long, title: String) =
        database.updateSubItemTitle(subItemId, title)

    override suspend fun deleteSubItem(subItemId: Long) =
        database.deleteSubItem(subItemId)

    override suspend fun createStation(
        taskId: Long,
        name: String,
        sortOrder: Int
    ): Long = database.insertStation(taskId, name, sortOrder)

    override suspend fun updateStation(stationId: Long, name: String) =
        database.updateStationName(stationId, name)

    override suspend fun deleteStation(stationId: Long) =
        database.deleteStation(stationId)

    override suspend fun getAllCheckItemsByStation(stationId: Long): List<CheckItem> =
        database.getAllCheckItemsByStation(stationId)

    override suspend fun swapCheckItemSortOrder(firstId: Long, secondId: Long) =
        database.swapCheckItemSortOrder(firstId, secondId)

    override suspend fun updateCheckItemSortOrder(itemId: Long, newOrder: Int) =
        database.updateCheckItemSortOrder(itemId, newOrder)

    override suspend fun updateCheckItemTiming(
        itemId: Long,
        isTimeLinear: Boolean,
        timeStart: String?,
        timeEnd: String?
    ) = database.updateCheckItemTiming(itemId, isTimeLinear, timeStart, timeEnd)

    override suspend fun exportDutyConfigJson(): String = withContext(Dispatchers.IO) {
        val activeTask = database.getActiveTask()
            ?: throw IllegalStateException("No active duty task found for export")
        val taskWithHierarchy = database.getTaskWithStations(activeTask.id)
            ?: throw IllegalStateException("Hierarchy not found for task ${activeTask.id}")
        val stations = taskWithHierarchy.stations.map { it.station }
        val items = taskWithHierarchy.stations.flatMap { it.items.map { ci -> ci.item } }
        val subItems = taskWithHierarchy.stations.flatMap { it.items.flatMap { ci -> ci.subItems } }
        DutyConfigManager.exportConfigToJson(taskWithHierarchy.task, stations, items, subItems)
    }

    override suspend fun importDutyConfig(
        dutyTask: DutyTask,
        stations: List<TaskStation>,
        items: List<CheckItem>,
        subItems: List<SubItem>
    ) {
        database.importDutyConfig(dutyTask, stations, items, subItems)
    }

    override suspend fun importDutyConfig(config: DutyConfigExport) {
        database.importDutyConfig(config.task, config.stations, config.items, config.subItems)
    }

    override suspend fun archiveAndResetForNextCycle(taskId: Long): String = withContext(Dispatchers.IO) {
        val taskWithHierarchy = database.getTaskWithStations(taskId)
            ?: throw IllegalStateException("Duty task $taskId could not be found for archiving")

        val task = taskWithHierarchy.task
        val stations = taskWithHierarchy.stations

        // 1. Package duty shift into ZIP archive
        val zipPath = archivePackager.packageDutyShift(task, stations)

        // 2. Mark current task as ARCHIVED with archive zip path
        database.updateTaskStatus(
            taskId = taskId,
            status = TaskStatus.ARCHIVED,
            completedAt = System.currentTimeMillis(),
            archiveZipPath = zipPath
        )

        // 3. Cancel alarm for old task
        alarmScheduler.cancelPreShiftAlarm(taskId)

        // 4. Calculate next occurrence date
        val currentDate = try {
            LocalDate.parse(task.scheduledDate)
        } catch (e: Exception) {
            LocalDate.now()
        }
        val nextDate = calculateNextScheduledDate(currentDate, task.recurrenceRule)
        val nextDateStr = nextDate.format(DateTimeFormatter.ISO_LOCAL_DATE)

        // 5. Clone stations and items for next cycle
        val newTaskId = database.cloneTaskForNextCycle(
            oldTaskId = taskId,
            nextDate = nextDateStr,
            nextTime = task.scheduledTime
        )

        // 6. Schedule pre-shift reminder for the new cycle task
        val newTask = database.getTaskById(newTaskId)
        if (newTask != null) {
            alarmScheduler.schedulePreShiftAlarm(newTask)
        }

        zipPath
    }

    private fun calculateNextScheduledDate(currentDate: LocalDate, recurrenceRule: String): LocalDate {
        return when {
            recurrenceRule.startsWith("WEEKLY") -> currentDate.plusWeeks(1)
            recurrenceRule.startsWith("DAILY") -> currentDate.plusDays(1)
            recurrenceRule.startsWith("BIWEEKLY") -> currentDate.plusWeeks(2)
            recurrenceRule.startsWith("MONTHLY") -> currentDate.plusMonths(1)
            else -> currentDate.plusWeeks(1)
        }
    }

    companion object {
        @Volatile
        private var instance: DutyRepository? = null

        fun getInstance(context: Context): DutyRepository {
            return instance ?: synchronized(this) {
                instance ?: DefaultDutyRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
