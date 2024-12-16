package io.github.yueeng.hacg.viewmodels.factories

import android.os.Bundle
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.savedstate.SavedStateRegistryOwner
import io.github.yueeng.hacg.viewmodels.WebViewModel

class WebViewModelFactory(owner: SavedStateRegistryOwner, private val defaultArgs: Bundle? = null) : AbstractSavedStateViewModelFactory(owner, defaultArgs) {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(key: String, modelClass: Class<T>, handle: SavedStateHandle): T = WebViewModel(
        handle,
        defaultArgs
    ) as T
}