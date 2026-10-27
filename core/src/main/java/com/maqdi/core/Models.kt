package com.maqdi.core

import com.google.firebase.firestore.DocumentSnapshot

data class Profile(
    val uid: String, val email: String, val name: String, val phone: String?, val role: String,
    val storeId: String?, val status: String, val savedAddress: String?, val isTestAccount: Boolean,
    /** null = كل صلاحيات الدور (الافتراضي) */
    val permissions: List<String>? = null,
) {
    /** هل يملك هذا الحساب الصلاحية؟ الزبون بلا صلاحيات إدارية */
    fun can(p: String): Boolean = Permissions.has(role, permissions, p)
}

data class Store(
    val id: String, val name: String, val category: String, val phone: String,
    val address: String?, val status: String, val isOpen: Boolean, val imagePath: String? = null,
)

data class Category(val id: String, val name: String, val order: Long, val isActive: Boolean)

data class Product(
    val id: String, val storeId: String, val name: String, val description: String?,
    val category: String?, val price: Long, val stockQty: Long?, val isAvailable: Boolean, val imagePath: String?,
)

data class Line(val productId: String, val name: String, val unitPrice: Long, val qty: Long, val lineTotal: Long)

data class Proposal(
    val items: List<Line>, val itemsTotal: Long, val deliveryFee: Long, val total: Long,
    val note: String?, val revision: Long,
)

data class Order(
    val id: String, val customerId: String, val customerName: String, val customerPhone: String,
    val storeId: String, val storeName: String, val status: String,
    val requestedItems: List<Line>, val proposal: Proposal?, val approvedRevision: Long?, val revision: Long,
    val itemsTotal: Long, val deliveryFee: Long, val total: Long, val totalsAreEstimate: Boolean,
    val address: String, val locationNote: String?, val createdAt: Long?, val expiresAt: Long?,
    val rejectReason: String?, val cancelReason: String?, val failReason: String?,
    val settlementStatus: String, val payableAmount: Long, val settledAmount: Long,
    val courierName: String?, val courierUid: String?, val isTest: Boolean,
    val preparingAt: Long? = null,
    val courierPhone: String? = null,
    val paymentMethod: String = "cash_on_delivery",
    val paymentStatus: String = "not_required",
    val paidAmount: Long = 0,
    val paymentRejectReason: String? = null,
    val deliveredAt: Long? = null,
    val refundedAmount: Long = 0,
    val refundOpenId: String? = null,
) {
    /** الأسطر المعروضة: اقتراح التاجر إن وُجد وإلا ما طلبه الزبون */
    val shownLines: List<Line> get() = proposal?.items ?: requestedItems

    val isTransfer: Boolean get() = paymentMethod == "bank_transfer"

    /** المطلوب نقدًا عند التسليم: الإجمالي ناقص ما أُكّد استلامه بالتحويل (نقدًا: الإجمالي كله) */
    val cashDue: Long get() = (total - paidAmount.coerceAtLeast(0)).coerceAtLeast(0)
}

data class Settings(
    val deliveryFee: Long = 4000, val merchantReviewMinutes: Long = 15,
    val customerApprovalMinutes: Long = 30, val ordersEnabled: Boolean = true,
    val maxOpenOrders: Long = 3, val customerCancelGraceMinutes: Long = 3,
    val courierCommission: Long = 0, val courierPickupMinutes: Long = 10,
    val transferEnabled: Boolean = false, val transferBankName: String = "", val transferAccountNumber: String = "",
    val transferAccountName: String = "", val transferNote: String = "",
    val paymentWindowMinutes: Long = 60, val refundWindowHours: Long = 48,
)

/** إثبات التحويل (يراه الزبون صاحبه والإدارة المالية فقط) */
data class PaymentProof(
    val orderId: String, val path: String, val transferRef: String?, val payerAccount: String, val amount: Long,
    val status: String, val rejectReason: String?, val submittedAt: Long?,
)

data class RefundRequest(
    val id: String, val orderId: String, val customerName: String, val storeName: String,
    val kind: String, val source: String, val amount: Long, val approvedAmount: Long?, val reason: String,
    val refundAccount: String?, val status: String, val reference: String?, val adminNote: String?,
    val chargedToStore: Long, val createdAt: Long?, val resolvedAt: Long?, val paymentMethod: String,
)

data class AppNotification(
    val id: String, val title: String, val body: String, val orderId: String?, val createdAt: Long?, val read: Boolean,
)

data class StoreBalance(val storeId: String, val payable: Long, val settled: Long) {
    val outstanding: Long get() = payable - settled
}

