package com.maqdi.courier

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.maqdi.core.MaqdiTheme
import com.maqdi.core.Notifier
import com.maqdi.core.SessionGate
import com.maqdi.partner.CourierApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.createChannel(this)
        setContent {
            MaqdiTheme {
                SessionGate(
                    appTitle = "مقضي للمندوبين",
                    subtitle = "لمندوبي التوصيل: استلام الطلبات الجاهزة وتسليمها.\nسجّل الدخول بالبريد الذي سجّلته الإدارة.",
                    webClientId = getString(R.string.default_web_client_id),
                    allowedRoles = setOf("courier"),
                    wrongRoleMessage = "هذا التطبيق للمندوبين فقط.",
                ) { profile -> CourierApp(profile) }
            }
        }
    }
}
