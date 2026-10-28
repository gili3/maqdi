package com.maqdi.core

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PHONE_RE = Regex("^\\+?[0-9]{6,15}$")

/** يوحّد الأرقام العربية/الفارسية إلى لاتينية ويحذف المسافات والشرطات */
private fun normalizePhone(raw: String): String = buildString {
    for (ch in raw.trim()) {
        when {
            ch in '\u0660'..'\u0669' -> append('0' + (ch - '\u0660'))
            ch in '\u06F0'..'\u06F9' -> append('0' + (ch - '\u06F0'))
            ch == ' ' || ch == '-' || ch == '(' || ch == ')' -> Unit
            else -> append(ch)
        }
    }
}

/**
 * الملف الشخصي المشترك بين التطبيقين: عرض وتعديل (الاسم، الهاتف، وعنوان الزبون) وحفظ.
 * - المسودة تبقى عند تبديل التبويبات (rememberSaveable)، والرجوع مع تغييرات غير محفوظة يطلب تأكيدًا.
 * - التحقق قبل الإرسال برسائل واضحة، والحفظ لا يُفعَّل إلا بوجود تغيير صالح.
 */
@Composable
fun ProfileScreen(profile: Profile, onBack: (() -> Unit)? = null, showAddress: Boolean = false) {
    var askOut by remember { mutableStateOf(false) }
    var name by rememberSaveable(profile.uid) { mutableStateOf(profile.name) }
    var phone by rememberSaveable(profile.uid) { mutableStateOf(profile.phone.orEmpty()) }
    var address by rememberSaveable(profile.uid) { mutableStateOf(profile.savedAddress.orEmpty()) }
    var saved by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    val st = rememberAction()

    val cleanName = name.trim()
    val cleanPhone = normalizePhone(phone)
    val cleanAddress = address.trim()
    val nameErr = when {
        cleanName.length < 2 -> "الاسم مطلوب (حرفان على الأقل)."
        cleanName.length > 80 -> "الاسم أطول من 80 حرفًا."
        else -> null
    }
    val phoneErr = if (cleanPhone.isNotEmpty() && !PHONE_RE.matches(cleanPhone)) "رقم الهاتف غير صحيح (أرقام فقط، من 6 إلى 15 رقمًا)." else null
    val addressErr = if (showAddress && cleanAddress.length > 200) "العنوان أطول من 200 حرف." else null
    val valid = nameErr == null && phoneErr == null && addressErr == null
    val dirty = cleanName != profile.name ||
        (cleanPhone != normalizePhone(profile.phone.orEmpty())) ||
        (showAddress && cleanAddress != profile.savedAddress.orEmpty().trim())

    fun touch() { saved = false; st.error = null }

    val leave: (() -> Unit)? = onBack?.let { back -> { if (dirty) { confirmLeave = true } else { back() } } }
    BackHandler(enabled = onBack != null && dirty) { confirmLeave = true }

    Screen("الملف الشخصي", onBack = leave) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            MqCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        Modifier.size(64.dp).background(Mq.GreenSoft, CircleShape),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            profile.name.ifBlank { profile.email }.trim().take(1),
                            color = Mq.Green, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(profile.name.ifBlank { "بلا اسم" }, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        Text(roleLabel(profile.role), color = Mq.Green, style = MaterialTheme.typography.bodySmall)
                        Text(
                            profile.email,
                            color = Mq.Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                        )
                    }
                }
            }
            Section("بياناتي") {
                Field("الاسم", name, { name = it.take(120); touch() }, Modifier.fillMaxWidth().padding(top = 8.dp), error = nameErr)
                Field(
                    "رقم الهاتف (اختياري)", phone, { phone = it.take(30); touch() },
                    Modifier.fillMaxWidth().padding(top = 4.dp), keyboard = KeyboardType.Phone, error = phoneErr,
                )
                if (showAddress) {
                    Field(
                        "العنوان الافتراضي", address, { address = it.take(300); touch() },
                        Modifier.fillMaxWidth().padding(top = 4.dp), error = addressErr,
                    )
                }
                Text(
                    "البريد مرتبط بحساب Google ولا يمكن تعديله من هنا.",
                    color = Mq.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp),
                )
            }
            ActionButton(
                "حفظ التغييرات", Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                enabled = dirty && valid, state = st,
            ) {
                Repo.updateProfile(
                    profile.uid, cleanName, cleanPhone.ifBlank { null },
                    if (showAddress) cleanAddress.ifBlank { null } else profile.savedAddress,
                )
                saved = true
            }
            if (dirty) {
                TextButton(
                    onClick = {
                        name = profile.name; phone = profile.phone.orEmpty(); address = profile.savedAddress.orEmpty(); touch()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp),
                ) { Text("تراجع عن التعديلات") }
            } else if (saved) {
                Text("تم الحفظ ✓", color = Mq.Green, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            }
            Spacer(Modifier.height(8.dp))
            ActionButton(
                "تسجيل الخروج", Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), destructive = true,
            ) { askOut = true }
        }
    }
    if (askOut) SignOutDialog(onDismiss = { askOut = false })

    if (confirmLeave && onBack != null) {
        ConfirmDialog(
            "تجاهل التغييرات؟", "لديك تعديلات لم تُحفظ بعد. إن رجعت الآن ستضيع.", "تجاهل",
            onDismiss = { confirmLeave = false },
        ) { onBack() }
    }
}
