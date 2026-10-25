package com.maqdi.partner

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.maqdi.core.daysAgoKhartoum
import com.maqdi.core.Mq
import com.maqdi.core.ActionButton
import com.maqdi.core.Api
import com.maqdi.core.ApiException
import com.maqdi.core.Empty
import com.maqdi.core.MqCard
import com.maqdi.core.Entry
import com.maqdi.core.Field
import com.maqdi.core.InfoRow
import com.maqdi.core.Loadable
import com.maqdi.core.LoadableView
import com.maqdi.core.Profile
import com.maqdi.core.Repo
import com.maqdi.core.Screen
import com.maqdi.core.Section
import com.maqdi.core.Settings
import com.maqdi.core.St
import com.maqdi.core.Store
import com.maqdi.core.categoryLabel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.ui.draw.clip
import com.maqdi.core.Category
import com.maqdi.core.CategoryNames
import com.maqdi.core.ImageUtil
import com.maqdi.core.Permissions
import com.maqdi.core.StorageImage
import com.maqdi.core.UserRow
import com.maqdi.core.dateTime
import com.maqdi.core.dayStartMs
import com.maqdi.core.entryTypeLabel
import com.maqdi.core.isoEndExclusive
import com.maqdi.core.isoStart
import com.maqdi.core.money
import com.maqdi.core.rememberAction
import com.maqdi.core.rememberLoadable
import com.maqdi.core.roleLabel
import com.maqdi.core.signOutAll
import com.maqdi.core.todayKhartoum
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private fun newKey() = UUID.randomUUID().toString().replace("-", "")

// ---------- لوحة الإحصائيات ----------
@Composable
fun DashboardScreen() {
    var data by remember { mutableStateOf<Api.Dashboard?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(tick) {
        err = null
        try {
            data = Api.adminDashboard()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            err = e.userMessage
        }
    }
    Screen("الرئيسية", actions = {
        IconButton(onClick = { tick++ }) { Icon(Icons.Default.Refresh, contentDescription = "تحديث") }
    }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            err?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
            val d = data
            if (d == null && err == null) Text("جارٍ التحميل...", modifier = Modifier.padding(16.dp))
            if (d != null) {
                Section("اليوم") {
                    InfoRow("إيراد التوصيل", money(d.deliveryRevenueToday), bold = true)
                    InfoRow("النقد المحصَّل", money(d.cashToday))
                }
                Section("الطلبات حسب الحالة") {
                    St.all.forEach { s ->
                        val c = d.byStatus[s] ?: 0L
                        if (c > 0) InfoRow(St.label(s), c.toString())
                    }
                    if (d.byStatus.values.all { it == 0L }) Text("لا توجد طلبات بعد.", color = Mq.Muted)
                }
            }
        }
    }
}

// ---------- كل الطلبات ----------
@Composable
fun AdminOrdersScreen(onOpen: (String) -> Unit) {
    var group by remember { mutableStateOf("all") }
    var storeId by remember { mutableStateOf("all") }
    var period by remember { mutableStateOf("all") }
    val stores = rememberLoadable { Repo.allStores() }
    val fromMs = if (period == "all") null else dayStartMs(daysAgoKhartoum(period.toInt() - 1))
    val orders = rememberLoadable(group == "delivered", storeId, fromMs) {
        Repo.adminOrders(if (group == "delivered") St.DELIVERED else null, if (storeId == "all") null else storeId, fromMs, null)
    }
    val storeOptions = listOf("all" to "كل المتاجر") + ((stores as? Loadable.Data)?.value ?: emptyList()).map { it.id to it.name }
    Screen("كل الطلبات") { pad ->
        Column(Modifier.padding(pad)) {
            ChipRow(listOf("all" to "الكل", "open" to "جارية", "delivered" to "مسلَّمة", "closed" to "ملغاة أو فاشلة"), group) { group = it }
            ChipRow(listOf("1" to "اليوم", "7" to "٧ أيام", "30" to "٣٠ يومًا", "all" to "كل الفترات"), period) { period = it }
            if (storeOptions.size > 2) ChipRow(storeOptions, storeId) { storeId = it }
            LoadableView(orders) { all ->
                val list = when (group) {
                    "open" -> all.filter { it.status !in St.terminal }
                    "closed" -> all.filter { it.status in St.terminal && it.status != St.DELIVERED }
                    else -> all
                }
                if (list.isEmpty()) Empty("لا توجد طلبات.")
                else LazyColumn { items(list, key = { it.id }) { PartnerOrderRow(it, showStore = true, onOpen = onOpen) } }
            }
        }
    }
}

