package com.maqdi.core

import java.text.Normalizer

private val diacritics = Regex("[\\u064B-\\u065F\\u0670\\u0640]")
private val spaces = Regex("\\s+")

/** تطبيع النص العربي للبحث: يطابق normalizeSearch في functions/src/textUtil.ts تمامًا */
fun normalizeSearch(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFKC)
        .lowercase()
        .replace(diacritics, "")
        .replace(Regex("[أإآٱ]"), "ا")
        .replace('ى', 'ي')
        .replace('ة', 'ه')
        .replace(spaces, " ")
        .trim()

/** كل كلمات الاستعلام يجب أن تظهر في أحد الحقول (مطابقة جزئية بعد التطبيع) */
fun matchesQuery(query: String, vararg fields: String?): Boolean {
    val terms = normalizeSearch(query).split(' ').filter { it.isNotEmpty() }
    if (terms.isEmpty()) return true
    val hay = fields.filterNotNull().joinToString(" ") { normalizeSearch(it) }
    return terms.all { hay.contains(it) }
}
