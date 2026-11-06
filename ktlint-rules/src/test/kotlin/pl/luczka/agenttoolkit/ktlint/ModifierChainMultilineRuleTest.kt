package pl.luczka.agenttoolkit.ktlint

import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import com.pinterest.ktlint.test.LintViolation
import org.junit.Test

class ModifierChainMultilineRuleTest {

    private val modifierChainMultilineRuleAssertThat = assertThatRule { ModifierChainMultilineRule() }

    @Test
    fun `should put each call on its own line when a Modifier chain has two calls`() {
        // Arrange
        val code =
            """
            fun foo() {
                val modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            }
            """.trimIndent()
        val formattedCode =
            """
            fun foo() {
                val modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            }
            """.trimIndent()

        // Act
        val result = modifierChainMultilineRuleAssertThat(code)

        // Assert
        result
            .hasLintViolations(
                LintViolation(2, 28, "Modifier chain call should be on a separate line"),
                LintViolation(2, 43, "Modifier chain call should be on a separate line"),
            ).isFormattedAs(formattedCode)
    }

    @Test
    fun `should wrap a chain on a modifier parameter when it has two calls`() {
        // Arrange
        val code =
            """
            fun foo(modifier: Modifier) {
                val boxModifier = modifier.fillMaxSize().background(color)
            }
            """.trimIndent()
        val formattedCode =
            """
            fun foo(modifier: Modifier) {
                val boxModifier = modifier
                    .fillMaxSize()
                    .background(color)
            }
            """.trimIndent()

        // Act
        val result = modifierChainMultilineRuleAssertThat(code)

        // Assert
        result
            .hasLintViolations(
                LintViolation(2, 31, "Modifier chain call should be on a separate line"),
                LintViolation(2, 45, "Modifier chain call should be on a separate line"),
            ).isFormattedAs(formattedCode)
    }

    @Test
    fun `should leave a Modifier chain on one line when it has a single call`() {
        // Arrange
        val code =
            """
            fun foo() {
                val modifier = Modifier.fillMaxWidth()
            }
            """.trimIndent()

        // Act
        val result = modifierChainMultilineRuleAssertThat(code)

        // Assert
        result.hasNoLintViolations()
    }

    @Test
    fun `should leave a chain alone when its receiver is not a modifier`() {
        // Arrange
        val code =
            """
            fun foo() {
                val names = list.map { it.name }.filter { it.isNotBlank() }
            }
            """.trimIndent()

        // Act
        val result = modifierChainMultilineRuleAssertThat(code)

        // Assert
        result.hasNoLintViolations()
    }

    @Test
    fun `should keep a call on the closing line of a multiline call when chain-method-continuation puts it there`() {
        // Arrange
        val code =
            """
            fun foo() {
                val modifier = Modifier
                    .selectable(
                        selected = selected,
                        onClick = onClick,
                    ).padding(vertical = 8.dp)
            }
            """.trimIndent()

        // Act
        val result = modifierChainMultilineRuleAssertThat(code)

        // Assert
        result.hasNoLintViolations()
    }

    @Test
    fun `should report nothing when the chain is already one call per line`() {
        // Arrange
        val code =
            """
            fun foo() {
                val modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            }
            """.trimIndent()

        // Act
        val result = modifierChainMultilineRuleAssertThat(code)

        // Assert
        result.hasNoLintViolations()
    }
}
