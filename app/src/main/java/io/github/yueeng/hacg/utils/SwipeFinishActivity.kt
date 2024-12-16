package io.github.yueeng.hacg.utils

import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import io.github.yueeng.hacg.R

open class SwipeFinishActivity : AppCompatActivity() {
    @Deprecated(
        "Disable super setContentView",
        ReplaceWith(
            "super.setContentView(layoutResID)",
            "io.github.yueeng.hacg.SwipeFinishActivity"
        )
    )
    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
    }

    @Deprecated(
        "Disable super setContentView",
        ReplaceWith(
            "super.setContentView(layoutResID)",
            "io.github.yueeng.hacg.SwipeFinishActivity"
        )
    )
    override fun setContentView(view: View?) {
        super.setContentView(view)
    }

    @Deprecated(
        "Disable super setContentView",
        ReplaceWith(
            "super.setContentView(layoutResID)",
            "io.github.yueeng.hacg.SwipeFinishActivity"
        )
    )
    override fun setContentView(view: View?, params: ViewGroup.LayoutParams?) {
        super.setContentView(view, params)
    }

    fun setContentView(layout: Int, callback: (View) -> Unit) {
        setContentView(layoutInflater.inflate(layout, null), callback)
    }

    fun setContentView(layout: View, callback: (View) -> Unit) {
        super.setContentView(R.layout.activity_swipeback)
        val pager = super.findViewById<ViewPager2>(R.id.swipe_host)
        pager.adapter = SwipeBackAdapter(layout, callback)
        pager.offscreenPageLimit = 2
        pager.setCurrentItem(1, false)
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            var pos = -1

            override fun onPageSelected(position: Int) {
                pos = position
            }

            override fun onPageScrollStateChanged(state: Int) {
                if (state != ViewPager2.SCROLL_STATE_IDLE || pos != 0) return
                finish()
                if (Build.VERSION.SDK_INT >= 34) {
                    overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
                } else {
                    @Suppress("DEPRECATION") overridePendingTransition(0, 0)
                }
            }
        })
    }

    override fun <T : View?> findViewById(id: Int): T? {
        val pager =
            super.findViewById<ViewPager2>(R.id.swipe_host)?.children?.firstOrNull() as? RecyclerView
        val holder = pager?.findViewHolderForLayoutPosition(1)
        return holder?.itemView?.findViewById<T>(id)
    }

    class SwipeBackHolder(view: View) : RecyclerView.ViewHolder(view)

    class SwipeBackAdapter(private val layout: View, private val callback: (View) -> Unit) :
        RecyclerView.Adapter<SwipeBackHolder>() {
        override fun getItemViewType(position: Int): Int = when (position) {
            0 -> 0
            1 -> 1
            else -> throw IllegalArgumentException("Position $position is not supported")
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SwipeBackHolder =
            when (viewType) {
                0 -> SwipeBackHolder(
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.swipeback_start, parent, false)
                )

                1 -> LayoutInflater.from(parent.context).let { inflater ->
                    val host =
                        inflater.inflate(R.layout.swipeback_container, parent, false) as ViewGroup
                    host.addView(layout)
                    SwipeBackHolder(host)
                }

                else -> throw IllegalArgumentException("ViewType $viewType is not supported")
            }


        override fun onBindViewHolder(holder: SwipeBackHolder, position: Int) {
            if (position == 1) holder.itemView.post { callback(holder.itemView) }
        }

        override fun getItemCount(): Int = 2
    }

}