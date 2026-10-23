package com.oxherd.app.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oxherd.app.data.CheckItem
import com.oxherd.app.data.CheckItemWithSubItems
import com.oxherd.app.data.SubItem
import com.oxherd.app.data.TaskStation
import com.oxherd.app.ui.motion.FluidMotionTokens
import com.oxherd.app.ui.motion.StrikethroughText
import com.oxherd.app.ui.motion.SwipeToCompleteBox
import com.oxherd.app.ui.motion.flashButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Main duty inspection screen with time-linear & non-linear checklist orchestration,
 * gesture swipe-to-complete, expandable sub-items, station switching, and completion handoff.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    viewModel: DutyViewModel,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Sub-item expanded states map
    val expandedItems = remember { mutableStateMapOf<Long, Boolean>() }

    // Evidence editing modal state
    var selectedItemForEvidence by remember { mutableStateOf<CheckItem?>(null) }
    var isQuickStationSwitcherOpen by remember { mutableStateOf(false) }
    var isCompletedGroupExpanded by remember { mutableStateOf(false) }

    // Separate active (uncompleted) items vs completed items
    val activeLinearItems = remember(uiState.linearItems) {
        uiState.linearItems.filter { !it.item.isCompleted }
    }
    val activeNonLinearItems = remember(uiState.nonLinearItems) {
        uiState.nonLinearItems.filter { !it.item.isCompleted }
    }
    val completedItems = remember(uiState.linearItems, uiState.nonLinearItems) {
        (uiState.linearItems + uiState.nonLinearItems).filter { it.item.isCompleted }
    }

    val taskTitle = uiState.activeTask?.title ?: "周四图书馆晚班巡检"
    val stationName = uiState.currentStation?.name ?: uiState.userSettings.activeStationName

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFF0F1117)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ── Top Header ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF141823))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = taskTitle,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "计划时间: ${uiState.activeTask?.scheduledDate ?: "今天"} ${uiState.activeTask?.scheduledTime ?: "20:00"}",
                            fontSize = 11.sp,
                            color = Color(0xFF8E9AAF)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // "提前交班" Button
                        Box(
                            modifier = Modifier
                                .height(32.dp)
                                .flashButton(
                                    accentColor = Color(0xFF4CAF50),
                                    cornerRadiusDp = 8.dp
                                ) {
                                    viewModel.openCompletionDialog()
                                }
                                .background(Color(0xFF1B3B2B), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFF2E7D32), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "交班结算",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF81C784)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Settings Icon Button
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .flashButton(
                                    accentColor = Color(0xFF5B8DEF),
                                    cornerRadiusDp = 18.dp
                                ) {
                                    onNavigateToSettings()
                                }
                                .background(Color(0xFF222838)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "⚙️", fontSize = 16.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Progress Bar and Stat
                val progress = uiState.progressPercentage
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "巡检进度 ${(progress * 100).toInt()}%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFB0C4DE)
                    )
                    Text(
                        text = "${completedCount(uiState)}/${totalCount(uiState)} 项已打卡",
                        fontSize = 11.sp,
                        color = Color(0xFF8E9AAF)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = Color(0xFF4CAF50),
                    trackColor = Color(0xFF262D3D)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Active Station Chip with fast dropdown switcher
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { isQuickStationSwitcherOpen = true }
                        .background(Color(0xFF1E283C))
                        .border(1.dp, Color(0xFF384C70), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4CAF50))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "当前岗位: $stationName",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFD6E4FF)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "▾", fontSize = 12.sp, color = Color(0xFF8BB5FF))
                }
            }

            // ── Checklist Items Scrollable List ──
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp)
            ) {
                // Section 1: ⏱️ Time-Linear Section
                if (activeLinearItems.isNotEmpty()) {
                    item {
                        SectionHeader(
                            icon = "⏱️",
                            title = "时段巡查",
                            subtitle = "到达预定时段执行，优先置顶指引节奏"
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    items(activeLinearItems, key = { it.item.id }) { itemWithSubs ->
                        val isExpanded = expandedItems[itemWithSubs.item.id] ?: true
                        CheckItemCard(
                            itemWithSubs = itemWithSubs,
                            isExpanded = isExpanded,
                            onToggleExpand = {
                                expandedItems[itemWithSubs.item.id] = !isExpanded
                            },
                            onSwipeComplete = {
                                viewModel.toggleCheckItem(itemWithSubs.item.id, true)
                            },
                            onToggleSubItem = { subId, isCompleted ->
                                viewModel.toggleSubItem(subId, isCompleted)
                            },
                            onOpenEvidence = {
                                selectedItemForEvidence = itemWithSubs.item
                            }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }

                // Section 2: 📋 Non-Linear Routine Section
                if (activeNonLinearItems.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        SectionHeader(
                            icon = "📋",
                            title = "常规待办",
                            subtitle = "值班期间随时可巡查，支持右滑快速划去"
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    items(activeNonLinearItems, key = { it.item.id }) { itemWithSubs ->
                        val isExpanded = expandedItems[itemWithSubs.item.id] ?: false
                        CheckItemCard(
                            itemWithSubs = itemWithSubs,
                            isExpanded = isExpanded,
                            onToggleExpand = {
                                expandedItems[itemWithSubs.item.id] = !isExpanded
                            },
                            onSwipeComplete = {
                                viewModel.toggleCheckItem(itemWithSubs.item.id, true)
                            },
                            onToggleSubItem = { subId, isCompleted ->
                                viewModel.toggleSubItem(subId, isCompleted)
                            },
                            onOpenEvidence = {
                                selectedItemForEvidence = itemWithSubs.item
                            }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }

                // Empty state if both linear and non-linear active items are empty
                if (activeLinearItems.isEmpty() && activeNonLinearItems.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "✨", fontSize = 40.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "当前岗位待办项已全部检查完毕！",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "请点击上方「交班结算」完成今日值班汇报",
                                    fontSize = 12.sp,
                                    color = Color(0xFF8E9AAF)
                                )
                            }
                        }
                    }
                }

                // Section 3: Completed Items Group (Collapsible at bottom)
                if (completedItems.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { isCompletedGroupExpanded = !isCompletedGroupExpanded }
                                .background(Color(0xFF141720))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "✓", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "已完成检查项 (${completedItems.size})",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFA6B2C8)
                                )
                            }
                            Text(
                                text = if (isCompletedGroupExpanded) "收起 ▲" else "展开 ▼",
                                fontSize = 12.sp,
                                color = Color(0xFF5B8DEF)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    if (isCompletedGroupExpanded) {
                        items(completedItems, key = { it.item.id }) { itemWithSubs ->
                            CompletedItemRow(
                                item = itemWithSubs.item,
                                onRestore = {
                                    viewModel.toggleCheckItem(itemWithSubs.item.id, false)
                                }
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(30.dp))
                }
            }
        }
    }

    // ── Quick Station Switcher Bottom Sheet ──
    if (isQuickStationSwitcherOpen) {
        ModalBottomSheet(
            onDismissRequest = { isQuickStationSwitcherOpen = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = Color(0xFF161A24)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "快速切换巡检岗位 / 楼层",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(14.dp))

                uiState.stations.forEach { station ->
                    val isSelected = station.id == uiState.currentStation?.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                viewModel.switchStation(station.id)
                                isQuickStationSwitcherOpen = false
                            }
                            .background(if (isSelected) Color(0xFF223554) else Color(0xFF1C2230))
                            .border(1.dp, if (isSelected) Color(0xFF5B8DEF) else Color(0xFF2C3446), RoundedCornerShape(10.dp))
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = station.name,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color(0xFF8BB5FF) else Color.White
                        )
                        if (isSelected) {
                            Text(text = "当前执勤 ✓", fontSize = 12.sp, color = Color(0xFF4CAF50))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    // ── Evidence Attachment & Exception Modal ──
    val evidenceItem = selectedItemForEvidence
    if (evidenceItem != null) {
        EvidenceEntrySheet(
            item = evidenceItem,
            onDismiss = { selectedItemForEvidence = null },
            onSave = { photoUri, note, isException ->
                viewModel.recordEvidence(evidenceItem.id, photoUri, note, isException)
                selectedItemForEvidence = null
            }
        )
    }

    // ── Completion Handoff Dialog ──
    if (uiState.showCompletionDialog) {
        CompletionHandoffDialog(
            uiState = uiState,
            onDismiss = { viewModel.dismissCompletionDialog() },
            onConfirmArchive = {
                viewModel.confirmAndArchive(context) { zipPath ->
                    Toast.makeText(context, "巡检已成功归档并就绪下次值班！", Toast.LENGTH_LONG).show()
                }
            }
        )
    }
}

@Composable
private fun SectionHeader(
    icon: String,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = icon, fontSize = 16.sp)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = subtitle,
            fontSize = 11.sp,
            color = Color(0xFF718096),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Individual CheckItem Card featuring SwipeToCompleteBox, time pulse, expandable sub-items,
 * and photo/exception actions.
 */
@Composable
private fun CheckItemCard(
    itemWithSubs: CheckItemWithSubItems,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onSwipeComplete: () -> Unit,
    onToggleSubItem: (subItemId: Long, isCompleted: Boolean) -> Unit,
    onOpenEvidence: () -> Unit
) {
    val item = itemWithSubs.item
    val subItems = itemWithSubs.subItems
    val isTimeLinear = item.isTimeLinear
    val isInTimeWindow = remember(item.timeStart, item.timeEnd) {
        isCurrentTimeInWindow(item.timeStart, item.timeEnd)
    }

    SwipeToCompleteBox(
        onComplete = onSwipeComplete,
        isCompleted = item.isCompleted,
        threshold = 64.dp,
        dismissOnComplete = true
    ) { progress, isCompleted ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF181C26))
                .border(
                    1.dp,
                    if (isInTimeWindow) Color(0xFF5B8DEF).copy(alpha = 0.8f) else Color(0xFF262C3A),
                    RoundedCornerShape(12.dp)
                )
                .padding(14.dp)
        ) {
            // Header Row: Time Badge, Title, and Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Time tag with subtle glowing pulse if active
                if (isTimeLinear && !item.timeStart.isNullOrBlank()) {
                    TimeBadge(
                        timeStart = item.timeStart,
                        timeEnd = item.timeEnd ?: "",
                        isActiveWindow = isInTimeWindow
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                // Item Title with Strikethrough driven by swipe
                Box(modifier = Modifier.weight(1f)) {
                    StrikethroughText(
                        text = item.title,
                        progress = progress,
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Photo & Exception Action Button
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onOpenEvidence() }
                        .background(
                            if (item.isException) Color(0xFF4A1E1E)
                            else if (!item.photoUri.isNullOrBlank()) Color(0xFF1E3A4D)
                            else Color(0xFF222735)
                        )
                        .border(
                            1.dp,
                            if (item.isException) Color(0xFFFF5722)
                            else if (!item.photoUri.isNullOrBlank()) Color(0xFF5B8DEF)
                            else Color(0xFF333B4D),
                            RoundedCornerShape(6.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (item.isException) "⚠️" else if (!item.photoUri.isNullOrBlank()) "📸" else "📷",
                        fontSize = 14.sp
                    )
                }

                // Expand sub-items arrow button if item has sub-items
                if (subItems.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onToggleExpand() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isExpanded) "▲" else "▼",
                            fontSize = 11.sp,
                            color = Color(0xFF8E9AAF)
                        )
                    }
                }
            }

            // Note or exception badge if present
            if (!item.note.isNullOrBlank() || item.isException) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.isException) {
                        Text(
                            text = "[异常登记]",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF7043)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    if (!item.note.isNullOrBlank()) {
                        Text(
                            text = item.note,
                            fontSize = 11.sp,
                            color = Color(0xFFB0C4DE),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Expandable sub-items checklist
            AnimatedVisibility(
                visible = isExpanded && subItems.isNotEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    subItems.forEach { subItem ->
                        SubItemRow(
                            subItem = subItem,
                            onToggle = { onToggleSubItem(subItem.id, !subItem.isCompleted) }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

/**
 * Time badge displaying e.g. "20:00 - 20:30" with glowing pulse indicator if within active window.
 */
@Composable
private fun TimeBadge(
    timeStart: String,
    timeEnd: String,
    isActiveWindow: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition()
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FluidMotionTokens.EaseQuint),
            repeatMode = RepeatMode.Reverse
        )
    )

    val label = if (timeEnd.isNotBlank()) "$timeStart - $timeEnd" else timeStart

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (isActiveWindow) Color(0xFF1E3A5F).copy(alpha = glowAlpha)
                else Color(0xFF222633)
            )
            .border(
                1.dp,
                if (isActiveWindow) Color(0xFF5B8DEF).copy(alpha = glowAlpha)
                else Color(0xFF333B4D),
                RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 6.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isActiveWindow) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF64B5F6))
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = label,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (isActiveWindow) FontWeight.Bold else FontWeight.Normal,
                color = if (isActiveWindow) Color(0xFFE3F2FD) else Color(0xFF8E9AAF)
            )
        }
    }
}

