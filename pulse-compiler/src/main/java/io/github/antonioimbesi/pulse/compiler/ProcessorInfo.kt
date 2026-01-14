package io.github.antonioimbesi.pulse.compiler

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import io.github.antonioimbesi.pulse.core.processor.IntentionProcessor

internal data class ProcessorInfo(
    val parameterName: String,
    val parameterType: KSType,
    val handledIntentionType: KSType,
    val baseIntentionType: KSType,
) {
    internal companion object {
        fun from(declaration: KSClassDeclaration, logger: KSPLogger): ProcessorInfo? {
            val processorInterfaceName = IntentionProcessor::class.qualifiedName!!

            val superType = declaration.getAllSuperTypes().firstOrNull {
                it.declaration.qualifiedName?.asString() == processorInterfaceName
            } ?: run {
                logger.error(
                    "Could not find IntentionProcessor in the hierarchy of" +
                            " '${declaration.simpleName.asString()}'"
                )
                return null
            }

            // I personally don't like too much having a fixed index (in this case 1) as if
            // a change will be applied to the IntentionProcessor interface in the future,
            // it will break this line.
            // Since no changes are foreseeable to be made to the IntentionProcessor interface,
            // and for the sake of a sample project, I decided to keep it this way for the moment.
            // When I'll have more free time to dedicate to this project, I'll refine it.
            val intentionType = superType.arguments.getOrNull(1)?.type?.resolve()

            val intentionDeclaration = intentionType?.declaration
            if (intentionDeclaration !is KSClassDeclaration) {
                logger.error(
                    message = "Intention type " +
                            "'${intentionType?.declaration?.simpleName?.asString()}' " +
                            "must be a class or object.",
                    symbol = intentionDeclaration
                )
                return null
            }

            return ProcessorInfo(
                parameterName = declaration.simpleName.asString()
                    .replaceFirstChar { a: Char -> a.lowercase() },
                parameterType = declaration.asStarProjectedType(),
                handledIntentionType = intentionType,
                baseIntentionType = intentionDeclaration.superTypes.first().resolve(),
            )
        }
    }
}
