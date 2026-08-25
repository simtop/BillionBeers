package com.simtop.billionbeers.snapshot_processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.annotated
import com.squareup.kotlinpoet.ksp.writeTo

private const val UI_MODE_NIGHT_MASK = 0x30
private const val UI_MODE_NIGHT_YES = 0x20

private data class InventoryTypes(
  val snapshotClass: ClassName,
  val previewConfigurationClass: ClassName,
  val matrixClass: ClassName,
)

private data class FunctionContext(
  val functionMember: MemberName,
  val previewConfigurationClass: ClassName,
)

@Suppress("unused")
class SnapshotProcessorProvider : SymbolProcessorProvider {
  override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
    SnapshotProcessor(environment.codeGenerator, environment.logger, environment.options)
}

@Suppress("TooManyFunctions", "SpreadOperator")
private class SnapshotProcessor(
  private val codeGenerator: CodeGenerator,
  private val logger: KSPLogger,
  private val options: Map<String, String>,
) : SymbolProcessor {
  private var generated = false

  override fun process(resolver: Resolver): List<com.google.devtools.ksp.symbol.KSAnnotated> {
    if (generated) return emptyList()

    val sourceFiles = resolver.getAllFiles().toList()
    val functions =
      sourceFiles
        .flatMap { file -> file.declarations.filterIsInstance<KSFunctionDeclaration>() }
        .filter(::belongsToModule)
        .filter(::hasPreview)
        .distinctBy { it.qualifiedName?.asString() }
        .sortedBy { it.qualifiedName?.asString().orEmpty() }

    generateInventory(functions, sourceFiles)
    generated = true
    return emptyList()
  }

  private fun generateInventory(functions: List<KSFunctionDeclaration>, sourceFiles: List<KSFile>) {
    val namespace = options[MODULE_NAMESPACE_OPTION].orEmpty().ifBlank { DEFAULT_NAMESPACE }
    val snapshotPackage = "com.simtop.billionbeers.snapshot_testing"
    val types =
      InventoryTypes(
        snapshotClass = ClassName(snapshotPackage, "Snapshot"),
        previewConfigurationClass = ClassName(snapshotPackage, "PreviewConfiguration"),
        matrixClass = ClassName(snapshotPackage, "AccessibilityMatrix"),
      )
    val composableAnnotation = ClassName("androidx.compose.runtime", "Composable")
    val composableLambda =
      LambdaTypeName.get(returnType = UNIT)
        .annotated(AnnotationSpec.builder(composableAnnotation).build())
    val initializer = CodeBlock.builder().add("buildList {\n")
    initializer.add("  val usedNames = mutableSetOf<String>()\n")
    functions.forEach { function -> appendFunction(initializer, function, types) }
    initializer.add("}\n")

    val inventoryClass =
      TypeSpec.objectBuilder(INVENTORY_CLASS_NAME)
        .addProperty(
          PropertySpec.builder("snapshots", LIST.parameterizedBy(types.snapshotClass))
            .initializer(initializer.build())
            .build()
        )
        .addFunction(
          FunSpec.builder("sanitize")
            .addModifiers(KModifier.PRIVATE)
            .addParameter("value", STRING)
            .returns(STRING)
            .addStatement("return value.replace(Regex(%S), %S)", "[^A-Za-z0-9_.-]", "_")
            .build()
        )
        .addFunction(
          addSnapshotFunction(
            types.snapshotClass,
            types.previewConfigurationClass,
            composableLambda,
          )
        )
        .build()

    val fileSpec = FileSpec.builder(namespace, INVENTORY_CLASS_NAME).addType(inventoryClass).build()
    val dependencies = Dependencies(aggregating = true, *sourceFiles.toTypedArray())
    fileSpec.writeTo(codeGenerator, dependencies)
  }

  private fun appendFunction(
    initializer: CodeBlock.Builder,
    function: KSFunctionDeclaration,
    types: InventoryTypes,
  ) {
    val functionName = function.simpleName.asString()
    val context =
      FunctionContext(
        functionMember = MemberName(function.packageName.asString(), functionName),
        previewConfigurationClass = types.previewConfigurationClass,
      )
    validateFunction(function)

    if (isAccessibilityMatrixPreview(function)) {
      appendMatrixSnapshots(initializer, function, functionName, context, types)
      return
    }

    val previews = expandPreviews(function)
    if (previews.isEmpty()) {
      logger.error("No androidx.compose.ui.tooling.preview.Preview metadata found", function)
    }
    val parameter = function.parameters.firstOrNull { hasPreviewParameter(it) }
    if (parameter != null) {
      addParameterizedFunction(initializer, function, parameter, previews, context)
    } else {
      previews.forEach { preview ->
        addOrdinaryFunction(initializer, function, preview, context)
      }
    }
  }

  private fun appendMatrixSnapshots(
    initializer: CodeBlock.Builder,
    function: KSFunctionDeclaration,
    functionName: String,
    context: FunctionContext,
    types: InventoryTypes,
  ) {
    initializer.add("  %T.configurations.forEach { configuration ->\n", types.matrixClass)
    initializer.add("    val name = %S + \"_\" + configuration.name\n", functionName)
    initializer.add(
      "    check(usedNames.add(name)) { \"Duplicate screenshot ID: \" + name + \" from \" + %S }\n",
      function.qualifiedName?.asString().orEmpty(),
    )
    initializer.add(
      "    add(%T(name, { %M() }, configuration))\n",
      types.snapshotClass,
      context.functionMember,
    )
    initializer.add("  }\n")
  }

  private fun addOrdinaryFunction(
    initializer: CodeBlock.Builder,
    function: KSFunctionDeclaration,
    preview: PreviewSpec,
    context: FunctionContext,
  ) {
    initializer.add(
      "  addSnapshot(this, usedNames, %S, %L) { %M() }\n",
      function.simpleName.asString(),
      preview.configurationCode(context.previewConfigurationClass),
      context.functionMember,
    )
  }

  private fun addParameterizedFunction(
    initializer: CodeBlock.Builder,
    function: KSFunctionDeclaration,
    parameter: KSValueParameter,
    previews: List<PreviewSpec>,
    context: FunctionContext,
  ) {
    val annotation =
      parameter.annotations.first {
        annotationQualifiedName(it) == PREVIEW_PARAMETER_ANNOTATION
      }
    val providerType =
      annotation.arguments.firstOrNull { it.name?.asString() == "provider" }?.value as? KSType
        ?: annotation.arguments.firstOrNull()?.value as? KSType
        ?: error("PreviewParameter provider is missing on ${function.qualifiedName?.asString()}")
    val providerName =
      providerType.declaration.qualifiedName?.asString()
        ?: error(
          "PreviewParameter provider has no qualified name on ${function.qualifiedName?.asString()}"
        )
    val limit =
      annotation.arguments.firstOrNull { it.name?.asString() == "limit" }?.value.asIntOrNull()
        ?: PREVIEW_PARAMETER_UNLIMITED
    val providerClass = ClassName.bestGuess(providerName)

    initializer.add("  %T().let { provider ->\n", providerClass)
    if (limit > 0 && limit != PREVIEW_PARAMETER_UNLIMITED) {
      initializer.add("    provider.values.take(%L).forEachIndexed { index, value ->\n", limit)
    } else {
      initializer.add("    provider.values.forEachIndexed { index, value ->\n")
    }
    initializer.add(
      "      val displayName = sanitize(provider.getDisplayName(index).orEmpty().ifBlank { index.toString() })\n"
    )
    previews.forEach { preview ->
      initializer.add(
        "      addSnapshot(this, usedNames, %S + \"_\" + displayName, %L) { %M(value) }\n",
        function.simpleName.asString(),
        preview.configurationCode(context.previewConfigurationClass),
        context.functionMember,
      )
    }
    initializer.add("    }\n")
    initializer.add("  }\n")
  }

  private fun addSnapshotFunction(
    snapshotClass: ClassName,
    previewConfigurationClass: ClassName,
    composableLambda: TypeName,
  ): FunSpec =
    FunSpec.builder("addSnapshot")
      .addModifiers(KModifier.PRIVATE)
      .addParameter(
        "snapshots",
        ClassName("kotlin.collections", "MutableList").parameterizedBy(snapshotClass),
      )
      .addParameter(
        "usedNames",
        ClassName("kotlin.collections", "MutableSet").parameterizedBy(STRING),
      )
      .addParameter("baseName", STRING)
      .addParameter("configuration", previewConfigurationClass)
      .addParameter(ParameterSpec.builder("content", composableLambda).build())
      .addStatement("var name = baseName")
      .addStatement("if (!usedNames.add(name)) {")
      .addStatement("  val suffix = buildList {")
      .addStatement("    if (configuration.theme == %S) add(%S)", "dark", "dark")
      .addStatement(
        "    if (configuration.fontScale != 1f) add(%S + (configuration.fontScale * 100).toInt())",
        "font",
      )
      .addStatement(
        "    if (configuration.locale != %S) add(configuration.locale.replace('-', '_'))",
        "en",
      )
      .addStatement("    if (configuration.device.isNotBlank()) add(%S)", "device")
      .addStatement("  }.ifEmpty { listOf(%S) }.joinToString(%S)", "variant", "_")
      .addStatement("  name = baseName + %S + suffix", "_")
      .addStatement("  check(usedNames.add(name)) { %S + name }", "Duplicate screenshot ID: ")
      .addStatement("}")
      .addStatement(
        "snapshots += %T(name, content, configuration.copy(name = name))",
        snapshotClass,
      )
      .build()

  private fun validateFunction(function: KSFunctionDeclaration) {
    if (Modifier.PRIVATE in function.modifiers) {
      logger.error(
        "KSP screenshot discovery cannot invoke private preview ${function.qualifiedName?.asString()}; " +
          "make it internal or public",
        function,
      )
    }
    if (function.extensionReceiver != null) {
      logger.error(
        "Extension preview functions are not supported: ${function.qualifiedName?.asString()}",
        function,
      )
    }
    val previewParameters = function.parameters.filter(::hasPreviewParameter)
    if (previewParameters.size > 1) {
      logger.error(
        "Only one @PreviewParameter is supported: ${function.qualifiedName?.asString()}",
        function,
      )
    }
    if (function.parameters.any { !hasPreviewParameter(it) }) {
      logger.error(
        "Every preview parameter must be annotated with @PreviewParameter: ${function.qualifiedName?.asString()}",
        function,
      )
    }
  }

  private fun hasPreview(function: KSFunctionDeclaration): Boolean =
    isAccessibilityMatrixPreview(function) || expandPreviews(function).isNotEmpty()

  private fun belongsToModule(function: KSFunctionDeclaration): Boolean {
    val namespace = options[MODULE_NAMESPACE_OPTION].orEmpty()
    val qualifiedName = function.qualifiedName?.asString().orEmpty()
    return namespace.isBlank() ||
      qualifiedName == namespace ||
      qualifiedName.startsWith("$namespace.")
  }

  private fun isAccessibilityMatrixPreview(function: KSFunctionDeclaration): Boolean =
    function.annotations.any { annotationQualifiedName(it) == ACCESSIBILITY_MATRIX_PREVIEW }

  private fun expandPreviews(function: KSFunctionDeclaration): List<PreviewSpec> {
    val result = mutableListOf<PreviewSpec>()
    function.annotations.forEach { annotation ->
      visitAnnotation(annotation, mutableSetOf(), result)
    }
    return result.distinct()
  }

  private fun visitAnnotation(
    annotation: KSAnnotation,
    visited: MutableSet<String>,
    result: MutableList<PreviewSpec>,
  ) {
    val qualifiedName = annotationQualifiedName(annotation)
    if (qualifiedName == ACCESSIBILITY_MATRIX_PREVIEW || !isVisitable(qualifiedName, visited))
      return
    val name = checkNotNull(qualifiedName)
    try {
      visitAnnotationDeclaration(annotation, name, visited, result)
    } finally {
      visited.remove(name)
    }
  }

  private fun visitAnnotationDeclaration(
    annotation: KSAnnotation,
    qualifiedName: String,
    visited: MutableSet<String>,
    result: MutableList<PreviewSpec>,
  ) {
    if (qualifiedName == PREVIEW_ANNOTATION) {
      result += PreviewSpec.from(annotation)
    } else {
      val declaration = annotation.annotationType.resolve().declaration as? KSClassDeclaration
      declaration?.annotations?.forEach { nested ->
        visitAnnotation(nested, visited, result)
      }
    }
  }

  private fun hasPreviewParameter(parameter: KSValueParameter): Boolean =
    parameter.annotations.any {
      annotationQualifiedName(it) == PREVIEW_PARAMETER_ANNOTATION
    }

  private fun annotationQualifiedName(annotation: KSAnnotation): String? =
    annotation.annotationType.resolve().declaration.qualifiedName?.asString()

  private companion object {
    const val PREVIEW_ANNOTATION = "androidx.compose.ui.tooling.preview.Preview"
    const val PREVIEW_PARAMETER_ANNOTATION = "androidx.compose.ui.tooling.preview.PreviewParameter"
    const val ACCESSIBILITY_MATRIX_PREVIEW =
      "com.simtop.billionbeers.core.designsystem.component.AccessibilityMatrixPreview"
    const val MODULE_NAMESPACE_OPTION = "billionbeers.screenshot.namespace"
    const val DEFAULT_NAMESPACE = "com.simtop.billionbeers"
    const val INVENTORY_CLASS_NAME = "GeneratedPreviewInventory"
    const val PREVIEW_PARAMETER_UNLIMITED = Int.MAX_VALUE
  }
}

