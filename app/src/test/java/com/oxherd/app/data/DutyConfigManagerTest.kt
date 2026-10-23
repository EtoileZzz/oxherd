package com.oxherd.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class DutyConfigManagerTest {

    @Test
    fun testExportAndImportJsonRoundTrip() {
        val task = DutyTask(
            id = 1L,
            title = "周四图书馆晚班巡检",
            scheduledDate = "2026-10-23",
            scheduledTime = "20:00",
            recurrenceRule = "WEEKLY:4",
            remindAdvanceMinutes = 15,
            status = TaskStatus.NOT_STARTED,
            handoffUrl = "https://example.com/form",
            handoffTargetApp = "com.tencent.tim"
        )
        val station = TaskStation(
            id = 10L,
            taskId = 1L,
            name = "图书馆一楼",
            sortOrder = 0,
            isCompleted = false
        )
        val item = CheckItem(
            id = 100L,
            stationId = 10L,
            title = "自习室巡查",
            isTimeLinear = true,
            timeStart = "20:00",
            timeEnd = "20:30",
            isCompleted = true,
            isException = true,
            photoUri = "content://media/photos/101.jpg",
            note = "发现插座松动",
            completedAt = 1771234567890L,
            sortOrder = 1
        )
        val subItem = SubItem(
            id = 1000L,
            parentId = 100L,
            title = "电源插座检查",
            isCompleted = true,
            sortOrder = 0
        )

        val jsonStr = DutyConfigManager.exportConfigToJson(
            task = task,
            stations = listOf(station),
            items = listOf(item),
            subItems = listOf(subItem)
        )

        assertNotNull(jsonStr)
        assertTrue(jsonStr.contains("周四图书馆晚班巡检"))
        assertTrue(jsonStr.contains("发现插座松动"))
        assertTrue(jsonStr.contains("content://media/photos/101.jpg"))

        val outStream = ByteArrayOutputStream()
        DutyConfigManager.writeConfigToStream(jsonStr, outStream)
        val inStream = ByteArrayInputStream(outStream.toByteArray())

        val imported = DutyConfigManager.parseConfigFromStream(inStream)

        assertEquals("周四图书馆晚班巡检", imported.task.title)
        assertEquals("https://example.com/form", imported.task.handoffUrl)
        assertEquals("com.tencent.tim", imported.task.handoffTargetApp)
        assertEquals(1, imported.stations.size)
        assertEquals("图书馆一楼", imported.stations[0].name)
        assertEquals(1, imported.items.size)
        assertEquals("自习室巡查", imported.items[0].title)
        assertTrue(imported.items[0].isTimeLinear)
        assertEquals("20:00", imported.items[0].timeStart)
        assertEquals("20:30", imported.items[0].timeEnd)
        assertTrue(imported.items[0].isCompleted)
        assertTrue(imported.items[0].isException)
        assertEquals("content://media/photos/101.jpg", imported.items[0].photoUri)
        assertEquals("发现插座松动", imported.items[0].note)
        assertEquals(1771234567890L, imported.items[0].completedAt)
        assertEquals(1, imported.subItems.size)
        assertEquals("电源插座检查", imported.subItems[0].title)
        assertTrue(imported.subItems[0].isCompleted)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testParseInvalidJsonThrowsException() {
        val invalidJson = """{ "version": "1.3.0", "items": [] }"""
        DutyConfigManager.parseConfigFromJson(invalidJson)
    }

    @Test
    fun testParseJsonWithDefaultStationFallback() {
        val jsonStr = """
            {
                "version": "1.3.0",
                "task": {
                    "id": 1,
                    "title": "默认测试",
                    "scheduled_date": "2026-10-23",
                    "scheduled_time": "20:00",
                    "recurrence_rule": "WEEKLY:4",
                    "remind_advance_minutes": 15,
                    "status": "NOT_STARTED"
                },
                "stations": [],
                "items": [],
                "sub_items": []
            }
        """.trimIndent()

        val parsed = DutyConfigManager.parseConfigFromJson(jsonStr)
        assertEquals("默认测试", parsed.task.title)
        assertEquals(1, parsed.stations.size)
        assertEquals("默认岗位", parsed.stations[0].name)
        assertTrue(parsed.items.isEmpty())
        assertTrue(parsed.subItems.isEmpty())
    }

    @Test
    fun testUserSettingsNewFields() {
        val settings = UserSettings(
            notifyEnabled = true,
            notifyAdvanceMinutes = 30,
            notifySound = false,
            notifyVibrate = true,
            notifySlotStart = false,
            autoRestoreOnBoot = true
        )

        assertTrue(settings.notifyEnabled)
        assertEquals(30, settings.notifyAdvanceMinutes)
        assertFalse(settings.notifySound)
        assertTrue(settings.notifyVibrate)
        assertFalse(settings.notifySlotStart)
        assertTrue(settings.autoRestoreOnBoot)

        val updated = settings.copy(notifyAdvanceMinutes = 60, notifySound = true)
        assertEquals(60, updated.notifyAdvanceMinutes)
        assertTrue(updated.notifySound)
    }
}
