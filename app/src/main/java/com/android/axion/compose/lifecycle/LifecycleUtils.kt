package com.android.axion.compose.lifecycle

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

fun View.repeatWhenAttached(block: suspend CoroutineScope.() -> Unit) {
    var job: Job? = null
    
    val attachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            val lifecycleOwner = v.findViewTreeLifecycleOwner() ?: return
            if (lifecycleOwner.lifecycle.currentState != Lifecycle.State.DESTROYED) {
                job = lifecycleOwner.lifecycleScope.launch(block = block)
            }
        }

        override fun onViewDetachedFromWindow(v: View) {
            job?.cancel()
            job = null
        }
    }
    
    this.addOnAttachStateChangeListener(attachListener)
    
    // If the view is already attached, trigger immediately
    if (this.isAttachedToWindow) {
        attachListener.onViewAttachedToWindow(this)
    }
}
