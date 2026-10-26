package local.agenteye.selfupdate.transport

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateUrlPolicyTest {
    private val asset = UpdateUrlPolicy.asset("v1.1.1", "agent-eye.apk")

    @Test fun fixedApiAndExactTagAssetPaths() {
        assertTrue(UpdateUrlPolicy.allowed(UpdateUrlPolicy.latest()))
        assertTrue(UpdateUrlPolicy.allowed(UpdateUrlPolicy.release(77)))
        assertTrue(UpdateUrlPolicy.allowed(asset))
        val refused = listOf(
            "http://api.github.com/repos/dirtyredz/agent-eye/releases/latest",
            "https://api.github.com:8443/repos/dirtyredz/agent-eye/releases/latest",
            "https://user@api.github.com/repos/dirtyredz/agent-eye/releases/latest",
            "https://api.github.com/repos/dirtyredz/agent-eye/releases/latest#x",
            "https://api.github.com/repos/dirtyredz/agent-eye/releases/latest?per_page=1",
            "https://api.github.com/repos/other/agent-eye/releases/latest",
            "https://api.github.com/repos/dirtyredz/agent-eye/releases",
            "https://api.github.com.evil.com/repos/dirtyredz/agent-eye/releases/latest",
            "https://github.com/dirtyredz/agent-eye/releases/download/latest/agent-eye.apk",
            "https://github.com/dirtyredz/agent-eye/releases/download/v1.1.1/other.apk",
            "https://github.com/dirtyredz/agent-eye/releases/download/v1.1.1/../agent-eye.apk",
            "https://evilgithub.com/dirtyredz/agent-eye/releases/download/v1.1.1/agent-eye.apk"
        )
        for (u in refused) assertFalse(u, UpdateUrlPolicy.allowed(URI(u)))
    }

    @Test fun assetRedirectsOnlyToExactApprovedHosts() {
        val cdn = UpdateUrlPolicy.redirect(
            asset,
            "https://release-assets.githubusercontent.com/github-production/x?sig=abc"
        )
        assertEquals("release-assets.githubusercontent.com", cdn?.host)
        assertEquals(
            "objects.githubusercontent.com",
            UpdateUrlPolicy.redirect(cdn!!, "https://objects.githubusercontent.com/y")?.host
        )
        val bad = listOf(
            "http://release-assets.githubusercontent.com/x",
            "https://evil.release-assets.githubusercontent.com/x",
            "https://release-assets.githubusercontent.com.evil.com/x",
            "https://release-assets.githubusercontent.com:444/x",
            "https://user:pw@objects.githubusercontent.com/x",
            "https://example.com/x",
            "//example.com/x",
            "/relative/path",
            "",
            null
        )
        for (location in bad) {
            assertNull(
                location.toString(),
                UpdateUrlPolicy.redirect(asset, location)
            )
        }
        // API requests never redirect.
        assertNull(
            UpdateUrlPolicy.redirect(
                UpdateUrlPolicy.latest(),
                "https://objects.githubusercontent.com/x"
            )
        )
    }
}
