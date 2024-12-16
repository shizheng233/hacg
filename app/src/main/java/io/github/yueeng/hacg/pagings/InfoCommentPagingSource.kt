@file:Suppress("PrivatePropertyName")

package io.github.yueeng.hacg.pagings

import androidx.paging.PagingSource
import androidx.paging.PagingState
import io.github.yueeng.hacg.utils.Comment
import io.github.yueeng.hacg.utils.HAcg
import io.github.yueeng.hacg.utils.JWpdiscuzComment
import io.github.yueeng.hacg.utils.fromJsonOrNull
import io.github.yueeng.hacg.utils.gson
import io.github.yueeng.hacg.utils.httpPostAwait
import io.github.yueeng.hacg.viewmodels.InfoCommentViewModel
import org.jsoup.Jsoup

class InfoCommentPagingSource(
    private val _id: Int,
    private val sorting: () -> InfoCommentViewModel.Sorting
) : PagingSource<Pair<Int?, Int>, Comment>() {
    override suspend fun load(params: LoadParams<Pair<Int?, Int>>): LoadResult<Pair<Int?, Int>, Comment> =
        try {
            val (parentId, offset) = params.key!!
            val data = mapOf(
                "action" to "wpdLoadMoreComments",
                "sorting" to sorting().sort,
                "offset" to "$offset",
                "lastParentId" to "$parentId",
                "isFirstLoad" to (if (offset == 0) "1" else "0"),
                "wpdType" to "",
                "postId" to "$_id"
            )
            val json = HAcg.wpdiscuz.httpPostAwait(data)
            val comments = gson.fromJsonOrNull<JWpdiscuzComment>(json?.first)
            val list = Jsoup.parse(comments!!.data.commentList ?: "", HAcg.wpdiscuz)
                .select("body>.wpd-comment").map { Comment(it) }.toList()
            val next = if (comments.data.isShowLoadMore) {
                comments.data.lastParentId.toIntOrNull() to (offset + 1)
            } else {
                null
            }
            LoadResult.Page(list, null, next)
        } catch (e: Exception) {
            LoadResult.Error(e)
        }

    override fun getRefreshKey(state: PagingState<Pair<Int?, Int>, Comment>): Pair<Int?, Int>? =
        null
}

