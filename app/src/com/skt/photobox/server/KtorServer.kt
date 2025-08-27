package com.skt.photobox.server

import com.jiangdg.ausbc.utils.bus.BusKey
import com.jiangdg.ausbc.utils.bus.EventBus
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Helper function outside the Ktor DSL scope to avoid ambiguity and type issues.
private fun sendCaptureCommand() {
    EventBus.with<Unit>(BusKey.KEY_CAPTURE_IMAGE).postMessage(Unit)
}

@Serializable
data class TickerRequest(val text: String)

class KtorServer {

    private var server: ApplicationEngine? = null

    fun start() {
        if (server != null) {
            return
        }

        server = embeddedServer(CIO, port = 8080, module = Application::module).start(wait = false)
    }

    fun stop() {
        server?.stop(1_000, 2_000)
        server = null
    }
}

fun Application.module() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
        })
    }

    routing {
        get("/") {
            call.respondText("Hello, PhotoBox!")
        }

        get("/status") {
            call.respond(mapOf("status" to "running", "camera" to "connected"))
        }

        post("/camera/capture") {
            sendCaptureCommand() // Call the helper function
            call.respond(mapOf("result" to "capture command sent"))
        }

        post("/ticker") {
            val request = call.receive<TickerRequest>()
            EventBus.with<String>(BusKey.KEY_UPDATE_TICKER_TEXT).postMessage(request.text)
            call.respond(mapOf("status" to "ok", "text" to request.text))
        }
    }
} 