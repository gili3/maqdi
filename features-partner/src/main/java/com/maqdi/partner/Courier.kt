package com.maqdi.partner

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.maqdi.core.Empty
import com.maqdi.core.InfoRow
import com.maqdi.core.LoadableView
import com.maqdi.core.Profile
import com.maqdi.core.Repo
import com.maqdi.core.Screen
import com.maqdi.core.Section
import com.maqdi.core.St
import com.maqdi.core.money
import com.maqdi.core.rememberLoadable

/** سجل توصيلات المندوب مع ملخص المبالغ التي حصّلها */
@Composable
fun CourierHistoryScreen(profile: Profile, onOpen: (String) -> Unit) {
    Screen("سجل توصيلاتي") { pad ->
        Column(Modifier.padding(pad)) { CourierHistoryContent(profile, onOpen) }
    }
}

/** محتوى السجل بلا إطار شاشة، ليُعرض داخل تبويب التوصيل */
@Composable
fun CourierHistoryContent(profile: Profile, onOpen: (String) -> Unit) {
    val orders = rememberLoadable(profile.uid) { Repo.courierDeliveries(profile.uid) }
    Column {
        run {
            LoadableView(orders) { list ->
                if (list.isEmpty()) Empty("لا توجد توصيلات بعد.")
                else {
                    val delivered = list.filter { it.status == St.DELIVERED }
                    Section("ملخص آخر ${list.size} طلبًا") {
                        InfoRow("التوصيلات المنجزة", delivered.size.toString())
                        InfoRow("المبالغ المحصَّلة نقدًا", money(delivered.sumOf { it.cashDue }), bold = true)
                        InfoRow("تعذّر التسليم", list.count { it.status == St.FAILED }.toString())
                    }
                    LazyColumn { items(list, key = { it.id }) { PartnerOrderRow(it, showStore = true, onOpen = onOpen) } }
                }
            }
        }
    }
}
