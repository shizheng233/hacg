package io.github.yueeng.hacg

import android.app.Application
import com.google.android.material.color.DynamicColors

class HAcgApplication : Application() {
    companion object {

        private lateinit var _instance: HAcgApplication

        val instance: HAcgApplication get() = _instance
    }

    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }

    init {
        _instance = this
    }
}