package io.github.ffelixq.medswidget

import android.app.Application
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.FirebaseApp

class MedsApplication :
    Application(),
    DefaultLifecycleObserver {
    @Volatile
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super<Application>.onCreate()
        runCatching { FirebaseApp.initializeApp(this) }
        graph = AppGraph(this)
        graph.start()
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        graph.setForeground(true)
    }

    override fun onStop(owner: LifecycleOwner) {
        graph.setForeground(false)
    }

    @Synchronized
    fun reinitializeAfterAccountDeletion() {
        val replacement = AppGraph(this)
        graph = replacement
        replacement.start()
        replacement.setForeground(
            ProcessLifecycleOwner
                .get()
                .lifecycle.currentState
                .isAtLeast(Lifecycle.State.STARTED),
        )
    }

    @Synchronized
    private fun currentGraph(): AppGraph = graph

    companion object {
        fun graph(context: Context): AppGraph = (context.applicationContext as MedsApplication).currentGraph()
    }
}
