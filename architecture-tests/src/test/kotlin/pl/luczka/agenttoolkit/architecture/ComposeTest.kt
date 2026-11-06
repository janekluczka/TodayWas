package pl.luczka.agenttoolkit.architecture

import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
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

    @Test
    fun `should take dimensions from a Ds token when a dp value is used outside tokens`() {
        // Arrange
        val withRawDp = Scopes.production.files
            .filterNot { tokensPackage.containsMatchIn(it.packagee?.name.orEmpty()) }
            .associateWith { it.rawDpLiterals() }
            .filterValues { it.isNotEmpty() }

        // Assert
        withRawDp.keys.toList().assertEmpty(
            additionalMessage = "Take every dimension from a Ds token object in designsystem/tokens/ " +
                "(DsSpacing, DsRadius, DsSize, …) or MaterialTheme.shapes; 0.dp is fine except as an elevation. " +
                "Found: " + withRawDp.values.flatten().joinToString(),
        )
    }

    @Test
    fun `should take text styles from the theme typography when text is styled outside theme`() {
        // Arrange
        val withOwnStyle = Scopes.production.files
            .filterNot { themePackage.containsMatchIn(it.packagee?.name.orEmpty()) }
            .associateWith { file -> textStyling.findAll(file.codeOutsidePreviews()).map { it.value.trim() }.toList() }
            .filterValues { it.isNotEmpty() }

        // Assert
        withOwnStyle.keys.toList().assertEmpty(
            additionalMessage = "Take text styles from MaterialTheme.typography (built in designsystem/theme/); " +
                "a style the scale lacks gets a Typography extension there. Found: " +
                withOwnStyle.entries.joinToString { (file, found) -> "${file.nameWithExtension}: $found" },
        )
    }
}

// Comments, strings and char literals in one alternation, so whichever starts first wins ("//" inside a string stays
// part of the string).
private val commentOrString = Regex(
    "\"\"\"[\\s\\S]*?\"\"\"|\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\\\n])+'|//[^\\n]*|/\\*[\\s\\S]*?\\*/",
)
private val dpLiteral = Regex("""(?<![\w.])(\d+(?:\.\d+)?)\s*\.\s*dp\b|\bDp\(\s*(\d+(?:\.\d+)?)f?\s*\)""")
private val elevationAssignment = Regex("""[Ee]levation\s*=\s*$""")
private val tokensPackage = Regex("""(^|\.)tokens(\.|$)""")
private val themePackage = Regex("""(^|\.)theme(\.|$)""")

// A sp literal, a TextStyle/TextUnit built by hand, or a font attribute set as an argument.
private val textStyling = Regex(
    """(?<![\w.])\d+(?:\.\d+)?\s*\.\s*sp\b|\b(?:TextStyle|TextUnit)\(|""" +
        """\b(?:fontSize|fontWeight|fontFamily|fontStyle|letterSpacing|lineHeight)\s*=(?!=)""",
)

/** This file's text with preview functions, comments and string literals blanked out. */
private fun KoFileDeclaration.codeOutsidePreviews(): String {
    val previews = functions(includeNested = true, includeLocal = false)
        .filter { function -> function.annotations.any { it.name.startsWith("Preview") } }
    return previews
        .fold(text) { remaining, preview -> remaining.replace(preview.text, "") }
        .replace(commentOrString, " ")
}

/** `N.dp` and `Dp(N)` literals in this file's code outside previews; `0.dp` counts only as an elevation. */
private fun KoFileDeclaration.rawDpLiterals(): List<String> {
    val code = codeOutsidePreviews()
    return dpLiteral
        .findAll(code)
        .filter { match ->
            val value = match.groupValues[1].ifEmpty { match.groupValues[2] }.toDouble()
            value != 0.0 || elevationAssignment.containsMatchIn(code.substring(0, match.range.first))
        }.map { "$nameWithExtension: ${it.value}" }
        .toList()
}
