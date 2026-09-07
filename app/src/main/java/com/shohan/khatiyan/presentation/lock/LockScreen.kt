package com.shohan.khatiyan.presentation.lock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.ui.res.painterResource
import com.shohan.khatiyan.di.AppContainer
import com.shohan.khatiyan.ui.components.PrimaryButton
import com.shohan.khatiyan.utilities.BnText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * App-lock gate (Phase 29): numeric PIN always, biometric shortcut when the
 * user enabled it. Auto-prompts biometrics on arrival; PIN pad otherwise.
 * The PIN itself never leaves [com.shohan.khatiyan.security.PinCrypto]'s
 * constant-time verify path.
 */
@Composable
fun LockScreen(container: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var verifying by remember { mutableStateOf(false) }
    var biometricAvailable by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val s = container.settings.current()
        biometricAvailable = if (s.biometricEnabled) {
            runCatching {
                BiometricManager.from(context)
                    .canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
            }.getOrDefault(false)
        } else {
            false
        }
        if (biometricAvailable) {
            promptBiometric(context, container) {
                container.appLock.unlock()
            }
        }
    }

    fun submit() {
        if (pin.length < 4) {
            error = "পিন অন্তত ৪ সংখ্যার।"
            return
        }
        if (verifying) return
        error = null
        scope.launch {
            verifying = true
            val ok = withContext(Dispatchers.Default) { container.settings.verifyPin(pin) }
            verifying = false
            if (ok) {
                pin = ""
                container.appLock.unlock()
            } else {
                pin = ""
                error = "পিন মেলেনি — আবার চেষ্টা করুন।"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text("খতিয়ান লক করা আছে", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "চালিয়ে যেতে পিন দিন",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(8) { idx ->
                Box(
                    modifier = Modifier
                        .size(11.dp)
                        .background(
                            if (idx < pin.length) MaterialTheme.colorScheme.primary else Color(0xFFDAD7CC),
                            CircleShape,
                        ),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        if (error != null) {
            Text(error ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(22.dp))
        val keys = listOf(
            listOf("১", "২", "৩"),
            listOf("৪", "৫", "৬"),
            listOf("৭", "৮", "৯"),
        )
        keys.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { key ->
                    KeypadButton(key) { pin = (pin + toAscii(key)).take(8) }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                if (biometricAvailable) {
                    Icon(
                        Icons.Outlined.Fingerprint,
                        contentDescription = "বায়োমেট্রিক দিয়ে আনলক",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(34.dp)
                            .clickable { promptBiometric(context, container) { container.appLock.unlock() } },
                    )
                }
            }
            KeypadButton("০") { pin = (pin + "0").take(8) }
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.AutoMirrored.Outlined.Backspace,
                    contentDescription = "মুছুন",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .size(30.dp)
                        .clickable { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
                )
            }
        }

        Spacer(Modifier.height(22.dp))
        PrimaryButton(
            text = if (verifying) "যাচাই হচ্ছে…" else "আনলক করুন",
            onClick = ::submit,
            modifier = Modifier.fillMaxWidth(),
            enabled = pin.length >= 4 && !verifying,
        )
    }
}

private fun toAscii(bn: String): String = BnText.fromBnDigits(bn)

@Composable
private fun KeypadButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .background(MaterialTheme.colorScheme.surface, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
    }
}

private fun promptBiometric(
    context: android.content.Context,
    container: AppContainer,
    onSuccess: () -> Unit,
) {
    val activity = context as? androidx.fragment.app.FragmentActivity ?: return
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(context),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }
        },
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("খতিয়ান আনলক করুন")
        .setSubtitle("আঙুলের ছাপ বা পাসওয়ার্ড দিয়ে আনলক করুন")
        .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
        .build()
    runCatching { prompt.authenticate(info) }
}