// ---------- المالية ----------
@Composable
fun FinanceScreen(onStore: (String) -> Unit) {
    val balances = rememberLoadable { Repo.balances() }
    val stores = rememberLoadable { Repo.allStores() }
    val names = ((stores as? Loadable.Data)?.value ?: emptyList()).associate { it.id to it.name }
    Screen("المالية") { pad ->
        Column(Modifier.padding(pad)) {
            LoadableView(balances) { list ->
                val owed = list.sumOf { it.outstanding }
                Section("مستحقات المتاجر عليك") {
                    InfoRow("الإجمالي المتبقي", money(owed), bold = true)
                    Text("المبلغ الذي حصّلته من الزبائن نيابة عن المتاجر ولم تسدّده بعد. ليس ربحًا للمنصة.", color = Mq.Muted)
                }
                if (list.isEmpty()) Empty("لا توجد مستحقات بعد. تظهر بعد أول طلب مسلَّم.")
                else LazyColumn {
                    items(list.sortedByDescending { it.outstanding }, key = { it.storeId }) { b ->
                        MqCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp).clickable { onStore(b.storeId) }) {
                            Row(Modifier.padding(14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(names[b.storeId] ?: b.storeId, style = MaterialTheme.typography.titleMedium)
                                Text(money(b.outstanding), color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StoreFinanceScreen(storeId: String, onBack: () -> Unit) {
    val bal = rememberLoadable(storeId) { Repo.balance(storeId) }
    val entries = rememberLoadable(storeId) { Repo.allEntries(storeId) }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var key by remember { mutableStateOf(newKey()) }
    var correcting by remember { mutableStateOf<Entry?>(null) }

    Screen("حساب المتجر", onBack = onBack) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            LoadableView(bal) { b ->
                val payable = b?.payable ?: 0L
                val settled = b?.settled ?: 0L
                Section("الرصيد") {
                    InfoRow("مبيعات المنتجات المسلَّمة", money(payable))
                    InfoRow("ما سُدِّد للمتجر", money(settled))
                    InfoRow("المتبقي للمتجر", money(payable - settled), bold = true)
                }
            }
            Section("تسجيل تسوية (دفعة للمتجر)") {
                Field("المبلغ", amount, { amount = it.filter { c -> c.isDigit() } }, keyboard = KeyboardType.Number)
                Field("ملاحظة (اختياري)", note, { note = it }, Modifier.fillMaxWidth().padding(top = 8.dp))
                ActionButton("تسجيل التسوية", Modifier.fillMaxWidth().padding(top = 8.dp), enabled = (amount.toLongOrNull() ?: 0L) > 0) {
                    Api.adminSettlement(storeId, amount.toLong(), key, note.trim().ifBlank { null })
                    key = newKey()
                    amount = ""
                    note = ""
                }
            }
            LoadableView(entries) { list ->
                val reversed = list.mapNotNull { it.reversesEntryId }.toSet()
                Section("القيود المالية") {
                    if (list.isEmpty()) Text("لا توجد قيود.", color = Mq.Muted)
                    list.forEach { e ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(entryTypeLabel(e.type) + " • " + ledgerLabel(e.ledger), Modifier.weight(1f))
                                Text(money(e.amount), color = if (e.amount < 0) Mq.Red else Mq.Green)
                            }
                            Text(dateTime(e.createdAt) + (e.note?.let { " • $it" } ?: ""), color = Mq.Muted, style = MaterialTheme.typography.bodySmall)
                            if (e.reversesEntryId == null && e.id !in reversed) {
                                TextButton(onClick = { correcting = e }) { Text("تصحيح هذا القيد") }
                            } else if (e.id in reversed) {
                                Text("مُصحَّح", color = Mq.Muted)
                            }
                        }
                    }
                }
            }
        }
    }
    correcting?.let { CorrectionDialog(it) { correcting = null } }
}

private fun ledgerLabel(l: String) = when (l) {
    "cash" -> "النقد"; "store_payable" -> "مستحق المتجر"; "delivery_revenue" -> "إيراد التوصيل"; else -> l
}

@Composable
private fun CorrectionDialog(e: Entry, onDismiss: () -> Unit) {
    var reason by remember { mutableStateOf("") }
    var repl by remember { mutableStateOf("") }
    val st = rememberAction()
    AlertDialog(
        onDismissRequest = { if (!st.busy) onDismiss() },
        title = { Text("تصحيح قيد بمبلغ ${money(e.amount)}") },
        text = {
            Column {
                Text("سيُنشأ قيد عكسي مرتبط بالقيد الأصلي، ولا يُحذف الأصل.", color = Mq.Muted)
                Field("سبب التصحيح", reason, { reason = it }, Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = false)
                Field("المبلغ الصحيح البديل (اختياري)", repl, { repl = it.filter { c -> c.isDigit() || c == '-' } },
                    Modifier.fillMaxWidth().padding(top = 8.dp), keyboard = KeyboardType.Number)
            }
        },
        confirmButton = {
            ActionButton("تأكيد التصحيح", Modifier, enabled = reason.trim().length >= 3, state = st, destructive = true) {
                Api.adminCorrect(e.id, reason.trim(), repl.toLongOrNull())
                onDismiss()
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !st.busy) { Text("رجوع") } },
    )
}

// ---------- قائمة الإدارة ----------
@Composable
fun AdminMoreScreen(profile: Profile, onNav: (String) -> Unit) {
    val activity = androidx.compose.ui.platform.LocalContext.current as Activity
    Screen("الإدارة") { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Triple("a_reports", "التقارير والإحصاءات", profile.can("reports")),
                Triple("a_stores", "المتاجر والتجار والمنتجات", profile.can("stores")),
                Triple("a_categories", "فئات المتاجر", profile.can("categories")),
                Triple("a_users", "المستخدمون والصلاحيات", profile.can("users") || profile.can("permissions")),
                Triple("a_settings", "رسوم التوصيل والمُهَل", profile.can("settings")),
                Triple("a_expenses", "المصروفات", profile.can("expenses")),
                Triple("a_export", "ملخص مالي وتصدير (CSV)", profile.can("finance")),
                Triple("a_audit", "سجل التدقيق", profile.can("audit")),
            ).filter { it.third }.forEach { (r, t, _) -> ActionButton(t, tonal = true) { onNav(r) } }
            Text(profile.email, color = Mq.Muted, modifier = Modifier.padding(top = 12.dp))
            ActionButton("تسجيل الخروج", destructive = true) { signOutAll(activity) }
        }
    }
}

// ---------- المتاجر ----------
@Composable
fun AdminStoresScreen(onBack: () -> Unit, onProducts: (String) -> Unit) {
    val stores = rememberLoadable { Repo.allStores() }
    var editing by remember { mutableStateOf<Store?>(null) }
    var creating by remember { mutableStateOf(false) }

    if (editing != null || creating) {
        StoreEditor(editing, onClose = { editing = null; creating = false }, onProducts = onProducts)
        return
    }
    Screen("المتاجر", onBack = onBack, floating = {
        FloatingActionButton(onClick = { creating = true }) { Icon(Icons.Default.Add, contentDescription = "متجر جديد") }
    }) { pad ->
        Column(Modifier.padding(pad)) {
            LoadableView(stores) { list ->
                if (list.isEmpty()) Empty("لا توجد متاجر. اضغط + لإضافة أول متجر.")
                else LazyColumn {
                    items(list.sortedBy { it.name }, key = { it.id }) { s ->
                        MqCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp).clickable { editing = s }) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                StorageImage(s.imagePath, Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(s.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        categoryLabel(s.category) + " • " + (if (s.status == "active") "نشط" else "موقوف") + " • " + (if (s.isOpen) "مفتوح" else "مغلق"),
                                        color = Mq.Muted,
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
private fun StoreEditor(s: Store?, onClose: () -> Unit, onProducts: (String) -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(s?.name ?: "") }
    var phone by remember { mutableStateOf(s?.phone ?: "") }
    var address by remember { mutableStateOf(s?.address ?: "") }
    val options = CategoryNames.options().map { it.id to it.name }.toMutableList()
    // فئة المتجر الحالية قد تكون معطّلة: تبقى ظاهرة ليتمكن المدير من رؤيتها
    if (s != null && options.none { it.first == s.category }) options.add(s.category to categoryLabel(s.category))
    var category by remember { mutableStateOf(s?.category ?: options.firstOrNull()?.first ?: "grocery") }
    var active by remember { mutableStateOf((s?.status ?: "active") == "active") }
    var open by remember { mutableStateOf(s?.isOpen ?: true) }
    var email by remember { mutableStateOf("") }
    var invited by remember { mutableStateOf(false) }
    var pendingImage by remember { mutableStateOf<android.net.Uri?>(null) }
    // بعد أول حفظ ناجح لمتجر جديد نتذكر معرّفه حتى لا يتكرر الإنشاء عند إعادة المحاولة
    var createdId by remember { mutableStateOf<String?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) pendingImage = uri }
    val storeId = s?.id ?: createdId

    Screen(if (s == null) "متجر جديد" else "تعديل المتجر", onBack = onClose) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Section("صورة المتجر") {
                StorageImage(s?.imagePath, Modifier.size(120.dp).clip(RoundedCornerShape(12.dp)))
                ActionButton(
                    if (pendingImage != null) "تغيير الصورة المختارة" else "اختيار صورة للمتجر",
                    Modifier.fillMaxWidth().padding(top = 8.dp), tonal = true,
                ) { pick.launch("image/*") }
                if (pendingImage != null) Text("تم اختيار صورة جديدة. تُرفع عند الضغط على «حفظ».", color = Mq.Green)
                Text("تُصغَّر الصورة وتُضغط تلقائيًا. تظهر للزبائن في قائمة المتاجر.", color = Mq.Muted, style = MaterialTheme.typography.bodySmall)
            }
            Section("بيانات المتجر") {
                Field("اسم المتجر", name, { name = it })
                Field("هاتف المتجر", phone, { phone = it }, Modifier.fillMaxWidth().padding(top = 8.dp), keyboard = KeyboardType.Phone)
                Field("العنوان (اختياري)", address, { address = it }, Modifier.fillMaxWidth().padding(top = 8.dp))
                ChipRow(options, category) { category = it }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("المتجر نشط"); Switch(checked = active, onCheckedChange = { active = it })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("مفتوح الآن"); Switch(checked = open, onCheckedChange = { open = it })
                }
            }
            ActionButton(
                "حفظ", Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                enabled = name.trim().length >= 2 && phone.trim().length >= 6,
            ) {
                val status = if (active) "active" else "suspended"
                val id = Api.adminUpsertStore(storeId, name.trim(), category, phone.trim(), address.trim().ifBlank { null }, status, open)
                if (s == null) createdId = id
                val img = pendingImage
                if (img != null) {
                    val path = ImageUtil.uploadStoreImage(ctx, id, img)
                    Api.adminUpsertStore(id, name.trim(), category, phone.trim(), address.trim().ifBlank { null }, status, open, imagePath = path)
                    pendingImage = null
                }
                onClose()
            }
            if (storeId != null) {
                ActionButton("منتجات هذا المتجر", Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), tonal = true) { onProducts(storeId) }
                Section("دعوة تاجر لهذا المتجر") {
                    Text("اكتب بريد Gmail الخاص بالتاجر. عند أول دخول له بهذا البريد يصبح تاجرًا لهذا المتجر.", color = Mq.Muted)
                    Field("بريد التاجر", email, { email = it; invited = false }, Modifier.fillMaxWidth().padding(top = 8.dp), keyboard = KeyboardType.Email)
                    ActionButton("إرسال الدعوة", Modifier.fillMaxWidth().padding(top = 8.dp), enabled = email.contains("@")) {
                        Api.adminInvite(email.trim().lowercase(), "merchant", storeId)
                        invited = true
                    }
                    if (invited) Text("تم تسجيل الدعوة.", color = Mq.Green)
                }
            }
        }
    }
}

// ---------- فئات المتاجر ----------
@Composable
fun AdminCategoriesScreen(onBack: () -> Unit) {
    val cats = rememberLoadable { Repo.categories() }
    var editing by remember { mutableStateOf<Category?>(null) }
    var creating by remember { mutableStateOf(false) }

    Screen("فئات المتاجر", onBack = onBack, floating = {
        FloatingActionButton(onClick = { creating = true }) { Icon(Icons.Default.Add, contentDescription = "فئة جديدة") }
    }) { pad ->
        Column(Modifier.padding(pad)) {
            LoadableView(cats) { list ->
                LazyColumn {
                    if (list.isEmpty()) {
                        item {
                            Section("الفئات الافتراضية") {
                                Text("لم تُنشئ فئات بعد، فيعرض التطبيق: بقالة، صيدلية، أخرى. اضغط لإنشائها هنا لتعدّلها أو ترتّبها أو تضيف غيرها.", color = Mq.Muted)
                                ActionButton("إنشاء الفئات الافتراضية", Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                    Api.adminUpsertCategory("grocery", "بقالة", 1, true)
                                    Api.adminUpsertCategory("pharmacy", "صيدلية", 2, true)
                                    Api.adminUpsertCategory("other", "أخرى", 3, true)
                                }
                            }
                        }
                    }
                    items(list.sortedWith(compareBy({ it.order }, { it.name })), key = { it.id }) { c ->
                        MqCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp).clickable { editing = c }) {
                            Column(Modifier.padding(14.dp)) {
                                Text(c.name, style = MaterialTheme.typography.titleMedium)
                                Text("الترتيب ${c.order} • " + (if (c.isActive) "ظاهرة للزبائن" else "مخفية"), color = Mq.Muted)
                            }
                        }
                    }
                }
            }
        }
    }
    if (creating) CategoryDialog(null, nextOrder = ((cats as? com.maqdi.core.Loadable.Data)?.value?.maxOfOrNull { it.order } ?: 0L) + 1) { creating = false }
    editing?.let { c -> CategoryDialog(c, nextOrder = c.order) { editing = null } }
}

