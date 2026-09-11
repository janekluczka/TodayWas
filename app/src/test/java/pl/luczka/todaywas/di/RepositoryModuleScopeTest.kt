package pl.luczka.todaywas.di

import org.junit.Assert.assertTrue
import org.junit.Test
import pl.luczka.todaywas.data.repository.HabitRepositoryImpl
import pl.luczka.todaywas.data.repository.JournalRepositoryImpl
import javax.inject.Singleton

class RepositoryModuleScopeTest {

    @Test
    fun `should carry Singleton scope on bindJournalRepository`() {
        // Act
        val method = RepositoryModule::class.java.getDeclaredMethod(
            "bindJournalRepository",
            JournalRepositoryImpl::class.java,
        )

        // Assert
        assertTrue(method.isAnnotationPresent(Singleton::class.java))
    }

    @Test
    fun `should carry Singleton scope on bindHabitRepository`() {
        // Act
        val method = RepositoryModule::class.java.getDeclaredMethod(
            "bindHabitRepository",
            HabitRepositoryImpl::class.java,
        )

        // Assert
        assertTrue(method.isAnnotationPresent(Singleton::class.java))
    }
}
