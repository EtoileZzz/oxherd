package com.oxherd.app

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.oxherd.app.ui.DutyViewModel
import com.oxherd.app.ui.SettingsScreen
import com.oxherd.app.ui.TaskDetailScreen
import com.oxherd.app.ui.motion.LocalMotionScale
import com.oxherd.app.ui.motion.LocalPerfLevel
import com.oxherd.app.ui.motion.PerfLevel

@Composable
fun MainNavigation() {
    val context = LocalContext.current
    val backStack = rememberNavBackStack(TaskDetail)

    val dutyViewModel: DutyViewModel = viewModel(
        factory = DutyViewModel.factory(context.applicationContext as Application)
    )

    val uiState by dutyViewModel.uiState.collectAsStateWithLifecycle()
    val activePerfLevel = PerfLevel.fromString(uiState.userSettings.perfLevel)
    val activeMotionScale = uiState.userSettings.motionScale

    CompositionLocalProvider(
        LocalPerfLevel provides activePerfLevel,
        LocalMotionScale provides activeMotionScale
    ) {
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            modifier = Modifier.fillMaxSize(),
            entryProvider = entryProvider {
                entry<TaskDetail> {
                    TaskDetailScreen(
                        viewModel = dutyViewModel,
                        onNavigateToSettings = { backStack.add(Settings) }
                    )
                }
                entry<Main> {
                    TaskDetailScreen(
                        viewModel = dutyViewModel,
                        onNavigateToSettings = { backStack.add(Settings) }
                    )
                }
                entry<Settings> {
                    SettingsScreen(
                        viewModel = dutyViewModel,
                        onBack = { backStack.removeLastOrNull() }
                    )
                }
            }
        )
    }
}
