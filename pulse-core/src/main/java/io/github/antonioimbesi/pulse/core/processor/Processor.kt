package io.github.antonioimbesi.pulse.core.processor

/**
 * Marks a class as an intent processor for KSP code generation.
 *
 * When applied to a class implementing [IntentProcessor], the KSP compiler generates:
 * - A `ProcessorExecutor` that routes intents to the appropriate processor
 * - Type-safe `when` expressions ensuring exhaustive intent handling at compile-time
 * - Optional `@Inject` constructor when javax.inject is on the classpath
 *
 * **Usage:**
 * ```kotlin
 * @Processor
 * class LoginProcessor : IntentProcessor<LoginState, LoginIntent.Submit, LoginEffect> {
 *     override suspend fun ProcessorScope<LoginState, LoginEffect>.process(
 *         intent: LoginIntent.Submit
 *     ) {
 *         reduce { copy(isLoading = true) }
 *         // ... handle login
 *     }
 * }
 * ```
 *
 * **Generated Code Location:**
 * Executors are generated in a `generated` subpackage relative to the processors.
 * For example, processors in `com.example.login` produce an executor in
 * `com.example.login.generated`.
 *
 * @see IntentProcessor The interface that annotated classes must implement
 * @see ProcessorExecutor The generated executor interface
 */
@Target(AnnotationTarget.CLASS)
annotation class Processor