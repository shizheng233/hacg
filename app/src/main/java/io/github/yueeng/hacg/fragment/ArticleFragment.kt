package io.github.yueeng.hacg.fragment

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import io.github.yueeng.hacg.utils.Article
import io.github.yueeng.hacg.utils.DataAdapter
import io.github.yueeng.hacg.adapters.FooterAdapter
import io.github.yueeng.hacg.utils.HAcg
import io.github.yueeng.hacg.HAcgApplication
import io.github.yueeng.hacg.activities.InfoActivity
import io.github.yueeng.hacg.utils.LoadState
import io.github.yueeng.hacg.utils.PagingAdapter
import io.github.yueeng.hacg.R
import io.github.yueeng.hacg.activities.ListActivity
import io.github.yueeng.hacg.databinding.ArticleItemBinding
import io.github.yueeng.hacg.databinding.FragmentListBinding
import io.github.yueeng.hacg.utils.loading
import io.github.yueeng.hacg.utils.randomColor
import io.github.yueeng.hacg.utils.spannable
import io.github.yueeng.hacg.utils.toast
import io.github.yueeng.hacg.viewmodels.ArticleViewModel
import io.github.yueeng.hacg.viewmodels.factories.ArticleViewModelFactory
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ArticleFragment : Fragment() {

    private val viewModel: ArticleViewModel by viewModels {
        ArticleViewModelFactory(
            this,
            bundleOf("url" to defurl)
        )
    }
    private val adapter by lazy { ArticleAdapter() }

    private val defurl: String
        get() = requireArguments().getString("url")!!
            .let { uri -> if (uri.startsWith("/")) "${HAcg.web}$uri" else uri }

    private fun query(refresh: Boolean = false) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                if (refresh) adapter.clear()
                val (list, _) = viewModel.source.query(refresh)
                if (list != null) adapter.addAll(list)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        viewModel.last.value = adapter.last
        viewModel.data.value = adapter.data
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.last.value?.let { adapter.last = it }
        viewModel.data.value?.let { adapter.addAll(it) }
        if (adapter.itemCount == 0) query()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        FragmentListBinding.inflate(inflater, container, false).apply {
            viewModel.source.state.observe(viewLifecycleOwner) {
                adapter.state.postValue(it)
                swipe.isRefreshing = it is LoadState.Loading
                image1.visibility =
                    if (it is LoadState.Error && adapter.itemCount == 0) View.VISIBLE else View.INVISIBLE
                if (it is LoadState.Error && adapter.itemCount == 0) if (viewModel.retry) activity?.openOptionsMenu() else activity?.toast(
                    R.string.app_network_retry
                )
            }
            if (requireActivity().title.isNullOrEmpty()) {
                requireActivity().title = getString(R.string.app_name)
                viewModel.title.observe(viewLifecycleOwner) {
                    requireActivity().title = it
                }
            }
            image1.setOnClickListener {
                viewModel.retry = true
                query(true)
            }
            swipe.setOnRefreshListener { query(true) }
            recycler.setHasFixedSize(true)
            recycler.adapter = adapter.withLoadStateFooter(FooterAdapter({ adapter.itemCount }) {
                query()
            })
            recycler.loading {
                when (viewModel.source.state.value) {
                    LoadState.NotLoading(false) -> query()
                }
            }
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.CREATED) {
                    adapter.refreshFlow.collectLatest {
                        recycler.scrollToPosition(0)
                    }
                }
            }
        }.root

    class ArticleHolder(private val binding: ArticleItemBinding) :
        RecyclerView.ViewHolder(binding.root), View.OnClickListener {
        private val datafmt = SimpleDateFormat("yyyy-MM-dd hh:ss", Locale.getDefault())
        private val context = binding.root.context
        var article: Article? = null
            set(value) {
                field = value
                val item = value!!
                binding.text1.text = item.title
                binding.text1.visibility = if (item.title.isNotEmpty()) View.VISIBLE else View.GONE
                val color = randomColor()
                binding.text1.setTextColor(color)
                binding.text2.text = item.content
                binding.text2.visibility =
                    if (item.content?.isNotEmpty() == true) View.VISIBLE else View.GONE
                val span = item.expend.spannable(string = { it.name },
                    call = { tag ->
                        context.startActivity(
                            Intent(
                                context,
                                ListActivity::class.java
                            ).putExtra("url", tag.url).putExtra("name", tag.name)
                        )
                    })
                binding.text3.text = span
                binding.text3.visibility = if (item.tags.isNotEmpty()) View.VISIBLE else View.GONE
                binding.text4.text = context.getString(
                    R.string.app_list_time,
                    datafmt.format(item.time ?: Date()),
                    item.author?.name ?: "",
                    item.comments
                )
                binding.text4.setTextColor(color)
                binding.text4.visibility =
                    if (binding.text4.text.isNullOrEmpty()) View.GONE else View.VISIBLE
                Glide.with(context).load(item.img).placeholder(R.drawable.loading)
                    .error(R.drawable.placeholder).into(binding.image1)
            }

        init {
            binding.root.setOnClickListener(this)
            binding.root.tag = this
            binding.text3.movementMethod = LinkMovementMethod.getInstance()
        }

        override fun onClick(p0: View?) {
            context.startActivity(
                Intent(context, InfoActivity::class.java).putExtra(
                    "article",
                    article as Parcelable
                )
            )
        }
    }

    class ArticleDiffCallback : DiffUtil.ItemCallback<Article>() {
        override fun areItemsTheSame(oldItem: Article, newItem: Article): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Article, newItem: Article): Boolean =
            oldItem == newItem
    }

    class ArticleAdapter : PagingAdapter<Article, ArticleHolder>(ArticleDiffCallback()) {
        var last: Int = -1
        private val interpolator = DecelerateInterpolator(3F)
        private val from: Float by lazy {
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                200F,
                HAcgApplication.instance.resources.displayMetrics
            )
        }

        override fun onBindViewHolder(holder: ArticleHolder, position: Int) {
            holder.article = data[position]
            if (position > last) {
                last = position
                ObjectAnimator.ofFloat(holder.itemView, View.TRANSLATION_Y.name, from, 0F)
                    .setDuration(1000).also { it.interpolator = interpolator }.start()
            }
        }

        override fun clear(): DataAdapter<Article, ArticleHolder> =
            super.clear().apply { last = -1 }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArticleHolder =
            ArticleHolder(
                ArticleItemBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
    }
}