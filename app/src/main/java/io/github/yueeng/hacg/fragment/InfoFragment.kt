package io.github.yueeng.hacg.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.Insets
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import io.github.yueeng.hacg.databinding.FragmentInfoBinding
import io.github.yueeng.hacg.utils.arguments

class InfoFragment : Fragment(), View.OnLayoutChangeListener, OnApplyWindowInsetsListener {

    private var _lastInsets: Insets? = null
    private var _binding: FragmentInfoBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentInfoBinding.inflate(inflater, container, false).also { binding ->
            val activity = activity as AppCompatActivity
            activity.setSupportActionBar(binding.toolbar)
            activity.supportActionBar?.setDisplayHomeAsUpEnabled(true)
            binding.container.adapter = InfoAdapter(this)
        }
        _binding = binding
        binding.root.addOnLayoutChangeListener(this)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root, this)
        return binding.root
    }

    override fun onLayoutChange(
        v: View,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        oldLeft: Int,
        oldTop: Int,
        oldRight: Int,
        oldBottom: Int
    ) {
        v.removeOnLayoutChangeListener(this)
        if (_lastInsets == null) {
            onApplyWindowInsets(v, ViewCompat.getRootWindowInsets(v) ?: return)
        }
    }

    override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
        val lastInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        if (_lastInsets == null) _lastInsets = lastInsets
        _binding?.appbar?.updatePadding(top = lastInsets.top)
        return WindowInsetsCompat.CONSUMED
    }

    inner class InfoAdapter(fm: Fragment) : FragmentStateAdapter(fm) {
        override fun getItemCount(): Int = 2

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> InfoWebFragment().arguments(arguments)
                1 -> InfoCommentFragment().arguments(arguments)
                else -> throw IllegalArgumentException()
            }
        }

    }

    fun onBackPressed(): Boolean = FragmentInfoBinding.bind(requireView()).container
        .takeIf { it.currentItem > 0 }?.let { it.currentItem = 0; true } ?: false
}