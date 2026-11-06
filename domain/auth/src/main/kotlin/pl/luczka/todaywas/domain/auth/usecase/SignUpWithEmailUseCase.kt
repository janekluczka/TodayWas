package pl.luczka.todaywas.domain.auth.usecase

import javax.inject.Inject
import pl.luczka.todaywas.domain.auth.repository.AuthRepository

class SignUpWithEmailUseCase @Inject constructor(
    private val repository: AuthRepository,
) {

    suspend operator fun invoke(
        email: String,
        password: String,
    ): Result<Unit> = repository.signUpWithEmail(email, password)
}
