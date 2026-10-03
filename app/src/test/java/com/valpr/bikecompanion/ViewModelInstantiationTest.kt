package com.valpr.bikecompanion

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.valpr.bikecompanion.ui.athletestats.AthleteStatsViewModel
import com.valpr.bikecompanion.ui.dashboard.DashboardViewModel
import com.valpr.bikecompanion.ui.editor.WorkoutEditorViewModel
import com.valpr.bikecompanion.ui.history.RideHistoryViewModel
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies that all Activity-scoped and screen ViewModels expose a single-argument
 * (Application) constructor compatible with AndroidViewModelFactory / SavedStateViewModelFactory.
 *
 * When adding default parameters (e.g. ioDispatcher: CoroutineDispatcher = Dispatchers.IO)
 * to an AndroidViewModel, @JvmOverloads constructor(...) is required, or Kotlin does not
 * generate the 1-arg Java constructor, causing runtime NoSuchMethodException crashes on launch.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ViewModelInstantiationTest {

    private lateinit var store: ViewModelStore

    @Before
    fun setUp() {
        store = ViewModelStore()
    }

    @After
    fun tearDown() {
        // Cancel every viewModelScope: the real ViewModels own infinite
        // collectors (profile/state flows) that would otherwise leak into
        // whatever test class shares this forked JVM next and starve its
        // main looper.
        store.clear()
    }

    @Test
    fun allAndroidViewModelsExposeApplicationConstructor() {
        val viewModelClasses = listOf(
            DashboardViewModel::class.java,
            WorkoutEditorViewModel::class.java,
            AthleteStatsViewModel::class.java,
            RideHistoryViewModel::class.java
        )

        for (clazz in viewModelClasses) {
            val constructor = clazz.getConstructor(Application::class.java)
            assertNotNull("Constructor (Application) missing on ${clazz.name}", constructor)
        }
    }

    @Test
    fun defaultAndroidViewModelFactoryInstantiatesAllViewModels() {
        val app = ApplicationProvider.getApplicationContext<BikeApplication>()
        val factory = ViewModelProvider.AndroidViewModelFactory.getInstance(app)
        val provider = ViewModelProvider(store, factory)

        val dashboardVm = provider.get(DashboardViewModel::class.java)
        assertNotNull(dashboardVm)

        val editorVm = provider.get(WorkoutEditorViewModel::class.java)
        assertNotNull(editorVm)

        val athleteStatsVm = provider.get(AthleteStatsViewModel::class.java)
        assertNotNull(athleteStatsVm)

        val historyVm = provider.get(RideHistoryViewModel::class.java)
        assertNotNull(historyVm)
    }
}
