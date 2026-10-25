package com.maqdi.customer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.maqdi.core.MaqdiTheme
import com.maqdi.core.Notifier
import com.maqdi.core.SessionGate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(applicationContext)
        Notifier.createChannel(this)
        setContent {
            MaqdiTheme {
                SessionGate(
                    appTitle = "مقضي",
                    subtitle = "اطلب من متاجر قريتك ويصلك الطلب إلى بابك",
                    webClientId = getString(R.string.default_web_client_id),
                    allowedRoles = setOf("customer"),
                    wrongRoleMessage = "هذا التطبيق للزبائن فقط. استخدم تطبيق «مقضي للشركاء» لحسابك.",
                ) { profile -> CustomerApp(profile) }
            }
        }
    }
}
