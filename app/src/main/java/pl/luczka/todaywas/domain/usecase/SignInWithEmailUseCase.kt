package pl.luczka.todaywas.domain.usecase

import javax.inject.Inject
import pl.luczka.todaywas.domain.repository.AuthRepository

class SignInWithEmailUseCase @Inject constructor(
    private val repository: AuthRepository,
) {

    suspend operator fun invoke(
        email: String,
        password: String,
    ): Result<Unit> = repository.signInWithEmail(email, password)
}
