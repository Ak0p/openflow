package ro.akop.openflow.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavHostController
import ro.akop.openflow.AppContainer
import ro.akop.openflow.feature.detail.DetailScreen
import ro.akop.openflow.feature.home.HomeScreen
import ro.akop.openflow.feature.record.RecordScreen
import ro.akop.openflow.feature.settings.SettingsScreen

@Composable
fun AppNavHost(
    container: AppContainer,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = HomeRoute,
    ) {
        composable<HomeRoute> {
            HomeScreen(
                container = container,
                onDictate = { navController.navigate(RecordRoute) },
                onOpenTranscript = { id -> navController.navigate(DetailRoute(id)) },
                onOpenSettings = { navController.navigate(SettingsRoute) },
            )
        }

        composable<RecordRoute> {
            RecordScreen(
                container = container,
                onSaved = { id ->
                    navController.popBackStack()
                    navController.navigate(DetailRoute(id))
                },
                onDismiss = { navController.popBackStack() },
            )
        }

        composable<DetailRoute> {
            DetailScreen(
                container = container,
                onBack = { navController.popBackStack() },
            )
        }

        composable<SettingsRoute> {
            SettingsScreen(
                container = container,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
