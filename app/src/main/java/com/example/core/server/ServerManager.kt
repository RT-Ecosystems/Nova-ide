package com.example.core.server

import com.example.domain.model.ServerState
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Manages the lifecycle of Nova IDE's embedded localhost HTTP server with automatic
 * port fallback (8080..8090), LAN IP detection, and reactive StateFlow updates.
 */
class ServerManager {

    private val mutex = Mutex()
    private var activeServer: LocalWebServer? = null
    private var lastServedDir: File? = null

    private val _serverState = MutableStateFlow(ServerState())
    val serverState: StateFlow<ServerState> = _serverState.asStateFlow()

    suspend fun startServer(projectDir: File): Result<ServerState> = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                require(projectDir.exists() && projectDir.isDirectory) {
                    "Project folder \"${projectDir.name}\" does not exist."
                }

                // Stop any previously running instance cleanly before rebinding
                stopInternal()

                var startedServer: LocalWebServer? = null
                var boundPort = PORT_START
                var lastError: Throwable? = null

                for (candidatePort in PORT_START..PORT_END) {
                    val server = LocalWebServer(projectRootDir = projectDir, port = candidatePort)
                    try {
                        server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                        startedServer = server
                        boundPort = candidatePort
                        break
                    } catch (e: Exception) {
                        lastError = e
                        runCatching { server.stop() }
                    }
                }

                if (startedServer == null) {
                    val msg = "Ports $PORT_START–$PORT_END unavailable: ${lastError?.localizedMessage ?: "Bind failed"}"
                    _serverState.update {
                        it.copy(isRunning = false, errorMessage = msg)
                    }
                    error(msg)
                }

                activeServer = startedServer
                lastServedDir = projectDir

                val localUrl = "http://127.0.0.1:$boundPort"
                val lanIp = detectLanIpv4Address()
                val lanUrl = lanIp?.let { "http://$it:$boundPort" }

                val nextState = ServerState(
                    isRunning = true,
                    activeProjectName = projectDir.name,
                    activeProjectPath = projectDir.canonicalPath,
                    port = boundPort,
                    bindAddress = LocalWebServer.BIND_HOST,
                    localUrl = localUrl,
                    lanUrl = lanUrl,
                    reloadNonce = _serverState.value.reloadNonce + 1L,
                    errorMessage = null
                )
                _serverState.value = nextState
                nextState
            }
        }
    }

    suspend fun stopServer() = withContext(Dispatchers.IO) {
        mutex.withLock {
            stopInternal()
            _serverState.update {
                it.copy(
                    isRunning = false,
                    errorMessage = null
                )
            }
        }
    }

    suspend fun restartServer(): Result<ServerState> {
        val dir = lastServedDir ?: _serverState.value.activeProjectPath?.let { File(it) }
        return if (dir != null && dir.exists()) {
            startServer(dir)
        } else {
            Result.failure(IllegalStateException("No active project directory to restart."))
        }
    }

    fun triggerReload() {
        _serverState.update { current ->
            if (!current.isRunning) current
            else current.copy(reloadNonce = current.reloadNonce + 1L)
        }
    }

    fun stopServerSync() {
        runCatching {
            activeServer?.stop()
            activeServer = null
        }
    }

    private fun stopInternal() {
        runCatching { activeServer?.stop() }
        activeServer = null
    }

    private fun detectLanIpv4Address(): String? {
        return runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            val addresses = mutableListOf<String>()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback || iface.isVirtual) continue
                val inetAddrs = iface.inetAddresses
                while (inetAddrs.hasMoreElements()) {
                    val addr = inetAddrs.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                        val hostAddress = addr.hostAddress ?: continue
                        if (addr.isSiteLocalAddress) {
                            return hostAddress
                        }
                        addresses.add(hostAddress)
                    }
                }
            }
            addresses.firstOrNull()
        }.getOrNull()
    }

    companion object {
        const val PORT_START = 8080
        const val PORT_END = 8090
    }
}
