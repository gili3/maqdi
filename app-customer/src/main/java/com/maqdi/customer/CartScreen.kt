package com.maqdi.customer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.maqdi.core.Mq
import com.maqdi.core.ActionButton
import com.maqdi.core.Api
import com.maqdi.core.Empty
import com.maqdi.core.Field
import com.maqdi.core.InfoRow
import com.maqdi.core.Loadable
import com.maqdi.core.Profile
import com.maqdi.core.Repo
import com.maqdi.core.Screen
import com.maqdi.core.Section
import com.maqdi.core.Settings
import com.maqdi.core.Stepper
import com.maqdi.core.money
import com.maqdi.core.rememberLoadable

private val phoneRegex = Regex("^[0-9+\\- ]{6,20}$")

@Composable
fun CartScreen(profile: Profile, onBrowse: () -> Unit, onOrderSent: (String) -> Unit) {
    val settingsL = rememberLoadable { Repo.settings() }
    val settings = (settingsL as? Loadable.Data<Settings>)?.value ?: Settings()
    var phone by remember { mutableStateOf(profile.phone ?: "") }
    var address by remember { mutableStateOf(profile.savedAddress ?: "") }

    Screen("السلة") { pad ->
        if (Cart.lines.isEmpty()) {
            androidx.compose.foundation.layout.Box(Modifier.padding(pad).fillMaxSize()) {
                Empty("سلتك فارغة. اختر متجرًا وأضف منتجاتك.")
                ActionButton(
                    "تصفّح المتاجر",
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
                ) { onBrowse() }
            }
        } else {
            val itemsTotal = Cart.itemsTotal
            val phoneOk = phone.isBlank() || phoneRegex.matches(phone.trim())
            Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                Section("طلبك من ${Cart.storeName}") {
                    Cart.lines.values.sortedBy { it.product.name }.forEach { l ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(l.product.name, style = MaterialTheme.typography.titleMedium)
                                Text(money(l.product.price * l.qty), color = Mq.Muted)
                            }
                            Stepper(l.qty, { Cart.set(l.product, Cart.storeName, it) }, min = 0,
                                max = minOf(99L, 99L).toInt())
                        }
                    }
                }
                Section("الإجمالي (تقديري)") {
                    InfoRow("قيمة المنتجات", money(itemsTotal))
                    InfoRow("رسوم التوصيل", money(settings.deliveryFee))
                    InfoRow("المبلغ التقريبي", money(itemsTotal + settings.deliveryFee), bold = true)
                    Text(
                        "التاجر سيراجع التوفر والأسعار، ثم يصلك الإجمالي النهائي لتوافق عليه قبل بدء التجهيز. الدفع نقدًا عند الاستلام.",
                        style = MaterialTheme.typography.bodySmall, color = Mq.Muted, modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Section("بيانات التوصيل") {
                    Field("العنوان", address, { address = it }, Modifier.fillMaxWidth())
                    Field("رقم الهاتف (اختياري، لا يظهر لأحد)", phone, { phone = it }, Modifier.fillMaxWidth().padding(top = 8.dp), keyboard = KeyboardType.Phone)
                }
                ActionButton(
                    "إرسال الطلب",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    enabled = phoneOk && address.trim().length >= 3,
                ) {
                    val storeId = Cart.storeId ?: throw com.maqdi.core.ApiException("السلة فارغة.")
                    // نفس البصمة = نفس معرّف الإرسال، فإعادة المحاولة بعد انقطاع الاتصال لا تنشئ طلبًا مكررًا
                    val submissionId = Prefs.submissionFor(Cart.fingerprint(phone.trim(), address.trim()))
                    val items = Cart.lines.values.map { it.product.id to it.qty }
                    val orderId = Api.createOrder(submissionId, storeId, items, phone.trim(), address.trim(), null)
                    try { Repo.updateProfile(profile.uid, profile.name, phone.trim().ifBlank { null }, address.trim()) } catch (_: Exception) { }
                    Prefs.clearSubmission()
                    Cart.clear()
                    onOrderSent(orderId)
                }
            }
        }
    }
}
