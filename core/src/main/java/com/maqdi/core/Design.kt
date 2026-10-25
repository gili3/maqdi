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

/** لوحة الهوية البصرية الموحّدة (فاتح/داكن). الأساسي زمردي #087F5B والمساعد #20B486 */
@Immutable
class MqPalette(
    val primary: Color, val onPrimary: Color, val primaryContainer: Color, val onPrimaryContainer: Color,
    val secondary: Color, val bg: Color, val card: Color, val ink: Color, val muted: Color,
    val line: Color, val chip: Color, val red: Color, val amber: Color, val blue: Color,
    val violet: Color, val teal: Color, val dark: Boolean,
)

val MqLight = MqPalette(
    primary = Color(0xFF087F5B), onPrimary = Color.White, primaryContainer = Color(0xFFD3F0E4), onPrimaryContainer = Color(0xFF04402D),
    secondary = Color(0xFF20B486), bg = Color(0xFFF5F8F6), card = Color.White, ink = Color(0xFF202923), muted = Color(0xFF58675E),
    line = Color(0xFFDCE5DF), chip = Color(0xFFE8EFEB), red = Color(0xFFB42318), amber = Color(0xFFB45309), blue = Color(0xFF1D4ED8),
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
