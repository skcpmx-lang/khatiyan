package com.shohan.khatiyan.presentation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.shohan.khatiyan.di.AppContainer
import com.shohan.khatiyan.presentation.cashflow.CashflowScreen
import com.shohan.khatiyan.presentation.cashflow.ExpenseEditScreen
import com.shohan.khatiyan.presentation.cashflow.IncomeEditScreen
import com.shohan.khatiyan.presentation.dashboard.DashboardScreen
import com.shohan.khatiyan.presentation.emi.EmiDetailScreen
import com.shohan.khatiyan.presentation.emi.EmiEditScreen
import com.shohan.khatiyan.presentation.hisab.HisabScreen
import com.shohan.khatiyan.presentation.loan.LoanDetailScreen
import com.shohan.khatiyan.presentation.loan.LoanEditScreen
import com.shohan.khatiyan.presentation.personal.PersonDetailScreen
import com.shohan.khatiyan.presentation.personal.PersonEditScreen
import com.shohan.khatiyan.presentation.reports.ReportsScreen
import com.shohan.khatiyan.presentation.search.SearchScreen
import com.shohan.khatiyan.presentation.settings.SettingsScreen
import com.shohan.khatiyan.presentation.shop.CreditEditScreen
import com.shohan.khatiyan.presentation.shop.ShopDetailScreen
import com.shohan.khatiyan.presentation.shop.ShopEditScreen
import com.shohan.khatiyan.presentation.navigation.Routes

private data class MainTab(val route: String, val labelBn: String, val icon: ImageVector)

private val mainTabs = listOf(
    MainTab(Routes.DASHBOARD, "ড্যাশবোর্ড", Icons.Outlined.Dashboard),
    MainTab(Routes.HISAB, "হিসাব", Icons.Outlined.Calculate),
    MainTab(Routes.LEDGER, "লেনদেন", Icons.Outlined.SwapHoriz),
    MainTab(Routes.REPORTS, "রিপোর্ট", Icons.Outlined.PieChart),
    MainTab(Routes.SETTINGS, "সেটিংস", Icons.Outlined.Settings),
)

/** Root of the signed-in app: bottom navigation + full NavHost. */
@Composable
fun KhatiyanMainApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val container = (context.applicationContext as com.shohan.khatiyan.KhatiyanApp).container
    val navController = rememberNavController()
    KhatiyanNavHost(navController, container)
}

@Composable
fun KhatiyanNavHost(navController: NavHostController, container: AppContainer) {
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isTab = mainTabs.any { it.route == currentRoute }
    if (isTab) {
        selectedIndex = mainTabs.indexOfFirst { it.route == currentRoute }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (isTab) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                ) {
                    mainTabs.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = index == selectedIndex,
                            onClick = {
                                if (currentRoute != tab.route) {
                                    navController.navigate(tab.route) {
                                        popUpTo(Routes.DASHBOARD) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.labelBn) },
                            label = { Text(tab.labelBn, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.DASHBOARD,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.DASHBOARD) {
                DashboardScreen(navController, container)
            }
            composable(Routes.HISAB) {
                HisabScreen(navController, container)
            }
            composable(Routes.LEDGER) {
                CashflowScreen(navController, container)
            }
            composable(Routes.REPORTS) {
                ReportsScreen(navController, container)
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(navController, container)
            }
            composable(Routes.SEARCH) {
                SearchScreen(navController, container)
            }

            composable(Routes.SHOPS) {
                com.shohan.khatiyan.presentation.shop.ShopsScreen(navController, container)
            }
            composable(
                Routes.SHOP_EDIT,
                arguments = listOf(navArgument("shopId") { nullable = true; type = NavType.StringType }),
            ) { entry ->
                ShopEditScreen(navController, container, entry.longArg("shopId"))
            }
            composable(
                Routes.SHOP_DETAIL,
                arguments = listOf(navArgument("shopId") { type = NavType.LongType }),
            ) { entry ->
                ShopDetailScreen(navController, container, entry.arguments?.getLong("shopId") ?: 0L)
            }
            composable(
                Routes.CREDIT_EDIT,
                arguments = listOf(
                    navArgument("shopId") { type = NavType.LongType },
                    navArgument("creditId") { nullable = true; type = NavType.StringType },
                ),
            ) { entry ->
                CreditEditScreen(
                    navController,
                    container,
                    entry.arguments?.getLong("shopId") ?: 0L,
                    entry.longArg("creditId"),
                )
            }

            composable(Routes.LOANS) {
                com.shohan.khatiyan.presentation.loan.LoansScreen(navController, container)
            }
            composable(
                Routes.LOAN_EDIT,
                arguments = listOf(navArgument("loanId") { nullable = true; type = NavType.StringType }),
            ) { entry ->
                LoanEditScreen(navController, container, entry.longArg("loanId"))
            }
            composable(
                Routes.LOAN_DETAIL,
                arguments = listOf(navArgument("loanId") { type = NavType.LongType }),
            ) { entry ->
                LoanDetailScreen(navController, container, entry.arguments?.getLong("loanId") ?: 0L)
            }

            composable(Routes.EMIS) {
                com.shohan.khatiyan.presentation.emi.EmisScreen(navController, container)
            }
            composable(
                Routes.EMI_EDIT,
                arguments = listOf(navArgument("emiId") { nullable = true; type = NavType.StringType }),
            ) { entry ->
                EmiEditScreen(navController, container, entry.longArg("emiId"))
            }
            composable(
                Routes.EMI_DETAIL,
                arguments = listOf(navArgument("emiId") { type = NavType.LongType }),
            ) { entry ->
                EmiDetailScreen(navController, container, entry.arguments?.getLong("emiId") ?: 0L)
            }

            composable(Routes.PEOPLE) {
                com.shohan.khatiyan.presentation.personal.PeopleScreen(navController, container)
            }
            composable(
                Routes.PERSON_EDIT,
                arguments = listOf(navArgument("personId") { nullable = true; type = NavType.StringType }),
            ) { entry ->
                PersonEditScreen(navController, container, entry.longArg("personId"))
            }
            composable(
                Routes.PERSON_DETAIL,
                arguments = listOf(navArgument("personId") { type = NavType.LongType }),
            ) { entry ->
                PersonDetailScreen(navController, container, entry.arguments?.getLong("personId") ?: 0L)
            }

            composable(
                Routes.INCOME_EDIT,
                arguments = listOf(navArgument("entryId") { nullable = true; type = NavType.StringType }),
            ) { entry ->
                IncomeEditScreen(navController, container, entry.longArg("entryId"))
            }
            composable(
                Routes.EXPENSE_EDIT,
                arguments = listOf(navArgument("entryId") { nullable = true; type = NavType.StringType }),
            ) { entry ->
                ExpenseEditScreen(navController, container, entry.longArg("entryId"))
            }
        }
    }
}

private fun androidx.navigation.NavBackStackEntry.longArg(name: String): Long? =
    arguments?.getString(name)?.toLongOrNull()
