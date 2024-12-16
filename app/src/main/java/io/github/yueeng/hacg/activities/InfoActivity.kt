package io.github.yueeng.hacg.activities

import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import io.github.yueeng.hacg.R
import io.github.yueeng.hacg.databinding.ActivityInfoBinding
import io.github.yueeng.hacg.fragment.InfoFragment
import io.github.yueeng.hacg.utils.SwipeFinishActivity
import io.github.yueeng.hacg.utils.TRANSPARENT
import io.github.yueeng.hacg.utils.addOnBackPressedCallback
import io.github.yueeng.hacg.utils.arguments

/**
 * Info activity
 * Created by Rain on 2015/5/12.
 * Modify by ShihCheeng Chen on 2024/12/16.
 */

class InfoActivity : SwipeFinishActivity() {

    private lateinit var binding: ActivityInfoBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.TRANSPARENT)
        binding = ActivityInfoBinding.inflate(layoutInflater)
        setContentView(binding.root) {
            val manager = supportFragmentManager
            val fragment = manager.findFragmentById(R.id.container)?.takeIf { fragment ->
                fragment is InfoFragment
            } ?: InfoFragment().arguments(intent.extras)

            manager.beginTransaction()
                .replace(R.id.container, fragment).commit()
        }
        addOnBackPressedCallback {
            supportFragmentManager.findFragmentById(R.id.container)
                ?.let { (it as? InfoFragment)?.onBackPressed() } ?: false
        }
        if (Build.VERSION.SDK_INT > 29) {
            window.isNavigationBarContrastEnforced = false
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> true.also {
            onBackPressedDispatcher.onBackPressed()
        }

        else -> super.onOptionsItemSelected(item)
    }
}