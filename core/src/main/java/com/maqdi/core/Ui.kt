package com.maqdi.core

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import coil.compose.AsyncImage
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// ---------- السمة (عربية RTL، خط كبير) ----------
@Composable
fun MaqdiTheme(content: @Composable () -> Unit) {
    val colors = lightColorScheme(
        primary = Color(0xFF166534), onPrimary = Color.White,
        primaryContainer = Color(0xFFDCFCE7), onPrimaryContainer = Color(0xFF052E16),
        secondary = Color(0xFFB45309), background = Color(0xFFFAFAF9), surface = Color.White,
        error = Color(0xFFB91C1C),
    )
    val typo = Typography(
        bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
        bodySmall = TextStyle(fontSize = 14.sp),
        titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
        titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
        labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    )
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme = colors, typography = typo) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Screen(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floating: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع") }
                },
                actions = actions,
            )
        },
        bottomBar = bottomBar,
        floatingActionButton = floating,
        content = content,
    )
}

// ---------- التحميل والأخطاء ----------
sealed interface Loadable<out T> {
    data object Loading : Loadable<Nothing>
    data class Data<T>(val value: T) : Loadable<T>
    data class Err(val message: String) : Loadable<Nothing>
}

private fun errorText(e: Throwable): String = when {
    e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
        "لا تملك صلاحية عرض هذه البيانات."
    e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.FAILED_PRECONDITION ->
        "قاعدة البيانات ما زالت تجهّز الفهارس. انتظر دقائق ثم أعد المحاولة. (FAILED_PRECONDITION)"
    else -> "تعذّر تحميل البيانات. تحقق من الإنترنت وأعد المحاولة."
}

fun <T> Flow<T>.asLoadable(): Flow<Loadable<T>> =
    map<T, Loadable<T>> { Loadable.Data(it) }
        .onStart { emit(Loadable.Loading) }
        .catch { emit(Loadable.Err(errorText(it))) }

@Composable
fun <T> rememberLoadable(vararg keys: Any?, make: () -> Flow<T>): Loadable<T> {
    val flow = remember(*keys) { make().asLoadable() }
    val state by flow.collectAsState(initial = Loadable.Loading)
    return state
}

@Composable
fun <T> LoadableView(l: Loadable<T>, content: @Composable (T) -> Unit) {
    when (l) {
        is Loadable.Loading -> LoadingBox()
        is Loadable.Err -> ErrorBox(l.message)
        is Loadable.Data -> content(l.value)
    }
}

@Composable
fun LoadingBox() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun ErrorBox(message: String, onRetry: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
        if (onRetry != null) {
            Spacer(Modifier.size(16.dp))
            Button(onClick = onRetry) { Text("إعادة المحاولة") }
        }
    }
}

@Composable
fun Empty(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, color = Color.Gray)
    }
}

// ---------- الأزرار (تمنع النقر المزدوج) ----------
class ActionState {
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
}

@Composable
fun rememberAction(): ActionState = remember { ActionState() }

@Composable
fun ActionButton(
    text: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    tonal: Boolean = false,
    destructive: Boolean = false,
    state: ActionState = rememberAction(),
    onClick: suspend () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val onTap: () -> Unit = {
        if (!state.busy) {
            state.busy = true
            state.error = null
            scope.launch {
                try {
                    onClick()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: ApiException) {
                    state.error = e.userMessage
                } catch (e: Exception) {
                    state.error = "حدث خطأ غير متوقع. حاول مجددًا."
                } finally {
                    state.busy = false
                }
            }
        }
    }
    val btnModifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
    Column(modifier) {
        val label: @Composable () -> Unit = {
            if (state.busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Text(text)
        }
        when {
            tonal -> FilledTonalButton(onClick = onTap, enabled = enabled && !state.busy, modifier = btnModifier) { label() }
            destructive -> Button(
                onClick = onTap, enabled = enabled && !state.busy, modifier = btnModifier,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { label() }
            else -> Button(onClick = onTap, enabled = enabled && !state.busy, modifier = btnModifier) { label() }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp)) }
    }
}

// ---------- مكونات صغيرة ----------
@Composable
fun Field(
    label: String, value: String, onChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(), keyboard: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true, enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, modifier = modifier,
        singleLine = singleLine, enabled = enabled, keyboardOptions = KeyboardOptions(keyboardType = keyboard),
    )
}

@Composable
fun StatusChip(status: String) {
    val c = St.color(status)
    Surface(color = c.copy(alpha = 0.12f), shape = RoundedCornerShape(50)) {
        Text(
            St.label(status), color = c, style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun StatusSteps(status: String) {
    val i = St.steps.indexOf(status)
    if (i >= 0) {
        LinearProgressIndicator(
            progress = { (i + 1f) / St.steps.size },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
    }
}

@Composable
fun InfoRow(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.Gray)
        Spacer(Modifier.width(12.dp))
        Text(value, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.End)
    }
}

@Composable
fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(6.dp))
            content()
        }
    }
}

@Composable
fun Stepper(qty: Int, onChange: (Int) -> Unit, min: Int = 0, max: Int = 99) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledIconButton(onClick = { if (qty > min) onChange(qty - 1) }) { Text("−") }
        Text(qty.toString(), modifier = Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.titleMedium)
        FilledIconButton(onClick = { if (qty < max) onChange(qty + 1) }) { Text("+") }
    }
}

@Composable
fun ReasonDialog(title: String, confirmLabel: String, onDismiss: () -> Unit, onConfirm: suspend (String) -> Unit) {
    var reason by remember { mutableStateOf("") }
    val st = rememberAction()
    AlertDialog(
        onDismissRequest = { if (!st.busy) onDismiss() },
        title = { Text(title) },
        text = { Field("السبب", reason, { reason = it }, singleLine = false) },
        confirmButton = {
            ActionButton(confirmLabel, modifier = Modifier, enabled = reason.trim().length >= 3, state = st, destructive = true) {
                onConfirm(reason.trim())
                onDismiss()
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !st.busy) { Text("رجوع") } },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirmLabel: String, onDismiss: () -> Unit, onConfirm: suspend () -> Unit) {
    val st = rememberAction()
    AlertDialog(
        onDismissRequest = { if (!st.busy) onDismiss() },
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            ActionButton(confirmLabel, modifier = Modifier, state = st) {
                onConfirm()
                onDismiss()
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !st.busy) { Text("رجوع") } },
    )
}

private val urlCache = mutableMapOf<String, String>()

@Composable
fun StorageImage(path: String?, modifier: Modifier = Modifier) {
    val placeholder = Modifier.background(Color(0xFFE7E5E4))
    if (path.isNullOrBlank()) {
        Box(modifier.then(placeholder), contentAlignment = Alignment.Center) { Text("🛒") }
    } else {
        val url by produceState<String?>(initialValue = urlCache[path], path) {
            if (value == null) {
                value = try {
                    FirebaseStorage.getInstance().reference.child(path).downloadUrl.await().toString().also { urlCache[path] = it }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
            }
        }
        val u = url
        if (u == null) Box(modifier.then(placeholder)) else AsyncImage(
            model = u, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop,
        )
    }
}
