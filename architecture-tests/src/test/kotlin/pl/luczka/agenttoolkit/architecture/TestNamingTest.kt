package pl.luczka.agenttoolkit.architecture

import com.lemonappdev.konsist.api.ext.list.withAnnotationNamed
import com.lemonappdev.konsist.api.verify.assertEmpty
import org.junit.Test

/** House style, "Tests". */
class TestNamingTest {

    @Test
    fun `should read should outcome when scenario when a function is a test`() {
        // Arrange
        val scheme = Regex("`?should .+ when .+`?")

        // Act
        val misnamed = Scopes.test
            .functions()
            .withAnnotationNamed("Test", "org.junit.Test")
            .filterNot { scheme.matches(it.name) }

        // Assert
        misnamed.assertEmpty(additionalMessage = "Name tests `should [expected outcome] when [scenario]`.")
    }
}
