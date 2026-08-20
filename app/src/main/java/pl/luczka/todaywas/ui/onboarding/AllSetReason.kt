package pl.luczka.todaywas.ui.onboarding

import kotlinx.serialization.Serializable

@Serializable
enum class AllSetReason {
    NO_ACCOUNT,
    SIGNED_IN,
    ACCOUNT_CREATED,
}
