package com.maqdi.core

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

// وثيقة مشوّهة (حقل بنوع غير متوقع) تُتجاوز بدل أن يسقط التطبيق كله؛ التحويل يجري داخل مستمع Firestore
private fun <T> Query.listFlow(map: (DocumentSnapshot) -> T): Flow<List<T>> = callbackFlow {
    val reg = addSnapshotListener { snap, err ->
        if (err != null) {
            close(err)
        } else {
            val rows = snap?.documents?.mapNotNull { d ->
                try { map(d) } catch (e: Exception) { android.util.Log.w("Repo", "skip malformed doc ${d.reference.path}", e); null }
            } ?: emptyList()
            trySend(rows)
        }
    }
    awaitClose { reg.remove() }
}

private fun <T> DocumentReference.docFlow(map: (DocumentSnapshot) -> T): Flow<T?> = callbackFlow {
    val reg = addSnapshotListener { snap, err ->
        if (err != null) {
            close(err)
        } else {
            try {
                trySend(if (snap != null && snap.exists()) map(snap) else null)
            } catch (e: Exception) {
                close(e) // يظهر كخطأ قابل لإعادة المحاولة بدل انهيار التطبيق
            }
        }
    }
    awaitClose { reg.remove() }
}

/** قراءة البيانات فقط. كل الكتابات الحساسة تمر عبر Api. */
object Repo {
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()
    private val desc = Query.Direction.DESCENDING

    fun profile(uid: String) = db.document("users/$uid").docFlow { it.toProfile() }
    fun settings(): Flow<Settings> = callbackFlow {
        val reg = db.document("settings/app").addSnapshotListener { snap, err ->
            if (err != null) close(err) else trySend(if (snap != null && snap.exists()) snap.toSettings() else Settings())
        }
        awaitClose { reg.remove() }
    }

    fun activeStores() = db.collection("stores").whereEqualTo("status", "active").listFlow { it.toStore() }
    fun allStores() = db.collection("stores").listFlow { it.toStore() }
    fun store(id: String) = db.document("stores/$id").docFlow { it.toStore() }
    fun products(storeId: String) = db.collection("products").whereEqualTo("storeId", storeId).listFlow { it.toProduct() }

    /** قراءة واحدة (بلا مستمع حيّ) للمنتجات المتاحة، لتغذية البحث العام بأقل تكلفة قراءات */
    fun availableProductsOnce(): Flow<List<Product>> = flow {
        emit(db.collection("products").whereEqualTo("isAvailable", true).limit(400).get().await().documents.map { it.toProduct() })
    }

    fun categories() = db.collection("categories").listFlow { it.toCategory() }

    fun order(id: String) = db.document("orders/$id").docFlow { it.toOrder() }
    fun customerOrders(uid: String) =
        db.collection("orders").whereEqualTo("customerId", uid).orderBy("createdAt", desc).limit(100).listFlow { it.toOrder() }
    fun storeOrders(storeId: String) =
        db.collection("orders").whereEqualTo("storeId", storeId).orderBy("createdAt", desc).limit(100).listFlow { it.toOrder() }
    fun adminOrders(status: String?, storeId: String?, fromMs: Long?, toMs: Long?): Flow<List<Order>> {
        var q: Query = db.collection("orders")
        if (status != null) q = q.whereEqualTo("status", status)
        if (storeId != null) q = q.whereEqualTo("storeId", storeId)
        if (fromMs != null) q = q.whereGreaterThanOrEqualTo("createdAt", Timestamp(java.util.Date(fromMs)))
        if (toMs != null) q = q.whereLessThan("createdAt", Timestamp(java.util.Date(toMs)))
        return q.orderBy("createdAt", desc).limit(200).listFlow { it.toOrder() }
    }
    /** الجاهزة للاستلام (يراها كل موصّل) + توصيلات هذا الموصّل الجارية فقط؛ المدير يرى الحالتين */
    fun courierOrders(uid: String, isAdmin: Boolean): Flow<List<Order>> =
        if (isAdmin) {
            db.collection("orders").whereIn("status", listOf(St.READY, St.OUT)).listFlow { it.toOrder() }
        } else {
            combine(
                db.collection("orders").whereEqualTo("status", St.READY).listFlow { it.toOrder() },
                db.collection("orders").whereEqualTo("courierUid", uid).whereEqualTo("status", St.OUT).listFlow { it.toOrder() },
            ) { ready, mine -> ready + mine }
        }

