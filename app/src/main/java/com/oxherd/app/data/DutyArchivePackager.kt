package com.oxherd.app.data

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Packages a completed duty shift into a compressed archive (.zip)
 * containing report.txt, metadata.json, and all session evidence photos.
 */
class DutyArchivePackager(private val context: Context) {

    /**
     * Creates an archive ZIP file for the given task and its stations hierarchy.
     *
     * @param task DutyTask metadata
     * @param stationsWithItems List of stations with nested check items and sub-items
     * @return Absolute path of the generated ZIP file
     */
    suspend fun packageDutyShift(
        task: DutyTask,
        stationsWithItems: List<StationWithItems>
    ): String = withContext(Dispatchers.IO) {
        val archivesDir = context.getExternalFilesDir("archives")
            ?: File(context.filesDir, "archives")
        if (!archivesDir.exists()) {
            archivesDir.mkdirs()
        }

        val cleanTitle = task.title.replace(Regex("[\\\\/:*?\"<>|\\s]"), "_")
        val cleanDate = task.scheduledDate.replace("-", "")
        val zipFileName = "oxherd_巡查归档_${cleanTitle}_${cleanDate}.zip"
        val zipFile = File(archivesDir, zipFileName)

        // If file exists from previous run, delete it
        if (zipFile.exists()) {
            zipFile.delete()
        }

        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zipOut ->
            // 1. Write report.txt
            val reportContent = generateReportText(task, stationsWithItems)
            zipOut.putNextEntry(ZipEntry("report.txt"))
            zipOut.write(reportContent.toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()

            // 2. Write metadata.json
            val jsonContent = generateMetadataJson(task, stationsWithItems)
            zipOut.putNextEntry(ZipEntry("metadata.json"))
            zipOut.write(jsonContent.toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()

            // 3. Write photos/
            for (stationWithItems in stationsWithItems) {
                val cleanStationName = stationWithItems.station.name.replace(Regex("[\\\\/:*?\"<>|\\s]"), "_")
                for (itemWithSubs in stationWithItems.items) {
                    val item = itemWithSubs.item
                    val photoUriStr = item.photoUri
                    if (!photoUriStr.isNullOrBlank()) {
                        val cleanItemTitle = item.title.take(20).replace(Regex("[\\\\/:*?\"<>|\\s]"), "_")
                        val photoEntryPath = "photos/$cleanStationName/item_${item.id}_${cleanItemTitle}.jpg"
                        try {
                            openPhotoInputStream(context, photoUriStr)?.use { input ->
                                zipOut.putNextEntry(ZipEntry(photoEntryPath))
                                input.copyTo(zipOut)
                                zipOut.closeEntry()
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to include photo $photoUriStr in archive", e)
                        }
                    }
                }
            }
            zipOut.finish()
        }

        Log.i(TAG, "Successfully created duty archive: ${zipFile.absolutePath} (${zipFile.length()} bytes)")
        zipFile.absolutePath
    }

    private fun generateReportText(
        task: DutyTask,
        stationsWithItems: List<StationWithItems>
    ): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val completedAtStr = task.completedAt?.let { dateFormat.format(Date(it)) } ?: "未记录"
        val reportGeneratedAt = dateFormat.format(Date())

        var totalItems = 0
        var completedItems = 0
        var totalExceptions = 0
        var totalPhotos = 0

        for (s in stationsWithItems) {
            for (i in s.items) {
                totalItems++
                if (i.item.isCompleted) completedItems++
                if (i.item.isException) totalExceptions++
                if (!i.item.photoUri.isNullOrBlank()) totalPhotos++
            }
        }

        val completionPercent = if (totalItems > 0) (completedItems * 100 / totalItems) else 0

        val sb = StringBuilder()
        sb.appendLine("======================================================================")
        sb.appendLine("                      OXHERD 巡检值班完成报告                          ")
        sb.appendLine("======================================================================")
        sb.appendLine("任务名称: ${task.title}")
        sb.appendLine("巡查排班: ${task.scheduledDate} ${task.scheduledTime}")
        sb.appendLine("循环规则: ${task.recurrenceRule}")
        sb.appendLine("巡检状态: ${task.status}")
        sb.appendLine("完成时间: $completedAtStr")
        sb.appendLine("报告生成: $reportGeneratedAt")
        sb.appendLine("----------------------------------------------------------------------")
        sb.appendLine("【巡检概况汇总】")
        sb.appendLine("  巡检区域数: ${stationsWithItems.size} 个")
        sb.appendLine("  检查项目数: $totalItems 项 (已完成 $completedItems 项, 进度: $completionPercent%)")
        sb.appendLine("  异常问题数: $totalExceptions 项")
        sb.appendLine("  照片佐证数: $totalPhotos 张")
        sb.appendLine("----------------------------------------------------------------------")
        sb.appendLine("【区域明细与检查清单】")

        stationsWithItems.forEachIndexed { sIndex, stationWithItems ->
            val station = stationWithItems.station
            val sStatus = if (station.isCompleted) "[已完成]" else "[未完成]"
            sb.appendLine()
            sb.appendLine("[区域 ${sIndex + 1}] ${station.name} $sStatus")

            stationWithItems.items.forEachIndexed { iIndex, itemWithSubs ->
                val item = itemWithSubs.item
                val statusMarker = if (item.isCompleted) "✓" else "✗"
                val linearTag = if (item.isTimeLinear) " [时序 ${item.timeStart ?: ""}~${item.timeEnd ?: ""}]" else " [常规]"
                val exceptionTag = if (item.isException) " ⚠️【异常】" else ""

                sb.appendLine("  $statusMarker ${iIndex + 1}. ${item.title}$linearTag$exceptionTag")
                if (!item.note.isNullOrBlank()) {
                    sb.appendLine("     巡查记录/备注: ${item.note}")
                }
                if (!item.photoUri.isNullOrBlank()) {
                    sb.appendLine("     照片附件: 已关联存证")
                }

                // Sub-items
                for (sub in itemWithSubs.subItems) {
                    val subMarker = if (sub.isCompleted) "[✓]" else "[ ]"
                    sb.appendLine("       $subMarker ${sub.title}")
                }
            }
        }

        sb.appendLine()
        sb.appendLine("======================================================================")
        sb.appendLine("                     Oxherd Patrol Report End                         ")
        sb.appendLine("======================================================================")

        return sb.toString()
    }

    private fun generateMetadataJson(
        task: DutyTask,
        stationsWithItems: List<StationWithItems>
    ): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())

