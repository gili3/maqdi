package com.maqdi.customer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

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
fun OrderDetailScreen(orderId: String, onBack: () -> Unit, onReorder: () -> Unit = {}) {
    val order = rememberLoadable(orderId) { Repo.order(orderId) }
    Screen("تفاصيل الطلب", onBack = onBack) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            LoadableView(order) { o ->
                if (o == null) Empty("الطلب غير موجود.") else OrderBody(o, onReorder)
            }
        }
    }
}

@Composable
private fun OrderBody(o: Order, onReorder: () -> Unit) {
    var showDecline by remember { mutableStateOf(false) }
    var showCancel by remember { mutableStateOf(false) }
    var showApprove by remember { mutableStateOf(false) }

    // مهلة الإلغاء بعد قبول التاجر وبدء التجهيز (من إعدادات الإدارة)
    val settingsL = rememberLoadable { Repo.settings() }
    val settings = (settingsL as? com.maqdi.core.Loadable.Data<com.maqdi.core.Settings>)?.value ?: com.maqdi.core.Settings()
    val graceMs = settings.customerCancelGraceMinutes * 60_000L
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(o.status) { while (o.status == St.PREPARING) { delay(10_000); now = System.currentTimeMillis() } }
    val prepAt = o.preparingAt
    val cancelLeftMs = if (o.status == St.PREPARING && prepAt != null && graceMs > 0) graceMs - (now - prepAt) else 0L
    val canCancelPreparing = cancelLeftMs > 0

    Column(Modifier.fillMaxSize()) {
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
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
            InfoRow("طريقة الدفع", com.maqdi.core.paymentMethodLabel(o.paymentMethod))
        }

        // الدفع بالتحويل (إيصال وحالة) ثم سجل الاسترداد
        PaymentSection(o, settings)
        RefundSection(o, settings)

        Section("التوصيل") {
            InfoRow("العنوان", o.address)
            o.locationNote?.let { InfoRow("وصف الموقع", it) }
            o.courierName?.let { InfoRow("الموصّل", it) }
            o.courierPhone?.takeIf { it.isNotBlank() && o.status == St.OUT }?.let { phone ->
                val ctx = androidx.compose.ui.platform.LocalContext.current
                ActionButton("اتصال بالموصّل", tonal = true) {
                    ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:" + phone)))
                }
            }
        }

        if (o.status in St.terminal) {
            // إعادة الطلب: يملأ السلة بنفس المنتجات المتوفرة الآن ثم يفتح السلة
            ActionButton("اطلب مرة أخرى", Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                val products = Repo.products(o.storeId).first()
                Cart.clear()
                var added = 0
                o.shownLines.forEach { l ->
                    val p = products.firstOrNull { it.id == l.productId && it.isAvailable } ?: return@forEach
                    val qty = minOf(l.qty.toInt(), (99L).toInt(), 99)
                    if (qty > 0) { Cart.set(p, o.storeName, qty); added++ }
                }
                if (added == 0) throw com.maqdi.core.ApiException("منتجات هذا الطلب غير متوفرة الآن.")
                onReorder()
            }
        }
        if (canCancelPreparing) {
            Text(
                "قبِل المتجر طلبك وبدأ التجهيز. يمكنك الإلغاء خلال ${((cancelLeftMs + 59_999) / 60_000).coerceAtLeast(1)} دقيقة فقط.",
                color = Mq.Muted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        if (o.status == St.PENDING || o.status == St.CONFIRMED || canCancelPreparing) {
            ActionButton("إلغاء الطلب", Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), tonal = true) {
                showCancel = true
            }
        }
    }
    // لحظة القرار الأهم: الإجمالي وزر الموافقة ثابتان أسفل الشاشة
    if (o.status == St.AWAITING) {
        androidx.compose.material3.Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (o.isTransfer) "المبلغ المستحق بالتحويل" else "المبلغ المستحق نقدًا", color = Mq.Muted)
                    Text(money(o.total), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                }
                ActionButton("أوافق على الإجمالي") { showApprove = true }
                ActionButton("لا أوافق (إلغاء الطلب)", tonal = true) { showDecline = true }
            }
        }
    }
    }

    if (showApprove) {
        ConfirmDialog(
            "تأكيد الموافقة",
            if (o.isTransfer) "ستحوّل ${money(o.total)} إلى حساب مقضي وترفع صورة الإيصال ليبدأ التجهيز. هل توافق؟"
            else "ستدفع ${money(o.total)} نقدًا عند الاستلام. هل توافق؟",
            "نعم، أوافق",
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
        ConfirmDialog("إلغاء الطلب", "هل تريد إلغاء الطلب؟", "نعم، ألغِ", onDismiss = { showCancel = false }) {
            Api.changeStatus(o.id, St.CANCELLED, "ألغاه الزبون")
        }
    }
}
