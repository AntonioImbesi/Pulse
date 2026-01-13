package io.github.antonioimbesi.pulse.sample.counter

import android.util.Log
import io.github.antonioimbesi.pulse.core.observer.IntentionObserver
import io.github.antonioimbesi.pulse.core.observer.SideEffectObserver
import io.github.antonioimbesi.pulse.core.observer.StateObserver

object LoggerMviObserver : IntentionObserver, StateObserver, SideEffectObserver {

    override fun onSideEffect(sideEffect: Any) {
        if (BuildConfig.DEBUG)
            Log.d(CounterMviEngineFactory::class.simpleName, "SideEffect: $sideEffect")
    }

    override fun onState(oldState: Any, newState: Any) {
        if (BuildConfig.DEBUG)
            Log.d(CounterMviEngineFactory::class.simpleName, "NewState: $newState")
    }

    override fun onIntention(intention: Any) {
        if (BuildConfig.DEBUG)
            Log.d(CounterMviEngineFactory::class.simpleName, "Intention: $intention")
    }
}
