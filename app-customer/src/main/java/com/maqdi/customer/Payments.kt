package com.maqdi.customer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.maqdi.core.ActionButton
import com.maqdi.core.Api
import com.maqdi.core.Field
import com.maqdi.core.ImageUtil
import com.maqdi.core.InfoRow
import com.maqdi.core.Loadable
import com.maqdi.core.Mq
import com.maqdi.core.Order
import com.maqdi.core.PaymentProof
import com.maqdi.core.RefundRequest
import com.maqdi.core.Repo
import com.maqdi.core.Section
import com.maqdi.core.Settings
import com.maqdi.core.St
import com.maqdi.core.StorageImage
import com.maqdi.core.dateTime
import com.maqdi.core.minutesLeft
import com.maqdi.core.money
import com.maqdi.core.paymentMethodLabel
import com.maqdi.core.refundKindLabel
import com.maqdi.core.refundStatusLabel
import com.maqdi.core.rememberAction
import com.maqdi.core.rememberLoadable
import java.util.UUID

/** خيار اختيار طريقة الدفع في السلة */
@Composable
fun PayOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, modifier = Modifier.padding(start = 4.dp))
    }
}

private fun copyText(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("account", text))
}

/**
 * قسم الدفع بالتحويل في تفاصيل الطلب:
 * قبل موافقة الزبون: تنبيه فقط. بعد الموافقة: بيانات الحساب ورفع الإيصال. بعد الرفع: بانتظار الإدارة. بعد التأكيد: تم الدفع.
 */
@Composable
fun PaymentSection(o: Order, s: Settings) {
    if (!o.isTransfer) return
    val ctx = LocalContext.current
    val proofL = rememberLoadable(o.id, o.paymentStatus) { Repo.paymentProof(o.id) }
    val proof = (proofL as? Loadable.Data<PaymentProof?>)?.value

    var picked by remember { mutableStateOf<Uri?>(null) }
    var payer by remember(proof?.payerAccount) { mutableStateOf(proof?.payerAccount ?: "") }
    var ref by remember { mutableStateOf("") }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) picked = uri }

    when {
        o.paymentStatus == "verified" -> Section("الدفع بالتحويل") {
            InfoRow("المبلغ المستلَم", money(o.paidAmount), bold = true)
            if (o.status !in St.terminal) {
                if (o.cashDue > 0) Text("المتبقي نقدًا عند الاستلام: ${money(o.cashDue)}", color = Mq.Muted)
                else if (o.paidAmount > o.total) Text("دفعتَ أكثر من الإجمالي، وسيُرد لك الفرق ${money(o.paidAmount - o.total)}.", color = Mq.Muted)
            }
        }

        o.paymentStatus == "proof_submitted" -> Section("الدفع بالتحويل") {
            Text("أرسلتَ الإيصال وهو قيد مراجعة الإدارة. سيبدأ التجهيز بعد تأكيد استلام المبلغ.")
            if (proof != null) {
                InfoRow("المبلغ", money(proof.amount))
                StorageImage(proof.path, Modifier.fillMaxWidth().height(220.dp).padding(top = 8.dp), contentScale = ContentScale.Fit)
            }
        }

        o.status == St.CONFIRMED && o.paymentStatus == "awaiting_payment" -> Section("ادفع بالتحويل البنكي") {
            Text("حوّل ${money(o.total)} إلى الحساب التالي ثم أرفق صورة الإيصال ليبدأ التجهيز.")
            InfoRow("البنك", s.transferBankName)
            InfoRow("رقم الحساب", s.transferAccountNumber, bold = true)
            InfoRow("اسم صاحب الحساب", s.transferAccountName)
            TextButton(onClick = { copyText(ctx, s.transferAccountNumber) }) { Text("نسخ رقم الحساب") }
            if (s.transferNote.isNotBlank()) Text(s.transferNote, color = Mq.Muted)
            minutesLeft(o.expiresAt)?.let { Text("مهلة الدفع: نحو $it دقيقة، وبعدها ينتهي الطلب.", color = Mq.Amber, modifier = Modifier.padding(top = 4.dp)) }
            o.paymentRejectReason?.let { Text("لم يُقبل الإيصال السابق: $it", color = Mq.Red, modifier = Modifier.padding(top = 4.dp)) }

            Field("اسم أو رقم الحساب الذي حوّلت منه", payer, { payer = it }, Modifier.fillMaxWidth().padding(top = 8.dp))
            Field("رقم العملية (اختياري)", ref, { ref = it }, Modifier.fillMaxWidth().padding(top = 8.dp))
            ActionButton(
                if (picked == null) "اختيار صورة الإيصال" else "تم اختيار الصورة (اضغط لتغييرها)",
                Modifier.fillMaxWidth().padding(top = 8.dp), tonal = true,
            ) { pick.launch("image/*") }
            ActionButton(
                "إرسال الإيصال", Modifier.fillMaxWidth().padding(top = 8.dp),
                enabled = picked != null && payer.trim().length >= 3,
            ) {
                val uri = picked ?: throw com.maqdi.core.ApiException("اختر صورة الإيصال أولًا.")
                val path = ImageUtil.uploadPaymentProof(ctx, o.id, uri)
                Api.submitTransferProof(o.id, path, payer.trim(), ref.trim().ifBlank { null })
            }
        }

        o.status in setOf(St.PENDING, St.AWAITING) -> Section("الدفع بالتحويل") {
            Text("بعد موافقتك على الإجمالي النهائي ستحوّل المبلغ وترفع صورة الإيصال، ثم يبدأ التجهيز.")
        }

        else -> {}
    }
}

