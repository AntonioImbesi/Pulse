package io.github.antonioimbesi.pulse.core.observer

fun interface SideEffectObserver {
    fun onSideEffect(sideEffect: Any)
}