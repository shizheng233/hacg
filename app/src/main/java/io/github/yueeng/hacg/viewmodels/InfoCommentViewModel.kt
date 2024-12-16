package io.github.yueeng.hacg.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import io.github.yueeng.hacg.utils.Comment
import io.github.yueeng.hacg.pagings.InfoCommentPagingSource
import io.github.yueeng.hacg.utils.Paging

class InfoCommentViewModel(id: Int, handle: SavedStateHandle) : ViewModel() {
    enum class Sorting(val sort: String) {
        Vote("by_vote"), Newest("newest"), Oldest("oldest")
    }

    val progress = handle.getLiveData("progress", false)
    val sorting = handle.getLiveData("sorting", Sorting.Vote)
    val source = Paging(handle, 0 to 0) { InfoCommentPagingSource(id) { sorting.value!! } }
    val data = handle.getLiveData<List<Comment>>("data")
}