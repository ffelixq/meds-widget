package io.github.ffelixq.medswidget

import android.app.Application
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

class MedsApplication :
    Application(),
    DefaultLifecycleObserver {
    @Volatile
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super<Application>.onCreate()
        val firebaseApp = runCatching { FirebaseApp.initializeApp(this) }.getOrNull()
        if (firebaseApp != null && BuildConfig.FIREBASE_CONFIGURED && !BuildConfig.DEBUG) {
            runCatching {
                FirebaseAppCheck
                    .getInstance()
                    .installAppCheckProviderFactory(
                        PlayIntegrityAppCheckProviderFactory.getInstance(),
                    )
            }
        }
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
