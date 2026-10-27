package com.maqdi.core

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

// ---------- حسابات الهاتف: الرقم + كلمة المرور بلا رمز SMS ----------
// يُحوَّل الرقم داخليًا إلى بريد مثل 249912345678@phone.maqdi.app؛ والخادم يمنح هذه الحسابات دور «زبون» فقط.

private const val PHONE_EMAIL_DOMAIN = "phone.maqdi.app"

/** اسم الحساب الجديد يُحفظ هنا لحظة الإنشاء كي يصل إلى ensureProfile قبل أن يتوفر displayName */
object PendingSignup { @Volatile var name: String? = null }

private fun latinDigits(raw: String): String = buildString {
    for (ch in raw) when {
        ch in '\u0660'..'\u0669' -> append('0' + (ch - '\u0660'))
        ch in '\u06F0'..'\u06F9' -> append('0' + (ch - '\u06F0'))
        ch.isDigit() -> append(ch)
    }
}

/** يرجع الرقم بصيغة 249XXXXXXXXX (جوال سوداني: 9 أرقام تبدأ بـ 9 أو 1)، أو null إن كان غير صالح */
fun normalizeSudanPhone(raw: String): String? {
    var d = latinDigits(raw)
    if (d.startsWith("00249")) d = d.removePrefix("00")
    if (d.startsWith("249")) d = d.removePrefix("249")
    d = d.removePrefix("0")
    return if (d.length == 9 && (d[0] == '9' || d[0] == '1')) "249$d" else null
}

private fun phoneEmail(normalized: String) = "$normalized@$PHONE_EMAIL_DOMAIN"

/** هل هذا الحساب حساب هاتف؟ (لإخفاء البريد الداخلي وعرض الرقم بدله) */
fun isPhoneAccountEmail(email: String) = email.endsWith("@$PHONE_EMAIL_DOMAIN")

fun phoneFromAccountEmail(email: String): String? =
    if (isPhoneAccountEmail(email)) "+" + email.substringBefore('@') else null

private fun authError(e: Exception): ApiException = when (e) {
    is FirebaseAuthUserCollisionException -> ApiException("هذا الرقم مسجّل مسبقًا. انتقل إلى «تسجيل دخول».")
    is FirebaseAuthWeakPasswordException -> ApiException("كلمة المرور ضعيفة. استخدم 8 أحرف أو أرقام على الأقل.")
    is FirebaseNetworkException -> ApiException("تعذّر الاتصال. تحقق من الإنترنت وأعد المحاولة.")
    is FirebaseAuthException -> when (e.errorCode) {
        "ERROR_USER_NOT_FOUND", "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL", "ERROR_INVALID_EMAIL" ->
            ApiException("الرقم أو كلمة المرور غير صحيحة.")
        "ERROR_USER_DISABLED" -> ApiException("هذا الحساب معطّل. تواصل مع الإدارة.")
        "ERROR_TOO_MANY_REQUESTS" -> ApiException("محاولات كثيرة. انتظر قليلًا ثم أعد المحاولة.")
        "ERROR_OPERATION_NOT_ALLOWED" -> ApiException("الدخول بالهاتف غير مفعّل بعد. تواصل مع الإدارة.")
        else -> ApiException("تعذّرت العملية. أعد المحاولة.")
    }
    else -> ApiException("تعذّرت العملية. تحقق من الاتصال وأعد المحاولة.")
}

suspend fun phoneSignUp(name: String, phone: String, password: String) {
    val n = normalizeSudanPhone(phone) ?: throw ApiException("رقم الهاتف غير صحيح.")
    PendingSignup.name = name.trim()
    try {
        FirebaseAuth.getInstance().createUserWithEmailAndPassword(phoneEmail(n), password).await()
    } catch (e: CancellationException) {
        PendingSignup.name = null; throw e
    } catch (e: Exception) {
        PendingSignup.name = null; throw authError(e)
    }
}

suspend fun phoneSignIn(phone: String, password: String) {
    val n = normalizeSudanPhone(phone) ?: throw ApiException("رقم الهاتف غير صحيح.")
    PendingSignup.name = null
    try {
        FirebaseAuth.getInstance().signInWithEmailAndPassword(phoneEmail(n), password).await()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw authError(e)
    }
}

