package com.maqdi.core

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** لوحة الهوية البصرية الموحّدة (فاتح/داكن). الأساسي أخضر زيتي #1F6F4A على خلفية كريمية دافئة #F7F4EC */
@Immutable
class MqPalette(
    val primary: Color, val onPrimary: Color, val primaryContainer: Color, val onPrimaryContainer: Color,
    val secondary: Color, val bg: Color, val card: Color, val ink: Color, val muted: Color,
    val line: Color, val chip: Color, val red: Color, val amber: Color, val blue: Color,
    val violet: Color, val teal: Color, val dark: Boolean,
)

val MqLight = MqPalette(
    primary = Color(0xFF1F6F4A), onPrimary = Color.White, primaryContainer = Color(0xFFE6F2EA), onPrimaryContainer = Color(0xFF123B2A),
    secondary = Color(0xFF3FA37A), bg = Color(0xFFF7F4EC), card = Color.White, ink = Color(0xFF1B2620), muted = Color(0xFF5B665F),
    line = Color(0xFFE7E0CF), chip = Color(0xFFEFE9DA), red = Color(0xFFB42318), amber = Color(0xFFB45309), blue = Color(0xFF1D4ED8),
    violet = Color(0xFF6D4FD6), teal = Color(0xFF0E7490), dark = false,
)

val MqDark = MqPalette(
    primary = Color(0xFF20B486), onPrimary = Color(0xFF04261B), primaryContainer = Color(0xFF0B4A38), onPrimaryContainer = Color(0xFFBFF0DC),
    secondary = Color(0xFF4FD1A5), bg = Color(0xFF0F1512), card = Color(0xFF18211C), ink = Color(0xFFE6EEE9), muted = Color(0xFF9DB0A5),
    line = Color(0xFF2A3731), chip = Color(0xFF212D26), red = Color(0xFFF87171), amber = Color(0xFFFBBF24), blue = Color(0xFF7AA2FF),
    violet = Color(0xFFA79BFF), teal = Color(0xFF67D6EE), dark = true,
)

val LocalMq = staticCompositionLocalOf { MqLight }

/** الوصول للألوان من أي شاشة: تتبدّل تلقائيًا مع الوضع الداكن */
object Mq {
    val Green: Color @Composable @ReadOnlyComposable get() = LocalMq.current.primary
    val GreenSoft: Color @Composable @ReadOnlyComposable get() = LocalMq.current.primaryContainer
    val Accent: Color @Composable @ReadOnlyComposable get() = LocalMq.current.secondary
    val Amber: Color @Composable @ReadOnlyComposable get() = LocalMq.current.amber
    val Red: Color @Composable @ReadOnlyComposable get() = LocalMq.current.red
    val Blue: Color @Composable @ReadOnlyComposable get() = LocalMq.current.blue
    val Violet: Color @Composable @ReadOnlyComposable get() = LocalMq.current.violet
    val Teal: Color @Composable @ReadOnlyComposable get() = LocalMq.current.teal
    val Bg: Color @Composable @ReadOnlyComposable get() = LocalMq.current.bg
    val Card: Color @Composable @ReadOnlyComposable get() = LocalMq.current.card
    val Ink: Color @Composable @ReadOnlyComposable get() = LocalMq.current.ink
    val Muted: Color @Composable @ReadOnlyComposable get() = LocalMq.current.muted
    val Line: Color @Composable @ReadOnlyComposable get() = LocalMq.current.line
    val Chip: Color @Composable @ReadOnlyComposable get() = LocalMq.current.chip
}

/** بطاقة موحّدة: زوايا مدوّرة وحدّ خفيف بدل اللون الأبيض الصريح */
@Composable
fun MqCard(
    modifier: Modifier = Modifier.fillMaxWidth(),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val m = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    Card(
        modifier = m, shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = Mq.Card),
        border = BorderStroke(1.dp, Mq.Line), content = content,
    )
}

/** حالة فارغة: أيقونة ونص وتلميح اختياري */
@Composable
fun EmptyState(title: String, hint: String? = null, icon: ImageVector = Icons.Default.Inbox) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = Mq.Muted, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium, color = Mq.Ink)
        if (hint != null) {
            Spacer(Modifier.height(4.dp))
            Text(hint, textAlign = TextAlign.Center, color = Mq.Muted)
        }
    }
}

/** هيكل تحميل نابض بدل الدوّامة الوحيدة: يُشعر بسرعة أكبر */
@Composable
fun Modifier.shimmer(): Modifier {
    val t = rememberInfiniteTransition(label = "skeleton")
    val a by t.animateFloat(
        initialValue = 0.35f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "alpha",
    )
    return this.background(Mq.Line.copy(alpha = a), RoundedCornerShape(10.dp))
}

@Composable
fun ListSkeleton(rows: Int = 6) {
    // عمود عادي (لا LazyColumn) كي يصلح داخل الشاشات القابلة للتمرير أيضًا
    Column(Modifier.fillMaxWidth()) {
        repeat(rows) {
            MqCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(56.dp).shimmer())
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Box(Modifier.fillMaxWidth(0.6f).height(16.dp).shimmer())
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.fillMaxWidth(0.35f).height(12.dp).shimmer())
                    }
                }
            }
        }
    }
}

