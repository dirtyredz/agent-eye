package local.agenteye.service

import local.agenteye.json.StrictJson

/**
 * The `GET /status` wire body (docs/AGENT-EYE.md): exactly seven fields, explicit nulls, and
 * [SessionError.code] as the only place the error becomes a wire value.
 */
object SessionStatusJson {
    const val PROTOCOL_VERSION = 1

    fun render(snapshot: CaptureSnapshot): String = StrictJson.write(
        linkedMapOf(
            "protocolVersion" to PROTOCOL_VERSION,
            "sessionReady" to (snapshot.running && snapshot.address != null),
            "cameraReady" to snapshot.cameraReady,
            "boundAddress" to snapshot.address,
            "uptimeMs" to snapshot.uptimeMs,
            "lastCaptureTime" to snapshot.lastCaptureTime,
            "error" to snapshot.error?.code
        )
    )
}
