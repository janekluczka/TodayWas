package pl.luczka.todaywas.di

import javax.inject.Singleton
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.luczka.todaywas.data.repository.HabitRepositoryImpl
import pl.luczka.todaywas.data.repository.JournalRepositoryImpl

class RepositoryModuleScopeTest {

    @Test
    fun `should carry Singleton scope when binding the journal repository`() {
        // Act
        val method = RepositoryModule::class.java.getDeclaredMethod(
            "bindJournalRepository",
            JournalRepositoryImpl::class.java,
        )

        // Assert
        assertTrue(method.isAnnotationPresent(Singleton::class.java))
    }

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