    // ---------- الدفع بالتحويل والاسترداد ----------
    fun paymentProof(orderId: String) = db.document("payment_proofs/$orderId").docFlow { it.toPaymentProof() }

    /** طلبات الزبون الواحد لطلب بعينه (شرطا مساواة فقط: بلا فهرس مركّب، والقاعدة تشترط customerId) */
    fun refundsForOrder(uid: String, orderId: String) =
        db.collection("refund_requests").whereEqualTo("customerId", uid).whereEqualTo("orderId", orderId)
            .listFlow { it.toRefund() }.map { l -> l.sortedByDescending { it.createdAt ?: 0L } }

    /** للإدارة المالية: حسب الحالة (pending/refunded/rejected)؛ الترتيب في التطبيق لتفادي فهرس مركّب */
    fun adminRefunds(status: String) =
        db.collection("refund_requests").whereEqualTo("status", status).limit(100)
            .listFlow { it.toRefund() }.map { l -> l.sortedByDescending { it.createdAt ?: 0L } }

    /** الطلبات التي رفع زبونها إيصال تحويل وتنتظر تأكيد الإدارة */
    fun pendingPaymentOrders() =
        db.collection("orders").whereEqualTo("paymentStatus", "proof_submitted").limit(100)
            .listFlow { it.toOrder() }.map { l -> l.sortedBy { it.createdAt ?: 0L } }

    fun balance(storeId: String) = db.document("store_balances/$storeId").docFlow { it.toBalance() }
    fun balances() = db.collection("store_balances").listFlow { it.toBalance() }
    fun storeLedger(storeId: String) =
        db.collection("financial_entries").whereEqualTo("storeId", storeId).whereEqualTo("ledger", "store_payable")
            .orderBy("createdAt", desc).limit(100).listFlow { it.toEntry() }
    fun allEntries(storeId: String) =
        db.collection("financial_entries").whereEqualTo("storeId", storeId).orderBy("createdAt", desc).limit(100).listFlow { it.toEntry() }

    fun notifications(uid: String) =
        db.collection("users/$uid/notifications").orderBy("createdAt", desc).limit(50).listFlow { it.toNotification() }

    /** عدد غير المقروء (حتى 10) لعرضه على الجرس */
    fun unreadCount(uid: String): Flow<Int> =
        db.collection("users/$uid/notifications").whereEqualTo("read", false).limit(10).listFlow { 1 }.map { it.size }

    suspend fun markRead(uid: String, id: String) {
        db.document("users/$uid/notifications/$id").update("read", true).await()
    }

    suspend fun markAllRead(uid: String, ids: List<String>) {
        if (ids.isEmpty()) return
        val batch = db.batch()
        ids.forEach { batch.update(db.document("users/$uid/notifications/$it"), "read", true) }
        batch.commit().await()
    }

    /** توصيلات المندوب (التي استلمها) */
    fun courierDeliveries(uid: String) =
        db.collection("orders").whereEqualTo("courierUid", uid).orderBy("createdAt", desc).limit(100).listFlow { it.toOrder() }

    fun users() = db.collection("users").limit(200).listFlow { it.toUserRow() }
    fun audit() = db.collection("audit_logs").orderBy("at", desc).limit(100).listFlow { it.toAudit() }
    fun expenses() = db.collection("expenses").orderBy("createdAt", desc).limit(50).listFlow { it.toExpense() }

    /** الزبون يعدّل بياناته الشخصية فقط (تسمح القواعد بهذه الحقول) */
    suspend fun updateProfile(uid: String, name: String, phone: String?, savedAddress: String?) {
        // بلا اتصال تبقى الكتابة في طابور Firestore ولا يعود await؛ نضع مهلة ونشرح الحالة بصدق
        val done = withTimeoutOrNull(15_000) {
            db.document("users/$uid").update(
                mapOf("name" to name, "phone" to phone, "savedAddress" to savedAddress, "updatedAt" to Timestamp.now())
            ).await()
            true
        }
        if (done == null) throw ApiException("تعذّر تأكيد الحفظ لضعف الاتصال. ستُحفظ تغييراتك تلقائيًا عند عودة الاتصال.")
    }

    suspend fun addToken(uid: String, token: String) {
        db.document("users/$uid").update(
            mapOf("fcmTokens" to FieldValue.arrayUnion(token), "updatedAt" to Timestamp.now())
        ).await()
    }
}
