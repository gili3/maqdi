package com.maqdi.core

import com.google.firebase.firestore.DocumentSnapshot

data class Profile(
    val uid: String, val email: String, val name: String, val phone: String?, val role: String,
    val storeId: String?, val status: String, val savedAddress: String?, val isTestAccount: Boolean,
)

data class Store(
    val id: String, val name: String, val category: String, val phone: String,
    val address: String?, val status: String, val isOpen: Boolean,
)

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
) {
    /** الأسطر المعروضة: اقتراح التاجر إن وُجد وإلا ما طلبه الزبون */
    val shownLines: List<Line> get() = proposal?.items ?: requestedItems
}

data class Settings(
    val deliveryFee: Long = 4000, val merchantReviewMinutes: Long = 15,
    val customerApprovalMinutes: Long = 30, val ordersEnabled: Boolean = true,
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
    val storeId: String?, val isTestAccount: Boolean,
)

// ---------- التحويل من Firestore ----------
private fun DocumentSnapshot.lng(f: String): Long = getLong(f) ?: 0L
private fun DocumentSnapshot.time(f: String): Long? = getTimestamp(f)?.toDate()?.time

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
)

fun DocumentSnapshot.toStore() = Store(
    id = id, name = getString("name") ?: "", category = getString("category") ?: "other",
    phone = getString("phone") ?: "", address = getString("address"),
    status = getString("status") ?: "active", isOpen = getBoolean("isOpen") ?: false,
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
    )
}

fun DocumentSnapshot.toSettings() = Settings(
    deliveryFee = getLong("deliveryFee") ?: 4000, merchantReviewMinutes = getLong("merchantReviewMinutes") ?: 15,
    customerApprovalMinutes = getLong("customerApprovalMinutes") ?: 30, ordersEnabled = getBoolean("ordersEnabled") ?: true,
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
)
