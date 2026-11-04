package pl.luczka.agenttoolkit.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration

/** Parsing the project is the slow part, so every test class shares one scope per source-set kind. */
internal object Scopes {
    val production by lazy { Konsist.scopeFromProduction() }
    val test by lazy { Konsist.scopeFromTest() }
}

/** Simple names this file imports from any `domain.model` package. */
internal fun KoFileDeclaration.importedDomainModelNames(): Set<String> =
    imports
        .map { it.name }
        .filter { it.contains(".domain.model.") }
        .map { it.substringAfterLast('.') }
        .toSet()

/** Whether [text] uses any of [names] as a whole word. */
internal fun mentionsAny(
    text: String,
    names: Set<String>,
): Boolean = names.any { Regex("""\b${Regex.escape(it)}\b""").containsMatchIn(text) }
