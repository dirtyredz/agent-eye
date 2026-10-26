package local.agenteye.selfupdate.release

import local.agenteye.selfupdate.Candidate
import local.agenteye.selfupdate.UpdateFailure
import local.agenteye.selfupdate.UpdateProblem
import local.agenteye.selfupdate.transport.UpdateHttpClient
import local.agenteye.selfupdate.transport.UpdateUrlPolicy

/**
 * Stable-release discovery against the public GitHub API, unauthenticated. Only metadata
 * (`release.json`, `SHA256SUMS`) is fetched here; the APK is fetched by the downloader through
 * the candidate's exact-tag URL.
 */
class GitHubReleaseSource(private val http: UpdateHttpClient, private val pin: String) {
    sealed class Check {
        data class Available(val candidate: Candidate) : Check()

        object Current : Check()

        /** The latest release has a lower version code than this installation. */
        object NoUpgrade : Check()

        data class Incompatible(val versionName: String, val minSdk: Int) : Check()
    }

    private data class Asset(val id: Long, val name: String, val size: Long)

    private data class Release(val id: Long, val tag: String, val assets: Map<String, Asset>)

    fun check(installedCode: Long, sdk: Int): Check {
        val release =
            release(http.fetch(UpdateUrlPolicy.latest(), ReleasePolicy.MAX_RELEASE_JSON_BYTES))
        val manifestBytes = http.fetch(
            UpdateUrlPolicy.asset(release.tag, ReleasePolicy.MANIFEST),
            ReleasePolicy.MAX_MANIFEST_BYTES.toLong()
        )
        val sums = ReleaseManifest.checksums(
            http.fetch(
                UpdateUrlPolicy.asset(release.tag, ReleasePolicy.SUMS),
                ReleasePolicy.MAX_SUMS_BYTES.toLong()
            )
        )
        val manifest = ReleaseManifest.decode(manifestBytes)
        ReleasePolicy.validate(manifest, release.tag, pin)
        ReleasePolicy.validateChecksums(sums, manifestBytes, manifest)
        val apk = manifest.artifacts.getValue(ReleasePolicy.APK)
        if (release.assets.getValue(ReleasePolicy.APK).size != apk.size ||
            release.assets.getValue(ReleasePolicy.MANIFEST).size != manifestBytes.size.toLong()
        ) {
            throw UpdateFailure(UpdateProblem.Malformed)
        }
        return when (ReleasePolicy.availability(manifest, installedCode, sdk)) {
            ReleasePolicy.Availability.Current -> Check.Current

            ReleasePolicy.Availability.Older -> Check.NoUpgrade

            ReleasePolicy.Availability.Incompatible -> Check.Incompatible(
                manifest.versionName,
                manifest.minSdk.toInt()
            )

            ReleasePolicy.Availability.Upgrade -> Check.Available(
                Candidate(
                    releaseId = release.id,
                    tag = release.tag,
                    versionName = manifest.versionName,
                    versionCode = manifest.versionCode,
                    minSdk = manifest.minSdk.toInt(),
                    manifestAssetId = release.assets.getValue(ReleasePolicy.MANIFEST).id,
                    sumsAssetId = release.assets.getValue(ReleasePolicy.SUMS).id,
                    apkAssetId = release.assets.getValue(ReleasePolicy.APK).id,
                    manifestSha256 = ReleasePolicy.sha256(manifestBytes),
                    apkSize = apk.size,
                    apkSha256 = apk.sha256,
                    signerSha256 = manifest.signerSha256
                )
            )
        }
    }

    /**
     * Refetches the exact release by ID and its manifest before any download; anything that
     * moved (tag, asset IDs, manifest bytes) requires a fresh check.
     */
    fun revalidate(candidate: Candidate) {
        val release = try {
            release(
                http.fetch(
                    UpdateUrlPolicy.release(candidate.releaseId),
                    ReleasePolicy.MAX_RELEASE_JSON_BYTES
                )
            )
        } catch (e: UpdateFailure) {
            throw if (e.problem == UpdateProblem.NoRelease ||
                e.problem == UpdateProblem.Malformed
            ) {
                UpdateFailure(UpdateProblem.Changed)
            } else {
                e
            }
        }
        val same = release.id == candidate.releaseId && release.tag == candidate.tag &&
            release.assets[ReleasePolicy.APK]?.id == candidate.apkAssetId &&
            release.assets[ReleasePolicy.MANIFEST]?.id == candidate.manifestAssetId &&
            release.assets[ReleasePolicy.SUMS]?.id == candidate.sumsAssetId
        if (!same) throw UpdateFailure(UpdateProblem.Changed)
        val manifest = http.fetch(
            UpdateUrlPolicy.asset(candidate.tag, ReleasePolicy.MANIFEST),
            ReleasePolicy.MAX_MANIFEST_BYTES.toLong()
        )
        if (ReleasePolicy.sha256(manifest) !=
            candidate.manifestSha256
        ) {
            throw UpdateFailure(UpdateProblem.Changed)
        }
    }

    private fun release(bytes: ByteArray): Release {
        val root = try {
            StrictJson.parse(bytes) as? Map<*, *>
        } catch (_: StrictJson.Invalid) {
            null
        } ?: malformed()
        val id = root["id"] as? Long ?: malformed()
        val tag = root["tag_name"] as? String ?: malformed()
        if (id <= 0 || root["draft"] != false || root["prerelease"] != false ||
            !ReleasePolicy.isStableTag(tag)
        ) {
            malformed()
        }
        val assets = (root["assets"] as? List<*> ?: malformed()).map { item ->
            val a = item as? Map<*, *> ?: malformed()
            val asset = Asset(
                a["id"] as? Long ?: malformed(),
                a["name"] as? String ?: malformed(),
                a["size"] as? Long ?: malformed()
            )
            val url = a["browser_download_url"] as? String ?: malformed()
            val state = a["state"]
            if (asset.id <= 0 || asset.size <= 0 || (state != null && state != "uploaded") ||
                url !=
                UpdateUrlPolicy.asset(
                    tag,
                    asset.name.takeIf {
                        it in ReleasePolicy.RELEASE_ASSETS
                    } ?: malformed()
                ).toString()
            ) {
                malformed()
            }
            asset
        }
        val byName = assets.associateBy { it.name }
        if (byName.size != assets.size || byName.keys != ReleasePolicy.RELEASE_ASSETS) malformed()
        return Release(id, tag, byName)
    }

    private fun malformed(): Nothing = throw UpdateFailure(UpdateProblem.Malformed)
}
