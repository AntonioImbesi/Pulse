package io.github.antonioimbesi.pulse.core.processor

/**
 * Defines a handler for a specific intent type within the MVI pattern.
 *
 * Each processor handles exactly one intent type and has access to a [ProcessorScope]
 * for updating state and emitting side effects.
 *
 * **Implementation Pattern:**
 * ```kotlin
 * @Processor
 * class IncrementProcessor : IntentProcessor<CounterState, CounterIntent.Increment, CounterEffect> {
 *     override suspend fun ProcessorScope<CounterState, CounterEffect>.process(
 *         intent: CounterIntent.Increment
 *     ) {
 *         reduce { copy(count = count + 1) }
 *     }
 * }
 * ```
 *
 * @param State The immutable state type this processor can read and update.
 * @param Intent The specific intent type this processor handles (contravariant).
 * @param SideEffect The side effect type this processor can emit.
 *
 * @see Processor Annotation to enable KSP code generation
 * @see ProcessorScope The scope providing state access and mutation capabilities
 */
fun interface IntentProcessor<State, in Intent, SideEffect> {
    /**
     * Processes a single intent, potentially updating state and emitting side effects.
     *
     * @receiver The [ProcessorScope] providing access to current state, reduce, and send.
     * @param intent The intent to process.
     */
    suspend fun ProcessorScope<State, SideEffect>.process(intent: Intent)
}
