package io.github.yueeng.hacg.fragment

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.yueeng.hacg.utils.Article
import io.github.yueeng.hacg.utils.Comment
import io.github.yueeng.hacg.utils.HAcg
import io.github.yueeng.hacg.utils.JWpdiscuzCommentResult
import io.github.yueeng.hacg.utils.JWpdiscuzVote
import io.github.yueeng.hacg.utils.JWpdiscuzVoteSucceed
import io.github.yueeng.hacg.utils.LoadState
import io.github.yueeng.hacg.utils.PagingAdapter
import io.github.yueeng.hacg.R
import io.github.yueeng.hacg.activities.WebActivity
import io.github.yueeng.hacg.adapters.FooterAdapter
import io.github.yueeng.hacg.utils.childrenRecursiveSequence
import io.github.yueeng.hacg.databinding.CommentItemBinding
import io.github.yueeng.hacg.databinding.CommentPostBinding
import io.github.yueeng.hacg.databinding.FragmentInfoListBinding
import io.github.yueeng.hacg.utils.fromJsonOrNull
import io.github.yueeng.hacg.utils.getParcelableCompat
import io.github.yueeng.hacg.utils.gson
import io.github.yueeng.hacg.utils.httpPostAwait
import io.github.yueeng.hacg.utils.loading
import io.github.yueeng.hacg.utils.setRandomColor
import io.github.yueeng.hacg.utils.user
import io.github.yueeng.hacg.viewmodels.InfoCommentViewModel
import io.github.yueeng.hacg.viewmodels.factories.InfoCommentViewModelFactory
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jsoup.Jsoup

class InfoCommentFragment : Fragment(), MenuProvider {
    private val viewModel: InfoCommentViewModel by viewModels {
        InfoCommentViewModelFactory(
            this,
            bundleOf("id" to _id)
        )
    }
    private val _article by lazy { requireArguments().getParcelableCompat<Article>("article") }
    private val _url by lazy { _article?.link ?: requireArguments().getString("url")!! }
    private val _id by lazy { _article?.id ?: Article.getIdFromUrl(_url) ?: 0 }
    private val _adapter by lazy { CommentAdapter() }
    private val adapterPool = RecyclerView.RecycledViewPool()
    private val CONFIG_AUTHOR = "config.author"
    private val CONFIG_EMAIL = "config.email"
    private val CONFIG_COMMENT = "config.comment"
    private val AUTHOR = "wc_name"
    private val EMAIL = "wc_email"
    private var COMMENT = "wc_comment"

