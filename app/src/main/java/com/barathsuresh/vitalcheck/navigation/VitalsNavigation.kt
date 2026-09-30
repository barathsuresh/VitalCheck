package com.barathsuresh.vitalcheck.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.barathsuresh.vitalcheck.ui.screens.home.HomeScreen
import com.barathsuresh.vitalcheck.ui.screens.symptoms.SymptomsScreen
import com.barathsuresh.vitalcheck.ui.screens.vitals.VitalsScreen

/**
 * VitalsNavigation
 * @author barathsuresh
 */
object Routes {
    const val HOME = "home"
    const val VITALS = "vitals"
    const val SYMPTOMS = "symptoms/{heartRate}/{respiratoryRate}"

    fun symptoms(heartRate: Int, respiratoryRate: Int) = "symptoms/$heartRate/$respiratoryRate"
}

@Composable
fun VitalsNavigation(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.HOME) { // Default is HomePage
        composable(Routes.HOME) {
            HomeScreen(
                onRecordHealthData = { navController.navigate(Routes.VITALS) },
            )
        }
        composable(Routes.VITALS) {
            VitalsScreen(
                onContinue = { heartRate, respiratoryRate ->
                    navController.navigate(Routes.symptoms(heartRate, respiratoryRate))
                },
            )
        }
        composable(
            route = Routes.SYMPTOMS,
            arguments = listOf(
                navArgument("heartRate") { type = NavType.IntType },
                navArgument("respiratoryRate") { type = NavType.IntType },
            ),
        ) { backStackEntry -> // fetch arguments from previous screen
            val heartRate = backStackEntry.arguments?.getInt("heartRate") ?: 0
            val respiratoryRate = backStackEntry.arguments?.getInt("respiratoryRate") ?: 0
            SymptomsScreen(
                heartRate = heartRate,
                respiratoryRate = respiratoryRate,
                onUploadComplete = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                },
            )
        }
    }
}