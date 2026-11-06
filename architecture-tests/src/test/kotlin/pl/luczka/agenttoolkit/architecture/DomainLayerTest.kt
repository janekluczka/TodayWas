package pl.luczka.agenttoolkit.architecture

import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.verify.assertEmpty
import org.junit.Test

/** House style, "Domain layer". */
class DomainLayerTest {

    private val useCases = Scopes.production.classes().withNameEndingWith("UseCase")

    @Test
    fun `should live in domain usecase when a class is a use case`() {
        // Arrange
        val misplaced = useCases.filterNot { it.resideInPackage("..domain..usecase..") }

        // Assert
        misplaced.assertEmpty()
    }

    @Test
    fun `should expose only operator invoke when a use case is declared`() {
        // Arrange
        val wrongSurface = useCases.filterNot { useCase ->
            val public = useCase
                .functions(
                    includeNested = false,
                    includeLocal = false,
                ).filter { it.hasPublicOrDefaultModifier }
            public.size == 1 && public.single().name == "invoke" && public.single().hasOperatorModifier
        }

        // Assert
        wrongSurface.assertEmpty(additionalMessage = "A use case has one public `operator fun invoke`.")
    }

    @Test
    fun `should not inject another use case when a use case is declared`() {
        // Arrange
        val chained = useCases.filter { useCase ->
            useCase.primaryConstructor
                ?.parameters
                .orEmpty()
                .any {
                    it.type.name
                        .removeSuffix("?")
                        .endsWith("UseCase")
                }
        }

        // Assert
        chained.assertEmpty(additionalMessage = "Share logic through a combinator use case built on the repository.")
    }
}
