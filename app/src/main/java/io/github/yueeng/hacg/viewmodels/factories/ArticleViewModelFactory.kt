package io.github.yueeng.hacg.viewmodels.factories

import android.os.Bundle
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.savedstate.SavedStateRegistryOwner
import io.github.yueeng.hacg.viewmodels.ArticleViewModel

class ArticleViewModelFactory(owner: SavedStateRegistryOwner, private val args: Bundle? = null) :
    AbstractSavedStateViewModelFactory(owner, args) {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        key: String,
        modelClass: Class<T>,
        handle: SavedStateHandle
    ): T = ArticleViewModel(handle, args) as T
}