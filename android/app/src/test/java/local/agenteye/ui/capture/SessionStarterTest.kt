package local.agenteye.ui.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStarterTest {
    @Test fun permissionReturnNeedsCameraAndAnOfferedStart() {
        val offered = CameraState(canRequestStart = true)
        assertTrue(SessionStarter.canStart(offered, cameraGranted = true))
        assertFalse(SessionStarter.canStart(offered, cameraGranted = false))
        assertFalse(SessionStarter.canStart(CameraState(), cameraGranted = true))
    }
}
