package com.maqdi.customer

import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
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
import com.maqdi.core.Profile

private data class Tab(val route: String, val label: String, val icon: ImageVector)

@Composable
fun CustomerApp(profile: Profile) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val tabs = listOf(
        Tab("stores", "المتاجر", Icons.Default.Home),
        Tab("cart", if (Cart.count > 0) "السلة (${Cart.count})" else "السلة", Icons.Default.ShoppingCart),
        Tab("orders", "طلباتي", Icons.AutoMirrored.Filled.ReceiptLong),
        Tab("account", "حسابي", Icons.Default.Person),
    )
    val tabRoutes = tabs.map { it.route }

    com.maqdi.core.AppShell(profile, onOpenOrder = { nav.navigate("order/$it") }) {
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
                                    popUpTo("stores") { saveState = true }
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
        NavHost(
            nav, startDestination = "stores", modifier = Modifier.padding(pad),
        enterTransition = { fadeIn(tween(220)) },
        exitTransition = { fadeOut(tween(120)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { fadeOut(tween(120)) },
        ) {
            composable("stores") { StoresScreen(onOpen = { nav.navigate("store/$it") }, onSearch = { nav.navigate("search") }) }
            composable("store/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                val id = it.arguments?.getString("id") ?: ""
                ProductsScreen(id, onBack = { nav.popBackStack() }, onOpenCart = { nav.navigate("cart") })
            }
            composable("search") { SearchScreen(onBack = { nav.popBackStack() }, onOpenStore = { nav.navigate("store/$it") }) }
            composable("cart") {
                CartScreen(
                    profile,
                    onBrowse = { nav.navigate("stores") },
                    onOrderSent = { id ->
                        nav.navigate("order/$id") { popUpTo("stores") }
                    },
                )
            }
            composable("orders") { OrdersScreen(profile.uid, onOpen = { nav.navigate("order/$it") }) }
            composable("order/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                OrderDetailScreen(it.arguments?.getString("id") ?: "", onBack = { nav.popBackStack() })
            }
            composable("account") { AccountScreen(profile) }
        }
    }
    }
}
