package io.github.yueeng.hacg.viewmodels

import android.os.Bundle
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import io.github.yueeng.hacg.utils.Article
import io.github.yueeng.hacg.utils.getParcelableCompat

class InfoWebViewModel(handle: SavedStateHandle, args: Bundle?) : ViewModel() {
    val web = handle.getLiveData<Pair<String, String>>("web")
    val error = handle.getLiveData("error", false)
    val magnet = handle.getLiveData<List<String>>("magnet", emptyList())
    val progress = handle.getLiveData("progress", false)
    val article: MutableLiveData<Article?> =
        handle.getLiveData("article", args?.getParcelableCompat("article"))
}