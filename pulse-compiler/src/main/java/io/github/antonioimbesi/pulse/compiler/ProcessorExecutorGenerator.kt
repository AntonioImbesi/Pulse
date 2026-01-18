package io.github.antonioimbesi.pulse.compiler

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

/**
 * KSP [SymbolProcessor] that generates [ProcessorExecutor][io.github.antonioimbesi.pulse.core.processor.ProcessorExecutor]
 * implementations for each distinct base intent type.
 *
 * **Processing Flow:**
 * 1. Finds all classes annotated with [@Processor][Processor]
 * 2. Extracts [ProcessorInfo] from each, validating the type hierarchy
 * 3. Groups processors by their base intent type (e.g., all `LoginIntent` handlers together)
 * 4. Delegates to [ProcessorExecutorFactory] to generate the executor source code
 * 5. Writes the generated file to the KSP output directory
 *
 * @property codeGenerator KSP code generator for creating output files.
 * @property logger KSP logger for reporting errors during processing.
 *
 * @see ProcessorExecutorProvider The service provider that instantiates this processor
 * @see ProcessorExecutorFactory The code template generator
 */
internal class ProcessorExecutorGenerator(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {

    /**
     * Main entry point called by KSP during compilation.
     *
     * Processors that aren't yet valid (e.g., depend on types not yet generated) are
     * skipped via [validate]. KSP will re-invoke this method in subsequent rounds
     * as more symbols become available.
     *
     * @param resolver Provides access to the Kotlin symbols in the compilation.
     * @return Always returns an empty list; all processing completes in a single round.
     */
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val processorsKClass = resolver
            .getSymbolsWithAnnotation(Processor::class.qualifiedName!!)
            .filterIsInstance<KSClassDeclaration>()
            .filter(KSDeclaration::validate)

        // Early return if no @Processor annotations found in this round
        if (!processorsKClass.iterator().hasNext()) return emptyList()

        // Group processors by base intent type, then generate one executor per group
        processorsKClass.mapNotNull { ProcessorInfo.Companion.from(it, logger) }
            .groupBy { it.baseIntentType.declaration.qualifiedName!!.asString() }
            .forEach { (baseIntentFullName, processorsInfo) ->
                generateMapperFactoryClass(
                    baseIntentFullName = baseIntentFullName,
                    processorsInfo = processorsInfo,
                    resolver = resolver,
                )
            }

        return emptyList()
    }

    /**
     * Generates and writes a single ProcessorExecutor source file.
     *
     * The executor is placed in a `.generated` subpackage relative to the base intent's
     * package, making it easily distinguishable from hand-written code.
     *
     * @param baseIntentFullName Fully qualified name of the base intent (for naming).
     * @param processorsInfo All processors handling subtypes of this base intent.
     * @param resolver KSP resolver for classpath checks.
     */
    private fun generateMapperFactoryClass(
        baseIntentFullName: String,
        processorsInfo: List<ProcessorInfo>,
        resolver: Resolver,
    ) {
        val baseIntentDeclaration = processorsInfo.first().baseIntentType.declaration
        val featurePackageName = baseIntentDeclaration.packageName.asString()
        val baseIntentSimpleName = baseIntentFullName.substringAfterLast('.')

        val packageName = "$featurePackageName.generated"
        val executorClassName = "${baseIntentSimpleName}ProcessorExecutor"

        val fileContent = ProcessorExecutorFactory.generate(
            packageName = packageName,
            executorClassName = executorClassName,
            processors = processorsInfo,
            resolver = resolver,
        )

        // Dependencies are marked as aggregating because adding a new @Processor
        // for this intent type should trigger regeneration
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
