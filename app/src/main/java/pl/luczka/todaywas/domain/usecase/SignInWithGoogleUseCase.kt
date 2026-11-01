package pl.luczka.todaywas.domain.usecase

import javax.inject.Inject
import pl.luczka.todaywas.domain.repository.AuthRepository

class SignInWithGoogleUseCase @Inject constructor(
    private val repository: AuthRepository,
) {

    suspend operator fun invoke(idToken: String): Result<Unit> = repository.signInWithGoogleIdToken(
        idToken,
    )
}
