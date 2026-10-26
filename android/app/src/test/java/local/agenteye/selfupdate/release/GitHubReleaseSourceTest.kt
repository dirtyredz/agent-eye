package local.agenteye.selfupdate.release

import local.agenteye.selfupdate.FakeConnector
import local.agenteye.selfupdate.FakeResponse
import local.agenteye.selfupdate.MemoryBackoff
import local.agenteye.selfupdate.UpdateFailure
import local.agenteye.selfupdate.UpdateFixtures
import local.agenteye.selfupdate.UpdateProblem
import local.agenteye.selfupdate.transport.UpdateHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleaseSourceTest {
    private val connector = FakeConnector().healthy()
    private val backoff = MemoryBackoff()
    private val source =
        GitHubReleaseSource(
            UpdateHttpClient(connector, backoff, now = {
                1000L
            }),
            UpdateFixtures.PIN
        )
    private val latest = "https://api.github.com/repos/dirtyredz/agent-eye/releases/latest"

    private fun problem(block: () -> Unit) = assertThrows(UpdateFailure::class.java) {
        block()
    }.problem

    private fun serveLatest(bytes: ByteArray) {
        connector.routes[latest] = { FakeResponse(200, bytes) }
    }

    @Test fun selectsTheStableReleaseAndFreezesItsIdentity() {
        val result = source.check(installedCode = 2, sdk = 34)
        assertEquals(GitHubReleaseSource.Check.Available(UpdateFixtures.candidate()), result)
        // Only metadata: the APK is never fetched by a check.
        assertTrue(
            connector.requests.none {
                it.path.endsWith(".apk") ||
                    it.host.contains("githubusercontent")
            }
        )
    }

    @Test fun equalIsCurrentLowerIsNoUpgradeAndHigherSdkIsExplained() {
        assertEquals(GitHubReleaseSource.Check.Current, source.check(3, 34))
        assertEquals(GitHubReleaseSource.Check.NoUpgrade, source.check(4, 34))
        assertEquals(GitHubReleaseSource.Check.Incompatible("1.1.1", 24), source.check(2, 23))
    }

    @Test fun rejectsDraftsPrereleasesIncompleteAndCollidingAssetSets() {
        val bad = listOf(
            UpdateFixtures.releaseJson(draft = true),
            UpdateFixtures.releaseJson(prerelease = true),
            UpdateFixtures.releaseJson(tag = "v1.1.1-rc1"),
            UpdateFixtures.releaseJson(
                names = ReleasePolicy.RELEASE_ASSETS.toList() - "SHA256SUMS"
            ),
            UpdateFixtures.releaseJson(
                names =
                    ReleasePolicy.RELEASE_ASSETS.toList() + "agent-eye.apk"
            ),
            UpdateFixtures.releaseJson(
                names =
                    ReleasePolicy.RELEASE_ASSETS.toList() + "agent-eye-unsigned.apk"
            ),
            UpdateFixtures.releaseJson(url = { "https://evil.example/$it" }),
            UpdateFixtures.releaseJson(url = {
                "https://github.com/dirtyredz/agent-eye/releases/download/v1.1.0/$it"
            }),
            "{\"id\":1,\"id\":2}".toByteArray()
        )
        for (bytes in bad) {
            serveLatest(bytes)
            assertEquals(
                bytes.decodeToString(),
                UpdateProblem.Malformed,
                problem {
                    source.check(2, 34)
                }
            )
        }
    }

    @Test fun rejectsChangedOrForgedMetadata() {
        connector.routes[UpdateFixtures.asset("SHA256SUMS")] = {
            FakeResponse(
                200,
                UpdateFixtures.sums.decodeToString().replaceFirst('4', '5').toByteArray()
            )
        }
        assertEquals(UpdateProblem.Malformed, problem { source.check(2, 34) })
        connector.healthy()
        connector.routes[UpdateFixtures.asset("release.json")] =
            { FakeResponse(200, UpdateFixtures.manifest + '\n'.code.toByte()) }
        assertEquals(UpdateProblem.Malformed, problem { source.check(2, 34) })
    }

    @Test fun notFoundIsNoReleaseAndRateLimitsBackOff() {
        connector.routes[latest] = { FakeResponse(404) }
        assertEquals(UpdateProblem.NoRelease, problem { source.check(2, 34) })
        connector.routes[latest] = { FakeResponse(429, headers = mapOf("Retry-After" to "60")) }
        assertEquals(UpdateProblem.RateLimited, problem { source.check(2, 34) })
        assertEquals(61_000L, backoff.until)
    }

    @Test fun revalidatesTheExactCandidateBeforeDownload() {
        val candidate = UpdateFixtures.candidate()
        source.revalidate(candidate)
        val byId = "https://api.github.com/repos/dirtyredz/agent-eye/releases/77"
        connector.routes[byId] =
            { FakeResponse(200, UpdateFixtures.releaseJson(assetId = { 900L + it.length })) }
        assertEquals(UpdateProblem.Changed, problem { source.revalidate(candidate) })
        connector.routes[byId] = { FakeResponse(404) }
        assertEquals(UpdateProblem.Changed, problem { source.revalidate(candidate) })
        connector.healthy()
        connector.routes[UpdateFixtures.asset("release.json")] =
            { FakeResponse(200, UpdateFixtures.manifest.copyOf(100)) }
        assertEquals(UpdateProblem.Changed, problem { source.revalidate(candidate) })
    }
}
