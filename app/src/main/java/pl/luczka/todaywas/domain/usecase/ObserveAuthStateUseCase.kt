package pl.luczka.todaywas.domain.usecase

import kotlinx.coroutines.flow.Flow
import pl.luczka.todaywas.domain.model.AuthState
import pl.luczka.todaywas.domain.repository.AuthRepository
import javax.inject.Inject

class ObserveAuthStateUseCase @Inject constructor(
    private val repository: AuthRepository,
) {

    operator fun invoke(): Flow<AuthState> = repository.observeAuthState()
}
