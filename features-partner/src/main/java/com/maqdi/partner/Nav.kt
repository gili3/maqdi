package com.maqdi.partner

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.maqdi.core.ActionButton
import com.maqdi.core.ConnectionBanner
import com.maqdi.core.MqBottomBar
import com.maqdi.core.MqNavItem
import com.maqdi.core.Profile
import com.maqdi.core.ProfileScreen
import com.maqdi.core.Screen
import com.maqdi.core.signOutAll

/** التبويب هو عنصر التنقّل المشترك (الشريط السفلي والقائمة الجانبية) */
typealias Tab = MqNavItem

/** المسار الحالي في الملاحة (null قبل أول وجهة) */
@Composable
fun NavHostController.currentRoute(): String? {
    val entry by currentBackStackEntryAsState()
    return entry?.destination?.route
}

/** دفع وجهة فوق الحالية؛ singleTop يمنع فتح الشاشة مرتين عند النقر المزدوج */
fun NavHostController.push(route: String) {
    navigate(route) { launchSingleTop = true }
}

/** تبويب: يحفظ الحالة ويعود لنقطة البداية. وجهة أخرى: تُدفع فوق الحالية */
fun NavHostController.menuGo(route: String, tabs: List<Tab>, start: String) {
    if (tabs.any { it.route == route }) {
        navigate(route) {
            popUpTo(start) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    } else {
        navigate(route) { launchSingleTop = true }
    }
}

/** هيكل مشترك: شريط اتصال + شريط سفلي + ملاحة */
@Composable
fun TabScaffold(nav: NavHostController, tabs: List<Tab>, start: String, builder: NavGraphBuilder.() -> Unit) {
    val route = nav.currentRoute()
    val tabRoutes = tabs.map { it.route }
    Scaffold(
        topBar = { ConnectionBanner() },
        bottomBar = {
            if (route in tabRoutes) {
                MqBottomBar(tabs, route, onSelect = { nav.menuGo(it, tabs, start) })
            }
        },
    ) { pad ->
        NavHost(
            nav, startDestination = start, modifier = Modifier.padding(pad),
        enterTransition = { fadeIn(tween(220)) },
        exitTransition = { fadeOut(tween(120)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { fadeOut(tween(120)) },
            builder = builder,
        )
    }
}

/** مسار تفاصيل الطلب المشترك بين الأدوار */
fun NavGraphBuilder.orderRoute(nav: NavHostController, profile: Profile) {
    composable("order/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
        PartnerOrderScreen(it.arguments?.getString("id") ?: "", profile, onBack = { nav.popBackStack() })
    }
}

/** حسابي للتاجر/المندوب/المدير: الملف الشخصي المشترك */
@Composable
fun MeScreen(profile: Profile, onBack: (() -> Unit)? = null) = ProfileScreen(profile, onBack = onBack)

@Composable
fun NoStoreScreen() {
    val activity = LocalContext.current as Activity
    Screen("حسابك غير مكتمل") { pad ->
        Column(Modifier.padding(pad).padding(24.dp)) {
            Text("حساب التاجر هذا غير مربوط بمتجر. تواصل مع الإدارة لربطه بمتجرك ثم أعد فتح التطبيق.")
            ActionButton("تسجيل الخروج", Modifier.fillMaxWidth().padding(top = 24.dp), tonal = true) { signOutAll(activity) }
        }
    }
}
