package pl.luczka.todaywas.feature.onboarding.api.navigation

import kotlinx.serialization.Serializable

@Serializable
enum class AllSetReason {
    NO_ACCOUNT,
    SIGNED_IN,
    ACCOUNT_CREATED,
}