data class Entry(
    val id: String, val orderId: String?, val storeId: String?, val ledger: String, val type: String,
    val amount: Long, val reversesEntryId: String?, val note: String?, val createdAt: Long?,
)

data class AuditLog(
    val id: String, val actorUid: String, val actorRole: String, val action: String,
    val targetType: String, val targetId: String, val at: Long?,
)

data class Expense(val id: String, val amount: Long, val category: String, val note: String?, val createdAt: Long?)

data class UserRow(
    val uid: String, val email: String, val name: String, val role: String, val status: String,
    val storeId: String?, val isTestAccount: Boolean, val permissions: List<String>? = null, val phone: String? = null,
) {
    /** ما يُعرض للتعريف بالمستخدم: البريد، أو الرقم لحسابات الهاتف */
    val contact: String get() = email.ifBlank { phone.orEmpty() }
}

// ---------- التحويل من Firestore ----------
private fun DocumentSnapshot.lng(f: String): Long = getLong(f) ?: 0L
private fun DocumentSnapshot.time(f: String): Long? = getTimestamp(f)?.toDate()?.time

/** قائمة نصوص من حقل؛ null إن كان الحقل غائبًا أو null (يعني: الافتراضي) */
private fun DocumentSnapshot.strList(f: String): List<String>? =
    (get(f) as? List<*>)?.filterIsInstance<String>()

private fun num(v: Any?): Long = (v as? Number)?.toLong() ?: 0L

@Suppress("UNCHECKED_CAST")
private fun parseLines(raw: Any?): List<Line> =
    (raw as? List<Map<String, Any?>>)?.map { m ->
        Line(
            productId = m["productId"] as? String ?: "",
            name = m["name"] as? String ?: "",
            unitPrice = num(m["unitPrice"]), qty = num(m["qty"]), lineTotal = num(m["lineTotal"]),
        )
    } ?: emptyList()

@Suppress("UNCHECKED_CAST")
private fun parseProposal(raw: Any?): Proposal? {
    val m = raw as? Map<String, Any?> ?: return null
    return Proposal(
        items = parseLines(m["items"]), itemsTotal = num(m["itemsTotal"]), deliveryFee = num(m["deliveryFee"]),
        total = num(m["total"]), note = m["note"] as? String, revision = num(m["revision"]),
    )
}

fun DocumentSnapshot.toProfile() = Profile(
    uid = id, email = getString("email") ?: "", name = getString("name") ?: "", phone = getString("phone"),
    role = getString("role") ?: "customer", storeId = getString("storeId"), status = getString("status") ?: "active",
    savedAddress = getString("savedAddress"), isTestAccount = getBoolean("isTestAccount") ?: false,
    permissions = strList("permissions"),
)

fun DocumentSnapshot.toStore() = Store(
    id = id, name = getString("name") ?: "", category = getString("category") ?: "other",
    phone = getString("phone") ?: "", address = getString("address"),
    status = getString("status") ?: "active", isOpen = getBoolean("isOpen") ?: false, imagePath = getString("imagePath"),
)

fun DocumentSnapshot.toCategory() = Category(
    id = id, name = getString("name") ?: "", order = getLong("order") ?: 0L, isActive = getBoolean("isActive") ?: true,
)

fun DocumentSnapshot.toProduct() = Product(
    id = id, storeId = getString("storeId") ?: "", name = getString("name") ?: "",
    description = getString("description"), category = getString("category"), price = lng("price"),
    stockQty = getLong("stockQty"), isAvailable = getBoolean("isAvailable") ?: false, imagePath = getString("imagePath"),
)

fun DocumentSnapshot.toOrder(): Order {
    val approved = (get("approvedSnapshot") as? Map<*, *>)?.get("revision") as? Number
    return Order(
        id = id, customerId = getString("customerId") ?: "", customerName = getString("customerName") ?: "",
        customerPhone = getString("customerPhone") ?: "", storeId = getString("storeId") ?: "",
        storeName = getString("storeName") ?: "", status = getString("status") ?: "",
        requestedItems = parseLines(get("requestedItems")), proposal = parseProposal(get("proposal")),
        approvedRevision = approved?.toLong(), revision = lng("revision"),
        itemsTotal = lng("itemsTotal"), deliveryFee = lng("deliveryFee"), total = lng("total"),
        totalsAreEstimate = getBoolean("totalsAreEstimate") ?: true,
        address = getString("address") ?: "", locationNote = getString("locationNote"),
        createdAt = time("createdAt"), expiresAt = time("expiresAt"),
        rejectReason = getString("rejectReason"), cancelReason = getString("cancelReason"), failReason = getString("failReason"),
        settlementStatus = getString("settlementStatus") ?: "unsettled",
        payableAmount = lng("payableAmount"), settledAmount = lng("settledAmount"),
        courierName = getString("courierName"), courierUid = getString("courierUid"), isTest = getBoolean("isTest") ?: false,
        preparingAt = time("preparingAt"),
        courierPhone = getString("courierPhone"),
        paymentMethod = getString("paymentMethod") ?: "cash_on_delivery",
        paymentStatus = getString("paymentStatus") ?: "not_required",
        paidAmount = lng("paidAmount"), paymentRejectReason = getString("paymentRejectReason"),
        deliveredAt = time("deliveredAt"), refundedAmount = lng("refundedAmount"), refundOpenId = getString("refundOpenId"),
    )
}

