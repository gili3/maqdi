package com.maqdi.customer

import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.maqdi.core.MqBottomBar
import com.maqdi.core.MqDrawerSection
import com.maqdi.core.MqNavItem
import com.maqdi.core.Profile

@Composable
fun CustomerApp(profile: Profile) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val tabs = listOf(
        MqNavItem("stores", "المتاجر", Icons.Default.Home),
        MqNavItem("cart", "السلة", Icons.Default.ShoppingCart, badge = Cart.count),
        MqNavItem("orders", "طلباتي", Icons.AutoMirrored.Filled.ReceiptLong),
        MqNavItem("account", "حسابي", Icons.Default.Person),
    )
    // القائمة الجانبية: التبويبات + البحث (وجهة مستقلة ليست تبويبًا)
    val menu = listOf(
        MqDrawerSection(
            null,
            listOf(tabs[0], MqNavItem("search", "بحث", Icons.Default.Search), tabs[1], tabs[2], tabs[3]),
        ),
    )
    val tabRoutes = tabs.map { it.route }

    val go: (String) -> Unit = { r ->
        if (r in tabRoutes) {
            nav.navigate(r) {
                popUpTo("stores") { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        } else {
            nav.navigate(r) { launchSingleTop = true }
        }
    }

    val push: (String) -> Unit = { nav.navigate(it) { launchSingleTop = true } }

    com.maqdi.core.AppShell(
        profile, onOpenOrder = { push("order/$it") },
        menu = menu, currentRoute = route, onMenuNavigate = go,
    ) {
    Scaffold(
        topBar = { com.maqdi.core.ConnectionBanner() },
        bottomBar = {
            if (route in tabRoutes) {
                MqBottomBar(tabs, route, onSelect = go)
            }
        },
    ) { pad ->
        NavHost(
            nav, startDestination = "stores", modifier = Modifier.padding(pad),
        enterTransition = { fadeIn(tween(220)) },
        exitTransition = { fadeOut(tween(120)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { fadeOut(tween(120)) },
        ) {
            composable("stores") { StoresScreen(onOpen = { push("store/$it") }, onSearch = { push("search") }) }
            composable("store/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                val id = it.arguments?.getString("id") ?: ""
                ProductsScreen(id, onBack = { nav.popBackStack() }, onOpenCart = { go("cart") })
            }
            composable("search") { SearchScreen(onBack = { nav.popBackStack() }, onOpenStore = { push("store/$it") }) }
            composable("cart") {
                CartScreen(
                    profile,
                    onBrowse = { go("stores") },
                    onOrderSent = { id ->
                        nav.navigate("order/$id") { popUpTo("stores") }
                    },
                )
            }
            composable("orders") { OrdersScreen(profile.uid, onOpen = { push("order/$it") }) }
            composable("order/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                OrderDetailScreen(it.arguments?.getString("id") ?: "", onBack = { nav.popBackStack() })
            }
            composable("account") { AccountScreen(profile) }
        }
    }
    }
}
