package pl.luczka.todaywas.ktlint

import com.pinterest.ktlint.cli.ruleset.core.api.RuleSetProviderV3
import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import com.pinterest.ktlint.rule.engine.core.api.RuleSetId

class TodayWasRuleSetProvider : RuleSetProviderV3(RuleSetId("todaywas")) {
    override fun getRuleProviders(): Set<RuleProvider> = setOf(RuleProvider { ArgumentListMultilineRule() })
}
