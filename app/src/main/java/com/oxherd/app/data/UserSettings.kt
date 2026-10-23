package com.oxherd.app.data

/**
 * User application settings and active navigation state.
 *
 * @property activeStationId Currently active station ID for duty inspection
 * @property activeStationName Display name of active station
 * @property perfLevel Animation / rendering performance level: "FULL", "LITE", "MIN"
 * @property motionScale Global motion duration multiplier (default 1.0f)
 * @property defaultHandoffUrl Default handover destination URL / webhook
 * @property defaultHandoffApp Default quick-share app package name (default "com.tencent.tim")
 * @property themeMode Application visual theme mode: "SYSTEM", "LIGHT", "DARK"
 */
data class UserSettings(
    val activeStationId: Long = 1L,
    val activeStationName: String = "默认岗位",
    val perfLevel: String = PerfLevel.FULL,
    val motionScale: Float = 1.0f,
    val defaultHandoffUrl: String = "",
    val defaultHandoffApp: String = "com.tencent.tim",
    val themeMode: String = ThemeMode.SYSTEM
)

object PerfLevel {
    const val FULL = "FULL"
    const val LITE = "LITE"
    const val MIN = "MIN"
}

object ThemeMode {
    const val SYSTEM = "SYSTEM"
    const val LIGHT = "LIGHT"
    const val DARK = "DARK"
}
