package com.shohan.khatiyan.presentation.settings

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.shohan.khatiyan.BuildConfig
import com.shohan.khatiyan.core.BnDates
import com.shohan.khatiyan.data.backup.CsvExporter
import com.shohan.khatiyan.data.settings.AppSettings
import com.shohan.khatiyan.di.AppContainer
import com.shohan.khatiyan.ui.components.AppCard
import com.shohan.khatiyan.ui.components.ChoiceChipsRow
import com.shohan.khatiyan.ui.components.ConfirmDialog
import com.shohan.khatiyan.ui.components.KhatiyanScaffold
import com.shohan.khatiyan.ui.components.KhatiyanTextField
import com.shohan.khatiyan.ui.components.SectionTitle
import com.shohan.khatiyan.ui.theme.KhatiyanBrand
import com.shohan.khatiyan.utilities.BnText
import com.shohan.khatiyan.utilities.containerFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val settings: AppSettings = AppSettings(),
        val hasPin: Boolean = false,
        val working: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 6)
    val events = _events

    init {
        viewModelScope.launch {
            container.settings.settings.collect { s ->
                _state.value = _state.value.copy(settings = s)
            }
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(hasPin = container.settings.hasPin())
        }
    }

    fun refreshPin() {
        viewModelScope.launch {
            _state.value = _state.value.copy(hasPin = container.settings.hasPin())
        }
    }

    fun emit(msg: String) {
        viewModelScope.launch { _events.emit(msg) }
    }

    fun saveName(name: String) {
        viewModelScope.launch {
            container.settings.setUserName(name.trim())
            _events.emit("নাম সংরক্ষিত হয়েছে")
        }
    }

    fun setCurrency(label: String) {
        viewModelScope.launch { container.settings.setCurrencySymbol(label) }
    }

    fun setReminderEnabled(on: Boolean) {
        viewModelScope.launch {
            container.settings.setNotificationsEnabled(on)
            container.notificationsTurned(on)
            _events.emit(if (on) "প্রতিদিন রিমাইন্ডার চালু হয়েছে" else "রিমাইন্ডার বন্ধ করা হয়েছে")
        }
    }

    fun setReminderHour(hour: Int) {
        viewModelScope.launch {
            container.settings.setReminderHour(hour)
            if (_state.value.settings.notificationsEnabled) container.notificationsTurned(true)
            _events.emit("রিমাইন্ডারের সময় আপডেট হয়েছে")
        }
    }

    fun setAppLock(on: Boolean) {
        viewModelScope.launch { container.settings.setAppLockEnabled(on) }
    }

    fun setBiometric(on: Boolean) {
        viewModelScope.launch { container.settings.setBiometricEnabled(on) }
    }

    fun setSecure(on: Boolean) {
        viewModelScope.launch { container.settings.setSecureScreen(on) }
    }

    fun lockNow() {
        container.appLock.locked.value = true
    }

    fun setPin(pin: String, thenEnableLock: Boolean, onDone: () -> Unit) {
        viewModelScope.launch {
            container.settings.setPin(pin)
            if (thenEnableLock) container.settings.setAppLockEnabled(true)
            _state.value = _state.value.copy(hasPin = true)
            onDone()
        }
    }

    suspend fun verifyPin(pin: String): Boolean =
        withContext(Dispatchers.Default) { container.settings.verifyPin(pin) }

    fun backupTo(context: android.content.Context, uri: android.net.Uri, onDone: (String) -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(working = true)
            val result = runCatching { container.backupManager.exportTo(context, uri) }
            _state.value = _state.value.copy(working = false)
            result.fold(
                onSuccess = { count -> onDone("ব্যাকআপ তৈরি: ${BnText.toBnDigits(count.toString())}টি রেকর্ড") },
                onFailure = { onDone("ব্যাকআপ ব্যর্থ: ফাইলটি লেখা যায়নি") },
            )
        }
    }

    fun restoreFrom(context: android.content.Context, uri: android.net.Uri, onDone: (String) -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(working = true)
            val result = runCatching { container.backupManager.restoreFrom(context, uri) }
            _state.value = _state.value.copy(working = false)
            result.fold(
                onSuccess = { sum ->
                    onDone(
                        "ফেরানো সম্পন্ন (${BnDates.formatShort(BnDates.fromIso(sum.exportedAtIso) ?: BnDates.today())}) — " +
                            "${BnText.toBnDigits(sum.totalRows.toString())}টি রেকর্ড ফিরে এসেছে।",
                    )
                },
                onFailure = { e ->
                    val msg = if (e is com.shohan.khatiyan.data.backup.BackupFormatException) e.messageBn else "ফাইলটি পড়া যায়নি — সঠিক ব্যাকআপ ফাইল দিন।"
                    onDone("রিস্টোর ব্যর্থ: $msg")
                },
            )
        }
    }

    fun exportThisMonthCsv(context: android.content.Context, uri: android.net.Uri, onDone: (String) -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(working = true)
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val from = BnDates.monthStart(BnDates.today())
                    val csv = CsvExporter.transactionsCsv(container.db, from.toString(), BnDates.today().toString())
                    CsvExporter.writeTo(context, uri, csv)
                }
            }
            _state.value = _state.value.copy(working = false)
            onDone(if (result.isSuccess) "CSV রপ্তানি হয়েছে" else "CSV রপ্তানি ব্যর্থ")
        }
    }

    fun wipeAllData(onDone: () -> Unit) {
        viewModelScope.launch {
            container.db.clearAllTables()
            onDone()
            _events.emit("সব তথ্য মুছে ফেলা হয়েছে — অ্যাপ একবার বন্ধ করে চালু করুন।")
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController, container: AppContainer) {
    val vm: SettingsViewModel = viewModel(factory = containerFactory { SettingsViewModel(it) })
    val s by vm.state.collectAsStateWithLifecycle()
    val settings = s.settings
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = androidx.compose.material3.SnackbarHostState()
    LaunchedEffect(Unit) { vm.events.collect { snackbar.showSnackbar(it) } }

    var showTime by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf<String?>(null) } // "set" | "change" | null
    var confirmRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var showWipe by remember { mutableStateOf(false) }
    var pendingMessage by remember { mutableStateOf<String?>(null) }

    val backupLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) vm.backupTo(context, uri) { msg -> pendingMessage = msg }
    }
    val restoreLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) confirmRestoreUri = uri
    }
    val notifPermLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted -> if (!granted) pendingMessage = "নোটিফিকেশন পারমিশন দেওয়া হয়নি — রিমাইন্ডার দেখানো হতে পারে না।" }
    val csvLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) vm.exportThisMonthCsv(context, uri) { msg -> pendingMessage = msg }
    }
    LaunchedEffect(pendingMessage) {
        pendingMessage?.let {
            snackbar.showSnackbar(it)
            pendingMessage = null
        }
    }

    KhatiyanScaffold(
        title = "সেটিংস",
        onBack = { navController.popBackStack() },
        snackbarHostState = snackbar,
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppCard {
                Text("আপনার প্রোফাইল", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                var nameDraft by remember(settings.userName) { mutableStateOf(settings.userName) }
                KhatiyanTextField(nameDraft, { nameDraft = it }, "আপনার নাম")
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { vm.saveName(nameDraft) }) { Text("নাম সংরক্ষণ", color = MaterialTheme.colorScheme.primary) }
                }
                Spacer(Modifier.height(4.dp))
                Text("মুদ্রা প্রতীক", style = MaterialTheme.typography.titleSmall)
                ChoiceChipsRow(
                    options = listOf("৳", "₹", "$", "£"),
                    selected = settings.currencySymbol,
                    onSelect = vm::setCurrency,
                )
            }

            SectionTitle("রিমাইন্ডার")
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("দৈনিক বকেয়ার নোটিশন", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "বকেয়া কিস্তি/পরিশোধ থাকলে প্রতিদিন একবার মনে করিয়ে দেয় — ইন্টারনেট লাগে না।",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = settings.notificationsEnabled,
                        onCheckedChange = { on ->
                            vm.setReminderEnabled(on)
                            if (on && android.os.Build.VERSION.SDK_INT >= 33) {
                                notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Schedule, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "সময়: ${hourBn(settings.reminderHour)}",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { showTime = true }) { Text("পরিবর্তন") }
                }
            }

            SectionTitle("নিরাপত্তা")
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("অ্যাপ পিন লক", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            if (s.hasPin) "পিন সেট আছে — চালু করলে খোলার সময় চাইবে" else "৪–৮ সংখ্যার পিন সেট করুন",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = settings.appLockEnabled,
                        onCheckedChange = { on ->
                            if (on) {
                                if (s.hasPin) vm.setAppLock(true) else showPinDialog = "set"
                            } else {
                                vm.setAppLock(false)
                                vm.setBiometric(false)
                            }
                        },
                    )
                }
                if (s.hasPin) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { showPinDialog = "change" }) { Text("পিন পরিবর্তন") }
                        TextButton(onClick = { vm.lockNow() }) { Text("এখনই লক") }
                    }
                }
                if (settings.appLockEnabled && s.hasPin) {
                    val canBio = remember {
                        runCatching {
                            BiometricManager.from(context)
                                .canAuthenticate(BIOMETRIC_STRONG or BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
                        }.getOrDefault(false)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("বায়োমেট্রিক আনলক", style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (canBio) "ফিঙ্গারপ্রিন্ট/ফেস দিয়ে দ্রুত আনলক" else "এই ডিভাইসে বায়োমেট্রিক পাওয়া যায়নি",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = settings.biometricEnabled && canBio,
                            enabled = canBio,
                            onCheckedChange = { on ->
                                if (on) {
                                    authenticateWithBiometrics(context) { ok ->
                                        scope.launch {
                                            if (ok) vm.setBiometric(true) else snackbar.showSnackbar("বায়োমেট্রিক যাচাই সম্পন্ন হয়নি")
                                        }
                                    }
                                } else {
                                    vm.setBiometric(false)
                                }
                            },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("স্ক্রিনশট ব্লক", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "সুরক্ষার জন্য স্ক্রিনশট ও রিসেন্টস-প্রিভিউ আটকায়",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = settings.secureScreen, onCheckedChange = vm::setSecure)
                }
            }

            SectionTitle("ব্যাকআপ ও ডাটা")
            if (s.working) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("চলছে…", style = MaterialTheme.typography.bodySmall)
                }
            }
            AppCard {
                SettingAction(
                    Icons.Outlined.Backup,
                    "ব্যাকআপ ফাইল তৈরি",
                    "সব তথ্য একটি JSON ফাইলে — ড্রাইভ/ফোনে জমা রাখুন",
                ) { backupLauncher.launch(container.backupManager.suggestFileName()) }
                Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                SettingAction(
                    Icons.Outlined.Restore,
                    "ব্যাকআপ থেকে ফেরান",
                    "সাবধান: বর্তমান সব তথ্য মুছে ফাইলের তথ্য বসবে",
                ) { restoreLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
                Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                SettingAction(
                    Icons.Outlined.TableChart,
                    "এই মাসের লেনদেন CSV",
                    "এক্সসেল/গুগল শিটে খোলা যায় এমন ফাইল",
                ) {
                    csvLauncher.launch("khatiyan-${BnDates.today().year}-${BnText.toBnDigits(BnDates.today().monthValue.toString())}.csv")
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                SettingAction(
                    Icons.Outlined.Info,
                    "সব তথ্য মুছে ফেলুন",
                    "ডিভাইস থেকে স্থায়ীভাবে সব রেকর্ড মুছে যাবে। আগে ব্যাকআপ নিন।",
                    destructive = true,
                ) { showWipe = true }
            }

            SectionTitle("খতিয়ান সম্পর্কে")
            AppCard {
                Text("খতিয়ান (Khatiyan) v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "সম্পূর্ণ অফলাইন হিসাবের অ্যাপ — ইন্টারনেট পারমিশন নেই, কোনো বিজ্ঞাপন বা ট্র্যাকিং নেই, " +
                        "কোনো ডাটা সার্ভারে যায় না। সব তথ্য শুধু আপনার ফোনেই থাকে।",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text("তৈরি করেছেন: শোহান খান", style = MaterialTheme.typography.titleSmall)
                Text("যোগাযোগ: helloiamshohan@gmail.com", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Text(
                    "টীকা: সব টাকার অঙ্ক পয়সা এককে সংরক্ষিত হয় — দশমিকে কোনো রাউন্ডিং ভুল হয় না।",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showTime) {
        val tState = rememberTimePickerState(initialHour = settings.reminderHour, initialMinute = 0, is24Hour = false)
        AlertDialog(
            onDismissRequest = { showTime = false },
            title = { Text("রিমাইন্ডারের ঘণ্টা") },
            text = {
                Column {
                    Text("খতিয়ান প্রতিদিন এই ঘণ্টায় নোটিশন পাঠাবে (মিনিট ০০ ধরা হয়)।", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    TimePicker(state = tState)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.setReminderHour(tState.hour)
                        showTime = false
                    },
                ) { Text("ঠিক আছে", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("বাতিল") } },
            shape = MaterialTheme.shapes.extraLarge,
        )
    }

    showPinDialog?.let { mode ->
        PinSetupDialog(
            container = container,
            mode = mode,
            onDismiss = { showPinDialog = null },
            onDone = { msg ->
                showPinDialog = null
                vm.refreshPin()
                vm.emit(msg)
            },
        )
    }

    confirmRestoreUri?.let { uri ->
        ConfirmDialog(
            title = "রিস্টোর নিশ্চিত করুন",
            message = "রিস্টোর করলে বর্তমান সব তথ্য মুছে ফাইলের তথ্য বসবে। এটি আর ফেরানো যাবে না — তাই চাইলে আগে নতুন ব্যাকআপ নিয়ে নিন।",
            confirmLabel = "ফেরান",
            onConfirm = {
                confirmRestoreUri = null
                vm.restoreFrom(context, uri) { msg -> pendingMessage = msg }
            },
            onDismiss = { confirmRestoreUri = null },
            danger = true,
        )
    }

    if (showWipe) {
        ConfirmDialog(
            title = "সব মুছে ফেলবেন?",
            message = "দোকান, ঋণ, EMI, মানুষ, লেনদেন — সব রেকর্ড স্থায়ীভাবে মুছে যাবে। ব্যাকআপ না থাকলে তথ্য হারিয়ে যাবে।",
            confirmLabel = "হ্যাঁ, সব মুছুন",
            onConfirm = {
                showWipe = false
                vm.wipeAllData { vm.emit("সব তথ্য মুছে ফেলা হয়েছে") }
            },
            onDismiss = { showWipe = false },
            danger = true,
        )
    }
}

@Composable
private fun SettingAction(
    icon: ImageVector,
    title: String,
    subtitle: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .background(if (destructive) KhatiyanBrand.DangerBg else MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                null,
                tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = if (destructive) MaterialTheme.colorScheme.error else Color.Unspecified,
            )
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PinSetupDialog(
    container: AppContainer,
    mode: String, // "set" | "change"
    onDismiss: () -> Unit,
    onDone: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(if (mode == "change") "current" else "new1") }
    var first by remember { mutableStateOf("") }
    var entered by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val title = when (step) {
        "current" -> "বর্তমান পিন দিন"
        "new1" -> "নতুন পিন দিন"
        else -> "আবার লিখে নিশ্চিত করুন"
    }

    fun submit() {
        if (busy) return
        if (entered.length < 4) {
            error = "অন্তত ৪ সংখ্যা"
            return
        }
        error = null
        busy = true
        scope.launch {
            when (step) {
                "current" -> {
                    val ok = container.settings.verifyPin(entered)
                    busy = false
                    if (ok) {
                        entered = ""
                        step = "new1"
                    } else {
                        error = "পিন মেলেনি"
                        entered = ""
                    }
                }
                "new1" -> {
                    busy = false
                    first = entered
                    entered = ""
                    step = "new2"
                }
                else -> {
                    if (entered == first) {
                        container.settings.setPin(entered)
                        if (mode == "set") container.settings.setAppLockEnabled(true)
                        busy = false
                        onDone(if (mode == "set") "পিন সেট হয়েছে — অ্যাপ লক চালু" else "পিন পরিবর্তন হয়েছে")
                    } else {
                        busy = false
                        error = "দুবার একই পিন দিতে হবে"
                        entered = ""
                        step = "new1"
                    }
                }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 30.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Spacer(Modifier.height(10.dp))
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text("৪ থেকে ৮ সংখ্যা", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(4) { i ->
                        Box(
                            Modifier
                                .size(if (i < entered.length.coerceAtMost(4)) 13.dp else 11.dp)
                                .background(
                                    if (i < entered.length.coerceAtMost(4)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    CircleShape,
                                ),
                        )
                    }
                    if (entered.length > 4) {
                        Text("+${BnText.toBnDigits((entered.length - 4).toString())}", style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (error != null) {
                    Text(error ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                if (busy) {
                    CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.height(80.dp))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (row in listOf("123", "456", "789")) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { ch ->
                                    PinKey(ch.toString(), Modifier.weight(1f)) { entered = (entered + ch).take(8) }
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PinKey("মুছুন", Modifier.weight(1f)) { if (entered.isNotEmpty()) entered = entered.dropLast(1) }
                            PinKey("0", Modifier.weight(1f)) { entered = (entered + '0').take(8) }
                            PinKey("ঠিক", Modifier.weight(1f), accent = true) { submit() }
                        }
                    }
                }
                TextButton(onClick = onDismiss) { Text("বাতিল", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun PinKey(label: String, modifier: Modifier = Modifier, accent: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(58.dp)
            .background(
                if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                MaterialTheme.shapes.medium,
            )
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (label.length == 1) BnText.toBnDigits(label) else label,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium,
            color = if (accent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun hourBn(hour: Int): String {
    val ampm = when {
        hour < 4 -> "রাত"
        hour < 12 -> "সকাল"
        hour < 16 -> "দুপুর"
        hour < 19 -> "বিকেল"
        else -> "রাত"
    }
    val h12 = if (hour % 12 == 0) 12 else hour % 12
    return "$ampm ${BnText.toBnDigits(h12.toString())}টা"
}

private fun authenticateWithBiometrics(context: android.content.Context, onResult: (Boolean) -> Unit) {
    val activity = context as? androidx.fragment.app.FragmentActivity ?: run { onResult(false); return }
    val prompt = BiometricPrompt(
        activity,
        androidx.core.content.ContextCompat.getMainExecutor(context),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true)
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(false)
            override fun onAuthenticationFailed() = onResult(false)
        },
    )
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("খতিয়ান আনলক")
            .setSubtitle("পরিচয় যাচাই করুন")
            .setNegativeButtonText("বাতিল")
            .setAllowedAuthenticators(BIOMETRIC_STRONG or BIOMETRIC_WEAK)
            .build(),
    )
}
