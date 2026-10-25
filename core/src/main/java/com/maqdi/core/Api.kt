package com.maqdi.core

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

class ApiException(val userMessage: String, val code: String? = null) : Exception(userMessage)

private val CONNECTION_MSG = "الاتصال ضعيف أو مقطوع. تحقق من الإنترنت وحاول مجددًا."

private val errors = mapOf(
    "login_required" to "سجّل الدخول أولًا.",
    "profile_missing" to "حسابك غير مكتمل. أعد تسجيل الدخول.",
    "account_suspended" to "حسابك موقوف. تواصل مع الإدارة.",
    "role_not_allowed" to "لا تملك صلاحية هذه العملية.",
    "email_not_verified" to "البريد غير موثّق.",
    "orders_disabled" to "استقبال الطلبات متوقف حاليًا.",
    "store_unavailable" to "المتجر غير متاح حاليًا.",
    "store_closed" to "المتجر مغلق حاليًا.",
    "store_not_found" to "المتجر غير موجود.",
    "product_unavailable" to "منتج في سلتك لم يعد متوفرًا. عدّل السلة.",
    "insufficient_stock" to "الكمية المطلوبة غير متوفرة.",
    "product_not_in_store" to "هذا المنتج لا يتبع المتجر.",
    "product_not_found" to "المنتج غير موجود.",
    "order_not_found" to "الطلب غير موجود.",
    "order_access_denied" to "لا تملك صلاحية على هذا الطلب.",
    "not_awaiting_customer" to "الطلب ليس بانتظار موافقتك.",
    "stale_proposal" to "تغيّر الطلب. افتح التفاصيل وراجع الإجمالي الجديد.",
    "proposal_expired" to "انتهت مهلة الموافقة.",
    "cannot_decline_now" to "لا يمكن إلغاء الطلب في هذه المرحلة.",
    "transition_not_allowed" to "لا يمكن تنفيذ هذه العملية في حالة الطلب الحالية.",
    "total_not_approved" to "لم يوافق الزبون على الإجمالي بعد.",
    "not_your_delivery" to "هذا التوصيل ليس لك.",
    "exceeds_outstanding" to "المبلغ أكبر من المستحق للمتجر.",
    "already_reversed" to "تم تصحيح هذا القيد سابقًا.",
    "cannot_reverse_a_correction" to "لا يمكن تصحيح قيد تصحيح.",
    "entry_not_found" to "القيد غير موجود.",
    "cannot_suspend_self" to "لا يمكنك إيقاف حسابك.",
    "user_not_found" to "المستخدم غير موجود.",
    "invalid_range" to "نطاق التاريخ غير صحيح.",
    "too_many_open_orders" to "لديك طلبات مفتوحة كثيرة. انتظر اكتمال أحدها ثم أرسل غيره.",
    "already_taken" to "استلم مندوب آخر هذا الطلب.",
)

fun arabicError(msg: String?): String {
    val key = msg?.substringBefore(':') ?: ""
    errors[key]?.let { return it }
    if (key.startsWith("invalid_")) return "بيانات غير صحيحة. راجع الحقول."
    return "تعذّر تنفيذ العملية. حاول مجددًا."
}

object Api {
    private val fns: FirebaseFunctions by lazy { FirebaseFunctions.getInstance("europe-west3") }

    suspend fun call(name: String, data: Map<String, Any?> = emptyMap()): Map<String, Any?> {
        try {
            val res = fns.getHttpsCallable(name).call(data).await()
            @Suppress("UNCHECKED_CAST")
            return (res.data as? Map<String, Any?>) ?: emptyMap()
        } catch (e: FirebaseFunctionsException) {
            val offline = e.code == FirebaseFunctionsException.Code.UNAVAILABLE ||
                e.code == FirebaseFunctionsException.Code.DEADLINE_EXCEEDED
            throw ApiException(if (offline) CONNECTION_MSG else arabicError(e.message), e.code.name)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            throw e
        } catch (e: Exception) {
            throw ApiException(CONNECTION_MSG)
        }
    }

    // ---------- الحساب ----------
    suspend fun ensureProfile(name: String?) { call("ensureProfile", mapOf("name" to name)) }

    // ---------- الطلبات ----------
    suspend fun createOrder(
        submissionId: String, storeId: String, items: List<Pair<String, Int>>,
        phone: String, address: String, note: String?,
    ): String {
        val r = call("createOrder", mapOf(
            "submissionId" to submissionId, "storeId" to storeId,
            "items" to items.map { mapOf("productId" to it.first, "qty" to it.second) },
            "phone" to phone, "address" to address, "locationNote" to note,
        ))
        return r["orderId"] as? String ?: throw ApiException("ردّ غير متوقع من الخادم.")
    }

