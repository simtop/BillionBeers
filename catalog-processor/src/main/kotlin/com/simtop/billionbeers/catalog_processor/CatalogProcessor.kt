package com.simtop.billionbeers.catalog_processor

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.writeTo
import java.io.OutputStream

class CatalogProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return CatalogProcessor(environment.codeGenerator, environment.logger)
    }
}

class CatalogProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {

    @OptIn(com.google.devtools.ksp.KspExperimental::class)
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val annotationName = "com.simtop.billionbeers.catalog_annotations.CatalogComponent"
        val symbols = resolver.getSymbolsWithAnnotation(annotationName)
            .filterIsInstance<KSFunctionDeclaration>()
        
        if (!symbols.iterator().hasNext()) return emptyList()

        val catalogItems = mutableListOf<CatalogItemInfo>()

        symbols.forEach { func ->
            val annotation = func.annotations.find { 
                it.annotationType.resolve().declaration.qualifiedName?.asString() == annotationName 
            } ?: return@forEach

            val tab = annotation.arguments.find { it.name?.asString() == "tab" }?.value as String
            val explicitName = annotation.arguments.find { it.name?.asString() == "name" }?.value as String
            val displayName = if (explicitName.isEmpty()) func.simpleName.asString() else explicitName
            val demoContainer = annotation.arguments.find { it.name?.asString() == "demoContainer" }?.value as? String ?: ""
            
            val packageName = func.packageName.asString()
            val funcName = func.simpleName.asString()
            
            // Generate the interactive wrapper for this component
            val wrapperName = "${funcName}_CatalogWrapper"
            generateWrapper(func, wrapperName, demoContainer)
            
            catalogItems.add(CatalogItemInfo(tab, displayName, packageName, wrapperName))
        }

        if (catalogItems.isNotEmpty()) {
            val moduleName = resolver.getModuleName().asString().replace("-", "_").replace(":", "_")
            generateProvider(catalogItems, moduleName)
        }

