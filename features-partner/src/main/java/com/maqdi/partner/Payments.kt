package com.maqdi.partner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.maqdi.core.ActionButton
import com.maqdi.core.Api
import com.maqdi.core.EmptyState
import com.maqdi.core.Field
import com.maqdi.core.InfoRow
import com.maqdi.core.Loadable
import com.maqdi.core.LoadableView
import com.maqdi.core.Mq
import com.maqdi.core.MqCard
import com.maqdi.core.Order
import com.maqdi.core.PaymentProof
import com.maqdi.core.ReasonDialog
import com.maqdi.core.RefundRequest
import com.maqdi.core.Repo
import com.maqdi.core.Screen
import com.maqdi.core.St
import com.maqdi.core.StorageImage
import com.maqdi.core.dateTime
import com.maqdi.core.money
import com.maqdi.core.refundKindLabel
import com.maqdi.core.refundStatusLabel
import com.maqdi.core.rememberAction
import com.maqdi.core.rememberLoadable

// ---------- إيصالات التحويل بانتظار التأكيد (الإدارة المالية) ----------
@Composable
fun AdminPaymentsScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val orders = rememberLoadable { Repo.pendingPaymentOrders() }
    Screen("إيصالات التحويل", onBack = onBack) { pad ->
        Column(Modifier.padding(pad)) {
            LoadableView(orders) { list ->
                if (list.isEmpty()) EmptyState("لا توجد إيصالات بانتظار المراجعة", "تظهر هنا حين يرفع زبون إيصال تحويل.")
                else LazyColumn { items(list, key = { it.id }) { o -> PaymentReviewCard(o, onOpen) } }
            }
        }
    }
}

@Composable
private fun PaymentReviewCard(o: Order, onOpen: (String) -> Unit) {
    val proofL = rememberLoadable(o.id) { Repo.paymentProof(o.id) }
    val proof = (proofL as? Loadable.Data<PaymentProof?>)?.value
    var received by remember(proof?.amount) { mutableStateOf((proof?.amount ?: o.total).toString()) }
    var rejecting by remember { mutableStateOf(false) }
    val rv = received.toLongOrNull()

    MqCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(o.customerName.ifBlank { "الزبون" }, style = MaterialTheme.typography.titleMedium)
                Text(St.label(o.status), color = Mq.Muted, style = MaterialTheme.typography.bodySmall)
            }
            InfoRow("المتجر", o.storeName)
            InfoRow("إجمالي الطلب", money(o.total), bold = true)
            if (proof == null) {
                Text("تعذّر تحميل بيانات الإيصال.", color = Mq.Red)
            } else {
                InfoRow("مبلغ الإيصال", money(proof.amount))
                InfoRow("المحوِّل", proof.payerAccount)
                proof.transferRef?.let { InfoRow("رقم العملية", it) }
                InfoRow("وقت الرفع", dateTime(proof.submittedAt))
                StorageImage(proof.path, Modifier.fillMaxWidth().height(280.dp).padding(vertical = 6.dp), contentScale = ContentScale.Fit)
            }
            if (o.status in St.terminal) {
                Text("هذا الطلب انتهى (${St.label(o.status)}). عند التأكيد سيُسجَّل استرداد للزبون تلقائيًا.", color = Mq.Amber)
            }
            Field("المبلغ الذي وصل فعلًا إلى حسابك", received, { received = it.filter { c -> c.isDigit() } }, keyboard = KeyboardType.Number)
            ActionButton("تأكيد استلام المبلغ", Modifier.fillMaxWidth().padding(top = 4.dp), enabled = proof != null && rv != null && rv > 0) {
                Api.adminReviewPayment(o.id, true, rv, null)
            }
            ActionButton("رفض الإيصال", Modifier.fillMaxWidth(), tonal = true) { rejecting = true }
            TextButton(onClick = { onOpen(o.id) }) { Text("فتح الطلب") }
        }
    }
    if (rejecting) {
        ReasonDialog("رفض الإيصال", "تأكيد الرفض", { rejecting = false }) { Api.adminReviewPayment(o.id, false, null, it) }
    }
}

// ---------- طلبات الاسترداد (الإدارة المالية) ----------
@Composable
fun AdminRefundsScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    var filter by remember { mutableStateOf("pending") }
    val refunds = rememberLoadable(filter) { Repo.adminRefunds(filter) }
    Screen("طلبات الاسترداد", onBack = onBack) { pad ->
        Column(Modifier.padding(pad)) {
            ChipRow(listOf("pending" to "قيد المراجعة", "refunded" to "تم الاسترداد", "rejected" to "مرفوضة"), filter) { filter = it }
            LoadableView(refunds) { list ->
                if (list.isEmpty()) EmptyState("لا توجد طلبات هنا")
                else LazyColumn { items(list, key = { it.id }) { r -> RefundCard(r, onOpen) } }
            }
        }
    }
}

