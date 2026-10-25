package com.maqdi.partner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.maqdi.core.MaqdiTheme
import com.maqdi.core.Notifier
import com.maqdi.core.SessionGate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.createChannel(this)
        setContent {
            MaqdiTheme {
                SessionGate(
                    appTitle = "مقضي للشركاء",
                    subtitle = "للتجار والموصّلين وإدارة مقضي.\nسجّل الدخول بالبريد الذي سجّلته الإدارة.",
                    webClientId = getString(R.string.default_web_client_id),
                    allowedRoles = setOf("merchant", "admin", "courier"),
                    wrongRoleMessage = "هذا التطبيق للتجار والموصّلين والإدارة. للطلب استخدم تطبيق «مقضي».",
                ) { profile -> PartnerApp(profile) }
            }
        }
    }
}
