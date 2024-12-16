package io.github.yueeng.hacg.viewmodels

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import io.github.yueeng.hacg.utils.Article
import io.github.yueeng.hacg.utils.Paging
import io.github.yueeng.hacg.pagings.ArticlePagingSource

class ArticleViewModel(private val handle: SavedStateHandle, args: Bundle?) : ViewModel() {
    var retry: Boolean
        get() = handle["retry"] ?: false
        set(value) = handle.set("retry", value)
    val title = handle.getLiveData<String>("title")
    val source =
        Paging(handle, args?.getString("url")) { ArticlePagingSource { title.postValue(it) } }
    val data = handle.getLiveData<List<Article>>("data")
    val last = handle.getLiveData("last", -1)
}