@Composable
private fun RefundCard(r: RefundRequest, onOpen: (String) -> Unit) {
    var paying by remember { mutableStateOf(false) }
    var rejecting by remember { mutableStateOf(false) }
    // الاسترداد التلقائي بلا حساب مكتوب: نعرض حساب المحوِّل من الإيصال
    val needsProofAccount = r.refundAccount.isNullOrBlank() && r.paymentMethod == "bank_transfer"
    val proofL = rememberLoadable(r.orderId, needsProofAccount) { if (needsProofAccount) Repo.paymentProof(r.orderId) else kotlinx.coroutines.flow.flowOf(null) }
    val proof = (proofL as? Loadable.Data<PaymentProof?>)?.value
    val account = r.refundAccount?.takeIf { it.isNotBlank() } ?: proof?.payerAccount

    MqCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(refundKindLabel(r.kind), style = MaterialTheme.typography.titleMedium)
                Text(refundStatusLabel(r.status), color = if (r.status == "rejected") Mq.Red else if (r.status == "refunded") Mq.Green else Mq.Amber)
            }
            InfoRow("الزبون", r.customerName.ifBlank { "—" })
            InfoRow("المتجر", r.storeName)
            InfoRow("المبلغ", money(r.approvedAmount ?: r.amount), bold = true)
            InfoRow("السبب", r.reason)
            InfoRow("حساب الزبون للاستلام", account ?: "غير محدد")
            InfoRow("التاريخ", dateTime(r.createdAt))
            r.reference?.takeIf { it.isNotBlank() }?.let { InfoRow("رقم عملية الاسترداد", it) }
            if (r.chargedToStore > 0) InfoRow("خُصم من مستحق المتجر", money(r.chargedToStore))
            r.adminNote?.takeIf { it.isNotBlank() }?.let { InfoRow("ملاحظة الإدارة", it) }
            if (r.status == "pending") {
                ActionButton("حوّلتُ المبلغ للزبون", Modifier.fillMaxWidth().padding(top = 4.dp)) { paying = true }
                ActionButton("رفض الطلب", Modifier.fillMaxWidth(), tonal = true) { rejecting = true }
            }
            TextButton(onClick = { onOpen(r.orderId) }) { Text("فتح الطلب") }
        }
    }
    if (paying) RefundPayDialog(r, onDismiss = { paying = false })
    if (rejecting) {
        ReasonDialog("رفض طلب الاسترداد", "تأكيد الرفض", { rejecting = false }) { Api.adminResolveRefund(r.id, false, null, null, it, false) }
    }
}

@Composable
private fun RefundPayDialog(r: RefundRequest, onDismiss: () -> Unit) {
    var amount by remember { mutableStateOf(r.amount.toString()) }
    var reference by remember { mutableStateOf("") }
    var chargeStore by remember { mutableStateOf(false) }
    val st = rememberAction()
    val a = amount.toLongOrNull()
    val valid = a != null && a in 1..r.amount && reference.trim().length >= 3
    AlertDialog(
        onDismissRequest = { if (!st.busy) onDismiss() },
        title = { Text("تسجيل الاسترداد") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("حوّل المبلغ إلى حساب الزبون من بنكك أولًا، ثم سجّل رقم العملية هنا.", color = Mq.Muted)
                Field("المبلغ المحوَّل (حتى ${money(r.amount)})", amount, { amount = it.filter { c -> c.isDigit() } }, keyboard = KeyboardType.Number)
                Field("رقم عملية التحويل", reference, { reference = it })
                if (r.kind == "customer") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("خصمه من مستحق المتجر", Modifier.weight(1f))
                        Switch(checked = chargeStore, onCheckedChange = { chargeStore = it })
                    }
                    Text(
                        if (chargeStore) "سيُخصم من مستحق المتجر عليك (بحد أقصى مستحقه من هذا الطلب)، وما زاد تتحمله المنصة."
                        else "ستتحمل المنصة المبلغ كله ويُخصم من صافي إيرادها.",
                        color = Mq.Muted, style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            ActionButton("تأكيد الاسترداد", modifier = Modifier, enabled = valid, state = st) {
                Api.adminResolveRefund(r.id, true, a, reference.trim(), null, chargeStore)
                onDismiss()
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !st.busy) { Text("رجوع") } },
    )
}
