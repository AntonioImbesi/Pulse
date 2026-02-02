package io.github.antonioimbesi.pulse.compiler

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

/**
 * KSP service discovery entry point for the Pulse code generator.
 *
 * This class is registered in `META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider`
 * and is instantiated by KSP at compile time. It creates the [ProcessorExecutorGenerator]
 * that performs the actual code generation.
 *
 * @see ProcessorExecutorGenerator The symbol processor this provider creates
 */
internal class ProcessorExecutorProvider : SymbolProcessorProvider {
    /**
     * Creates the [ProcessorExecutorGenerator] with access to the KSP environment.
     *
     * @param environment Provides access to the code generator and logger APIs.
     * @return A configured symbol processor ready to generate executor classes.
     */
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return ProcessorExecutorGenerator(
            codeGenerator = environment.codeGenerator,
            logger = environment.logger,
        )
    }
}
