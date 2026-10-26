package local.agenteye.selfupdate.release

import local.agenteye.selfupdate.UpdateFailure
import local.agenteye.selfupdate.UpdateFixtures
import local.agenteye.selfupdate.UpdateProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ReleaseManifestTest {
    private fun problem(block: () -> Unit) =
        assertThrows(UpdateFailure::class.java) { block() }.problem

    @Test fun decodesThePublisherFixture() {
        val m = ReleaseManifest.decode(UpdateFixtures.manifest)
        assertEquals("1.1.1", m.versionName)
        assertEquals(3L, m.versionCode)
        assertEquals(UpdateFixtures.PIN, m.signerSha256)
        assertEquals(ReleasePolicy.FINAL, m.artifacts.keys)
        val sums = ReleaseManifest.checksums(UpdateFixtures.sums)
        assertEquals(ReleasePolicy.FINAL + ReleasePolicy.MANIFEST, sums.keys)
    }

    @Test fun rejectsMalformedDuplicateMissingOrExtraFields() {
        val text = UpdateFixtures.manifest.decodeToString()
        val cases = listOf(
            text.replace("\"schema\": 1,", "\"schema\": 1, \"schema\": 1,"),
            text.replace("\"commit\": \"${"c".repeat(40)}\",", ""),
            text.replace("\"runId\": 4242,", "\"runId\": 4242, \"extra\": true,"),
            text.replace("\"versionCode\": 3", "\"versionCode\": 99999999999999999999"),
            text.replace("\"versionCode\": 3", "\"versionCode\": \"3\""),
            text.dropLast(3),
            "$text{}",
            "[]"
        )
        for (case in cases) {
            assertEquals(
                UpdateProblem.Malformed,
                problem {
                    ReleaseManifest.decode(case.toByteArray())
                }
            )
        }
        assertEquals(
            UpdateProblem.Malformed,
            problem {
                ReleaseManifest.decode(ByteArray(64 * 1024 + 1) { ' '.code.toByte() })
            }
        )
        assertEquals(
            UpdateProblem.Malformed,
            problem {
                ReleaseManifest.decode(byteArrayOf(0xC3.toByte(), 0x28))
            }
        )
    }

    @Test fun checksumsRequireTheExactTwoSpaceFormatAndUniqueNames() {
        val good = UpdateFixtures.sums.decodeToString()
        val line = good.lines().first()
        for (bad in listOf(
            "",
            good.dropLast(1),
            good.replace("  ", " "),
            good + line + "\n",
            good.replace("\n", "\r\n"),
            good.uppercase()
        )) {
            assertEquals(
                UpdateProblem.Malformed,
                problem {
                    ReleaseManifest.checksums(bad.toByteArray())
                }
            )
        }
        assertEquals(
            UpdateProblem.Malformed,
            problem {
                ReleaseManifest.checksums(ByteArray(8 * 1024 + 1))
            }
        )
    }

    @Test fun strictJsonRoundTripsRecords() {
        val value = mapOf(
            "a" to 1L,
            "b" to "x\"\\n",
            "c" to listOf(true, null),
            "d" to mapOf<String, Any?>()
        )
        assertEquals(value, StrictJson.parse(StrictJson.write(value)))
        assertThrows(StrictJson.Invalid::class.java) { StrictJson.parse("{\"a\":01}") }
        assertThrows(StrictJson.Invalid::class.java) {
            StrictJson.parse("[".repeat(40) + "]".repeat(40))
        }
    }
}
