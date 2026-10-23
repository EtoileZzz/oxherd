package com.oxherd.app.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Data bundle representing exported or imported duty checklist and configuration.
 */
data class DutyConfigExport(
    val version: String = "1.3.0",
    val exportedAt: Long = System.currentTimeMillis(),
    val task: DutyTask,
    val stations: List<TaskStation>,
    val items: List<CheckItem>,
    val subItems: List<SubItem>
)

/**
 * Manages duty inspection configuration export, import, file serialization, and system share intents.
 * Built with standard Android org.json for zero external dependencies.
 */
object DutyConfigManager {

    private const val CURRENT_VERSION = "1.3.0"

    /**
     * Serializes duty task, stations, check items, and sub items into a formatted JSON string.
     */
    fun exportConfigToJson(
        task: DutyTask,
        stations: List<TaskStation>,
        items: List<CheckItem>,
        subItems: List<SubItem>
    ): String {
        val root = JSONObject()
        root.put("version", CURRENT_VERSION)
        root.put("exported_at", System.currentTimeMillis())

        // Task
        val taskObj = JSONObject().apply {
            put("id", task.id)
            put("title", task.title)
            put("scheduled_date", task.scheduledDate)
            put("scheduled_time", task.scheduledTime)
            put("recurrence_rule", task.recurrenceRule)
            put("remind_advance_minutes", task.remindAdvanceMinutes)
            put("handoff_url", task.handoffUrl)
            put("handoff_target_app", task.handoffTargetApp)
            put("status", task.status)
            task.archiveZipPath?.let { put("archive_zip_path", it) }
            task.completedAt?.let { put("completed_at", it) }
            put("created_at", task.createdAt)
        }
        root.put("task", taskObj)

        // Stations
        val stationsArray = JSONArray()
        for (station in stations) {
            val stationObj = JSONObject().apply {
                put("id", station.id)
                put("task_id", station.taskId)
                put("name", station.name)
                put("sort_order", station.sortOrder)
                put("is_completed", station.isCompleted)
            }
            stationsArray.put(stationObj)
        }
        root.put("stations", stationsArray)

        // Items
        val itemsArray = JSONArray()
        for (item in items) {
            val itemObj = JSONObject().apply {
                put("id", item.id)
                put("station_id", item.stationId)
                put("title", item.title)
                put("is_time_linear", item.isTimeLinear)
                item.timeStart?.let { put("time_start", it) }
                item.timeEnd?.let { put("time_end", it) }
                put("is_completed", item.isCompleted)
                put("is_exception", item.isException)
                item.photoUri?.let { put("photo_uri", it) }
                item.note?.let { put("note", it) }
                item.completedAt?.let { put("completed_at", it) }
                put("sort_order", item.sortOrder)
            }
            itemsArray.put(itemObj)
        }
        root.put("items", itemsArray)

        // Sub items
        val subItemsArray = JSONArray()
        for (sub in subItems) {
            val subObj = JSONObject().apply {
                put("id", sub.id)
                put("parent_id", sub.parentId)
                put("title", sub.title)
                put("is_completed", sub.isCompleted)
                put("sort_order", sub.sortOrder)
            }
            subItemsArray.put(subObj)
        }
        root.put("sub_items", subItemsArray)

        return root.toString(2)
    }

