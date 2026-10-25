package com.maqdi.partner

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.maqdi.core.ActionButton
import com.maqdi.core.Profile
import com.maqdi.core.Screen
import com.maqdi.core.signOutAll

private data class Tab(val route: String, val label: String, val icon: ImageVector)

@Composable
fun PartnerApp(profile: Profile) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    val tabs: List<Tab>
    val start: String
    when (profile.role) {
        "merchant" -> {
            start = "m_orders"
            tabs = listOf(
                Tab("m_orders", "الطلبات", Icons.Default.Menu),
                Tab("m_products", "المنتجات", Icons.Default.ShoppingCart),
                Tab("m_account", "حساب المتجر", Icons.Default.Info),
                Tab("me", "أنا", Icons.Default.Person),
            )
        }
        "courier" -> {
            start = "c_deliveries"
            tabs = listOf(
                Tab("c_deliveries", "التوصيل", Icons.Default.Home),
                Tab("me", "أنا", Icons.Default.Person),
            )
        }
        else -> {
            start = "a_dashboard"
            tabs = listOf(
                Tab("a_dashboard", "الرئيسية", Icons.Default.Home),
                Tab("a_orders", "الطلبات", Icons.Default.Menu),
                Tab("c_deliveries", "التوصيل", Icons.Default.ShoppingCart),
                Tab("a_finance", "المالية", Icons.Default.Info),
                Tab("a_more", "الإدارة", Icons.Default.Settings),
            )
        }
    }
    val tabRoutes = tabs.map { it.route }

    Scaffold(
        topBar = { com.maqdi.core.ConnectionBanner() },
        bottomBar = {
            if (route in tabRoutes) {
                NavigationBar {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = route == t.route,
                            onClick = {
                                nav.navigate(t.route) {
                                    popUpTo(start) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
        },
    ) { pad ->
        NavHost(nav, startDestination = start, modifier = Modifier.padding(pad)) {
            // التاجر
            composable("m_orders") { MerchantOrdersScreen(profile, onOpen = { nav.navigate("order/$it") }) }
            composable("m_products") { MerchantProductsScreen(profile) }
            composable("m_account") { StoreAccountScreen(profile.storeId ?: "", profile.name) }
            composable("me") { MeScreen(profile) }

            // الموصّل (والمدير)
            composable("c_deliveries") { CourierScreen(profile, onOpen = { nav.navigate("order/$it") }) }

            // المدير
            composable("a_dashboard") { DashboardScreen() }
            composable("a_orders") { AdminOrdersScreen(onOpen = { nav.navigate("order/$it") }) }
            composable("a_finance") { FinanceScreen(onStore = { nav.navigate("fin_store/$it") }) }
            composable("a_more") { AdminMoreScreen(profile, onNav = { nav.navigate(it) }) }
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

            // تفاصيل الطلب (مشتركة)
            composable("order/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                PartnerOrderScreen(it.arguments?.getString("id") ?: "", profile, onBack = { nav.popBackStack() })
            }
        }
    }
}

@Composable
fun MeScreen(profile: Profile) {
    val activity = LocalContext.current as Activity
    Screen("حسابي") { pad ->
        Column(Modifier.padding(pad).padding(16.dp)) {
            Text(profile.name.ifBlank { profile.email })
            Text(profile.email)
            ActionButton("تسجيل الخروج", Modifier.fillMaxWidth().padding(top = 24.dp), tonal = true) { signOutAll(activity) }
        }
    }
}
