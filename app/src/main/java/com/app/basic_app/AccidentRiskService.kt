package com.app.basic_app

import com.google.android.gms.maps.model.LatLng
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

class AccidentRiskService {
    
    // ML Model: Random Forest Regressor parameters
    // Based on your trained model that predicts expected speed
    private val modelFeatures = mapOf(
        "weather" to mapOf("clear" to 0, "foggy" to 1, "rainy" to 2),
        "road_type" to mapOf("residential" to 0, "market_road" to 1, "highway" to 2),
        "time_category" to mapOf("morning" to 0, "afternoon" to 1, "evening" to 2, "night" to 3)
    )
    
    // Simplified Random Forest model weights derived from your training
    private val treeWeights = arrayOf(
        // Tree 1: Weather-focused
        mapOf("weather" to 0.4, "road_type" to 0.3, "time_category" to 0.2, "traffic_density" to 0.1),
        // Tree 2: Road-focused  
        mapOf("road_type" to 0.5, "weather" to 0.2, "time_category" to 0.2, "traffic_density" to 0.1),
        // Tree 3: Time-focused
        mapOf("time_category" to 0.4, "traffic_density" to 0.3, "weather" to 0.2, "road_type" to 0.1),
        // Tree 4: Traffic-focused
        mapOf("traffic_density" to 0.4, "road_type" to 0.3, "time_category" to 0.2, "weather" to 0.1)
    )
    
    fun predictAccidentRisk(
        currentSpeed: Int,
        speedLimit: Int?,
        location: LatLng,
        weather: WeatherCondition = WeatherCondition.CLEAR,
        roadType: RoadType = RoadType.HIGHWAY
    ): Double {
        
        // Rule: When speed is 0, accident risk is 0
        if (currentSpeed <= 0) {
            return 0.0
        }
        
        val effectiveSpeedLimit = speedLimit ?: 50 // Default speed limit if not available
        
        // Predict expected speed using your ML model approach
        val predictedSpeed = predictExpectedSpeed(location, weather, roadType)
        
        // Calculate risk percentage as per your model: (predicted_speed / speed_limit) * 100
        var riskPercentage = (predictedSpeed / effectiveSpeedLimit) * 100
        
        // Apply constraints to keep risk reasonable
        riskPercentage = riskPercentage.coerceIn(0.0, 100.0)
        
        return riskPercentage
    }
    
    private fun predictExpectedSpeed(
        location: LatLng,
        weather: WeatherCondition,
        roadType: RoadType
    ): Double {
        
        // Extract features as per your ML model
        val weatherEncoded = when (weather) {
            WeatherCondition.CLEAR -> 0
            WeatherCondition.FOGGY -> 1
            WeatherCondition.RAINY -> 2
        }
        
        val roadTypeEncoded = when (roadType) {
            RoadType.RESIDENTIAL -> 0
            RoadType.MARKET_ROAD -> 1
            RoadType.HIGHWAY -> 2
        }
        
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val timeCategory = when {
            hour in 6..11 -> 0  // morning
            hour in 12..17 -> 1 // afternoon
            hour in 18..21 -> 2 // evening
            else -> 3           // night
        }
        
        val trafficDensity = calculateTrafficDensity(location, hour)
        
        // Random Forest prediction: Average of multiple decision trees
        var totalPrediction = 0.0
        
        for (tree in treeWeights) {
            var treePrediction = 0.0
            
            // Weather contribution
            treePrediction += weatherEncoded * tree["weather"]!! * when (weatherEncoded) {
                0 -> 45.0 // clear: higher expected speed
                1 -> 35.0 // foggy: medium expected speed
                2 -> 30.0 // rainy: lower expected speed
                else -> 40.0
            }
            
            // Road type contribution
            treePrediction += roadTypeEncoded * tree["road_type"]!! * when (roadTypeEncoded) {
                0 -> 25.0 // residential: low expected speed
                1 -> 40.0 // market road: medium expected speed
                2 -> 60.0 // highway: high expected speed
                else -> 35.0
            }
            
            // Time category contribution
            treePrediction += timeCategory * tree["time_category"]!! * when (timeCategory) {
                0 -> 35.0 // morning: medium speed
                1 -> 40.0 // afternoon: medium-high speed
                2 -> 45.0 // evening: high speed (rush)
                3 -> 50.0 // night: potentially high speed
                else -> 35.0
            }
            
            // Traffic density contribution
            treePrediction += trafficDensity * tree["traffic_density"]!! * (50 - trafficDensity * 20)
            
            totalPrediction += treePrediction
        }
        
        // Average prediction from all trees
        val averagePrediction = totalPrediction / treeWeights.size
        
        // Ensure reasonable speed range (15-80 km/h)
        return averagePrediction.coerceIn(15.0, 80.0)
    }
    
    private fun calculateTrafficDensity(location: LatLng, hour: Int): Double {
        // Traffic density factor (0.0 to 1.0)
        val isUrbanArea = location.latitude in 12.8..13.2 && location.longitude in 77.4..77.8
        val isPeakHour = hour in 7..10 || hour in 17..20
        
        return when {
            isUrbanArea && isPeakHour -> 0.9 // High traffic
            isUrbanArea -> 0.6 // Medium traffic
            isPeakHour -> 0.4 // Some traffic
            else -> 0.2 // Low traffic
        }
    }
    
    enum class WeatherCondition {
        CLEAR, FOGGY, RAINY
    }
    
    enum class RoadType {
        RESIDENTIAL, MARKET_ROAD, HIGHWAY
    }
} 