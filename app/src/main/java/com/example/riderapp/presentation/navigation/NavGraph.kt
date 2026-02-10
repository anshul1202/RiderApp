package com.example.riderapp.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.riderapp.presentation.screen.taskdetail.TaskDetailScreen
import com.example.riderapp.presentation.screen.tasklist.TaskListScreen

@Composable
fun RiderNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = "taskList"
    ) {
        composable("taskList") {
            TaskListScreen(
                onTaskClick = { taskId ->
                    navController.navigate("taskDetail/$taskId")
                }
            )
        }
        composable(
            route = "taskDetail/{taskId}",
            arguments = listOf(
                navArgument("taskId") { type = NavType.StringType }
            )
        ) {
            TaskDetailScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