@Composable
private fun CategoryDialog(c: Category?, nextOrder: Long, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(c?.name ?: "") }
    var order by remember { mutableStateOf(nextOrder.toString()) }
    var active by remember { mutableStateOf(c?.isActive ?: true) }
    val orderVal = order.trim().toLongOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (c == null) "فئة جديدة" else "تعديل الفئة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("اسم الفئة (مثل: مخبوزات، ألبان، خضار)", name, { name = it })
                Field("الترتيب (الأصغر أولًا)", order, { order = it.filter { ch -> ch.isDigit() } }, keyboard = KeyboardType.Number)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("ظاهرة للزبائن"); Switch(checked = active, onCheckedChange = { active = it })
                }
                ActionButton("حفظ", enabled = name.trim().length >= 2 && orderVal != null) {
                    Api.adminUpsertCategory(c?.id, name.trim(), orderVal ?: 0L, active)
                    onDismiss()
                }
                if (c != null) {
                    ActionButton("حذف الفئة", destructive = true) {
                        Api.adminDeleteCategory(c.id)
                        onDismiss()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } },
    )
}

// ---------- المستخدمون ----------
@Composable
fun AdminUsersScreen(profile: Profile, onBack: () -> Unit) {
    val users = rememberLoadable { Repo.users() }
    var email by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("courier") }
    var invited by remember { mutableStateOf(false) }
    var permsFor by remember { mutableStateOf<UserRow?>(null) }
    val canManageUsers = profile.can("users")
    val canPerms = profile.can("permissions")

    permsFor?.let { PermissionsDialog(it) { permsFor = null } }

    Screen("المستخدمون", onBack = onBack) { pad ->
        LoadableView(users) { list ->
            LazyColumn(Modifier.padding(pad)) {
                if (canManageUsers) item {
                    Section("دعوة موصّل أو مدير") {
                        Field("البريد (Gmail)", email, { email = it; invited = false }, keyboard = KeyboardType.Email)
                        ChipRow(listOf("courier" to "موصّل", "admin" to "مدير"), role) { role = it }
                        ActionButton("إرسال الدعوة", enabled = email.contains("@")) {
                            Api.adminInvite(email.trim().lowercase(), role, null)
                            invited = true
                        }
                        if (invited) Text("تم تسجيل الدعوة. تُفعَّل عند أول دخول بهذا البريد.", color = Mq.Green)
                    }
                }
                items(list.sortedBy { it.role }, key = { it.uid }) { u ->
                    MqCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Text(u.name.ifBlank { u.email }, style = MaterialTheme.typography.titleMedium)
                            Text(u.email, color = Mq.Muted, style = MaterialTheme.typography.bodySmall)
                            Text(roleLabel(u.role) + " • " + (if (u.status == "active") "نشط" else "موقوف") + (if (u.isTestAccount) " • تجريبي" else ""))
                            if (u.role != "customer") {
                                val all = Permissions.all(u.role)
                                Text(
                                    "الصلاحيات: " + (u.permissions?.let { p -> "مخصّصة (${p.count { it in all }} من ${all.size})" } ?: "كاملة (افتراضية)"),
                                    color = Mq.Muted, style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            if (u.uid != profile.uid) {
                                if (canManageUsers) Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ActionButton(if (u.status == "active") "إيقاف" else "تفعيل", Modifier.weight(1f), tonal = true) {
                                        Api.adminSetUserStatus(u.uid, if (u.status == "active") "suspended" else "active", null)
                                    }
                                    ActionButton(if (u.isTestAccount) "إلغاء تجريبي" else "حساب تجريبي", Modifier.weight(1f), tonal = true) {
                                        Api.adminSetUserStatus(u.uid, null, !u.isTestAccount)
                                    }
                                }
                                if (canPerms && u.role != "customer") {
                                    ActionButton("تخصيص الصلاحيات", Modifier.fillMaxWidth().padding(top = 8.dp), tonal = true) { permsFor = u }
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
private fun PermissionsDialog(u: UserRow, onDismiss: () -> Unit) {
    val items = Permissions.catalog[u.role].orEmpty()
    val all = items.map { it.first }
    var selected by remember { mutableStateOf((u.permissions ?: all).filter { it in all }.toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("صلاحيات " + u.name.ifBlank { u.email } + " (" + roleLabel(u.role) + ")") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("ما لا تؤشّر عليه لن يستطيع هذا الحساب فعله أو رؤيته، حتى لو حاول من خارج التطبيق.", color = Mq.Muted, style = MaterialTheme.typography.bodySmall)
                items.forEach { (key, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { selected = if (key in selected) selected - key else selected + key },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = key in selected, onCheckedChange = { selected = if (it) selected + key else selected - key })
                        Text(label)
                    }
                }
                ActionButton("حفظ الصلاحيات", Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    // الاختيار الكامل = الافتراضي (يشمل أي صلاحية تُضاف مستقبلًا)
                    Api.adminSetPermissions(u.uid, if (selected.containsAll(all)) null else all.filter { it in selected })
                    onDismiss()
                }
                ActionButton("إرجاع الصلاحيات الكاملة", Modifier.fillMaxWidth().padding(top = 4.dp), tonal = true) {
                    Api.adminSetPermissions(u.uid, null)
                    onDismiss()
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } },
    )
}

// ---------- الإعدادات ----------
@Composable
fun AdminSettingsScreen(onBack: () -> Unit) {
    val settings = rememberLoadable { Repo.settings() }
    Screen("الرسوم والمُهَل", onBack = onBack) { pad ->
        Column(Modifier.padding(pad)) {
            LoadableView(settings) { s -> SettingsForm(s) }
        }
    }
}

@Composable
private fun SettingsForm(s: Settings) {
    var fee by remember(s) { mutableStateOf(s.deliveryFee.toString()) }
    var merchantMin by remember(s) { mutableStateOf(s.merchantReviewMinutes.toString()) }
    var customerMin by remember(s) { mutableStateOf(s.customerApprovalMinutes.toString()) }
    var enabled by remember(s) { mutableStateOf(s.ordersEnabled) }
    var maxOpen by remember(s) { mutableStateOf(s.maxOpenOrders.toString()) }
    var saved by remember { mutableStateOf(false) }
    val feeV = fee.toLongOrNull()
    val mV = merchantMin.toLongOrNull()
    val cV = customerMin.toLongOrNull()
    val oV = maxOpen.toLongOrNull()
    val valid = feeV != null && feeV in 0..1_000_000 && mV != null && mV in 1..1440 && cV != null && cV in 1..1440 && oV != null && oV in 1..20

    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Section("رسوم التوصيل") {
            Field("رسوم التوصيل (جنيه)", fee, { fee = it.filter { c -> c.isDigit() }; saved = false }, keyboard = KeyboardType.Number)
            Text("تسري على الطلبات الجديدة فقط. الطلبات الحالية تحتفظ برسومها.", color = Mq.Muted)
        }
        Section("المهل بالدقائق") {
            Field("مهلة مراجعة التاجر", merchantMin, { merchantMin = it.filter { c -> c.isDigit() }; saved = false }, keyboard = KeyboardType.Number)
            Field("مهلة موافقة الزبون", customerMin, { customerMin = it.filter { c -> c.isDigit() }; saved = false },
                Modifier.fillMaxWidth().padding(top = 8.dp), keyboard = KeyboardType.Number)
        }
        Section("سقف الطلبات المفتوحة لكل زبون") {
            Field("أقصى عدد (1 إلى 20)", maxOpen, { maxOpen = it.filter { c -> c.isDigit() }; saved = false }, keyboard = KeyboardType.Number)
            Text("يمنع زبونًا واحدًا من إغراق المتاجر بطلبات مفتوحة. يُحتسب كل طلب لم يُسلَّم أو يُلغَ بعد.", color = Mq.Muted)
        }
        Section("استقبال الطلبات") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("استقبال الطلبات مفعّل"); Switch(checked = enabled, onCheckedChange = { enabled = it; saved = false })
            }
        }
        ActionButton("حفظ", Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), enabled = valid) {
            Api.adminUpdateSettings(Settings(feeV ?: 0, mV ?: 15, cV ?: 30, enabled, oV ?: 3))
            saved = true
        }
        if (saved) Text("تم الحفظ.", modifier = Modifier.padding(16.dp), color = Mq.Green)
    }
}

// ---------- سجل التدقيق ----------
@Composable
fun AuditScreen(onBack: () -> Unit) {
    val logs = rememberLoadable { Repo.audit() }
    Screen("سجل التدقيق", onBack = onBack) { pad ->
        Column(Modifier.padding(pad)) {
            LoadableView(logs) { list ->
                if (list.isEmpty()) Empty("لا توجد عمليات مسجلة.")
                else LazyColumn {
                    items(list, key = { it.id }) { a ->
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(a.action, style = MaterialTheme.typography.titleMedium)
                            Text("${a.actorRole} • ${a.targetType}: ${a.targetId}", color = Mq.Muted, style = MaterialTheme.typography.bodySmall)
                            Text(dateTime(a.at), color = Mq.Muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

// ---------- المصروفات ----------
@Composable
fun ExpensesScreen(onBack: () -> Unit) {
    val expenses = rememberLoadable { Repo.expenses() }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var key by remember { mutableStateOf(newKey()) }

    Screen("المصروفات", onBack = onBack) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Section("إضافة مصروف") {
                Field("المبلغ", amount, { amount = it.filter { c -> c.isDigit() } }, keyboard = KeyboardType.Number)
                Field("النوع (وقود، صيانة...)", category, { category = it }, Modifier.fillMaxWidth().padding(top = 8.dp))
                Field("ملاحظة (اختياري)", note, { note = it }, Modifier.fillMaxWidth().padding(top = 8.dp))
                ActionButton(
                    "حفظ المصروف", Modifier.fillMaxWidth().padding(top = 8.dp),
                    enabled = (amount.toLongOrNull() ?: 0L) > 0 && category.trim().length >= 2,
                ) {
                    Api.adminExpense(amount.toLong(), category.trim(), note.trim().ifBlank { null }, key)
                    key = newKey(); amount = ""; category = ""; note = ""
                }
            }
            LoadableView(expenses) { list ->
                Section("آخر المصروفات") {
                    if (list.isEmpty()) Text("لا توجد مصروفات.", color = Mq.Muted)
                    list.forEach { e ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(e.category)
                                Text(dateTime(e.createdAt), color = Mq.Muted, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(money(e.amount), color = Mq.Red)
                        }
                    }
                }
            }
        }
    }
}

// ---------- تصدير CSV ----------
@Composable
fun ExportScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var range by remember { mutableStateOf("7") }
    val from = daysAgoKhartoum(range.toInt() - 1)
    val to = todayKhartoum()
    val a = isoStart(from)
    val b = isoEndExclusive(to)
    var summary by remember { mutableStateOf<Api.Summary?>(null) }

    Screen("ملخص مالي وتصدير", onBack = onBack) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Section("الفترة") {
                ChipRow(listOf("1" to "اليوم", "7" to "٧ أيام", "30" to "٣٠ يومًا"), range) { range = it; summary = null }
                Text("يشمل التقرير كل القيود المالية في الفترة (حتى 5000 قيد).", color = Mq.Muted, modifier = Modifier.padding(top = 6.dp))
            }
            ActionButton("عرض ملخص الفترة", Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), tonal = true, enabled = a != null && b != null) {
                summary = Api.adminSummary(a!!, b!!)
            }
            summary?.let { sm ->
                Section("ملخص الفترة") {
                    InfoRow("إيراد التوصيل", money(sm.deliveryRevenue))
                    InfoRow("المصروفات", money(sm.expenses))
                    InfoRow("الصافي (إيراد − مصروفات)", money(sm.netRevenue), bold = true)
                    InfoRow("حركة النقد (محصَّل − مدفوع للمتاجر)", money(sm.cashNet))
                    InfoRow("صافي مستحقات المتاجر في الفترة", money(sm.storePayableNet))
                    Text("مستحقات المتاجر ليست ربحًا للمنصة. الصافي أعلاه من إيراد التوصيل فقط.", color = Mq.Muted)
                }
            }
            ActionButton("تصدير ومشاركة", Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), enabled = a != null && b != null) {
                val csv = Api.adminCsv(a!!, b!!, null)
                val file = withContext(Dispatchers.IO) {
                    val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
                    File(dir, "maqdi-report-$from-to-$to.csv").also { it.writeText("\uFEFF" + csv) }
                }
                val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                ctx.startActivity(Intent.createChooser(send, "مشاركة التقرير"))
            }
        }
    }
}
