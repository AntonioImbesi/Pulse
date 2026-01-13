package io.github.antonioimbesi.pulse.core.observer

fun interface StateObserver {
    fun onState(oldState: Any, newState: Any)
}