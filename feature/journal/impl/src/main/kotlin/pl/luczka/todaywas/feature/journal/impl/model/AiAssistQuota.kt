package pl.luczka.todaywas.feature.journal.impl.model

// The daily quota is shared by "help me start" and "help me refine": a DAILY_LIMIT_REACHED failure means none are
// left, any other failure leaves the last known count as it was.
internal fun remainingTodayAfter(
    error: AiAssistErrorUiState,
    remainingToday: Int?,
): Int? = if (error == AiAssistErrorUiState.DAILY_LIMIT_REACHED) 0 else remainingToday

// remainingToday is only known once a call has actually returned this session -- null means "don't know yet", so the
// first call of a session is never preemptively blocked here; the server is the real source of truth either way.
internal fun hasRemainingToday(remainingToday: Int?): Boolean = (remainingToday ?: 1) > 0
