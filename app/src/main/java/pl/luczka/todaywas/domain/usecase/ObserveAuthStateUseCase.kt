package pl.luczka.todaywas.domain.usecase

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import pl.luczka.todaywas.domain.model.AuthState
import pl.luczka.todaywas.domain.repository.AuthRepository

class ObserveAuthStateUseCase @Inject constructor(
    private val repository: AuthRepository,
) {

    operator fun invoke(): Flow<AuthState> = repository.observeAuthState()
}
