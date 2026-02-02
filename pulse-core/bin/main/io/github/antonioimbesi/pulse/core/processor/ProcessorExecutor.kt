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
 * @Processor class SignInProcessor : IntentProcessor<LoginState, LoginIntent.SignIn, LoginEffect>
 * @Processor class InitLoginProcessor : InitProcessor<LoginState, LoginEffect>
 * ```
 *
 * KSP generates an executor like:
 * ```kotlin
 * class LoginIntentProcessorExecutor @Inject constructor(
 *     private val emailChangedProcessor: EmailChangedProcessor,
 *     private val signInProcessor: SignInProcessor,
 *     private val initLoginProcessor: InitLoginProcessor
 * ) : ProcessorExecutor<LoginState, LoginIntent, LoginEffect> {
 *     override suspend fun execute(processorScope: ProcessorScope<LoginState, LoginEffect>, intent: LoginIntent) {
 *         when (intent) {
 *             is LoginIntent.EmailChanged -> with(emailChangedProcessor) { processorScope.process(intent) }
 *             is LoginIntent.SignIn -> with(SignInProcessor) { processorScope.process(intent) }
 *         }
 *     }
 *
 *     override fun getInitProcessor(): InitProcessor<LoginState, LoginEffect>? {
 *         return initLoginProcessor
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
 * @see InitProcessor The interface for initialization processors
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
     * @param processorScope The [ProcessorScope] providing state access and mutation.
     * @param intent The intent to route and process.
     */
    suspend fun execute(processorScope: ProcessorScope<State, SideEffect>, intent: Intent): Unit

    /**
     * Returns the init processor if one exists for this feature.
     *
     * Called once during engine initialization. The init processor runs before
     * any user intents are processed.
     *
     * The generated implementation returns the injected [InitProcessor]
     * if one was annotated with [@Processor][Processor], otherwise returns null.
     *
     * **Note:** Only one init processor is allowed per feature. The KSP compiler
     * will generate a compile error if multiple init processors are detected.
     *
     * @return The init processor, or null if no init processor was defined
     */
    fun getInitProcessor(): InitProcessor<State, SideEffect>? = null
}