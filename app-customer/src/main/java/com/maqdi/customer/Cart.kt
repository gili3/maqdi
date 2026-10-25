package com.maqdi.customer

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.maqdi.core.Product
import java.util.UUID

data class CartLine(val product: Product, val qty: Int)

/** السلة من متجر واحد فقط */
object Cart {
    var storeId by mutableStateOf<String?>(null)
    var storeName by mutableStateOf("")
    val lines = mutableStateMapOf<String, CartLine>()

    val count: Int get() = lines.values.sumOf { it.qty }
    val itemsTotal: Long get() = lines.values.sumOf { it.product.price * it.qty }

    fun belongsToOtherStore(p: Product): Boolean = lines.isNotEmpty() && storeId != p.storeId

    fun set(p: Product, name: String, qty: Int) {
        if (qty <= 0) {
            lines.remove(p.id)
            if (lines.isEmpty()) { storeId = null; storeName = "" }
            return
        }
        if (lines.isEmpty()) { storeId = p.storeId; storeName = name }
        lines[p.id] = CartLine(p, qty)
    }

    fun clear() {
        lines.clear()
        storeId = null
        storeName = ""
    }

    /** بصمة الطلب: تتغير إذا تغيّر المحتوى، فيتغير معرّف الإرسال */
    fun fingerprint(phone: String, address: String): String =
        storeId + "|" + lines.entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${it.value.qty}" } + "|" + phone + "|" + address
}

/** يحفظ معرّف الإرسال حتى لو أُغلق التطبيق أثناء الإرسال، فلا يتكرر الطلب */
object Prefs {
    private var sp: SharedPreferences? = null

    fun init(c: Context) { sp = c.getSharedPreferences("maqdi", Context.MODE_PRIVATE) }

    fun submissionFor(fp: String): String {
        val s = sp ?: return UUID.randomUUID().toString().replace("-", "")
        if (s.getString("sub_fp", null) == fp) s.getString("sub_id", null)?.let { return it }
        val id = UUID.randomUUID().toString().replace("-", "")
        s.edit().putString("sub_fp", fp).putString("sub_id", id).apply()
        return id
    }

    fun clearSubmission() { sp?.edit()?.remove("sub_fp")?.remove("sub_id")?.apply() }
}
