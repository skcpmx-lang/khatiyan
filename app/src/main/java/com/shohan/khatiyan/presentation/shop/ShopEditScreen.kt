package com.shohan.khatiyan.presentation.shop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.shohan.khatiyan.data.local.entity.ShopEntity
import com.shohan.khatiyan.di.AppContainer
import com.shohan.khatiyan.ui.components.AppCard
import com.shohan.khatiyan.ui.components.ChoiceChipsRow
import com.shohan.khatiyan.ui.components.ConfirmDialog
import com.shohan.khatiyan.ui.components.KhatiyanScaffold
import com.shohan.khatiyan.ui.components.KhatiyanTextField
import com.shohan.khatiyan.ui.components.PrimaryButton
import com.shohan.khatiyan.ui.components.SecondaryButton
import com.shohan.khatiyan.utilities.containerFactory
import kotlinx.coroutines.flow.MutableSharedFlow

class ShopEditViewModel(private val container: AppContainer, private val shopId: Long?) : ViewModel() {

    data class Form(
        val name: String = "",
        val ownerName: String = "",
        val phone: String = "",
        val address: String = "",
        val category: String = "",
        val note: String = "",
        val loaded: Boolean = false,
        val error: String? = null,
        val archived: Boolean = false,
    )

    val state = MutableSharedFlow<Form>()
    private var form = Form()

    val events = MutableSharedFlow<String>(extraBufferCapacity = 4)

    init {
        viewModelScopeLaunch()
    }

    private fun viewModelScopeLaunch() {
        viewModelScope.launch {
            if (shopId != null) {
                val shop = container.db.shopDao().getShop(shopId)
                form = if (shop != null) {
                    Form(
                        name = shop.name,
                        ownerName = shop.ownerName,
                        phone = shop.phone,
                        address = shop.address,
                        category = shop.category,
                        note = shop.note,
                        loaded = true,
                        archived = shop.archived,
                    )
                } else {
                    form.copy(loaded = true)
                }
            } else {
                form = form.copy(loaded = true)
            }
            state.emit(form)
        }
    }

    fun update(transform: (Form) -> Form) {
        form = transform(form)
        viewModelScope.launch { state.emit(form) }
    }

    fun save(onSaved: () -> Unit) {
        viewModelScope.launch {
            val current = form
            if (current.name.isBlank()) {
                update { it.copy(error = "দোকানের নাম লিখুন।") }
                return@launch
            }
            val entity = ShopEntity(
                id = shopId ?: 0L,
                name = current.name.trim(),
                ownerName = current.ownerName.trim(),
                phone = current.phone.trim(),
                address = current.address.trim(),
                category = current.category.trim(),
                note = current.note.trim(),
                archived = current.archived,
            )
            val result = runCatching { container.shopRepo.saveShop(entity) }
            result.exceptionOrNull()?.let { e ->
                if (e is com.shohan.khatiyan.utilities.FinanceValidationException) {
                    update { it.copy(error = e.messageBn) }
                } else {
                    update { it.copy(error = "সংরক্ষণ করা যায়নি — আবার চেষ্টা করুন।") }
                }
                return@launch
            }
            events.emit(if (shopId == null) "দোকান যোগ হয়েছে" else "দোকানের তথ্য আপডেট হয়েছে")
            onSaved()
        }
    }

    fun delete(onDone: () -> Unit) {
        val id = shopId ?: return
        viewModelScope.launch {
            container.shopRepo.deleteShop(id)
            events.emit("দোকান ও তার পুরো হিসাব মুছে ফেলা হয়েছে")
            onDone()
        }
    }

    fun toggleArchive(onDone: () -> Unit) {
        val id = shopId ?: return
        viewModelScope.launch {
            container.shopRepo.setArchived(id, !form.archived)
            onDone()
        }
    }
}

private val SHOP_CATEGORIES = listOf("মুদি", "কাঁচাবাজার", "ফার্মেসি", "ইলেকট্রনিক্স", "দুধের দোকান", "কাপড়", "নির্মাণসামগ্রী", "অন্যান্য")

