package pl.luczka.agenttoolkit.architecture

import com.lemonappdev.konsist.api.ext.list.withAnnotationNamed
import com.lemonappdev.konsist.api.verify.assertEmpty
import org.junit.Test

/** House style, "Compose and the design system". */
class ComposeTest {

    @Test
    fun `should start with Ds when a public design-system component is declared`() {
        // Arrange
        val unprefixed = Scopes.production
            .functions()
            .withAnnotationNamed("Composable")
            .filter { it.resideInPackage("..designsystem.components..") && it.hasPublicOrDefaultModifier }
            .filterNot { it.name.startsWith("Ds") }

        // Assert
        unprefixed.assertEmpty()
    }

    @Test
    fun `should ship a light and dark preview when a file is a screen`() {
        // Arrange
        val withoutPreview = Scopes.production.files
            .filter { it.nameWithExtension.endsWith("Screen.kt") }
            .filterNot { file -> file.functions().any { it.hasAnnotationWithName("PreviewLightDark") } }

        // Assert
        withoutPreview.assertEmpty()
    }
}
