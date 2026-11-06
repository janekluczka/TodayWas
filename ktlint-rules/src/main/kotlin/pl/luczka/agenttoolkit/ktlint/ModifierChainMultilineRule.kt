package pl.luczka.agenttoolkit.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.ElementType.CALL_EXPRESSION
import com.pinterest.ktlint.rule.engine.core.api.ElementType.DOT
import com.pinterest.ktlint.rule.engine.core.api.ElementType.DOT_QUALIFIED_EXPRESSION
import com.pinterest.ktlint.rule.engine.core.api.ElementType.REFERENCE_EXPRESSION
import com.pinterest.ktlint.rule.engine.core.api.IndentConfig
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.EditorConfig
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.INDENT_SIZE_PROPERTY
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.INDENT_STYLE_PROPERTY
import com.pinterest.ktlint.rule.engine.core.api.ifAutocorrectAllowed
import com.pinterest.ktlint.rule.engine.core.api.indent20
import com.pinterest.ktlint.rule.engine.core.api.isPartOfComment20
import com.pinterest.ktlint.rule.engine.core.api.isWhiteSpaceWithNewline20
import com.pinterest.ktlint.rule.engine.core.api.prevLeaf
import com.pinterest.ktlint.rule.engine.core.api.upsertWhitespaceBeforeMe
import org.jetbrains.kotlin.com.intellij.lang.ASTNode

/**
 * Puts every call of a Compose modifier chain on its own line once the chain has at least two calls, so
 * `Modifier.fillMaxWidth().padding(8.dp)` becomes `Modifier` followed by `.fillMaxWidth()` and `.padding(8.dp)` on
 * lines of their own. A chain is one whose innermost receiver is `Modifier` or a `modifier` parameter. Indentation of
 * anything inside the calls is left to the standard `indent` rule.
 */
class ModifierChainMultilineRule :
    Rule(
        ruleId = RuleId("agent-toolkit:modifier-chain-multiline"),
        about = About(maintainer = "agent-toolkit"),
        usesEditorConfigProperties = setOf(INDENT_SIZE_PROPERTY, INDENT_STYLE_PROPERTY),
    ),
    RuleAutocorrectApproveHandler {
    private var indentConfig = IndentConfig.DEFAULT_INDENT_CONFIG

    override fun beforeFirstNode(editorConfig: EditorConfig) {
        indentConfig = IndentConfig(
            indentStyle = editorConfig[INDENT_STYLE_PROPERTY],
            tabWidth = editorConfig[INDENT_SIZE_PROPERTY],
        )
    }

    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision,
    ) {
        // Start from the outermost link only, so each chain is handled once.
        if (node.elementType != DOT_QUALIFIED_EXPRESSION || node.isReceiverOfOuterChain()) return
        val links = generateSequence(node) { link ->
            link.firstChildNode.takeIf { it.elementType == DOT_QUALIFIED_EXPRESSION }
        }.toList()
        val receiver = links.last().firstChildNode
        if (receiver.elementType != REFERENCE_EXPRESSION || receiver.text !in MODIFIER_RECEIVERS) return
        if (links.count { it.lastChildNode?.elementType == CALL_EXPRESSION } < MIN_CALLS) return

        val continuationIndent = node.indent20 + indentConfig.indent
        links
            .reversed()
            .mapNotNull { it.findChildByType(DOT) }
            .filterNot { it.startsOnNewLine() || it.followsMultilineCall() }
            .forEach { dot ->
                emit(dot.startOffset, "Modifier chain call should be on a separate line", true)
                    .ifAutocorrectAllowed { dot.upsertWhitespaceBeforeMe(continuationIndent) }
            }
    }

    // ktlint's chain-method-continuation keeps `.next()` on the line that closes a multiline call (`).padding(…)`);
    // wrapping it would fight that rule on every format pass.
    private fun ASTNode.followsMultilineCall(): Boolean {
        val previousCall = treePrev?.takeIf { it.elementType != DOT_QUALIFIED_EXPRESSION } ?: treePrev?.lastChildNode
        return previousCall?.elementType == CALL_EXPRESSION && previousCall.text.contains('\n')
    }

    private fun ASTNode.isReceiverOfOuterChain(): Boolean =
        treeParent?.elementType == DOT_QUALIFIED_EXPRESSION && treeParent.firstChildNode == this

    // A comment right before the node owns its line already; never move it.
    private fun ASTNode.startsOnNewLine(): Boolean {
        val previous = prevLeaf ?: return true
        return previous.isWhiteSpaceWithNewline20 || previous.isPartOfComment20
    }

    private companion object {
        val MODIFIER_RECEIVERS = setOf("Modifier", "modifier")
        const val MIN_CALLS = 2
    }
}
