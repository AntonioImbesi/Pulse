package io.github.antonioimbesi.pulse.compiler

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import io.github.antonioimbesi.pulse.core.processor.IntentProcessor

internal data class ProcessorInfo(
    val parameterName: String,
    val parameterType: KSType,
    val handledIntentType: KSType,
    val baseIntentType: KSType,
) {
    internal companion object {
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

            // I personally don't like too much having a fixed index (in this case 1) as if
            // a change will be applied to the IntentProcessor interface in the future,
            // it will break this line.
            // Since no changes are foreseeable to be made to the IntentProcessor interface,
            // and for the sake of a sample project, I decided to keep it this way for the moment.
            // When I'll have more free time to dedicate to this project, I'll refine it.
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
