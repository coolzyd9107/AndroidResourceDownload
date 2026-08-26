package com.resdownload.android.feature.files

import androidx.activity.BackEventCompat
import org.junit.Assert.assertEquals
import org.junit.Test

class ScaleBackAnimationTest {
    @Test
    fun leftEdgeUsesRightPivotAndExitsRight() {
        assertEquals(0.8f, scaleBackPivotX(BackEventCompat.EDGE_LEFT), 0f)
        assertEquals(1f, scaleBackExitDirection(BackEventCompat.EDGE_LEFT), 0f)
    }

    @Test
    fun rightEdgeUsesLeftPivotAndExitsLeft() {
        assertEquals(0.2f, scaleBackPivotX(BackEventCompat.EDGE_RIGHT), 0f)
        assertEquals(-1f, scaleBackExitDirection(BackEventCompat.EDGE_RIGHT), 0f)
    }

    @Test
    fun verticalPivotFollowsTouchWithinSafeBounds() {
        assertEquals(0.5f, scaleBackPivotY(touchY = 500f, containerHeightPx = 1_000), 0f)
        assertEquals(0.1f, scaleBackPivotY(touchY = 0f, containerHeightPx = 1_000), 0f)
        assertEquals(0.9f, scaleBackPivotY(touchY = 1_000f, containerHeightPx = 1_000), 0f)
        assertEquals(0.5f, scaleBackPivotY(touchY = Float.NaN, containerHeightPx = 0), 0f)
    }

    @Test
    fun scaleReachesReSukiSuTarget() {
        assertEquals(1f, scaleBackScale(0f), 0f)
        assertEquals(0.85f, scaleBackScale(1f), 0.0001f)
    }
}
