package com.oxherd.app.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oxherd.app.data.PerfLevel
import com.oxherd.app.ui.motion.SlidePillSegmentedControl
import com.oxherd.app.ui.motion.flashButton

/**
 * Settings screen allowing duty station selection, Fluid Motion preferences tuning,
 * and integration link setup.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: DutyViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val currentSettings = uiState.userSettings
    var formUrl by remember(currentSettings.defaultHandoffUrl) {
        mutableStateOf(currentSettings.defaultHandoffUrl)
    }
    var targetApp by remember(currentSettings.defaultHandoffApp) {
        mutableStateOf(currentSettings.defaultHandoffApp)
    }

    val motionModes = listOf("完整 (FULL)", "标准 (LITE)", "极速 (MIN)")
    val currentModeIndex = when (currentSettings.perfLevel) {
        PerfLevel.LITE -> 1
        PerfLevel.MIN -> 2
        else -> 0
    }

    val speedMultipliers = listOf(0.5f, 1.0f, 1.5f, 2.0f)
    val speedLabels = listOf("0.5x", "1.0x", "1.5x", "2.0x")
    val currentSpeedIndex = speedMultipliers.indexOfFirst {
        kotlin.math.abs(it - currentSettings.motionScale) < 0.1f
    }.let { if (it >= 0) it else 1 }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFF0F1117),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "值班巡检与动效设置",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E222D))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "←", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F1117)
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ── Section 1: Active Station Switcher ──
            SettingsSectionHeader(title = "当前值班岗位 (ACTIVE POST)", subtitle = "专人专岗：切换负责楼层后，主页将专注呈现该点位任务")

            Spacer(modifier = Modifier.height(10.dp))

            val stations = uiState.stations
            var isAddingStationInSettings by remember { mutableStateOf(false) }
            var newStationNameInSettings by remember { mutableStateOf("") }

            if (stations.isNotEmpty()) {
                val stationNames = stations.map { it.name }
                val activeStationIndex = stations.indexOfFirst { it.id == currentSettings.activeStationId }
                    .let { if (it >= 0) it else 0 }

                SlidePillSegmentedControl(
                    items = stationNames,
                    selectedIndex = activeStationIndex,
                    onItemSelected = { index ->
                        val selectedStation = stations.getOrNull(index)
                        if (selectedStation != null) {
                            viewModel.switchStation(selectedStation.id)
                        }
                    },
                    containerColor = Color(0xFF1A1E29),
                    pillColor = Color(0xFF2E5B9A),
                    activeContentColor = Color.White,
                    contentColor = Color(0xFF8E9AAF),
                    height = 44.dp
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Station Management list
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF141822))
                        .padding(10.dp)
                ) {
                    stations.forEach { st ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = st.name,
                                color = if (st.id == currentSettings.activeStationId) Color(0xFF8BB5FF) else Color(0xFFB0C4DE),
                                fontSize = 13.sp,
                                fontWeight = if (st.id == currentSettings.activeStationId) FontWeight.Bold else FontWeight.Normal
                            )
                            if (stations.size > 1) {
                                Text(
                                    text = "删除",
                                    fontSize = 11.sp,
                                    color = Color(0xFFFF5252),
                                    modifier = Modifier
                                        .clickable { viewModel.deleteStation(st.id) }
                                        .padding(4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (isAddingStationInSettings) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = newStationNameInSettings,
                                onValueChange = { newStationNameInSettings = it },
                                placeholder = { Text("岗位名称...", fontSize = 12.sp, color = Color(0xFF5A667A)) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF5B8DEF),
                                    unfocusedBorderColor = Color(0xFF2C3446),
                                    focusedContainerColor = Color(0xFF1B2230),
                                    unfocusedContainerColor = Color(0xFF1B2230),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .height(38.dp)
                                    .flashButton(accentColor = Color(0xFF5B8DEF), cornerRadiusDp = 8.dp) {
                                        if (newStationNameInSettings.isNotBlank()) {
                                            viewModel.createStation(newStationNameInSettings.trim())
                                            newStationNameInSettings = ""
                                            isAddingStationInSettings = false
                                        }
                                    }
                                    .background(Color(0xFF2563EB), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("添加", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .height(38.dp)
                                    .clickable { isAddingStationInSettings = false }
                                    .padding(horizontal = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("取消", fontSize = 12.sp, color = Color(0xFF8E9AAF))
                            }
                        }
                    } else {
                        Text(
                            text = "+ 添加自定义岗位 / 楼层",
                            fontSize = 12.sp,
                            color = Color(0xFF5B8DEF),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clickable { isAddingStationInSettings = true }
                                .padding(vertical = 4.dp)
                        )
                    }
                }
            } else {
                Text(
                    text = "当前岗位：${currentSettings.activeStationName}",
                    color = Color.White,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Section 2: Fluid Motion Design Specification Preferences ──
            SettingsSectionHeader(
                title = "流体物理动效引擎 (FLUID MOTION)",
                subtitle = "基于 Fluid Motion 规范的非线性贝塞尔物理补间与分级降级体系"
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "动效渲染档位 (Performance Tier)",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFB0C4DE)
            )
            Spacer(modifier = Modifier.height(6.dp))

            SlidePillSegmentedControl(
                items = motionModes,
                selectedIndex = currentModeIndex,
                onItemSelected = { index ->
                    val newLevel = when (index) {
                        1 -> PerfLevel.LITE
                        2 -> PerfLevel.MIN
                        else -> PerfLevel.FULL
                    }
                    viewModel.updateUserSettings(currentSettings.copy(perfLevel = newLevel))
                },
                containerColor = Color(0xFF1A1E29),
                pillColor = Color(0xFF334155),
                activeContentColor = Color(0xFF60A5FA),
                contentColor = Color(0xFF8E9AAF),
                height = 42.dp
            )

            Spacer(modifier = Modifier.height(6.dp))
            val modeExplanation = when (currentModeIndex) {
                1 -> "标准流畅 (LITE)：保留真实几何补间与 3D 翻转，去除昂贵 GPU 毛玻璃滤镜"
                2 -> "极速省电 (MIN)：纯 2D 位移与渐变，错峰延时清零 (0ms)，动画时长 ≤240ms"
                else -> "完整动效 (FULL)：3D 透视角度全开、多层高斯模糊与毛玻璃、30ms 错峰拼合"
            }
            Text(
                text = modeExplanation,
                fontSize = 11.sp,
                color = Color(0xFF718096),
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "动效时长倍率 (Global Speed Multiplier)",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFB0C4DE)
            )
            Spacer(modifier = Modifier.height(6.dp))

            SlidePillSegmentedControl(
                items = speedLabels,
                selectedIndex = currentSpeedIndex,
                onItemSelected = { index ->
                    val newScale = speedMultipliers.getOrElse(index) { 1.0f }
                    viewModel.updateUserSettings(currentSettings.copy(motionScale = newScale))
                },
                containerColor = Color(0xFF1A1E29),
                pillColor = Color(0xFF2563EB),
                activeContentColor = Color.White,
                contentColor = Color(0xFF8E9AAF),
                height = 40.dp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── Section 3: Integration & External Links ──
            SettingsSectionHeader(
                title = "交班协同与链接联动 (HANDOFF LINKS)",
                subtitle = "打卡完成后自动提供一键跳转填表与工作群汇报支持"
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "预置巡查表单链接 (Web Form URL)",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFB0C4DE)
            )
            Spacer(modifier = Modifier.height(4.dp))

            OutlinedTextField(
                value = formUrl,
                onValueChange = { formUrl = it },
                placeholder = { Text("如: https://docs.qq.com/form/page/...", color = Color(0xFF4A5568), fontSize = 13.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF5B8DEF),
                    unfocusedBorderColor = Color(0xFF2D3748),
                    focusedContainerColor = Color(0xFF151922),
                    unfocusedContainerColor = Color(0xFF151922),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "目标工作汇报应用包名 (Target App Package)",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFB0C4DE)
            )
            Spacer(modifier = Modifier.height(4.dp))

            OutlinedTextField(
                value = targetApp,
                onValueChange = { targetApp = it },
                placeholder = { Text("默认: com.tencent.tim", color = Color(0xFF4A5568), fontSize = 13.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF5B8DEF),
                    unfocusedBorderColor = Color(0xFF2D3748),
                    focusedContainerColor = Color(0xFF151922),
                    unfocusedContainerColor = Color(0xFF151922),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Save Settings Button with FlashButton micro-interaction
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .flashButton(
                        accentColor = Color(0xFF5B8DEF),
                        cornerRadiusDp = 12.dp
                    ) {
                        viewModel.updateUserSettings(
                            currentSettings.copy(
                                defaultHandoffUrl = formUrl.trim(),
                                defaultHandoffApp = targetApp.trim().ifBlank { "com.tencent.tim" }
                            )
                        )
                        Toast.makeText(context, "设置已保存", Toast.LENGTH_SHORT).show()
                        onBack()
                    }
                    .background(Color(0xFF2563EB), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "保存并应用配置",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Section 4: Schedule info box ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF131722))
                    .border(1.dp, Color(0xFF242C3F), RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "⏰", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "自动排班与系统通知唤醒",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "周期规则：每周四 20:00 准时执勤\n提醒设定：提前 15 分钟发出高优先级状态栏通知，点击通知瞬间秒开进入巡检打卡页。",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = Color(0xFF8E9AAF)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsSectionHeader(
    title: String,
    subtitle: String
) {
    Column {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF5B8DEF),
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subtitle,
            fontSize = 12.sp,
            color = Color(0xFF718096),
            lineHeight = 16.sp
        )
    }
}
