package io.github.antonioimbesi.pulse.compiler

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import io.github.antonioimbesi.pulse.core.processor.InitProcessor
import io.github.antonioimbesi.pulse.core.processor.IntentProcessor

/**
 * Container for the resolved MVI type references extracted from processor declarations.
 *
 * During code generation, the compiler needs to know the concrete types for State, Intent,
 * and SideEffect to generate properly typed executor classes. This class aggregates these
 * types after resolving them from the [IntentProcessor] or [InitProcessor]
 * supertype hierarchy.
 *
 * @property stateType The resolved [KSType] for the State type parameter.
 * @property baseIntentType The sealed base intent type that all handled intents extend (null if only init processor exists).
 * @property sideEffectType The resolved [KSType] for the SideEffect type parameter.
 *
 * @see ProcessorInfo The per-processor metadata that this class aggregates
 */
internal data class MviContractInfo(
    val stateType: KSType,
    val baseIntentType: KSType?,
    val sideEffectType: KSType,
) {
    internal companion object {
        /**
         * Creates an [MviContractInfo] by extracting type information from the processors.
         *
         * Handles both regular intent processors and init processors. Type information
         * is extracted from the first intent processor if available, otherwise from
         * the init processor.
         *
         * @param processors The list of processors for a single feature (must not be empty).
         * @return The extracted MVI type contract.
         * @throws NoSuchElementException If [processors] is empty.
         */
        fun from(processors: List<ProcessorInfo>): MviContractInfo {
            // Try to get type info from an intent processor first
            val intentProcessor = processors.firstOrNull { it.processorKind == ProcessorInfo.ProcessorKind.INTENT }

            if (intentProcessor != null) {
                val processorInterface =
                    (intentProcessor.parameterType.declaration as KSClassDeclaration)
                        .superTypes.first {
                            it.resolve().declaration.qualifiedName?.asString() ==
                                    IntentProcessor::class.qualifiedName
                        }.resolve()
                val stateType = processorInterface.arguments[0].type!!.resolve()
                val sideEffectType = processorInterface.arguments[2].type!!.resolve()
                val baseIntentType = intentProcessor.baseIntentType!!
                return MviContractInfo(stateType, baseIntentType, sideEffectType)
            }

            // Otherwise, get it from init processor
            val initProcessor = processors.first { it.processorKind == ProcessorInfo.ProcessorKind.INIT }
            val processorInterface =
                (initProcessor.parameterType.declaration as KSClassDeclaration)
                    .superTypes.first {
                        it.resolve().declaration.qualifiedName?.asString() ==
                                InitProcessor::class.qualifiedName
                    }.resolve()
            val stateType = processorInterface.arguments[0].type!!.resolve()
            val sideEffectType = processorInterface.arguments[1].type!!.resolve()
            return MviContractInfo(stateType, null, sideEffectType)
        }
    }
}