package com.maqdi.partner

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.maqdi.core.AppShell
import com.maqdi.core.Profile

// ---------- واجهة المتجر ----------
@Composable
fun MerchantApp(profile: Profile) {
    val nav = rememberNavController()
    val tabs = listOf(
        Tab("m_orders", "الطلبات", Icons.Default.Menu),
        Tab("m_products", "المنتجات", Icons.Default.ShoppingCart),
        Tab("m_reports", "التقارير", Icons.Default.Assessment),
        Tab("m_account", "حساب المتجر", Icons.Default.Info),
        Tab("me", "أنا", Icons.Default.Person),
    )
    AppShell(profile, onOpenOrder = { nav.navigate("order/$it") }) {
        TabScaffold(nav, tabs, "m_orders") {
            composable("m_orders") { MerchantOrdersScreen(profile, onOpen = { nav.navigate("order/$it") }) }
            composable("m_products") { MerchantProductsScreen(profile) }
            composable("m_reports") { ReportScreen("merchant", "تقارير المتجر") }
            composable("m_account") { StoreAccountScreen(profile.storeId ?: "", profile.name) }
            composable("me") { MeScreen(profile) }
            orderRoute(nav, profile)
        }
    }
}

// ---------- واجهة المندوب ----------
@Composable
fun CourierApp(profile: Profile) {
    val nav = rememberNavController()
    val tabs = listOf(
        Tab("c_deliveries", "التوصيل", Icons.Default.Home),
        Tab("c_history", "سجلي", Icons.Default.Menu),
        Tab("c_reports", "التقارير", Icons.Default.Assessment),
        Tab("me", "أنا", Icons.Default.Person),
    )
    AppShell(profile, onOpenOrder = { nav.navigate("order/$it") }) {
        TabScaffold(nav, tabs, "c_deliveries") {
            composable("c_deliveries") { CourierScreen(profile, onOpen = { nav.navigate("order/$it") }) }
            composable("c_history") { CourierHistoryScreen(profile, onOpen = { nav.navigate("order/$it") }) }
            composable("c_reports") { ReportScreen("courier", "تقارير توصيلاتي") }
            composable("me") { MeScreen(profile) }
            orderRoute(nav, profile)
        }
    }
}

// ---------- واجهة الإدارة (والمدير يوصّل من تبويب التوصيل) ----------
@Composable
fun AdminApp(profile: Profile) {
    val nav = rememberNavController()
    val tabs = listOf(
        Tab("a_dashboard", "الرئيسية", Icons.Default.Home),
        Tab("a_orders", "الطلبات", Icons.Default.Menu),
        Tab("c_deliveries", "التوصيل", Icons.Default.ShoppingCart),
        Tab("a_finance", "المالية", Icons.Default.Info),
        Tab("a_more", "الإدارة", Icons.Default.Settings),
    )
    AppShell(profile, onOpenOrder = { nav.navigate("order/$it") }) {
        TabScaffold(nav, tabs, "a_dashboard") {
            composable("a_dashboard") { DashboardScreen() }
            composable("a_orders") { AdminOrdersScreen(onOpen = { nav.navigate("order/$it") }) }
            composable("c_deliveries") { CourierScreen(profile, onOpen = { nav.navigate("order/$it") }) }
            composable("a_finance") { FinanceScreen(onStore = { nav.navigate("fin_store/$it") }) }
            composable("a_more") { AdminMoreScreen(profile, onNav = { nav.navigate(it) }) }
            composable("a_reports") { ReportScreen("admin", "تقارير المنصة", onBack = { nav.popBackStack() }) }
            composable("a_stores") { AdminStoresScreen(onBack = { nav.popBackStack() }, onProducts = { nav.navigate("a_products/$it") }) }
            composable("a_products/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                ProductsManager(it.arguments?.getString("id") ?: "", onBack = { nav.popBackStack() })
            }
            composable("a_users") { AdminUsersScreen(profile, onBack = { nav.popBackStack() }) }
            composable("a_settings") { AdminSettingsScreen(onBack = { nav.popBackStack() }) }
            composable("a_audit") { AuditScreen(onBack = { nav.popBackStack() }) }
            composable("a_expenses") { ExpensesScreen(onBack = { nav.popBackStack() }) }
            composable("a_export") { ExportScreen(onBack = { nav.popBackStack() }) }
            composable("fin_store/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                StoreFinanceScreen(it.arguments?.getString("id") ?: "", onBack = { nav.popBackStack() })
            }
            orderRoute(nav, profile)
        }
    }
}
