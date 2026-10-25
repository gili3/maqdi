package com.maqdi.partner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.LocalContext
import android.net.Uri
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.maqdi.core.Mq
import com.maqdi.core.ActionButton
import com.maqdi.core.Api
import com.maqdi.core.ApiException
import com.maqdi.core.ConfirmDialog
import com.maqdi.core.Empty
import com.maqdi.core.Field
import com.maqdi.core.InfoRow
import com.maqdi.core.Loadable
import com.maqdi.core.LoadableView
import com.maqdi.core.Order
import com.maqdi.core.Product
import com.maqdi.core.Profile
import com.maqdi.core.ReasonDialog
import com.maqdi.core.Repo
import com.maqdi.core.Screen
import com.maqdi.core.Section
import com.maqdi.core.St
import com.maqdi.core.StatusChip
import com.maqdi.core.StatusSteps
import com.maqdi.core.Stepper
import com.maqdi.core.dateTime
import com.maqdi.core.money
import com.maqdi.core.rememberLoadable
import com.maqdi.core.settlementLabel

// ---------- قائمة طلبات التاجر ----------
@Composable
fun MerchantOrdersScreen(profile: Profile, onOpen: (String) -> Unit) {
    val storeId = profile.storeId ?: ""
    var filter by remember { mutableStateOf("new") }
    val orders = rememberLoadable(storeId) { Repo.storeOrders(storeId) }
    Screen("طلبات المتجر") { pad ->
        Column(Modifier.padding(pad)) {
            if (storeId.isNotBlank() && profile.can("open_close")) StoreOpenSwitch(storeId)
            ChipRow(listOf("new" to "جديدة", "active" to "قيد التنفيذ", "done" to "المنتهية"), filter) { filter = it }
            LoadableView(orders) { list ->
                val shown = list.filter {
                    when (filter) {
                        "new" -> it.status == St.PENDING
                        "active" -> it.status in setOf(St.AWAITING, St.CONFIRMED, St.PREPARING, St.READY, St.OUT)
                        else -> it.status in St.terminal
                    }
                }
                if (shown.isEmpty()) Empty("لا توجد طلبات هنا.")
                else LazyColumn { items(shown, key = { it.id }) { PartnerOrderRow(it, showStore = false, onOpen = onOpen) } }
            }
        }
    }
}

// ---------- شاشة التوصيل (موصّل أو مدير) ----------
@Composable
fun CourierScreen(profile: Profile, onOpen: (String) -> Unit) {
    var filter by remember { mutableStateOf("ready") }
    val orders = rememberLoadable { Repo.courierOrders(profile.uid, profile.role == "admin") }
    Screen("التوصيل") { pad ->
        Column(Modifier.padding(pad)) {
            ChipRow(listOf("ready" to "جاهزة للاستلام", "out" to "قيد التوصيل"), filter) { filter = it }
            LoadableView(orders) { list ->
                val shown = list
                    .filter { if (filter == "ready") it.status == St.READY else it.status == St.OUT && (profile.role == "admin" || it.courierUid == profile.uid) }
                    .sortedBy { it.createdAt ?: 0L }
                if (shown.isEmpty()) Empty(if (filter == "ready") "لا توجد طلبات جاهزة الآن." else "لا توجد توصيلات جارية.")
                else LazyColumn { items(shown, key = { it.id }) { PartnerOrderRow(it, showStore = true, onOpen = onOpen) } }
            }
        }
    }
}

// ---------- تفاصيل الطلب المشتركة ----------
@Composable
fun PartnerOrderScreen(orderId: String, profile: Profile, onBack: () -> Unit) {
    val order = rememberLoadable(orderId) { Repo.order(orderId) }
    Screen("تفاصيل الطلب", onBack = onBack) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            LoadableView(order) { o -> if (o == null) Empty("الطلب غير موجود.") else PartnerOrderBody(o, profile) }
        }
    }
}

