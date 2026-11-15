package com.quranrecite.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.quranrecite.app.di.AppContainer
import com.quranrecite.app.session.SessionSummary
import com.quranrecite.app.ui.screens.AsrDiagnosticsScreen
import com.quranrecite.app.ui.screens.ReaderScreen
import com.quranrecite.app.ui.screens.RecitationScreen
import com.quranrecite.app.ui.screens.SettingsScreen
import com.quranrecite.app.ui.screens.SummaryScreen
import com.quranrecite.app.ui.screens.WelcomeScreen
import kotlinx.coroutines.launch

object Routes {
    const val WELCOME = "welcome"
    const val READER = "reader/{page}"
    const val RECITE = "recite/{page}"
    const val SUMMARY = "summary"
    const val SETTINGS = "settings"
    const val ASR_DIAGNOSTICS = "asr-diagnostics"
    fun reader(page: Int) = "reader/$page"
    fun recite(page: Int) = "recite/$page"
}

private val pageArg = listOf(navArgument("page") { type = NavType.IntType })

@Composable
fun AppNav(container: AppContainer) {
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()

    NavHost(navController = nav, startDestination = Routes.WELCOME) {
        composable(Routes.WELCOME) {
            WelcomeScreen(onContinue = {
                nav.navigate(Routes.reader(1)) { popUpTo(Routes.WELCOME) { inclusive = true } }
            })
        }
        composable(Routes.READER, pageArg) { entry ->
            val page = entry.arguments?.getInt("page") ?: 1
            ReaderScreen(
                page = page,
                container = container,
                onStartRecitation = { nav.navigate(Routes.recite(it)) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.RECITE, pageArg) { entry ->
            val page = entry.arguments?.getInt("page") ?: 1
            RecitationScreen(
                page = page,
                container = container,
                onFinish = { summary: SessionSummary ->
                    scope.launch {
                        container.sessionStore.save(summary)
                        nav.navigate(Routes.SUMMARY) { popUpTo(Routes.RECITE) { inclusive = true } }
                    }
                },
                onBack = { nav.popBackStack() },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.SUMMARY) {
            SummaryScreen(
                container = container,
                onDone = { nav.popBackStack() },
                onRetry = { nav.navigate(Routes.recite(it)) { popUpTo(Routes.SUMMARY) { inclusive = true } } },
                onNextPage = { nav.navigate(Routes.recite(it)) { popUpTo(Routes.SUMMARY) { inclusive = true } } },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                container = container,
                onBack = { nav.popBackStack() },
                onOpenAsrDiagnostics = { nav.navigate(Routes.ASR_DIAGNOSTICS) },
            )
        }
        composable(Routes.ASR_DIAGNOSTICS) {
            AsrDiagnosticsScreen(container = container, onBack = { nav.popBackStack() })
        }
    }
}
