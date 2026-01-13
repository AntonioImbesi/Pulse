package io.github.antonioimbesi.pulse.sample.counter.contract

sealed interface CounterIntention {
    data object Increase : CounterIntention
    data object Decrease : CounterIntention
}