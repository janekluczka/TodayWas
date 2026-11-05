package pl.luczka.agenttoolkit.architecture

import com.lemonappdev.konsist.api.declaration.KoFunctionDeclaration
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.ext.list.withNameMatching
import com.lemonappdev.konsist.api.verify.assertEmpty
import org.junit.Test

/** House style, "UI layer" and "Mapping". */
class UiLayerTest {

    private val viewModels = Scopes.production.classes().withNameEndingWith("ViewModel")

    @Test
    fun `should not reference a domain model when a public UI type is declared`() {
        // Arrange
        val leaking = Scopes.production
            .classesAndInterfacesAndObjects(includeNested = false)
            .withNameMatching(Regex(".+(UiState|Ui|Intent|Navigation)"))
            .filter { (it.resideInPackage("..ui..") || it.resideInPackage("..feature..")) && !it.hasPrivateModifier }
            .filter { mentionsAny(it.text, it.containingFile.importedDomainModelNames()) }

        // Assert
        leaking.assertEmpty(additionalMessage = "Map domain models to a ui.model type in a *Mapper.kt file first.")
    }

    @Test
    fun `should not hold an event stream when a ViewModel is declared`() {
        // Arrange
        val eventStream = Regex("""\b(Channel|SharedFlow|MutableSharedFlow)\b""")

        // Act
        val streaming = viewModels.filter { eventStream.containsMatchIn(it.text) }

        // Assert
        streaming.assertEmpty(additionalMessage = "Model one-off effects as state (pendingNavigation, userMessage).")
    }

    @Test
    fun `should not inject a repository when a ViewModel is declared`() {
        // Arrange
        val withRepository = viewModels.filter { viewModel ->
            viewModel.primaryConstructor
                ?.parameters
                .orEmpty()
                .any {
                    it.type.name
                        .removeSuffix("?")
                        .endsWith("Repository")
                }
        }

        // Assert
        withRepository.assertEmpty(additionalMessage = "ViewModels reach data through use cases.")
    }

    @Test
    fun `should only route to private handlers when onIntent dispatches an intent`() {
        // Arrange
        val routers = viewModels.flatMap { viewModel ->
            viewModel.functions(includeNested = false, includeLocal = false).filter { it.name == "onIntent" }
        }

        // Act
        val impure = routers.filterNot { it.isPureRouter() }

        // Assert
        impure.assertEmpty(
            additionalMessage = "Each `when` branch in onIntent only calls one private onXxx() handler.",
        )
    }

    @Test
    fun `should not declare a mapping function when the file is a ViewModel or a repository`() {
        // Arrange
        // Named for a layer's model type; `toDisabledActions()`-style derivations inside one layer are fine.
        val mapping = Regex("to\\w*(UiState|Ui|Domain|Entity|Dto|Model)")

        // Act
        val inlineMappers = Scopes.production.files
            .filter {
                it.nameWithExtension.endsWith(
                    "ViewModel.kt",
                ) || it.nameWithExtension.endsWith("RepositoryImpl.kt")
            }.flatMap { it.functions(includeNested = true, includeLocal = true) }
            .filter { mapping.matches(it.name) }

        // Assert
        inlineMappers.assertEmpty(additionalMessage = "Move mapping into its own *Mapper.kt file.")
    }

    /**
     * The body is a single `when` (block or expression body) whose branches each start a call to an `onXxx` handler,
     * and nothing in it opens a block or lambda — ktlint's layout keeps this text shape stable.
     */
    private fun KoFunctionDeclaration.isPureRouter(): Boolean {
        val afterSignature = text.substringAfter("onIntent(").substringAfter(")")
        val beforeWhen = afterSignature.substringBefore("when").trim()
        val arrows = Regex("""->\s*(\S+)""").findAll(afterSignature).map { it.groupValues[1] }.toList()
        val braces = afterSignature.count { it == '{' }
        return (beforeWhen == "{" || beforeWhen == "=") &&
            braces <= (if (beforeWhen == "{") 2 else 1) &&
            arrows.isNotEmpty() &&
            arrows.all { Regex("""on[A-Z]\w*\(.*""").matches(it) }
    }
}