@Composable
fun ShopEditScreen(navController: NavController, container: AppContainer, shopId: Long?) {
    val vm: ShopEditViewModel = viewModel(factory = containerFactory { ShopEditViewModel(it, shopId) })
    var form by remember { mutableStateOf(ShopEditViewModel.Form()) }
    var showDelete by remember { mutableStateOf(false) }
    val snackbar = androidx.compose.material3.SnackbarHostState()

    LaunchedEffect(Unit) { vm.state.collect { form = it } }
    LaunchedEffect(Unit) { vm.events.collect { snackbar.showSnackbar(it) } }

    KhatiyanScaffold(
        title = if (shopId == null) "নতুন দোকান" else "দোকান সম্পাদনা",
        onBack = { navController.popBackStack() },
        snackbarHostState = snackbar,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppCard {
                Text("দোকানের তথ্য", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                KhatiyanTextField(
                    value = form.name,
                    onValueChange = { v -> vm.update { it.copy(name = v, error = null) } },
                    label = "দোকানের নাম *",
                    error = form.error,
                )
                Spacer(Modifier.height(8.dp))
                KhatiyanTextField(value = form.ownerName, onValueChange = { v -> vm.update { it.copy(ownerName = v) } }, label = "মালিকের নাম")
                Spacer(Modifier.height(8.dp))
                KhatiyanTextField(
                    value = form.phone,
                    onValueChange = { v -> vm.update { it.copy(phone = v.filter { c -> c.isDigit() || c == '+' || c == '-' || c == ' ' }.take(18)) } },
                    label = "ফোন নম্বর",
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone,
                )
                Spacer(Modifier.height(8.dp))
                KhatiyanTextField(value = form.address, onValueChange = { v -> vm.update { it.copy(address = v) } }, label = "ঠিকানা", singleLine = false, minLines = 2)
            }

            AppCard {
                Text("ক্যাটাগরি", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                ChoiceChipsRow(
                    options = SHOP_CATEGORIES,
                    selected = form.category.ifBlank { SHOP_CATEGORIES.first() },
                    onSelect = { c -> vm.update { it.copy(category = if (it.category == c) "" else c) } },
                )
                Spacer(Modifier.height(10.dp))
                KhatiyanTextField(value = form.note, onValueChange = { v -> vm.update { it.copy(note = v) } }, label = "নোট (ঐচ্ছিক)", singleLine = false, minLines = 2)
            }

            PrimaryButton(
                text = "সংরক্ষণ করুন",
                onClick = { vm.save { navController.popBackStack() } },
                modifier = Modifier.fillMaxWidth(),
                enabled = form.loaded,
            )

            if (shopId != null) {
                SecondaryButton(
                    text = if (form.archived) "আর্কাইভ থেকে ফিরিয়ে আনুন" else "দোকান আর্কাইভ করুন",
                    onClick = { vm.toggleArchive { navController.popBackStack() } },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "মুছে ফেললে পুরো খাতা (বাকি, পণ্য, পরিশোধ)সহ মুছে যাবে — প্রথমে ব্যাকআপ নিন।",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                ) {
                    androidx.compose.material3.TextButton(
                        onClick = { showDelete = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("এই দোকান ও হিসাব মুছুন", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
                    }
                }
                if (showDelete) {
                    ConfirmDialog(
                        title = "নিশ্চিতভাবে মুছেন?",
                        message = "দোকান “${form.name}”-এর সব বাকি, পণ্যের এন্ট্রি ও পরিশোধের রেকর্ড স্থায়ীভাবে মুছে যাবে। এটি ফেরানো যাবে না (ব্যাকআপ থাকলে রিস্টোর করা যাবে)।",
                        confirmLabel = "হ্যাঁ, সব মুছে দিন",
                        onConfirm = { showDelete = false; vm.delete { navController.popBackStack() } },
                        onDismiss = { showDelete = false },
                        danger = true,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
