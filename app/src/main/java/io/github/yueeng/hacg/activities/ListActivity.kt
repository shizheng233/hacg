package io.github.yueeng.hacg.activities

import android.app.SearchManager
import android.net.Uri
import android.os.Bundle
import android.provider.SearchRecentSuggestions
import android.view.MenuItem
import android.view.View
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import io.github.yueeng.hacg.R
import io.github.yueeng.hacg.databinding.ActivityListBinding
import io.github.yueeng.hacg.fragment.ArticleFragment
import io.github.yueeng.hacg.provider.SearchHistoryProvider
import io.github.yueeng.hacg.utils.HAcg
import io.github.yueeng.hacg.utils.SwipeFinishActivity
import io.github.yueeng.hacg.utils.TRANSPARENT
import io.github.yueeng.hacg.utils.arguments
import io.github.yueeng.hacg.utils.string

class ListActivity : SwipeFinishActivity(), OnApplyWindowInsetsListener {

    private lateinit var binding: ActivityListBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListBinding.inflate(layoutInflater)
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.TRANSPARENT)
        setContentView(binding.root) {
            setSupportActionBar(binding.toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            ViewCompat.setOnApplyWindowInsetsListener(binding.root, this)
            val (url: String?, name: String?) = intent.let { i ->
                when {
                    i.hasExtra("url") -> (i.getStringExtra("url") to i.getStringExtra("name"))
                    i.hasExtra(SearchManager.QUERY) -> {
                        val key = i.getStringExtra(SearchManager.QUERY)
                        val suggestions = SearchRecentSuggestions(
                            this,
                            SearchHistoryProvider.AUTHORITY,
                            SearchHistoryProvider.MODE
                        )
                        suggestions.saveRecentQuery(key, null)
                        ("""${HAcg.wordpress}/?s=${Uri.encode(key)}&submit=%E6%90%9C%E7%B4%A2""" to key)
                    }

                    else -> null to null
                }
            }
            if (url == null) {
                finish()
                return@setContentView
            }
            title = name
            val transaction = supportFragmentManager.beginTransaction()
            val fragment = supportFragmentManager.findFragmentById(R.id.container)
                .takeIf { it is ArticleFragment }
                ?: ArticleFragment().arguments(Bundle().string("url", url))
            transaction.replace(R.id.container, fragment)
            transaction.commit()
        }
    }

    override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
        val systems = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        binding.appbar.updatePadding(top = systems.top)
        return WindowInsetsCompat.CONSUMED
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> true.also {
                onBackPressedDispatcher.onBackPressed()
            }

            else -> super.onOptionsItemSelected(item)
        }
    }
}