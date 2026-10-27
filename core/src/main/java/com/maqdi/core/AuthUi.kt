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
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

// ---------- الدخول بالهاتف: رقم + رمز SMS (توثيق Firebase الحقيقي للرقم) ----------
// الرقم موثَّق الملكية برمز يصل إلى الجوال. الخادم يمنح هذه الحسابات دور «زبون» فقط ولا يقبل إلا أرقام السودان.

/** اسم الحساب الجديد يُحفظ هنا كي يصل إلى ensureProfile (حساب الهاتف بلا displayName) */
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

// حسابات قديمة أُنشئت ببريد داخلي (قبل الدخول برمز SMS): تبقى تعمل ويُعرض رقمها
private const val LEGACY_PHONE_EMAIL_DOMAIN = "phone.maqdi.app"

/** هل هذا حساب هاتف؟ (قديم ببريد داخلي، أو جديد بلا بريد) لإخفاء البريد وعرض الرقم */
fun isPhoneAccountEmail(email: String) = email.isBlank() || email.endsWith("@$LEGACY_PHONE_EMAIL_DOMAIN")

fun phoneFromAccountEmail(email: String): String? =
    if (email.endsWith("@$LEGACY_PHONE_EMAIL_DOMAIN")) "+" + email.substringBefore('@') else null

/** السطر التقني الذي يُلحق بكل خطأ دخول: نوع الاستثناء والرمز والنص الأصلي من Firebase، ليُعرف السبب بالضبط */
private fun techDetail(e: Exception): String {
    val code = (e as? FirebaseAuthException)?.errorCode
    val msg = e.message?.take(300)
    return "\u2066[" + listOfNotNull(e.javaClass.simpleName, code, msg).joinToString(" | ") + "]\u2069"
}

private fun authError(e: Exception): ApiException {
    val text = when (e) {
        is FirebaseNetworkException -> "تعذّر الاتصال. تحقق من الإنترنت وأعد المحاولة."
        is FirebaseTooManyRequestsException -> "محاولات كثيرة من هذا الجهاز أو الرقم. انتظر قليلًا ثم أعد المحاولة."
        is FirebaseAuthException -> when (e.errorCode) {
            "ERROR_INVALID_VERIFICATION_CODE" -> "الرمز غير صحيح."
            "ERROR_SESSION_EXPIRED" -> "انتهت صلاحية الرمز. اطلب رمزًا جديدًا."
            "ERROR_INVALID_PHONE_NUMBER" -> "رقم الهاتف غير صحيح."
            "ERROR_QUOTA_EXCEEDED" -> "تجاوز المشروع حصة الرسائل. حاول لاحقًا أو تواصل مع الإدارة."
            "ERROR_USER_DISABLED" -> "هذا الحساب معطّل. تواصل مع الإدارة."
            "ERROR_TOO_MANY_REQUESTS" -> "محاولات كثيرة. انتظر قليلًا ثم أعد المحاولة."
            "ERROR_OPERATION_NOT_ALLOWED" -> "الدخول بالهاتف غير مفعّل في Firebase، أو منطقة هذا الرقم غير مسموحة في سياسة SMS."
            "ERROR_APP_NOT_AUTHORIZED" -> "التطبيق غير مصرَّح له: تحقق من بصمات SHA-1/SHA-256 وملف google-services.json."
            else -> "تعذّر إرسال الرمز أو التحقق منه."
        }
        else -> when {
            // أخطاء Firebase غير الموثَّقة بكود (مثل فشل بصمة التطبيق أو Play Integrity/reCAPTCHA) تظهر برسالتها الأصلية
            e.message?.contains("not authorized", true) == true || e.message?.contains("INVALID_APP_CREDENTIAL", true) == true ->
                "التطبيق غير مصرَّح له باستخدام Firebase Authentication: تحقق من بصمات SHA-1/SHA-256 وملف google-services.json."
            e.message?.contains("integrity", true) == true || e.message?.contains("recaptcha", true) == true ->
                "فشل التحقق من التطبيق (Play Integrity/reCAPTCHA). تحقق من تفعيل Play Integrity API وبصمة SHA-256."
            else -> "تعذّرت العملية."
        }
    }
    return ApiException(text + "\n" + techDetail(e), (e as? FirebaseAuthException)?.errorCode)
}

/** جلسة رمز مُرسَل: معرّف التحقق + رمز إعادة الإرسال */
class PhoneCodeSession(val verificationId: String, val resendToken: PhoneAuthProvider.ForceResendingToken)

/**
 * يرسل رمز SMS إلى الرقم. يرجع جلسة الرمز، أو null إن سُجّل الدخول فورًا (تحقق تلقائي من الجهاز).
 * مرّر [resend] لإعادة الإرسال.
 */