// ---------- مكوّنات الشكل ----------
@Composable
private fun AuthField(
    label: String, value: String, onChange: (String) -> Unit, icon: androidx.compose.ui.graphics.vector.ImageVector,
    keyboard: KeyboardType = KeyboardType.Text, ltr: Boolean = false, password: Boolean = false, prefix: String? = null,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        shape = RoundedCornerShape(14.dp),
        leadingIcon = { Icon(icon, contentDescription = null) },
        prefix = prefix?.let { p -> { Text(p) } },
        trailingIcon = if (password) {
            {
                IconButton(onClick = { visible = !visible }) {
                    Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = "إظهار كلمة المرور")
                }
            }
        } else null,
        visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard),
        textStyle = if (ltr || password) LocalTextStyle.current.copy(textDirection = TextDirection.Ltr) else LocalTextStyle.current,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = Mq.Card, focusedContainerColor = Mq.GreenSoft,
            unfocusedBorderColor = Mq.Muted.copy(alpha = 0.45f), focusedBorderColor = Mq.Green,
            unfocusedLeadingIconColor = Mq.Muted, focusedLeadingIconColor = Mq.Green,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneSheet(onDismiss: () -> Unit) {
    var signup by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val st = rememberAction()
    val nameOk = name.trim().length >= 2
    val phoneOk = normalizeSudanPhone(phone) != null
    val passOk = password.length >= 8
    val valid = phoneOk && passOk && (!signup || nameOk)

    ModalBottomSheet(
        onDismissRequest = { if (!st.busy) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Mq.Card,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().imePadding().navigationBarsPadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 22.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                listOf(true to "إنشاء حساب", false to "تسجيل دخول").forEach { (isSignup, label) ->
                    val on = signup == isSignup
                    Column(Modifier.clickable { signup = isSignup; st.error = null }) {
                        Text(
                            label, fontSize = 16.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                            color = if (on) Mq.Green else Mq.Muted, modifier = Modifier.padding(bottom = 8.dp),
                        )
                        Box(Modifier.height(2.dp).fillMaxWidth().background(if (on) Mq.Green else Color.Transparent))
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Mq.Line))
            if (signup) AuthField("الاسم", name, { name = it.take(80); st.error = null }, Icons.Default.Person)
            AuthField(
                "رقم الهاتف", phone, { phone = it.take(20); st.error = null }, Icons.Default.Phone,
                keyboard = KeyboardType.Phone, ltr = true, prefix = "+249 ",
            )
            AuthField("كلمة المرور", password, { password = it.take(64); st.error = null }, Icons.Default.Lock, password = true)
            if (signup) {
                Text(
                    "8 أحرف أو أرقام على الأقل", color = Mq.Muted, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp, start = 4.dp),
                )
            }
            Spacer(Modifier.height(18.dp))
            ActionButton(
                if (signup) "إنشاء الحساب" else "تسجيل الدخول", Modifier.fillMaxWidth(),
                enabled = valid, state = st,
            ) {
                if (signup) phoneSignUp(name, phone, password) else phoneSignIn(phone, password)
            }
            if (!signup) {
                Text(
                    "نسيت كلمة المرور؟ تواصل مع الإدارة لإعادة تعيينها.", color = Mq.Muted, fontSize = 12.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        }
    }
}

/** شاشة الترحيب: شعار وزران فقط (الهاتف يفتح نافذة، وجوجل مباشر) */
@Composable
fun LoginScreen(title: String, subtitle: String, webClientId: String) {
    val activity = LocalContext.current as Activity
    var showPhone by remember { mutableStateOf(false) }
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
            ActionButton("المتابعة برقم الهاتف") { showPhone = true }
            Spacer(Modifier.height(10.dp))
            ActionButton("المتابعة مع جوجل", tonal = true) { googleSignIn(activity, webClientId) }
        }
    }
    if (showPhone) PhoneSheet(onDismiss = { showPhone = false })
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