    private fun query(refresh: Boolean = false) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                if (refresh) _adapter.clear()
                val (list, _) = viewModel.source.query(refresh)
                if (list != null) _adapter.addAll(list)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        FragmentInfoListBinding.inflate(inflater, container, false).also { binding ->
            binding.list1.adapter =
                _adapter.withLoadStateFooter(FooterAdapter({ _adapter.itemCount }) { query() })
            viewModel.progress.observe(viewLifecycleOwner) { binding.swipe.isRefreshing = it }
            viewModel.source.state.observe(viewLifecycleOwner) {
                _adapter.state.postValue(it)
                binding.swipe.isRefreshing = it is LoadState.Loading
            }
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.CREATED) {
                    _adapter.refreshFlow.collectLatest {
                        binding.list1.scrollToPosition(0)
                    }
                }
            }
            binding.list1.loading {
                when (viewModel.source.state.value) {
                    LoadState.NotLoading(false) -> query()
                }
            }
            binding.swipe.setOnRefreshListener { query(true) }
            binding.button3.setRandomColor().setOnClickListener {
                comment(null) {
                    _adapter.add(it, 0)
                    binding.list1.smoothScrollToPosition(0)
                }
            }
        }.root

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.data.value?.let { _adapter.addAll(it) }
        if (_adapter.itemCount == 0) query()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        viewModel.data.value = _adapter.data
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_comment, menu)
    }

    override fun onPrepareMenu(menu: Menu) {
        when (viewModel.sorting.value) {
            InfoCommentViewModel.Sorting.Newest -> menu.findItem(R.id.newest).isChecked = true
            InfoCommentViewModel.Sorting.Oldest -> menu.findItem(R.id.oldest).isChecked = true
            else -> menu.findItem(R.id.vote).isChecked = true
        }
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.vote, R.id.newest, R.id.oldest -> true.also {
            viewModel.sorting.postValue(
                when (item.itemId) {
                    R.id.oldest -> InfoCommentViewModel.Sorting.Oldest
                    R.id.newest -> InfoCommentViewModel.Sorting.Newest
                    else -> InfoCommentViewModel.Sorting.Vote
                }
            )
            query(true)
        }

        else -> false
    }

    inner class CommentHolder(private val binding: CommentItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        private val adapter = CommentAdapter()
        private var comment: Comment? = null

        init {
            binding.list1.setRecycledViewPool(adapterPool)
            binding.list1.adapter = adapter
            listOf(binding.button1, binding.button2).forEach { b ->
                b.setOnClickListener { view ->
                    val v = if (view.id == R.id.button1) -1 else 1
                    val item = comment ?: return@setOnClickListener
                    val pos = bindingAdapterPosition
                    vote(item, v) {
                        item.moderation = it
                        bindingAdapter?.notifyItemChanged(pos, "moderation")
                    }
                }
            }
            binding.root.setOnClickListener {
                comment(comment!!) {
                    adapter.add(it)
                }
            }
        }

        @SuppressLint("SetTextI18n")
        fun bind(item: Comment, payloads: MutableList<Any>) {
            if (payloads.contains("moderation")) {
                binding.text4.text = "${item.moderation}"
                return
            }
            comment = item
            itemView.tag = item
            binding.text1.text = item.user
            binding.text2.text = item.content
            binding.text3.text = item.time
            binding.text4.text = "${item.moderation}"
            adapter.clear()
            adapter.addAll(item.children)
            if (item.face.isEmpty()) {
                binding.image1.setImageResource(R.mipmap.ic_launcher)
            } else {
                Glide.with(requireContext()).load(item.face).placeholder(R.mipmap.ic_launcher)
                    .into(binding.image1)
            }
        }
    }

    class CommentDiffCallback : DiffUtil.ItemCallback<Comment>() {
        override fun areItemsTheSame(oldItem: Comment, newItem: Comment): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Comment, newItem: Comment): Boolean =
            oldItem == newItem
    }

    inner class CommentAdapter : PagingAdapter<Comment, CommentHolder>(CommentDiffCallback()) {
        override fun onBindViewHolder(holder: CommentHolder, position: Int) {}

        override fun onBindViewHolder(
            holder: CommentHolder,
            position: Int,
            payloads: MutableList<Any>
        ) {
            holder.bind(getItem(position)!!, payloads)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentHolder =
            CommentHolder(CommentItemBinding.inflate(layoutInflater, parent, false))
    }

    fun vote(c: Comment?, v: Int, call: (Int) -> Unit) {
        if (c == null) return
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                val result = HAcg.wpdiscuz.httpPostAwait(
                    mapOf(
                        "action" to "wpdVoteOnComment",
                        "commentId" to "${c.id}",
                        "voteType" to "$v",
                        "postId" to "$_id"
                    )
                )
                val succeed = gson.fromJsonOrNull<JWpdiscuzVoteSucceed>(result?.first ?: "")
                if (succeed?.success != true) {
                    val json = gson.fromJsonOrNull<JWpdiscuzVote>(result?.first ?: "")
                    Toast.makeText(
                        requireActivity(),
                        json?.data ?: result?.first,
                        Toast.LENGTH_LONG
                    ).show()
                    return@repeatOnLifecycle
                }
                call(succeed.data.votes.toIntOrNull() ?: 0)
            }
        }
    }

    fun comment(c: Comment?, succeed: (Comment) -> Unit) {
        if (c == null) {
            commenting(null, succeed)
            return
        }
        MaterialAlertDialogBuilder(requireActivity())
            .setTitle(c.user)
            .setMessage(c.content)
            .setPositiveButton(R.string.comment_review) { _, _ -> commenting(c, succeed) }
            .setNegativeButton(R.string.app_cancel, null)
            .setNeutralButton(R.string.app_copy) { _, _ ->
                val clipboard =
                    requireActivity().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText(c.user, c.content)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(
                    activity,
                    requireActivity().getString(R.string.app_copied, c.content),
                    Toast.LENGTH_SHORT
                ).show()
            }.create().apply {
                setOnShowListener { dialog ->
                    dialog.let { it as? AlertDialog }?.window?.decorView?.childrenRecursiveSequence()
                        ?.mapNotNull { it as? TextView }?.filter { it !is Button }
                        ?.forEach { it.setTextIsSelectable(true) }
                }
            }.show()
    }

    @SuppressLint("InflateParams")
    private fun commenting(c: Comment?, succeed: (Comment) -> Unit) {
        val input = CommentPostBinding.inflate(layoutInflater)
        val author: EditText = input.edit1
        val email: EditText = input.edit2
        val content: EditText = input.edit3
        val post = mutableMapOf<String, String>()
        val preference = PreferenceManager.getDefaultSharedPreferences(requireActivity())
        if (user != 0) {
            input.input1.visibility = View.GONE
            input.input2.visibility = View.GONE
        } else {
            post += (AUTHOR to preference.getString(CONFIG_AUTHOR, "")!!)
            post += (EMAIL to preference.getString(CONFIG_EMAIL, "")!!)
            author.setText(post[AUTHOR])
            email.setText(post[EMAIL])
        }
        post += (COMMENT to preference.getString(CONFIG_COMMENT, "")!!)
        content.setText(post[COMMENT] ?: "")
        post["action"] = "wpdAddComment"
        post["submit"] = "发表评论"
        post["postId"] = "$_id"
        post["wpdiscuz_unique_id"] = (c?.uniqueId ?: "0_0")
        post["wc_comment_depth"] = "${(c?.depth ?: 1)}"

        fun fill() {
            post[AUTHOR] = author.text.toString()
            post[EMAIL] = email.text.toString()
            post[COMMENT] = content.text.toString()
            preference.edit().putString(CONFIG_AUTHOR, post[AUTHOR])
                .putString(CONFIG_EMAIL, post[EMAIL])
                .putString(CONFIG_COMMENT, post[COMMENT]).apply()
        }

        MaterialAlertDialogBuilder(requireActivity())
            .setTitle(
                if (c != null) getString(
                    R.string.comment_review_to,
                    c.user
                ) else getString(R.string.comment_title)
            )
            .setView(input.root)
            .setPositiveButton(R.string.comment_submit) { _, _ ->
                fill()
                if (post[COMMENT].isNullOrBlank() || (user == 0 && (post[AUTHOR].isNullOrBlank() || post[EMAIL].isNullOrBlank()))) {
                    Toast.makeText(
                        requireActivity(),
                        getString(R.string.comment_verify),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }
                viewModel.progress.postValue(true)
                lifecycleScope.launch {
                    repeatOnLifecycle(Lifecycle.State.CREATED) {
//                        delay(100)
//                        succeed(Comment(random.nextInt(), c?.id ?: 0, "Test", "ZS", "", 0, datefmt.format(Date()), mutableListOf()))
                        val result = HAcg.wpdiscuz.httpPostAwait(post.toMap())
                        val json = gson.fromJsonOrNull<JWpdiscuzCommentResult>(result?.first)
                        val review = Jsoup.parse(json?.data?.message ?: "", result?.second ?: "")
                            .select("body>.wpd-comment").map { Comment(it) }.firstOrNull()
                        if (review == null) {
                            Toast.makeText(
                                requireActivity(),
                                json?.data?.code ?: result?.first,
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            post[COMMENT] = ""
                            succeed(review)
                        }
                        viewModel.progress.postValue(false)
                    }
                }
            }
            .setNegativeButton(R.string.app_cancel, null)
            .apply {
                if (user != 0) return@apply
                setNeutralButton(R.string.app_user_login) { _, _ ->
                    startActivity(
                        Intent(
                            requireActivity(),
                            WebActivity::class.java
                        ).putExtra("login", true)
                    )
                }
            }
            .setOnDismissListener { fill() }
            .create().show()
    }
}