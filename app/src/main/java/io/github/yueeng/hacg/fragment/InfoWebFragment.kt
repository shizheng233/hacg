package io.github.yueeng.hacg.fragment

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.MimeTypeMap
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.github.clans.fab.FloatingActionMenu
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.yueeng.hacg.BuildConfig
import io.github.yueeng.hacg.HAcgApplication
import io.github.yueeng.hacg.R
import io.github.yueeng.hacg.databinding.FragmentInfoWebBinding
import io.github.yueeng.hacg.utils.Article
import io.github.yueeng.hacg.utils.HacgPermission.Companion.checkPermissions
import io.github.yueeng.hacg.utils.clipboard
import io.github.yueeng.hacg.utils.findViewByViewType
import io.github.yueeng.hacg.utils.httpDownloadAwait
import io.github.yueeng.hacg.utils.httpGetAwait
import io.github.yueeng.hacg.utils.jsoup
import io.github.yueeng.hacg.utils.magnet
import io.github.yueeng.hacg.utils.okhttp
import io.github.yueeng.hacg.utils.openUri
import io.github.yueeng.hacg.utils.setRandomColor
import io.github.yueeng.hacg.viewmodels.InfoWebViewModel
import io.github.yueeng.hacg.viewmodels.factories.InfoWebViewModelFactory
import kotlinx.coroutines.launch
import org.jsoup.Jsoup
import org.jsoup.safety.Safelist
import java.util.Locale
import java.util.UUID

class InfoWebFragment : Fragment() {


