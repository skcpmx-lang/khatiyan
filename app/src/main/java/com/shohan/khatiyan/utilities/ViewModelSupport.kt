package com.shohan.khatiyan.utilities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shohan.khatiyan.KhatiyanApp
import com.shohan.khatiyan.di.AppContainer

/**
 * One small factory helper so every ViewModel gets the [AppContainer] without
 * a DI framework: `viewModel(factory = containerFactory { ShopViewModel(it) })`.
 */
fun <VM : ViewModel> containerFactory(create: (AppContainer) -> VM): ViewModelProvider.Factory =
    viewModelFactory {
        initializer { extras: CreationExtras ->
            val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as KhatiyanApp
            create(app.container)
        }
    }
