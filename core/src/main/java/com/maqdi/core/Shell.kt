package com.maqdi.core

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

val LocalProfile = staticCompositionLocalOf<Profile?> { null }
val LocalOpenNotifications = staticCompositionLocalOf<(() -> Unit)?> { null }
/** يفتح القائمة الجانبية؛ null إن لم يوفّر التطبيق قائمة (فلا يظهر زر القائمة) */
val LocalOpenDrawer = staticCompositionLocalOf<(() -> Unit)?> { null }

/**
 * غلاف مشترك لكل التطبيقات: يوفّر الملف الشخصي ومركز الإشعارات فوق الشاشات،
 * وقائمة جانبية اختيارية: تظهر إن مُرّر [menu] غير فارغ.
 */
@Composable
fun AppShell(
    profile: Profile,
    onOpenOrder: (String) -> Unit,
    menu: List<MqDrawerSection> = emptyList(),
    currentRoute: String? = null,
    onMenuNavigate: (String) -> Unit = {},
    content: @Composable () -> Unit,
) {
    var show by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val activity = LocalContext.current as Activity
    val openDrawer: () -> Unit = remember { { scope.launch { drawerState.open() } } }
    BackHandler(enabled = show) { show = false }
    // أسماء الفئات المخصّصة من الإدارة (تتحدّث حيًّا)
    LaunchedEffect(Unit) {
        try {
            Repo.categories().collect { CategoryNames.update(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // تبقى الفئات الافتراضية إن تعذّرت القراءة
        }
    }
    CompositionLocalProvider(
        LocalProfile provides profile,
        LocalOpenNotifications provides { show = true },
        LocalOpenDrawer provides (if (menu.isNotEmpty()) openDrawer else null),
    ) {
        val body: @Composable () -> Unit = {
            Box(Modifier.fillMaxSize()) {
                content()
                if (show) {
                    NotificationsScreen(
                        uid = profile.uid,
                        onBack = { show = false },
                        onOpenOrder = { show = false; onOpenOrder(it) },
                    )
                }
            }
        }
        if (menu.isEmpty()) {
            body()
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                // السحب مغلق وهو مغلق كي لا يتعارض مع تمرير القوائم والرسوم؛ يُفتح بزر القائمة ويُغلق بالسحب أو اللمس
                gesturesEnabled = drawerState.isOpen,
                drawerContent = {
                    MqDrawerContent(
                        profile = profile,
                        sections = menu,
                        selected = currentRoute,
                        onSelect = { route ->
                            scope.launch { drawerState.close() }
                            onMenuNavigate(route)
                        },
                        onSignOut = {
                            scope.launch { drawerState.close() }
                            scope.launch { signOutAll(activity) }
                        },
                    )
                },
                content = body,
            )
        }
    }
}

@Composable
fun BellAction() {
    val p = LocalProfile.current ?: return
    val open = LocalOpenNotifications.current ?: return
    val unread = rememberLoadable(p.uid) { Repo.unreadCount(p.uid) }
    val n = (unread as? Loadable.Data)?.value ?: 0
    IconButton(onClick = open) {
        BadgedBox(badge = { if (n > 0) Badge { Text(if (n > 9) "9+" else n.toString()) } }) {
            Icon(Icons.Default.Notifications, contentDescription = "الإشعارات")
        }
    }
}

@Composable
fun NotificationsScreen(uid: String, onBack: () -> Unit, onOpenOrder: (String) -> Unit) {
    val list = rememberLoadable(uid) { Repo.notifications(uid) }
    val scope = rememberCoroutineScope()
    val current = (list as? Loadable.Data)?.value ?: emptyList()
    Screen(
        "الإشعارات", onBack = onBack,
        actions = {
            val unreadIds = current.filter { !it.read }.map { it.id }
            if (unreadIds.isNotEmpty()) {
                TextButton(onClick = {
                    scope.launch {
                        try { Repo.markAllRead(uid, unreadIds) } catch (e: CancellationException) { throw e } catch (_: Exception) { }
                    }
                }) { Text("قراءة الكل") }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            LoadableView(list) { all ->
                if (all.isEmpty()) Empty("لا توجد إشعارات بعد.")
                else LazyColumn {
                    items(all, key = { it.id }) { n ->
                        Card(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp).clickable {
                                scope.launch {
                                    try { if (!n.read) Repo.markRead(uid, n.id) } catch (e: CancellationException) { throw e } catch (_: Exception) { }
                                }
                                n.orderId?.let(onOpenOrder)
                            },
                            colors = CardDefaults.cardColors(containerColor = if (n.read) Mq.Card else Mq.GreenSoft),
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(n.title, style = MaterialTheme.typography.titleMedium)
                                    if (!n.read) Badge()
                                }
                                Text(n.body)
                                Text(dateTime(n.createdAt), color = Mq.Muted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
