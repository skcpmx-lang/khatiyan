package com.shohan.khatiyan.presentation.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shohan.khatiyan.R
import com.shohan.khatiyan.core.BnDates
import com.shohan.khatiyan.data.local.entity.UserProfileEntity
import com.shohan.khatiyan.di.AppContainer
import com.shohan.khatiyan.ui.components.AmountInput
import com.shohan.khatiyan.ui.components.ChoiceChipsRow
import com.shohan.khatiyan.ui.components.KhatiyanTextField
import com.shohan.khatiyan.ui.components.PrimaryButton
import com.shohan.khatiyan.ui.theme.KhatiyanBrand
import kotlinx.coroutines.launch

/**
 * First-launch onboarding (Phase 7): welcome → what the app covers → privacy
 * promise → name + currency. Light-only, no account, nothing leaves the device.
 */
@Composable
fun OnboardingScreen(container: AppContainer) {
    val pagerState = rememberPagerState(pageCount = { 4 })
    val scope = rememberCoroutineScope()
    val notifPermLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { /* result handled by the system; reminders simply stay silent until granted */ }

    var userName by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("৳") }
    var error by remember { mutableStateOf<String?>(null) }

    val currencies = remember { listOf("৳" to "বাংলাদেশি টাকা", "₹" to "ভারতীয় টাকা", "$" to "ডলার", "£" to "পাউন্ড") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(KhatiyanBrand.Deep, MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(34.dp),
                )
            }
            Text("খতিয়ান", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            if (pagerState.currentPage > 0) {
                androidx.compose.material3.TextButton(onClick = {
                    scope.launch { pagerState.animateScrollToPage(3) }
                }) { Text("সরাসরি শুরু করুন", style = MaterialTheme.typography.labelLarge) }
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            beyondViewportPageCount = 1,
        ) { page ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                when (page) {
                    0 -> WelcomePage()
                    1 -> ModulesPage()
                    2 -> PrivacyPage()
                    else -> SetupPage(
                        name = userName,
                        onName = { userName = it; error = null },
                        currency = currency,
                        onCurrency = { currency = it },
                        error = error,
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(4) { idx ->
                val active = pagerState.currentPage == idx
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (active) 22.dp else 8.dp, 8.dp)
                        .background(
                            if (active) MaterialTheme.colorScheme.primary else KhatiyanBrand.Hairline,
                            CircleShape,
                        ),
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            text = if (pagerState.currentPage < 3) "পরবর্তী" else "শুরু করি",
            onClick = {
                if (pagerState.currentPage < 3) {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                } else {
                    if (userName.isBlank()) {
                        error = "নাম লিখুন — পরেও দিতে পারবেন।"
                    }
                    val name = userName.trim()
                    if (name.isEmpty() || name.length > 60) {
                        error = "১–৬০ অক্ষরের নাম লিখুন।"
                    } else {
                        if (android.os.Build.VERSION.SDK_INT >= 33) {
                            notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                        scope.launch {
                            container.db.profileDao().upsertProfile(
                                UserProfileEntity(
                                    id = 1,
                                    name = name,
                                    currencySymbol = currency,
                                    createdAtIso = BnDates.toIso(BnDates.today()),
                                ),
                            )
                            container.settings.completeOnboarding(name, currency)
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 26.dp),
        )
    }
}

@Composable
private fun WelcomePage() {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            "খতিয়ানে স্বাগতম",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "সব হিসাব, এক জায়গায়।",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(22.dp))
        Text(
            "দোকানের বাকি থেকে লোন, EMI থেকে ব্যক্তিগত ধার — সবকিছুর হিসাব এখন একদম পরিষ্কার। " +
                "আপনার ফোনে, আপনার হাতে।",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ModulesPage() {
    val items = listOf(
        Triple(Icons.Outlined.Storefront, "দোকানের বাকি", "প্রতিটি দোকানের আলাদা খাতা — পণ্য, পরিমাণ, দাম আর পরিশোধের হিসাব।"),
        Triple(Icons.Outlined.AccountBalance, "ব্যাংক / এনজিও লোন", "কিস্তির সময়, বকেয়া আর অবশিষ্ট — সব একনজরে।"),
        Triple(Icons.Outlined.PhoneAndroid, "পণ্যের কিস্তি (EMI)", "মোবাইল, ল্যাপটপ, ফ্রিজ — প্রতিটি কিস্তির তারিখ মনে করিয়ে দেবে।"),
        Triple(Icons.AutoMirrored.Outlined.Login, "ব্যক্তিগত ধার", "বন্ধু-আত্মীয়ের কাছ থেকে নেওয়া টাকা আর ফেরতের হিসাব।"),
        Triple(Icons.Outlined.Work, "আয়-ব্যয়", "প্রতিদিনের হিসাব ক্যাটাগরি অনুযায়ী, রিপোর্টসহ।"),
    )
    Column {
        Text("কী কী থাকবে", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(14.dp))
        items.forEach { (icon, title, desc) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f), MaterialTheme.shapes.medium),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun PrivacyPage() {
    Column {
        Text("আপনার তথ্য, আপনার কাছেই", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(14.dp))
        Icon(
            Icons.Outlined.PrivacyTip,
            null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(44.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "আপনার আর্থিক তথ্য আপনার ডিভাইসেই থাকবে।",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "কোনো অ্যাকাউন্ট খুলতে হবে না, কোনো সার্ভারে কিছু যায় না — অ্যাপের মধ্যে ইন্টারনেটের অনুমতিই নেই। " +
                "ব্যাকআপ ফাইলও আপনি চাইলেই, আপনার হাতে।",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SetupPage(
    name: String,
    onName: (String) -> Unit,
    currency: String,
    onCurrency: (String) -> Unit,
    error: String?,
) {
    Column {
        Text("দ্রুত শুরু", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(14.dp))
        Text("আপনার নাম", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        KhatiyanTextField(
            value = name,
            onValueChange = onName,
            label = "ড্যাশবোর্ড ও রিপোর্টে আপনার নাম দেখাবে",
            error = error,
        )
        Spacer(Modifier.height(18.dp))
        Text("মুদ্রা", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        CurrencyPicker(selected = currency, onSelect = onCurrency)
        Spacer(Modifier.height(8.dp))
        Text(
            "বাংলাদেশি টাকা (৳) ডিফল্ট রাখা আছে",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Start,
        )
    }
}

@Composable
private fun CurrencyPicker(selected: String, onSelect: (String) -> Unit) {
    val options = listOf("৳ বাংলাদেশি টাকা", "₹ Indian Rupee", "$ US Dollar", "£ Pound")
    val symbols = listOf("৳", "₹", "$", "£")
    val selectedIndex = symbols.indexOf(selected).coerceAtLeast(0)
    ChoiceChipsRow(
        options = options,
        selected = options[selectedIndex],
        onSelect = { label -> onSelect(symbols[options.indexOf(label)].ifEmpty { "৳" }) },
    )
}
