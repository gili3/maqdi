package com.maqdi.partnerapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.maqdi.core.MaqdiTheme
import com.maqdi.core.Notifier
import com.maqdi.core.SessionGate
import com.maqdi.partner.AdminApp
import com.maqdi.partner.CourierApp
import com.maqdi.partner.MerchantApp

/** تطبيق الشركاء: واجهة واحدة تختار الشاشات حسب دور الحساب (تاجر، مندوب، مدير). الزبون مرفوض. */
class MainActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        Notifier.clearAll(this) // فتح التطبيق يوقف التنبيه المتكرر
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.createChannel(this)
        setContent {
            MaqdiTheme {
                SessionGate(
                    appTitle = "مقضي للشركاء",
                    subtitle = "للتجار والمندوبين والإدارة.\nسجّل الدخول بالبريد الذي سجّلته الإدارة.",
                    webClientId = getString(R.string.default_web_client_id),
                    allowedRoles = setOf("merchant", "courier", "admin"),
                    wrongRoleMessage = "هذا التطبيق للتجار والمندوبين والإدارة فقط. استخدم تطبيق مقضي للزبائن.",
                ) { profile ->
                    when (profile.role) {
                        "merchant" -> MerchantApp(profile)
                        "courier" -> CourierApp(profile)
                        else -> AdminApp(profile)
                    }
                }
            }
        }
    }
}
