package com.maqdi.core

/** كتالوج الصلاحيات لكل دور (يطابق functions/src/permissions.ts). */
object Permissions {
    val catalog: Map<String, List<Pair<String, String>>> = mapOf(
        "admin" to listOf(
            "orders" to "الطلبات (عرض وإدارة)",
            "deliveries" to "التوصيل (استلام وتسليم)",
            "stores" to "المتاجر والمنتجات وصور المتاجر",
            "categories" to "فئات المتاجر",
            "users" to "المستخدمون (تعيين الأدوار/إيقاف)",
            "permissions" to "تخصيص صلاحيات الآخرين",
            "grant_admin" to "منح وسحب صلاحية أدمن",
            "finance" to "المالية والتسويات والتصدير",
            "expenses" to "المصروفات",
            "settings" to "رسوم التوصيل والمُهَل",
            "reports" to "التقارير والإحصاءات",
            "audit" to "سجل التدقيق",
        ),
        "merchant" to listOf(
            "orders" to "الطلبات (قبول/تجهيز/اقتراح)",
            "products" to "إدارة المنتجات",
            "open_close" to "فتح وإغلاق المتجر",
            "reports" to "تقارير المتجر",
            "balance" to "حساب المتجر",
        ),
        "courier" to listOf(
            "deliveries" to "التوصيلات (استلام وتسليم)",
            "reports" to "تقارير التوصيل",
        ),
    )

    /** الصلاحيات القياسية عند التعيين (بلا تخصيص): المندوب الطلبات فقط، والتاجر متجره فقط */
    val presets: Map<String, List<String>> = mapOf(
        "courier" to listOf("deliveries"),
        "merchant" to listOf("orders", "products", "open_close"),
    )

    fun all(role: String): List<String> = catalog[role].orEmpty().map { it.first }

    /** null = كل صلاحيات الدور */
    fun has(role: String, stored: List<String>?, p: String): Boolean {
        val all = all(role)
        if (p !in all) return false
        return stored == null || p in stored
    }
}
