package pl.luczka.todaywas.core.domain.usecase

import javax.inject.Inject
import pl.luczka.todaywas.core.domain.repository.AuthRepository

class SignInWithGoogleUseCase @Inject constructor(
    private val repository: AuthRepository,
) {

    suspend operator fun invoke(idToken: String): Result<Unit> = repository.signInWithGoogleIdToken(idToken)
}
