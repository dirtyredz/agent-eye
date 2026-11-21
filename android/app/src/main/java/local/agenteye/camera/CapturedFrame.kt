package local.agenteye.camera

data class CapturedFrame(val jpeg: ByteArray, val capturedAt: Long, val sequence: Long)
