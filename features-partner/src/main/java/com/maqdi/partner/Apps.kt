package com.maqdi.partner

import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.maqdi.core.AppShell
import com.maqdi.core.MqDrawerSection
import com.maqdi.core.MqNavItem
import com.maqdi.core.Profile

// ---------- واجهة المتجر ----------
@Composable
fun MerchantApp(profile: Profile) {
    // تاجر بلا متجر (دعوة قديمة ناقصة): نشرح بدل أن تفشل كل شاشة باستعلام فارغ
    if (profile.storeId.isNullOrBlank()) {
        NoStoreScreen()
        return
    }
    val nav = rememberNavController()
    val tabs = listOfNotNull(
        if (profile.can("orders")) Tab("m_orders", "الطلبات", Icons.AutoMirrored.Filled.ReceiptLong) else null,
        if (profile.can("products")) Tab("m_products", "المنتجات", Icons.Default.Inventory2) else null,
        if (profile.can("reports")) Tab("m_reports", "التقارير", Icons.Default.Assessment) else null,
        Tab("m_account", "حسابي", Icons.Default.Storefront),
    )
    AppShell(
        profile, onOpenOrder = { nav.push("order/$it") },
        menu = listOf(MqDrawerSection(null, tabs), MqDrawerSection(null, listOf(MqNavItem("profile", "الملف الشخصي", Icons.Default.AccountCircle)))),
        currentRoute = nav.currentRoute(),
        onMenuNavigate = { nav.menuGo(it, tabs, tabs.first().route) },
    ) {
        TabScaffold(nav, tabs, tabs.first().route) {
            composable("profile") { MeScreen(profile, onBack = { nav.popBackStack() }) }
            composable("m_orders") { MerchantOrdersScreen(profile, onOpen = { nav.push("order/$it") }) }
            composable("m_products") { MerchantProductsScreen(profile) }
            composable("m_reports") { ReportScreen("merchant", "تقارير المتجر") }
            composable("m_account") { if (profile.can("balance")) StoreAccountScreen(profile.storeId ?: "", profile.name, onProfile = { nav.push("profile") }) else MeScreen(profile) }
            orderRoute(nav, profile)
        }
    }
}

// ---------- واجهة المندوب ----------
@Composable
fun CourierApp(profile: Profile) {
    val nav = rememberNavController()
    val tabs = listOfNotNull(
        if (profile.can("deliveries")) Tab("c_deliveries", "التوصيل", Icons.Default.LocalShipping) else null,
        if (profile.can("deliveries")) Tab("c_history", "سجلي", Icons.Default.History) else null,
        if (profile.can("reports")) Tab("c_reports", "التقارير", Icons.Default.Assessment) else null,
        Tab("me", "أنا", Icons.Default.Person),
    )
    AppShell(
        profile, onOpenOrder = { nav.push("order/$it") },
        menu = listOf(MqDrawerSection(null, tabs)),
        currentRoute = nav.currentRoute(),
        onMenuNavigate = { nav.menuGo(it, tabs, tabs.first().route) },
    ) {
        TabScaffold(nav, tabs, tabs.first().route) {
            composable("c_deliveries") { CourierScreen(profile, onOpen = { nav.push("order/$it") }) }
            composable("c_history") { CourierHistoryScreen(profile, onOpen = { nav.push("order/$it") }) }
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
    val tabs = listOfNotNull(
        if (profile.can("reports")) Tab("a_dashboard", "الرئيسية", Icons.Default.Home) else null,
        if (profile.can("orders")) Tab("a_orders", "الطلبات", Icons.AutoMirrored.Filled.ReceiptLong) else null,
        if (profile.can("deliveries")) Tab("c_deliveries", "التوصيل", Icons.Default.LocalShipping) else null,
        if (profile.can("finance")) Tab("a_finance", "المالية", Icons.Default.AccountBalanceWallet) else null,
        Tab("a_more", "الإدارة", Icons.Default.Settings),
    )
    AppShell(
        profile, onOpenOrder = { nav.push("order/$it") },
        menu = listOf(
            MqDrawerSection("الرئيسية", tabs.filter { it.route != "a_more" }),
            MqDrawerSection("الإدارة", adminMenuItems(profile) + MqNavItem("profile", "الملف الشخصي", Icons.Default.AccountCircle)),
        ),
        currentRoute = nav.currentRoute(),
        onMenuNavigate = { nav.menuGo(it, tabs, tabs.first().route) },
    ) {
        TabScaffold(nav, tabs, tabs.first().route) {
            composable("profile") { MeScreen(profile, onBack = { nav.popBackStack() }) }
            composable("a_dashboard") { DashboardScreen() }
            composable("a_orders") { AdminOrdersScreen(onOpen = { nav.push("order/$it") }) }
            composable("c_deliveries") { CourierScreen(profile, onOpen = { nav.push("order/$it") }) }
            composable("a_finance") { FinanceScreen(onStore = { nav.push("fin_store/$it") }) }
            composable("a_more") { AdminMoreScreen(profile, onNav = { nav.push(it) }) }
            composable("a_reports") { ReportScreen("admin", "تقارير المنصة", onBack = { nav.popBackStack() }) }
            composable("a_stores") { AdminStoresScreen(onBack = { nav.popBackStack() }, onProducts = { nav.push("a_products/$it") }) }
            composable("a_products/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                ProductsManager(it.arguments?.getString("id") ?: "", onBack = { nav.popBackStack() })
            }
            composable("a_categories") { AdminCategoriesScreen(onBack = { nav.popBackStack() }) }
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

/** وجهات الإدارة حسب الصلاحية: مصدر واحد لشاشة «الإدارة» والقائمة الجانبية */
fun adminMenuItems(profile: Profile): List<MqNavItem> = listOf(
    MqNavItem("a_reports", "التقارير والإحصاءات", Icons.Default.Assessment) to profile.can("reports"),
    MqNavItem("a_stores", "المتاجر والتجار والمنتجات", Icons.Default.Storefront) to profile.can("stores"),
    MqNavItem("a_categories", "فئات المتاجر", Icons.Default.Category) to profile.can("categories"),
    MqNavItem("a_users", "المستخدمون والصلاحيات", Icons.Default.People) to
        (profile.can("users") || profile.can("permissions") || profile.can("grant_admin")),
    MqNavItem("a_settings", "رسوم التوصيل والمُهَل", Icons.Default.Tune) to profile.can("settings"),
    MqNavItem("a_expenses", "المصروفات", Icons.Default.Payments) to profile.can("expenses"),
    MqNavItem("a_export", "ملخص مالي وتصدير (CSV)", Icons.Default.FileDownload) to profile.can("finance"),
    MqNavItem("a_audit", "سجل التدقيق", Icons.Default.Security) to profile.can("audit"),
).filter { it.second }.map { it.first }