@Composable
private fun PartnerOrderBody(o: Order, profile: Profile) {
    val role = profile.role
    val merchantish = role == "merchant" || role == "admin"
    val courierish = role == "courier" || role == "admin"
    var editing by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) }

    val canCancel = when (role) {
        "merchant" -> o.status == St.CONFIRMED || o.status == St.PREPARING
        "admin" -> o.status in setOf(St.PENDING, St.AWAITING, St.CONFIRMED, St.PREPARING, St.READY)
        else -> false
    }

    if (editing) {
        ReviewEditor(o, onClose = { editing = false })
        return
    }

    Column(Modifier.fillMaxSize()) {
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Section(o.customerName.ifBlank { "الزبون" }) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(o.storeName)
                StatusChip(o.status)
            }
            StatusSteps(o.status)
            InfoRow("الهاتف", o.customerPhone)
            if (courierish) {
                Text("العنوان", color = Mq.Muted, modifier = Modifier.padding(top = 4.dp))
                Text(o.address, style = MaterialTheme.typography.titleMedium)
            } else {
                InfoRow("العنوان", o.address)
            }
            o.locationNote?.let { InfoRow("وصف الموقع", it) }
            InfoRow("وقت الطلب", dateTime(o.createdAt))
            if (courierish && o.customerPhone.isNotBlank() && (o.status == St.READY || o.status == St.OUT)) {
                val ctx = LocalContext.current
                Spacer(Modifier.height(8.dp))
                ActionButton("اتصال بالزبون", tonal = true) {
                    ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + o.customerPhone)))
                }
            }
            o.courierName?.let { InfoRow("الموصّل", it) }
            o.rejectReason?.let { Text("سبب الرفض: $it", color = Mq.Red) }
            o.cancelReason?.let { Text("سبب الإلغاء: $it", color = Mq.Red) }
            o.failReason?.let { Text("سبب تعذّر التسليم: $it", color = Mq.Red) }
        }
        Section("المنتجات") {
            o.shownLines.forEach { l ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${l.name} × ${l.qty}", Modifier.weight(1f))
                    Text(money(l.lineTotal))
                }
            }
        }
        Section(if (o.totalsAreEstimate) "الإجمالي (تقديري)" else "الإجمالي") {
            InfoRow("قيمة المنتجات", money(o.itemsTotal))
            InfoRow("رسوم التوصيل", money(o.deliveryFee))
            InfoRow("المطلوب من الزبون", money(o.total), bold = true)
            if (o.status == St.DELIVERED && role == "admin") {
                InfoRow("مستحق المتجر", money(o.payableAmount))
                InfoRow("التسوية", "${settlementLabel(o.settlementStatus)} (${money(o.settledAmount)})")
            }
        }

        val pad = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
        if (merchantish) {
            when (o.status) {
                St.PENDING -> {
                    ActionButton("مراجعة وتأكيد الأسعار والكميات", pad) { editing = true }
                    ActionButton("رفض الطلب", pad, tonal = true) { dialog = "reject" }
                }
                St.AWAITING -> Text("بانتظار موافقة الزبون على الإجمالي.", modifier = Modifier.padding(16.dp))
                St.CONFIRMED -> {
                    ActionButton("بدء التجهيز", pad) { Api.changeStatus(o.id, St.PREPARING) }
                    ActionButton("تعديل الطلب (يحتاج موافقة جديدة)", pad, tonal = true) { editing = true }
                }
                St.PREPARING -> {
                    ActionButton("جاهز للاستلام", pad) { Api.changeStatus(o.id, St.READY) }
                    ActionButton("تعديل الطلب (يحتاج موافقة جديدة)", pad, tonal = true) { editing = true }
                }
                else -> {}
            }
        }
        if (canCancel) ActionButton("إلغاء الطلب", pad, destructive = true) { dialog = "cancel" }
    }
    // إجراء المندوب الرئيسي ثابت أسفل الشاشة (استلام أو تسليم)، وصلاحيته تُفرض في الخادم
    if (courierish && (o.status == St.READY || o.status == St.OUT)) {
        Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (o.status == St.READY) {
                    ActionButton("استلمت الطلب من المتجر") { Api.changeStatus(o.id, St.OUT) }
                } else {
                    ActionButton("تم التسليم وتحصيل ${money(o.total)}") { dialog = "deliver" }
                    ActionButton("تعذّر التسليم", tonal = true) { dialog = "fail" }
                }
            }
        }
    }
    }

    when (dialog) {
        "reject" -> ReasonDialog("رفض الطلب", "تأكيد الرفض", { dialog = null }) { Api.changeStatus(o.id, St.REJECTED, it) }
        "cancel" -> ReasonDialog("إلغاء الطلب", "تأكيد الإلغاء", { dialog = null }) { Api.changeStatus(o.id, St.CANCELLED, it) }
        "fail" -> ReasonDialog("تعذّر التسليم", "تأكيد", { dialog = null }) { Api.changeStatus(o.id, St.FAILED, it) }
        "deliver" -> ConfirmDialog(
            "تأكيد التسليم", "هل استلمت ${money(o.total)} نقدًا من الزبون؟ لا يمكن التراجع.", "نعم، تم التحصيل",
            onDismiss = { dialog = null },
        ) { Api.changeStatus(o.id, St.DELIVERED) }
        else -> {}
    }
}

