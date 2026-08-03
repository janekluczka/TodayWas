package pl.luczka.todaywas.domain.usecase

import pl.luczka.todaywas.data.repository.AuthRepository
import javax.inject.Inject

class SignOutUseCase @Inject constructor(
    private val repository: AuthRepository,
) {

    suspend operator fun invoke(): Result<Unit> = repository.signOut()
}
