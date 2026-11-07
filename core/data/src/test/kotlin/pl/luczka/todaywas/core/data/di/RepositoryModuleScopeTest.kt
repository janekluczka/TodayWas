package pl.luczka.todaywas.core.data.di

import javax.inject.Singleton
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.luczka.todaywas.core.data.repository.HabitRepositoryImpl

class RepositoryModuleScopeTest {

    @Test
    fun `should carry Singleton scope when binding the habit repository`() {
        // Act
        val method = RepositoryModule::class.java.getDeclaredMethod(
            "bindHabitRepository",
            HabitRepositoryImpl::class.java,
        )

        // Assert
        assertTrue(method.isAnnotationPresent(Singleton::class.java))
    }
}
