package io.github.yueeng.hacg.activities

import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import io.github.yueeng.hacg.R
import io.github.yueeng.hacg.databinding.ActivityWebBinding
import io.github.yueeng.hacg.fragment.WebFragment
import io.github.yueeng.hacg.utils.addOnBackPressedCallback
import io.github.yueeng.hacg.utils.arguments

class WebActivity : AppCompatActivity(), OnApplyWindowInsetsListener {

    private lateinit var binding: ActivityWebBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityWebBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root, this)
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, supportFragmentManager.findFragmentById(R.id.container)
                ?.let { it as? WebFragment }
                ?: WebFragment().arguments(intent.extras))
            .commit()
        addOnBackPressedCallback {
            supportFragmentManager.findFragmentById(R.id.container)
                ?.let { (it as? WebFragment)?.onBackPressed() } ?: false
        }
        if (Build.VERSION.SDK_INT > 29) {
            window.isNavigationBarContrastEnforced = false
        }
    }

    override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
        val systemBar = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        binding.appbar.updatePadding(top = systemBar.top)
        return WindowInsetsCompat.CONSUMED
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> true.also {
            onBackPressedDispatcher.onBackPressed()
        }

        else -> super.onOptionsItemSelected(item)
    }
}