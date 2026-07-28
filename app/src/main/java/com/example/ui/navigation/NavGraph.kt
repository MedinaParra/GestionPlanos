package com.example.ui.navigation

import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.ui.screens.*
import com.example.ui.viewmodel.MainViewModel

object Routes {
    const val HOME = "home"
    const val DETAIL = "detail/{otId}"
    const val ADMIN = "admin"
    const val HTML_FORM = "html_form"

    fun buildDetailRoute(otId: String) = "detail/$otId"
}

@Composable
fun AppNavGraph(
    navController: NavHostController,
    viewModel: MainViewModel
) {
    var showCreateOtDialog by remember { mutableStateOf(false) }

    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                viewModel = viewModel,
                onNavigateToDetail = { otId ->
                    viewModel.selectWorkOrder(otId)
                    navController.navigate(Routes.buildDetailRoute(otId))
                },
                onNavigateToAdmin = {
                    navController.navigate(Routes.ADMIN)
                },
                onNavigateToHtmlForm = {
                    navController.navigate(Routes.HTML_FORM)
                },
                onOpenCreateOtDialog = {
                    showCreateOtDialog = true
                }
            )
        }

        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("otId") { type = NavType.StringType })
        ) { backStackEntry ->
            val otId = backStackEntry.arguments?.getString("otId") ?: ""
            LaunchedEffect(otId) {
                if (otId.isNotBlank()) {
                    viewModel.selectWorkOrder(otId)
                }
            }

            WorkOrderDetailScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.ADMIN) {
            AdminPanelScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToHtmlForm = { navController.navigate(Routes.HTML_FORM) }
            )
        }

        composable(Routes.HTML_FORM) {
            HtmlFormViewerScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }

    if (showCreateOtDialog) {
        CreateOtDialog(
            viewModel = viewModel,
            onDismiss = { showCreateOtDialog = false }
        )
    }
}
