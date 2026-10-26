package com.maqdi.customer

import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.maqdi.core.Empty
import com.maqdi.core.EmptyState
import com.maqdi.core.Mq
import com.maqdi.core.MqCard
import com.maqdi.core.SearchField
import com.maqdi.core.Field
import com.maqdi.core.Loadable
import com.maqdi.core.LoadableView
import com.maqdi.core.Product
import com.maqdi.core.Repo
import com.maqdi.core.Screen
import com.maqdi.core.Stepper
import com.maqdi.core.StorageImage
import androidx.compose.ui.draw.alpha
import com.maqdi.core.Store
import com.maqdi.core.categoryLabel
import com.maqdi.core.money
import com.maqdi.core.rememberLoadable

@Composable
fun StoresScreen(onOpen: (String) -> Unit, onSearch: () -> Unit) {
    var cat by remember { mutableStateOf("all") }
    val stores = rememberLoadable { Repo.activeStores() }
    Screen("مقضي") { pad ->
        Column(Modifier.padding(pad)) {
            MqCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), onClick = onSearch) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.Search, contentDescription = null, tint = Mq.Muted)
                    androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))
                    Text("ابحث عن منتج أو متجر", color = Mq.Muted)
                }
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                (listOf("all" to "الكل") + com.maqdi.core.CategoryNames.options().map { it.id to it.name }).forEach { (k, v) ->
                    FilterChip(selected = cat == k, onClick = { cat = k }, label = { Text(v) })
                }
            }
            LoadableView(stores) { list ->
                val shown = list.filter { cat == "all" || it.category == cat }.sortedByDescending { it.isOpen }
                if (shown.isEmpty()) Empty("لا توجد متاجر في هذا التصنيف حاليًا.")
                else LazyColumn {
                    items(shown, key = { it.id }) { s -> StoreRow(s) { onOpen(s.id) } }
                }
            }
        }
    }
}

@Composable
fun OpenBadge(open: Boolean) {
    val c = if (open) Mq.Green else Mq.Red
    androidx.compose.material3.Surface(color = c.copy(alpha = 0.12f), shape = RoundedCornerShape(50)) {
        Text(
            if (open) "مفتوح" else "مغلق", color = c, style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun StoreRow(s: Store, onClick: () -> Unit) {
    MqCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).alpha(if (s.isOpen) 1f else 0.55f), onClick = onClick) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            StorageImage(s.imagePath, Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)))
            androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.name, style = MaterialTheme.typography.titleMedium)
                Text(categoryLabel(s.category), color = Mq.Muted)
            }
            OpenBadge(s.isOpen)
        }
    }
}

@Composable
fun ProductsScreen(storeId: String, onBack: () -> Unit, onOpenCart: () -> Unit) {
    val store = rememberLoadable(storeId) { Repo.store(storeId) }
    val products = rememberLoadable(storeId) { Repo.products(storeId) }
    var q by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("all") }
    var conflict by remember { mutableStateOf<Pair<Product, Int>?>(null) }

    val storeObj = (store as? Loadable.Data)?.value
    val storeName = storeObj?.name ?: ""
    val closed = storeObj != null && !storeObj.isOpen

    fun change(p: Product, qty: Int) {
        if (Cart.belongsToOtherStore(p)) conflict = p to qty else Cart.set(p, storeName, qty)
    }

    Screen(
        storeName.ifBlank { "المتجر" }, onBack = onBack,
        bottomBar = {
            if (Cart.count > 0 && Cart.storeId == storeId) {
                Button(
                    onClick = onOpenCart,
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                ) { Text("عرض السلة (${Cart.count}) • ${money(Cart.itemsTotal)}") }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            if (closed) {
                Text(
                    "المتجر مغلق حاليًا. يمكنك التصفح لكن لا يمكن إرسال طلب.",
                    color = Mq.Red, modifier = Modifier.padding(12.dp),
                )
            }
            SearchField(q, { q = it }, "ابحث في المتجر", Modifier.fillMaxWidth().padding(horizontal = 12.dp))
            LoadableView(products) { list ->
                val cats = list.mapNotNull { it.category?.trim()?.takeIf { c -> c.isNotEmpty() } }.distinct().sorted()
                if (cats.isNotEmpty()) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(selected = cat == "all", onClick = { cat = "all" }, label = { Text("الكل") })
                        cats.forEach { c -> FilterChip(selected = cat == c, onClick = { cat = c }, label = { Text(c) }) }
                    }
                }
                val shown = list.filter { (cat == "all" || it.category?.trim() == cat) && com.maqdi.core.matchesQuery(q, it.name, it.category) }
                    .sortedWith(compareByDescending<Product> { it.isAvailable }.thenBy { it.name })
                if (shown.isEmpty()) EmptyState("لا توجد منتجات", "جرّب بحثًا آخر أو تصنيفًا مختلفًا.")
                else LazyColumn {
                    items(shown, key = { it.id }) { p ->
                        ProductRow(p, qty = Cart.lines[p.id]?.qty ?: 0, canOrder = !closed) { change(p, it) }
                    }
                }
            }
        }
    }

    conflict?.let { (p, qty) ->
        AlertDialog(
            onDismissRequest = { conflict = null },
            title = { Text("سلة من متجر آخر") },
            text = { Text("سلتك تحتوي منتجات من متجر آخر، والطلب الواحد من متجر واحد فقط. هل تريد تفريغ السلة وإضافة هذا المنتج؟") },
            confirmButton = {
                TextButton(onClick = { Cart.clear(); Cart.set(p, storeName, qty); conflict = null }) { Text("تفريغ وإضافة") }
            },
            dismissButton = { TextButton(onClick = { conflict = null }) { Text("إلغاء") } },
        )
    }
}

@Composable
private fun ProductRow(p: Product, qty: Int, canOrder: Boolean, onQty: (Int) -> Unit) {
    val maxQty = minOf(99L, 99L).toInt()
    MqCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            StorageImage(p.imagePath, Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleMedium)
                Text(money(p.price), color = MaterialTheme.colorScheme.primary)
                if (!p.isAvailable || maxQty <= 0) Text("غير متوفر", color = Mq.Red)
            }
            if (p.isAvailable && maxQty > 0 && canOrder) {
                Stepper(qty, onQty, min = 0, max = maxQty)
            }
        }
    }
}
