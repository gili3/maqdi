package com.maqdi.partner

import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.maqdi.core.Mq
import com.maqdi.core.ActionButton
import com.maqdi.core.Api
import com.maqdi.core.ApiException
import com.maqdi.core.Empty
import com.maqdi.core.MqCard
import com.maqdi.core.Field
import com.maqdi.core.ImageUtil
import com.maqdi.core.InfoRow
import com.maqdi.core.LoadableView
import com.maqdi.core.Product
import com.maqdi.core.Profile
import com.maqdi.core.Repo
import com.maqdi.core.Screen
import com.maqdi.core.Section
import com.maqdi.core.StorageImage
import com.maqdi.core.dateTime
import com.maqdi.core.entryTypeLabel
import com.maqdi.core.money
import com.maqdi.core.rememberLoadable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun MerchantProductsScreen(profile: Profile) {
    ProductsManager(profile.storeId ?: "", onBack = null)
}

@Composable
fun ProductsManager(storeId: String, onBack: (() -> Unit)?) {
    var editing by remember { mutableStateOf<Product?>(null) }
    var creating by remember { mutableStateOf(false) }
    val products = rememberLoadable(storeId) { Repo.products(storeId) }

    if (editing != null || creating) {
        ProductEditor(storeId, editing, onClose = { editing = null; creating = false })
        return
    }

    Screen(
        "المنتجات", onBack = onBack,
        floating = {
            FloatingActionButton(onClick = { creating = true }) { Icon(Icons.Default.Add, contentDescription = "إضافة منتج") }
        },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            LoadableView(products) { list ->
                if (list.isEmpty()) Empty("لا توجد منتجات بعد. اضغط + لإضافة أول منتج.")
                else LazyColumn {
                    items(list.sortedBy { it.name }, key = { it.id }) { p ->
                        MqCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp).clickable { editing = p }) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                StorageImage(p.imagePath, Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, style = MaterialTheme.typography.titleMedium)
                                    Text(money(p.price), color = MaterialTheme.colorScheme.primary)
                                    Text(
                                        (if (p.isAvailable) "متوفر" else "غير متوفر") + (p.stockQty?.let { " • الكمية: $it" } ?: ""),
                                        color = if (p.isAvailable) Mq.Muted else Mq.Red,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductEditor(storeId: String, p: Product?, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(p?.name ?: "") }
    var price by remember { mutableStateOf(p?.price?.toString() ?: "") }
    var stock by remember { mutableStateOf(p?.stockQty?.toString() ?: "") }
    var desc by remember { mutableStateOf(p?.description ?: "") }
    var category by remember { mutableStateOf(p?.category ?: "") }
    var available by remember { mutableStateOf(p?.isAvailable ?: true) }
    var imagePath by remember { mutableStateOf(p?.imagePath) }
    var uploading by remember { mutableStateOf(false) }
    var uploadError by remember { mutableStateOf<String?>(null) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                uploading = true
                uploadError = null
                try {
                    imagePath = ImageUtil.uploadProductImage(ctx, storeId, uri)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: ApiException) {
                    uploadError = e.userMessage
                } catch (e: Exception) {
                    uploadError = "تعذّر رفع الصورة. تحقق من الاتصال وحاول مجددًا."
                } finally {
                    uploading = false
                }
            }
        }
    }

    val priceVal = price.trim().toLongOrNull()
    val stockVal = if (stock.isBlank()) null else stock.trim().toLongOrNull()
    val valid = name.trim().length >= 2 && priceVal != null && priceVal >= 0 && (stock.isBlank() || (stockVal != null && stockVal >= 0))

    Screen(if (p == null) "منتج جديد" else "تعديل المنتج", onBack = onClose) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Section("الصورة") {
                StorageImage(imagePath, Modifier.size(120.dp).clip(RoundedCornerShape(12.dp)))
                ActionButton(if (uploading) "جارٍ الرفع..." else "اختيار صورة", Modifier.fillMaxWidth().padding(top = 8.dp), tonal = true, enabled = !uploading) {
                    pick.launch("image/*")
                }
                Text("تُصغَّر الصورة وتُضغط تلقائيًا لتوفير البيانات.", color = Mq.Muted, style = MaterialTheme.typography.bodySmall)
                uploadError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            Section("البيانات") {
                Field("اسم المنتج", name, { name = it })
                Field("السعر (جنيه)", price, { price = it.filter { c -> c.isDigit() } }, Modifier.fillMaxWidth().padding(top = 8.dp), keyboard = KeyboardType.Number)
                Field("الكمية المتوفرة (اتركها فارغة إن لم تُحصِها)", stock, { stock = it.filter { c -> c.isDigit() } },
                    Modifier.fillMaxWidth().padding(top = 8.dp), keyboard = KeyboardType.Number)
                Field("التصنيف (مثل: مشروبات، ألبان، أدوية عامة)", category, { category = it }, Modifier.fillMaxWidth().padding(top = 8.dp))
                Field("وصف (اختياري)", desc, { desc = it }, Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = false)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("متوفر للطلب")
                    Switch(checked = available, onCheckedChange = { available = it })
                }
            }
            ActionButton("حفظ", Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), enabled = valid && !uploading) {
                Api.upsertProduct(p?.id, storeId, name.trim(), desc.trim().ifBlank { null }, category.trim().ifBlank { null }, priceVal ?: 0L, stockVal, available, imagePath)
                onClose()
            }
        }
    }
}

@Composable
fun StoreAccountScreen(storeId: String, name: String) {
    val bal = rememberLoadable(storeId) { Repo.balance(storeId) }
    val entries = rememberLoadable(storeId) { Repo.storeLedger(storeId) }
    val activity = LocalContext.current as android.app.Activity
    Screen("حساب المتجر", actions = {
        androidx.compose.material3.IconButton(onClick = { com.maqdi.core.signOutAll(activity) }) {
            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.Logout, contentDescription = "تسجيل الخروج")
        }
    }) { pad ->
        Column(Modifier.padding(pad)) {
            LoadableView(bal) { b ->
                val payable = b?.payable ?: 0L
                val settled = b?.settled ?: 0L
                Section("الرصيد") {
                    InfoRow("إجمالي مبيعات المنتجات المسلَّمة", money(payable))
                    InfoRow("ما استلمته من الإدارة", money(settled))
                    InfoRow("المتبقي لك", money(payable - settled), bold = true)
                }
            }
            LoadableView(entries) { list ->
                if (list.isEmpty()) Empty("لا توجد حركات بعد.")
                else LazyColumn {
                    items(list, key = { it.id }) { e ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(entryTypeLabel(e.type))
                                Text(dateTime(e.createdAt), color = Mq.Muted, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(money(e.amount), color = if (e.amount < 0) Mq.Red else Mq.Green)
                        }
                    }
                }
            }
        }
    }
}
