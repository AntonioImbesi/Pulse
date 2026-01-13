package io.github.antonioimbesi.pulse.sample.counter.processor

import io.github.antonioimbesi.pulse.sample.counter.contract.CounterIntention
import io.github.antonioimbesi.pulse.sample.counter.contract.CounterSideEffect
import io.github.antonioimbesi.pulse.sample.counter.contract.CounterState
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class DecreaseProcessorTest {

    private lateinit var testSubject: DecreaseProcessor

    @Before
    fun setUp() {
        testSubject = DecreaseProcessor()
    }

    @Test
    fun `Decrease counter when current value is greater than zero`() = runTest {
        val initialState = CounterState(counter = 5U)
        val scope = TestProcessorScope<CounterState, CounterSideEffect>(initialState)
        testSubject.run { scope.process(CounterIntention.Decrease) }
        assert(scope.events[0] == StateReduced(newState = CounterState(counter = 4U)))
    }

    @Test
    fun `Prevent decrease and trigger side effect when counter is zero`() = runTest {
        val initialState = CounterState(counter = 0U)
        val scope = TestProcessorScope<CounterState, CounterSideEffect>(initialState)
        testSubject.run { scope.process(CounterIntention.Decrease) }
        assert(scope.events[0] == SideEffectSent(sideEffect = CounterSideEffect.BelowZero))
    }

    @Test
    fun `Processor scope side effect emission integrity`() = runTest {
        val initialState = CounterState(counter = 0U)
        val scope = TestProcessorScope<CounterState, CounterSideEffect>(initialState)
        testSubject.run { scope.process(CounterIntention.Decrease) }
        assert(scope.events.size == 1)
        assert(scope.events[0] is SideEffectSent)
    }

    @Test
    fun `Processor scope state reduction integrity`() = runTest {
        val initialState = CounterState(counter = 1U)
        val scope = TestProcessorScope<CounterState, CounterSideEffect>(initialState)
        testSubject.run { scope.process(CounterIntention.Decrease) }
        assert(scope.events.size == 1)
        assert(scope.events[0] is StateReduced)
    }

}