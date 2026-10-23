package com.oxherd.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.oxherd.app.theme.LocalOxherdColors
import com.oxherd.app.ui.motion.FluidMotionTokens
import com.oxherd.app.ui.motion.LocalMotionScale
import com.oxherd.app.ui.motion.LocalPerfLevel
import com.oxherd.app.ui.motion.flashButton
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Completion handoff modal dialog displayed when duty checklist is completed
 * or when the user triggers early handoff.
 */
@Composable
fun CompletionHandoffDialog(
    uiState: DutyUiState,
    onDismiss: () -> Unit,
    onConfirmArchive: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalOxherdColors.current
    val perfLevel = LocalPerfLevel.current
    val motionScale = LocalMotionScale.current

    val animScale = remember { Animatable(if (perfLevel.allows3D) 0.92f else 0.96f) }
    val animAlpha = remember { Animatable(0f) }
    var isArchiving by remember { mutableStateOf(false) }
    var isDrawerExpanded by remember { mutableStateOf(false) }

    val resolvedDur = remember(perfLevel, motionScale) {
        perfLevel.degradeDuration(FluidMotionTokens.DUR_DIALOG_IN, motionScale)
    }

    LaunchedEffect(Unit) {
        if (resolvedDur > 0) {
            animAlpha.animateTo(1f, tween(resolvedDur, easing = FluidMotionTokens.EaseQuint))
        } else {
            animAlpha.snapTo(1f)
        }
    }
    LaunchedEffect(Unit) {
        if (resolvedDur > 0) {
            animScale.animateTo(1f, tween(resolvedDur, easing = FluidMotionTokens.EaseBack))
        } else {
            animScale.snapTo(1f)
        }
    }

    // Collect all items across linear and non-linear
    val allItems = remember(uiState.linearItems, uiState.nonLinearItems) {
        uiState.linearItems + uiState.nonLinearItems
    }
    val totalCount = allItems.size
    val completedCount = allItems.count { it.item.isCompleted }
    val exceptionItems = remember(allItems) { allItems.filter { it.item.isException } }
    val evidenceItems = remember(allItems) { allItems.filter { !it.item.photoUri.isNullOrBlank() } }

    val taskTitle = uiState.activeTask?.title ?: "值班巡检任务"
    val stationName = uiState.currentStation?.name ?: "当前岗位"
    val scheduledDate = uiState.activeTask?.scheduledDate ?: ""
    val scheduledTime = uiState.activeTask?.scheduledTime ?: ""
    val targetApp = uiState.activeTask?.handoffTargetApp?.ifBlank { null }
        ?: uiState.userSettings.defaultHandoffApp.ifBlank { "com.tencent.tim" }
    val handoffUrl = uiState.activeTask?.handoffUrl?.ifBlank { null }
        ?: uiState.userSettings.defaultHandoffUrl

    // Build shift report summary text
    val reportSummary = remember(taskTitle, stationName, completedCount, totalCount, exceptionItems, evidenceItems) {
        val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        buildString {
            appendLine("📋【值班巡检日报】")
            appendLine("任务：$taskTitle")
            appendLine("岗位：$stationName")
            if (scheduledDate.isNotBlank()) appendLine("计划排期：$scheduledDate $scheduledTime")
            appendLine("交班时间：$nowStr")
            appendLine("打卡进度：$completedCount / $totalCount 项已完成")
            if (exceptionItems.isNotEmpty()) {
                appendLine("⚠️ 异常事项 (${exceptionItems.size}项)：")
                exceptionItems.forEach { item ->
                    appendLine("  • ${item.item.title}${if (!item.item.note.isNullOrBlank()) " (${item.item.note})" else ""}")
                }
            } else {
                appendLine("状态：✅ 全区巡查正常，无异常遗留")
            }
            if (evidenceItems.isNotEmpty()) {
                appendLine("凭证：已拍摄存证 ${evidenceItems.size} 项现场照片")
            }
        }
    }

    Dialog(
        onDismissRequest = {
            if (!isArchiving) onDismiss()
        },
        properties = DialogProperties(
            dismissOnBackPress = !isArchiving,
            dismissOnClickOutside = !isArchiving,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .scale(animScale.value)
                .background(Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.dp, colors.border, RoundedCornerShape(24.dp)),
                color = colors.surface,
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header celebration badge
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(colors.successContainer)
                            .border(2.dp, colors.success.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🎉", fontSize = 32.sp)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = if (completedCount >= totalCount && totalCount > 0) "值班巡检全部完成" else "提前交班结算",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "$stationName · $taskTitle",
                        fontSize = 13.sp,
                        color = colors.textSecondary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Stats row cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatCard(
                            title = "完成率",
                            value = "${(uiState.progressPercentage * 100).toInt()}%",
                            accentColor = colors.success,
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            title = "打卡条目",
                            value = "$completedCount/$totalCount",
                            accentColor = colors.accent,
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            title = "异常记录",
                            value = "${exceptionItems.size}",
                            accentColor = if (exceptionItems.isNotEmpty()) colors.warning else colors.textTertiary,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Report summary preview box
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surfaceVariant)
                            .border(1.dp, colors.borderSubtle, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "值班日报摘要预览",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "已自动生成",
                                fontSize = 11.sp,
                                color = colors.success
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = reportSummary,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            fontFamily = FontFamily.Monospace,
                            color = colors.textSecondary,
                            maxLines = 8,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 巡查凭证抽屉 (Asset Drawer)
                    if (evidenceItems.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.surfaceVariant)
                                .border(1.dp, colors.borderSubtle, RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { isDrawerExpanded = !isDrawerExpanded },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = "📸", fontSize = 16.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "巡查凭证抽屉 (${evidenceItems.size}张)",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = colors.textPrimary
                                    )
                                }
                                Text(
                                    text = if (isDrawerExpanded) "收起 ▲" else "展开 ▼",
                                    fontSize = 12.sp,
                                    color = colors.accent
                                )
                            }

                            AnimatedVisibility(
                                visible = isDrawerExpanded,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Column(modifier = Modifier.padding(top = 10.dp)) {
                                    LazyRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(evidenceItems) { itemWithSubs ->
                                            EvidenceThumbnailCard(
                                                title = itemWithSubs.item.title,
                                                photoUri = itemWithSubs.item.photoUri,
                                                isException = itemWithSubs.item.isException
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Button to share all evidence to TIM
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(36.dp)
                                            .flashButton(
                                                accentColor = colors.accent,
                                                cornerRadiusDp = 8.dp
                                            ) {
                                                shareEvidenceToTim(context, evidenceItems.mapNotNull { it.item.photoUri }, targetApp)
                                            }
                                            .background(colors.accentContainer, RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "一键分享凭证照片至 TIM",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = colors.accentContent
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    // Action buttons
                    // 1. Copy Report Summary
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .flashButton(
                                accentColor = colors.accent,
                                cornerRadiusDp = 10.dp
                            ) {
                                copyToClipboard(context, "巡检日报摘要", reportSummary)
                            }
                            .background(colors.surfaceVariant, RoundedCornerShape(10.dp))
                            .border(1.dp, colors.border, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "📋 复制值班日报摘要",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 2. Open Form / Open TIM
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .flashButton(
                                accentColor = colors.accent,
                                cornerRadiusDp = 10.dp
                            ) {
                                openFormOrApp(context, handoffUrl, targetApp)
                            }
                            .background(colors.accentContainer, RoundedCornerShape(10.dp))
                            .border(1.dp, colors.accent.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (!handoffUrl.isNullOrBlank()) "🔗 打开巡检表单 / TIM" else "🚀 打开腾讯 TIM",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.accentContent
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 3. Confirm & Archive Button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .flashButton(
                                accentColor = colors.success,
                                cornerRadiusDp = 12.dp,
                                enabled = !isArchiving
                            ) {
                                isArchiving = true
                                onConfirmArchive()
                            }
                            .background(colors.success, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isArchiving) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "打包归档并就绪下次值班...",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                            }
                        } else {
                            Text(
                                text = "✅ 确认交班并归档",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Cancel / Close
                    Text(
                        text = "暂不交班，继续巡查",
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                enabled = !isArchiving
                            ) { onDismiss() }
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val colors = LocalOxherdColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceVariant)
            .border(1.dp, colors.borderSubtle, RoundedCornerShape(10.dp))
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = accentColor
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = title,
            fontSize = 11.sp,
            color = colors.textSecondary
        )
    }
}

@Composable
private fun EvidenceThumbnailCard(
    title: String,
    photoUri: String?,
    isException: Boolean
) {
    val colors = LocalOxherdColors.current
    Column(
        modifier = Modifier
            .width(90.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surfaceVariant)
            .border(1.dp, if (isException) colors.warning else colors.borderSubtle, RoundedCornerShape(8.dp))
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(colors.surfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "📷", fontSize = 24.sp)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = title,
            fontSize = 10.sp,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard?.setPrimaryClip(clip)
    Toast.makeText(context, "已复制值班日报摘要至剪贴板", Toast.LENGTH_SHORT).show()
}

private fun openFormOrApp(context: Context, formUrl: String?, targetPackage: String) {
    if (!formUrl.isNullOrBlank()) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(formUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Attempt to open target app (e.g. TIM)
    val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPackage)
    if (launchIntent != null) {
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
    } else {
        Toast.makeText(context, "未找到目标应用 ($targetPackage)，请先安装或配置表单链接", Toast.LENGTH_SHORT).show()
    }
}

private fun shareEvidenceToTim(context: Context, photoUris: List<String>, targetPackage: String) {
    if (photoUris.isEmpty()) {
        Toast.makeText(context, "无巡查凭证照片", Toast.LENGTH_SHORT).show()
        return
    }

    try {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "本次巡查共存证 ${photoUris.size} 张现场照片凭证")
            if (targetPackage.isNotBlank()) {
                setPackage(targetPackage)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(shareIntent)
    } catch (e: Exception) {
        // Fallback to general share
        val fallbackIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "本次巡查共存证 ${photoUris.size} 张现场照片凭证")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(fallbackIntent, "分享凭证至..."))
    }
}
