package pl.luczka.agenttoolkit.architecture

import com.lemonappdev.konsist.api.ext.list.withAnnotationNamed
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.verify.assertEmpty
import org.junit.Test

/** House style, "Package layout": where DI modules and repositories live. */
class PackageLayoutTest {

    @Test
    fun `should live in di when a declaration is a Hilt module`() {
        // Arrange
        val misplaced = Scopes.production
            .classesAndInterfacesAndObjects()
            .withAnnotationNamed("Module", "dagger.Module")
            .filterNot { it.resideInPackage("..di..") }

        // Assert
        misplaced.assertEmpty()
    }

    @Test
    fun `should hold only implementations when a file is in data repository`() {
        // Arrange
        val interfaces = Scopes.production.interfaces().filter { it.resideInPackage("..data.repository..") }

        // Assert
        interfaces.assertEmpty(additionalMessage = "Repository interfaces live in domain.repository.")
    }

    @Test
    fun `should live in domain repository when an interface is a repository`() {
        // Arrange
        val misplaced = Scopes.production
            .interfaces()
            .withNameEndingWith("Repository")
            .filterNot { it.resideInPackage("..domain.repository..") }

        // Assert
        misplaced.assertEmpty()
    }
}
