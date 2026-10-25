package com.maqdi.core

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class Ready(val profile: Profile) : SessionState
    data class Failed(val message: String) : SessionState
}

class SessionViewModel : ViewModel() {
    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state
    private var job: Job? = null
    private val auth = FirebaseAuth.getInstance()

    private val listener = FirebaseAuth.AuthStateListener { a ->
        val u = a.currentUser
        if (u == null) {
            job?.cancel()
            _state.value = SessionState.SignedOut
        } else {
            begin(u.uid, u.displayName)
        }
    }

    init { auth.addAuthStateListener(listener) }

    override fun onCleared() { auth.removeAuthStateListener(listener) }

    fun retry() { auth.currentUser?.let { begin(it.uid, it.displayName) } }

    private fun begin(uid: String, name: String?) {
        job?.cancel()
        _state.value = SessionState.Loading
        job = viewModelScope.launch {
            try {
                Api.ensureProfile(name)
                launch {
                    try {
                        val token = FirebaseMessaging.getInstance().token.await()
                        Repo.addToken(uid, token)
                    } catch (e: CancellationException) { throw e } catch (_: Exception) { }
                }
                Repo.profile(uid).collect { p ->
                    _state.value = if (p == null) SessionState.Loading else SessionState.Ready(p)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                _state.value = SessionState.Failed(e.userMessage)
            } catch (e: Exception) {
                _state.value = SessionState.Failed("تعذّر تحميل حسابك. تحقق من الاتصال وأعد المحاولة.")
            }
        }
    }
}

suspend fun googleSignIn(activity: Activity, webClientId: String) {
    try {
        val cm = CredentialManager.create(activity)
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val cred = cm.getCredential(activity, request).credential
        if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val google = GoogleIdTokenCredential.createFrom(cred.data)
            FirebaseAuth.getInstance()
                .signInWithCredential(GoogleAuthProvider.getCredential(google.idToken, null)).await()
        } else {
            throw ApiException("نوع الحساب غير مدعوم.")
        }
    } catch (e: GetCredentialCancellationException) {
        throw ApiException("تم إلغاء تسجيل الدخول.")
    } catch (e: NoCredentialException) {
        throw ApiException("لا يوجد حساب Google على هذا الهاتف. أضف حسابًا ثم أعد المحاولة.")
    } catch (e: GetCredentialException) {
        throw ApiException("تعذّر تسجيل الدخول بـ Google. (${e.type})")
    } catch (e: ApiException) {
        throw e
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw ApiException("تعذّر تسجيل الدخول. تحقق من الاتصال وحاول مجددًا.")
    }
}

suspend fun signOutAll(activity: Activity) {
    FirebaseAuth.getInstance().signOut()
    try { CredentialManager.create(activity).clearCredentialState(ClearCredentialStateRequest()) } catch (_: Exception) { }
}

@Composable
fun LoginScreen(title: String, subtitle: String, webClientId: String) {
    val activity = LocalContext.current as Activity
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        ActionButton("تسجيل الدخول بحساب Google") { googleSignIn(activity, webClientId) }
    }
}

@Composable
private fun Blocked(message: String) {
    val activity = LocalContext.current as Activity
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(24.dp))
        ActionButton("تسجيل الخروج", tonal = true) { signOutAll(activity) }
    }
}

/** بوابة الدخول المشتركة: تسجيل الدخول ثم التحقق من الحساب والدور قبل عرض التطبيق */
@Composable
fun SessionGate(
    appTitle: String, subtitle: String, webClientId: String,
    allowedRoles: Set<String>, wrongRoleMessage: String,
    content: @Composable (Profile) -> Unit,
) {
    val vm: SessionViewModel = viewModel()
    val s by vm.state.collectAsState()
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    when (val st = s) {
        is SessionState.Loading -> LoadingBox()
        is SessionState.SignedOut -> LoginScreen(appTitle, subtitle, webClientId)
        is SessionState.Failed -> Column(Modifier.fillMaxSize()) {
            ErrorBox(st.message, onRetry = { vm.retry() })
        }
        is SessionState.Ready -> {
            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            when {
                st.profile.status != "active" -> Blocked("حسابك موقوف. تواصل مع الإدارة.")
                st.profile.role !in allowedRoles -> Blocked(wrongRoleMessage)
                else -> content(st.profile)
            }
        }
    }
}
