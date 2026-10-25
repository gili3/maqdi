package com.maqdi.partner

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.maqdi.core.ActionButton
import com.maqdi.core.ConnectionBanner
import com.maqdi.core.Profile
import com.maqdi.core.Screen
import com.maqdi.core.signOutAll

data class Tab(val route: String, val label: String, val icon: ImageVector)

/** هيكل مشترك: شريط اتصال + تبويبات سفلية + ملاحة */
@Composable
fun TabScaffold(nav: NavHostController, tabs: List<Tab>, start: String, builder: NavGraphBuilder.() -> Unit) {
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val tabRoutes = tabs.map { it.route }
    Scaffold(
        topBar = { ConnectionBanner() },
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
        NavHost(nav, startDestination = start, modifier = Modifier.padding(pad), builder = builder)
    }
}

/** مسار تفاصيل الطلب المشترك بين الأدوار */
fun NavGraphBuilder.orderRoute(nav: NavHostController, profile: Profile) {
    composable("order/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
        PartnerOrderScreen(it.arguments?.getString("id") ?: "", profile, onBack = { nav.popBackStack() })
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
