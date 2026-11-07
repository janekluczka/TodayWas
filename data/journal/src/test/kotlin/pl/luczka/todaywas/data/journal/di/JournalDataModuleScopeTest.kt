package pl.luczka.todaywas.data.journal.di

import javax.inject.Singleton
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.luczka.todaywas.data.journal.repository.JournalRepositoryImpl

class JournalDataModuleScopeTest {

    @Test
    fun `should carry Singleton scope when binding the journal repository`() {
        // Act
        val method = JournalDataModule::class.java.getDeclaredMethod(
            "bindJournalRepository",
            JournalRepositoryImpl::class.java,
        )

        // Assert
        assertTrue(method.isAnnotationPresent(Singleton::class.java))
    }
}