    private val viewModel: InfoWebViewModel by viewModels {
        InfoWebViewModelFactory(
            this,
            arguments
        )
    }
    private val _url by lazy {
        viewModel.article.value?.link ?: requireArguments().getString("url")!!
    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        FragmentInfoWebBinding.inflate(inflater, container, false).also { binding ->
            viewModel.article.observe(viewLifecycleOwner) {
                it?.title?.takeIf { i -> i.isNotEmpty() }?.let { t -> requireActivity().title = t }
            }
            viewModel.error.observe(viewLifecycleOwner) {
                binding.image1.visibility = if (it) View.VISIBLE else View.INVISIBLE
            }
            binding.image1.setOnClickListener { query(_url) }
            binding.menu1.setRandomColor()
            val click = View.OnClickListener { v ->
                when (v.id) {
                    R.id.button1 -> activity?.openUri(_url, true)
                    R.id.button2 -> activity?.window?.decorView
                        ?.findViewByViewType<ViewPager2>(R.id.container)
                        ?.firstOrNull()?.currentItem = 1

                    R.id.button4 -> share()
                }
                view?.findViewById<FloatingActionMenu>(R.id.menu1)?.close(true)
            }
            listOf(
                binding.button1,
                binding.button2,
                binding.button4
            ).forEach { it.setOnClickListener(click) }
            viewModel.progress.observe(viewLifecycleOwner) {
                binding.progress.isIndeterminate = it
                binding.progress.visibility = if (it) View.VISIBLE else View.INVISIBLE
            }
            viewModel.magnet.observe(viewLifecycleOwner) {
                binding.button5.visibility = if (it.isNotEmpty()) View.VISIBLE else View.GONE
            }
            binding.button5.setOnClickListener(object : View.OnClickListener {
                val max = 3
                var magnet = 1
                var toast: Toast? = null

                override fun onClick(v: View): Unit = when {
                    magnet == max -> {
                        val magnets = viewModel.magnet.value ?: emptyList()
                        MaterialAlertDialogBuilder(activity!!)
                            .setTitle(R.string.app_magnet)
                            .setSingleChoiceItems(magnets.map { m -> "${if (m.contains(",")) "baidu" else "magnet"}:$m" }
                                .toTypedArray(), 0, null)
                            .setNegativeButton(R.string.app_cancel, null)
                            .setPositiveButton(R.string.app_open) { d, _ ->
                                val pos = (d as AlertDialog).listView.checkedItemPosition
                                val item = magnets[pos]
                                val link = if (item.contains(",")) {
                                    val baidu = item.split(",")
                                    context?.clipboard(getString(R.string.app_magnet), baidu.last())
                                    "https://yun.baidu.com/s/${baidu.first()}"
                                } else "magnet:?xt=urn:btih:${magnets[pos]}"
                                startActivity(
                                    Intent.createChooser(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse(link)
                                        ), getString(R.string.app_magnet)
                                    )
                                )
                            }
                            .setNeutralButton(R.string.app_copy) { d, _ ->
                                val pos = (d as AlertDialog).listView.checkedItemPosition
                                val item = magnets[pos]
                                val link = if (item.contains(",")) "https://yun.baidu.com/s/${
                                    item.split(",").first()
                                }" else "magnet:?xt=urn:btih:${magnets[pos]}"
                                context?.clipboard(getString(R.string.app_magnet), link)
                            }.create().show()
                        binding.menu1.close(true)
                    }

                    magnet < max -> {
                        magnet += 1
                        toast?.cancel()
                        toast = Toast.makeText(
                            activity!!,
                            (0 until magnet).joinToString("") { "..." },
                            Toast.LENGTH_SHORT
                        ).also { t -> t.show() }
                    }

                    else -> Unit
                }
            })
            CookieManager.getInstance().acceptThirdPartyCookies(binding.web)
            val settings = binding.web.settings
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            @SuppressLint("SetJavaScriptEnabled")
            settings.javaScriptEnabled = true
            binding.web.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    activity?.openUri(request?.url?.toString())
                    return true
                }

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? =
                    when (request?.url?.scheme?.lowercase(Locale.getDefault())) {
                        "http", "https" -> try {
                            val call = okhttp3.Request.Builder().method(request.method, null)
                                .url(request.url.toString()).apply {
                                    request.requestHeaders?.forEach { header(it.key, it.value) }
                                }.build()
                            val response = okhttp.newCall(call).execute()
                            WebResourceResponse(
                                response.header("Content-Type", "text/html; charset=UTF-8"),
                                response.header("Content-Encoding", "utf-8"),
                                response.code,
                                response.message,
                                response.headers.toMap(),
                                response.body?.byteStream()
                            )
                        } catch (_: Exception) {
                            null
                        }

                        else -> null
                    } ?: super.shouldInterceptRequest(view, request)

                override fun onRenderProcessGone(
                    view: WebView?,
                    detail: RenderProcessGoneDetail?
                ): Boolean {
                    binding.scroll.removeView(binding.web)
                    binding.web.destroy()
                    return true
                }
            }
            binding.web.addJavascriptInterface(JsFace(), "hacg")
            listOf(
                binding.button1,
                binding.button2,
                binding.button4,
                binding.button5
            ).forEach { b ->
                b.setRandomColor()
            }
            viewModel.web.observe(viewLifecycleOwner) { value ->
                if (value != null) binding.web.loadDataWithBaseURL(
                    value.second,
                    value.first,
                    "text/html",
                    "utf-8",
                    null
                )
            }
        }.root

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (viewModel.web.value == null) query(_url)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        view?.findViewById<WebView>(R.id.web)?.destroy()
    }


    fun share(url: String? = null) {
        fun share(uri: Uri? = null) {
            val ext = MimeTypeMap.getFileExtensionFromUrl(uri?.toString() ?: _url)
            val mime =
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.takeIf { it.isNotEmpty() }
                    ?: "text/plain"
            val title = viewModel.article.value?.title ?: ""
            val intro = viewModel.article.value?.content ?: ""
            val link = _url
            val share = Intent(Intent.ACTION_SEND)
                .setType(mime)
                .putExtra(Intent.EXTRA_TITLE, title)
                .putExtra(Intent.EXTRA_SUBJECT, title)
                .putExtra(Intent.EXTRA_TEXT, "$title\n$intro $link")
                .putExtra(Intent.EXTRA_REFERRER, Uri.parse(link))
            uri?.let { share.putExtra(Intent.EXTRA_STREAM, uri) }
            startActivity(Intent.createChooser(share, title))
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                url?.httpDownloadAwait()?.let { file ->
                    share(
                        FileProvider.getUriForFile(
                            requireActivity(),
                            "${BuildConfig.APPLICATION_ID}.fileprovider",
                            file
                        )
                    )
                } ?: share()
            }
        }
    }

    @Suppress("unused")
    inner class JsFace {
        @JavascriptInterface
        fun play(name: String, url: String) {
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(Uri.parse(url), "video/mp4"), name
                )
            )
        }

        @JavascriptInterface
        fun save(url: String) {
            activity?.runOnUiThread {
                val uri = Uri.parse(url)
                val image = ImageView(activity)
                image.adjustViewBounds = true
                Glide.with(requireActivity()).load(uri).placeholder(R.drawable.loading).into(image)
                val alert = MaterialAlertDialogBuilder(activity!!)
                    .setView(image)
                    .setNeutralButton(R.string.app_share) { _, _ -> share(url) }
                    .setPositiveButton(R.string.app_save) { _, _ ->
                        checkPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE) {
                            val name = uri.path?.split("/")?.last()
                                ?: UUID.randomUUID().toString()
                            val ext = MimeTypeMap.getFileExtensionFromUrl(name)
                            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                            val manager =
                                HAcgApplication.instance.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                            manager.enqueue(DownloadManager.Request(uri).apply {
                                setDestinationInExternalPublicDir(
                                    Environment.DIRECTORY_PICTURES,
                                    "hacg/$name"
                                )
                                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                setTitle(name)
                                setMimeType(mime)
                            })
                        }
                    }
                    .setNegativeButton(R.string.app_cancel, null)
                    .create()
                image.setOnClickListener { alert.dismiss() }
                alert.show()
            }
        }
    }

    private fun query(url: String) {
        if (viewModel.progress.value == true) return
        viewModel.error.postValue(false)
        viewModel.progress.postValue(true)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                val dom = url.httpGetAwait()?.jsoup()
                val article = dom?.select("article")?.firstOrNull()?.let { Article(it) }
                val entry = dom?.select(".entry-content")?.let { entry ->
                    val clean = Jsoup.clean(
                        entry.html(), url, Safelist.basicWithImages()
                            .addTags("audio", "video", "source")
                            .addAttributes("audio", "controls", "src")
                            .addAttributes("video", "controls", "src")
                            .addAttributes("source", "type", "src", "media")
                    )

                    Jsoup.parse(clean, url).select("body").also { e ->
                        e.select("[width],[height]")
                            .forEach { it.removeAttr("width").removeAttr("height") }
                        e.select("img[src]").forEach {
                            it.attr("data-original", it.attr("src"))
                                .addClass("lazy")
                                .removeAttr("src")
                                .after("""<a href="javascript:hacg.save('${it.attr("data-original")}');">下载此图</a>""")
                        }
                    }
                }
                val html = entry?.let {
                    activity?.resources?.openRawResource(R.raw.template)?.bufferedReader()
                        ?.readText()
                        ?.replace("{{title}}", article?.title ?: "")
                        ?.replace("{{body}}", entry.html())
                }
                val magnet = entry?.text()?.magnet()?.toList() ?: emptyList()
                if (article != null) viewModel.article.postValue(article)
                when (html) {
                    null -> {
                        viewModel.error.postValue(viewModel.web.value == null)
                    }

                    else -> {
                        viewModel.magnet.postValue(magnet)
                        viewModel.web.postValue(html to url)
                    }
                }
                viewModel.progress.postValue(false)
            }
        }
    }
}