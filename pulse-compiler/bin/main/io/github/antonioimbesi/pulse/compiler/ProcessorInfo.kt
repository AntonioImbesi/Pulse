package io.github.antonioimbesi.pulse.compiler

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import io.github.antonioimbesi.pulse.core.processor.InitProcessor
import io.github.antonioimbesi.pulse.core.processor.IntentProcessor

/**
 * Metadata extracted from a single [Processor][io.github.antonioimbesi.pulse.core.processor.Processor]-annotated class.
 *
 * This data class captures all type information needed to generate the routing logic
 * in the generated [ProcessorExecutor][io.github.antonioimbesi.pulse.core.processor.ProcessorExecutor].
 *
 * Processors can be one of two types:
 * - **Intent Processors**: Handle specific user intents (implements [IntentProcessor])
 * - **Init Processors**: Run once at engine startup (implements [InitProcessor])
 *
 * @property parameterName The camelCase name for the processor constructor parameter.
 *                         Derived from the class name (e.g., `LoginProcessor` → `loginProcessor`).
 * @property parameterType The star-projected type of the processor class for import generation.
 * @property processorKind The kind of processor (intent handler or init processor).
 * @property handledIntentType The specific intent subtype this processor handles (null for init processors).
 * @property baseIntentType The sealed base intent type used to group processors (null for init processors).
 *
 * @see MviContractInfo The aggregated type contract derived from processors
 */
internal data class ProcessorInfo(
    val parameterName: String,
    val parameterType: KSType,
    val processorKind: ProcessorKind,
    val handledIntentType: KSType?,
    val baseIntentType: KSType?,
) {
    /**
     * Discriminates between intent processors and init processors.
     */
    enum class ProcessorKind {
        /** Runs once at engine initialization */
        INIT,
        /** Handles specific user intents */
        INTENT,
    }

    internal companion object {
        /**
         * Extracts [ProcessorInfo] from a KSP class declaration.
         *
         * Walks the class's supertype hierarchy to find either [IntentProcessor] or
         * [InitProcessor], then resolves the type arguments accordingly.
         *
         * @param declaration The [KSClassDeclaration] of the `@Processor`-annotated class.
         * @param logger KSP logger for reporting errors when the hierarchy is invalid.
         * @return The extracted metadata, or `null` if validation fails (error logged).
         */
        fun from(declaration: KSClassDeclaration, logger: KSPLogger): ProcessorInfo? {
            val intentProcessorInterfaceName = IntentProcessor::class.qualifiedName!!
            val initProcessorInterfaceName = InitProcessor::class.qualifiedName!!

            val allSuperTypes = declaration.getAllSuperTypes().toList()

            // Check if this is an InitProcessor
            val initSuperType = allSuperTypes.firstOrNull {
                it.declaration.qualifiedName?.asString() == initProcessorInterfaceName
            }

            if (initSuperType != null) {
                return ProcessorInfo(
                    parameterName = declaration.simpleName.asString()
                        .replaceFirstChar { it.lowercase() },
                    parameterType = declaration.asStarProjectedType(),
                    processorKind = ProcessorKind.INIT,
                    handledIntentType = null,
                    baseIntentType = null,
                )
            }

            // Check if this is an IntentProcessor
            val intentSuperType = allSuperTypes.firstOrNull {
                it.declaration.qualifiedName?.asString() == intentProcessorInterfaceName
            }

            if (intentSuperType == null) {
                logger.error(
                    "Could not find IntentProcessor or InitProcessor in the hierarchy of" +
                            " '${declaration.simpleName.asString()}'"
                )
                return null
            }

            // Positional access is used to extract the 'Intent' type (second parameter)
            // from IntentProcessor<State, Intent, SideEffect>. This creates a compile-time
            // dependency on the interface's type parameter order. Any structural
            // changes to IntentProcessor will require a corresponding update to this logic.
            val intentType = intentSuperType.arguments.getOrNull(1)?.type?.resolve()

            val intentDeclaration = intentType?.declaration
            if (intentDeclaration !is KSClassDeclaration) {
                logger.error(
                    message = "Intent type " +
                            "'${intentType?.declaration?.simpleName?.asString()}' " +
                            "must be a class or object.",
                    symbol = intentDeclaration
                )
                return null
            }

            return ProcessorInfo(
                parameterName = declaration.simpleName.asString()
                    .replaceFirstChar { it.lowercase() },
                parameterType = declaration.asStarProjectedType(),
                processorKind = ProcessorKind.INTENT,
                handledIntentType = intentType,
                baseIntentType = intentDeclaration.superTypes.first().resolve(),
            )
        }
    }
}