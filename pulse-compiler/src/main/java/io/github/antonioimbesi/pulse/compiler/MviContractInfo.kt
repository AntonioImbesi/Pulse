package io.github.antonioimbesi.pulse.compiler

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType

/**
 * Container for the resolved MVI type references extracted from processor declarations.
 *
 * During code generation, the compiler needs to know the concrete types for State, Intent,
 * and SideEffect to generate properly typed executor classes. This class aggregates these
 * types after resolving them from the [IntentProcessor][io.github.antonioimbesi.pulse.core.processor.IntentProcessor]
 * supertype hierarchy.
 *
 * @property stateType The resolved [KSType] for the State type parameter.
 * @property baseIntentType The sealed base intent type that all handled intents extend.
 * @property sideEffectType The resolved [KSType] for the SideEffect type parameter.
 *
 * @see ProcessorInfo The per-processor metadata that this class aggregates
 */
internal data class MviContractInfo(
    val stateType: KSType,
    val baseIntentType: KSType,
    val sideEffectType: KSType,
) {
    internal companion object {
        /**
         * Creates an [MviContractInfo] by extracting type information from the first processor.
         *
         * @param processors The list of processors for a single feature (must not be empty).
         * @return The extracted MVI type contract.
         * @throws NoSuchElementException If [processors] is empty.
         */
        fun from(processors: List<ProcessorInfo>): MviContractInfo {
            val firstProcessor = processors.first()
            val processorInterface =
                (firstProcessor.parameterType.declaration as KSClassDeclaration)
                    .superTypes.first().resolve()
            val stateType = processorInterface.arguments[0].type!!.resolve()
            val sideEffectType = processorInterface.arguments[2].type!!.resolve()
            val baseIntentType = firstProcessor.baseIntentType
            return MviContractInfo(stateType, baseIntentType, sideEffectType)
        }
    }
}
