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
 * 4. Validates that at most one init processor exists per group
 * 5. Delegates to [ProcessorExecutorFactory] to generate the executor source code
 * 6. Writes the generated file to the KSP output directory
 *
 * **Init Processor Validation:**
 * - At most ONE [InitProcessor][io.github.antonioimbesi.pulse.core.processor.InitProcessor]
 *   is allowed per feature
 * - If multiple init processors are detected, a compilation error is raised
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

        // Extract processor info and group by feature
        val processorsByFeature = processorsKClass
            .mapNotNull { ProcessorInfo.from(it, logger) }
            .groupBy { info ->
                // Group by base intent type, or by state type if init processor
                info.baseIntentType?.declaration?.qualifiedName?.asString()
                    ?: info.parameterType.declaration.qualifiedName!!.asString()
            }

        // Generate executor for each feature group
        processorsByFeature.forEach { (featureKey, processorsInfo) ->
            // Validate init processor constraints
            val initProcessors = processorsInfo.filter { it.processorKind == ProcessorInfo.ProcessorKind.INIT }
            if (initProcessors.size > 1) {
                logger.error(
                    "Feature has multiple init processors. Only one InitProcessor is allowed per feature.\n" +
                            "Found: ${initProcessors.joinToString(", ") { it.parameterName }}"
                )
                return emptyList()
            }

            // Determine the feature name for the executor
            val featureName = if (processorsInfo.any { it.processorKind == ProcessorInfo.ProcessorKind.INTENT }) {
                // Use base intent name if intent processors exist
                processorsInfo.first { it.processorKind == ProcessorInfo.ProcessorKind.INTENT }
                    .baseIntentType!!.declaration.qualifiedName!!.asString()
            } else {
                // Otherwise use the state type name from init processor
                val stateType = MviContractInfo.from(processorsInfo).stateType
                stateType.declaration.qualifiedName!!.asString()
            }

            generateExecutorClass(
                featureName = featureName,
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
     * (or state's) package, making it easily distinguishable from hand-written code.
     *
     * @param featureName Fully qualified name of the feature (base intent or state type).
     * @param processorsInfo All processors for this feature.
     * @param resolver KSP resolver for classpath checks.
     */
    private fun generateExecutorClass(
        featureName: String,
        processorsInfo: List<ProcessorInfo>,
        resolver: Resolver,
    ) {
        // Determine package based on first processor
        val firstProcessor = processorsInfo.first()
        val featurePackageName = firstProcessor.parameterType.declaration.packageName.asString()
        val featureSimpleName = featureName.substringAfterLast('.')

        val packageName = "$featurePackageName.generated"
        val executorClassName = "${featureSimpleName}ProcessorExecutor"

        val fileContent = ProcessorExecutorFactory.generate(
            packageName = packageName,
            executorClassName = executorClassName,
            processors = processorsInfo,
            resolver = resolver,
        )

        // Dependencies are marked as aggregating because adding a new @Processor
        // for this feature should trigger regeneration
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