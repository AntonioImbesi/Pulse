package io.github.antonioimbesi.pulse.sample.counter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.antonioimbesi.pulse.android.MviViewModel
import io.github.antonioimbesi.pulse.core.MviEngineFactory
import io.github.antonioimbesi.pulse.core.MviHost
import io.github.antonioimbesi.pulse.sample.counter.contract.CounterIntent
import io.github.antonioimbesi.pulse.sample.counter.contract.CounterSideEffect
import io.github.antonioimbesi.pulse.sample.counter.contract.CounterState
import javax.inject.Inject

@HiltViewModel
class CounterViewModel @Inject constructor(
    private val engineFactory: MviEngineFactory<CounterState, CounterIntent, CounterSideEffect>
) : MviViewModel<CounterState, CounterIntent, CounterSideEffect>(
    engineFactory = engineFactory,
    initialState = CounterState()
)
