package pl.luczka.todaywas.domain.auth.usecase

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import pl.luczka.todaywas.domain.auth.model.AuthState
import pl.luczka.todaywas.domain.auth.repository.AuthRepository

class ObserveAuthStateUseCase @Inject constructor(
    private val repository: AuthRepository,
) {

    operator fun invoke(): Flow<AuthState> = repository.observeAuthState()
}
