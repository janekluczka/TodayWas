package pl.luczka.todaywas.domain.usecase

import pl.luczka.todaywas.domain.repository.AuthRepository
import javax.inject.Inject

class SignUpWithEmailUseCase @Inject constructor(
    private val repository: AuthRepository,
) {

    suspend operator fun invoke(
        email: String,
        password: String,
    ): Result<Unit> = repository.signUpWithEmail(email, password)
}
