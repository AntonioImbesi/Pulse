package io.github.antonioimbesi.pulse.core.processor.internal

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.validate
import io.github.antonioimbesi.pulse.core.processor.Processor

internal class ProcessorExecutorGenerator(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val processorsKClass = resolver
            .getSymbolsWithAnnotation(Processor::class.qualifiedName!!)
            .filterIsInstance<KSClassDeclaration>()
            .filter(KSDeclaration::validate)

        if (!processorsKClass.iterator().hasNext()) return emptyList()

        processorsKClass.mapNotNull { ProcessorInfo.Companion.from(it, logger) }
            .groupBy { it.baseIntentionType.declaration.qualifiedName!!.asString() }
            .forEach { (baseIntentionFullName, processorsInfo) ->
                generateMapperFactoryClass(
                    baseIntentionFullName = baseIntentionFullName,
                    processorsInfo = processorsInfo,
                    resolver = resolver,
                )
            }

        return emptyList()
    }

    private fun generateMapperFactoryClass(
        baseIntentionFullName: String,
        processorsInfo: List<ProcessorInfo>,
        resolver: Resolver,
    ) {
        val baseIntentionDeclaration = processorsInfo.first().baseIntentionType.declaration
        val featurePackageName = baseIntentionDeclaration.packageName.asString()
        val baseIntentionSimpleName = baseIntentionFullName.substringAfterLast('.')

        val packageName = "$featurePackageName.generated"
        val executorClassName = "${baseIntentionSimpleName}ProcessorExecutor"

        val fileContent = ProcessorExecutorFactory.generate(
            packageName = packageName,
            executorClassName = executorClassName,
            processors = processorsInfo,
            resolver = resolver,
        )

        codeGenerator.createNewFile(
            dependencies = Dependencies(
                aggregating = true,
                sources = processorsInfo.mapNotNull { it.parameterType.declaration.containingFile }
                    .toTypedArray()
            ),
            packageName = packageName,
            fileName = executorClassName
        ).use { it.write(fileContent.toByteArray()) }
    }
}
