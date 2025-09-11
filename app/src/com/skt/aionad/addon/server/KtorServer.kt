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
import timber.log.Timber

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
                    Timber.d("🌐 HTTP GET /api/car-repair - Retrieving all car repair info")
                    if (addOnBluehands == null) {
                        Timber.e("❌ AddOnBluehands not available for GET request")
                        call.respond(
                            HttpStatusCode.ServiceUnavailable,
                            ApiResponse<Unit>(success = false, message = "AddOnBluehands not available")
                        )
                        return@get
                    }
                    val carRepairInfos = addOnBluehands.getAllCarRepairInfo()
                    val responses = carRepairInfos.map { CarRepairResponse.fromCarRepairInfo(it) }
                    Timber.i("✅ HTTP GET /api/car-repair - Retrieved %d items", carRepairInfos.size)
                    call.respond(
                        HttpStatusCode.OK,
                        ApiResponse(success = true, message = "Success", data = responses)
                    )
                } catch (e: Exception) {
                    Timber.e(e, "❌ HTTP GET /api/car-repair - Error retrieving list")
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<Unit>(success = false, message = "Error: ${e.message}")
                    )
                }
            }

            // GET /api/car-repair/{plate} - 특정 차량 조회
            get("/{plate}") {
                val plate = call.parameters["plate"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<Unit>(success = false, message = "License plate number is required")
                )
                
                try {
                    Timber.d("🌐 HTTP GET /api/car-repair/%s - Retrieving specific car info", plate)
                    if (addOnBluehands == null) {
                        Timber.e("❌ AddOnBluehands not available for GET request")
                        call.respond(
                            HttpStatusCode.ServiceUnavailable,
                            ApiResponse<Unit>(success = false, message = "AddOnBluehands not available")
                        )
                        return@get
                    }
                    val carRepairInfo = addOnBluehands.getCarRepairInfoByPlate(plate)
                    if (carRepairInfo != null) {
                        val response = CarRepairResponse.fromCarRepairInfo(carRepairInfo)
                        Timber.i("✅ HTTP GET /api/car-repair/%s - Found: %s %s", plate, carRepairInfo.getCarModel(), carRepairInfo.getRepairStatus().name)
                        call.respond(
                            HttpStatusCode.OK,
                            ApiResponse(success = true, message = "Success", data = response)
                        )
                    } else {
                        Timber.w("⚠️ HTTP GET /api/car-repair/%s - Not found", plate)
                        call.respond(
                            HttpStatusCode.NotFound,
                            ApiResponse<Unit>(success = false, message = "Car repair info not found")
                        )
                    }
                } catch (e: Exception) {
                    Timber.e(e, "❌ HTTP GET /api/car-repair/%s - Error retrieving info", plate)
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
                    Timber.i("🌐 HTTP POST /api/car-repair - Received request: licensePlate=%s, carModel=%s, status=%s, estimatedFinishTime=%s", 
                            request.licensePlateNumber ?: "null", 
                            request.carModel, 
                            request.repairStatus,
                            request.estimatedFinishTime ?: "null")
                    
                    val carRepairInfo = request.toCarRepairInfo()
                    Timber.d("🔄 Converted to CarRepairInfo: licensePlate=%s, estimatedFinishTime=%s", 
                            carRepairInfo.getLicensePlateNumber(), 
                            carRepairInfo.getEstimatedFinishTime() ?: "null")
                    
                    if (addOnBluehands == null) {
                        Timber.e("❌ AddOnBluehands not available for POST request")
                        call.respond(
                            HttpStatusCode.ServiceUnavailable,
                            ApiResponse<Unit>(success = false, message = "AddOnBluehands not available")
                        )
                        return@post
                    }
                    val success = addOnBluehands.addCarRepairInfoApi(carRepairInfo)
                    if (success) {
                        val response = CarRepairResponse.fromCarRepairInfo(carRepairInfo)
                        Timber.i("✅ HTTP POST /api/car-repair - Successfully added: %s %s", 
                                carRepairInfo.getLicensePlateNumber(), carRepairInfo.getCarModel())
                        call.respond(
                            HttpStatusCode.Created,
                            ApiResponse(success = true, message = "Car repair info created successfully", data = response)
                        )
                    } else {
                        Timber.w("⚠️ HTTP POST /api/car-repair - Failed to add (conflict or invalid): %s", 
                                carRepairInfo.getLicensePlateNumber())
                        call.respond(
                            HttpStatusCode.Conflict,
                            ApiResponse<Unit>(success = false, message = "Car repair info already exists or invalid data")
                        )
                    }
                } catch (e: Exception) {
                    Timber.e(e, "❌ HTTP POST /api/car-repair - Error processing request")
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Unit>(success = false, message = "Invalid request: ${e.message}")
                    )
                }
            }

            // PUT /api/car-repair/{plate} - 차량 정보 수정
            put("/{plate}") {
                val plate = call.parameters["plate"] ?: return@put call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<Unit>(success = false, message = "License plate number is required")
                )
                
                try {
                    val request = call.receive<CarRepairRequest>()
                    Timber.i("🌐 HTTP PUT /api/car-repair/%s - Received request: carModel=%s, status=%s, estimatedFinishTime=%s", 
                            plate, request.carModel, request.repairStatus, request.estimatedFinishTime ?: "null")
                    
                    val carRepairInfo = request.toCarRepairInfo()
                    Timber.d("🔄 Converted to CarRepairInfo for update: estimatedFinishTime=%s", 
                            carRepairInfo.getEstimatedFinishTime() ?: "null")
                    
                    if (addOnBluehands == null) {
                        Timber.e("❌ AddOnBluehands not available for PUT request")
                        call.respond(
                            HttpStatusCode.ServiceUnavailable,
                            ApiResponse<Unit>(success = false, message = "AddOnBluehands not available")
                        )
                        return@put
                    }
                    val success = addOnBluehands.updateCarRepairInfoApi(plate, carRepairInfo)
                    if (success) {
                        val response = CarRepairResponse.fromCarRepairInfo(carRepairInfo)
                        Timber.i("✅ HTTP PUT /api/car-repair/%s - Successfully updated: %s", plate, carRepairInfo.getCarModel())
                        call.respond(
                            HttpStatusCode.OK,
                            ApiResponse(success = true, message = "Car repair info updated successfully", data = response)
                        )
                    } else {
                        Timber.w("⚠️ HTTP PUT /api/car-repair/%s - Not found for update", plate)
                        call.respond(
                            HttpStatusCode.NotFound,
                            ApiResponse<Unit>(success = false, message = "Car repair info not found")
                        )
                    }
                } catch (e: Exception) {
                    Timber.e(e, "❌ HTTP PUT /api/car-repair/%s - Error processing request", plate)
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Unit>(success = false, message = "Invalid request: ${e.message}")
                    )
                }
            }

            // DELETE /api/car-repair/{plate} - 차량 정보 삭제
            delete("/{plate}") {
                val plate = call.parameters["plate"] ?: return@delete call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<Unit>(success = false, message = "License plate number is required")
                )
                
                try {
                    Timber.i("🌐 HTTP DELETE /api/car-repair/%s - Deleting car info", plate)
                    if (addOnBluehands == null) {
                        Timber.e("❌ AddOnBluehands not available for DELETE request")
                        call.respond(
                            HttpStatusCode.ServiceUnavailable,
                            ApiResponse<Unit>(success = false, message = "AddOnBluehands not available")
                        )
                        return@delete
                    }
                    val success = addOnBluehands.deleteCarRepairInfoApi(plate)
                    if (success) {
                        Timber.i("✅ HTTP DELETE /api/car-repair/%s - Successfully deleted", plate)
                        call.respond(
                            HttpStatusCode.OK,
                            ApiResponse<Unit>(success = true, message = "Car repair info deleted successfully")
                        )
                    } else {
                        Timber.w("⚠️ HTTP DELETE /api/car-repair/%s - Not found for deletion", plate)
                        call.respond(
                            HttpStatusCode.NotFound,
                            ApiResponse<Unit>(success = false, message = "Car repair info not found")
                        )
                    }
                } catch (e: Exception) {
                    Timber.e(e, "❌ HTTP DELETE /api/car-repair/%s - Error processing deletion", plate)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<Unit>(success = false, message = "Error: ${e.message}")
                    )
                }
            }
        }
    }
} 