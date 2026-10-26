package local.agenteye.selfupdate

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.security.MessageDigest
import local.agenteye.json.StrictJson
import local.agenteye.selfupdate.contract.ReleaseContract
import local.agenteye.selfupdate.download.ApkInspector
import local.agenteye.selfupdate.download.PackageIdentity
import local.agenteye.selfupdate.install.InstallerPort
import local.agenteye.selfupdate.install.SessionState
import local.agenteye.selfupdate.release.ReleasePolicy
import local.agenteye.selfupdate.transport.Backoff
import local.agenteye.selfupdate.transport.HttpConnector
import local.agenteye.selfupdate.transport.HttpExchange

/** Shared fixtures (written by the Node publisher) and fakes for updater tests. */
object UpdateFixtures {
    const val PIN = "f0d99c5825fbc240e7fefb68bb791d47cb61c37f13c42a5f9f81730168d6d6e2"
    const val TAG = "v1.1.1"
    private val dir = File(
        System.getProperty("agenteye.updateFixtures")
            ?: "../../src/android/releases/artifacts/fixtures/update-schema1"
    )
    val manifest: ByteArray get() = File(dir, "release.json").readBytes()
    val sums: ByteArray get() = File(dir, "SHA256SUMS").readBytes()

    /** Same synthetic bytes `update-fixtures.mjs` hashes. */
    fun synthetic(name: String) = "synthetic $name\n".toByteArray()

    val apk get() = synthetic(ReleaseContract.APK)

    fun asset(name: String) = "$DOWNLOAD/$TAG/$name"

    fun releaseJson(
        id: Long = 77,
        tag: String = TAG,
        draft: Boolean = false,
        prerelease: Boolean = false,
        names: List<String> = ReleaseContract.RELEASE_ASSETS.toList(),
        assetId: (String) -> Long = { 100L + ReleaseContract.RELEASE_ASSETS.indexOf(it) },
        url: (String) -> String = { "$DOWNLOAD/$tag/$it" }
    ): ByteArray {
        val sizes = mapOf(
            ReleaseContract.MANIFEST to manifest.size.toLong(),
            ReleaseContract.SUMS to sums.size.toLong()
        )
        val assets = names.map { n ->
            mapOf(
                "id" to assetId(n),
                "name" to n,
                "size" to (sizes[n] ?: synthetic(n).size.toLong()),
                "state" to "uploaded",
                "browser_download_url" to url(n),
                "label" to null
            )
        }
        val release = mapOf(
            "id" to id,
            "tag_name" to tag,
            "draft" to draft,
            "prerelease" to prerelease,
            "name" to "Agent Eye",
            "assets" to assets,
            "author" to mapOf("login" to "x")
        )
        return StrictJson.write(release).toByteArray()
    }

    private const val DOWNLOAD = "https://github.com/dirtyredz/agent-eye/releases/download"

    fun sha256(bytes: ByteArray) =
        ReleasePolicy.hex(MessageDigest.getInstance("SHA-256").digest(bytes))

    fun candidate(code: Long = 3) = Candidate(
        releaseId = 77, tag = TAG, versionName = "1.1.1", versionCode = code, minSdk = 24,
        manifestAssetId = 103, sumsAssetId = 104, apkAssetId = 100,
        manifestSha256 = sha256(
            manifest
        ),
        apkSize = apk.size.toLong(), apkSha256 = sha256(apk), signerSha256 = PIN
    )
}

class FakeResponse(
    override val code: Int,
    val bytes: ByteArray = ByteArray(0),
    val headers: Map<String, String> = emptyMap(),
    val failAfter: Int? = null
) : HttpExchange {
    var closed = false

    override fun header(name: String) = headers.entries.firstOrNull {
        it.key.equals(name, true)
    }?.value

    override fun body(): InputStream = if (failAfter == null) {
        ByteArrayInputStream(bytes)
    } else {
        object : InputStream() {
            var i = 0

            override fun read(): Int = if (i >=
                failAfter
            ) {
                throw IOException("reset")
            } else {
                bytes[i++].toInt() and 0xff
            }
        }
    }

    override fun close() {
        closed = true
    }
}

