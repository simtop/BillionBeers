import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

abstract class VerifySharedFeaturePairsTask : DefaultTask() {
  @get:Input abstract val androidFeatureNames: ListProperty<String>

  @get:Input abstract val sharedFeatureNames: ListProperty<String>

  @TaskAction
  fun verify() {
    val violations =
      ArchitecturePolicy.sharedFeaturePairViolations(
        androidFeatureNames.get().toSet(),
        sharedFeatureNames.get().toSet(),
      )
    check(violations.isEmpty()) {
      "Every Android feature must have a same-name shared feature, and vice versa:\n- " +
        violations.joinToString("\n- ")
    }
  }
}
