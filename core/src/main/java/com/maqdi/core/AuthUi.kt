package com.maqdi.core

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.common.SignInButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * زر «تسجيل الدخول بـ Google» الرسمي من خدمات Google Play (SignInButton): الشعار والنص والشكل من Google نفسها،
 * ونصه يتبع لغة الجهاز. الدخول نفسه عبر googleSignIn في Session.kt.
 */
@Composable
private fun GoogleButton(webClientId: String) {
    val activity = LocalContext.current as Activity
    val st = rememberAction()
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth()) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { ctx ->
                SignInButton(ctx).apply {
                    setSize(SignInButton.SIZE_WIDE)
                    setColorScheme(SignInButton.COLOR_LIGHT)
                }
            },
            update = { btn ->
                btn.isEnabled = !st.busy
                btn.setOnClickListener {
                    if (!st.busy) {
                        st.busy = true
                        st.error = null
                        scope.launch {
                            try {
                                googleSignIn(activity, webClientId)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                st.error = actionErrorText(e)
                            } finally {
                                st.busy = false
                            }
                        }
                    }
                }
            },
        )
        st.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
    }
}

/** شاشة الترحيب: شعار وعنوان وزر Google */
@Composable
fun LoginScreen(title: String, subtitle: String, webClientId: String) {
    Column(
        Modifier.fillMaxSize().background(Mq.Bg).navigationBarsPadding(),
    ) {
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(76.dp).background(Mq.Green, CircleShape), contentAlignment = Alignment.Center) {
                Text(title.trim().take(1), color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(18.dp))
            Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Mq.Ink)
            Spacer(Modifier.height(8.dp))
            Text(subtitle, color = Mq.Muted, textAlign = TextAlign.Center, fontSize = 15.sp)
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            GoogleButton(webClientId)
        }
    }
}

/** تأكيد تسجيل الخروج: يُستخدم من كل أماكن الخروج في التطبيقين */
@Composable
fun SignOutDialog(onDismiss: () -> Unit) {
    val activity = LocalContext.current as Activity
    val st = rememberAction()
    AlertDialog(
        onDismissRequest = { if (!st.busy) onDismiss() },
        title = { Text("تسجيل الخروج؟") },
        text = { Text("ستحتاج إلى تسجيل الدخول مرة أخرى لاستخدام التطبيق.") },
        confirmButton = {
            ActionButton("تسجيل الخروج", modifier = Modifier, destructive = true, state = st) {
                signOutAll(activity)
                onDismiss()
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !st.busy) { Text("إلغاء") } },
    )
}
