package com.oxherd.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * SQLite database helper for Oxherd duty patrol management.
 * Provides thread-safe, reactive Flow queries without code-gen or annotation processors.
 */
class OxherdDatabase private constructor(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {

    private val tableChangeFlow = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE duty_tasks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                scheduled_date TEXT NOT NULL,
                scheduled_time TEXT NOT NULL,
                recurrence_rule TEXT NOT NULL,
                remind_advance_minutes INTEGER NOT NULL DEFAULT 15,
                handoff_url TEXT NOT NULL DEFAULT '',
                handoff_target_app TEXT NOT NULL DEFAULT 'com.tencent.tim',
                status TEXT NOT NULL DEFAULT 'NOT_STARTED',
                archive_zip_path TEXT,
                completed_at INTEGER,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE task_stations (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                task_id INTEGER NOT NULL,
                name TEXT NOT NULL,
                sort_order INTEGER NOT NULL DEFAULT 0,
                is_completed INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY (task_id) REFERENCES duty_tasks(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE check_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                station_id INTEGER NOT NULL,
                title TEXT NOT NULL,
                is_time_linear INTEGER NOT NULL DEFAULT 0,
                time_start TEXT,
                time_end TEXT,
                is_completed INTEGER NOT NULL DEFAULT 0,
                is_exception INTEGER NOT NULL DEFAULT 0,
                photo_uri TEXT,
                note TEXT,
                completed_at INTEGER,
                sort_order INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY (station_id) REFERENCES task_stations(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE sub_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                parent_id INTEGER NOT NULL,
                title TEXT NOT NULL,
                is_completed INTEGER NOT NULL DEFAULT 0,
                sort_order INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY (parent_id) REFERENCES check_items(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE user_settings (
                id INTEGER PRIMARY KEY CHECK (id = 1),
                active_station_id INTEGER NOT NULL DEFAULT 1,
                active_station_name TEXT NOT NULL DEFAULT '默认岗位',
                perf_level TEXT NOT NULL DEFAULT 'FULL',
                motion_scale REAL NOT NULL DEFAULT 1.0,
                default_handoff_url TEXT NOT NULL DEFAULT '',
                default_handoff_app TEXT NOT NULL DEFAULT 'com.tencent.tim',
                theme_mode TEXT DEFAULT 'SYSTEM'
            )
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX idx_stations_task ON task_stations(task_id)")
        db.execSQL("CREATE INDEX idx_items_station ON check_items(station_id)")
        db.execSQL("CREATE INDEX idx_subitems_parent ON sub_items(parent_id)")

        // Seed initial data
        seedInitialData(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS sub_items")
        db.execSQL("DROP TABLE IF EXISTS check_items")
        db.execSQL("DROP TABLE IF EXISTS task_stations")
        db.execSQL("DROP TABLE IF EXISTS duty_tasks")
        db.execSQL("DROP TABLE IF EXISTS user_settings")
        onCreate(db)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        if (!db.isReadOnly) {
            // Guarantee seed data exists
            val cursor = db.rawQuery("SELECT COUNT(*) FROM duty_tasks", null)
            val count = if (cursor.moveToFirst()) cursor.getInt(0) else 0
            cursor.close()
            if (count == 0) {
                seedInitialData(db)
            }
        }
    }

    private fun seedInitialData(db: SQLiteDatabase) {
        val todayStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

        // 1. Clean default task
        val taskValues = ContentValues().apply {
            put("title", "我的值班任务")
            put("scheduled_date", todayStr)
            put("scheduled_time", "20:00")
            put("recurrence_rule", "WEEKLY:4")
            put("remind_advance_minutes", 15)
            put("handoff_url", "")
            put("handoff_target_app", "com.tencent.tim")
            put("status", TaskStatus.NOT_STARTED)
            put("created_at", System.currentTimeMillis())
        }
        val taskId = db.insert("duty_tasks", null, taskValues)

        // 2. Clean default station: exactly 1 station, name = "默认岗位", sort_order = 1, is_completed = 0
        val stationValues = ContentValues().apply {
            put("task_id", taskId)
            put("name", "默认岗位")
            put("sort_order", 1)
            put("is_completed", 0)
        }
        val stationId = db.insert("task_stations", null, stationValues)

        // Exactly 0 rows in check_items!
        // Exactly 0 rows in sub_items!

        // 3. Initial user_settings: active_station_name = "默认岗位", theme_mode = "SYSTEM"
        val settingsValues = ContentValues().apply {
            put("id", 1)
            put("active_station_id", stationId)
            put("active_station_name", "默认岗位")
            put("perf_level", PerfLevel.FULL)
            put("motion_scale", 1.0f)
            put("default_handoff_url", "")
            put("default_handoff_app", "com.tencent.tim")
            put("theme_mode", "SYSTEM")
        }
        db.insertWithOnConflict("user_settings", null, settingsValues, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // --- Invalidation & Reactive Flow Helper ---

    fun notifyTableChanged(table: String = "*") {
        tableChangeFlow.tryEmit(table)
    }

    private fun <T> observeQuery(tables: Set<String>, query: () -> T): Flow<T> = flow {
        emit(query())
        tableChangeFlow.collect { changed ->
            if (tables.isEmpty() || changed == "*" || tables.contains(changed)) {
                emit(query())
            }
        }
    }.flowOn(Dispatchers.IO).distinctUntilChanged()

    // --- Task Queries & Mutations ---

    suspend fun getActiveTask(): DutyTask? = withContext(Dispatchers.IO) {
        readableDatabase.rawQuery(
            "SELECT * FROM duty_tasks WHERE status != ? ORDER BY id DESC LIMIT 1",
            arrayOf(TaskStatus.ARCHIVED)
        ).use { cursor ->
            if (cursor.moveToFirst()) cursorToDutyTask(cursor) else null
        }
    }

    fun observeActiveTask(): Flow<DutyTask?> = observeQuery(setOf("duty_tasks")) {
        readableDatabase.rawQuery(
            "SELECT * FROM duty_tasks WHERE status != ? ORDER BY id DESC LIMIT 1",
            arrayOf(TaskStatus.ARCHIVED)
        ).use { cursor ->
            if (cursor.moveToFirst()) cursorToDutyTask(cursor) else null
        }
    }

    suspend fun getTaskById(taskId: Long): DutyTask? = withContext(Dispatchers.IO) {
        readableDatabase.rawQuery(
            "SELECT * FROM duty_tasks WHERE id = ?",
            arrayOf(taskId.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) cursorToDutyTask(cursor) else null
        }
    }

    suspend fun updateTaskStatus(
        taskId: Long,
        status: String,
        completedAt: Long? = null,
        archiveZipPath: String? = null
    ) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("status", status)
            completedAt?.let { put("completed_at", it) }
            archiveZipPath?.let { put("archive_zip_path", it) }
        }
        writableDatabase.update("duty_tasks", values, "id = ?", arrayOf(taskId.toString()))
        notifyTableChanged("duty_tasks")
    }

    suspend fun insertDutyTask(task: DutyTask): Long = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("title", task.title)
            put("scheduled_date", task.scheduledDate)
            put("scheduled_time", task.scheduledTime)
            put("recurrence_rule", task.recurrenceRule)
            put("remind_advance_minutes", task.remindAdvanceMinutes)
            put("handoff_url", task.handoffUrl)
            put("handoff_target_app", task.handoffTargetApp)
            put("status", task.status)
            put("archive_zip_path", task.archiveZipPath)
            put("completed_at", task.completedAt)
            put("created_at", task.createdAt)
        }
        val id = writableDatabase.insert("duty_tasks", null, values)
        notifyTableChanged("duty_tasks")
        id
    }

    // --- Station Queries & Mutations ---

    suspend fun getStationsForTask(taskId: Long): List<TaskStation> = withContext(Dispatchers.IO) {
        readableDatabase.rawQuery(
            "SELECT * FROM task_stations WHERE task_id = ? ORDER BY sort_order ASC, id ASC",
            arrayOf(taskId.toString())
        ).use { cursor ->
            val list = mutableListOf<TaskStation>()
            while (cursor.moveToNext()) {
                list.add(cursorToTaskStation(cursor))
            }
            list
        }
    }

    fun observeStationsForTask(taskId: Long): Flow<List<TaskStation>> =
        observeQuery(setOf("task_stations", "check_items", "sub_items")) {
            readableDatabase.rawQuery(
                "SELECT * FROM task_stations WHERE task_id = ? ORDER BY sort_order ASC, id ASC",
                arrayOf(taskId.toString())
            ).use { cursor ->
                val list = mutableListOf<TaskStation>()
                while (cursor.moveToNext()) {
                    list.add(cursorToTaskStation(cursor))
                }
                list
            }
        }

    suspend fun getStationById(stationId: Long): TaskStation? = withContext(Dispatchers.IO) {
        readableDatabase.rawQuery(
            "SELECT * FROM task_stations WHERE id = ?",
            arrayOf(stationId.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) cursorToTaskStation(cursor) else null
        }
    }

    suspend fun updateStationCompleted(stationId: Long, isCompleted: Boolean) =
        withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put("is_completed", if (isCompleted) 1 else 0)
            }
            writableDatabase.update("task_stations", values, "id = ?", arrayOf(stationId.toString()))
            notifyTableChanged("task_stations")
        }

    // --- Check Items Queries & Mutations ---

    suspend fun getItemsForStation(stationId: Long): List<CheckItem> = withContext(Dispatchers.IO) {
        readableDatabase.rawQuery(
            "SELECT * FROM check_items WHERE station_id = ? ORDER BY sort_order ASC, id ASC",
            arrayOf(stationId.toString())
        ).use { cursor ->
            val list = mutableListOf<CheckItem>()
            while (cursor.moveToNext()) {
                list.add(cursorToCheckItem(cursor))
            }
            list
        }
    }

    fun observeItemsForStation(stationId: Long): Flow<List<CheckItem>> =
        observeQuery(setOf("check_items", "sub_items")) {
            readableDatabase.rawQuery(
                "SELECT * FROM check_items WHERE station_id = ? ORDER BY sort_order ASC, id ASC",
                arrayOf(stationId.toString())
            ).use { cursor ->
                val list = mutableListOf<CheckItem>()
                while (cursor.moveToNext()) {
                    list.add(cursorToCheckItem(cursor))
                }
                list
            }
        }

    suspend fun getItemById(itemId: Long): CheckItem? = withContext(Dispatchers.IO) {
        readableDatabase.rawQuery(
            "SELECT * FROM check_items WHERE id = ?",
            arrayOf(itemId.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) cursorToCheckItem(cursor) else null
        }
    }

    /**
     * Toggles check item completion, updates all sub-items under it to match,
     * evaluates station completion status, and updates task status to IN_PROGRESS or COMPLETED.
     */
    suspend fun updateCheckItemCompleted(
        itemId: Long,
        isCompleted: Boolean,
        completedAt: Long? = if (isCompleted) System.currentTimeMillis() else null
    ) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            // 1. Update check_items
            val values = ContentValues().apply {
                put("is_completed", if (isCompleted) 1 else 0)
                if (isCompleted) {
                    put("completed_at", completedAt ?: System.currentTimeMillis())
                } else {
                    putNull("completed_at")
                }
            }
            db.update("check_items", values, "id = ?", arrayOf(itemId.toString()))

            // 2. Cascade completion to sub_items
            val subValues = ContentValues().apply {
                put("is_completed", if (isCompleted) 1 else 0)
            }
            db.update("sub_items", subValues, "parent_id = ?", arrayOf(itemId.toString()))

            // 3. Find stationId and recalculate station status
            val stationId = queryStationIdForItem(db, itemId)
            if (stationId != null) {
                checkAndUpdateStationCompletion(db, stationId)
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        notifyTableChanged("check_items")
        notifyTableChanged("sub_items")
        notifyTableChanged("task_stations")
        notifyTableChanged("duty_tasks")
    }

    suspend fun updateCheckItemEvidence(
        itemId: Long,
        photoUri: String?,
        note: String?,
        isException: Boolean
    ) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("photo_uri", photoUri)
            put("note", note)
            put("is_exception", if (isException) 1 else 0)
        }
        writableDatabase.update("check_items", values, "id = ?", arrayOf(itemId.toString()))
        notifyTableChanged("check_items")
    }

    // --- Sub Items Queries & Mutations ---

    suspend fun getSubItemsForItem(parentId: Long): List<SubItem> = withContext(Dispatchers.IO) {
        readableDatabase.rawQuery(
            "SELECT * FROM sub_items WHERE parent_id = ? ORDER BY sort_order ASC, id ASC",
            arrayOf(parentId.toString())
        ).use { cursor ->
            val list = mutableListOf<SubItem>()
            while (cursor.moveToNext()) {
                list.add(cursorToSubItem(cursor))
            }
            list
        }
    }

    fun observeSubItemsForItem(parentId: Long): Flow<List<SubItem>> =
        observeQuery(setOf("sub_items")) {
            readableDatabase.rawQuery(
                "SELECT * FROM sub_items WHERE parent_id = ? ORDER BY sort_order ASC, id ASC",
                arrayOf(parentId.toString())
            ).use { cursor ->
                val list = mutableListOf<SubItem>()
                while (cursor.moveToNext()) {
                    list.add(cursorToSubItem(cursor))
                }
                list
            }
        }

    /**
     * Toggles a single sub-item. If all siblings are completed, auto-completes parent CheckItem;
     * if uncompleted, ensures parent CheckItem is uncompleted.
     */
    suspend fun updateSubItemCompleted(subItemId: Long, isCompleted: Boolean) =
        withContext(Dispatchers.IO) {
            val db = writableDatabase
            db.beginTransaction()
            try {
                // 1. Update this sub_item
                val values = ContentValues().apply {
                    put("is_completed", if (isCompleted) 1 else 0)
                }
                db.update("sub_items", values, "id = ?", arrayOf(subItemId.toString()))

                // 2. Query parent_id
                var parentId: Long? = null
                db.rawQuery("SELECT parent_id FROM sub_items WHERE id = ?", arrayOf(subItemId.toString()))
                    .use { c ->
                        if (c.moveToFirst()) parentId = c.getLong(0)
                    }

                if (parentId != null) {
                    // Check if all sub_items under parent are completed
                    var allSubCompleted = true
                    db.rawQuery(
                        "SELECT is_completed FROM sub_items WHERE parent_id = ?",
                        arrayOf(parentId.toString())
                    ).use { c ->
                        while (c.moveToNext()) {
                            if (c.getInt(0) == 0) {
                                allSubCompleted = false
                                break
                            }
                        }
                    }

                    val itemValues = ContentValues().apply {
                        put("is_completed", if (allSubCompleted) 1 else 0)
                        if (allSubCompleted) {
                            put("completed_at", System.currentTimeMillis())
                        } else {
                            putNull("completed_at")
                        }
                    }
                    db.update("check_items", itemValues, "id = ?", arrayOf(parentId.toString()))

                    // Update station completion
                    val stationId = queryStationIdForItem(db, parentId)
                    if (stationId != null) {
                        checkAndUpdateStationCompletion(db, stationId)
                    }
                }

                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            notifyTableChanged("sub_items")
            notifyTableChanged("check_items")
            notifyTableChanged("task_stations")
            notifyTableChanged("duty_tasks")
        }

    // --- Custom Station, Item, and Sub-Item Mutations ---

    suspend fun insertCheckItem(
        stationId: Long,
        title: String,
        isTimeLinear: Boolean,
        timeStart: String?,
        timeEnd: String?,
        sortOrder: Int = 0
    ): Long = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("station_id", stationId)
            put("title", title.trim())
            put("is_time_linear", if (isTimeLinear) 1 else 0)
            put("time_start", timeStart?.trim())
            put("time_end", timeEnd?.trim())
            put("is_completed", 0)
            put("is_exception", 0)
            put("sort_order", sortOrder)
        }
        val id = writableDatabase.insert("check_items", null, values)
        checkAndUpdateStationCompletion(writableDatabase, stationId)
        notifyTableChanged("check_items")
        notifyTableChanged("task_stations")
        id
    }

    suspend fun updateCheckItemDetails(
        itemId: Long,
        title: String,
        isTimeLinear: Boolean,
        timeStart: String?,
        timeEnd: String?
    ) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("title", title.trim())
            put("is_time_linear", if (isTimeLinear) 1 else 0)
            put("time_start", timeStart?.trim())
            put("time_end", timeEnd?.trim())
        }
        writableDatabase.update("check_items", values, "id = ?", arrayOf(itemId.toString()))
        notifyTableChanged("check_items")
    }

    suspend fun deleteCheckItem(itemId: Long) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val stationId = queryStationIdForItem(db, itemId)
            db.delete("sub_items", "parent_id = ?", arrayOf(itemId.toString()))
            db.delete("check_items", "id = ?", arrayOf(itemId.toString()))
            if (stationId != null) {
                checkAndUpdateStationCompletion(db, stationId)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        notifyTableChanged("sub_items")
        notifyTableChanged("check_items")
        notifyTableChanged("task_stations")
        notifyTableChanged("duty_tasks")
    }

    suspend fun insertSubItem(
        parentId: Long,
        title: String,
        sortOrder: Int = 0
    ): Long = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("parent_id", parentId)
            put("title", title.trim())
            put("is_completed", 0)
            put("sort_order", sortOrder)
        }
        val id = writableDatabase.insert("sub_items", null, values)
        val db = writableDatabase
        db.beginTransaction()
        try {
            val itemValues = ContentValues().apply { put("is_completed", 0) }
            db.update("check_items", itemValues, "id = ?", arrayOf(parentId.toString()))
            val stationId = queryStationIdForItem(db, parentId)
            if (stationId != null) {
                checkAndUpdateStationCompletion(db, stationId)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        notifyTableChanged("sub_items")
        notifyTableChanged("check_items")
        notifyTableChanged("task_stations")
        id
    }

    suspend fun updateSubItemTitle(subItemId: Long, title: String) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply { put("title", title.trim()) }
        writableDatabase.update("sub_items", values, "id = ?", arrayOf(subItemId.toString()))
        notifyTableChanged("sub_items")
    }

    suspend fun deleteSubItem(subItemId: Long) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            var parentId: Long? = null
            db.rawQuery("SELECT parent_id FROM sub_items WHERE id = ?", arrayOf(subItemId.toString()))
                .use { c -> if (c.moveToFirst()) parentId = c.getLong(0) }
            db.delete("sub_items", "id = ?", arrayOf(subItemId.toString()))
            if (parentId != null) {
                db.rawQuery("SELECT COUNT(*) FROM sub_items WHERE parent_id = ?", arrayOf(parentId.toString()))
                    .use { cTotal ->
                        val total = if (cTotal.moveToFirst()) cTotal.getInt(0) else 0
                        if (total > 0) {
                            db.rawQuery(
                                "SELECT COUNT(*) FROM sub_items WHERE parent_id = ? AND is_completed = 0",
                                arrayOf(parentId.toString())
                            ).use { c ->
                                val uncompleted = if (c.moveToFirst()) c.getInt(0) else 0
                                val allValues = ContentValues().apply {
                                    put("is_completed", if (uncompleted == 0) 1 else 0)
                                }
                                db.update("check_items", allValues, "id = ?", arrayOf(parentId.toString()))
                            }
                        }
                    }
                val stationId = queryStationIdForItem(db, parentId)
                if (stationId != null) {
                    checkAndUpdateStationCompletion(db, stationId)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        notifyTableChanged("sub_items")
        notifyTableChanged("check_items")
        notifyTableChanged("task_stations")
        notifyTableChanged("duty_tasks")
    }

    suspend fun insertStation(taskId: Long, name: String, sortOrder: Int = 0): Long = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        val values = ContentValues().apply {
            put("task_id", taskId)
            put("name", trimmed)
            put("sort_order", sortOrder)
            put("is_completed", 0)
        }
        val id = writableDatabase.insert("task_stations", null, values)
        checkAndUpdateTaskCompletion(writableDatabase, taskId)

        // If user_settings currently points to a non-existent station, update to this newly inserted station
        writableDatabase.rawQuery(
            "SELECT count(*) FROM task_stations WHERE id = (SELECT active_station_id FROM user_settings WHERE id = 1)",
            null
        ).use { c ->
            val valid = if (c.moveToFirst()) c.getInt(0) > 0 else false
            if (!valid) {
                val setVals = ContentValues().apply {
                    put("active_station_id", id)
                    put("active_station_name", trimmed)
                }
                writableDatabase.update("user_settings", setVals, "id = 1", null)
                notifyTableChanged("user_settings")
            }
        }

        notifyTableChanged("task_stations")
        notifyTableChanged("duty_tasks")
        id
    }

    suspend fun updateStationName(stationId: Long, name: String) = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        val values = ContentValues().apply { put("name", trimmed) }
        writableDatabase.update("task_stations", values, "id = ?", arrayOf(stationId.toString()))

        // Synchronize active_station_name in user_settings if active station was renamed
        val setValues = ContentValues().apply { put("active_station_name", trimmed) }
        writableDatabase.update("user_settings", setValues, "id = 1 AND active_station_id = ?", arrayOf(stationId.toString()))

        notifyTableChanged("task_stations")
        notifyTableChanged("user_settings")
    }

    suspend fun deleteStation(stationId: Long) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            var taskId: Long? = null
            db.rawQuery("SELECT task_id FROM task_stations WHERE id = ?", arrayOf(stationId.toString()))
                .use { c -> if (c.moveToFirst()) taskId = c.getLong(0) }

            db.execSQL(
                "DELETE FROM sub_items WHERE parent_id IN (SELECT id FROM check_items WHERE station_id = ?)",
                arrayOf(stationId.toString())
            )
            db.delete("check_items", "station_id = ?", arrayOf(stationId.toString()))
            db.delete("task_stations", "id = ?", arrayOf(stationId.toString()))

            if (taskId != null) {
                checkAndUpdateTaskCompletion(db, taskId)

                // If the deleted station was active in user_settings, fallback to first available station
                db.rawQuery("SELECT active_station_id FROM user_settings WHERE id = 1", null).use { c ->
                    if (c.moveToFirst() && c.getLong(0) == stationId) {
                        db.rawQuery(
                            "SELECT id, name FROM task_stations WHERE task_id = ? ORDER BY sort_order ASC, id ASC LIMIT 1",
                            arrayOf(taskId.toString())
                        ).use { nextStation ->
                            if (nextStation.moveToFirst()) {
                                val newId = nextStation.getLong(0)
                                val newName = nextStation.getString(1)
                                val setVals = ContentValues().apply {
                                    put("active_station_id", newId)
                                    put("active_station_name", newName)
                                }
                                db.update("user_settings", setVals, "id = 1", null)
                            }
                        }
                    }
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        notifyTableChanged("sub_items")
        notifyTableChanged("check_items")
        notifyTableChanged("task_stations")
        notifyTableChanged("duty_tasks")
        notifyTableChanged("user_settings")
    }

    private fun queryStationIdForItem(db: SQLiteDatabase, itemId: Long): Long? {
        db.rawQuery("SELECT station_id FROM check_items WHERE id = ?", arrayOf(itemId.toString()))
            .use { cursor ->
                return if (cursor.moveToFirst()) cursor.getLong(0) else null
            }
    }

    private fun checkAndUpdateStationCompletion(db: SQLiteDatabase, stationId: Long) {
        var allItemsCompleted = true
        var hasAnyCompleted = false
        var totalItems = 0

        db.rawQuery(
            "SELECT is_completed FROM check_items WHERE station_id = ?",
            arrayOf(stationId.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                totalItems++
                val completed = cursor.getInt(0) == 1
                if (completed) {
                    hasAnyCompleted = true
                } else {
                    allItemsCompleted = false
                }
            }
        }

        val isStationCompleted = totalItems > 0 && allItemsCompleted
        val sValues = ContentValues().apply {
            put("is_completed", if (isStationCompleted) 1 else 0)
        }
        db.update("task_stations", sValues, "id = ?", arrayOf(stationId.toString()))

        // Check overall task progress
        var taskId: Long? = null
        db.rawQuery("SELECT task_id FROM task_stations WHERE id = ?", arrayOf(stationId.toString()))
            .use { cursor ->
                if (cursor.moveToFirst()) taskId = cursor.getLong(0)
            }

        if (taskId != null) {
            checkAndUpdateTaskCompletion(db, taskId)
        }
    }

    private fun checkAndUpdateTaskCompletion(db: SQLiteDatabase, taskId: Long) {
        var allStationsCompleted = true
        var hasAnyCompletedStation = false
        var totalStations = 0

        db.rawQuery(
            "SELECT is_completed FROM task_stations WHERE task_id = ?",
            arrayOf(taskId.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                totalStations++
                val completed = cursor.getInt(0) == 1
                if (completed) {
                    hasAnyCompletedStation = true
                } else {
                    allStationsCompleted = false
                }
            }
        }

        val isTaskFullyCompleted = totalStations > 0 && allStationsCompleted
        val currentStatus: String
        db.rawQuery("SELECT status FROM duty_tasks WHERE id = ?", arrayOf(taskId.toString()))
            .use { cursor ->
                currentStatus = if (cursor.moveToFirst()) cursor.getString(0) else TaskStatus.NOT_STARTED
            }

        if (currentStatus != TaskStatus.ARCHIVED) {
            val newStatus = when {
                isTaskFullyCompleted -> TaskStatus.COMPLETED
                hasAnyCompletedStation -> TaskStatus.IN_PROGRESS
                else -> {
                    var hasAnyCompletedItem = false
                    db.rawQuery(
                        """
                        SELECT 1 FROM check_items ci
                        JOIN task_stations ts ON ci.station_id = ts.id
                        WHERE ts.task_id = ? AND ci.is_completed = 1
                        LIMIT 1
                        """.trimIndent(),
                        arrayOf(taskId.toString())
                    ).use { c ->
                        if (c.moveToFirst()) hasAnyCompletedItem = true
                    }
                    if (hasAnyCompletedItem) TaskStatus.IN_PROGRESS else TaskStatus.NOT_STARTED
                }
            }
            if (newStatus != currentStatus) {
                val tValues = ContentValues().apply {
                    put("status", newStatus)
                    if (newStatus == TaskStatus.COMPLETED) {
                        put("completed_at", System.currentTimeMillis())
                    } else if (currentStatus == TaskStatus.COMPLETED) {
                        putNull("completed_at")
                    }
                }
                db.update("duty_tasks", tValues, "id = ?", arrayOf(taskId.toString()))
            }
        }
    }

    // --- User Settings Queries & Mutations ---

    suspend fun getUserSettings(): UserSettings = withContext(Dispatchers.IO) {
        readableDatabase.rawQuery("SELECT * FROM user_settings WHERE id = 1", null).use { cursor ->
            if (cursor.moveToFirst()) cursorToUserSettings(cursor) else UserSettings()
        }
    }

    fun observeUserSettings(): Flow<UserSettings> = observeQuery(setOf("user_settings")) {
        readableDatabase.rawQuery("SELECT * FROM user_settings WHERE id = 1", null).use { cursor ->
            if (cursor.moveToFirst()) cursorToUserSettings(cursor) else UserSettings()
        }
    }

    suspend fun updateActiveStation(stationId: Long, stationName: String) =
        withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put("active_station_id", stationId)
                put("active_station_name", stationName)
            }
            writableDatabase.update("user_settings", values, "id = 1", null)
            notifyTableChanged("user_settings")
        }

    suspend fun updateUserSettings(settings: UserSettings) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("id", 1)
            put("active_station_id", settings.activeStationId)
            put("active_station_name", settings.activeStationName)
            put("perf_level", settings.perfLevel)
            put("motion_scale", settings.motionScale)
            put("default_handoff_url", settings.defaultHandoffUrl)
            put("default_handoff_app", settings.defaultHandoffApp)
            put("theme_mode", settings.themeMode)
        }
        writableDatabase.insertWithOnConflict("user_settings", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        notifyTableChanged("user_settings")
    }

    suspend fun updateThemeMode(themeMode: String) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("theme_mode", themeMode)
        }
        writableDatabase.update("user_settings", values, "id = 1", null)
        notifyTableChanged("user_settings")
    }

    // --- Full Inspection Hierarchy Query ---

    suspend fun getTaskWithStations(taskId: Long): TaskWithStations? = withContext(Dispatchers.IO) {
        val task = getTaskById(taskId) ?: return@withContext null
        val stations = getStationsForTask(taskId)
        val stationList = stations.map { station ->
            val items = getItemsForStation(station.id)
            val itemsWithSubs = items.map { item ->
                val subs = getSubItemsForItem(item.id)
                CheckItemWithSubItems(item, subs)
            }
            StationWithItems(station, itemsWithSubs)
        }
        TaskWithStations(task, stationList)
    }

    // --- Clone Task For Next Cycle ---

    suspend fun cloneTaskForNextCycle(
        oldTaskId: Long,
        nextDate: String,
        nextTime: String
    ): Long = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.beginTransaction()
        var newTaskId = -1L
        try {
            // 1. Fetch old task
            val oldTask = getTaskById(oldTaskId) ?: throw IllegalStateException("Old task $oldTaskId not found")

            // 2. Insert new task
            val taskValues = ContentValues().apply {
                put("title", oldTask.title)
                put("scheduled_date", nextDate)
                put("scheduled_time", nextTime)
                put("recurrence_rule", oldTask.recurrenceRule)
                put("remind_advance_minutes", oldTask.remindAdvanceMinutes)
                put("handoff_url", oldTask.handoffUrl)
                put("handoff_target_app", oldTask.handoffTargetApp)
                put("status", TaskStatus.NOT_STARTED)
                putNull("archive_zip_path")
                putNull("completed_at")
                put("created_at", System.currentTimeMillis())
            }
            newTaskId = db.insert("duty_tasks", null, taskValues)

            // 3. Clone stations, items, subitems
            val oldStations = getStationsForTask(oldTaskId)
            var firstNewStationId: Long? = null
            var firstNewStationName = ""

            for (station in oldStations) {
                val sValues = ContentValues().apply {
                    put("task_id", newTaskId)
                    put("name", station.name)
                    put("sort_order", station.sortOrder)
                    put("is_completed", 0)
                }
                val newStationId = db.insert("task_stations", null, sValues)
                if (firstNewStationId == null) {
                    firstNewStationId = newStationId
                    firstNewStationName = station.name
                }

                val oldItems = getItemsForStation(station.id)
                for (item in oldItems) {
                    val itemValues = ContentValues().apply {
                        put("station_id", newStationId)
                        put("title", item.title)
                        put("is_time_linear", if (item.isTimeLinear) 1 else 0)
                        put("time_start", item.timeStart)
                        put("time_end", item.timeEnd)
                        put("is_completed", 0)
                        put("is_exception", 0)
                        putNull("photo_uri")
                        putNull("note")
                        putNull("completed_at")
                        put("sort_order", item.sortOrder)
                    }
                    val newItemId = db.insert("check_items", null, itemValues)

                    val oldSubs = getSubItemsForItem(item.id)
                    for (sub in oldSubs) {
                        val subValues = ContentValues().apply {
                            put("parent_id", newItemId)
                            put("title", sub.title)
                            put("is_completed", 0)
                            put("sort_order", sub.sortOrder)
                        }
                        db.insert("sub_items", null, subValues)
                    }
                }
            }

            // 4. Update user settings active station to new first station
            if (firstNewStationId != null) {
                val setValues = ContentValues().apply {
                    put("active_station_id", firstNewStationId)
                    put("active_station_name", firstNewStationName)
                }
                db.update("user_settings", setValues, "id = 1", null)
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        notifyTableChanged("*")
        newTaskId
    }

    // --- Cursor Mappers ---

    private fun cursorToDutyTask(cursor: Cursor): DutyTask {
        return DutyTask(
            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
            title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
            scheduledDate = cursor.getString(cursor.getColumnIndexOrThrow("scheduled_date")),
            scheduledTime = cursor.getString(cursor.getColumnIndexOrThrow("scheduled_time")),
            recurrenceRule = cursor.getString(cursor.getColumnIndexOrThrow("recurrence_rule")),
            remindAdvanceMinutes = cursor.getInt(cursor.getColumnIndexOrThrow("remind_advance_minutes")),
            handoffUrl = cursor.getString(cursor.getColumnIndexOrThrow("handoff_url")),
            handoffTargetApp = cursor.getString(cursor.getColumnIndexOrThrow("handoff_target_app")),
            status = cursor.getString(cursor.getColumnIndexOrThrow("status")),
            archiveZipPath = cursor.getStringOrNull("archive_zip_path"),
            completedAt = cursor.getLongOrNull("completed_at"),
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"))
        )
    }

    private fun cursorToTaskStation(cursor: Cursor): TaskStation {
        return TaskStation(
            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
            taskId = cursor.getLong(cursor.getColumnIndexOrThrow("task_id")),
            name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
            sortOrder = cursor.getInt(cursor.getColumnIndexOrThrow("sort_order")),
            isCompleted = cursor.getInt(cursor.getColumnIndexOrThrow("is_completed")) == 1
        )
    }

    private fun cursorToCheckItem(cursor: Cursor): CheckItem {
        return CheckItem(
            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
            stationId = cursor.getLong(cursor.getColumnIndexOrThrow("station_id")),
            title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
            isTimeLinear = cursor.getInt(cursor.getColumnIndexOrThrow("is_time_linear")) == 1,
            timeStart = cursor.getStringOrNull("time_start"),
            timeEnd = cursor.getStringOrNull("time_end"),
            isCompleted = cursor.getInt(cursor.getColumnIndexOrThrow("is_completed")) == 1,
            isException = cursor.getInt(cursor.getColumnIndexOrThrow("is_exception")) == 1,
            photoUri = cursor.getStringOrNull("photo_uri"),
            note = cursor.getStringOrNull("note"),
            completedAt = cursor.getLongOrNull("completed_at"),
            sortOrder = cursor.getInt(cursor.getColumnIndexOrThrow("sort_order"))
        )
    }

    private fun cursorToSubItem(cursor: Cursor): SubItem {
        return SubItem(
            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
            parentId = cursor.getLong(cursor.getColumnIndexOrThrow("parent_id")),
            title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
            isCompleted = cursor.getInt(cursor.getColumnIndexOrThrow("is_completed")) == 1,
            sortOrder = cursor.getInt(cursor.getColumnIndexOrThrow("sort_order"))
        )
    }

    private fun cursorToUserSettings(cursor: Cursor): UserSettings {
        val themeModeIndex = cursor.getColumnIndex("theme_mode")
        val themeMode = if (themeModeIndex != -1 && !cursor.isNull(themeModeIndex)) {
            cursor.getString(themeModeIndex)
        } else {
            ThemeMode.SYSTEM
        }
        return UserSettings(
            activeStationId = cursor.getLong(cursor.getColumnIndexOrThrow("active_station_id")),
            activeStationName = cursor.getString(cursor.getColumnIndexOrThrow("active_station_name")),
            perfLevel = cursor.getString(cursor.getColumnIndexOrThrow("perf_level")),
            motionScale = cursor.getFloat(cursor.getColumnIndexOrThrow("motion_scale")),
            defaultHandoffUrl = cursor.getString(cursor.getColumnIndexOrThrow("default_handoff_url")),
            defaultHandoffApp = cursor.getString(cursor.getColumnIndexOrThrow("default_handoff_app")),
            themeMode = themeMode
        )
    }

    private fun Cursor.getStringOrNull(columnName: String): String? {
        val index = getColumnIndexOrThrow(columnName)
        return if (isNull(index)) null else getString(index)
    }

    private fun Cursor.getLongOrNull(columnName: String): Long? {
        val index = getColumnIndexOrThrow(columnName)
        return if (isNull(index)) null else getLong(index)
    }

    companion object {
        const val DATABASE_NAME = "oxherd.db"
        const val DATABASE_VERSION = 2

        @Volatile
        private var instance: OxherdDatabase? = null

        fun getInstance(context: Context): OxherdDatabase {
            return instance ?: synchronized(this) {
                instance ?: OxherdDatabase(context.applicationContext).also { instance = it }
            }
        }
    }
}
