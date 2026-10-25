package com.maqdi.partner

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.maqdi.core.Api
import com.maqdi.core.ApiException
import com.maqdi.core.Loadable
import com.maqdi.core.Order
import com.maqdi.core.Repo
import com.maqdi.core.StatusChip
import com.maqdi.core.dateTime
import com.maqdi.core.money
import com.maqdi.core.rememberLoadable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun PartnerOrderRow(o: Order, showStore: Boolean, onOpen: (String) -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clickable { onOpen(o.id) },
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (showStore) "${o.storeName} • ${o.customerName}" else o.customerName.ifBlank { "زبون" },
                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
                )
                StatusChip(o.status)
            }
            Text((if (o.totalsAreEstimate) "تقريبي: " else "") + money(o.total), color = MaterialTheme.colorScheme.primary)
            Text(o.address, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            Text(dateTime(o.createdAt) + if (o.isTest) " • تجريبي" else "", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun ChipRow(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (k, v) -> FilterChip(selected = selected == k, onClick = { onSelect(k) }, label = { Text(v) }) }
    }
}

/** مفتاح فتح/إغلاق المتجر */
@Composable
fun StoreOpenSwitch(storeId: String) {
    val store = rememberLoadable(storeId) { Repo.store(storeId) }
    val s = (store as? Loadable.Data)?.value
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    if (s != null) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (s.isOpen) "${s.name}: مفتوح لاستقبال الطلبات" else "${s.name}: مغلق")
                Switch(checked = s.isOpen, enabled = !busy, onCheckedChange = { v ->
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            Api.setStoreOpen(v, storeId)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: ApiException) {
                            error = e.userMessage
                        } finally {
                            busy = false
                        }
                    }
                })
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
