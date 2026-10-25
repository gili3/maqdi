package com.maqdi.customer

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.maqdi.core.ActionButton
import com.maqdi.core.Field
import com.maqdi.core.Profile
import com.maqdi.core.Repo
import com.maqdi.core.Screen
import com.maqdi.core.Section
import com.maqdi.core.signOutAll

@Composable
fun AccountScreen(profile: Profile) {
    val activity = LocalContext.current as Activity
    var name by remember { mutableStateOf(profile.name) }
    var phone by remember { mutableStateOf(profile.phone ?: "") }
    var address by remember { mutableStateOf(profile.savedAddress ?: "") }
    var saved by remember { mutableStateOf(false) }

    Screen("حسابي") { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Section("بياناتي") {
                Text(profile.email)
                Field("الاسم", name, { name = it; saved = false }, Modifier.fillMaxWidth().padding(top = 8.dp))
                Field("رقم الهاتف", phone, { phone = it; saved = false }, Modifier.fillMaxWidth().padding(top = 8.dp), keyboard = KeyboardType.Phone)
                Field("العنوان الافتراضي", address, { address = it; saved = false }, Modifier.fillMaxWidth().padding(top = 8.dp))
            }
            ActionButton("حفظ", Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), enabled = name.trim().length >= 2) {
                Repo.updateProfile(profile.uid, name.trim(), phone.trim().ifBlank { null }, address.trim().ifBlank { null })
                saved = true
            }
            if (saved) Text("تم الحفظ.", modifier = Modifier.padding(horizontal = 16.dp))
            ActionButton("تسجيل الخروج", Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), tonal = true) {
                signOutAll(activity)
            }
        }
    }
}
