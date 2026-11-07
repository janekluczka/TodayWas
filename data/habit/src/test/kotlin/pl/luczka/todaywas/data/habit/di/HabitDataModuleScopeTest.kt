package pl.luczka.todaywas.data.habit.di

import javax.inject.Singleton
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.luczka.todaywas.data.habit.repository.HabitRepositoryImpl

class HabitDataModuleScopeTest {

    @Test
    fun `should carry Singleton scope when binding the habit repository`() {
        // Act
        val method = HabitDataModule::class.java.getDeclaredMethod(
            "bindHabitRepository",
            HabitRepositoryImpl::class.java,
        )

        // Assert
        assertTrue(method.isAnnotationPresent(Singleton::class.java))
    }
}
