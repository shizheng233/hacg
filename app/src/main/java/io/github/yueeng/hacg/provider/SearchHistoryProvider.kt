package io.github.yueeng.hacg.provider

import android.content.SearchRecentSuggestionsProvider
import io.github.yueeng.hacg.BuildConfig

class SearchHistoryProvider : SearchRecentSuggestionsProvider() {
    companion object {
        const val AUTHORITY = "${BuildConfig.APPLICATION_ID}.SuggestionProvider"
        const val MODE: Int = DATABASE_MODE_QUERIES
    }

    init {
        setupSuggestions(AUTHORITY, MODE)
    }
}