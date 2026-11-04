package pl.luczka.agenttoolkit.architecture

import com.lemonappdev.konsist.api.architecture.KoArchitectureCreator.assertArchitecture
import com.lemonappdev.konsist.api.architecture.Layer
import com.lemonappdev.konsist.api.ext.list.withPackage
import com.lemonappdev.konsist.api.verify.assertEmpty
import org.junit.Test

/** House style, "Package layout": which layer may import which. */
class LayerTest {

    @Test
    fun `should keep imports pointing inwards when a layer references another`() {
        // Arrange
        val ui = Layer("UI", "..ui..")
        val domain = Layer("Domain", "..domain..")
        val data = Layer("Data", "..data..")

        // Assert
        Scopes.production.assertArchitecture {
            domain.doesNotDependOn(ui, data)
            ui.doesNotDependOn(data)
        }
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
}
