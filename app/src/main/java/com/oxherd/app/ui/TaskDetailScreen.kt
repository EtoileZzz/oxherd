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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oxherd.app.data.CheckItem
import com.oxherd.app.data.CheckItemWithSubItems
import com.oxherd.app.data.SubItem
import com.oxherd.app.data.TaskStation
import com.oxherd.app.theme.LocalOxherdColors
import com.oxherd.app.ui.motion.DefaultSwipeCompleteBackground
import com.oxherd.app.ui.motion.FluidMotionTokens
import com.oxherd.app.ui.motion.SlidePillSegmentedControl
import com.oxherd.app.ui.motion.StrikethroughText
import com.oxherd.app.ui.motion.SwipeToCompleteBox
import com.oxherd.app.ui.motion.flashButton
import com.oxherd.app.ui.motion.fluidListInItem
import com.oxherd.app.ui.motion.fluidTabShift
import com.oxherd.app.ui.motion.fluidTemporalShift
import com.oxherd.app.ui.motion.fluidViewInContent
import com.oxherd.app.ui.motion.fluidViewInHeader
import java.io.File
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
    val colors = LocalOxherdColors.current

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

    // Segmented Slider Switch State: 0 = ⏱️ 时段巡查, 1 = 📋 常规待办
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    // Direct Camera Launch Support
    var pendingCameraItemId by remember { mutableStateOf<Long?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            val itemId = pendingCameraItemId
            val uri = pendingCameraUri
            if (itemId != null && uri != null) {
                val uriStr = uri.toString()
                viewModel.recordEvidence(itemId, uriStr, null, false)
                Toast.makeText(context, "📸 现场存证照片已记录", Toast.LENGTH_SHORT).show()
            }
        }
        pendingCameraItemId = null
        pendingCameraUri = null
    }

    val onDirectCameraLaunch: (Long) -> Unit = { itemId ->
        try {
            val photosDir = File(context.filesDir, "photos").apply {
                if (!exists()) mkdirs()
            }
            val photoFile = File(photosDir, "evidence_${itemId}_${System.currentTimeMillis()}.jpg")
            val photoUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )
            pendingCameraItemId = itemId
            pendingCameraUri = photoUri
            cameraLauncher.launch(photoUri)
        } catch (e: Exception) {
            Toast.makeText(context, "无法启动相机: ${e.localizedMessage ?: "未知错误"}", Toast.LENGTH_SHORT).show()
        }
    }

    val taskTitle = uiState.activeTask?.title ?: "我的值班任务"
    val stationName = uiState.currentStation?.name ?: uiState.userSettings.activeStationName

    var isInitialLoad by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(650)
        isInitialLoad = false
    }
    LaunchedEffect(uiState.currentStation?.id) {
        isInitialLoad = true
        kotlinx.coroutines.delay(650)
        isInitialLoad = false
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { isCreatingNewItem = true },
                containerColor = colors.accent,
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
                    .background(colors.surface)
                    .fluidViewInHeader()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = taskTitle,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "计划时间: ${uiState.activeTask?.scheduledDate ?: "今天"} ${uiState.activeTask?.scheduledTime ?: "20:00"}",
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // "交班结算" Button
                        Box(
                            modifier = Modifier
                                .height(34.dp)
                                .flashButton(
                                    accentColor = colors.success,
                                    cornerRadiusDp = 8.dp
                                ) {
                                    viewModel.openCompletionDialog()
                                }
                                .background(colors.successContainer, RoundedCornerShape(8.dp))
                                .border(1.dp, colors.success.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "交班结算",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (colors.isDark) Color(0xFF81C784) else Color(0xFF047857)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Settings Icon Button
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .flashButton(
                                    accentColor = colors.accent,
                                    cornerRadiusDp = 18.dp
                                ) {
                                    onNavigateToSettings()
                                }
                                .background(colors.surfaceVariant)
                                .border(1.dp, colors.borderSubtle, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "⚙️", fontSize = 16.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

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
                        color = colors.textPrimary
                    )
                    Text(
                        text = "${completedCount(uiState)}/${totalCount(uiState)} 项已打卡",
                        fontSize = 11.sp,
                        color = colors.textSecondary
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = colors.success,
                    trackColor = colors.borderSubtle
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Active Station Chip with fast dropdown switcher
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { isQuickStationSwitcherOpen = true }
                        .background(colors.surfaceVariant)
                        .border(1.dp, colors.border, RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(colors.success)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "当前岗位: $stationName",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "▾", fontSize = 12.sp, color = colors.accent)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // SlidePill Segmented Switch for 时段巡查 vs 常规待办
            SlidePillSegmentedControl(
                items = listOf(
                    "⏱️ 时段巡查 (${activeLinearItems.size})",
                    "📋 常规待办 (${activeNonLinearItems.size})"
                ),
                selectedIndex = selectedTab,
                onItemSelected = { selectedTab = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                containerColor = colors.surfaceVariant,
                pillColor = colors.accent,
                contentColor = colors.textSecondary,
                activeContentColor = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── Checklist Items Scrollable List (Fluid Motion Scenes 4, 5, 6) ──
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .fluidViewInContent()
                    .fluidTemporalShift(uiState.currentStation?.id ?: 0L)
                    .fluidTabShift(selectedTab)
                    .padding(horizontal = 16.dp)
            ) {
                // Tab 0: ⏱️ Time-Linear Section
                if (selectedTab == 0) {
                    if (activeLinearItems.isNotEmpty()) {
                        item {
                            SectionHeader(
                                icon = "⏱️",
                                title = "时段巡查",
                                subtitle = "到达预定时段执行，左右滑动皆可划去完成",
                                colors = colors
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
                                onDirectCamera = {
                                    onDirectCameraLaunch(itemWithSubs.item.id)
                                },
                                onEditItem = {
                                    itemToEdit = itemWithSubs.item
                                },
                                onDeleteItem = {
                                    itemToDelete = itemWithSubs.item
                                },
                                modifier = Modifier.fluidListInItem(index, isInitialLoad)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    } else {
                        // Elegant Empty State Card for 时段巡查
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(colors.surface)
                                    .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(CircleShape)
                                            .background(colors.accentContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("⏱️", fontSize = 28.sp)
                                    }
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = "暂无时段巡查项目",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textPrimary
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "按时段计划（如 20:00-20:30）执行的巡检项将在此展示。\n到达设定时段会有高光指引，支持左右滑动快速完成。",
                                        fontSize = 12.sp,
                                        color = colors.textSecondary,
                                        lineHeight = 18.sp,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(18.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp)
                                            .flashButton(accentColor = colors.accent, cornerRadiusDp = 12.dp) {
                                                isCreatingNewItem = true
                                            }
                                            .background(colors.accent, RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("＋", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "添加时段巡查项目",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Tab 1: 📋 Non-Linear Routine Section
                if (selectedTab == 1) {
                    if (activeNonLinearItems.isNotEmpty()) {
                        item {
                            SectionHeader(
                                icon = "📋",
                                title = "常规待办",
                                subtitle = "值班期间随时可巡查，支持左右滑动快速划去",
                                colors = colors
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
                                onDirectCamera = {
                                    onDirectCameraLaunch(itemWithSubs.item.id)
                                },
                                onEditItem = {
                                    itemToEdit = itemWithSubs.item
                                },
                                onDeleteItem = {
                                    itemToDelete = itemWithSubs.item
                                },
                                modifier = Modifier.fluidListInItem(index, isInitialLoad)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    } else {
                        // Elegant Empty State Card for 常规待办
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(colors.surface)
                                    .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(CircleShape)
                                            .background(colors.accentContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("📋", fontSize = 28.sp)
                                    }
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = "暂无常规待办项目",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textPrimary
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "值班期间随时可巡查的例行条目将在此展示。\n支持左右滑动划去完成，并可随时添加细分子条目。",
                                        fontSize = 12.sp,
                                        color = colors.textSecondary,
                                        lineHeight = 18.sp,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(18.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp)
                                            .flashButton(accentColor = colors.accent, cornerRadiusDp = 12.dp) {
                                                isCreatingNewItem = true
                                            }
                                            .background(colors.accent, RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("＋", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "添加常规待办项目",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Add New Check Item Action Row (when items exist in selected tab)
                val currentTabHasItems = if (selectedTab == 0) activeLinearItems.isNotEmpty() else activeNonLinearItems.isNotEmpty()
                if (currentTabHasItems) {
                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { isCreatingNewItem = true }
                                .background(colors.surface)
                                .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "＋", color = colors.accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (selectedTab == 0) "新增时段巡查项目" else "新增常规待办项目",
                                    color = colors.accent,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                // Section 3: Completed Items Group (Collapsible at bottom)
                if (completedItems.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { isCompletedGroupExpanded = !isCompletedGroupExpanded }
                                .background(colors.surface)
                                .border(1.dp, colors.borderSubtle, RoundedCornerShape(10.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "✓", color = colors.success, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "已完成检查项 (${completedItems.size})",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.textSecondary
                                )
                            }
                            Text(
                                text = if (isCompletedGroupExpanded) "收起 ▲" else "展开 ▼",
                                fontSize = 12.sp,
                                color = colors.accent
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
                                },
                                colors = colors
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(72.dp))
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
                defaultIsTimeLinear = (selectedTab == 0),
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
            containerColor = colors.surface,
            title = { Text("确认删除该巡查项目？", color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "项目「${deleteItem.title}」及其所有子条目将被永久删除。",
                    color = colors.textSecondary,
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
                    Text("删除", color = colors.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("取消", color = colors.textSecondary)
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
    subtitle: String,
    colors: com.oxherd.app.theme.OxherdColors
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = icon, fontSize = 16.sp)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = subtitle,
            fontSize = 11.sp,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Individual CheckItem Card featuring bidirectional SwipeToCompleteBox, time pulse,
 * expandable sub-items with bidirectional swipe, and photo/exception/edit actions.
 */
@OptIn(ExperimentalFoundationApi::class)
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
    onDirectCamera: () -> Unit,
    onEditItem: () -> Unit,
    onDeleteItem: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalOxherdColors.current
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
        canSwipeWhenCompleted = true,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    ) { progress, isCompleted ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surface)
                .border(
                    1.dp,
                    if (isInTimeWindow) colors.accent else colors.border,
                    RoundedCornerShape(14.dp)
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
                        isActiveWindow = isInTimeWindow,
                        colors = colors
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
                            color = colors.textPrimary
                        )
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Photo & Exception Action Button (Direct Camera on single tap if no photo, evidence sheet on long-click or if photo exists)
                val hasPhotoOrException = !item.photoUri.isNullOrBlank() || item.isException
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                if (hasPhotoOrException) {
                                    onOpenEvidence()
                                } else {
                                    onDirectCamera()
                                }
                            },
                            onLongClick = {
                                onOpenEvidence()
                            }
                        )
                        .background(
                            if (item.isException) colors.error.copy(alpha = 0.15f)
                            else if (!item.photoUri.isNullOrBlank()) colors.accentContainer
                            else colors.surfaceVariant
                        )
                        .border(
                            1.dp,
                            if (item.isException) colors.error
                            else if (!item.photoUri.isNullOrBlank()) colors.accent
                            else colors.borderSubtle,
                            RoundedCornerShape(8.dp)
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
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { showMenu = true }
                            .background(colors.surfaceVariant)
                            .border(1.dp, colors.borderSubtle, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "⋮", fontSize = 16.sp, color = colors.textSecondary, fontWeight = FontWeight.Bold)
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(colors.surfaceElevated)
                    ) {
                        DropdownMenuItem(
                            text = { Text("✏️ 编辑巡查项", color = colors.textPrimary, fontSize = 13.sp) },
                            onClick = {
                                showMenu = false
                                onEditItem()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("🗑️ 删除巡查项", color = colors.error, fontSize = 13.sp) },
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
                        color = colors.textTertiary
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
                            color = colors.warning
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    if (!item.note.isNullOrBlank()) {
                        Text(
                            text = item.note,
                            fontSize = 11.sp,
                            color = colors.textSecondary,
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
                                onDelete = { onDeleteSubItem(subItem.id) },
                                colors = colors
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
                                placeholder = { Text("输入子条目内容...", fontSize = 12.sp, color = colors.textTertiary) },
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
                                        if (newSubItemText.isNotBlank()) {
                                            onAddSubItem(newSubItemText.trim())
                                            newSubItemText = ""
                                            isAddingSubItem = false
                                        }
                                    }
                                    .background(colors.accent, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp),
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
                                Text("取消", fontSize = 12.sp, color = colors.textSecondary)
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { isAddingSubItem = true }
                                .padding(vertical = 5.dp, horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "＋ 添加子检查条目",
                                fontSize = 12.sp,
                                color = colors.accent,
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
    isActiveWindow: Boolean,
    colors: com.oxherd.app.theme.OxherdColors
) {
    val infiniteTransition = rememberInfiniteTransition()
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
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
                if (isActiveWindow) colors.accentContainer.copy(alpha = glowAlpha)
                else colors.surfaceVariant
            )
            .border(
                1.dp,
                if (isActiveWindow) colors.accent.copy(alpha = glowAlpha)
                else colors.borderSubtle,
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
                        .background(colors.accent)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = label,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (isActiveWindow) FontWeight.Bold else FontWeight.Normal,
                color = if (isActiveWindow) colors.accentContent else colors.textSecondary
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
    onDelete: () -> Unit,
    colors: com.oxherd.app.theme.OxherdColors
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
                activeColor = colors.success,
                inactiveColor = colors.surfaceVariant,
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
                .background(colors.surfaceVariant)
                .padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = subItem.isCompleted,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = colors.success,
                    uncheckedColor = colors.border,
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
                        color = if (subItem.isCompleted) colors.textTertiary else colors.textPrimary
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
                Text(text = "✕", fontSize = 11.sp, color = colors.textTertiary)
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
    onRestore: () -> Unit,
    colors: com.oxherd.app.theme.OxherdColors
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surface)
            .border(1.dp, colors.borderSubtle, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
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
                color = colors.success
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = item.title,
                fontSize = 13.sp,
                color = colors.textTertiary,
                textDecoration = TextDecoration.LineThrough,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = "撤销",
            fontSize = 11.sp,
            color = colors.accent,
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
    val colors = LocalOxherdColors.current
    var isAddingStation by remember { mutableStateOf(false) }
    var newStationName by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = colors.surface
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
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "专人专岗：自由切换或添加自定义巡查岗位",
                fontSize = 12.sp,
                color = colors.textSecondary
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
                        .background(if (isSelected) colors.accentContainer else colors.surfaceVariant)
                        .border(
                            1.dp,
                            if (isSelected) colors.accent else colors.border,
                            RoundedCornerShape(10.dp)
                        )
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = station.name,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) colors.accentContent else colors.textPrimary
                        )
                        if (isSelected) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "✓ 当前", fontSize = 11.sp, color = colors.success, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (stations.size > 1) {
                        Text(
                            text = "删除",
                            fontSize = 12.sp,
                            color = colors.error,
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
                        placeholder = { Text("输入新岗位/楼层名称...", color = colors.textTertiary, fontSize = 13.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
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
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .height(48.dp)
                            .flashButton(accentColor = colors.accent, cornerRadiusDp = 10.dp) {
                                if (newStationName.isNotBlank()) {
                                    onCreateStation(newStationName.trim())
                                    newStationName = ""
                                    isAddingStation = false
                                }
                            }
                            .background(colors.accent, RoundedCornerShape(10.dp))
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
                        .background(colors.surfaceVariant)
                        .border(1.dp, colors.border, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("+ 添加自定义岗位 / 楼层", color = colors.accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
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
    defaultIsTimeLinear: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (title: String, isTimeLinear: Boolean, timeStart: String?, timeEnd: String?) -> Unit
) {
    val colors = LocalOxherdColors.current
    var title by remember { mutableStateOf(initialItem?.title ?: "") }
    var isTimeLinear by remember { mutableStateOf(initialItem?.isTimeLinear ?: defaultIsTimeLinear) }
    var timeStart by remember { mutableStateOf(initialItem?.timeStart ?: "20:00") }
    var timeEnd by remember { mutableStateOf(initialItem?.timeEnd ?: "20:30") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface
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
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(14.dp))

            Text("检查项目名称", fontSize = 12.sp, color = colors.textSecondary, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text("如: 检查防火卷帘门与消防栓", color = colors.textTertiary, fontSize = 13.sp) },
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

            // Time-Linear Switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceVariant)
                    .border(1.dp, colors.borderSubtle, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isTimeLinear) "⏱️ 时段巡查 (严格时间线性)" else "📋 常规待办 (自由时段)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textPrimary
                    )
                    Text(
                        text = if (isTimeLinear) "到达指定时段高亮置顶提醒" else "值班期间随时执行，支持左右滑动划去",
                        fontSize = 11.sp,
                        color = colors.textSecondary
                    )
                }
                Switch(
                    checked = isTimeLinear,
                    onCheckedChange = { isTimeLinear = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = colors.accent,
                        uncheckedThumbColor = colors.textSecondary,
                        uncheckedTrackColor = colors.border
                    )
                )
            }

            if (isTimeLinear) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("起始时间 (HH:mm)", fontSize = 11.sp, color = colors.textSecondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = timeStart,
                            onValueChange = { timeStart = it },
                            placeholder = { Text("20:00", color = colors.textTertiary, fontSize = 13.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
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
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("结束时间 (HH:mm)", fontSize = 11.sp, color = colors.textSecondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = timeEnd,
                            onValueChange = { timeEnd = it },
                            placeholder = { Text("20:30", color = colors.textTertiary, fontSize = 13.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
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
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .flashButton(accentColor = colors.accent, cornerRadiusDp = 12.dp) {
                        if (title.isNotBlank()) {
                            onSave(
                                title.trim(),
                                isTimeLinear,
                                if (isTimeLinear) timeStart.trim().ifBlank { null } else null,
                                if (isTimeLinear) timeEnd.trim().ifBlank { null } else null
                            )
                        }
                    }
                    .background(colors.accent, RoundedCornerShape(12.dp)),
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
    val colors = LocalOxherdColors.current
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
        containerColor = colors.surface
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
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.title,
                fontSize = 13.sp,
                color = colors.textSecondary
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
                    .background(colors.surfaceVariant)
                    .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surfaceElevated),
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
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (!photoUri.isNullOrBlank()) "点击可重新更换照片" else "自动按岗位分类存入凭证抽屉",
                        fontSize = 11.sp,
                        color = colors.textSecondary
                    )
                }
                if (!photoUri.isNullOrBlank()) {
                    Text(text = "✓", color = colors.success, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Exception Flag Switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceVariant)
                    .border(1.dp, if (isException) colors.warning else colors.border, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "标记为异常事项 (Flag Exception)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isException) colors.warning else colors.textPrimary
                    )
                    Text(
                        text = "异常项将高亮标示并自动汇总至交班日报",
                        fontSize = 11.sp,
                        color = colors.textSecondary
                    )
                }
                Switch(
                    checked = isException,
                    onCheckedChange = { isException = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = colors.warning,
                        uncheckedThumbColor = colors.textSecondary,
                        uncheckedTrackColor = colors.border
                    )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Note Text Field
            Text(
                text = "巡查观察与文字说明",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                placeholder = { Text("输入巡查现场情况或异常具体说明...", color = colors.textTertiary, fontSize = 13.sp) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 90.dp),
                shape = RoundedCornerShape(10.dp),
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.border,
                    focusedContainerColor = colors.surfaceVariant,
                    unfocusedContainerColor = colors.surfaceVariant,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Save Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .flashButton(
                        accentColor = colors.accent,
                        cornerRadiusDp = 12.dp
                    ) {
                        onSave(photoUri, note.trim().ifBlank { null }, isException)
                        Toast.makeText(context, "凭证记录已保存", Toast.LENGTH_SHORT).show()
                    }
                    .background(colors.accent, RoundedCornerShape(12.dp)),
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
