package pl.luczka.todaywas.domain.usecase

import pl.luczka.todaywas.data.repository.AuthRepository
import javax.inject.Inject

class SignInWithGoogleUseCase @Inject constructor(
    private val repository: AuthRepository,
) {

    suspend operator fun invoke(idToken: String): Result<Unit> = repository.signInWithGoogleIdToken(idToken)
}
