package com.app.basic_app

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.*
import com.google.android.gms.tasks.CancellationToken
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.OnTokenCanceledListener
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class LocationHelper(context: Context) {
    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
    private val locationRequest = CurrentLocationRequest.Builder()
        .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
        .setMaxUpdateAgeMillis(5000)
        .setDurationMillis(10000)
        .build()

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Location = suspendCoroutine { continuation ->
        // First try to get last known location for immediate response
        fusedLocationClient.lastLocation.addOnSuccessListener { lastLocation ->
            if (lastLocation != null) {
                continuation.resume(lastLocation)
            } else {
                // If no last location, request current location
                requestCurrentLocation(continuation)
            }
        }.addOnFailureListener {
            // If last location fails, fallback to current location
            requestCurrentLocation(continuation)
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestCurrentLocation(continuation: kotlin.coroutines.Continuation<Location>) {
        fusedLocationClient.getCurrentLocation(
            locationRequest,
            object : CancellationToken() {
                override fun onCanceledRequested(listener: OnTokenCanceledListener) = CancellationTokenSource().token
                override fun isCancellationRequested() = false
            }
        ).addOnSuccessListener { location ->
            if (location != null) {
                continuation.resume(location)
            } else {
                continuation.resumeWith(Result.failure(Exception("Could not get location")))
            }
        }.addOnFailureListener { e ->
            continuation.resumeWith(Result.failure(e))
        }
    }
} 