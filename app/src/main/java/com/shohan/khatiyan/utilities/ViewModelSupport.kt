package com.shohan.khatiyan.utilities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shohan.khatiyan.KhatiyanApp
import com.shohan.khatiyan.di.AppContainer

/**
 * One small factory helper so every ViewModel gets the [AppContainer] without
 * a DI framework: `viewModel(factory = containerFactory { ShopViewModel(it) })`.
 */
inline fun <reified VM : ViewModel> containerFactory(crossinline create: (AppContainer) -> VM): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as KhatiyanApp
            create(app.container)
        }
    }
