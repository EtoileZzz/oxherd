package com.oxherd.app.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oxherd.app.data.CheckItem
import com.oxherd.app.data.DutyConfigExport
import com.oxherd.app.data.DutyConfigManager
import com.oxherd.app.data.KeepAliveHelper
import com.oxherd.app.data.PerfLevel
import com.oxherd.app.theme.LocalOxherdColors
import com.oxherd.app.theme.OxherdColors
import com.oxherd.app.ui.motion.SlidePillSegmentedControl
import com.oxherd.app.ui.motion.flashButton

/**
 * Settings screen for Oxherd v1.3.0.
 * Organized into clear, responsive cards:
 * 1. 值班岗位管理 (STATIONS)
 * 2. 巡查条目编排与显示 (ITEMS ORDER & TIMING)
 * 3. 通知与唤醒自启动保障 (NOTIFICATIONS & KEEP-ALIVE)
 * 4. 配置备份与导入导出 (BACKUP & SHARE)
 * 5. 外观色彩与物理动效 (THEME & PERFORMANCE)
 * 6. 交班外链设置 (HANDOFF LINKS)
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
    val colors = LocalOxherdColors.current
    val currentSettings = uiState.userSettings

    // Form and Handoff State
    var formUrl by remember(currentSettings.defaultHandoffUrl) {
        mutableStateOf(currentSettings.defaultHandoffUrl)
    }
    var targetApp by remember(currentSettings.defaultHandoffApp) {
        mutableStateOf(currentSettings.defaultHandoffApp)
    }

    // Battery optimization status tracking
    var isBatteryIgnored by remember {
        mutableStateOf(KeepAliveHelper.isIgnoringBatteryOptimizations(context))
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isBatteryIgnored = KeepAliveHelper.isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Timing adjustment dialog state
    var timingItemToEdit by remember { mutableStateOf<CheckItem?>(null) }
    var editTimeStart by remember { mutableStateOf("") }
    var editTimeEnd by remember { mutableStateOf("") }

    // Import confirmation dialog state
    var pendingImportConfig by remember { mutableStateOf<DutyConfigExport?>(null) }
    var showImportConfirmDialog by remember { mutableStateOf(false) }

    // File Picker Launchers
    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.exportDutyConfigJson { jsonStr ->
                try {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        DutyConfigManager.writeConfigToStream(jsonStr, stream)
                    }
                    Toast.makeText(context, "配置已成功保存至文件", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val openDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val parsed = context.contentResolver.openInputStream(uri)?.use { stream ->
                    DutyConfigManager.parseConfigFromStream(stream)
                }
                if (parsed != null) {
                    pendingImportConfig = parsed
                    showImportConfirmDialog = true
                } else {
                    Toast.makeText(context, "无法识别该文件内容", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "导入解析失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "值班巡检与系统设置",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                },
                navigationIcon = {
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceVariant)
                            .border(1.dp, colors.borderSubtle, CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "←", color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background
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

            // ─────────────────────────────────────────────────────────────
            // ── Section 1: 值班岗位管理 (STATIONS) ──────────────────────────
            // ─────────────────────────────────────────────────────────────
            SettingsSectionHeader(
                title = "值班岗位管理 (STATIONS)",
                subtitle = "管理值班点位与巡检岗位，切换当前主页责任区域",
                colors = colors
            )
            Spacer(modifier = Modifier.height(10.dp))

            val stations = uiState.stations
            var isAddingStationInSettings by remember { mutableStateOf(false) }
            var newStationNameInSettings by remember { mutableStateOf("") }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.borderSubtle, RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                if (stations.isNotEmpty()) {
                    val stationNames = stations.map { it.name }
                    val activeStationIndex = stations.indexOfFirst { it.id == currentSettings.activeStationId }
                        .let { if (it >= 0) it else 0 }

                    Text(
                        text = "选择当前值班岗位：",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    SlidePillSegmentedControl(
                        items = stationNames,
                        selectedIndex = activeStationIndex,
                        onItemSelected = { index ->
                            val selectedStation = stations.getOrNull(index)
                            if (selectedStation != null) {
                                viewModel.switchStation(selectedStation.id)
                            }
                        },
                        containerColor = colors.surfaceVariant,
                        pillColor = colors.accentContainer,
                        activeContentColor = colors.accentContent,
                        contentColor = colors.textSecondary,
                        height = 42.dp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    stations.forEach { st ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (st.id == currentSettings.activeStationId) {
                                    Text(text = "● ", color = colors.accent, fontSize = 12.sp)
                                }
                                Text(
                                    text = st.name,
                                    color = if (st.id == currentSettings.activeStationId) colors.accent else colors.textSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = if (st.id == currentSettings.activeStationId) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                            if (stations.size > 1) {
                                Text(
                                    text = "删除",
                                    fontSize = 11.sp,
                                    color = colors.error,
                                    modifier = Modifier
                                        .clickable { viewModel.deleteStation(st.id) }
                                        .padding(4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (isAddingStationInSettings) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newStationNameInSettings,
                            onValueChange = { newStationNameInSettings = it },
                            placeholder = { Text("岗位名称...", fontSize = 12.sp, color = colors.textTertiary) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.accent,
                                unfocusedBorderColor = colors.border,
                                focusedContainerColor = colors.surfaceVariant,
                                unfocusedContainerColor = colors.surfaceVariant,
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .height(38.dp)
                                .flashButton(accentColor = colors.accent, cornerRadiusDp = 8.dp) {
                                    if (newStationNameInSettings.isNotBlank()) {
                                        viewModel.createStation(newStationNameInSettings.trim())
                                        newStationNameInSettings = ""
                                        isAddingStationInSettings = false
                                    }
                                }
                                .background(colors.accent, RoundedCornerShape(8.dp))
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
                            Text("取消", fontSize = 12.sp, color = colors.textSecondary)
                        }
                    }
                } else {
                    Text(
                        text = "+ 添加自定义岗位 / 楼层",
                        fontSize = 12.sp,
                        color = colors.accent,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clickable { isAddingStationInSettings = true }
                            .padding(vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ─────────────────────────────────────────────────────────────
            // ── Section 2: 巡查条目编排与显示 (ITEMS ORDER & TIMING) ───────
            // ─────────────────────────────────────────────────────────────
            SettingsSectionHeader(
                title = "巡查条目编排与显示 (ITEMS ORDER & TIMING)",
                subtitle = "调整巡检条目先后顺序，自由切换时段卡片与常规清单，微调巡检时段",
                colors = colors
            )
            Spacer(modifier = Modifier.height(10.dp))

            var selectedStationIdForOrder by remember(currentSettings.activeStationId) {
                mutableStateOf(currentSettings.activeStationId)
            }
            if (stations.none { it.id == selectedStationIdForOrder } && stations.isNotEmpty()) {
                selectedStationIdForOrder = stations.first().id
            }

            val stationItems by remember(selectedStationIdForOrder) {
                viewModel.getItemsForStation(selectedStationIdForOrder)
            }.collectAsStateWithLifecycle(emptyList())

            val sortedItems = remember(stationItems) {
                stationItems.sortedWith(compareBy({ it.sortOrder }, { it.id }))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.borderSubtle, RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                // Station Dropdown / Selector if > 1 station
                if (stations.size > 1) {
                    val stationNames = stations.map { it.name }
                    val currentIdx = stations.indexOfFirst { it.id == selectedStationIdForOrder }.coerceAtLeast(0)

                    Text(
                        text = "选择待编排岗位：",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    SlidePillSegmentedControl(
                        items = stationNames,
                        selectedIndex = currentIdx,
                        onItemSelected = { index ->
                            stations.getOrNull(index)?.let {
                                selectedStationIdForOrder = it.id
                            }
                        },
                        containerColor = colors.surfaceVariant,
                        pillColor = colors.accent,
                        activeContentColor = Color.White,
                        contentColor = colors.textSecondary,
                        height = 36.dp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (sortedItems.isEmpty()) {
                    Text(
                        text = "该岗位暂无巡查条目。返回主页点击“+ 自定义巡查条目”即可快速创建。",
                        fontSize = 12.sp,
                        color = colors.textTertiary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    sortedItems.forEachIndexed { index, item ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.surfaceVariant.copy(alpha = 0.5f))
                                .border(1.dp, colors.borderSubtle, RoundedCornerShape(10.dp))
                                .padding(10.dp)
                        ) {
                            // Title & Badges
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "#${index + 1} ",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.accent
                                    )
                                    Text(
                                        text = item.title,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.textPrimary,
                                        maxLines = 1
                                    )
                                }

                                // Type Badge
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (item.isTimeLinear) colors.accentContainer else colors.surfaceElevated
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (item.isTimeLinear) "⏱️ 时段" else "📋 常规",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (item.isTimeLinear) colors.accentContent else colors.textSecondary
                                    )
                                }
                            }

                            // Time Window Info
                            if (item.isTimeLinear) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "巡检时段: ${item.timeStart ?: "未设"} ~ ${item.timeEnd ?: "未设"}",
                                    fontSize = 11.sp,
                                    color = colors.textSecondary
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Action buttons row: [ ▲ 上移 ] [ ▼ 下移 ] [ 切换类型 ] [ ⏱️ 时段微调 ]
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Move Up
                                SmallActionButton(
                                    text = "▲ 上移",
                                    enabled = index > 0,
                                    colors = colors
                                ) {
                                    val prevItem = sortedItems[index - 1]
                                    viewModel.swapCheckItemSortOrder(item.id, prevItem.id)
                                }

                                // Move Down
                                SmallActionButton(
                                    text = "▼ 下移",
                                    enabled = index < sortedItems.lastIndex,
                                    colors = colors
                                ) {
                                    val nextItem = sortedItems[index + 1]
                                    viewModel.swapCheckItemSortOrder(item.id, nextItem.id)
                                }

                                // Toggle Type
                                SmallActionButton(
                                    text = if (item.isTimeLinear) "转常规" else "转时段",
                                    enabled = true,
                                    colors = colors
                                ) {
                                    viewModel.updateCheckItemTiming(
                                        itemId = item.id,
                                        isTimeLinear = !item.isTimeLinear,
                                        timeStart = item.timeStart ?: "20:00",
                                        timeEnd = item.timeEnd ?: "20:30"
                                    )
                                }

                                // Fine tune timing (only for linear items)
                                if (item.isTimeLinear) {
                                    SmallActionButton(
                                        text = "⏱️ 微调",
                                        enabled = true,
                                        colors = colors,
                                        isAccent = true
                                    ) {
                                        timingItemToEdit = item
                                        editTimeStart = item.timeStart ?: "20:00"
                                        editTimeEnd = item.timeEnd ?: "20:30"
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ─────────────────────────────────────────────────────────────
            // ── Section 3: 通知与唤醒自启动保障 (NOTIFICATIONS & KEEP-ALIVE)
            // ─────────────────────────────────────────────────────────────
            SettingsSectionHeader(
                title = "通知与唤醒自启动保障 (NOTIFICATIONS & KEEP-ALIVE)",
                subtitle = "值班高优先级准时预警、手机熄屏保活与厂商自启动管理配置",
                colors = colors
            )
            Spacer(modifier = Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.borderSubtle, RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                // 1. Notify Enabled Master Switch
                SettingSwitchRow(
                    title = "值班提醒总开关",
                    subtitle = "值班开始前在通知栏发送醒目预警提醒",
                    checked = currentSettings.notifyEnabled,
                    colors = colors,
                    onCheckedChange = {
                        viewModel.updateUserSettings(currentSettings.copy(notifyEnabled = it))
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 2. Advance reminder minutes
                Text(
                    text = "提前预警时间",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "在值班任务开始前多少分钟触发提醒",
                    fontSize = 11.sp,
                    color = colors.textTertiary
                )
                Spacer(modifier = Modifier.height(6.dp))

                val advanceOptions = listOf(5, 10, 15, 30, 60)
                val advanceLabels = listOf("5分钟", "10分钟", "15分钟", "30分钟", "60分钟")
                val currentAdvanceIdx = advanceOptions.indexOf(currentSettings.notifyAdvanceMinutes).let {
                    if (it >= 0) it else 2
                }

                SlidePillSegmentedControl(
                    items = advanceLabels,
                    selectedIndex = currentAdvanceIdx,
                    onItemSelected = { idx ->
                        val selectedMin = advanceOptions.getOrElse(idx) { 15 }
                        viewModel.updateUserSettings(currentSettings.copy(notifyAdvanceMinutes = selectedMin))
                    },
                    containerColor = colors.surfaceVariant,
                    pillColor = colors.accent,
                    activeContentColor = Color.White,
                    contentColor = colors.textSecondary,
                    height = 36.dp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 3. Sound Switch
                SettingSwitchRow(
                    title = "提醒提示音",
                    subtitle = "发出系统标准通知提示音",
                    checked = currentSettings.notifySound,
                    colors = colors,
                    onCheckedChange = {
                        viewModel.updateUserSettings(currentSettings.copy(notifySound = it))
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 4. Vibrate Switch
                SettingSwitchRow(
                    title = "唤醒触感强震动",
                    subtitle = "触发连续物理震动，防止值班漏岗",
                    checked = currentSettings.notifyVibrate,
                    colors = colors,
                    onCheckedChange = {
                        viewModel.updateUserSettings(currentSettings.copy(notifyVibrate = it))
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 5. Auto restore on boot
                SettingSwitchRow(
                    title = "开机自启自动恢复提醒",
                    subtitle = "手机重启或应用升级后自动恢复已设定的排班提醒闹钟",
                    checked = currentSettings.autoRestoreOnBoot,
                    colors = colors,
                    onCheckedChange = {
                        viewModel.updateUserSettings(currentSettings.copy(autoRestoreOnBoot = it))
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 6. Test Notification Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .flashButton(accentColor = colors.accent, cornerRadiusDp = 10.dp) {
                            viewModel.sendTestNotification(context)
                        }
                        .background(colors.accentContainer, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🔔 发送即时测试通知与震动",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.accentContent
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 7. Battery Whitelist Status & Action
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isBatteryIgnored) colors.surfaceVariant else colors.surfaceElevated)
                        .border(
                            1.dp,
                            if (isBatteryIgnored) colors.borderSubtle else colors.warning,
                            RoundedCornerShape(10.dp)
                        )
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isBatteryIgnored) "⚡ 已加入电池优化白名单" else "⚠️ 未加入电池白名单",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isBatteryIgnored) colors.accent else colors.warning
                        )
                        Text(
                            text = if (isBatteryIgnored) "锁屏状态下不受系统休眠限制，保障准时闹钟唤醒" else "息屏锁屏时可能被系统深度休眠拦截通知",
                            fontSize = 11.sp,
                            color = colors.textTertiary,
                            lineHeight = 15.sp
                        )
                    }

                    if (!isBatteryIgnored) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .flashButton(accentColor = colors.warning, cornerRadiusDp = 6.dp) {
                                    KeepAliveHelper.requestIgnoreBatteryOptimizations(context)
                                }
                                .background(colors.warning, RoundedCornerShape(6.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("申请白名单", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 8. Manufacturer Autostart Navigation Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            KeepAliveHelper.openManufacturerAutoStartSettings(context)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🚀 跳转厂商自启动与后台保护管理",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ─────────────────────────────────────────────────────────────
            // ── Section 4: 配置备份与导入导出 (BACKUP & SHARE) ──────────────
            // ─────────────────────────────────────────────────────────────
            SettingsSectionHeader(
                title = "配置备份与导入导出 (BACKUP & SHARE)",
                subtitle = "导出排班岗位与检查清单为 JSON 文件，或通过微信/TIM快速分享与跨设备迁移",
                colors = colors
            )
            Spacer(modifier = Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.borderSubtle, RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                // Button 1: Save to file
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .flashButton(accentColor = colors.accent, cornerRadiusDp = 10.dp) {
                            val defaultName = "oxherd_duty_config_${System.currentTimeMillis()}.json"
                            createDocLauncher.launch(defaultName)
                        }
                        .background(colors.surfaceVariant, RoundedCornerShape(10.dp))
                        .border(1.dp, colors.border, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "💾 保存至手机文件 (JSON)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Button 2: Share to WeChat/TIM
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .flashButton(accentColor = colors.accent, cornerRadiusDp = 10.dp) {
                            viewModel.shareDutyConfig(context)
                        }
                        .background(colors.accent, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "💬 一键分享配置 (微信 / TIM / 工作群)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Button 3: Import from file
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .border(1.dp, colors.accent, RoundedCornerShape(10.dp))
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            openDocLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "📥 从文件导入值班配置",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.accent
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ─────────────────────────────────────────────────────────────
            // ── Section 5: 外观主题与性能档位 (THEME & PERFORMANCE) ────────
            // ─────────────────────────────────────────────────────────────
            SettingsSectionHeader(
                title = "外观色彩与物理动效 (THEME & PERFORMANCE)",
                subtitle = "自适应暗黑色彩与 Fluid Motion 规范的物理微动效引擎",
                colors = colors
            )
            Spacer(modifier = Modifier.height(10.dp))

            val themeModes = listOf("跟随系统 (AUTO)", "深色模式 (DARK)", "浅色模式 (LIGHT)")
            val currentThemeIndex = when (currentSettings.themeMode) {
                "DARK" -> 1
                "LIGHT" -> 2
                else -> 0
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

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.borderSubtle, RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = "外观主题模式",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))

                SlidePillSegmentedControl(
                    items = themeModes,
                    selectedIndex = currentThemeIndex,
                    onItemSelected = { index ->
                        val newTheme = when (index) {
                            1 -> "DARK"
                            2 -> "LIGHT"
                            else -> "SYSTEM"
                        }
                        viewModel.updateUserSettings(currentSettings.copy(themeMode = newTheme))
                    },
                    containerColor = colors.surfaceVariant,
                    pillColor = colors.accent,
                    activeContentColor = Color.White,
                    contentColor = colors.textSecondary,
                    height = 38.dp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "动效渲染档位 (Performance Tier)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary
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
                    containerColor = colors.surfaceVariant,
                    pillColor = if (colors.isDark) colors.surfaceElevated else colors.surface,
                    activeContentColor = colors.accent,
                    contentColor = colors.textSecondary,
                    height = 38.dp
                )

                Spacer(modifier = Modifier.height(4.dp))
                val modeExplanation = when (currentModeIndex) {
                    1 -> "标准流畅 (LITE)：保留真实几何补间与 3D 翻转，去除昂贵毛玻璃"
                    2 -> "极速省电 (MIN)：纯 2D 位移与渐变，错峰延时清零 (0ms)"
                    else -> "完整动效 (FULL)：3D 透视角度全开、多层高斯模糊与错峰拼合"
                }
                Text(
                    text = modeExplanation,
                    fontSize = 11.sp,
                    color = colors.textTertiary,
                    lineHeight = 15.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "动效时长倍率 (Global Speed)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))

                SlidePillSegmentedControl(
                    items = speedLabels,
                    selectedIndex = currentSpeedIndex,
                    onItemSelected = { index ->
                        val newScale = speedMultipliers.getOrElse(index) { 1.0f }
                        viewModel.updateUserSettings(currentSettings.copy(motionScale = newScale))
                    },
                    containerColor = colors.surfaceVariant,
                    pillColor = colors.accent,
                    activeContentColor = Color.White,
                    contentColor = colors.textSecondary,
                    height = 36.dp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ─────────────────────────────────────────────────────────────
            // ── Section 6: 交班外链设置 (HANDOFF LINKS) ───────────────────
            // ─────────────────────────────────────────────────────────────
            SettingsSectionHeader(
                title = "交班协同与外链设置 (HANDOFF LINKS)",
                subtitle = "巡查打卡完成后提供一键跳转填表与工作群协同汇报",
                colors = colors
            )
            Spacer(modifier = Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.borderSubtle, RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = "预置巡查表单链接 (Web Form URL)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = formUrl,
                    onValueChange = { formUrl = it },
                    placeholder = { Text("如: https://docs.qq.com/form/page/...", color = colors.textTertiary, fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.border,
                        focusedContainerColor = colors.surfaceVariant,
                        unfocusedContainerColor = colors.surfaceVariant,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "目标工作汇报应用包名 (Target App Package)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = targetApp,
                    onValueChange = { targetApp = it },
                    placeholder = { Text("默认: com.tencent.tim", color = colors.textTertiary, fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.border,
                        focusedContainerColor = colors.surfaceVariant,
                        unfocusedContainerColor = colors.surfaceVariant,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .flashButton(accentColor = colors.accent, cornerRadiusDp = 10.dp) {
                            viewModel.updateUserSettings(
                                currentSettings.copy(
                                    defaultHandoffUrl = formUrl.trim(),
                                    defaultHandoffApp = targetApp.trim().ifBlank { "com.tencent.tim" }
                                )
                            )
                            Toast.makeText(context, "设置已保存", Toast.LENGTH_SHORT).show()
                            onBack()
                        }
                        .background(colors.accent, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "保存并应用外链配置",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // ─────────────────────────────────────────────────────────────
    // ── Dialog: ⏱️ 时段微调 (Timing Fine-Tuning Dialog) ───────────
    // ─────────────────────────────────────────────────────────────
    timingItemToEdit?.let { item ->
        AlertDialog(
            onDismissRequest = { timingItemToEdit = null },
            title = {
                Text(
                    text = "时段微调: ${item.title}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            },
            text = {
                Column {
                    Text(
                        text = "设置巡检时段起始与结束时间 (HH:mm)：",
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = editTimeStart,
                            onValueChange = { editTimeStart = it },
                            label = { Text("开始时间") },
                            placeholder = { Text("20:00") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = editTimeEnd,
                            onValueChange = { editTimeEnd = it },
                            label = { Text("结束时间") },
                            placeholder = { Text("20:30") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text = "快捷时段推荐：", fontSize = 11.sp, color = colors.textTertiary)
                    Spacer(modifier = Modifier.height(4.dp))

                    val presets = listOf(
                        "20:00" to "20:30",
                        "20:30" to "21:00",
                        "21:00" to "21:30",
                        "21:30" to "22:00"
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        presets.forEach { (start, end) ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(colors.surfaceVariant)
                                    .clickable {
                                        editTimeStart = start
                                        editTimeEnd = end
                                    }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Text("$start~$end", fontSize = 10.sp, color = colors.textSecondary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateCheckItemTiming(
                            itemId = item.id,
                            isTimeLinear = true,
                            timeStart = editTimeStart.trim(),
                            timeEnd = editTimeEnd.trim()
                        )
                        timingItemToEdit = null
                        Toast.makeText(context, "巡检时段已更新", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("保存时段", color = colors.accent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { timingItemToEdit = null }) {
                    Text("取消", color = colors.textSecondary)
                }
            },
            containerColor = colors.surface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // ─────────────────────────────────────────────────────────────
    // ── Dialog: 📥 确认导入配置 (Import Confirmation Dialog) ─────────
    // ─────────────────────────────────────────────────────────────
    if (showImportConfirmDialog && pendingImportConfig != null) {
        val config = pendingImportConfig!!
        AlertDialog(
            onDismissRequest = { showImportConfirmDialog = false },
            title = {
                Text(
                    text = "确认导入值班配置？",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            },
            text = {
                Column {
                    Text(
                        text = "已成功解析配置备份，包含以下条目：",
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surfaceVariant)
                            .padding(10.dp)
                    ) {
                        Text("• 任务名称: ${config.task.title}", fontSize = 12.sp, color = colors.textPrimary)
                        Text("• 排班周期: ${config.task.scheduledDate} ${config.task.scheduledTime}", fontSize = 12.sp, color = colors.textSecondary)
                        Text("• 岗位点位: ${config.stations.size} 个", fontSize = 12.sp, color = colors.textSecondary)
                        Text("• 巡查条目: ${config.items.size} 项", fontSize = 12.sp, color = colors.textSecondary)
                        Text("• 子步骤项: ${config.subItems.size} 个", fontSize = 12.sp, color = colors.textSecondary)
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "⚠️ 警告：导入配置将清空当前进行中的值班条目，并以导入配置全新初始化。确定要继续吗？",
                        fontSize = 11.sp,
                        color = colors.error,
                        lineHeight = 16.sp
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.importDutyConfig(config) { success ->
                            showImportConfirmDialog = false
                            pendingImportConfig = null
                            if (success) {
                                Toast.makeText(context, "值班配置导入成功！", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "导入失败，请检查文件格式", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                ) {
                    Text("覆盖并导入", color = colors.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showImportConfirmDialog = false
                        pendingImportConfig = null
                    }
                ) {
                    Text("取消", color = colors.textSecondary)
                }
            },
            containerColor = colors.surface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun SmallActionButton(
    text: String,
    enabled: Boolean,
    colors: OxherdColors,
    isAccent: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    !enabled -> colors.surfaceVariant.copy(alpha = 0.4f)
                    isAccent -> colors.accent
                    else -> colors.surfaceElevated
                }
            )
            .border(
                1.dp,
                if (isAccent) colors.accent else colors.borderSubtle,
                RoundedCornerShape(6.dp)
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = when {
                !enabled -> colors.textTertiary.copy(alpha = 0.5f)
                isAccent -> Color.White
                else -> colors.textSecondary
            }
        )
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    colors: OxherdColors,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = colors.textTertiary,
                lineHeight = 15.sp
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = colors.accent,
                uncheckedThumbColor = colors.textSecondary,
                uncheckedTrackColor = colors.surfaceVariant
            )
        )
    }
}

@Composable
private fun SettingsSectionHeader(
    title: String,
    subtitle: String,
    colors: OxherdColors = LocalOxherdColors.current
) {
    Column {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = colors.accent,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subtitle,
            fontSize = 12.sp,
            color = colors.textSecondary,
            lineHeight = 16.sp
        )
    }
}