// ---------- محرر مراجعة التاجر ----------
private data class EditLine(val productId: String, val name: String, val qty: Int, val price: String)

@Composable
private fun ReviewEditor(o: Order, onClose: () -> Unit) {
    val lines = remember {
        mutableStateListOf<EditLine>().apply {
            o.shownLines.forEach { add(EditLine(it.productId, it.name, it.qty.toInt(), it.unitPrice.toString())) }
        }
    }
    var note by remember { mutableStateOf("") }
    var picking by remember { mutableStateOf(false) }
    val products = rememberLoadable(o.storeId) { Repo.products(o.storeId) }

    val active = lines.filter { it.qty > 0 }
    val pricesOk = active.all { it.price.trim().toLongOrNull()?.let { p -> p >= 0 } == true }
    val itemsTotal = active.sumOf { it.qty * (it.price.trim().toLongOrNull() ?: 0L) }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Section("راجع التوفر والأسعار والكميات") {
            Text("اجعل الكمية 0 لحذف منتج غير متوفر. أي تغيير يُرسل للزبون ليوافق عليه.", color = Mq.Muted)
            lines.forEachIndexed { i, l ->
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text(l.name, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Stepper(l.qty, { lines[i] = l.copy(qty = it) }, min = 0, max = 999)
                        Field("السعر", l.price, { lines[i] = l.copy(price = it.filter { c -> c.isDigit() }) },
                            Modifier.weight(1f).padding(start = 12.dp), keyboard = KeyboardType.Number)
                    }
                }
            }
            ActionButton("إضافة أو استبدال منتج", Modifier.fillMaxWidth().padding(top = 8.dp), tonal = true) { picking = !picking }
            if (picking) {
                LoadableView(products) { list ->
                    list.filter { p -> p.isAvailable && lines.none { it.productId == p.id } }.forEach { p: Product ->
                        ActionButton("${p.name} — ${money(p.price)}", Modifier.fillMaxWidth().padding(top = 4.dp), tonal = true) {
                            lines.add(EditLine(p.id, p.name, 1, p.price.toString()))
                            picking = false
                        }
                    }
                }
            }
        }
        Section("ملاحظة للزبون (اختياري)") { Field("ملاحظة", note, { note = it }, singleLine = false) }
        Section("الإجمالي الجديد") {
            InfoRow("قيمة المنتجات", money(itemsTotal))
            InfoRow("رسوم التوصيل", money(o.deliveryFee))
            InfoRow("المبلغ النهائي", money(itemsTotal + o.deliveryFee), bold = true)
        }
        val pad = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
        ActionButton("إرسال للزبون للموافقة", pad, enabled = active.isNotEmpty() && pricesOk) {
            if (active.isEmpty()) throw ApiException("أبقِ منتجًا واحدًا على الأقل، أو ارفض الطلب.")
            Api.proposeOrder(o.id, active.map { Triple(it.productId, it.qty, it.price.trim().toLong()) }, note.trim().ifBlank { null })
            onClose()
        }
        ActionButton("رجوع بدون تغيير", pad, tonal = true) { onClose() }
    }
}
