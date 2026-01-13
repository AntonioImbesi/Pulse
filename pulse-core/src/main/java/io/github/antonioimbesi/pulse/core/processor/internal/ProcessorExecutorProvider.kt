package io.github.antonioimbesi.pulse.core.processor.internal

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

internal class ProcessorExecutorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return ProcessorExecutorGenerator(
            codeGenerator = environment.codeGenerator,
            logger = environment.logger,
        )
    }
}
