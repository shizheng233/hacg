package io.github.yueeng.hacg

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.app.DownloadManager.Request
import android.content.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v4.app.Fragment
import android.support.v4.view.PagerAdapter
import android.support.v4.view.ViewPager
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.app.AlertDialog
import android.support.v7.app.AlertDialog.Builder
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.*
import com.github.clans.fab.FloatingActionButton
import com.github.clans.fab.FloatingActionMenu
import com.squareup.picasso.Picasso
import org.jetbrains.anko.childrenRecursiveSequence
import org.jetbrains.anko.doAsync
import org.jsoup.Jsoup
import org.jsoup.safety.Whitelist
import java.text.SimpleDateFormat
import java.util.concurrent.Future

/**
 * Info activity
 * Created by Rain on 2015/5/12.
 */

class InfoActivity : BaseSlideCloseActivity() {
    private val _article: Article by lazy { intent.getParcelableExtra<Article>("article") }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_info)

        val manager = supportFragmentManager

        val fragment = manager.findFragmentById(R.id.container)?.takeIf { it is InfoFragment }
                ?: InfoFragment().arguments(Bundle().parcelable("article", _article))

        manager.beginTransaction().replace(R.id.container, fragment).commit()
    }

    override fun onBackPressed() {
        supportFragmentManager.findFragmentById(R.id.container)?.let { it as InfoFragment }?.takeIf { it.onBackPressed() }
                ?: super.onBackPressed()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> {
            onBackPressed()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }
}

class InfoFragment : Fragment() {
    private val _article: Article by lazy { arguments!!.getParcelable<Article>("article") }
    private val _adapter by lazy { CommentAdapter() }
    private val _web = ViewBinder<Pair<String, String>?, WebView>(null) { view, value -> if (value != null) view.loadDataWithBaseURL(value.second, value.first, "text/html", "utf-8", null) }
    private val _error = object : ErrorBinder(false) {
        override fun retry(): Unit = query(_article.link, QUERY_ALL)
    }
    private val _post = mutableMapOf<String, String>()
    private var _url: String? = null

