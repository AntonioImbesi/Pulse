package io.github.antonioimbesi.pulse.compiler

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import io.github.antonioimbesi.pulse.core.processor.IntentProcessor

/**
 * Metadata extracted from a single [Processor][io.github.antonioimbesi.pulse.core.processor.Processor]-annotated class.
 *
 * This data class captures all type information needed to generate the `when` branch
 * that routes a specific intent to its processor in the generated [ProcessorExecutor][io.github.antonioimbesi.pulse.core.processor.ProcessorExecutor].
 *
 * @property parameterName The camelCase name for the processor constructor parameter.
 *                         Derived from the class name (e.g., `LoginProcessor` → `loginProcessor`).
 * @property parameterType The star-projected type of the processor class for import generation.
 * @property handledIntentType The specific intent subtype this processor handles
 *                             (e.g., `LoginIntent.Submit`).
 * @property baseIntentType The sealed base intent type (e.g., `LoginIntent`) used to group
 *                          processors into a single executor.
 *
 * @see MviContractInfo The aggregated type contract derived from processors
 */
internal data class ProcessorInfo(
    val parameterName: String,
    val parameterType: KSType,
    val handledIntentType: KSType,
    val baseIntentType: KSType,
) {
    internal companion object {
        /**
         * Extracts [ProcessorInfo] from a KSP class declaration.
         *
         * Walks the class's supertype hierarchy to find [IntentProcessor], then resolves
         * the type arguments to determine which intent this processor handles and what
         * the base intent type is.
         *
         * @param declaration The [KSClassDeclaration] of the `@Processor`-annotated class.
         * @param logger KSP logger for reporting errors when the hierarchy is invalid.
         * @return The extracted metadata, or `null` if validation fails (error logged).
         */
        fun from(declaration: KSClassDeclaration, logger: KSPLogger): ProcessorInfo? {
            val processorInterfaceName = IntentProcessor::class.qualifiedName!!

            val superType = declaration.getAllSuperTypes().firstOrNull {
                it.declaration.qualifiedName?.asString() == processorInterfaceName
            } ?: run {
                logger.error(
                    "Could not find IntentProcessor in the hierarchy of" +
                            " '${declaration.simpleName.asString()}'"
                )
                return null
            }

            // Positional access is used to extract the 'Intent' type (second parameter)
            // from IntentProcessor<State, Intent, SideEffect>. This creates a compile-time
            // dependency on the interface's type parameter order. Any structural
            // changes to IntentProcessor will require a corresponding update to this logic.
            val intentType = superType.arguments.getOrNull(1)?.type?.resolve()

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
                    .replaceFirstChar { a: Char -> a.lowercase() },
                parameterType = declaration.asStarProjectedType(),
                handledIntentType = intentType,
                baseIntentType = intentDeclaration.superTypes.first().resolve(),
            )
        }
    }
}
