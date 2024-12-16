package io.github.yueeng.hacg.viewholders

import androidx.recyclerview.widget.RecyclerView
import io.github.yueeng.hacg.utils.LoadState
import io.github.yueeng.hacg.R
import io.github.yueeng.hacg.databinding.ListMsgItemBinding

class MsgHolder(private val binding: ListMsgItemBinding, retry: () -> Unit) :
    RecyclerView.ViewHolder(binding.root) {
    init {
        binding.root.setOnClickListener { if (state is LoadState.Error) retry() }
    }

    private var state: LoadState? = null
    fun bind(value: LoadState, empty: () -> Boolean) {
        state = value
        binding.text1.setText(
            when (value) {
                is LoadState.NotLoading -> when {
                    value.endOfPaginationReached && empty() -> R.string.app_list_empty
                    value.endOfPaginationReached -> R.string.app_list_complete
                    else -> R.string.app_list_loadmore
                }

                is LoadState.Error -> R.string.app_list_failed
                else -> R.string.app_list_loading
            }
        )
    }
}