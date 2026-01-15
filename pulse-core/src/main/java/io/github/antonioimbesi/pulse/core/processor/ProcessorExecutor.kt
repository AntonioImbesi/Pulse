package io.github.antonioimbesi.pulse.core.processor

fun interface ProcessorExecutor<State, in Intent, SideEffect> {
    suspend fun execute(context: ProcessorScope<State, SideEffect>, intent: Intent): Unit
}