    /**
     * Writes JSON string to an output stream (e.g. from ActivityResultContracts.CreateDocument).
     */
    fun writeConfigToStream(jsonStr: String, outputStream: OutputStream) {
        outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(jsonStr)
            writer.flush()
        }
    }

    /**
     * Creates an Android Intent.ACTION_SEND for quick sharing (WeChat / TIM / Files)
     * via FileProvider with a temporary cached JSON file.
     */
    fun createShareIntent(context: Context, jsonStr: String): Intent {
        val fileName = "oxherd_duty_config_${System.currentTimeMillis()}.json"
        val cacheFile = File(context.cacheDir, fileName)
        cacheFile.writeText(jsonStr, Charsets.UTF_8)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            cacheFile
        )

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Oxherd 值班配置备份")
            putExtra(Intent.EXTRA_TEXT, "这是 Oxherd 巡检值班配置导出文件，可在应用中一键导入。")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        return Intent.createChooser(sendIntent, "分享值班配置 (微信/TIM/文件)")
    }

    /**
     * Reads and parses duty configuration from an input stream.
     */
    fun parseConfigFromStream(inputStream: InputStream): DutyConfigExport {
        val jsonStr = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        return parseConfigFromJson(jsonStr)
    }

    /**
     * Parses and validates a JSON string into DutyConfigExport.
     * Throws IllegalArgumentException if essential fields are missing.
     */
    fun parseConfigFromJson(jsonStr: String): DutyConfigExport {
        val root = JSONObject(jsonStr)
        val version = root.optString("version", CURRENT_VERSION)
        val exportedAt = root.optLong("exported_at", System.currentTimeMillis())

        val taskObj = root.optJSONObject("task")
            ?: throw IllegalArgumentException("Missing 'task' object in duty configuration JSON")

        val task = DutyTask(
            id = taskObj.optLong("id", 1L),
            title = taskObj.optString("title", "我的值班任务"),
            scheduledDate = taskObj.optString("scheduled_date", "2026-10-23"),
            scheduledTime = taskObj.optString("scheduled_time", "20:00"),
            recurrenceRule = taskObj.optString("recurrence_rule", "WEEKLY:4"),
            remindAdvanceMinutes = taskObj.optInt("remind_advance_minutes", 15),
            handoffUrl = taskObj.optString("handoff_url", ""),
            handoffTargetApp = taskObj.optString("handoff_target_app", "com.tencent.tim"),
            status = taskObj.optString("status", TaskStatus.NOT_STARTED),
            archiveZipPath = taskObj.getStringOrNull("archive_zip_path"),
            completedAt = taskObj.getLongOrNull("completed_at"),
            createdAt = taskObj.optLong("created_at", System.currentTimeMillis())
        )

        val stationsArray = root.optJSONArray("stations") ?: JSONArray()
        val stations = mutableListOf<TaskStation>()
        for (i in 0 until stationsArray.length()) {
            val sObj = stationsArray.getJSONObject(i)
            stations.add(
                TaskStation(
                    id = sObj.optLong("id", i + 1L),
                    taskId = sObj.optLong("task_id", task.id),
                    name = sObj.optString("name", "岗位 ${i + 1}"),
                    sortOrder = sObj.optInt("sort_order", i),
                    isCompleted = sObj.optBoolean("is_completed", false)
                )
            )
        }

        // Ensure at least one station exists
        if (stations.isEmpty()) {
            stations.add(TaskStation(id = 1L, taskId = task.id, name = "默认岗位", sortOrder = 0))
        }

        val itemsArray = root.optJSONArray("items") ?: JSONArray()
        val items = mutableListOf<CheckItem>()
        for (i in 0 until itemsArray.length()) {
            val iObj = itemsArray.getJSONObject(i)
            items.add(
                CheckItem(
                    id = iObj.optLong("id", i + 1L),
                    stationId = iObj.optLong("station_id", stations.first().id),
                    title = iObj.optString("title", "巡检项 ${i + 1}"),
                    isTimeLinear = iObj.optBoolean("is_time_linear", false),
                    timeStart = iObj.getStringOrNull("time_start"),
                    timeEnd = iObj.getStringOrNull("time_end"),
                    isCompleted = iObj.optBoolean("is_completed", false),
                    isException = iObj.optBoolean("is_exception", false),
                    photoUri = iObj.getStringOrNull("photo_uri"),
                    note = iObj.getStringOrNull("note"),
                    completedAt = iObj.getLongOrNull("completed_at"),
                    sortOrder = iObj.optInt("sort_order", i)
                )
            )
        }

        val subItemsArray = root.optJSONArray("sub_items") ?: JSONArray()
        val subItems = mutableListOf<SubItem>()
        for (i in 0 until subItemsArray.length()) {
            val subObj = subItemsArray.getJSONObject(i)
            subItems.add(
                SubItem(
                    id = subObj.optLong("id", i + 1L),
                    parentId = subObj.optLong("parent_id", 0L),
                    title = subObj.optString("title", "子项 ${i + 1}"),
                    isCompleted = subObj.optBoolean("is_completed", false),
                    sortOrder = subObj.optInt("sort_order", i)
                )
            )
        }

        return DutyConfigExport(
            version = version,
            exportedAt = exportedAt,
            task = task,
            stations = stations,
            items = items,
            subItems = subItems
        )
    }

    private fun JSONObject.getStringOrNull(key: String): String? {
        return if (has(key) && !isNull(key)) optString(key) else null
    }

    private fun JSONObject.getLongOrNull(key: String): Long? {
        return if (has(key) && !isNull(key)) optLong(key) else null
    }
}
