package link.mczihan.androidResourceDownload.feature.settings

import link.mczihan.androidResourceDownload.data.update.UpdateManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateDecisionTest {
    private val manifest = UpdateManifest(
        latestVersion = "2.1.1",
        updateUrl = "https://example.com/download",
    )

    @Test
    fun lowerLocalVersionProducesAvailableUpdate() {
        val state = resolveUpdateState("2.0.0", manifest)

        assertTrue(state is UpdateUiState.Available)
        state as UpdateUiState.Available
        assertEquals("2.0.0", state.currentVersion)
        assertEquals("2.1.1", state.latestVersion)
        assertEquals("https://example.com/download", state.updateUrl)
    }

    @Test
    fun equalOrHigherLocalVersionIsUpToDate() {
        assertEquals(UpdateUiState.UpToDate("2.1.1"), resolveUpdateState("2.1.1", manifest))
        assertEquals(UpdateUiState.UpToDate("2.2.0"), resolveUpdateState("2.2.0", manifest))
    }
}
