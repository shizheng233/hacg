package io.github.yueeng.hacg.activities

import android.app.SearchManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.SearchRecentSuggestions
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import io.github.yueeng.hacg.fragment.ArticleFragment
import io.github.yueeng.hacg.utils.HAcg
import io.github.yueeng.hacg.utils.JGitHubRelease
import io.github.yueeng.hacg.R
import io.github.yueeng.hacg.provider.SearchHistoryProvider
import io.github.yueeng.hacg.utils.TRANSPARENT
import io.github.yueeng.hacg.utils.Version
import io.github.yueeng.hacg.utils.addOnBackPressedCallback
import io.github.yueeng.hacg.utils.arguments
import io.github.yueeng.hacg.databinding.ActivityMainBinding
import io.github.yueeng.hacg.utils.gson
import io.github.yueeng.hacg.utils.httpGetAwait
import io.github.yueeng.hacg.utils.openUri
import io.github.yueeng.hacg.utils.pmap
import io.github.yueeng.hacg.utils.string
import io.github.yueeng.hacg.utils.test
import io.github.yueeng.hacg.utils.toast
import io.github.yueeng.hacg.utils.user
import io.github.yueeng.hacg.utils.version
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity(), OnApplyWindowInsetsListener {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.TRANSPARENT)
        binding = ActivityMainBinding.inflate(layoutInflater).apply {
            setSupportActionBar(toolbar)
            container.adapter = ArticleFragmentAdapter(this@MainActivity)
            TabLayoutMediator(tab, container) { tab, position ->
                tab.text = (container.adapter as ArticleFragmentAdapter).getPageTitle(position)
            }.attach()
        }
        setContentView(binding.root)
        if (Build.VERSION.SDK_INT > 29) {
            window.isNavigationBarContrastEnforced = false
        }
        if (savedInstanceState == null) checkVersion()
        ViewCompat.setOnApplyWindowInsetsListener(binding.root, this)
        var last = 0L
        addOnBackPressedCallback {
            if (System.currentTimeMillis() - last > 1500) {
                last = System.currentTimeMillis()
                toast(R.string.app_exit_confirm)
                return@addOnBackPressedCallback true
            }
            false
        }
    }

    private fun checkVersion(toast: Boolean = false) = lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.CREATED) {
            val release =
                "https://api.github.com/repos/shizheng233/hacg/releases/latest".httpGetAwait()?.let {
                    gson.fromJson(it.first, JGitHubRelease::class.java)
                }
            val ver = Version.from(release?.tagName)
            val apk =
                release?.assets?.firstOrNull { it.name == "app-release.apk" }?.browserDownloadUrl
            val local = version()
            if (local != null && ver != null && local < ver) {
                MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle(getString(R.string.app_update_new, local, ver))
                    .setMessage(release?.body ?: "")
                    .setPositiveButton(R.string.app_update) { _, _ -> openUri(apk) }
                    .setNeutralButton(R.string.app_publish) { _, _ -> openUri(HAcg.RELEASE) }
                    .setNegativeButton(R.string.app_cancel, null)
                    .create().show()
            } else {
                if (toast) Toast.makeText(
                    this@MainActivity,
                    getString(R.string.app_update_none, local),
                    Toast.LENGTH_SHORT
                ).show()
                checkConfig()
            }
        }
    }

    private fun checkConfig(toast: Boolean = false): Job = lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.CREATED) {
            HAcg.update(this@MainActivity, toast) {
                reload()
            }
        }
    }

    override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
        val systemBar = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        binding.appbar.updatePadding(top = systemBar.top)
        return WindowInsetsCompat.CONSUMED
    }

    private fun reload() {
        ActivityMainBinding.bind(findViewById(R.id.coordinator)).container.adapter =
            ArticleFragmentAdapter(this)
    }

    class ArticleFragmentAdapter(fm: FragmentActivity) : FragmentStateAdapter(fm) {
        private val data = HAcg.categories.toList()

        fun getPageTitle(position: Int): CharSequence = data[position].second

        override fun getItemCount(): Int = data.size

        override fun createFragment(position: Int): Fragment =
            ArticleFragment().arguments(Bundle().string("url", data[position].first))
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        val search = menu.findItem(R.id.search).actionView as SearchView
        val manager = getSystemService(SEARCH_SERVICE) as SearchManager
        val info = manager.getSearchableInfo(ComponentName(this, ListActivity::class.java))
        search.setSearchableInfo(info)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.search_clear -> true.also {
                val suggestions = SearchRecentSuggestions(
                    this,
                    SearchHistoryProvider.AUTHORITY,
                    SearchHistoryProvider.MODE
                )
                suggestions.clearHistory()
            }

            R.id.config -> true.also {
                checkConfig(true)
            }

            R.id.settings -> true.also {
                HAcg.setHost(this) { reload() }
            }

            R.id.auto -> true.also {
                lifecycleScope.launch {
                    repeatOnLifecycle(Lifecycle.State.CREATED) {
                        val good = withContext(Dispatchers.IO) {
                            HAcg.hosts().pmap { u -> (u to u.test()) }.filter { it.second.first }
                                .minByOrNull { it.second.second }
                        }
                        if (good != null) {
                            HAcg.host = good.first
                            toast(getString(R.string.settings_config_auto_choose, good.first))
                            reload()
                        } else {
                            toast(R.string.settings_config_auto_failed)
                        }
                    }
                }
            }

            R.id.user -> true.also {
                startActivity(Intent(this, WebActivity::class.java).apply {
                    if (user != 0) putExtra("url", "${HAcg.philosophy}/profile/$user")
                    else putExtra("login", true)
                })
            }

            R.id.philosophy -> true.also {
                startActivity(Intent(this, WebActivity::class.java))
            }

            R.id.about -> true.also {
                MaterialAlertDialogBuilder(this)
                    .setTitle("${getString(R.string.app_name)} ${version()}")
                    .setItems(arrayOf(getString(R.string.app_name))) { _, _ -> openUri(HAcg.wordpress) }
                    .setPositiveButton(R.string.app_publish) { _, _ -> openUri(HAcg.RELEASE) }
                    .setNeutralButton(R.string.app_update_check) { _, _ -> checkVersion(true) }
                    .setNegativeButton(R.string.app_cancel, null)
                    .create().show()
            }

            else -> super.onOptionsItemSelected(item)
        }
    }
}

