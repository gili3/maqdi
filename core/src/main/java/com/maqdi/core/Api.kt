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
    "phone_country_not_allowed" to "التسجيل برقم الهاتف متاح لأرقام السودان (+249) فقط.",
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
    "permission_denied" to "ليست لديك صلاحية لهذه العملية. تواصل مع المدير.",
    "category_in_use" to "هذه الفئة مستعملة في متاجر. غيّر فئة تلك المتاجر أولًا.",
    "role_has_no_permissions" to "هذا الحساب (زبون) بلا صلاحيات تُخصَّص.",
    "cannot_remove_own_permissions" to "لا يمكنك سحب صلاحية «تخصيص الصلاحيات» من نفسك.",
    "cannot_grant_beyond_own" to "لا يمكنك منح صلاحية لا تملكها أنت.",
    "cannot_grant_self" to "لا يمكنك منح نفسك صلاحية أدمن.",
    "cannot_revoke_self" to "لا يمكنك سحب صلاحية الأدمن من نفسك.",
    "cannot_revoke_higher" to "هذا الأدمن يملك صلاحيات أكثر منك، فلا يمكنك سحبها منه.",
    "already_admin" to "هذا المستخدم أدمن بالفعل. عدّل صلاحياته من «تخصيص الصلاحيات».",
    "not_admin" to "هذا المستخدم ليس أدمن.",
    "user_is_admin" to "هذا المستخدم أدمن. لا يُغيَّر دوره من هنا؛ اسحب صلاحية الأدمن منه أولًا.",
    "invalid_permissions" to "قائمة الصلاحيات غير صحيحة.",
    "invalid_storeId" to "اختر المتجر الذي يتبع له التاجر.",
    "invalid_email" to "البريد الإلكتروني غير صحيح.",
    "transfer_disabled" to "الدفع بالتحويل غير متاح حاليًا. اختر الدفع نقدًا.",
    "transfer_details_required" to "أدخل اسم البنك ورقم الحساب واسم صاحبه قبل تفعيل الدفع بالتحويل.",
    "payment_not_verified" to "لم يُؤكَّد استلام الدفع بعد، فلا يمكن بدء التجهيز.",
    "payment_not_open" to "يمكن رفع الإيصال بعد موافقتك على الإجمالي فقط.",
    "already_paid" to "تم تأكيد دفع هذا الطلب مسبقًا.",
    "not_transfer_order" to "هذا الطلب ليس بالتحويل.",
    "no_proof_to_review" to "لا يوجد إيصال بانتظار المراجعة.",
    "invalid_proofPath" to "تعذّر ربط صورة الإيصال. أعد المحاولة.",
    "refund_not_allowed" to "يمكن طلب الاسترداد بعد تسليم الطلب فقط.",
    "refund_already_open" to "لديك طلب استرداد قيد المراجعة لهذا الطلب.",
    "refund_window_closed" to "انتهت مدة طلب الاسترداد لهذا الطلب.",
    "refund_amount_exceeds" to "المبلغ أكبر من المتاح للاسترداد.",
    "refund_already_resolved" to "تم البتّ في هذا الطلب مسبقًا.",
    "refund_not_found" to "طلب الاسترداد غير موجود.",
    "data_unavailable" to "البيانات غير جاهزة بعد (قد تكون الفهارس قيد الإنشاء). انتظر دقائق ثم أعد المحاولة.",
)

fun arabicError(msg: String?): String {
    val key = msg?.substringBefore(':') ?: ""
    errors[key]?.let { return it }
    if (key.startsWith("invalid_")) return "بيانات غير صحيحة. راجع الحقول."
    return "تعذّر تنفيذ العملية. حاول مجددًا."
}

object Api {
    private val fns: FirebaseFunctions by lazy { FirebaseFunctions.getInstance("europe-west3") }