/** سجل طلبات الاسترداد لهذا الطلب + زر «طلب استرداد» بعد التسليم وضمن المهلة */
@Composable
fun RefundSection(o: Order, s: Settings) {
    val refundsL = rememberLoadable(o.id, o.refundOpenId, o.refundedAmount) { Repo.refundsForOrder(o.customerId, o.id) }
    val refunds = (refundsL as? Loadable.Data<List<RefundRequest>>)?.value ?: emptyList()
    var showRequest by remember { mutableStateOf(false) }
    val delivered = o.deliveredAt
    val left = o.total - o.refundedAmount
    val canRequest = o.status == St.DELIVERED && o.refundOpenId == null && delivered != null &&
        System.currentTimeMillis() - delivered <= s.refundWindowHours * 3_600_000L && left > 0

    if (refunds.isEmpty() && !canRequest) return
    Section("الاسترداد") {
        refunds.forEach { r ->
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(refundKindLabel(r.kind))
                    Text(refundStatusLabel(r.status), color = if (r.status == "rejected") Mq.Red else if (r.status == "refunded") Mq.Green else Mq.Amber)
                }
                Text(money(r.approvedAmount ?: r.amount) + " • " + dateTime(r.createdAt), color = Mq.Muted, style = MaterialTheme.typography.bodySmall)
                if (r.status == "refunded" && !r.reference.isNullOrBlank()) Text("رقم عملية التحويل إليك: ${r.reference}", style = MaterialTheme.typography.bodySmall)
                if (r.status == "rejected" && !r.adminNote.isNullOrBlank()) Text("السبب: ${r.adminNote}", color = Mq.Red, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (canRequest) {
            Text("إن كان في طلبك مشكلة يمكنك طلب استرداد خلال ${s.refundWindowHours} ساعة من التسليم.", color = Mq.Muted)
            ActionButton("طلب استرداد", Modifier.fillMaxWidth().padding(top = 8.dp), tonal = true) { showRequest = true }
        }
    }
    if (showRequest) RefundDialog(o, left, onDismiss = { showRequest = false })
}

@Composable
private fun RefundDialog(o: Order, maxAmount: Long, onDismiss: () -> Unit) {
    // المفتاح ثابت طوال عمر النافذة: إعادة المحاولة بعد انقطاع الاتصال لا تنشئ طلبًا مكررًا
    val key = remember { UUID.randomUUID().toString().replace("-", "") }
    var amount by remember { mutableStateOf(maxAmount.toString()) }
    var reason by remember { mutableStateOf("") }
    var account by remember { mutableStateOf("") }
    val st = rememberAction()
    val a = amount.toLongOrNull()
    val valid = a != null && a in 1..maxAmount && reason.trim().length >= 3 && account.trim().length >= 3
    AlertDialog(
        onDismissRequest = { if (!st.busy) onDismiss() },
        title = { Text("طلب استرداد") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("المبلغ المطلوب (حتى ${money(maxAmount)})", amount, { amount = it.filter { c -> c.isDigit() } }, keyboard = KeyboardType.Number)
                Field("سبب الطلب", reason, { reason = it }, singleLine = false)
                Field("حسابك لاستلام المبلغ (بنك ورقم الحساب)", account, { account = it }, singleLine = false)
            }
        },
        confirmButton = {
            ActionButton("إرسال الطلب", modifier = Modifier, enabled = valid, state = st) {
                Api.createRefundRequest(o.id, a ?: 0L, reason.trim(), account.trim(), key)
                onDismiss()
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !st.busy) { Text("رجوع") } },
    )
}

/** وصف مختصر لطريقة الدفع في قائمة التفاصيل */
fun payLine(o: Order) = paymentMethodLabel(o.paymentMethod)
