package com.maqdi.merchant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.maqdi.core.MaqdiTheme
import com.maqdi.core.Notifier
import com.maqdi.core.SessionGate
import com.maqdi.partner.MerchantApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.createChannel(this)
        setContent {
            MaqdiTheme {
                SessionGate(
                    appTitle = "مقضي للمتاجر",
                    subtitle = "لأصحاب المتاجر: استقبال الطلبات ومراجعتها وتجهيزها.\nسجّل الدخول بالبريد الذي سجّلته الإدارة.",
                    webClientId = getString(R.string.default_web_client_id),
                    allowedRoles = setOf("merchant"),
                    wrongRoleMessage = "هذا التطبيق لأصحاب المتاجر فقط.",
                ) { profile -> MerchantApp(profile) }
            }
        }
    }
}
