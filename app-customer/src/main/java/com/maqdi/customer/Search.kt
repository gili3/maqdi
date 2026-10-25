package com.maqdi.customer

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.maqdi.core.EmptyState
import com.maqdi.core.LoadableView
import com.maqdi.core.Mq
import com.maqdi.core.MqCard
import com.maqdi.core.Product
import com.maqdi.core.Repo
import com.maqdi.core.Screen
import com.maqdi.core.SearchField
import com.maqdi.core.Store
import com.maqdi.core.StorageImage
import com.maqdi.core.categoryLabel
import com.maqdi.core.matchesQuery
import com.maqdi.core.money
import com.maqdi.core.rememberLoadable

private sealed interface Hit {
    data class StoreHit(val store: Store) : Hit
    data class ProductHit(val product: Product, val store: Store) : Hit
}

/** بحث عام: المتاجر والمنتجات المتاحة في المتاجر النشطة، بتطبيع عربي (همزات، ياء/ألف مقصورة، تاء مربوطة، تشكيل) */
@Composable
fun SearchScreen(onBack: () -> Unit, onOpenStore: (String) -> Unit) {
    var q by remember { mutableStateOf("") }
    val stores = rememberLoadable { Repo.activeStores() }
    val products = rememberLoadable { Repo.availableProductsOnce() }

    Screen("بحث", onBack = onBack) { pad ->
        Column(Modifier.padding(pad)) {
            SearchField(q, { q = it }, "ابحث عن منتج أو متجر", Modifier.fillMaxWidth().padding(12.dp))
            LoadableView(stores) { storeList ->
                LoadableView(products) { productList ->
                    val byId = storeList.associateBy { it.id }
                    if (q.isBlank()) {
                        EmptyState("ابحث عن أي شيء", "اكتب اسم منتج أو متجر، مثل: حليب أو صيدلية.", Icons.Default.Search)
                    } else {
                        val storeHits = storeList
                            .filter { matchesQuery(q, it.name, categoryLabel(it.category)) }
                            .map { Hit.StoreHit(it) }
                        val productHits = productList
                            .mapNotNull { p -> byId[p.storeId]?.let { s -> if (matchesQuery(q, p.name, p.category, s.name)) Hit.ProductHit(p, s) else null } }
                            .sortedByDescending { it.store.isOpen }
                            .take(60)
                        val hits: List<Hit> = storeHits + productHits
                        if (hits.isEmpty()) {
                            EmptyState("لا نتائج لـ «${q.trim()}»", "جرّب كلمة أقصر أو اسمًا آخر.", Icons.Default.Search)
                        } else {
                            LazyColumn {
                                items(hits, key = {
                                    when (it) { is Hit.StoreHit -> "s_" + it.store.id; is Hit.ProductHit -> "p_" + it.product.id }
                                }) { h ->
                                    when (h) {
                                        is Hit.StoreHit -> StoreHitRow(h.store) { onOpenStore(h.store.id) }
                                        is Hit.ProductHit -> ProductHitRow(h.product, h.store) { onOpenStore(h.store.id) }
                                    }
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
private fun StoreHitRow(s: Store, onClick: () -> Unit) {
    MqCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp), onClick = onClick) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.name, style = MaterialTheme.typography.titleMedium)
                Text("متجر • " + categoryLabel(s.category), color = Mq.Muted)
            }
            OpenBadge(s.isOpen)
        }
    }
}

@Composable
private fun ProductHitRow(p: Product, s: Store, onClick: () -> Unit) {
    MqCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp), onClick = onClick) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            StorageImage(p.imagePath, Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(p.name, style = MaterialTheme.typography.titleMedium)
                Text(money(p.price), color = MaterialTheme.colorScheme.primary)
                Text(s.name, color = Mq.Muted, style = MaterialTheme.typography.bodySmall)
            }
            OpenBadge(s.isOpen)
        }
    }
}
