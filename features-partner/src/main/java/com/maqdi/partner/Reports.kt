package com.maqdi.partner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.maqdi.core.Api
import com.maqdi.core.ApiException
import com.maqdi.core.BarChart
import com.maqdi.core.InfoRow
import com.maqdi.core.ListSkeleton
import com.maqdi.core.Mq
import com.maqdi.core.Screen
import com.maqdi.core.Section
import com.maqdi.core.StatTile
import com.maqdi.core.money
import kotlinx.coroutines.CancellationException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** نوع التقرير يحدد الدالة في الخادم والأرقام المعروضة: admin | merchant | courier */
private val khartoumTz: TimeZone = TimeZone.getTimeZone("Africa/Khartoum")

private fun isoRange(days: Int): Pair<String, String> {
    val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply { timeZone = khartoumTz }
    val cal = Calendar.getInstance(khartoumTz)
    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
    cal.add(Calendar.DAY_OF_YEAR, 1)
    val to = f.format(cal.time)               // بداية الغد = نهاية اليوم الحالي
    cal.add(Calendar.DAY_OF_YEAR, -days)
    return f.format(cal.time) to to
}

private fun otherLabel(k: String) = when (k) {
    "rejected" -> "مرفوضة"; "cancelled" -> "ملغاة"; "expired" -> "منتهية المهلة"; "delivery_failed" -> "تعذّر تسليمها"; else -> k
}

@Composable
fun ReportScreen(kind: String, title: String, onBack: (() -> Unit)? = null) {
    var days by remember { mutableStateOf(7) }
    var tick by remember { mutableStateOf(0) }
    var data by remember { mutableStateOf<Api.ReportData?>(null) }
    var err by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(days, tick) {
        data = null; err = null
        val (from, to) = isoRange(days)
        try {
            data = Api.report(kind, from, to)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            err = e.userMessage
        }
    }

    Screen(title, onBack = onBack, actions = {
        IconButton(onClick = { tick++ }) { Icon(Icons.Default.Refresh, contentDescription = "تحديث") }
    }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7 to "٧ أيام", 30 to "٣٠ يومًا", 90 to "٩٠ يومًا").forEach { (d, l) ->
                    FilterChip(selected = days == d, onClick = { days = d }, label = { Text(l) })
                }
            }
            err?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
            val d = data
            if (d == null && err == null) {
                ListSkeleton(rows = 3)
            }
            if (d != null) ReportBody(kind, d)
        }
    }
}

@Composable
private fun ReportBody(kind: String, d: Api.ReportData) {
    val t = d.totals
    val tiles: List<Pair<String, String>> = when (kind) {
        "admin" -> listOf(
            "طلبات مسلَّمة" to (t["orders"] ?: 0L).toString(), "مبيعات المتاجر" to money(t["itemsTotal"] ?: 0L),
            "إيراد التوصيل" to money(t["deliveryRevenue"] ?: 0L), "النقد المحصَّل" to money(t["collected"] ?: 0L),
        )
        "merchant" -> listOf("طلبات مسلَّمة" to (t["orders"] ?: 0L).toString(), "مبيعات متجري" to money(t["itemsTotal"] ?: 0L))
        else -> listOf(
            "توصيلات منجزة" to (t["orders"] ?: 0L).toString(), "المبالغ المحصَّلة" to money(t["collected"] ?: 0L),
            "تعذّر التسليم" to d.failed.toString(),
        )
    }
    tiles.chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pair.forEach { (l, v) -> StatTile(l, v, Modifier.weight(1f)) }
            if (pair.size == 1) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
        }
    }

    if (d.byDay.isEmpty()) {
        Section("لا بيانات") { Text("لا توجد طلبات مسلَّمة في هذه الفترة.", color = Mq.Muted) }
        return
    }
    val chartDays = d.byDay.takeLast(14)
    Section(if (kind == "courier") "التوصيلات حسب اليوم" else "المبيعات حسب اليوم") {
        BarChart(chartDays.map { it.day.takeLast(5) to (if (kind == "courier") it.orders else it.amount) })
    }
    if (d.topProducts.isNotEmpty()) {
        Section("الأكثر مبيعًا") {
            d.topProducts.take(5).forEach { InfoRow(it.name, "${it.count} × • ${money(it.amount)}") }
        }
    }
    if (kind == "admin" && d.byStore.isNotEmpty()) {
        Section("حسب المتجر") {
            d.byStore.take(8).forEach { InfoRow(it.name, "${it.count} طلب • ${money(it.amount)}") }
        }
    }
    val others = d.other.filterValues { it > 0 }
    if (others.isNotEmpty()) {
        Section("طلبات غير مكتملة") { others.forEach { (k, v) -> InfoRow(otherLabel(k), v.toString()) } }
    }
    if (d.truncated) {
        Text("تنبيه: الفترة كبيرة فاقتُطعت النتائج. اختر فترة أقصر لأرقام كاملة.", color = Mq.Amber, modifier = Modifier.padding(16.dp))
    }
}
