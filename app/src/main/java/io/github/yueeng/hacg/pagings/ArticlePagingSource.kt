package io.github.yueeng.hacg.pagings

import androidx.paging.PagingSource
import androidx.paging.PagingState
import io.github.yueeng.hacg.utils.Article
import io.github.yueeng.hacg.utils.httpGetAwait
import io.github.yueeng.hacg.utils.jsoup

class ArticlePagingSource(private val title: (String) -> Unit) : PagingSource<String, Article>() {
    override suspend fun load(params: LoadParams<String>): LoadResult<String, Article> = try {
        val dom = params.key!!.httpGetAwait()!!.jsoup()
        listOf("h1.page-title>span", "h1#site-title", "title").asSequence()
            .map { dom.select(it).text() }
            .firstOrNull { it.isNotEmpty() }?.let(title::invoke)
        val articles = dom.select("article").map { o -> Article(o) }.toList()
        val next = dom.select("a.nextpostslink").lastOrNull()?.takeIf { "»" == it.text() }
            ?.attr("abs:href")
            ?: dom.select("#wp_page_numbers a").lastOrNull()?.takeIf { ">" == it.text() }
                ?.attr("abs:href")
            ?: dom.select("#nav-below .nav-previous a").firstOrNull()?.attr("abs:href")
        LoadResult.Page(articles, null, next)
    } catch (e: Exception) {
        LoadResult.Error(e)
    }

    override fun getRefreshKey(state: PagingState<String, Article>): String? = null
}