    /** items: معرّف المنتج، الكمية، السعر */
    suspend fun proposeOrder(orderId: String, items: List<Triple<String, Int, Long>>, note: String?) {
        call("proposeOrder", mapOf(
            "orderId" to orderId, "note" to note,
            "items" to items.map { mapOf("productId" to it.first, "qty" to it.second, "unitPrice" to it.third) },
        ))
    }

    suspend fun respond(orderId: String, revision: Long, approve: Boolean) {
        call("respondToProposal", mapOf("orderId" to orderId, "revision" to revision, "decision" to if (approve) "approve" else "decline"))
    }

    suspend fun changeStatus(orderId: String, to: String, reason: String? = null) {
        call("changeOrderStatus", mapOf("orderId" to orderId, "to" to to, "reason" to reason))
    }

    // ---------- المنتجات والمتاجر ----------
    suspend fun upsertProduct(
        productId: String?, storeId: String?, name: String, description: String?, category: String?, price: Long,
        stockQty: Long?, isAvailable: Boolean, imagePath: String?,
    ) {
        call("upsertProduct", mapOf(
            "productId" to productId, "storeId" to storeId, "name" to name, "description" to description,
            "category" to category, "price" to price, "stockQty" to stockQty, "isAvailable" to isAvailable, "imagePath" to imagePath,
        ))
    }

    suspend fun setStoreOpen(isOpen: Boolean, storeId: String? = null) {
        call("setStoreOpen", mapOf("isOpen" to isOpen, "storeId" to storeId))
    }

    // ---------- الإدارة ----------
    suspend fun adminUpsertStore(
        storeId: String?, name: String, category: String, phone: String, address: String?, status: String, isOpen: Boolean,
    ) {
        call("adminUpsertStore", mapOf(
            "storeId" to storeId, "name" to name, "category" to category, "phone" to phone,
            "address" to address, "status" to status, "isOpen" to isOpen,
        ))
    }

    suspend fun adminInvite(email: String, role: String, storeId: String?) {
        call("adminInviteUser", mapOf("email" to email, "role" to role, "storeId" to storeId))
    }

    suspend fun adminSetUserStatus(uid: String, status: String?, isTest: Boolean?) {
        val m = mutableMapOf<String, Any?>("uid" to uid)
        if (status != null) m["status"] = status
        if (isTest != null) m["isTestAccount"] = isTest
        call("adminSetUserStatus", m)
    }

    suspend fun adminUpdateSettings(s: Settings) {
        call("adminUpdateSettings", mapOf(
            "deliveryFee" to s.deliveryFee, "merchantReviewMinutes" to s.merchantReviewMinutes,
            "customerApprovalMinutes" to s.customerApprovalMinutes, "ordersEnabled" to s.ordersEnabled,
            "maxOpenOrders" to s.maxOpenOrders,
        ))
    }

    suspend fun adminSettlement(storeId: String, amount: Long, key: String, note: String?) {
        call("adminRecordSettlement", mapOf("storeId" to storeId, "amount" to amount, "settlementKey" to key, "note" to note))
    }

    suspend fun adminCorrect(entryId: String, reason: String, replacement: Long?) {
        call("adminCorrectEntry", mapOf("entryId" to entryId, "reason" to reason, "replacementAmount" to replacement))
    }

    suspend fun adminExpense(amount: Long, category: String, note: String?, key: String) {
        call("adminAddExpense", mapOf("amount" to amount, "category" to category, "note" to note, "expenseKey" to key))
    }

    suspend fun adminCsv(from: String, to: String, storeId: String?): String {
        val r = call("adminExportFinancialCsv", mapOf("from" to from, "to" to to, "storeId" to storeId))
        return r["csv"] as? String ?: ""
    }

    data class Summary(
        val deliveryRevenue: Long, val cashNet: Long, val storePayableNet: Long, val expenses: Long, val netRevenue: Long,
    )

    suspend fun adminSummary(from: String, to: String): Summary {
        val r = call("adminFinanceSummary", mapOf("from" to from, "to" to to))
        fun n(k: String) = (r[k] as? Number)?.toLong() ?: 0L
        return Summary(n("deliveryRevenue"), n("cashNet"), n("storePayableNet"), n("expenses"), n("netRevenue"))
    }

    data class Dashboard(val byStatus: Map<String, Long>, val deliveryRevenueToday: Long, val cashToday: Long)

    suspend fun adminDashboard(): Dashboard {
        val r = call("adminDashboard")
        @Suppress("UNCHECKED_CAST")
        val m = (r["ordersByStatus"] as? Map<String, Any?>) ?: emptyMap()
        return Dashboard(
            byStatus = m.mapValues { (it.value as? Number)?.toLong() ?: 0L },
            deliveryRevenueToday = (r["deliveryRevenueToday"] as? Number)?.toLong() ?: 0L,
            cashToday = (r["cashToday"] as? Number)?.toLong() ?: 0L,
        )
    }
}