fun DocumentSnapshot.toSettings() = Settings(
    deliveryFee = getLong("deliveryFee") ?: 4000, merchantReviewMinutes = getLong("merchantReviewMinutes") ?: 15,
    customerApprovalMinutes = getLong("customerApprovalMinutes") ?: 30, ordersEnabled = getBoolean("ordersEnabled") ?: true,
    maxOpenOrders = getLong("maxOpenOrders") ?: 3, customerCancelGraceMinutes = getLong("customerCancelGraceMinutes") ?: 3,
    courierCommission = getLong("courierCommission") ?: 0, courierPickupMinutes = getLong("courierPickupMinutes") ?: 10,
    transferEnabled = getBoolean("transferEnabled") ?: false, transferBankName = getString("transferBankName") ?: "",
    transferAccountNumber = getString("transferAccountNumber") ?: "", transferAccountName = getString("transferAccountName") ?: "",
    transferNote = getString("transferNote") ?: "",
    paymentWindowMinutes = getLong("paymentWindowMinutes") ?: 60, refundWindowHours = getLong("refundWindowHours") ?: 48,
)

fun DocumentSnapshot.toPaymentProof() = PaymentProof(
    orderId = getString("orderId") ?: id, path = getString("path") ?: "", transferRef = getString("transferRef"),
    payerAccount = getString("payerAccount") ?: "", amount = lng("amount"), status = getString("status") ?: "submitted",
    rejectReason = getString("rejectReason"), submittedAt = time("submittedAt"),
)

fun DocumentSnapshot.toRefund() = RefundRequest(
    id = id, orderId = getString("orderId") ?: "", customerName = getString("customerName") ?: "",
    storeName = getString("storeName") ?: "", kind = getString("kind") ?: "customer", source = getString("source") ?: "customer",
    amount = lng("amount"), approvedAmount = getLong("approvedAmount"), reason = getString("reason") ?: "",
    refundAccount = getString("refundAccount"), status = getString("status") ?: "pending", reference = getString("reference"),
    adminNote = getString("adminNote"), chargedToStore = lng("chargedToStore"), createdAt = time("createdAt"),
    resolvedAt = time("resolvedAt"), paymentMethod = getString("paymentMethod") ?: "cash_on_delivery",
)

fun DocumentSnapshot.toNotification() = AppNotification(
    id = id, title = getString("title") ?: "", body = getString("body") ?: "", orderId = getString("orderId"),
    createdAt = time("createdAt"), read = getBoolean("read") ?: false,
)

fun DocumentSnapshot.toBalance() = StoreBalance(storeId = id, payable = lng("payable"), settled = lng("settled"))

fun DocumentSnapshot.toEntry() = Entry(
    id = id, orderId = getString("orderId"), storeId = getString("storeId"), ledger = getString("ledger") ?: "",
    type = getString("type") ?: "", amount = lng("amount"), reversesEntryId = getString("reversesEntryId"),
    note = getString("note"), createdAt = time("createdAt"),
)

fun DocumentSnapshot.toAudit() = AuditLog(
    id = id, actorUid = getString("actorUid") ?: "", actorRole = getString("actorRole") ?: "",
    action = getString("action") ?: "", targetType = getString("targetType") ?: "",
    targetId = getString("targetId") ?: "", at = time("at"),
)

fun DocumentSnapshot.toExpense() = Expense(
    id = id, amount = lng("amount"), category = getString("category") ?: "", note = getString("note"), createdAt = time("createdAt"),
)

fun DocumentSnapshot.toUserRow() = UserRow(
    uid = id, email = getString("email") ?: "", name = getString("name") ?: "", role = getString("role") ?: "",
    status = getString("status") ?: "", storeId = getString("storeId"), isTestAccount = getBoolean("isTestAccount") ?: false,
    permissions = strList("permissions"), phone = getString("phone"),
)

