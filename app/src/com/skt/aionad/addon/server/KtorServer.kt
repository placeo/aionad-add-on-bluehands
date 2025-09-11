package com.skt.aionad.addon.server

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.skt.aionad.addon.MainActivity
import com.skt.aionad.addon.AddOnBluehands

@Serializable
data class TickerRequest(val text: String)

class KtorServer(private val context: android.content.Context) {

    private var server: ApplicationEngine? = null
    private var addOnBluehands: AddOnBluehands? = null

    fun start() {
        if (server != null) {
            return
        }

        // MainActivity로부터 AddOnBluehands 인스턴스 가져오기
        if (context is MainActivity) {
            addOnBluehands = context.getAddOnBluehands()
        }

        server = embeddedServer(CIO, port = 8080) { 
            module(addOnBluehands) 
        }.start(wait = false)
    }

    fun stop() {
        server?.stop(1_000, 2_000)
        server = null
    }
}

fun Application.module(addOnBluehands: AddOnBluehands?) {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }
    
    // CORS 설정 추가
    install(CORS) {
        anyHost() // 모든 호스트 허용 (개발용)
        allowHeader(HttpHeaders.ContentType)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
    }

    routing {
        get("/") {
            call.respondText("Hello, aionad-add-on-bluehands!")
        }

        get("/status") {
            call.respond(mapOf("status" to "running", "camera" to "connected"))
        }

        post("/camera/capture") {
            // TODO: Implement camera capture logic
            call.respond(mapOf("result" to "capture command sent"))
        }

        post("/ticker") {
            val request = call.receive<TickerRequest>()
            // TODO: Implement ticker update logic
            call.respond(mapOf("status" to "ok", "text" to request.text))
        }

        // Car Repair Info API Routes
        route("/api/car-repair") {
            
            // GET /api/car-repair - 전체 목록 조회
            get {
                try {
                    if (addOnBluehands == null) {
                        call.respond(
                            HttpStatusCode.ServiceUnavailable,
                            ApiResponse<Unit>(success = false, message = "AddOnBluehands not available")
                        )
                        return@get
                    }
                    val carRepairInfos = addOnBluehands.getAllCarRepairInfo()
                    val responses = carRepairInfos.map { CarRepairResponse.fromCarRepairInfo(it) }
                    call.respond(
                        HttpStatusCode.OK,
                        ApiResponse(success = true, message = "Success", data = responses)
                    )
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<Unit>(success = false, message = "Error: ${e.message}")
                    )
                }
            }

            // GET /api/car-repair/{plate} - 특정 차량 조회
            get("/{plate}") {
                try {
                    val plate = call.parameters["plate"] ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Unit>(success = false, message = "License plate number is required")
                    )

                    if (addOnBluehands == null) {
                        call.respond(
                            HttpStatusCode.ServiceUnavailable,
                            ApiResponse<Unit>(success = false, message = "AddOnBluehands not available")
                        )
                        return@get
                    }
                    val carRepairInfo = addOnBluehands.getCarRepairInfoByPlate(plate)
                    if (carRepairInfo != null) {
                        val response = CarRepairResponse.fromCarRepairInfo(carRepairInfo)
                        call.respond(
                            HttpStatusCode.OK,
                            ApiResponse(success = true, message = "Success", data = response)
                        )
                    } else {
                        call.respond(
                            HttpStatusCode.NotFound,
                            ApiResponse<Unit>(success = false, message = "Car repair info not found")
                        )
                    }
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<Unit>(success = false, message = "Error: ${e.message}")
                    )
                }
            }

            // POST /api/car-repair - 새 차량 정보 추가
            post {
                try {
                    val request = call.receive<CarRepairRequest>()
                    val carRepairInfo = request.toCarRepairInfo()
                    
                    if (addOnBluehands == null) {
                        call.respond(
                            HttpStatusCode.ServiceUnavailable,
                            ApiResponse<Unit>(success = false, message = "AddOnBluehands not available")
                        )
                        return@post
                    }
                    val success = addOnBluehands.addCarRepairInfoApi(carRepairInfo)
                    if (success) {
                        val response = CarRepairResponse.fromCarRepairInfo(carRepairInfo)
                        call.respond(
                            HttpStatusCode.Created,
                            ApiResponse(success = true, message = "Car repair info created successfully", data = response)
                        )
                    } else {
                        call.respond(
                            HttpStatusCode.Conflict,
                            ApiResponse<Unit>(success = false, message = "Car repair info already exists or invalid data")
                        )
                    }
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Unit>(success = false, message = "Invalid request: ${e.message}")
                    )
                }
            }

            // PUT /api/car-repair/{plate} - 차량 정보 수정
            put("/{plate}") {
                try {
                    val plate = call.parameters["plate"] ?: return@put call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Unit>(success = false, message = "License plate number is required")
                    )

                    val request = call.receive<CarRepairRequest>()
                    val carRepairInfo = request.toCarRepairInfo()
                    
                    if (addOnBluehands == null) {
                        call.respond(
                            HttpStatusCode.ServiceUnavailable,
                            ApiResponse<Unit>(success = false, message = "AddOnBluehands not available")
                        )
                        return@put
                    }
                    val success = addOnBluehands.updateCarRepairInfoApi(plate, carRepairInfo)
                    if (success) {
                        val response = CarRepairResponse.fromCarRepairInfo(carRepairInfo)
                        call.respond(
                            HttpStatusCode.OK,
                            ApiResponse(success = true, message = "Car repair info updated successfully", data = response)
                        )
                    } else {
                        call.respond(
                            HttpStatusCode.NotFound,
                            ApiResponse<Unit>(success = false, message = "Car repair info not found")
                        )
                    }
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Unit>(success = false, message = "Invalid request: ${e.message}")
                    )
                }
            }

            // DELETE /api/car-repair/{plate} - 차량 정보 삭제
            delete("/{plate}") {
                try {
                    val plate = call.parameters["plate"] ?: return@delete call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Unit>(success = false, message = "License plate number is required")
                    )

                    if (addOnBluehands == null) {
                        call.respond(
                            HttpStatusCode.ServiceUnavailable,
                            ApiResponse<Unit>(success = false, message = "AddOnBluehands not available")
                        )
                        return@delete
                    }
                    val success = addOnBluehands.deleteCarRepairInfoApi(plate)
                    if (success) {
                        call.respond(
                            HttpStatusCode.OK,
                            ApiResponse<Unit>(success = true, message = "Car repair info deleted successfully")
                        )
                    } else {
                        call.respond(
                            HttpStatusCode.NotFound,
                            ApiResponse<Unit>(success = false, message = "Car repair info not found")
                        )
                    }
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<Unit>(success = false, message = "Error: ${e.message}")
                    )
                }
            }
        }
    }
} 