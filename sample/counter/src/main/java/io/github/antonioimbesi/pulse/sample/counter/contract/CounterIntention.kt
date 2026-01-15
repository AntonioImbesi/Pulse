package io.github.antonioimbesi.pulse.sample.counter.contract

sealed interface CounterIntent {
    data object Increase : CounterIntent
    data object Decrease : CounterIntent
}