package local.agenteye.selfupdate.storage

import local.agenteye.json.JsonObject
import local.agenteye.json.StrictJson
import local.agenteye.selfupdate.Candidate
import local.agenteye.selfupdate.contract.ReleaseContract

/**
 * Updater records <-> JSON maps. Decoding validates shape and throws [StrictJson.Invalid] (or
 * [IllegalArgumentException] for an unknown stage); [UpdateStore] treats either as "discard".
 */
internal object RecordCodec {
    private val NONCE = Regex("[a-f0-9]{32}")

    fun encode(c: Candidate): Map<String, Any?> = mapOf(
        "releaseId" to c.releaseId, "tag" to c.tag, "versionName" to c.versionName,
        "versionCode" to c.versionCode, "minSdk" to c.minSdk.toLong(),
        "manifestAssetId" to c.manifestAssetId, "sumsAssetId" to c.sumsAssetId,
        "apkAssetId" to c.apkAssetId, "manifestSha256" to c.manifestSha256,
        "apkSize" to c.apkSize, "apkSha256" to c.apkSha256, "signerSha256" to c.signerSha256
    )

    fun candidate(m: JsonObject) = Candidate(
        releaseId = m.long("releaseId"),
        tag = m.string("tag"),
        versionName = m.string("versionName"),
        versionCode = m.long("versionCode"),
        minSdk = m.long("minSdk").toInt(),
        manifestAssetId = m.long("manifestAssetId"),
        sumsAssetId = m.long("sumsAssetId"),
        apkAssetId = m.long("apkAssetId"),
        manifestSha256 = m.string("manifestSha256"),
        apkSize = m.long("apkSize"),
        apkSha256 = m.string("apkSha256"),
        signerSha256 = m.string("signerSha256")
    ).also {
        require(ReleaseContract.isSha256(it.apkSha256) && it.apkSize > 0 && it.releaseId > 0)
    }

    fun encode(t: TransferRecord): Map<String, Any?> =
        mapOf("candidate" to encode(t.candidate), "verifiedAt" to t.verifiedAt)

    fun transfer(m: JsonObject) = TransferRecord(
        candidate(m.obj("candidate")),
        if (m.raw("verifiedAt") == null) null else m.long("verifiedAt")
    )

    fun encode(i: InstallRecord): Map<String, Any?> = mapOf(
        "candidate" to encode(i.candidate),
        "sessionId" to i.sessionId.toLong(),
        "nonce" to i.nonce,
        "stage" to i.stage.name,
        "pendingUserAction" to i.pendingUserAction
    )

    fun install(m: JsonObject) = InstallRecord(
        candidate = candidate(m.obj("candidate")),
        sessionId = m.long("sessionId").toInt(),
        nonce = m.string("nonce").also { require(NONCE.matches(it)) },
        stage = InstallStage.valueOf(m.string("stage")),
        pendingUserAction = m.bool("pendingUserAction")
    )
}
