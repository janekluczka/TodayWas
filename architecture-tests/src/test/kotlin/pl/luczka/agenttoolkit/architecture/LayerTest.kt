package pl.luczka.agenttoolkit.architecture

import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.ext.list.withPackage
import com.lemonappdev.konsist.api.verify.assertEmpty
import org.junit.Test

/**
 * House style, "Package layout" and "Modules": which layer may import which. A layer is a package
 * segment, so the same rule covers `<base>.ui` in one module and `<base>.feature.x` or
 * `<base>.core.database` in a modular project.
 */
class LayerTest {

    private val ui = setOf("ui", "feature")
    private val domain = setOf("domain")
    private val data = setOf("data", "database", "network")

    /** Packages declared in the project, so a library's `androidx.compose.ui` import isn't read as the UI layer. */
    private val projectPackages by lazy {
        Scopes.production.files
            .mapNotNull { it.packagee?.name }
            .toSet()
    }

    @Test
    fun `should not import ui or data when a file is in the domain layer`() {
        // Act
        val offending = filesIn(domain).filter { it.importsLayer(ui + data) }

        // Assert
        offending.assertEmpty(additionalMessage = "The domain layer imports neither UI nor data.")
    }

    @Test
    fun `should not import data when a file is in the ui layer`() {
        // Act
        val offending = filesIn(ui).filter { it.importsLayer(data) }

        // Assert
        offending.assertEmpty(additionalMessage = "UI reaches data through use cases in the domain layer.")
    }

    @Test
    fun `should not import ui when a file is in the data layer`() {
        // Act
        val offending = filesIn(data).filter { it.importsLayer(ui) }

        // Assert
        offending.assertEmpty(additionalMessage = "The data layer never imports UI types.")
    }

    @Test
    fun `should not import the Android framework when a file is in the domain layer`() {
        // Arrange
        val frameworkImports = Scopes.production.files
            .withPackage("..domain..")
            .flatMap { it.imports }
            .filter { it.name.startsWith("android.") || it.name.startsWith("androidx.") }

        // Assert
        frameworkImports.assertEmpty()
    }

    /** Files whose package names only segments of [layer], e.g. `<base>.data.journal.local` for data. */
    private fun filesIn(layer: Set<String>) =
        Scopes.production.files.filter { file ->
            val layers = file.packagee?.name.layers()
            layers.isNotEmpty() && layer.containsAll(layers)
        }

    private fun String?.layers(): Set<String> =
        this
            ?.split('.')
            .orEmpty()
            .filter { it in ui + domain + data }
            .toSet()

    /** The longest project package an import starts with, so a nested-class import still resolves. */
    private fun KoFileDeclaration.importsLayer(layer: Set<String>): Boolean =
        imports.any { import ->
            val importedPackage = projectPackages.filter { import.name.startsWith("$it.") }.maxByOrNull { it.length }
            importedPackage.layers().any { it in layer }
        }
}