private fun isVisitable(qualifiedName: String?, visited: MutableSet<String>): Boolean =
  qualifiedName != null && visited.add(qualifiedName)

private data class PreviewSpec(
  val name: String,
  val group: String,
  val fontScale: Float,
  val locale: String,
  val uiMode: Int,
  val device: String,
  val widthDp: Int,
  val heightDp: Int,
) {
  val theme: String
    get() = if (uiMode and UI_MODE_NIGHT_MASK == UI_MODE_NIGHT_YES) "dark" else "light"

  fun configurationCode(type: ClassName): CodeBlock =
    CodeBlock.builder()
      .add("%T(\n", type)
      .add("  name = %S,\n", "")
      .add("  theme = %S,\n", theme)
      .add("  fontScale = %Lf,\n", fontScale.toDouble())
      .add("  locale = %S.ifBlank { %S },\n", locale, "en")
      .add("  layoutDirection = %S,\n", "ltr")
      .add(
        "  width = if (%L >= 600 || %S.contains(%S, ignoreCase = true)) %S else %S,\n",
        widthDp,
        device,
        "TABLET",
        "expanded",
        "compact",
      )
      .add("  previewName = %S,\n", name)
      .add("  previewGroup = %S,\n", group)
      .add("  widthDp = %L,\n", widthDp)
      .add("  heightDp = %L,\n", heightDp)
      .add("  uiMode = %L,\n", uiMode)
      .add("  device = %S,\n", device)
      .add(")")
      .build()

  companion object {
    fun from(annotation: KSAnnotation): PreviewSpec =
      PreviewSpec(
        name = annotation.stringArgument("name", ""),
        group = annotation.stringArgument("group", ""),
        fontScale = annotation.floatArgument("fontScale", 1f),
        locale = annotation.stringArgument("locale", ""),
        uiMode = annotation.intArgument("uiMode", 0),
        device = annotation.stringArgument("device", ""),
        widthDp = annotation.intArgument("widthDp", -1),
        heightDp = annotation.intArgument("heightDp", -1),
      )
  }
}

private fun KSAnnotation.stringArgument(name: String, default: String): String =
  arguments.firstOrNull { it.name?.asString() == name }?.value?.toString() ?: default

private fun KSAnnotation.floatArgument(name: String, default: Float): Float =
  arguments.firstOrNull { it.name?.asString() == name }?.value.asFloatOrNull() ?: default

private fun KSAnnotation.intArgument(name: String, default: Int): Int =
  arguments.firstOrNull { it.name?.asString() == name }?.value.asIntOrNull() ?: default

private fun Any?.asIntOrNull(): Int? =
  when (this) {
    is Int -> this
    is Long -> toInt()
    is Short -> toInt()
    is Byte -> toInt()
    is Number -> toInt()
    is String -> toIntOrNull()
    else -> null
  }

private fun Any?.asFloatOrNull(): Float? =
  when (this) {
    is Float -> this
    is Double -> toFloat()
    is Number -> toFloat()
    is String -> toFloatOrNull()
    else -> null
  }
