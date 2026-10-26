package local.agenteye.http

import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import local.agenteye.security.PairResult
import local.agenteye.security.PairingCodes
import local.agenteye.security.SessionToken

data class Shot(val jpeg: ByteArray, val capturedAt: Long, val sequence: Long)

/**
 * Only the service supplies production binding/admission.
 * Loopback tests supply their own policy.
 */
class StillServer(
    address: InetAddress,
    port: Int = 8765,
    private val admit: (InetAddress) -> Boolean,
    private val token: SessionToken,
    private val status: () -> String,
    private val capture: () -> CompletableFuture<Shot>,
    private val captureTimeoutMs: Long = 5000,
    private val connectionTimeoutMs: Long = 10000,
    private val intervalMs: Long = 1000,
    private val pairing: PairingCodes? = null
) : AutoCloseable {
    private val listener = ServerSocket(port, 4, address)
    val localPort: Int get() = listener.localPort
    private val stopped = AtomicBoolean(false)
    private val slots = Semaphore(4)
    private val clients = ConcurrentHashMap.newKeySet<Socket>()
    private val pending = ConcurrentHashMap.newKeySet<CompletableFuture<Shot>>()
    private val workers = Executors.newFixedThreadPool(4)
    private val deadlines = Executors.newSingleThreadScheduledExecutor()
    private val captureLock = Any()
    private var busy = false
    private var lastStart = Long.MIN_VALUE
    private val acceptThread = Thread({ acceptLoop() }, "agent-eye-listener").apply { start() }

    private fun acceptLoop() {
        while (!stopped.get()) {
            val socket = try {
                listener.accept()
            } catch (_: Exception) {
                break
            }
            if (!admit(socket.inetAddress) || !slots.tryAcquire()) {
                // Never queue unbounded clients or write to unauthenticated peers here.
                socket.close()
                continue
            }
            clients.add(socket)
            if (stopped.get()) {
                clients.remove(socket)
                socket.close()
                slots.release()
                break
            }
            try {
                val timeout = deadlines.schedule({
                    runCatching { socket.close() }
                }, connectionTimeoutMs, TimeUnit.MILLISECONDS)
                workers.execute {
                    try {
                        handle(socket)
                    } finally {
                        timeout.cancel(false)
                        runCatching { socket.close() }
                        clients.remove(socket)
                        slots.release()
                    }
                }
            } catch (_: Exception) {
                clients.remove(socket)
                socket.close()
                slots.release()
            }
        }
    }

    private fun handle(socket: Socket) {
        try {
            socket.soTimeout = connectionTimeoutMs.toInt()
            val request = HttpRequest.read(socket.getInputStream())
            if (request.method == "POST") {
                pair(socket, request)
                return
            }
            if (!token.accepts(request.authorization)) throw HttpFailure(401)
            when (request.path) {
                "/status" -> respond(
                    socket,
                    200,
                    "application/json",
                    status().toByteArray(Charsets.UTF_8)
                )

                "/shot.jpg" -> {
                    val shot = freshShot()
                    if (shot.jpeg.size !in 4..8 * 1024 * 1024) throw HttpFailure(503)
                    respond(
                        socket,
                        200,
                        "image/jpeg",
                        shot.jpeg,
                        "X-Capture-Timestamp: ${shot.capturedAt}\r\n" +
                            "X-Capture-Sequence: ${shot.sequence}\r\n"
                    )
                }

                else -> throw HttpFailure(404)
            }
        } catch (failure: HttpFailure) {
            runCatching {
                respond(
                    socket,
                    failure.status,
                    "application/json",
                    "{\"error\":\"http_${failure.status}\"}".toByteArray()
                )
            }
        } catch (_: Exception) {
            runCatching {
                respond(
                    socket,
                    503,
                    "application/json",
                    "{\"error\":\"unavailable\"}".toByteArray()
                )
            }
        }
    }

    /** POST /pair {"code":"123456"} -> {"token":"..."}; unauthenticated by design. */
    private fun pair(socket: Socket, request: HttpRequest) {
        val gate = pairing ?: throw HttpFailure(404)
        val code = PAIR_BODY.matchEntire(request.body)?.groupValues?.get(1)
            ?: throw HttpFailure(400)
        val client = socket.inetAddress.hostAddress.orEmpty()
        when (gate.attempt(client, code)) {
            PairResult.PAIRED -> {
                val secret = token.display()
                if (secret.isEmpty()) throw HttpFailure(503)
                respond(socket, 200, "application/json", "{\"token\":\"$secret\"}".toByteArray())
            }

            PairResult.REJECTED -> throw HttpFailure(403)

            PairResult.RATE_LIMITED -> throw HttpFailure(429)
        }
    }

    private fun freshShot(): Shot {
        synchronized(captureLock) {
            val now = System.nanoTime()
            if (busy ||
                (
                    lastStart != Long.MIN_VALUE &&
                        now - lastStart < TimeUnit.MILLISECONDS.toNanos(intervalMs)
                    )
            ) {
                throw HttpFailure(429)
            }
            if (stopped.get()) throw HttpFailure(503)
            busy = true
            lastStart = now
        }
        var future: CompletableFuture<Shot>? = null
        try {
            future = capture()
            pending.add(future)
            if (stopped.get()) future.cancel(true)
            return future.get(captureTimeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: Exception) {
            throw HttpFailure(503)
        } finally {
            future?.let {
                pending.remove(it)
                it.cancel(true)
            }
            synchronized(captureLock) { busy = false }
        }
    }

    private fun respond(
        socket: Socket,
        code: Int,
        mime: String,
        body: ByteArray,
        extra: String = ""
    ) {
        val reason = when (code) {
            200 -> "OK"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            429 -> "Too Many Requests"
            else -> "Service Unavailable"
        }
        val hints = when (code) {
            401 -> "WWW-Authenticate: Bearer\r\n"
            405 -> "Allow: GET\r\n"
            429 -> "Retry-After: 1\r\n"
            else -> ""
        }
        val header = "HTTP/1.1 $code $reason\r\nContent-Type: $mime\r\n" +
            "Content-Length: ${body.size}\r\nCache-Control: no-store\r\n" +
            "Connection: close\r\n$hints$extra\r\n"
        socket.getOutputStream().apply {
            write(header.toByteArray(Charsets.US_ASCII))
            write(body)
            flush()
        }
    }

    private companion object {
        val PAIR_BODY = Regex("""\{\s*"code"\s*:\s*"([0-9]{6})"\s*\}""")
    }

    override fun close() {
        if (!stopped.compareAndSet(false, true)) return
        listener.close()
        clients.forEach { runCatching { it.close() } }
        pending.forEach { it.cancel(true) }
        workers.shutdownNow()
        deadlines.shutdownNow()
        acceptThread.join(1000)
    }
}