/**
 * Sub-item checklist row with checkbox and title.
 */
@Composable
private fun SubItemRow(
    subItem: SubItem,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onToggle() }
            .background(Color(0xFF12151D))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = subItem.isCompleted,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = Color(0xFF4CAF50),
                uncheckedColor = Color(0xFF525D73),
                checkmarkColor = Color.White
            ),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = subItem.title,
            fontSize = 12.sp,
            color = if (subItem.isCompleted) Color(0xFF687387) else Color(0xFFC7D2E5),
            textDecoration = if (subItem.isCompleted) TextDecoration.LineThrough else TextDecoration.None
        )
    }
}

/**
 * Row for checked-off completed items.
 */
@Composable
private fun CompletedItemRow(
    item: CheckItem,
    onRestore: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF12141C))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "✓",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF4CAF50)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = item.title,
                fontSize = 13.sp,
                color = Color(0xFF6E788B),
                textDecoration = TextDecoration.LineThrough,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = "撤销",
            fontSize = 11.sp,
            color = Color(0xFF5B8DEF),
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onRestore() }
                .padding(4.dp)
        )
    }
}

/**
 * Bottom sheet modal for capturing photographic evidence and logging notes/exceptions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EvidenceEntrySheet(
    item: CheckItem,
    onDismiss: () -> Unit,
    onSave: (photoUri: String?, note: String?, isException: Boolean) -> Unit
) {
    val context = LocalContext.current
    var note by remember(item.note) { mutableStateOf(item.note ?: "") }
    var photoUri by remember(item.photoUri) { mutableStateOf(item.photoUri) }
    var isException by remember(item.isException) { mutableStateOf(item.isException) }

    // Launcher for picking photo evidence
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            photoUri = uri.toString()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF161A24)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "登记检查凭证与异常说明",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.title,
                fontSize = 13.sp,
                color = Color(0xFF8E9AAF)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Photo Evidence Picker / Thumbnail Card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        photoPickerLauncher.launch("image/*")
                    }
                    .background(Color(0xFF1E2433))
                    .border(1.dp, Color(0xFF333D54), RoundedCornerShape(10.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF2C344A)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "📷", fontSize = 22.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (!photoUri.isNullOrBlank()) "已选择凭证照片" else "点击拍摄或上传现场照片",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (!photoUri.isNullOrBlank()) "点击可重新更换照片" else "自动按岗位分类存入凭证抽屉",
                        fontSize = 11.sp,
                        color = Color(0xFF8E9AAF)
                    )
                }
                if (!photoUri.isNullOrBlank()) {
                    Text(text = "✓", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Exception Flag Switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1E2433))
                    .border(1.dp, if (isException) Color(0xFFFF5722) else Color(0xFF333D54), RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "标记为异常事项 (Flag Exception)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isException) Color(0xFFFF7043) else Color.White
                    )
                    Text(
                        text = "异常项将高亮标示并自动汇总至交班日报",
                        fontSize = 11.sp,
                        color = Color(0xFF8E9AAF)
                    )
                }
                Switch(
                    checked = isException,
                    onCheckedChange = { isException = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFFFF5722),
                        uncheckedThumbColor = Color(0xFF8E9AAF),
                        uncheckedTrackColor = Color(0xFF2C344A)
                    )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Note Text Field
            Text(
                text = "巡查观察与文字说明",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFB0C4DE)
            )
            Spacer(modifier = Modifier.height(4.dp))

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                placeholder = { Text("输入巡查现场情况或异常具体说明...", color = Color(0xFF4A5568), fontSize = 13.sp) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 90.dp),
                shape = RoundedCornerShape(10.dp),
                maxLines = 4,
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

            // Save Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .flashButton(
                        accentColor = Color(0xFF5B8DEF),
                        cornerRadiusDp = 12.dp
                    ) {
                        onSave(photoUri, note.trim().ifBlank { null }, isException)
                        Toast.makeText(context, "凭证记录已保存", Toast.LENGTH_SHORT).show()
                    }
                    .background(Color(0xFF2563EB), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "保存存证记录",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private fun completedCount(uiState: DutyUiState): Int {
    return uiState.linearItems.count { it.item.isCompleted } +
            uiState.nonLinearItems.count { it.item.isCompleted }
}

private fun totalCount(uiState: DutyUiState): Int {
    return uiState.linearItems.size + uiState.nonLinearItems.size
}

private fun isCurrentTimeInWindow(start: String?, end: String?): Boolean {
    if (start.isNullOrBlank()) return false
    val now = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    return if (!end.isNullOrBlank()) {
        now >= start && now <= end
    } else {
        now >= start
    }
}