    /** timeoutSec: للقراءات فقط (لوحة وتقارير). الكتابات تبقى بمهلة النظام حتى لا تُقطع وهي تُنفَّذ. */
    suspend fun call(name: String, data: Map<String, Any?> = emptyMap(), timeoutSec: Long? = null): Map<String, Any?> {
        try {
            val fn = fns.getHttpsCallable(name)
            if (timeoutSec != null) fn.setTimeout(timeoutSec, java.util.concurrent.TimeUnit.SECONDS)
            val res = fn.call(data).await()
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
        phone: String, address: String, note: String?, paymentMethod: String = "cash_on_delivery",
    ): String {
        val r = call("createOrder", mapOf(
            "submissionId" to submissionId, "storeId" to storeId,
            "items" to items.map { mapOf("productId" to it.first, "qty" to it.second) },
            "phone" to phone, "address" to address, "locationNote" to note, "paymentMethod" to paymentMethod,
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

    // ---------- الدفع بالتحويل والاسترداد ----------
    suspend fun submitTransferProof(orderId: String, proofPath: String, payerAccount: String, transferRef: String?) {
        call("submitTransferProof", mapOf("orderId" to orderId, "proofPath" to proofPath, "payerAccount" to payerAccount, "transferRef" to transferRef))
    }

    /** verify: receivedAmount اختياري (الافتراضي مبلغ الإثبات) | reject: reason مطلوب */
    suspend fun adminReviewPayment(orderId: String, verify: Boolean, receivedAmount: Long?, reason: String?) {
        call("adminReviewPayment", mapOf(
            "orderId" to orderId, "decision" to if (verify) "verify" else "reject",
            "receivedAmount" to receivedAmount, "reason" to reason,
        ))
    }

    suspend fun createRefundRequest(orderId: String, amount: Long, reason: String, refundAccount: String, key: String) {
        call("createRefundRequest", mapOf("orderId" to orderId, "amount" to amount, "reason" to reason, "refundAccount" to refundAccount, "refundKey" to key))
    }

    suspend fun adminResolveRefund(
        refundId: String, refund: Boolean, approvedAmount: Long?, reference: String?, adminNote: String?, chargeStore: Boolean,
    ) {
        call("adminResolveRefund", mapOf(
            "refundId" to refundId, "decision" to if (refund) "refund" else "reject", "approvedAmount" to approvedAmount,
            "reference" to reference, "adminNote" to adminNote, "chargeStore" to chargeStore,
        ))
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
    /** يرجع معرّف المتجر. imagePath: null = لا تغيّر الصورة (لا يُرسَل الحقل) */
    suspend fun adminUpsertStore(
        storeId: String?, name: String, category: String, phone: String, address: String?, status: String, isOpen: Boolean,
        imagePath: String? = null,
    ): String {
        val m = mutableMapOf<String, Any?>(
            "storeId" to storeId, "name" to name, "category" to category, "phone" to phone,
            "address" to address, "status" to status, "isOpen" to isOpen,
        )
        if (imagePath != null) m["imagePath"] = imagePath
        return call("adminUpsertStore", m)["storeId"] as? String ?: throw ApiException("ردّ غير متوقع من الخادم.")
    }

    suspend fun adminUpsertCategory(categoryId: String?, name: String, order: Long, isActive: Boolean) {
        call("adminUpsertCategory", mapOf("categoryId" to categoryId, "name" to name, "order" to order, "isActive" to isActive))
    }

    suspend fun adminDeleteCategory(categoryId: String) {
        call("adminDeleteCategory", mapOf("categoryId" to categoryId))
    }

    /** permissions = null يعيد الافتراضي (كل صلاحيات الدور) */
    suspend fun adminSetPermissions(uid: String, permissions: List<String>?) {
        call("adminSetUserPermissions", mapOf("uid" to uid, "permissions" to permissions))
    }

    /**
     * تعيين دور تاجر أو موصّل لمستخدم موجود. الأدمن لا يُمنح من هنا (انظر adminGrantAdmin).
     * customizePermissions=false: لا يُرسَل الحقل. true: permissions=null يعني كل صلاحيات الدور.
     */
    suspend fun adminSetRole(
        uid: String, role: String, storeId: String?,
        permissions: List<String>? = null, customizePermissions: Boolean = false,
    ) {
        val m = mutableMapOf<String, Any?>("uid" to uid, "role" to role, "storeId" to storeId)
        if (customizePermissions) m["permissions"] = permissions
        call("adminSetUserRole", m)
    }

    /** منح صلاحية أدمن لمستخدم موجود بقائمة صلاحيات صريحة (لا تتجاوز صلاحيات المانح) */
    suspend fun adminGrantAdmin(uid: String, permissions: List<String>) {
        call("adminGrantAdmin", mapOf("uid" to uid, "permissions" to permissions))
    }

    /** سحب صلاحية الأدمن: يعود الحساب زبونًا */
    suspend fun adminRevokeAdmin(uid: String) {
        call("adminRevokeAdmin", mapOf("uid" to uid))
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
            "maxOpenOrders" to s.maxOpenOrders, "customerCancelGraceMinutes" to s.customerCancelGraceMinutes,
            "courierCommission" to s.courierCommission, "courierPickupMinutes" to s.courierPickupMinutes,
            "transferEnabled" to s.transferEnabled, "transferBankName" to s.transferBankName,
            "transferAccountNumber" to s.transferAccountNumber, "transferAccountName" to s.transferAccountName,
            "transferNote" to s.transferNote, "paymentWindowMinutes" to s.paymentWindowMinutes,
            "refundWindowHours" to s.refundWindowHours,
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
        val bankNet: Long = 0, val refundCost: Long = 0,
    )

    suspend fun adminSummary(from: String, to: String): Summary {
        val r = call("adminFinanceSummary", mapOf("from" to from, "to" to to))
        fun n(k: String) = (r[k] as? Number)?.toLong() ?: 0L
        return Summary(n("deliveryRevenue"), n("cashNet"), n("storePayableNet"), n("expenses"), n("netRevenue"), n("bankNet"), n("refundCost"))
    }

    /** الأرقام null = تعذّر حسابها (مثل فهرس غير جاهز)؛ partial = بعض الأجزاء ناقصة */
    data class Dashboard(
        val byStatus: Map<String, Long>, val deliveryRevenueToday: Long?, val cashToday: Long?, val partial: Boolean,
        val deliveredToday: Long? = null, val storePayablesToday: List<Pair<String, Long>> = emptyList(),
    )

    suspend fun adminDashboard(): Dashboard {
        val r = call("adminDashboard", timeoutSec = 30)
        @Suppress("UNCHECKED_CAST")
        val m = (r["ordersByStatus"] as? Map<String, Any?>) ?: emptyMap()
        return Dashboard(
            byStatus = m.mapValues { (it.value as? Number)?.toLong() ?: 0L },
            deliveryRevenueToday = (r["deliveryRevenueToday"] as? Number)?.toLong(),
            cashToday = (r["cashToday"] as? Number)?.toLong(),
            partial = r["partial"] as? Boolean ?: false,
            deliveredToday = (r["deliveredToday"] as? Number)?.toLong(),
            storePayablesToday = ((r["storePayablesToday"] as? List<*>) ?: emptyList<Any?>()).mapNotNull { e ->
                val mm = e as? Map<*, *> ?: return@mapNotNull null
                val id = mm["storeId"] as? String ?: return@mapNotNull null
                id to ((mm["amount"] as? Number)?.toLong() ?: 0L)
            },
        )
    }

    // ---------- التقارير (من الخادم؛ الصلاحية تُفرض هناك) ----------
    data class DayRow(val day: String, val orders: Long, val amount: Long)
    data class RankRow(val name: String, val count: Long, val amount: Long)
    data class ReportData(
        val totals: Map<String, Long>, val byDay: List<DayRow>, val topProducts: List<RankRow>,
        val byStore: List<RankRow>, val other: Map<String, Long>, val truncated: Boolean, val failed: Long,
    )

    private fun Any?.asLong() = (this as? Number)?.toLong() ?: 0L

    @Suppress("UNCHECKED_CAST")
    private fun Any?.asRows() = (this as? List<Map<String, Any?>>) ?: emptyList()

    @Suppress("UNCHECKED_CAST")
    private fun Any?.asMap() = (this as? Map<String, Any?>) ?: emptyMap()

    /** kind: admin | merchant | courier ؛ from/to بصيغة ISO-8601 (الحد الأقصى 92 يومًا) */
    suspend fun report(kind: String, from: String, to: String, storeId: String? = null): ReportData {
        val fn = when (kind) { "admin" -> "adminReport"; "merchant" -> "merchantReport"; else -> "courierReport" }
        val r = call(fn, mapOf("from" to from, "to" to to, "storeId" to storeId), timeoutSec = 60)
        return ReportData(
            totals = r["totals"].asMap().mapValues { it.value.asLong() },
            byDay = r["byDay"].asRows().map { DayRow(it["day"] as? String ?: "", it["orders"].asLong(), it["itemsTotal"].asLong()) },
            topProducts = r["topProducts"].asRows().map { RankRow(it["name"] as? String ?: "", it["qty"].asLong(), it["revenue"].asLong()) },
            byStore = r["byStore"].asRows().map { RankRow(it["storeName"] as? String ?: "", it["orders"].asLong(), it["itemsTotal"].asLong()) },
            other = r["otherStatuses"].asMap().mapValues { it.value.asLong() },
            truncated = r["truncated"] as? Boolean ?: false,
            failed = r["failed"].asLong(),
        )
    }
}
