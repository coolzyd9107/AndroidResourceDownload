package com.resdownload.android.core.common

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {
    @Test
    fun transferProgressIncludesPercentage() {
        assertEquals(
            "1.0 KB / 4.0 KB · 25%",
            formatTransferProgress(
                transferredBytes = 1_024L,
                totalBytes = 4_096L,
            ),
        )
    }

    @Test
    fun transferProgressHandlesUnknownTotalWithoutPercentage() {
        assertEquals(
            "1.0 KB / --",
            formatTransferProgress(
                transferredBytes = 1_024L,
                totalBytes = null,
            ),
        )
    }

    @Test
    fun transferSpeedClampsNegativeSamples() {
        assertEquals("0 B/s", formatTransferSpeed(-1L))
    }
}
