package io.github.yueeng.hacg.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import io.github.yueeng.hacg.utils.LoadState
import io.github.yueeng.hacg.utils.LoadStateAdapter
import io.github.yueeng.hacg.databinding.ListMsgItemBinding
import io.github.yueeng.hacg.viewholders.MsgHolder

class FooterAdapter(private val count: () -> Int, private val retry: () -> Unit) :
    LoadStateAdapter<MsgHolder>() {
    override fun displayLoadStateAsItem(loadState: LoadState): Boolean = when (loadState) {
        is LoadState.NotLoading -> count() != 0 || loadState.endOfPaginationReached
        is LoadState.Loading -> count() != 0
        else -> true
    }

    override fun onBindViewHolder(holder: MsgHolder, loadState: LoadState) {
        holder.bind(loadState) { count() == 0 }
    }

    override fun onCreateViewHolder(parent: ViewGroup, loadState: LoadState): MsgHolder =
        MsgHolder(
            ListMsgItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        ) { retry() }
}