        val taskObj = JSONObject().apply {
            put("id", task.id)
            put("title", task.title)
            put("scheduledDate", task.scheduledDate)
            put("scheduledTime", task.scheduledTime)
            put("recurrenceRule", task.recurrenceRule)
            put("remindAdvanceMinutes", task.remindAdvanceMinutes)
            put("handoffUrl", task.handoffUrl)
            put("handoffTargetApp", task.handoffTargetApp)
            put("status", task.status)
            put("completedAt", task.completedAt ?: JSONObject.NULL)
            put("createdAt", task.createdAt)
        }
        root.put("task", taskObj)

        val stationsArray = JSONArray()
        for (s in stationsWithItems) {
            val sObj = JSONObject().apply {
                put("id", s.station.id)
                put("name", s.station.name)
                put("sortOrder", s.station.sortOrder)
                put("isCompleted", s.station.isCompleted)
            }

            val itemsArray = JSONArray()
            for (i in s.items) {
                val item = i.item
                val iObj = JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("isTimeLinear", item.isTimeLinear)
                    put("timeStart", item.timeStart ?: JSONObject.NULL)
                    put("timeEnd", item.timeEnd ?: JSONObject.NULL)
                    put("isCompleted", item.isCompleted)
                    put("isException", item.isException)
                    put("photoUri", item.photoUri ?: JSONObject.NULL)
                    put("note", item.note ?: JSONObject.NULL)
                    put("completedAt", item.completedAt ?: JSONObject.NULL)
                    put("sortOrder", item.sortOrder)
                }

                val subsArray = JSONArray()
                for (sub in i.subItems) {
                    val subObj = JSONObject().apply {
                        put("id", sub.id)
                        put("title", sub.title)
                        put("isCompleted", sub.isCompleted)
                        put("sortOrder", sub.sortOrder)
                    }
                    subsArray.put(subObj)
                }
                iObj.put("subItems", subsArray)
                itemsArray.put(iObj)
            }
            sObj.put("items", itemsArray)
            stationsArray.put(sObj)
        }
        root.put("stations", stationsArray)

        return root.toString(2)
    }

    private fun openPhotoInputStream(context: Context, uriString: String): InputStream? {
        return try {
            val uri = Uri.parse(uriString)
            when (uri.scheme) {
                "content" -> context.contentResolver.openInputStream(uri)
                "file" -> File(uri.path ?: uriString).inputStream()
                else -> {
                    val file = File(uriString)
                    if (file.exists()) file.inputStream() else null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not open stream for photo URI: $uriString", e)
            null
        }
    }

    companion object {
        private const val TAG = "DutyArchivePackager"
    }
}
