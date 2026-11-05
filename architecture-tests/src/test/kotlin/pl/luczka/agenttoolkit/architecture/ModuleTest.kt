package pl.luczka.agenttoolkit.architecture

import com.lemonappdev.konsist.api.declaration.KoFunctionDeclaration
import com.lemonappdev.konsist.api.ext.list.withAnnotationNamed
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.provider.KoNameProvider
import com.lemonappdev.konsist.api.provider.modifier.KoVisibilityModifierProvider
import com.lemonappdev.konsist.api.verify.assertEmpty
import org.junit.Test

/** House style, "Modules": which module may depend on which, and what a feature's `:api` holds. */
class ModuleTest {

    private val byPath by lazy { Modules.all.associateBy { it.path } }

    private fun GradleModule.dependencyModules() = projectDependencies.mapNotNull { byPath[it] }

    @Test
    fun `should not depend on a feature impl when a module is not the app`() {
        // Arrange
        val offending = Modules.all
            .filterNot { it.isApp }
            .flatMap { module ->
                module.dependencyModules().filter { it.isFeatureImpl }.map { "${module.path} -> ${it.path}" }
            }

        // Assert
        offending.assertNone("Only :app depends on a feature's :impl; other modules use its :api.")
    }

    @Test
    fun `should reach another feature only through its api when a feature module declares dependencies`() {
        // Arrange
        val offending = Modules.all.filter { it.isFeature }.flatMap { module ->
            module
                .dependencyModules()
                .filter { it.isFeature && it.featureName != module.featureName && !it.isFeatureApi }
                .map { "${module.path} -> ${it.path}" }
        }

        // Assert
        offending.assertNone("A feature depends on another feature's :api only.")
    }

    @Test
    fun `should not depend on another feature when a feature api declares dependencies`() {
        // Arrange
        val offending = Modules.all.filter { it.isFeatureApi }.flatMap { module ->
            module.dependencyModules().filter { it.isFeature }.map { "${module.path} -> ${it.path}" }
        }

        // Assert
        offending.assertNone("A feature's :api holds its own keys and depends on core modules only.")
    }

    @Test
    fun `should not depend on a feature or the app when a core module declares dependencies`() {
        // Arrange
        val offending = Modules.all.filter { it.isCore }.flatMap { module ->
            module.dependencyModules().filter { it.isFeature || it.isApp }.map { "${module.path} -> ${it.path}" }
        }

        // Assert
        offending.assertNone("Core modules never depend on features or the app.")
    }

    @Test
    fun `should not depend on another module when the module is the design system`() {
        // Arrange
        val offending = Modules.all
            .filter { it.path == ":core:designsystem" }
            .flatMap { module -> module.dependencyModules().map { "${module.path} -> ${it.path}" } }

        // Assert
        offending.assertNone(":core:designsystem is the bottom of the graph.")
    }

    @Test
    fun `should hold no UI or ViewModel when a file is in a feature api module`() {
        // Arrange
        val apiModules = Modules.all.filter { it.isFeatureApi }.map { it.konsistName }
        val apiFiles = Scopes.production.files.filter { file -> file.modulePath in apiModules }

        // Act
        val composables = apiFiles.flatMap { it.functions() }.withAnnotationNamed("Composable")
        val viewModels = apiFiles.flatMap { it.classes() }.withNameEndingWith("ViewModel")

        // Assert
        (composables + viewModels).assertEmpty(
            additionalMessage = "A feature's :api holds navigation keys and Navigator functions; UI goes in :impl.",
        )
    }

    @Test
    fun `should be internal or private when a top-level declaration in a feature impl is not its entry builder`() {
        // Arrange
        val implModules = Modules.all.filter { it.isFeatureImpl }.map { it.konsistName }
        val implFiles = Scopes.production.files.filter { file -> file.modulePath in implModules }

        // Act
        val exposed = implFiles.flatMap { file ->
            file
                .declarations(includeNested = false, includeLocal = false)
                .filterIsInstance<KoVisibilityModifierProvider>()
                .filter { it.hasPublicOrDefaultModifier && !it.isEntryBuilder() }
                .map { "${file.path}: ${(it as? KoNameProvider)?.name}" }
        }

        // Assert
        exposed.assertNone(
            "Everything in a feature's :impl is internal or private, except its EntryProviderScope entry builder.",
        )
    }

    @Test
    fun `should live under its module's package when a file is in a project module`() {
        // Act
        val misplaced = (Scopes.production.files + Scopes.test.files).filter { file ->
            val prefix = Modules.expectedPackages[file.modulePath] ?: return@filter false
            val packageName = file.packagee?.name.orEmpty()
            packageName != prefix && !packageName.startsWith("$prefix.")
        }

        // Assert
        misplaced.assertEmpty(additionalMessage = "A module's package (and namespace) is <base>.<module path>.")
    }

    @Test
    fun `should match its package when an Android module declares a namespace`() {
        // Arrange
        val offending = Modules.all.mapNotNull { module ->
            val expected = Modules.expectedPackages[module.konsistName]
            if (module.namespace == null || expected == null || module.namespace == expected) {
                null
            } else {
                "${module.path}: ${module.namespace}, expected $expected"
            }
        }

        // Assert
        offending.assertNone("A module's namespace is <base>.<module path>.")
    }

    private fun KoVisibilityModifierProvider.isEntryBuilder() =
        this is KoFunctionDeclaration && receiverType?.name?.startsWith("EntryProviderScope") == true

    private fun List<String>.assertNone(message: String) {
        check(isEmpty()) { "$message\n" + joinToString("\n") }
    }
}
