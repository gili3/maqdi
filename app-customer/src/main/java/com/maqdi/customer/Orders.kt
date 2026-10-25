package com.maqdi.customer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.maqdi.core.Mq
import com.maqdi.core.ActionButton
import com.maqdi.core.Api
import com.maqdi.core.ConfirmDialog
import com.maqdi.core.Empty
import com.maqdi.core.InfoRow
import com.maqdi.core.LoadableView
import com.maqdi.core.Order
import com.maqdi.core.ReasonDialog
import com.maqdi.core.Repo
import com.maqdi.core.Screen
import com.maqdi.core.Section
import com.maqdi.core.St
import com.maqdi.core.StatusChip
import com.maqdi.core.StatusSteps
import com.maqdi.core.dateTime
import com.maqdi.core.minutesLeft
import com.maqdi.core.money
import com.maqdi.core.rememberLoadable

@Composable
fun OrdersScreen(uid: String, onOpen: (String) -> Unit) {
    val orders = rememberLoadable(uid) { Repo.customerOrders(uid) }
    Screen("طلباتي") { pad ->
        Column(Modifier.padding(pad)) {
            LoadableView(orders) { list ->
                if (list.isEmpty()) com.maqdi.core.EmptyState("لم تطلب شيئًا بعد", "تصفّح المتاجر وأرسل أول طلب.")
                else LazyColumn {
                    items(list, key = { it.id }) { o ->
                        com.maqdi.core.MqCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), onClick = { onOpen(o.id) }) {
                            Column(Modifier.padding(14.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(o.storeName, style = MaterialTheme.typography.titleMedium)
                                    StatusChip(o.status)
                                }
                                Text(
                                    (if (o.totalsAreEstimate) "تقريبي: " else "") + money(o.total),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(dateTime(o.createdAt), color = Mq.Muted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OrderDetailScreen(orderId: String, onBack: () -> Unit) {
    val order = rememberLoadable(orderId) { Repo.order(orderId) }
    Screen("تفاصيل الطلب", onBack = onBack) { pad ->
        Column(Modifier.padding(pad)) {
            LoadableView(order) { o ->
                if (o == null) Empty("الطلب غير موجود.") else OrderBody(o)
            }
        }
    }
}

@Composable
private fun OrderBody(o: Order) {
    var showDecline by remember { mutableStateOf(false) }
    var showCancel by remember { mutableStateOf(false) }
    var showApprove by remember { mutableStateOf(false) }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Section(o.storeName) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("الحالة")
                StatusChip(o.status)
            }
            StatusSteps(o.status)
            InfoRow("وقت الطلب", dateTime(o.createdAt))
            when (o.status) {
                St.PENDING -> Text(
                    "بانتظار مراجعة التاجر" + (minutesLeft(o.expiresAt)?.let { " (تنتهي المهلة بعد نحو $it دقيقة)" } ?: ""),
                )
                St.REJECTED -> Text("سبب الرفض: ${o.rejectReason ?: "—"}", color = Mq.Red)
                St.CANCELLED -> Text("سبب الإلغاء: ${o.cancelReason ?: "—"}", color = Mq.Red)
                St.FAILED -> Text("سبب تعذّر التسليم: ${o.failReason ?: "—"}", color = Mq.Red)
                St.EXPIRED -> Text("انتهت مهلة الطلب. يمكنك إرسال طلب جديد.", color = Mq.Red)
                St.OUT -> Text("طلبك في الطريق إليك.")
                St.DELIVERED -> Text("تم تسليم طلبك. شكرًا لك.")
                else -> {}
            }
        }

        if (o.status == St.AWAITING) {
            Section("راجع الإجمالي النهائي") {
                Text(
                    "راجع التاجر طلبك. افحص المنتجات والأسعار، ثم وافق ليبدأ التجهيز." +
                        (minutesLeft(o.expiresAt)?.let { " المهلة المتبقية: نحو $it دقيقة." } ?: ""),
                )
            }
        }

        Section(if (o.status == St.PENDING) "المنتجات المطلوبة" else "المنتجات") {
            o.shownLines.forEach { l ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${l.name} × ${l.qty}", Modifier.weight(1f))
                    Text(money(l.lineTotal))
                }
            }
            o.proposal?.note?.let { Text("ملاحظة التاجر: $it", color = Mq.Muted, modifier = Modifier.padding(top = 6.dp)) }
        }

        Section(if (o.totalsAreEstimate) "الإجمالي (تقديري)" else "الإجمالي") {
            InfoRow("قيمة المنتجات", money(o.itemsTotal))
            InfoRow("رسوم التوصيل", money(o.deliveryFee))
            InfoRow("المبلغ المستحق", money(o.total), bold = true)
            InfoRow("طريقة الدفع", "نقدًا عند الاستلام")
        }

        Section("التوصيل") {
            InfoRow("الهاتف", o.customerPhone)
            InfoRow("العنوان", o.address)
            o.locationNote?.let { InfoRow("وصف الموقع", it) }
            o.courierName?.let { InfoRow("الموصّل", it) }
        }

        if (o.status == St.AWAITING) {
            ActionButton("أوافق على الإجمالي (${money(o.total)})", Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                showApprove = true
            }
            ActionButton("لا أوافق (إلغاء الطلب)", Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), tonal = true) {
                showDecline = true
            }
        } else if (o.status == St.PENDING || o.status == St.CONFIRMED) {
            ActionButton("إلغاء الطلب", Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), tonal = true) {
                showCancel = true
            }
        }
    }

    if (showApprove) {
        ConfirmDialog(
            "تأكيد الموافقة", "ستدفع ${money(o.total)} نقدًا عند الاستلام. هل توافق؟", "نعم، أوافق",
            onDismiss = { showApprove = false },
        ) { Api.respond(o.id, o.revision, true) }
    }
    if (showDecline) {
        ConfirmDialog(
            "إلغاء الطلب", "سيُلغى الطلب ولن يُجهَّز. هل أنت متأكد؟", "نعم، ألغِ",
            onDismiss = { showDecline = false },
        ) { Api.respond(o.id, o.revision, false) }
    }
    if (showCancel) {
        ReasonDialog("إلغاء الطلب", "تأكيد الإلغاء", onDismiss = { showCancel = false }) { reason ->
            Api.changeStatus(o.id, St.CANCELLED, reason)
        }
    }
}