        return emptyList()
    }

    private fun generateWrapper(func: KSFunctionDeclaration, wrapperName: String, demoContainer: String) {
        val packageName = func.packageName.asString()
        val funcName = func.simpleName.asString()
        
        val fileSpec = FileSpec.builder(packageName, wrapperName)
            .addImport("androidx.compose.runtime", "getValue", "setValue", "mutableStateOf", "remember")
            .addImport("androidx.compose.foundation.layout", "Column", "Spacer", "height", "padding", "Row")
            .addImport("androidx.compose.material3", "Text", "TextField", "Switch", "Divider", "HorizontalDivider", "Slider", "Checkbox")
            .addImport("androidx.compose.ui", "Modifier", "Alignment")
            .addImport("androidx.compose.foundation.text", "KeyboardOptions")
            .addImport("androidx.compose.ui.text.input", "KeyboardType")
            .addImport("androidx.compose.ui.unit", "dp")
            
        val wrapperFunc = FunSpec.builder(wrapperName)
            .addAnnotation(ClassName("androidx.compose.runtime", "Composable"))
            .addCode(buildCodeBlock {
                // Generate state for each parameter
                func.parameters.forEach { param ->
                    val paramName = param.name?.asString() ?: return@forEach
                    val type = param.type.resolve()
                    val typeName = type.declaration.qualifiedName?.asString()
                    
                    val defaultValue = when (typeName) {
                        "kotlin.String" -> "\"Default Value\""
                        "kotlin.Boolean" -> "false"
                        "kotlin.Int" -> "0"
                        "kotlin.Float" -> "0f"
                        else -> "null"
                    }
                    
                    if (defaultValue != "null") {
                        addStatement("var $paramName by remember { mutableStateOf($defaultValue) }")
                    }
                }
                
                add("\n")
                beginControlFlow("Column(modifier = Modifier.padding(16.dp))")
                
                // Generate controls
                func.parameters.forEach { param ->
                    val paramName = param.name?.asString() ?: return@forEach
                    val type = param.type.resolve()
                    val typeName = type.declaration.qualifiedName?.asString()
                    
                    when (typeName) {
                        "kotlin.String" -> {
                            addStatement("TextField(value = $paramName, onValueChange = { $paramName = it }, label = { Text(%S) })", paramName)
                            addStatement("Spacer(modifier = Modifier.height(8.dp))")
                        }
                        "kotlin.Boolean" -> {
                            beginControlFlow("Row(verticalAlignment = Alignment.CenterVertically)")
                            addStatement("Text(%S)", paramName)
                            addStatement("Checkbox(checked = $paramName, onCheckedChange = { $paramName = it })")
                            endControlFlow()
                            addStatement("Spacer(modifier = Modifier.height(8.dp))")
                        }
                        "kotlin.Int" -> {
                            addStatement("TextField(value = $paramName.toString(), onValueChange = { $paramName = it.toIntOrNull() ?: 0 }, label = { Text(%S) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))", paramName)
                            addStatement("Spacer(modifier = Modifier.height(8.dp))")
                        }
                        "kotlin.Float" -> {
                            addStatement("Text(\"$paramName: \${$paramName}\")")
                            addStatement("Slider(value = $paramName, onValueChange = { $paramName = it })")
                            addStatement("Spacer(modifier = Modifier.height(8.dp))")
                        }
                    }
                }
                
                addStatement("HorizontalDivider()")
                addStatement("Spacer(modifier = Modifier.height(16.dp))")
                
                // Call the actual component
                val supportedTypes = listOf("kotlin.String", "kotlin.Boolean", "kotlin.Int", "kotlin.Float")
                val args = func.parameters
                    .filter { param -> 
                        val typeName = param.type.resolve().declaration.qualifiedName?.asString()
                        typeName in supportedTypes
                    }
                    .map { param -> "${param.name?.asString()} = ${param.name?.asString()}" }
                    .joinToString(", ")
                
                if (demoContainer.isNotEmpty()) {
                    addStatement("%M($args)", MemberName(packageName, demoContainer))
                } else {
                    addStatement("%M($args)", MemberName(packageName, funcName))
                }
                
                endControlFlow()
            })
            .build()
            
        fileSpec.addFunction(wrapperFunc)
        fileSpec.build().writeTo(codeGenerator, Dependencies(true, func.containingFile!!))
    }

    private fun generateProvider(items: List<CatalogItemInfo>, moduleName: String) {
        val packageName = "com.simtop.billionbeers.catalog.generated"
        val className = "${moduleName.capitalize()}CatalogProvider"
        
        val itemClass = ClassName("com.simtop.billionbeers.catalog_annotations", "CatalogItem")
        val providerInterface = ClassName("com.simtop.billionbeers.catalog_annotations", "CatalogProvider")
        
        val fileSpec = FileSpec.builder(packageName, className)
            .addType(TypeSpec.classBuilder(className)
                .addSuperinterface(providerInterface)
                .addProperty(PropertySpec.builder("items", LIST.parameterizedBy(itemClass))
                    .addModifiers(KModifier.OVERRIDE)
                    .initializer(buildCodeBlock {
                        add("listOf(\n")
                        items.forEach { item ->
                            add("    %T(tab = %S, name = %S, content = { %M() }),\n", 
                                itemClass, item.tab, item.name, MemberName(item.packageName, item.wrapperName))
                        }
                        add(")")
                    })
                    .build())
                .build())
            .build()
            
        fileSpec.writeTo(codeGenerator, Dependencies(true))
        
        // Register the provider via SPI
        registerSpi(packageName, className)
    }

    private fun registerSpi(packageName: String, className: String) {
        val serviceFile = "META-INF/services/com.simtop.billionbeers.catalog_annotations.CatalogProvider"
        codeGenerator.createNewFile(
            Dependencies(true),
            "",
            serviceFile,
            ""
        ).use { outputStream ->
            outputStream.write("$packageName.$className\n".toByteArray())
        }
    }

    data class CatalogItemInfo(val tab: String, val name: String, val packageName: String, val wrapperName: String)
    
    // Simple extension to capitalize for class names
    private fun String.capitalize() = this.replaceFirstChar { it.uppercase() }
}
