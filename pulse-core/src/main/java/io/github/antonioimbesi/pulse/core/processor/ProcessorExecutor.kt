package io.github.antonioimbesi.pulse.core.processor

/**
 * Routes dispatched intents to their corresponding [IntentProcessor] implementations.
 *
 * This interface is the bridge between the MVI engine and individual processors.
 * Implementations are **generated at compile-time by KSP** based on classes
 * annotated with [Processor]. You should not implement this interface manually.
 *
 * **How Code Generation Works:**
 *
 * Given processors for a feature:
 * ```kotlin
 * @Processor class EmailChangedProcessor : IntentProcessor<LoginState, LoginIntent.EmailChanged, LoginEffect>
 * @Processor class LoginClickedProcessor : IntentProcessor<LoginState, LoginIntent.LoginClicked, LoginEffect>
 * ```
 *
 * KSP generates an executor like:
 * ```kotlin
 * class LoginIntentProcessorExecutor @Inject constructor(
 *     private val emailChangedProcessor: EmailChangedProcessor,
 *     private val loginClickedProcessor: LoginClickedProcessor
 * ) : ProcessorExecutor<LoginState, LoginIntent, LoginEffect> {
 *     override suspend fun execute(processorScope: ProcessorScope<LoginState, LoginEffect>, intent: LoginIntent) {
 *         when (intent) {
 *             is LoginIntent.EmailChanged -> with(emailChangedProcessor) { processorScope.process(intent) }
 *             is LoginIntent.LoginClicked -> with(loginClickedProcessor) { processorScope.process(intent) }
 *         }
 *     }
 * }
 * ```
 *
 * @param State The immutable state type for all processors in this executor.
 * @param Intent The sealed base type of all intents handled by this executor (contravariant).
 * @param SideEffect The side effect type for all processors.
 *
 * @see Processor Annotation that triggers executor generation
 * @see IntentProcessor The interface each routed processor implements
 */
fun interface ProcessorExecutor<State, in Intent, SideEffect> {
    /**
     * Routes the intent to the appropriate processor and executes it.
     *
     * The generated implementation uses a `when` expression to match the intent
     * type and delegate to the corresponding processor. This is called by
     * [DefaultMviEngine][io.github.antonioimbesi.pulse.core.engine.DefaultMviEngine]
     * on a background coroutine.
     *
     * @param context The [ProcessorScope] providing state access and mutation.
     * @param intent The intent to route and process.
     */
    suspend fun execute(processorScope: ProcessorScope<State, SideEffect>, intent: Intent): Unit
}
