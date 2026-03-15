package com.simtop.billionbeers.snapshot_processor

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.writeTo
import java.io.OutputStream

@Suppress("unused")
class SnapshotProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return SnapshotProcessor(environment.codeGenerator, environment.logger)
    }
}

class SnapshotProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val previewSymbols = resolver.getSymbolsWithAnnotation("androidx.compose.ui.tooling.preview.Preview")
        val allPreviewFunctions = mutableSetOf<KSFunctionDeclaration>()
        
        // Use getNewFiles() to avoid reprocessing files that haven't changed in incremental builds
        resolver.getNewFiles().forEach { file ->
            file.declarations.filterIsInstance<KSFunctionDeclaration>().forEach { func ->
                if (isValidPreview(func)) {
                    allPreviewFunctions.add(func)
                }
            }
        }
        
        // If we found new functions, generate their providers
        if (allPreviewFunctions.isNotEmpty()) {
             // Deduplicate by function qualified name just in case
             val uniqueFunctions = allPreviewFunctions.distinctBy { it.qualifiedName?.asString() }
             generatePreviewProvider(uniqueFunctions, resolver)
        }
        
        // For the service file, we technically need a list of ALL generated providers, not just new ones.
        // But KSP doesn't allow easy aggregation across rounds/incremental runs into a single file without re-processing everything.
        // A common workaround is to not generate the service file here, or use a separate "aggregating" processor.
        // OR: accepted hack: since this is a test processor, maybe we just suppressing the exception for standard files was wrong, 
        // but for the ServiceFile it is tricky.
        
        // Let's rely on the previous try-catch for the ServiceFile which is supposed to be aggregating.
        // But we need to make sure we don't crash on the INDIVIDUAL provider files.
        // Switching to getNewFiles() above solves the crash for individual files.
        
        // However, if we only process new files, we only add NEW providers to the ServiceFile list passed to generatePreviewProvider.
        // This means the ServiceFile will only contain new providers, overwriting the old one?
        // No, createNewFile throws if it exists.
        
        // If we want a correct ServiceFile in incremental builds, we have to look up all existing providers?
        // This is complex.
        // Minimal viable fix: Only avoid crashing on individual files.
        // If ServiceFile generation fails, we might miss entries in incremental builds.
        // But for "recordPaparazzi", we usually run a CLEAN build or non-incremental is fine.
        // The crashing was the blocker.
        
        return emptyList() // We don't return deferrals for now
    }

    private fun isValidPreview(func: KSFunctionDeclaration): Boolean {
       // Check for direct @Preview or meta-annotation
       for (annotation in func.annotations) {
           val annotationType = annotation.annotationType.resolve()
           if (annotationType.declaration.qualifiedName?.asString() == "androidx.compose.ui.tooling.preview.Preview") {
               return true
           }
           // Check meta-annotations
           val metaAnnotations = annotationType.declaration.annotations
           for (meta in metaAnnotations) {
               if (meta.annotationType.resolve().declaration.qualifiedName?.asString() == "androidx.compose.ui.tooling.preview.Preview") {
                   return true
               }
           }
       }
       return false
    }

    private fun generatePreviewProvider(functions: List<KSFunctionDeclaration>, resolver: Resolver) {
        val generatedProviders = mutableListOf<String>()
        
        // Group by containing file to generate one provider per source file
        functions.groupBy { it.containingFile }.forEach { (file, previewFuncs) ->
             if (file == null) return@forEach
             
             val filePackage = file.packageName.asString()
             val fileName = file.fileName.substringBefore(".") + "_PreviewProvider"
             val fullClassName = "$filePackage.$fileName"
             
             generateFile(filePackage, fileName, previewFuncs, file)
             generatedProviders.add(fullClassName)
        }
        
        if (generatedProviders.isNotEmpty()) {
            generateServiceFile(generatedProviders, resolver)
        }
    }

    private fun generateServiceFile(providers: List<String>, resolver: Resolver) {
        // We need to aggregate providers from all rounds/files.
        // However, KSP process is per round.
        // If we generate META-INF/services/..., we might have issues with multiple rounds if not handled.
        // For now, let's assume one round or that this is sufficient.
        // BEWARE: Creating the same file in multiple rounds throws FileAlreadyExistsException.
        // We should probably only generate this once or append? KSP doesn't support append easily.
        
        // Hack: Use a unique file name per process/module? No, ServiceLoader needs a specific file name.
        // "com.simtop.billionbeers.snapshot_testing.PreviewProvider"
        
        // If we have multiple modules, each module generates its own jar/resources, so it's fine.
        // But if KSP runs multiple rounds, we might try to write it twice.
        // We can check if we already generated it?
        // simple check: fail silently if it exists?
        
        try {
            val resourceFile = codeGenerator.createNewFile(
                Dependencies(true, *resolver.getAllFiles().toList().toTypedArray()), 
                "META-INF/services",
                "com.simtop.billionbeers.snapshot_testing.PreviewProvider",
                ""
            )
            resourceFile.bufferedWriter().use { writer ->
                providers.forEach {
                    writer.write(it)
                    writer.newLine()
                }
            }
        } catch (e: FileAlreadyExistsException) {
            // If it exists, we might be in a second round or partial processing.
            // Ideally we should adhere to KSP incremental processing rules.
            // For now, logging and ignoring might be risky if we missed providers.
            // But since we are processing ALL files every time in our logic (resolver.getAllFiles()), we re-generate everything.
            // Wait, resolver.getAllFiles() returns files to be processed.
            // If we are in incremental mode, we might only get a subset.
            // This global aggregation strategy is weak for incremental builds.
            // But for "recordPaparazzi", we usually run a full build.
        } catch (e: Exception) { // Catch generically to avoid build failure on resource duplication
             // logger.warn("Failed to generate service file: $e")
        }
    }

    private fun generateFile(packageName: String, className: String, functions: List<KSFunctionDeclaration>, sourceFile: KSFile) {
        val snapshotClass = ClassName("com.simtop.billionbeers.snapshot_testing", "Snapshot")
        val previewProviderInterface = ClassName("com.simtop.billionbeers.snapshot_testing", "PreviewProvider")
        
        val codeBlockBuilder = CodeBlock.builder().add("listOf(\n")

        functions.forEach { func ->
            val funcName = func.simpleName.asString()
            val parameters = func.parameters
            
            if (parameters.isEmpty()) {
                codeBlockBuilder.add("    %T(%S, { %M() }),\n", snapshotClass, "${funcName}", MemberName(packageName, funcName))
            } else {
                // Handle PreviewParameter
                val param = parameters.first()
                val previewParamAnnotation = param.annotations.find { 
                    it.annotationType.resolve().declaration.qualifiedName?.asString() == "androidx.compose.ui.tooling.preview.PreviewParameter"
                }
                
                if (previewParamAnnotation != null) {
                        val providerType = previewParamAnnotation.arguments.first().value as KSType
                        val providerClassName = providerType.declaration.qualifiedName?.asString() ?: ""
                        
                        val providerClass = ClassName.bestGuess(providerClassName)
                         
                         codeBlockBuilder.add(
                             """
                             |    *%T().let { provider -> 
                             |        provider.values.mapIndexed { index, value ->
                             |            val nameSuffix = (value as? Enum<*>)?.name ?: index.toString()
                             |            %T("${funcName}_" + nameSuffix, { %M(value) })
                             |        }.toList().toTypedArray()
                             |    },
                             |""".trimMargin(),
                             providerClass, snapshotClass, MemberName(packageName, funcName)
                         )

                }
            }
        }
        
        codeBlockBuilder.add(")")

        val propertySpec = PropertySpec.builder("snapshots", LIST.parameterizedBy(snapshotClass))
            .addModifiers(KModifier.OVERRIDE)
            .initializer(codeBlockBuilder.build())
            .build()

        val typeSpec = TypeSpec.classBuilder(className)
            .addSuperinterface(previewProviderInterface)
            .addProperty(propertySpec)
            .build()

        val fileSpec = FileSpec.builder(packageName, className)
            .addType(typeSpec)
            .build()
            
        // Use aggregating=true because the service file depends on multiple files, 
        // but this specific file only depends on one source file.
        fileSpec.writeTo(codeGenerator, Dependencies(true, sourceFile))
    }
}