/** حقل بحث موحّد مع أيقونة وزر مسح */
@Composable
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier.fillMaxWidth()) {
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = modifier, singleLine = true,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) {
                Icon(Icons.Default.Close, contentDescription = "مسح")
            }
        },
        shape = RoundedCornerShape(28.dp),
        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Mq.Card, focusedContainerColor = Mq.Card),
    )
}

/** شريحة رقم/مؤشر صغيرة للّوحات (قيمة كبيرة وعنوان) */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, accent: Color = Mq.Green) {
    MqCard(modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = accent)
            Spacer(Modifier.height(2.dp))
            Text(label, color = Mq.Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** أعمدة بسيطة بلا مكتبة خارجية: تُستعمل لمبيعات الأيام */
@Composable
fun BarChart(values: List<Pair<String, Long>>, modifier: Modifier = Modifier) {
    val max = (values.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(1L)
    Row(modifier.fillMaxWidth().height(140.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        values.forEach { (label, v) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Box(
                    Modifier.fillMaxWidth().height((100f * v / max).coerceAtLeast(2f).dp)
                        .background(Mq.Green, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)),
                )
                Text(label, style = MaterialTheme.typography.bodySmall, color = Mq.Muted, maxLines = 1)
            }
        }
    }
}

// ---------- التنقّل: الشريط السفلي والقائمة الجانبية ----------

/** عنصر تنقّل مشترك بين الشريط السفلي والقائمة الجانبية. badge > 0 يُظهر شارة رقم */
data class MqNavItem(val route: String, val label: String, val icon: ImageVector, val badge: Int = 0)

/** قسم في القائمة الجانبية (العنوان اختياري) */
data class MqDrawerSection(val title: String?, val items: List<MqNavItem>)

/** شريط سفلي بزوايا علوية مدوّرة وظل، والعنصر المختار يظهر داخل حبّة متحركة اللون والعرض */
@Composable
fun MqBottomBar(items: List<MqNavItem>, selected: String?, onSelect: (String) -> Unit) {
    Surface(
        color = Mq.Card,
        shadowElevation = 16.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            items.forEach { item ->
                MqBottomItem(item, item.route == selected, Modifier.weight(1f)) { onSelect(item.route) }
            }
        }
    }
}

@Composable
private fun MqBottomItem(item: MqNavItem, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val pill by animateColorAsState(if (selected) Mq.GreenSoft else Color.Transparent, tween(250), label = "pill")
    val pillWidth by animateDpAsState(if (selected) 60.dp else 36.dp, tween(250), label = "pillWidth")
    val tint by animateColorAsState(if (selected) Mq.Green else Mq.Muted, tween(250), label = "tint")
    val iconScale by animateFloatAsState(if (selected) 1.1f else 1f, tween(250), label = "iconScale")
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.width(pillWidth).height(32.dp).background(pill, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            BadgedBox(badge = {
                if (item.badge > 0) Badge { Text(if (item.badge > 9) "9+" else item.badge.toString()) }
            }) {
                Icon(item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp).scale(iconScale))
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            item.label, color = tint, fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

/** محتوى القائمة الجانبية: ترويسة متدرّجة بالحساب، أقسام، ثم تسجيل الخروج */
@Composable
fun MqDrawerContent(
    profile: Profile,
    sections: List<MqDrawerSection>,
    selected: String?,
    onSelect: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    ModalDrawerSheet(
        drawerContainerColor = Mq.Bg,
        // الحافة الداخلية للقائمة: end تتبدّل تلقائيًا مع اتجاه RTL
        drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
    ) {
        Box(
            Modifier.fillMaxWidth()
                .background(Brush.linearGradient(listOf(Mq.Green, Mq.Accent)))
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            Column {
                Box(
                    Modifier.size(60.dp).background(Color.White.copy(alpha = 0.22f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        profile.name.ifBlank { profile.email }.trim().take(1),
                        color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    profile.name.ifBlank { profile.email },
                    color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1,
                )
                Text(profile.email, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                Spacer(Modifier.height(8.dp))
                Surface(color = Color.White.copy(alpha = 0.22f), shape = RoundedCornerShape(50)) {
                    Text(
                        roleLabel(profile.role), color = Color.White, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
                    )
                }
            }
        }
        val itemColors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = Mq.GreenSoft,
            selectedIconColor = Mq.Green,
            selectedTextColor = Mq.Green,
            unselectedContainerColor = Color.Transparent,
            unselectedIconColor = Mq.Muted,
            unselectedTextColor = Mq.Ink,
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
            sections.forEachIndexed { i, sec ->
                if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = Mq.Line)
                if (sec.title != null) {
                    Text(
                        sec.title, color = Mq.Muted, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 6.dp),
                    )
                }
                sec.items.forEach { item ->
                    NavigationDrawerItem(
                        label = { Text(item.label) },
                        selected = item.route == selected,
                        onClick = { onSelect(item.route) },
                        icon = { Icon(item.icon, contentDescription = null) },
                        badge = { if (item.badge > 0) Text(item.badge.toString(), color = Mq.Green) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                        colors = itemColors,
                    )
                }
            }
        }
        HorizontalDivider(color = Mq.Line)
        NavigationDrawerItem(
            label = { Text("تسجيل الخروج") },
            selected = false,
            onClick = onSignOut,
            icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding).padding(vertical = 4.dp),
            colors = NavigationDrawerItemDefaults.colors(
                unselectedContainerColor = Color.Transparent, unselectedIconColor = Mq.Red, unselectedTextColor = Mq.Red,
            ),
        )
    }
}
