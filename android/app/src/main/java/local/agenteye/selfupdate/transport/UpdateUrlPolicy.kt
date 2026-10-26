package local.agenteye.selfupdate.transport

import java.net.URI
import local.agenteye.selfupdate.release.ReleasePolicy

/**
 * The only outbound destinations the updater may contact. Pure, so every rule is unit-tested:
 * fixed API paths for this repository, exact-tag asset paths, HTTPS on 443, no userinfo or
 * fragment, and asset redirects only to exact approved GitHub download hosts (no wildcards).
 */
object UpdateUrlPolicy {
    const val API_HOST = "api.github.com"
    const val DOWNLOAD_HOST = "github.com"
    val ASSET_HOSTS = setOf("release-assets.githubusercontent.com", "objects.githubusercontent.com")
    const val MAX_REDIRECTS = 5
    private val repo = ReleasePolicy.REPOSITORY
    private val apiPath = Regex("/repos/${Regex.escape(repo)}/releases/(latest|[1-9][0-9]{0,15})")

    fun latest(): URI = URI("https://$API_HOST/repos/$repo/releases/latest")

    fun release(id: Long): URI = URI("https://$API_HOST/repos/$repo/releases/$id")

    fun asset(tag: String, name: String): URI {
        require(ReleasePolicy.isStableTag(tag) && name in ReleasePolicy.RELEASE_ASSETS)
        return URI("https://$DOWNLOAD_HOST/$repo/releases/download/$tag/$name")
    }

    private fun secure(uri: URI) = uri.scheme == "https" && (uri.port == -1 || uri.port == 443) &&
        uri.rawUserInfo == null && uri.rawFragment == null && uri.host != null

    fun isApi(uri: URI) = secure(uri) && uri.host == API_HOST && uri.rawQuery == null &&
        apiPath.matches(uri.rawPath.orEmpty())

    fun isAsset(uri: URI): Boolean {
        if (!secure(uri) || uri.host != DOWNLOAD_HOST || uri.rawQuery != null) return false
        val parts = uri.rawPath.orEmpty().split('/')
        return parts.size == 7 && parts[0].isEmpty() && "${parts[1]}/${parts[2]}" == repo &&
            parts[3] == "releases" && parts[4] == "download" && ReleasePolicy.isStableTag(
                parts[5]
            ) &&
            parts[6] in ReleasePolicy.RELEASE_ASSETS
    }

    /** Whether a request may be made to [uri] at all (initial request). */
    fun allowed(uri: URI) = isApi(uri) || isAsset(uri)

    /**
     * Resolves and approves a redirect. API requests never redirect; asset requests may only
     * hop to an exact approved CDN host over HTTPS (signed query strings are allowed there,
     * and are never logged or persisted). Returns null when the hop is refused.
     */
    fun redirect(from: URI, location: String?): URI? {
        if (location.isNullOrEmpty() || location.length > 8192) return null
        if (!(isAsset(from) || isCdn(from))) return null
        val target = try {
            from.resolve(URI(location))
        } catch (_: Exception) {
            return null
        }
        return target.takeIf { isCdn(it) }
    }

    private fun isCdn(uri: URI) = secure(uri) && uri.host in ASSET_HOSTS
}
