package io.github.yueeng.hacg.fragment

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.graphics.Insets
import androidx.core.os.bundleOf
import androidx.core.view.MenuProvider
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import io.github.yueeng.hacg.R
import io.github.yueeng.hacg.databinding.FragmentWebBinding
import io.github.yueeng.hacg.utils.HAcg
import io.github.yueeng.hacg.utils.openUri
import io.github.yueeng.hacg.utils.user
import io.github.yueeng.hacg.viewmodels.WebViewModel
import io.github.yueeng.hacg.viewmodels.factories.WebViewModelFactory

class WebFragment : Fragment(), MenuProvider, View.OnLayoutChangeListener,
    OnApplyWindowInsetsListener {

    private val viewModel: WebViewModel by viewModels {
        WebViewModelFactory(
            this,
            bundleOf("url" to defuri)
        )
    }

    private var _binding: FragmentWebBinding? = null
    private var _insets: Insets? = null

    private val defuri: String
        get() = arguments?.takeIf { it.containsKey("url") }?.getString("url")
            ?: (if (isLogin) "${HAcg.philosophy}?foro=signin" else HAcg.philosophy)
    private val isLogin: Boolean get() = arguments?.getBoolean("login", false) ?: false

    fun onBackPressed(): Boolean {
        val binding = FragmentWebBinding.bind(requireView())
        if (binding.web.canGoBack()) {
            binding.web.goBack()
            return true
        }
        return false
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_web, menu)
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.open -> true.also {
            activity?.openUri(viewModel.uri.value!!, true)
        }

        else -> false
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        super.onViewCreated(view, state)
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
        requireView().addOnLayoutChangeListener(this)
        ViewCompat.setOnApplyWindowInsetsListener(requireView(),this)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        FragmentWebBinding.bind(requireView()).web.destroy()
        _binding = null
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
        if (_insets == null) onApplyWindowInsets(v, ViewCompat.getRootWindowInsets(v) ?: return)
    }

    override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
        val insetBar = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        if (_insets == null) _insets = insetBar
        _binding?.bottomBar?.updatePadding(bottom = insetBar.bottom)
        return WindowInsetsCompat.CONSUMED
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentWebBinding.inflate(inflater, container, false)
        _binding = binding
        CookieManager.getInstance().acceptThirdPartyCookies(binding.web)
        viewModel.busy.observe(viewLifecycleOwner) { binding.swipe.isRefreshing = it }
        binding.swipe.setOnRefreshListener { binding.web.loadUrl(viewModel.uri.value!!) }

        val settings = binding.web.settings
        settings.javaScriptEnabled = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        val back = binding.button2
        val fore = binding.button3
        binding.web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean =
                activity?.openUri(request?.url?.toString(), false) == true

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                viewModel.busy.postValue(true)
                binding.progress.progress = 0
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                viewModel.busy.postValue(false)
                binding.progress.progress = 100
                viewModel.uri.postValue(url)

                back.isEnabled = view?.canGoBack() ?: false
                fore.isEnabled = view?.canGoForward() ?: false
                if (isLogin) view?.evaluateJavascript("""favorites_data["user_id"]""") { s ->
                    s?.trim('"')?.toIntOrNull()?.takeIf { it != 0 }?.let {
                        user = it
                        activity!!.finish()
                    }
                }
            }
        }
        binding.web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                binding.progress.progress = newProgress
            }
        }

        val click = View.OnClickListener { v ->
            when (v?.id) {
                R.id.button1 -> binding.web.loadUrl(defuri)
                R.id.button2 -> if (binding.web.canGoBack()) binding.web.goBack()
                R.id.button3 -> if (binding.web.canGoForward()) binding.web.goForward()
                R.id.button4 -> binding.web.loadUrl(viewModel.uri.value!!)
            }
        }
        listOf(binding.button1, binding.button2, binding.button3, binding.button4)
            .forEach { it.setOnClickListener(click) }

        binding.web.loadUrl(viewModel.uri.value!!)
        return binding.root
    }

}