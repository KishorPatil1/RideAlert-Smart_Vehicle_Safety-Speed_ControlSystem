package com.app.basic_app

import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

class GeoapifyService {
    private val apiKey = "9031fce32db743b1a426d7ef72277f66"

    suspend fun getSpeedLimit(point: LatLng): Int? = withContext(Dispatchers.IO) {
        try {
            // Create a second point slightly offset from the first (about 50 meters)
            val secondPoint = createOffsetPoint(point)
            
            val url = URL(
                "https://api.geoapify.com/v1/routing?" +
                "waypoints=${point.latitude},${point.longitude}|${secondPoint.latitude},${secondPoint.longitude}" +
                "&mode=drive&details=instruction_details,route_details" +
                "&apiKey=$apiKey"
            )

            val response = url.readText()
            val jsonResponse = JSONObject(response)
            
            return@withContext extractSpeedLimit(jsonResponse)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun createOffsetPoint(point: LatLng): LatLng {
        // Calculate offset points in different directions to increase chances of finding a road
        val offsets = listOf(
            0.0005 to 0.0, // North
            0.0 to 0.0005, // East
            -0.0005 to 0.0, // South
            0.0 to -0.0005  // West
        )

        return LatLng(
            point.latitude + offsets[0].first,
            point.longitude + offsets[0].second
        )
    }

    private fun extractSpeedLimit(jsonResponse: JSONObject): Int? {
        try {
            val features = jsonResponse.getJSONArray("features")
            if (features.length() > 0) {
                val properties = features.getJSONObject(0).getJSONObject("properties")
                val legs = properties.getJSONArray("legs")
                if (legs.length() > 0) {
                    val leg = legs.getJSONObject(0)
                    
                    // First try to get speed limit from steps
                    val steps = leg.getJSONArray("steps")
                    if (steps.length() > 0) {
                        val step = steps.getJSONObject(0)
                        if (step.has("speed_limit")) {
                            return step.getInt("speed_limit")
                        }
                    }
                    
                    // If no speed limit in steps, try to get from route details
                    if (leg.has("route_details")) {
                        val routeDetails = leg.getJSONObject("route_details")
                        if (routeDetails.has("speed_limit")) {
                            return routeDetails.getInt("speed_limit")
                        }
                    }
                }
            }
            return null
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
} 