package com.app.basic_app

import android.telephony.SmsManager
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

data class Hospital(
    val name: String,
    val address: String,
    val distance: Double,
    val location: LatLng,
    var contactInfo: String? = null,
    var displayPhoneNumber: String? = null
)

class HospitalService {
    private val apiKey = "9031fce32db743b1a426d7ef72277f66"
    private val gomapsApiKey = "AlzaSyq-8VzHYUB9gF_e25au3r199Qi_gBxbZmw"
    private var emergencyNumber: String? = null

    fun updateEmergencyNumber(number: String) {
        emergencyNumber = number
    }

    fun hasEmergencyNumber(): Boolean {
        return !emergencyNumber.isNullOrBlank()
    }

    suspend fun getNearbyHospitals(location: LatLng): List<Hospital> = withContext(Dispatchers.IO) {
        try {
            val url = URL(
                "https://api.geoapify.com/v2/places?" +
                "categories=healthcare.hospital" +
                "&filter=circle:${location.longitude},${location.latitude},5000" +
                "&bias=proximity:${location.longitude},${location.latitude}" +
                "&limit=20" +
                "&apiKey=$apiKey"
            )

            val response = url.readText()
            val jsonResponse = JSONObject(response)
            return@withContext parseHospitals(jsonResponse)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun parseHospitals(jsonResponse: JSONObject): List<Hospital> {
        val hospitals = mutableListOf<Hospital>()
        val features = jsonResponse.getJSONArray("features")
        
        for (i in 0 until features.length()) {
            val feature = features.getJSONObject(i)
            val properties = feature.getJSONObject("properties")
            val geometry = feature.getJSONObject("geometry")
            val coordinates = geometry.getJSONArray("coordinates")
            
            // Try to get phone number from properties
            val phoneNumber = if (properties.has("phone")) {
                properties.getString("phone")
            } else null
            
            val hospital = Hospital(
                name = properties.getString("name"),
                address = properties.getString("formatted"),
                distance = properties.getDouble("distance"),
                location = LatLng(
                    coordinates.getDouble(1),
                    coordinates.getDouble(0)
                ),
                displayPhoneNumber = phoneNumber
            )
            hospitals.add(hospital)
        }
        return hospitals
    }

    suspend fun sendEmergencySMS(location: LatLng, hospitalName: String, phoneNumber: String) {
        if (phoneNumber.isBlank()) return
        
        withContext(Dispatchers.IO) {
            try {
                val mapsLink = "https://www.google.com/maps?q=${location.latitude},${location.longitude}"
                
                val message = "[PROJECT TEST MESSAGE - NO REAL EMERGENCY]\n" +
                             "This is a test message from an emergency response project.\n" +
                             "Nearest Hospital: $hospitalName\n" +
                             "Location: $mapsLink\n" +
                             "THIS IS ONLY A TEST - NO ACTUAL EMERGENCY"
                             
                val smsManager = SmsManager.getDefault()
                val messageParts = smsManager.divideMessage(message)
                smsManager.sendMultipartTextMessage(
                    phoneNumber.replace(" ", ""), // Remove any spaces from the number
                    null,
                    messageParts,
                    null,
                    null
                )
            } catch (e: Exception) {
                e.printStackTrace()
                throw e  // Propagate the error to handle it in the UI
            }
        }
    }

    suspend fun getHospitalContactInfo(hospitalName: String, address: String): String? = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = java.net.URLEncoder.encode("$hospitalName,$address", "UTF-8")
            
            // First request - Text Search to get place_id
            val searchUrl = URL(
                "https://maps.gomaps.pro/maps/api/place/textsearch/json" +
                "?query=$encodedQuery" +
                "&key=$gomapsApiKey"
            )
            
            val searchResponse = searchUrl.readText()
            val searchJson = JSONObject(searchResponse)
            
            if (searchJson.has("results") && searchJson.getJSONArray("results").length() > 0) {
                val placeId = searchJson.getJSONArray("results")
                    .getJSONObject(0)
                    .getString("place_id")
                
                // Second request - Place Details using place_id
                val detailsUrl = URL(
                    "https://maps.gomaps.pro/maps/api/place/details/json" +
                    "?place_id=$placeId" +
                    "&key=$gomapsApiKey"
                )
                
                val detailsResponse = detailsUrl.readText()
                val detailsJson = JSONObject(detailsResponse)
                
                if (detailsJson.getString("status") == "OK" && detailsJson.has("result")) {
                    val result = detailsJson.getJSONObject("result")
                    val contactDetails = StringBuilder()
                    var hasContactInfo = false
                    var phoneNumber: String? = null
                    
                    // Check for formatted phone number first
                    if (result.has("formatted_phone_number")) {
                        phoneNumber = result.getString("formatted_phone_number")
                        contactDetails.append("Phone: $phoneNumber")
                        hasContactInfo = true
                    } 
                    // Fallback to international phone number if formatted is not available
                    else if (result.has("international_phone_number")) {
                        phoneNumber = result.getString("international_phone_number")
                        contactDetails.append("Phone: $phoneNumber")
                        hasContactInfo = true
                    }
                    
                    // Add website if available
                    if (result.has("website")) {
                        if (hasContactInfo) contactDetails.append("\n")
                        contactDetails.append("Website: ${result.getString("website")}")
                        hasContactInfo = true
                    }
                    
                    // Add opening hours if available
                    if (result.has("current_opening_hours")) {
                        val openingHours = result.getJSONObject("current_opening_hours")
                        if (openingHours.has("open_now")) {
                            if (hasContactInfo) contactDetails.append("\n")
                            contactDetails.append(
                                "Open now: ${if (openingHours.getBoolean("open_now")) "Yes" else "No"}"
                            )
                            hasContactInfo = true
                        }
                    }
                    
                    // Store the phone number for SMS if available
                    if (phoneNumber != null) {
                        val cleanPhoneNumber = phoneNumber.replace(Regex("[\\s\\-()]+"), "")
                        return@withContext contactDetails.toString()
                    }
                    
                    return@withContext if (hasContactInfo) {
                        contactDetails.toString()
                    } else {
                        "No contact information available"
                    }
                }
            }
            return@withContext "No contact information available"
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext "Error fetching contact information: ${e.message}"
        }
    }
} 