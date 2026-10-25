package com.maqdi.core

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** حالات الطلب بأسمائها الثابتة في النظام */
object St {
    const val PENDING = "pending_merchant"
    const val AWAITING = "awaiting_customer"
    const val CONFIRMED = "confirmed"
    const val PREPARING = "preparing"
    const val READY = "ready_for_pickup"
    const val OUT = "out_for_delivery"
    const val DELIVERED = "delivered"
    const val REJECTED = "rejected"
    const val CANCELLED = "cancelled"
    const val EXPIRED = "expired"
    const val FAILED = "delivery_failed"

    val all = listOf(PENDING, AWAITING, CONFIRMED, PREPARING, READY, OUT, DELIVERED, REJECTED, CANCELLED, EXPIRED, FAILED)
    val terminal = setOf(DELIVERED, REJECTED, CANCELLED, EXPIRED, FAILED)
    val steps = listOf(PENDING, AWAITING, CONFIRMED, PREPARING, READY, OUT, DELIVERED)

    private val labels = mapOf(
        PENDING to "بانتظار التاجر", AWAITING to "بانتظار موافقة الزبون", CONFIRMED to "تمت الموافقة",
        PREPARING to "قيد التجهيز", READY to "جاهز للاستلام", OUT to "في الطريق", DELIVERED to "تم التسليم",
        REJECTED to "مرفوض", CANCELLED to "ملغي", EXPIRED to "انتهت المهلة", FAILED to "تعذّر التسليم",
    )
    fun label(s: String) = labels[s] ?: s

    /** ألوان دلالية: رمادي=انتظار التاجر، كهرماني=يحتاج قرار الزبون، أخضر=موافقة/نجاح، بنفسجي=تجهيز، فيروزي=جاهز، أزرق=في الطريق، أحمر=فشل */
    @Composable
    fun color(s: String): Color = when (s) {
        PENDING -> Mq.Muted
        AWAITING -> Mq.Amber
        CONFIRMED, DELIVERED -> Mq.Green
        PREPARING -> Mq.Violet
        READY -> Mq.Teal
        OUT -> Mq.Blue
        else -> Mq.Red
    }

    fun icon(s: String): ImageVector = when (s) {
        PENDING -> Icons.Default.HourglassTop
        AWAITING -> Icons.Default.PendingActions
        CONFIRMED -> Icons.Default.CheckCircle
        PREPARING -> Icons.Default.Inventory2
        READY -> Icons.Default.Storefront
        OUT -> Icons.Default.LocalShipping
        DELIVERED -> Icons.Default.TaskAlt
        REJECTED -> Icons.Default.Cancel
        CANCELLED -> Icons.Default.Block
        EXPIRED -> Icons.Default.TimerOff
        else -> Icons.Default.ReportProblem
    }
}

/** أسماء الفئات المحمّلة من الإدارة (حالة مرصودة فتتحدّث الشاشات تلقائيًا) */
object CategoryNames {
    val names = androidx.compose.runtime.mutableStateMapOf<String, String>()
    private val defaults = mapOf("grocery" to "بقالة", "pharmacy" to "صيدلية", "other" to "أخرى")
    private val loaded = androidx.compose.runtime.mutableStateOf<List<Category>>(emptyList())

    fun label(id: String): String = names[id] ?: defaults[id] ?: "أخرى"

    fun update(list: List<Category>) {
        loaded.value = list
        names.clear()
        list.forEach { names[it.id] = it.name }
    }

    /** الفئات الفعّالة بالترتيب؛ إن لم تُنشأ فئات بعد تُعرض الافتراضية الثلاث */
    fun options(): List<Category> {
        val l = loaded.value
        if (l.isEmpty()) return defaults.entries.mapIndexed { i, e -> Category(e.key, e.value, i.toLong(), true) }
        return l.filter { it.isActive }.sortedWith(compareBy({ it.order }, { it.name }))
    }

    /** كل الفئات (حتى المعطّلة) للإدارة */
    fun all(): List<Category> = loaded.value.sortedWith(compareBy({ it.order }, { it.name }))
}

fun categoryLabel(c: String) = CategoryNames.label(c)
fun roleLabel(r: String) = when (r) { "customer" -> "زبون"; "merchant" -> "تاجر"; "admin" -> "مدير"; "courier" -> "مندوب"; else -> r }
fun settlementLabel(s: String) = when (s) { "settled" -> "مسوّى"; "partially_settled" -> "مسوّى جزئيًا"; else -> "غير مسوّى" }

fun money(v: Long): String = String.format(Locale.US, "%,d", v) + " ج.س"

private val khartoum: TimeZone = TimeZone.getTimeZone("Africa/Khartoum")

fun dateTime(ms: Long?): String {
    if (ms == null) return "—"
    val f = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US)
    f.timeZone = khartoum
    return f.format(Date(ms))
}

fun minutesLeft(ms: Long?): Long? = ms?.let { ((it - System.currentTimeMillis()) / 60000).coerceAtLeast(0) }

/** تاريخ قبل n يومًا بتوقيت الخرطوم بصيغة yyyy-MM-dd */
fun daysAgoKhartoum(n: Int): String {
    val cal = Calendar.getInstance(khartoum)
    cal.add(Calendar.DAY_OF_YEAR, -n)
    val f = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    f.timeZone = khartoum
    return f.format(cal.time)
}

private val dayRegex = Regex("^\\d{4}-\\d{2}-\\d{2}$")

/** بداية اليوم بتوقيت السودان بصيغة ISO، أو null إن كان النص غير صحيح */
fun isoStart(day: String): String? = if (dayRegex.matches(day.trim())) "${day.trim()}T00:00:00+02:00" else null

/** بداية اليوم التالي (نهاية حصرية) */
fun isoEndExclusive(day: String): String? {
    if (!dayRegex.matches(day.trim())) return null
    val f = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    f.timeZone = khartoum
    val d = f.parse(day.trim()) ?: return null
    val c = Calendar.getInstance(khartoum)
    c.time = d
    c.add(Calendar.DAY_OF_MONTH, 1)
    return f.format(c.time) + "T00:00:00+02:00"
}

fun todayKhartoum(): String {
    val f = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    f.timeZone = khartoum
    return f.format(Date())
}

fun entryTypeLabel(t: String) = when (t) {
    "order_delivered" -> "مستحق عن طلب مسلَّم"
    "settlement" -> "تسوية (دُفع للمتجر)"
    "correction" -> "تصحيح"
    "correction_replacement" -> "قيد بديل بعد التصحيح"
    else -> t
}

/** بداية اليوم بتوقيت السودان بالميلي ثانية، أو null إن كان النص غير صحيح */
fun dayStartMs(day: String): Long? {
    if (!dayRegex.matches(day.trim())) return null
    val f = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    f.timeZone = khartoum
    return f.parse(day.trim())?.time
}
