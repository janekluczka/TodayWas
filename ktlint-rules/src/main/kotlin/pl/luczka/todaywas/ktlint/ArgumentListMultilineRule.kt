package pl.luczka.todaywas.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.ElementType.CALL_EXPRESSION
import com.pinterest.ktlint.rule.engine.core.api.ElementType.RPAR
import com.pinterest.ktlint.rule.engine.core.api.ElementType.VALUE_ARGUMENT
import com.pinterest.ktlint.rule.engine.core.api.ElementType.VALUE_ARGUMENT_LIST
import com.pinterest.ktlint.rule.engine.core.api.ElementType.VALUE_ARGUMENT_NAME
import com.pinterest.ktlint.rule.engine.core.api.IndentConfig
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.EditorConfig
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.EditorConfigProperty
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.INDENT_SIZE_PROPERTY
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.INDENT_STYLE_PROPERTY
import com.pinterest.ktlint.rule.engine.core.api.ifAutocorrectAllowed
import com.pinterest.ktlint.rule.engine.core.api.indent20
import com.pinterest.ktlint.rule.engine.core.api.isPartOfComment20
import com.pinterest.ktlint.rule.engine.core.api.isWhiteSpaceWithNewline20
import com.pinterest.ktlint.rule.engine.core.api.prevLeaf
import com.pinterest.ktlint.rule.engine.core.api.upsertWhitespaceBeforeMe
import org.ec4j.core.model.PropertyType
import org.jetbrains.kotlin.com.intellij.lang.ASTNode

/**
 * Puts every argument of a call on its own line, with the closing parenthesis on a line of its own, once the call has at
 * least [FORCE_MULTILINE_ARGUMENT_COUNT_PROPERTY] arguments — with [NAMED_ARGUMENTS_ONLY_PROPERTY], only when at least
 * one of them is named, so positional calls like `assertEquals(2, count)` stay on one line. The call-site counterpart of
 * ktlint's `ktlint_function_signature_rule_force_multiline_when_parameter_count_greater_or_equal_than`; indentation and
 * the trailing comma are left to the standard `indent` and `trailing-comma-on-call-site` rules.
 */
class ArgumentListMultilineRule :
    Rule(
        ruleId = RuleId("todaywas:argument-list-multiline"),
        about = About(maintainer = "TodayWas"),
        usesEditorConfigProperties = setOf(
            FORCE_MULTILINE_ARGUMENT_COUNT_PROPERTY,
            NAMED_ARGUMENTS_ONLY_PROPERTY,
            INDENT_SIZE_PROPERTY,
            INDENT_STYLE_PROPERTY,
        ),
    ),
    RuleAutocorrectApproveHandler {
    private var forceMultilineWhenArgumentCount = Int.MAX_VALUE
    private var namedArgumentsOnly = false
    private var indentConfig = IndentConfig.DEFAULT_INDENT_CONFIG

    override fun beforeFirstNode(editorConfig: EditorConfig) {
        forceMultilineWhenArgumentCount = editorConfig[FORCE_MULTILINE_ARGUMENT_COUNT_PROPERTY]
        namedArgumentsOnly = editorConfig[NAMED_ARGUMENTS_ONLY_PROPERTY]
        indentConfig = IndentConfig(
            indentStyle = editorConfig[INDENT_STYLE_PROPERTY],
            tabWidth = editorConfig[INDENT_SIZE_PROPERTY],
        )
    }

    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision,
    ) {
        // Only real calls: annotation entries, super-type calls and enum entries keep their own layout.
        if (node.elementType != VALUE_ARGUMENT_LIST || node.treeParent?.elementType != CALL_EXPRESSION) return
        val arguments = node.getChildren(null).filter { it.elementType == VALUE_ARGUMENT }
        if (arguments.size < forceMultilineWhenArgumentCount) return
        if (namedArgumentsOnly && arguments.none { it.findChildByType(VALUE_ARGUMENT_NAME) != null }) return
        val closingParenthesis = node.lastChildNode?.takeIf { it.elementType == RPAR } ?: return

        val lineIndent = node.indent20
        arguments.filterNot { it.startsOnNewLine() }.forEach { argument ->
            emit(argument.startOffset, "Argument should be on a separate line", true)
                .ifAutocorrectAllowed { argument.upsertWhitespaceBeforeMe(lineIndent + indentConfig.indent) }
        }
        if (!closingParenthesis.startsOnNewLine()) {
            emit(closingParenthesis.startOffset, "Missing newline before \")\"", true)
                .ifAutocorrectAllowed { closingParenthesis.upsertWhitespaceBeforeMe(lineIndent) }
        }
    }

    // A comment right before the node owns its line already; never move it.
    private fun ASTNode.startsOnNewLine(): Boolean {
        val previous = prevLeaf ?: return true
        return previous.isWhiteSpaceWithNewline20 || previous.isPartOfComment20
    }

    companion object {
        val FORCE_MULTILINE_ARGUMENT_COUNT_PROPERTY: EditorConfigProperty<Int> =
            EditorConfigProperty(
                type = PropertyType.LowerCasingPropertyType(
                    "todaywas_argument_list_force_multiline_when_argument_count_greater_or_equal_than",
                    "Force one argument per line when a call has at least this many arguments. Use 'unset' to disable.",
                    PropertyType.PropertyValueParser.POSITIVE_INT_VALUE_PARSER,
                    setOf("1", "2", "3", "4", "5", "unset"),
                ),
                defaultValue = Int.MAX_VALUE,
                propertyMapper = { property, _ ->
                    if (property?.isUnset == true) Int.MAX_VALUE else property?.getValueAs<Int>()
                },
            )

        val NAMED_ARGUMENTS_ONLY_PROPERTY: EditorConfigProperty<Boolean> =
            EditorConfigProperty(
                type = PropertyType.LowerCasingPropertyType(
                    "todaywas_argument_list_force_multiline_named_arguments_only",
                    "Only force one argument per line when at least one argument of the call is named.",
                    PropertyType.PropertyValueParser.BOOLEAN_VALUE_PARSER,
                    setOf("true", "false"),
                ),
                defaultValue = false,
            )
    }
}
