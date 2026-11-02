package pl.luczka.agenttoolkit.ktlint

import com.pinterest.ktlint.cli.ruleset.core.api.RuleSetProviderV3
import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import com.pinterest.ktlint.rule.engine.core.api.RuleSetId

class AgentToolkitRuleSetProvider : RuleSetProviderV3(RuleSetId("agent-toolkit")) {
    override fun getRuleProviders(): Set<RuleProvider> = setOf(RuleProvider { ArgumentListMultilineRule() })
}
