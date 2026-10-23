package com.oxherd.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DutyDataTest {

    @Test
    fun testDutyTaskCreation() {
        val task = DutyTask(
            id = 1L,
            title = "周四图书馆晚班巡检",
            scheduledDate = "2026-10-23",
            scheduledTime = "20:00",
            recurrenceRule = "WEEKLY:4",
            remindAdvanceMinutes = 15,
            handoffTargetApp = "com.tencent.tim",
            status = TaskStatus.NOT_STARTED
        )

        assertEquals("周四图书馆晚班巡检", task.title)
        assertEquals("2026-10-23", task.scheduledDate)
        assertEquals("20:00", task.scheduledTime)
        assertEquals("WEEKLY:4", task.recurrenceRule)
        assertEquals(15, task.remindAdvanceMinutes)
        assertEquals(TaskStatus.NOT_STARTED, task.status)
        assertFalse(task.status == TaskStatus.COMPLETED)
    }

    @Test
    fun testCheckItemAndSubItems() {
        val checkItem = CheckItem(
            id = 10L,
            stationId = 1L,
            title = "20:00~20:30 开馆巡查与照明确认",
            isTimeLinear = true,
            timeStart = "20:00",
            timeEnd = "20:30"
        )
        val sub1 = SubItem(id = 101L, parentId = 10L, title = "检查大厅主灯开闭", isCompleted = true)
        val sub2 = SubItem(id = 102L, parentId = 10L, title = "确认301-315自习室电源插座通电", isCompleted = false)

        val itemWithSubs = CheckItemWithSubItems(
            item = checkItem,
            subItems = listOf(sub1, sub2)
        )

        assertTrue(itemWithSubs.item.isTimeLinear)
        assertEquals(2, itemWithSubs.subItems.size)
        assertTrue(itemWithSubs.subItems[0].isCompleted)
        assertFalse(itemWithSubs.subItems[1].isCompleted)
    }

    @Test
    fun testStationWithItemsHierarchy() {
        val station = TaskStation(
            id = 1L,
            taskId = 100L,
            name = "3楼自习区",
            sortOrder = 1,
            isCompleted = false
        )
        val stationWithItems = StationWithItems(station = station, items = emptyList())

        assertEquals("3楼自习区", stationWithItems.station.name)
        assertEquals(1, stationWithItems.station.sortOrder)
        assertFalse(stationWithItems.station.isCompleted)
    }

    @Test
    fun testUserSettingsDefaults() {
        val settings = UserSettings()
        assertEquals(1L, settings.activeStationId)
        assertEquals("默认岗位", settings.activeStationName)
        assertEquals(PerfLevel.FULL, settings.perfLevel)
        assertEquals(1.0f, settings.motionScale, 0.001f)
        assertEquals("com.tencent.tim", settings.defaultHandoffApp)
        assertEquals(ThemeMode.SYSTEM, settings.themeMode)
    }

    @Test
    fun testRecurrenceNextDateCalculation() {
        val baseDate = LocalDate.of(2026, 10, 22) // Thursday
        val nextDate = baseDate.plusWeeks(1)
        assertEquals(LocalDate.of(2026, 10, 29), nextDate)
    }

    @Test
    fun testCustomCheckItemAndSubItemMutationModels() {
        val customStation = TaskStation(
            id = 99L,
            taskId = 1L,
            name = "B1配电房",
            sortOrder = 9,
            isCompleted = false
        )
        val customItem = CheckItem(
            id = 501L,
            stationId = customStation.id,
            title = "检查变压器运行仪表",
            isTimeLinear = false,
            sortOrder = 1
        )
        val customSub = SubItem(
            id = 901L,
            parentId = customItem.id,
            title = "记录三相电压是否在380V±5%",
            isCompleted = false,
            sortOrder = 1
        )

        assertEquals(99L, customStation.id)
        assertEquals("B1配电房", customStation.name)
        assertEquals(501L, customItem.id)
        assertFalse(customItem.isTimeLinear)
        assertEquals(901L, customSub.id)
        assertEquals(501L, customSub.parentId)
        assertFalse(customSub.isCompleted)
    }

    @Test
    fun testDatabaseConstantsAndThemeModeSupport() {
        assertEquals(2, OxherdDatabase.DATABASE_VERSION)
        assertEquals("oxherd.db", OxherdDatabase.DATABASE_NAME)
        assertEquals("SYSTEM", ThemeMode.SYSTEM)
        assertEquals("LIGHT", ThemeMode.LIGHT)
        assertEquals("DARK", ThemeMode.DARK)

        val settings = UserSettings()
        assertEquals(ThemeMode.SYSTEM, settings.themeMode)

        val darkSettings = settings.copy(themeMode = ThemeMode.DARK)
        assertEquals("DARK", darkSettings.themeMode)

        val lightSettings = settings.copy(themeMode = ThemeMode.LIGHT)
        assertEquals("LIGHT", lightSettings.themeMode)
    }

    @Test
    fun testCleanSlateDefaultTaskAndStationModel() {
        val defaultTask = DutyTask(
            id = 1L,
            title = "我的值班任务",
            scheduledDate = "2026-10-23",
            scheduledTime = "20:00",
            recurrenceRule = "WEEKLY:4",
            remindAdvanceMinutes = 15,
            status = TaskStatus.NOT_STARTED
        )
        val defaultStation = TaskStation(
            id = 1L,
            taskId = defaultTask.id,
            name = "默认岗位",
            sortOrder = 1,
            isCompleted = false
        )

        assertEquals("我的值班任务", defaultTask.title)
        assertEquals(TaskStatus.NOT_STARTED, defaultTask.status)
        assertEquals("默认岗位", defaultStation.name)
        assertEquals(1, defaultStation.sortOrder)
        assertFalse(defaultStation.isCompleted)

        val stationWithItems = StationWithItems(defaultStation, emptyList())
        assertEquals(0, stationWithItems.items.size)
    }
}
