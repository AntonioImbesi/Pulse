package io.github.antonioimbesi.pulse.sample.counter.contract

sealed interface CounterSideEffect {
    data object BelowZero : CounterSideEffect
}