suspend fun phoneSendCode(activity: Activity, phone: String, name: String, resend: PhoneCodeSession? = null): PhoneCodeSession? {
    val n = normalizeSudanPhone(phone) ?: throw ApiException("رقم الهاتف غير صحيح.")
    PendingSignup.name = name.trim().ifBlank { null }
    val auth = FirebaseAuth.getInstance()
    return suspendCancellableCoroutine<PhoneCodeSession?> { cont ->
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                // تحقق تلقائي (قراءة الرمز أو تحقق فوري): ندخل مباشرة. قد يصل بعد onCodeSent فلا نستأنف مرتين.
                auth.signInWithCredential(credential).addOnCompleteListener { t ->
                    if (!cont.isActive) return@addOnCompleteListener
                    if (t.isSuccessful) cont.resume(null)
                    else cont.resumeWithException(authError(t.exception ?: Exception()))
                }
            }
            override fun onVerificationFailed(e: FirebaseException) {
                if (cont.isActive) cont.resumeWithException(authError(e))
            }
            override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                if (cont.isActive) cont.resume(PhoneCodeSession(verificationId, token))
            }
        }
        val b = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber("+$n")
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
        if (resend != null) b.setForceResendingToken(resend.resendToken)
        PhoneAuthProvider.verifyPhoneNumber(b.build())
    }
}

/** يتحقق من الرمز ويسجّل الدخول */
suspend fun phoneConfirmCode(session: PhoneCodeSession, code: String) {
    try {
        val cred = PhoneAuthProvider.getCredential(session.verificationId, code.trim())
        FirebaseAuth.getInstance().signInWithCredential(cred).await()
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
    val activity = LocalContext.current as Activity
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var session by remember { mutableStateOf<PhoneCodeSession?>(null) }
    var cooldown by remember { mutableIntStateOf(0) }
    val st = rememberAction()
    val resendSt = rememberAction()
    val phoneOk = normalizeSudanPhone(phone) != null

    LaunchedEffect(cooldown) { if (cooldown > 0) { delay(1000); cooldown -= 1 } }

    ModalBottomSheet(
        onDismissRequest = { if (!st.busy && !resendSt.busy) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Mq.Card,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().imePadding().navigationBarsPadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 22.dp),
        ) {
            val s = session
            if (s == null) {
                Text("الدخول برقم الهاتف", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Mq.Ink)
                Text(
                    "سنرسل رمز تحقق إلى جوالك. حساب جديد أو قديم، الخطوات نفسها.", color = Mq.Muted, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
                AuthField("الاسم (اختياري)", name, { name = it.take(80); st.error = null }, Icons.Default.Person)
                AuthField(
                    "رقم الهاتف", phone, { phone = it.take(20); st.error = null }, Icons.Default.Phone,
                    keyboard = KeyboardType.Phone, ltr = true, prefix = "\u2066+249\u2069 ",
                )
                Spacer(Modifier.height(18.dp))
                ActionButton("إرسال الرمز", Modifier.fillMaxWidth(), enabled = phoneOk, state = st) {
                    val sent = phoneSendCode(activity, phone, name)
                    if (sent != null) { session = sent; code = ""; cooldown = 60 }
                }
            } else {
                Text("أدخل رمز التحقق", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Mq.Ink)
                Text(
                    "أرسلنا رمزًا من 6 أرقام إلى \u2066+249 ${normalizeSudanPhone(phone)?.removePrefix("249").orEmpty()}\u2069",
                    color = Mq.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp),
                )
                AuthField(
                    "رمز التحقق", code, { code = latinDigits(it).take(6); st.error = null }, Icons.Default.Lock,
                    keyboard = KeyboardType.NumberPassword, ltr = true,
                )
                Spacer(Modifier.height(18.dp))
                ActionButton("تأكيد", Modifier.fillMaxWidth(), enabled = code.length == 6, state = st) {
                    phoneConfirmCode(s, code)
                }
                Spacer(Modifier.height(10.dp))
                ActionButton(
                    if (cooldown > 0) "إعادة الإرسال بعد $cooldown ثانية" else "إعادة إرسال الرمز",
                    Modifier.fillMaxWidth(), enabled = cooldown == 0, tonal = true, state = resendSt,
                ) {
                    val sent = phoneSendCode(activity, phone, name, s)
                    if (sent != null) { session = sent; code = ""; cooldown = 60 }
                }
                TextButton(
                    onClick = { session = null; code = ""; st.error = null; resendSt.error = null },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) { Text("تغيير الرقم") }
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
