package com.maqdi.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.maqdi.core.MaqdiTheme
import com.maqdi.core.Notifier
import com.maqdi.core.SessionGate
import com.maqdi.partner.AdminApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.createChannel(this)
        setContent {
            MaqdiTheme {
                SessionGate(
                    appTitle = "مقضي للإدارة",
                    subtitle = "للإدارة: الطلبات والمتاجر والمالية والتقارير.\nسجّل الدخول بالبريد الذي سجّلته الإدارة.",
                    webClientId = getString(R.string.default_web_client_id),
                    allowedRoles = setOf("admin"),
                    wrongRoleMessage = "هذا التطبيق للإدارة فقط.",
                ) { profile -> AdminApp(profile) }
            }
        }
    }
}