/** Serves fixed routes; records every requested URI. Unknown URLs fail like a dead network. */
class FakeConnector(val routes: MutableMap<String, () -> FakeResponse> = mutableMapOf()) :
    HttpConnector {
    val requests = mutableListOf<URI>()

    override fun open(uri: URI, timeoutMs: Int): HttpExchange {
        requests.add(uri)
        return routes[uri.toString()]?.invoke() ?: throw IOException("no route")
    }

    /** A healthy published v1.1.1 release with fixture metadata and the synthetic APK. */
    fun healthy(): FakeConnector {
        routes["https://api.github.com/repos/dirtyredz/agent-eye/releases/latest"] =
            { FakeResponse(200, UpdateFixtures.releaseJson()) }
        routes["https://api.github.com/repos/dirtyredz/agent-eye/releases/77"] =
            { FakeResponse(200, UpdateFixtures.releaseJson()) }
        routes[UpdateFixtures.asset("release.json")] =
            { FakeResponse(200, UpdateFixtures.manifest) }
        routes[UpdateFixtures.asset("SHA256SUMS")] = { FakeResponse(200, UpdateFixtures.sums) }
        routes[UpdateFixtures.asset("agent-eye.apk")] = {
            FakeResponse(
                302,
                headers = mapOf(
                    "Location" to "https://release-assets.githubusercontent.com/x/apk?sig=secret"
                )
            )
        }
        routes["https://release-assets.githubusercontent.com/x/apk?sig=secret"] =
            { FakeResponse(200, UpdateFixtures.apk) }
        return this
    }
}

class MemoryBackoff : Backoff {
    override var until = 0L
}

class FakeInspector(var installed: PackageIdentity = release(2)) : ApkInspector {
    var archive: PackageIdentity? = release(3, "1.1.1")

    override fun installed() = installed

    override fun archive(file: File) = archive

    companion object {
        fun release(
            code: Long,
            name: String = "1.1.0",
            signers: List<String> = listOf(UpdateFixtures.PIN),
            debuggable: Boolean = false
        ) = PackageIdentity("local.agenteye", name, code, 24, signers, debuggable)
    }
}

class FakeInstallerPort : InstallerPort {
    var allowed = true
    var nextId = 41
    val sessions = mutableMapOf<Int, SessionState>()
    val commits = mutableListOf<Pair<Int, String>>()
    val abandoned = mutableListOf<Int>()
    var tamper: ((ByteArray) -> ByteArray)? = null
    var failCommit = false

    override fun allowed() = allowed

    override fun create(size: Long): Int = nextId++.also { sessions[it] = SessionState.Open }

    override fun write(sessionId: Int, file: File): Pair<Long, String> {
        val bytes = file.readBytes().let { tamper?.invoke(it) ?: it }
        return bytes.size.toLong() to UpdateFixtures.sha256(bytes)
    }

    override fun commit(sessionId: Int, nonce: String) {
        if (failCommit) throw IOException("commit failed")
        commits.add(sessionId to nonce)
        sessions[sessionId] = SessionState.Committed
    }

    override fun abandon(sessionId: Int) {
        abandoned.add(sessionId)
        sessions.remove(sessionId)
    }

    override fun state(sessionId: Int) = sessions[sessionId] ?: SessionState.Missing
}

/** Windows-hosted tests: File.renameTo will not replace an existing file there. */
val replacingRename: (File, File) -> Boolean = { from, to ->
    to.delete()
    from.renameTo(to)
}

fun tempDir(): File = kotlin.io.path.createTempDirectory("agent-eye-update").toFile().also {
    it.deleteOnExit()
}
