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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.oxherd.app.ui.motion.DefaultSwipeCompleteBackground
import com.oxherd.app.ui.motion.FluidMotionTokens
import com.oxherd.app.ui.motion.StrikethroughText
import com.oxherd.app.ui.motion.SwipeToCompleteBox
import com.oxherd.app.ui.motion.flashButton
import com.oxherd.app.ui.motion.fluidListInItem
import com.oxherd.app.ui.motion.fluidTemporalShift
import com.oxherd.app.ui.motion.fluidViewInContent
import com.oxherd.app.ui.motion.fluidViewInHeader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Main duty inspection screen with time-linear & non-linear checklist orchestration,
 * bidirectional gesture swipe-to-complete, expandable sub-items, full CRUD customization,
 * station switching, and completion handoff aligned with Fluid Motion design specs.
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

    // Sub-item expanded states map
    val expandedItems = remember { mutableStateMapOf<Long, Boolean>() }

    // Evidence editing modal state
    var selectedItemForEvidence by remember { mutableStateOf<CheckItem?>(null) }
    var isQuickStationSwitcherOpen by remember { mutableStateOf(false) }
    var isCompletedGroupExpanded by remember { mutableStateOf(false) }

    // Dynamic customization states: CheckItem Create & Edit
    var isCreatingNewItem by remember { mutableStateOf(false) }
    var itemToEdit by remember { mutableStateOf<CheckItem?>(null) }
    var itemToDelete by remember { mutableStateOf<CheckItem?>(null) }

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
        containerColor = Color(0xFF0F1117),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { isCreatingNewItem = true },
                containerColor = Color(0xFF2563EB),
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Text(text = "+", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ── Top Header (Fluid Motion Scene 4: viewIn) ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF141823))
                    .fluidViewInHeader()
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
                        // "交班结算" Button
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

            // ── Checklist Items Scrollable List (Fluid Motion Scenes 4, 5, 6) ──
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .fluidViewInContent()
                    .fluidTemporalShift(uiState.currentStation?.id ?: 0L)
                    .padding(horizontal = 14.dp)
            ) {
                // Section 1: ⏱️ Time-Linear Section
                if (activeLinearItems.isNotEmpty()) {
                    item {
                        SectionHeader(
                            icon = "⏱️",
                            title = "时段巡查",
                            subtitle = "到达预定时段执行，左右滑动皆可划去完成"
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    itemsIndexed(activeLinearItems, key = { _, itemWithSubs -> itemWithSubs.item.id }) { index, itemWithSubs ->
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
                            onAddSubItem = { subTitle ->
                                viewModel.createSubItem(itemWithSubs.item.id, subTitle)
                            },
                            onDeleteSubItem = { subId ->
                                viewModel.deleteSubItem(subId)
                            },
                            onOpenEvidence = {
                                selectedItemForEvidence = itemWithSubs.item
                            },
                            onEditItem = {
                                itemToEdit = itemWithSubs.item
                            },
                            onDeleteItem = {
                                itemToDelete = itemWithSubs.item
                            },
                            modifier = Modifier.fluidListInItem(index)
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
                            subtitle = "值班期间随时可巡查，支持左右滑动快速划去"
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    itemsIndexed(activeNonLinearItems, key = { _, itemWithSubs -> itemWithSubs.item.id }) { index, itemWithSubs ->
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
                            onAddSubItem = { subTitle ->
                                viewModel.createSubItem(itemWithSubs.item.id, subTitle)
                            },
                            onDeleteSubItem = { subId ->
                                viewModel.deleteSubItem(subId)
                            },
                            onOpenEvidence = {
                                selectedItemForEvidence = itemWithSubs.item
                            },
                            onEditItem = {
                                itemToEdit = itemWithSubs.item
                            },
                            onDeleteItem = {
                                itemToDelete = itemWithSubs.item
                            },
                            modifier = Modifier.fluidListInItem(index)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }

                // Add New Check Item Action Row
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { isCreatingNewItem = true }
                            .background(Color(0xFF161A24))
                            .border(1.dp, Color(0xFF283244), RoundedCornerShape(10.dp))
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "＋", color = Color(0xFF5B8DEF), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "新增巡查项目 (时段巡查 / 常规待办)",
                                color = Color(0xFF8BB5FF),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Empty state if both linear and non-linear active items are empty
                if (activeLinearItems.isEmpty() && activeNonLinearItems.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 36.dp),
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
                                    text = "可点击上方「交班结算」完成今日值班汇报，或点击添加新项目",
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
                        Spacer(modifier = Modifier.height(8.dp))
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
                        itemsIndexed(completedItems, key = { _, itemWithSubs -> itemWithSubs.item.id }) { _, itemWithSubs ->
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
                    Spacer(modifier = Modifier.height(64.dp))
                }
            }
        }
    }

    // ── Quick Station Switcher & Manager Bottom Sheet ──
    if (isQuickStationSwitcherOpen) {
        StationManagerBottomSheet(
            currentStation = uiState.currentStation,
            stations = uiState.stations,
            onDismiss = { isQuickStationSwitcherOpen = false },
            onSelectStation = { stationId ->
                viewModel.switchStation(stationId)
                isQuickStationSwitcherOpen = false
            },
            onCreateStation = { stationName ->
                viewModel.createStation(stationName)
            },
            onDeleteStation = { stationId ->
                viewModel.deleteStation(stationId)
            }
        )
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

    // ── CheckItem Creator / Editor Modal ──
    if (isCreatingNewItem) {
        val currentStationId = uiState.currentStation?.id
        if (currentStationId != null) {
            CheckItemEditorModal(
                initialItem = null,
                onDismiss = { isCreatingNewItem = false },
                onSave = { title, isTimeLinear, timeStart, timeEnd ->
                    viewModel.createCheckItem(
                        stationId = currentStationId,
                        title = title,
                        isTimeLinear = isTimeLinear,
                        timeStart = timeStart,
                        timeEnd = timeEnd
                    )
                    isCreatingNewItem = false
                }
            )
        }
    }

    val editItem = itemToEdit
    if (editItem != null) {
        CheckItemEditorModal(
            initialItem = editItem,
            onDismiss = { itemToEdit = null },
            onSave = { title, isTimeLinear, timeStart, timeEnd ->
                viewModel.updateCheckItem(
                    itemId = editItem.id,
                    title = title,
                    isTimeLinear = isTimeLinear,
                    timeStart = timeStart,
                    timeEnd = timeEnd
                )
                itemToEdit = null
            }
        )
    }

    // ── Confirm Delete CheckItem Dialog ──
    val deleteItem = itemToDelete
    if (deleteItem != null) {
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            containerColor = Color(0xFF1E2330),
            title = { Text("确认删除该巡查项目？", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "项目「${deleteItem.title}」及其所有子条目将被永久删除。",
                    color = Color(0xFFB0C4DE),
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteCheckItem(deleteItem.id)
                        itemToDelete = null
                    }
                ) {
                    Text("删除", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("取消", color = Color(0xFF8E9AAF))
                }
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
 * Individual CheckItem Card featuring bidirectional SwipeToCompleteBox, time pulse,
 * expandable sub-items with bidirectional swipe, and photo/exception/edit actions.
 */
@Composable
private fun CheckItemCard(
    itemWithSubs: CheckItemWithSubItems,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onSwipeComplete: () -> Unit,
    onToggleSubItem: (subItemId: Long, isCompleted: Boolean) -> Unit,
    onAddSubItem: (title: String) -> Unit,
    onDeleteSubItem: (subItemId: Long) -> Unit,
    onOpenEvidence: () -> Unit,
    onEditItem: () -> Unit,
    onDeleteItem: () -> Unit,
    modifier: Modifier = Modifier
) {
    val item = itemWithSubs.item
    val subItems = itemWithSubs.subItems
    val isTimeLinear = item.isTimeLinear
    val isInTimeWindow = remember(item.timeStart, item.timeEnd) {
        isCurrentTimeInWindow(item.timeStart, item.timeEnd)
    }

    var showMenu by remember { mutableStateOf(false) }
    var isAddingSubItem by remember { mutableStateOf(false) }
    var newSubItemText by remember { mutableStateOf("") }

    SwipeToCompleteBox(
        onComplete = onSwipeComplete,
        isCompleted = item.isCompleted,
        threshold = 64.dp,
        dismissOnComplete = true,
        modifier = modifier
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
            // Header Row: Time Badge, Title, Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Time tag with glowing pulse if active
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

                Spacer(modifier = Modifier.width(6.dp))

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

                Spacer(modifier = Modifier.width(6.dp))

                // Options Menu Button (Edit / Delete)
                Box {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { showMenu = true }
                            .background(Color(0xFF222735))
                            .border(1.dp, Color(0xFF333B4D), RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "⋮", fontSize = 16.sp, color = Color(0xFFB0C4DE), fontWeight = FontWeight.Bold)
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(Color(0xFF1E2433))
                    ) {
                        DropdownMenuItem(
                            text = { Text("✏️ 编辑巡查项", color = Color.White, fontSize = 13.sp) },
                            onClick = {
                                showMenu = false
                                onEditItem()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("🗑️ 删除巡查项", color = Color(0xFFFF5252), fontSize = 13.sp) },
                            onClick = {
                                showMenu = false
                                onDeleteItem()
                            }
                        )
                    }
                }

                // Expand arrow button
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

            // Expandable sub-items checklist with Fluid Motion transition
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(tween(340, easing = FluidMotionTokens.EaseQuint)) + fadeIn(tween(220, easing = FluidMotionTokens.EaseQuint)),
                exit = shrinkVertically(tween(260, easing = FluidMotionTokens.EaseSoft)) + fadeOut(tween(180, easing = FluidMotionTokens.EaseFadeOut))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    if (subItems.isNotEmpty()) {
                        subItems.forEach { subItem ->
                            SubItemRow(
                                subItem = subItem,
                                onToggle = { onToggleSubItem(subItem.id, !subItem.isCompleted) },
                                onDelete = { onDeleteSubItem(subItem.id) }
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }

                    // Inline SubItem Creator
                    if (isAddingSubItem) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = newSubItemText,
                                onValueChange = { newSubItemText = it },
                                placeholder = { Text("输入子条目内容...", fontSize = 12.sp, color = Color(0xFF5A667A)) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF5B8DEF),
                                    unfocusedBorderColor = Color(0xFF2C3446),
                                    focusedContainerColor = Color(0xFF12151D),
                                    unfocusedContainerColor = Color(0xFF12151D),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .height(38.dp)
                                    .flashButton(accentColor = Color(0xFF5B8DEF), cornerRadiusDp = 8.dp) {
                                        if (newSubItemText.isNotBlank()) {
                                            onAddSubItem(newSubItemText.trim())
                                            newSubItemText = ""
                                            isAddingSubItem = false
                                        }
                                    }
                                    .background(Color(0xFF2E4B7A), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("添加", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .height(38.dp)
                                    .clickable { isAddingSubItem = false }
                                    .padding(horizontal = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("取消", fontSize = 12.sp, color = Color(0xFF8E9AAF))
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { isAddingSubItem = true }
                                .padding(vertical = 4.dp, horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "＋ 添加子检查条目",
                                fontSize = 11.sp,
                                color = Color(0xFF5B8DEF),
                                fontWeight = FontWeight.Medium
                            )
                        }
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
 * Sub-item checklist row with BIDIRECTIONAL SWIPE-TO-COMPLETE (左右滑划去):
 * Swiping either left or right past 44dp threshold triggers completion with haptic click,
 * live animated strikethrough, and smooth spring feedback.
 */
@Composable
private fun SubItemRow(
    subItem: SubItem,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    SwipeToCompleteBox(
        onComplete = onToggle,
        isCompleted = subItem.isCompleted,
        threshold = 44.dp,
        dismissOnComplete = false,
        canSwipeWhenCompleted = true,
        shape = RoundedCornerShape(8.dp),
        background = { progress, reached, isLeft ->
            DefaultSwipeCompleteBackground(
                progress = progress,
                thresholdReached = reached,
                isSwipingLeft = isLeft,
                shape = RoundedCornerShape(8.dp),
                iconSize = 22.dp,
                fontSize = 13.sp,
                horizontalPadding = 12.dp
            )
        }
    ) { progress, isCompleted ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onToggle() }
                .background(Color(0xFF12151D))
                .padding(horizontal = 8.dp, vertical = 7.dp),
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
            Box(modifier = Modifier.weight(1f)) {
                StrikethroughText(
                    text = subItem.title,
                    progress = if (subItem.isCompleted) 1f else progress,
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 12.sp,
                        color = if (subItem.isCompleted) Color(0xFF687387) else Color(0xFFC7D2E5)
                    )
                )
            }
            // Delete sub-item button
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onDelete() },
                contentAlignment = Alignment.Center
            ) {
                Text(text = "✕", fontSize = 11.sp, color = Color(0xFF5A667A))
            }
        }
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
 * Bottom sheet modal for managing stations / adding new stations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StationManagerBottomSheet(
    currentStation: TaskStation?,
    stations: List<TaskStation>,
    onDismiss: () -> Unit,
    onSelectStation: (stationId: Long) -> Unit,
    onCreateStation: (name: String) -> Unit,
    onDeleteStation: (stationId: Long) -> Unit
) {
    var isAddingStation by remember { mutableStateOf(false) }
    var newStationName by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = Color(0xFF161A24)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "巡检岗位与楼层管理",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "专人专岗：自由切换或添加自定义巡查岗位",
                fontSize = 12.sp,
                color = Color(0xFF8E9AAF)
            )
            Spacer(modifier = Modifier.height(14.dp))

            stations.forEach { station ->
                val isSelected = station.id == currentStation?.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelectStation(station.id) }
                        .background(if (isSelected) Color(0xFF223554) else Color(0xFF1C2230))
                        .border(1.dp, if (isSelected) Color(0xFF5B8DEF) else Color(0xFF2C3446), RoundedCornerShape(10.dp))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = station.name,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color(0xFF8BB5FF) else Color.White
                        )
                        if (isSelected) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "✓ 当前", fontSize = 11.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                        }
                    }

                    if (stations.size > 1) {
                        Text(
                            text = "删除",
                            fontSize = 12.sp,
                            color = Color(0xFFFF5252),
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onDeleteStation(station.id) }
                                .padding(4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (isAddingStation) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newStationName,
                        onValueChange = { newStationName = it },
                        placeholder = { Text("输入新岗位/楼层名称...", color = Color(0xFF5A667A), fontSize = 13.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
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
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .height(48.dp)
                            .flashButton(accentColor = Color(0xFF5B8DEF), cornerRadiusDp = 10.dp) {
                                if (newStationName.isNotBlank()) {
                                    onCreateStation(newStationName.trim())
                                    newStationName = ""
                                    isAddingStation = false
                                }
                            }
                            .background(Color(0xFF2563EB), RoundedCornerShape(10.dp))
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("创建", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { isAddingStation = true }
                        .background(Color(0xFF1E283C))
                        .border(1.dp, Color(0xFF384C70), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("+ 添加自定义岗位 / 楼层", color = Color(0xFF8BB5FF), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * Dialog / Sheet for Creating and Editing a CheckItem (Title, Time-Linear toggle, Start/End times).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CheckItemEditorModal(
    initialItem: CheckItem?,
    onDismiss: () -> Unit,
    onSave: (title: String, isTimeLinear: Boolean, timeStart: String?, timeEnd: String?) -> Unit
) {
    var title by remember { mutableStateOf(initialItem?.title ?: "") }
    var isTimeLinear by remember { mutableStateOf(initialItem?.isTimeLinear ?: false) }
    var timeStart by remember { mutableStateOf(initialItem?.timeStart ?: "20:00") }
    var timeEnd by remember { mutableStateOf(initialItem?.timeEnd ?: "20:30") }

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
                text = if (initialItem != null) "编辑巡查项目" else "新增巡查项目",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(14.dp))

            Text("检查项目名称", fontSize = 12.sp, color = Color(0xFFB0C4DE), fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text("如: 检查防火卷帘门与消防栓", color = Color(0xFF5A667A), fontSize = 13.sp) },
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

            // Time-Linear Switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1E2433))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isTimeLinear) "⏱️ 时段巡查 (严格时间线性)" else "📋 常规待办 (自由时段)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                    Text(
                        text = if (isTimeLinear) "到达指定时段高亮置顶提醒" else "值班期间随时执行，支持左右滑动划去",
                        fontSize = 11.sp,
                        color = Color(0xFF8E9AAF)
                    )
                }
                Switch(
                    checked = isTimeLinear,
                    onCheckedChange = { isTimeLinear = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF2563EB),
                        uncheckedThumbColor = Color(0xFF8E9AAF),
                        uncheckedTrackColor = Color(0xFF2C344A)
                    )
                )
            }

            if (isTimeLinear) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("起始时间 (HH:mm)", fontSize = 11.sp, color = Color(0xFFB0C4DE))
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = timeStart,
                            onValueChange = { timeStart = it },
                            placeholder = { Text("20:00", color = Color(0xFF5A667A), fontSize = 13.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF5B8DEF),
                                unfocusedBorderColor = Color(0xFF2D3748),
                                focusedContainerColor = Color(0xFF151922),
                                unfocusedContainerColor = Color(0xFF151922),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("结束时间 (HH:mm)", fontSize = 11.sp, color = Color(0xFFB0C4DE))
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = timeEnd,
                            onValueChange = { timeEnd = it },
                            placeholder = { Text("20:30", color = Color(0xFF5A667A), fontSize = 13.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF5B8DEF),
                                unfocusedBorderColor = Color(0xFF2D3748),
                                focusedContainerColor = Color(0xFF151922),
                                unfocusedContainerColor = Color(0xFF151922),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .flashButton(accentColor = Color(0xFF5B8DEF), cornerRadiusDp = 12.dp) {
                        if (title.isNotBlank()) {
                            onSave(
                                title.trim(),
                                isTimeLinear,
                                if (isTimeLinear) timeStart.trim().ifBlank { null } else null,
                                if (isTimeLinear) timeEnd.trim().ifBlank { null } else null
                            )
                        }
                    }
                    .background(Color(0xFF2563EB), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (initialItem != null) "保存修改" else "创建检查项",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
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