    private val _click = View.OnClickListener { v ->
        v.tag?.let { it as Comment }?.let { comment(it) }
    }
    private val CONFIG_AUTHOR = "config.author"
    private val CONFIG_EMAIL = "config.email"
    private val AUTHOR = "author"
    private val EMAIL = "email"
    private var COMMENTURL = ""
    private var COMMENT = "comment"
    private val COMMENTPREFIX = "comment-[a-f0-9]{8}".toRegex()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        retainInstance = true
        val preference = PreferenceManager.getDefaultSharedPreferences(activity)
        _post += (AUTHOR to preference.getString(CONFIG_AUTHOR, ""))
        _post += (EMAIL to preference.getString(CONFIG_EMAIL, ""))
        query(_article.link, QUERY_ALL)
    }

    private val _magnet = ViewBinder<List<String>, View>(listOf()) { view, value -> view.visibility = if (value.isNotEmpty()) View.VISIBLE else View.GONE }

    private val _progress = ViewBinder<Boolean, ProgressBar>(false) { view, value ->
        view.isIndeterminate = value
        view.visibility = if (value) View.VISIBLE else View.INVISIBLE
    }

    private val _progress2 = ViewBinder<Boolean, SwipeRefreshLayout>(false) { view, value -> view.post { view.isRefreshing = value } }

    override fun onDestroy() {
        super.onDestroy()
        _web.each { it.destroy() }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
            inflater.inflate(R.layout.fragment_info, container, false)

    override fun onViewCreated(view: View, state: Bundle?) {
        val activity = activity as AppCompatActivity
        activity.setSupportActionBar(view.findViewById(R.id.toolbar))
        activity.supportActionBar?.setLogo(R.mipmap.ic_launcher)
        activity.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        activity.title = _article.title
        view.findViewById<ViewPager>(R.id.container).adapter = InfoAdapter()
    }

    inner class InfoAdapter : PagerAdapter() {
        override fun getCount(): Int = 2

        override fun isViewFromObject(view: View, `object`: Any): Boolean = view == `object`

        override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
            container.removeView(`object` as View)
        }

        @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
        override fun instantiateItem(container: ViewGroup, position: Int): Any = when (position) {
            0 -> {
                container.inflate(R.layout.fragment_info_web).also { root ->
                    _error + root.findViewById(R.id.image1)
                    val menu: FloatingActionMenu = root.findViewById(R.id.menu1)
                    menu.menuButtonColorNormal = randomColor()
                    menu.menuButtonColorPressed = randomColor()
                    menu.menuButtonColorRipple = randomColor()
                    val click = View.OnClickListener { v ->
                        when (v.id) {
                            R.id.button1 -> openWeb(activity!!, _article.link!!)
                            R.id.button2 -> view?.findViewById<ViewPager>(R.id.container)?.currentItem = 1
                            R.id.button4 -> share(_article.image)
                        }
                        view?.findViewById<FloatingActionMenu>(R.id.menu1)?.close(true)
                    }
                    listOf(R.id.button1, R.id.button2, R.id.button4)
                            .map { root.findViewById<View>(it) }.forEach {
                                it.setOnClickListener(click)
                            }

                    _progress + root.findViewById(R.id.progress)
                    _magnet + root.findViewById<View>(R.id.button5).also {


                        it.setOnClickListener(object : View.OnClickListener {
                            val max = 3
                            var magnet = 0
                            var toast: Toast? = null

                            override fun onClick(v: View): Unit = when {
                                _magnet().isNotEmpty() -> {
                                    Builder(activity!!)
                                            .setTitle(R.string.app_magnet)
                                            .setSingleChoiceItems(_magnet().map { m -> "${if (m.contains(",")) "baidu" else "magnet"}:$m" }.toTypedArray(), 0, null)
                                            .setNegativeButton(R.string.app_cancel, null)
                                            .setPositiveButton(R.string.app_open) { d, _ ->
                                                val pos = (d as AlertDialog).listView.checkedItemPosition
                                                val item = _magnet()[pos]
                                                val link = if (item.contains(",")) {
                                                    val baidu = item.split(",")
                                                    context?.clipboard(getString(R.string.app_magnet), baidu.last())
                                                    "https://yun.baidu.com/s/${baidu.first()}"
                                                } else "magnet:?xt=urn:btih:${_magnet()[pos]}"
                                                startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW, Uri.parse(link)), getString(R.string.app_magnet)))
                                            }
                                            .setNeutralButton(R.string.app_copy) { d, _ ->
                                                val pos = (d as AlertDialog).listView.checkedItemPosition
                                                val item = _magnet()[pos]
                                                val link = if (item.contains(",")) "https://yun.baidu.com/s/${item.split(",").first()}" else "magnet:?xt=urn:btih:${_magnet()[pos]}"
                                                context?.clipboard(getString(R.string.app_magnet), link)
                                            }.create().show()
                                    menu.close(true)
                                }
                                magnet < max -> {
                                    magnet += 1
                                    toast?.cancel()
                                    toast = Toast.makeText(activity!!, (0 until magnet).joinToString("") { "..." }, Toast.LENGTH_SHORT).also { it.show() }
                                }
                                else -> {
                                }
                            }
                        })
                    }
                    val web: WebView = root.findViewById(R.id.web)
                    val settings = web.settings
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    }
                    settings.javaScriptEnabled = true
                    web.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                            val uri = Uri.parse(url)
                            startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), uri.scheme))
                            return true
                        }
                    }
                    web.addJavascriptInterface(JsFace(), "hacg")
                    _web + web
                }
            }
            1 -> {
                container.inflate(R.layout.fragment_info_list).also { root ->
                    val list: RecyclerView = root.findViewById(R.id.list1)
                    list.layoutManager = LinearLayoutManager(activity)
                    list.setHasFixedSize(true)
                    list.adapter = _adapter
                    list.loading { query(_url, QUERY_COMMENT) }

                    _progress2 + root.findViewById(R.id.swipe)
                    _progress2.each {
                        it.setOnRefreshListener {
                            _url = null
                            _adapter.clear()
                            query(_article.link, QUERY_COMMENT)
                        }
                    }
                    root.findViewById<View>(R.id.button3).setOnClickListener { comment(null) }
                }
            }
            else -> throw  IllegalAccessException()
        }.also { root ->
            listOf(R.id.button1, R.id.button2, R.id.button3, R.id.button4, R.id.button5)
                    .map { root.findViewById<View>(it) }.mapNotNull { it as? FloatingActionButton }.forEach { b ->
                        b.colorNormal = randomColor()
                        b.colorPressed = randomColor()
                        b.colorRipple = randomColor()
                    }
            container.addView(root)
        }
    }

    fun share(url: String?): Future<Unit>? = url?.httpDownloadAsync(context!!) {
        if (it != null) {
            val title = _article.title
            val intro = _article.content
            val link = _article.link
            startActivity(Intent.createChooser(
                    Intent(Intent.ACTION_SEND)
                            .setType("image/*")
                            .putExtra(Intent.EXTRA_TITLE, title)
                            .putExtra(Intent.EXTRA_SUBJECT, title)
                            .putExtra(Intent.EXTRA_TEXT, "$title\n$intro $link")
                            .putExtra(Intent.EXTRA_STREAM, Uri.fromFile(it))
                            .putExtra(Intent.EXTRA_REFERRER, Uri.parse(link)),
                    title))
        }
    }

    inner class JsFace {
        @JavascriptInterface
        fun play(name: String, url: String) {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW)
                    .setDataAndType(Uri.parse(url), "video/mp4"), name))
        }

        @JavascriptInterface
        fun save(url: String) {
            activity?.runOnUiThread {
                val uri = Uri.parse(url)
                val image = ImageView(activity)
                image.adjustViewBounds = true
                Picasso.with(activity).load(uri).placeholder(R.drawable.loading).into(image)
                val alert = Builder(activity!!)
                        .setView(image)
                        .setNeutralButton(R.string.app_share) { _, _ -> share(url) }
                        .setPositiveButton(R.string.app_save) { _, _ ->
                            val manager = HAcgApplication.instance.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                            val task = Request(uri)
                            task.allowScanningByMediaScanner()
                            task.setNotificationVisibility(Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                            val ext = MimeTypeMap.getFileExtensionFromUrl(url)
                            task.setMimeType(MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext))
                            manager.enqueue(task)
                        }
                        .setNegativeButton(R.string.app_cancel, null)
                        .create()
                image.setOnClickListener { alert.dismiss() }
                alert.show()
            }
        }
    }

    fun onBackPressed(): Boolean =
            view?.findViewById<View>(R.id.container /*drawer*/)?.let { it as? ViewPager }?.takeIf { it.currentItem > 0 }
                    ?.let { it.currentItem = 0; true } ?: false


    fun comment(c: Comment?) {
        if (c == null) {
            commenting(c)
            return
        }
        val alert = Builder(activity!!)
                .setTitle(c.user)
                .setMessage(c.content)
                .setPositiveButton(R.string.comment_review) { _, _ -> commenting(c) }
                .setNegativeButton(R.string.app_cancel, null)
                .setNeutralButton(R.string.app_copy) { _, _ ->
                    val clipboard = activity!!.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText(c.user, c.content)
                    clipboard.primaryClip = clip
                    Toast.makeText(activity, activity!!.getString(R.string.app_copied, c.content), Toast.LENGTH_SHORT).show()
                }
                .create()
        alert.setOnShowListener(object : DialogInterface.OnShowListener {
            fun r(v: Sequence<View>) {
                if (v.any()) {
                    rr(v.first())
                    r(v.drop(1))
                }
            }

            fun rr(v: View?): Unit = when (v) {
                is Button -> {
                }
                is TextView ->
                    v.setTextIsSelectable(true)
                is ViewGroup ->
                    r(v.childrenRecursiveSequence())
                else -> {
                }
            }

            override fun onShow(dialog: DialogInterface?) {
                rr((dialog as? AlertDialog)?.window?.decorView)
            }
        })
        alert.show()
    }

    private fun commenting(c: Comment?) {
        val input = LayoutInflater.from(activity!!).inflate(R.layout.comment_post, null)
        val author: EditText = input.findViewById(R.id.edit1)
        val email: EditText = input.findViewById(R.id.edit2)
        val content: EditText = input.findViewById(R.id.edit3)
        author.setText(_post[AUTHOR])
        email.setText(_post[EMAIL])
        content.setText(_post[COMMENT] ?: "")
        _post += ("comment_parent" to (c?.id?.toString() ?: "0"))

        fun fill() {
            _post += AUTHOR to author.text.toString()
            _post += EMAIL to email.text.toString()
            _post += COMMENT to content.text.toString()
            val preference = PreferenceManager.getDefaultSharedPreferences(activity!!)
            preference.edit().putString(CONFIG_AUTHOR, _post[AUTHOR]).putString(CONFIG_EMAIL, _post[EMAIL]).apply()
        }

        Builder(activity!!)
                .setTitle(if (c != null) getString(R.string.comment_review_to, c.user) else getString(R.string.comment_title))
                .setView(input)
                .setPositiveButton(R.string.comment_submit) { _, _ ->
                    fill()
                    if (COMMENTURL.isEmpty() || listOf(AUTHOR, EMAIL, COMMENT).map { _post.get(it) }.any { it.isNullOrEmpty() }) {
                        Toast.makeText(activity!!, getString(R.string.comment_verify), Toast.LENGTH_SHORT).show()
                    } else {
                        _progress2 * true
                        doAsync {
                            val result = COMMENTURL.httpPost(_post.toMap())?.jsoup { dom ->
                                dom.select("#error-page").firstOrNull()?.let { false to it.text() }
                                        ?: (true to getString(R.string.comment_succeeded))
                            } ?: (false to getString(R.string.comment_failed))
                            autoUiThread {
                                _progress2 * false
                                if (result.first) {
                                    _post[COMMENT] = ""
                                    _url = null
                                    _adapter.clear()
                                    query(_article.link, QUERY_COMMENT)
                                }
                                Toast.makeText(activity!!, result.second, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
                .setNegativeButton(R.string.app_cancel, null)
                .setOnDismissListener { fill() }
                .create().show()
    }

    private val QUERY_WEB = 1
    val QUERY_COMMENT: Int = QUERY_WEB shl 1
    val QUERY_ALL: Int = QUERY_WEB or QUERY_COMMENT

    fun query(url: String?, op: Int): Unit {
        if (_progress() || _progress2() || url.isNullOrEmpty()) {
            return
        }
        _error * false
        val content = (op and QUERY_WEB) == QUERY_WEB
        val comment = (op and QUERY_COMMENT) == QUERY_COMMENT
        _progress * content
        _progress2 * true
        doAsync {
            val data = url.httpGet()?.jsoup { dom ->
                val entry = dom.select(".entry-content").let { entry ->
                    val clean = Jsoup.clean(entry.html(), url, Whitelist.basicWithImages()
                            .addTags("audio", "video", "source")
                            .addAttributes("audio", "controls", "src")
                            .addAttributes("video", "controls", "src")
                            .addAttributes("source", "type", "src", "media"))

                    Jsoup.parse(clean, url).select("body").also { entry ->
                        entry.select("[width],[height]").forEach { it.removeAttr("width").removeAttr("height") }
                        entry.select("img[src]").forEach {
                            it.attr("data-original", it.attr("src"))
                                    .addClass("lazy")
                                    .removeAttr("src")
                                    .after("""<a href="javascript:hacg.save('${it.attr("data-original")}');">下载此图</a>""")
                        }
                    }
                }

                Sextuple(
                        if (content) HAcgApplication.instance.assets.open("template.html").use {
                            it.reader().use { r ->
                                r.readText().replace("{{title}}", _article.title).replace("{{body}}", entry.html())
                            }
                        } else null,
                        if (comment) dom.select("#comments .commentlist>li").map { e -> Comment(e) }.toList() else null,
                        dom.select("#comments #comment-nav-below #comments-nav .next").firstOrNull()?.let { it.attr("abs:href") },
                        dom.select("#commentform").select("textarea,input").map { o -> (o.attr("name") to o.attr("value")) }.toMap(),
                        dom.select("#commentform").attr("abs:action"),
                        if (content) entry.text() else null
                )
            }
            autoUiThread {
                when (data) {
                    null -> {
                        _error * (_web() == null)
                    }
                    else -> {
                        if (content) {
                            _magnet *
                                    ("""\b([a-zA-Z0-9]{32}|[a-zA-Z0-9]{40})\b""".toRegex().findAll(data.sixth!!).map { it.value }.toList() +
                                            """\b([a-zA-Z0-9]{8})\b\s+\b([a-zA-Z0-9]{4})\b""".toRegex().findAll(data.sixth).map { m -> "${m.groups[1]!!.value},${m.groups[2]!!.value}" })
                            _web * (data.first!! to url)
                        }
                        if (comment) {
                            _url = data.third
                            _adapter.data.removeAll(_adapter.data.filter { it is String })
                            _adapter.addAll(data.second!!)
                            val (d, u) = _adapter.data.isEmpty() to _url.isNullOrEmpty()
                            _adapter.add(when {
                                d && u -> getString(R.string.app_list_empty)
                                u -> getString(R.string.app_list_complete)
                                else -> getString(R.string.app_list_loading)
                            })
                        }
                        COMMENT = data.fourth.asSequence().firstOrNull { o -> o.key.matches(COMMENTPREFIX) }?.key
                                ?: COMMENT
                        val filter = listOf(AUTHOR, EMAIL, COMMENT)
                        _post.putAll(data.fourth.filter { o -> !filter.contains(o.key) })

                        COMMENTURL = data.fifth
                    }
                }
                _progress * false
                _progress2 * false
            }
        }
    }

    val datafmt = SimpleDateFormat("yyyy-MM-dd hh:ss")

    inner class CommentHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text1: TextView = view.findViewById(R.id.text1)
        val text2: TextView = view.findViewById(R.id.text2)
        val text3: TextView = view.findViewById(R.id.text3)
        val text4: TextView = view.findViewById(R.id.text4)
        val image: ImageView = view.findViewById(R.id.image1)
        private val list: RecyclerView = view.findViewById(R.id.list1)
        val adapter = CommentAdapter()
        val context: Context = view.context

        init {
            list.adapter = adapter
            list.layoutManager = LinearLayoutManager(context)
            list.setHasFixedSize(true)
            view.setOnClickListener(_click)
        }
    }

    inner class MsgHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text1: TextView = view.findViewById(R.id.text1)
    }

    inner class CommentAdapter : DataAdapter<Any, RecyclerView.ViewHolder>() {

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder is CommentHolder) {
                val item = data[position] as Comment
                holder.itemView.tag = item
                holder.text1.text = item.user
                holder.text2.text = item.content
                holder.text3.text = item.time?.let { datafmt.format(it) }
                holder.text3.visibility = if (item.time == null) View.GONE else View.VISIBLE
                holder.text4.text = item.moderation
                holder.text4.visibility = if (item.moderation.isEmpty()) View.GONE else View.VISIBLE
                holder.adapter.clear()
                holder.adapter.addAll(item.children)

                if (item.face.isEmpty()) {
                    holder.image.setImageResource(R.mipmap.ic_launcher)
                } else {
                    Picasso.with(holder.context).load(item.face).placeholder(R.mipmap.ic_launcher).into(holder.image)
                }
            }
            if (holder is MsgHolder)
                holder.text1.text = data[position] as String
        }

        private val CommentTypeComment = 0
        private val CommentTypeMsg = 1
        override fun getItemViewType(position: Int): Int = when (data[position]) {
            is Comment -> CommentTypeComment
            else -> CommentTypeMsg
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder = when (viewType) {
            CommentTypeComment -> CommentHolder(parent.inflate(R.layout.comment_item))
            else -> MsgHolder(parent.inflate(R.layout.list_msg_item))
        }

    }

}