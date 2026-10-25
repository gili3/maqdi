package com.maqdi.core

import android.net.ConnectivityManager
import android.net.Network
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** يظهر عند انقطاع الإنترنت حتى لا يُعرض ما هو قديم على أنه مؤكد */
@Composable
fun ConnectionBanner() {
    val ctx = LocalContext.current
    var online by remember { mutableStateOf(true) }
    DisposableEffect(Unit) {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        online = cm.activeNetwork != null
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { online = true }
            override fun onLost(network: Network) { online = cm.activeNetwork != null }
        }
        cm.registerDefaultNetworkCallback(cb)
        onDispose { cm.unregisterNetworkCallback(cb) }
    }
    if (!online) {
        Text(
            "لا يوجد اتصال بالإنترنت. قد تكون البيانات المعروضة قديمة، ولن تنجح الإرسالات حتى يعود الاتصال.",
            color = Color.White, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().background(Color(0xFFB45309)).padding(8.dp),
        )
    }
}
