package io.github.antonioimbesi.pulse.core.processor.internal

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType

internal data class MviContractInfo(
    val stateType: KSType,
    val baseIntentionType: KSType,
    val sideEffectType: KSType,
) {
    internal companion object {
        fun from(processors: List<ProcessorInfo>): MviContractInfo {
            val firstProcessor = processors.first()
            val processorInterface =
                (firstProcessor.parameterType.declaration as KSClassDeclaration)
                    .superTypes.first().resolve()
            val stateType = processorInterface.arguments[0].type!!.resolve()
            val sideEffectType = processorInterface.arguments[2].type!!.resolve()
            val baseIntentionType = firstProcessor.baseIntentionType
            return MviContractInfo(stateType, baseIntentionType, sideEffectType)
        }
    }
}
