package pl.luczka.todaywas.ktlint

import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import com.pinterest.ktlint.test.LintViolation
import org.junit.Test
import pl.luczka.todaywas.ktlint.ArgumentListMultilineRule.Companion.FORCE_MULTILINE_ARGUMENT_COUNT_PROPERTY
import pl.luczka.todaywas.ktlint.ArgumentListMultilineRule.Companion.NAMED_ARGUMENTS_ONLY_PROPERTY

class ArgumentListMultilineRuleTest {

    private val argumentListMultilineRuleAssertThat = assertThatRule { ArgumentListMultilineRule() }

    @Test
    fun `should put each argument and the closing parenthesis on its own line when a call has enough arguments`() {
        // Arrange
        val code =
            """
            fun foo() {
                bar(a = 1, b = 2)
            }
            """.trimIndent()
        val formattedCode =
            """
            fun foo() {
                bar(
                    a = 1,
                    b = 2
                )
            }
            """.trimIndent()

        // Act
        val result = argumentListMultilineRuleAssertThat(code)
            .withEditorConfigOverride(FORCE_MULTILINE_ARGUMENT_COUNT_PROPERTY to 2)

        // Assert
        result
            .hasLintViolations(
                LintViolation(2, 9, "Argument should be on a separate line"),
                LintViolation(2, 16, "Argument should be on a separate line"),
                LintViolation(2, 21, "Missing newline before \")\""),
            ).isFormattedAs(formattedCode)
    }

    @Test
    fun `should leave a call on one line when it has fewer arguments than the threshold`() {
        // Arrange
        val code =
            """
            fun foo() {
                bar(a = 1, b = 2)
            }
            """.trimIndent()

        // Act
        val result = argumentListMultilineRuleAssertThat(code)
            .withEditorConfigOverride(FORCE_MULTILINE_ARGUMENT_COUNT_PROPERTY to 3)

        // Assert
        result.hasNoLintViolations()
    }

    @Test
    fun `should leave a positional call on one line when named arguments only is enabled`() {
        // Arrange
        val code =
            """
            fun foo() {
                assertEquals(2, count)
            }
            """.trimIndent()

        // Act
        val result = argumentListMultilineRuleAssertThat(code)
            .withEditorConfigOverride(
                FORCE_MULTILINE_ARGUMENT_COUNT_PROPERTY to 2,
                NAMED_ARGUMENTS_ONLY_PROPERTY to true,
            )

        // Assert
        result.hasNoLintViolations()
    }

    @Test
    fun `should split a call with one named argument among positional ones when named arguments only is enabled`() {
        // Arrange
        val code =
            """
            fun foo() {
                bar(1, b = 2)
            }
            """.trimIndent()
        val formattedCode =
            """
            fun foo() {
                bar(
                    1,
                    b = 2
                )
            }
            """.trimIndent()

        // Act
        val result = argumentListMultilineRuleAssertThat(code)
            .withEditorConfigOverride(
                FORCE_MULTILINE_ARGUMENT_COUNT_PROPERTY to 2,
                NAMED_ARGUMENTS_ONLY_PROPERTY to true,
            )

        // Assert
        result
            .hasLintViolations(
                LintViolation(2, 9, "Argument should be on a separate line"),
                LintViolation(2, 12, "Argument should be on a separate line"),
                LintViolation(2, 17, "Missing newline before \")\""),
            ).isFormattedAs(formattedCode)
    }

    @Test
    fun `should split a positional call when named arguments only is disabled`() {
        // Arrange
        val code =
            """
            fun foo() {
                assertEquals(2, count)
            }
            """.trimIndent()
        val formattedCode =
            """
            fun foo() {
                assertEquals(
                    2,
                    count
                )
            }
            """.trimIndent()

        // Act
        val result = argumentListMultilineRuleAssertThat(code)
            .withEditorConfigOverride(FORCE_MULTILINE_ARGUMENT_COUNT_PROPERTY to 2)

        // Assert
        result
            .hasLintViolations(
                LintViolation(2, 18, "Argument should be on a separate line"),
                LintViolation(2, 21, "Argument should be on a separate line"),
                LintViolation(2, 26, "Missing newline before \")\""),
            ).isFormattedAs(formattedCode)
    }

    @Test
    fun `should report nothing when every argument is already on its own line`() {
        // Arrange
        val code =
            """
            fun foo() {
                bar(
                    a = 1, // first
                    b = 2,
                )
            }
            """.trimIndent()

        // Act
        val result = argumentListMultilineRuleAssertThat(code)
            .withEditorConfigOverride(FORCE_MULTILINE_ARGUMENT_COUNT_PROPERTY to 2)

        // Assert
        result.hasNoLintViolations()
    }

    @Test
    fun `should keep a comment on its own line when it precedes an argument`() {
        // Arrange
        val code =
            """
            fun foo() {
                bar(
                    // explains a
                    a = 1, b = 2
                )
            }
            """.trimIndent()
        val formattedCode =
            """
            fun foo() {
                bar(
                    // explains a
                    a = 1,
                    b = 2
                )
            }
            """.trimIndent()

        // Act
        val result = argumentListMultilineRuleAssertThat(code)
            .withEditorConfigOverride(FORCE_MULTILINE_ARGUMENT_COUNT_PROPERTY to 2)

        // Assert
        result
            .hasLintViolations(LintViolation(4, 16, "Argument should be on a separate line"))
            .isFormattedAs(formattedCode)
    }

    @Test
    fun `should leave annotation arguments on one line when the annotation has enough arguments`() {
        // Arrange
        val code =
            """
            @Foo(a = 1, b = 2)
            fun foo() {}
            """.trimIndent()

        // Act
        val result = argumentListMultilineRuleAssertThat(code)
            .withEditorConfigOverride(FORCE_MULTILINE_ARGUMENT_COUNT_PROPERTY to 2)

        // Assert
        result.hasNoLintViolations()
    }

    @Test
    fun `should report nothing when the threshold is not configured`() {
        // Arrange
        val code =
            """
            fun foo() {
                bar(a = 1, b = 2, c = 3)
            }
            """.trimIndent()

        // Act
        val result = argumentListMultilineRuleAssertThat(code)

        // Assert
        result.hasNoLintViolations()
    }
}
