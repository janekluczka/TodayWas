package pl.luczka.todaywas.domain.auth.usecase

import javax.inject.Inject
import pl.luczka.todaywas.domain.auth.repository.AuthRepository

class SignInWithGoogleUseCase @Inject constructor(
    private val repository: AuthRepository,
) {

    suspend operator fun invoke(idToken: String): Result<Unit> = repository.signInWithGoogleIdToken(idToken)
}
