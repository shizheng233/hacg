package io.github.yueeng.hacg.viewmodels

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel

class WebViewModel(handle: SavedStateHandle, args: Bundle?) : ViewModel() {
    val busy = handle.getLiveData("busy", false)
    val uri = handle.getLiveData("url", args?.getString("url")!!)
}

