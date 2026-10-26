package local.agenteye.selfupdate.release

import local.agenteye.selfupdate.UpdateFailure
import local.agenteye.selfupdate.UpdateFixtures
import local.agenteye.selfupdate.UpdateProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleasePolicyTest {
    private val base = ReleaseManifest.decode(UpdateFixtures.manifest)

    private fun problem(
        m: ReleaseManifest,
        tag: String = UpdateFixtures.TAG,
        pin: String = UpdateFixtures.PIN
    ) = assertThrows(UpdateFailure::class.java) { ReleasePolicy.validate(m, tag, pin) }.problem

    @Test fun acceptsTheFixture() {
        ReleasePolicy.validate(base, UpdateFixtures.TAG, UpdateFixtures.PIN)
        ReleasePolicy.validateChecksums(
            ReleaseManifest.checksums(UpdateFixtures.sums),
            UpdateFixtures.manifest,
            base
        )
    }

    @Test fun rejectsWrongIdentityVersionProvenanceAndAssets() {
        val bad = listOf(
            base.copy(
                schema = 2
            ),
            base.copy(
                repository = "evil/agent-eye"
            ),
            base.copy(applicationId = "local.agenteye.debug"),
            base.copy(
                tag = "v1.1.2"
            ),
            base.copy(versionName = "1.1.1-beta"), base.copy(versionCode = 0),
            base.copy(
                versionCode = ReleasePolicy.MAX_VERSION_CODE + 1
            ),
            base.copy(commit = "HEAD"), base.copy(runId = 0),
            base.copy(workflow = "evil.yml"), base.copy(minSdk = 0), base.copy(targetSdk = 23),
            base.copy(artifacts = base.artifacts - "mapping.txt"),
            base.copy(
                artifacts =
                    base.artifacts + ("agent-eye-unsigned.apk" to Artifact(1, "a".repeat(64)))
            ),
            base.copy(
                artifacts =
                    base.artifacts +
                        (
                            ReleasePolicy.APK to
                                Artifact(ReleasePolicy.MAX_APK_BYTES + 1, "a".repeat(64))
                            )
            ),
            base.copy(unsignedHashes = emptyMap())
        )
        for (m in bad) assertEquals(m.toString(), UpdateProblem.Malformed, problem(m))
        assertEquals(UpdateProblem.Malformed, problem(base, tag = "v1.1.0"))
    }

    @Test fun signerMustEqualThePackagedPinNeverTheNetworks() {
        val other = "e".repeat(64)
        assertEquals(UpdateProblem.Channel, problem(base.copy(signerSha256 = other)))
        assertEquals(
            UpdateProblem.Channel,
            problem(base.copy(signerSha256 = other), pin = other.replace('e', 'd'))
        )
        assertEquals(UpdateProblem.Channel, problem(base, pin = ""))
    }

    @Test fun checksumsMustAgreeWithManifestBytesAndArtifacts() {
        val sums = ReleaseManifest.checksums(UpdateFixtures.sums)
        val changedBytes = UpdateFixtures.manifest + ' '.code.toByte()
        assertThrows(UpdateFailure::class.java) {
            ReleasePolicy.validateChecksums(sums, changedBytes, base)
        }
        assertThrows(UpdateFailure::class.java) {
            ReleasePolicy.validateChecksums(sums - "mapping.txt", UpdateFixtures.manifest, base)
        }
        val forged = base.copy(
            artifacts =
                base.artifacts + (ReleasePolicy.APK to Artifact(24, "f".repeat(64)))
        )
        assertThrows(UpdateFailure::class.java) {
            ReleasePolicy.validateChecksums(sums, UpdateFixtures.manifest, forged)
        }
    }

    @Test fun availabilityComparesCodesAndSdkWithoutHardCodingTargets() {
        assertEquals(ReleasePolicy.Availability.Upgrade, ReleasePolicy.availability(base, 2, 24))
        assertEquals(ReleasePolicy.Availability.Current, ReleasePolicy.availability(base, 3, 24))
        assertEquals(ReleasePolicy.Availability.Older, ReleasePolicy.availability(base, 4, 24))
        assertEquals(
            ReleasePolicy.Availability.Incompatible,
            ReleasePolicy.availability(base.copy(minSdk = 40), 2, 36)
        )
        // A future target/compile SDK is still a valid release.
        ReleasePolicy.validate(
            base.copy(targetSdk = 41, compileSdk = 42),
            UpdateFixtures.TAG,
            UpdateFixtures.PIN
        )
    }

    @Test fun stableTagsOnly() {
        assertTrue(ReleasePolicy.isStableTag("v1.1.1"))
        for (t in listOf(
            "1.1.1",
            "v1.1",
            "v01.1.1",
            "v1.1.1-rc1",
            "v1.1.1/../x"
        )) {
            assertFalse(t, ReleasePolicy.isStableTag(t))
        }
    }
}
