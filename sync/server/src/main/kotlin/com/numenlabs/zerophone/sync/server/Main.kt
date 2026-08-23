package com.numenlabs.zerophone.sync.server

import com.numenlabs.zerophone.sync.server.pairing.PairingStore
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.io.path.createDirectories

/**
 * Runnable entry point of the sync server (packaged as a fat-jar by the
 * `shadowJar` task in `sync/server/build.gradle.kts`):
 *
 *   java -jar server.jar [--port=8080] [--host=0.0.0.0] [--data=DIR]
 *   java -jar server.jar --issue-code [--ttl=600000] [--data=DIR]
 *
 * The listen port also honours the `PORT` environment variable
 * (a `--port` argument wins over it; 8080 is the fallback).
 *
 * `--issue-code` mints one pairing shortcode valid for its TTL (10
 * minutes by default), prints it and exits — the operator shares it
 * with the desktop out-of-band; shortcodes are never handed out over
 * HTTP, so no anonymous client can mint its own pairing.
 */
fun main(args: Array<String>) {
    val options = args.associate {
        val key = it.substringBefore('=')
        val value = it.substringAfter('=', "")
        key to value
    }
    val dataDir = Path.of(
        options["--data"]
            ?: System.getProperty("user.home")?.let { "$it/.zerophone-sync" }
            ?: "zerophone-sync-data",
    )
    dataDir.createDirectories()

    if (options.containsKey("--issue-code")) {
        val ttlMillis = options["--ttl"]?.toLongOrNull() ?: PairingStore.DEFAULT_TTL_MILLIS
        val store = PairingStore(dataDir.resolve(PairingStore.FILE_NAME))
        val code = runBlocking { store.issue(ttlMillis = ttlMillis) }
        println("Pairing shortcode: $code (valid for ${ttlMillis / 60000} min)")
        println("Data directory: ${dataDir.toAbsolutePath()}")
        return
    }

    val port = options["--port"]?.toIntOrNull()
        ?: System.getenv("PORT")?.toIntOrNull()
        ?: 8080
    val host = options["--host"] ?: "0.0.0.0"
    println("ZeroPhone sync server listening on $host:$port (data: ${dataDir.toAbsolutePath()})")
    embeddedServer(CIO, host = host, port = port) {
        syncServerModule(dataDir)
    }.start(wait = true)
}
