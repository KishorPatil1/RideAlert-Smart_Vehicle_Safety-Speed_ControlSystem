#include <Arduino.h>
#include "BluetoothSerial.h"
#include <LiquidCrystal_I2C.h>

// Pin definitions
#define HALL_PIN GPIO_NUM_34    // Pin of Hall effect sensor to track speed
#define POT_PIN GPIO_NUM_4      // Potentiometer input pin (D4)
#define MOTOR_PIN GPIO_NUM_15   // Motor control output pin (D5)
#define CRASH_PIN GPIO_NUM_13    // Pin to check for voltage (trigger pin)

// Motor and speed calculation constants
#define WHEEL_DIAMETER_CM 7.0   // Wheel diameter in cm
#define WHEEL_CIRCUMFERENCE_M (WHEEL_DIAMETER_CM * 3.14159 / 100.0)  // Wheel circumference in meters
#define MAGNETS_COUNT 2         // Number of magnets on the wheel
#define MAX_SPEED_LIMIT 150     // Maximum speed limit in km/h for trottle control

// Variables for speed calculation
volatile unsigned long pulseCount = 0;
volatile unsigned long lastPulseTime = 0;
volatile unsigned long timeBetweenPulses = 0;
volatile bool newPulseReceived = false;
unsigned long lastSpeedCalcTime = 0;
volatile float currentRPM = 0.0;
volatile float currentKMPH = 0.0;
bool lastHallState = false;
int motorPWM = 0;  // Variable to store motor PWM value
bool updatedisplay = false;  // Flag to indicate if display needs update

bool crashDetected = false;  // Flag for crash detection
int currentSpeedLimit = 150;  // Default max speed limit (150 kmph)

// Manual Override Variables
bool manualOverrideActive = false;  // Flag to track manual override status
unsigned long overrideStartTime = 0;  // Track when override was activated

int oldTrottle;
int oldcurrentRPM;
int oldcurrentKMPH;
int oldcurrentSpeedLimit;

BluetoothSerial SerialBT;  // Create a Bluetooth Serial object
LiquidCrystal_I2C lcd(0x27, 16, 2); // Initialize LCD with I2C address 0x27, 16 columns and 2 rows

// Interrupt Service Routine for Hall effect sensor
void IRAM_ATTR hallSensorISR() {
    unsigned long currentTime = micros();
    
    // Only store the time difference, don't do calculations in ISR
    if (lastPulseTime > 0) {
        timeBetweenPulses = currentTime - lastPulseTime;
        newPulseReceived = true;
    }
    
    lastPulseTime = currentTime;
    pulseCount++;
}

// Interrupt Service Routine for Crash detection sensor
void IRAM_ATTR crashDetectionISR() {
    crashDetected = true;  // Set crash detected flag
}

void lcdinit() {
    lcd.init();
    lcd.backlight();  // Turn on the backlight
    lcd.setCursor(0, 0);
    lcd.print("Initializing...");
    delay(1000);  // Wait for LCD to initialize
    lcd.clear();

    lcd.setCursor(0, 0);    lcd.print("T:    "); // Clear previous value
    lcd.setCursor(2, 0);    lcd.print("0");    lcd.print("%");
    lcd.setCursor(8, 0);    lcd.print("R:       ");  //Clear previous value
    lcd.setCursor(10, 0);   lcd.print("0");
    lcd.setCursor(8, 1);    lcd.print("C:       "); // Clear previous value
    lcd.setCursor(10, 1);   lcd.print("0");    lcd.print("km");
    lcd.setCursor(0, 1);    lcd.print("L:      "); // Clear previous value
    lcd.setCursor(2, 1);    lcd.print("0");   lcd.print("km");
}

void setup() {
    Serial.begin(115200);
    delay(1000); // Allow serial to initialize
    
    Serial.println("===============================================");
    Serial.println("ESP32 Vehicle Control System Starting... ");
    Serial.println("===============================================");
    
    SerialBT.begin("ESP32_BT");  // Bluetooth device name
    Serial.println("Bluetooth initialized as 'ESP32_BT'");

    // LCD initialization
    lcdinit();
    Serial.println(" LCD display initialized");

    pinMode(HALL_PIN, INPUT_PULLUP);  // Set Hall effect sensor pin as input with pullup
    pinMode(POT_PIN, INPUT);          // Set potentiometer pin as input
    pinMode(CRASH_PIN, INPUT_PULLUP); // Set crash detection pin as input with pullup
    Serial.println(" GPIO pins configured");

    // Attach interrupt to Hall effect sensor
    attachInterrupt(digitalPinToInterrupt(HALL_PIN), hallSensorISR, CHANGE);
    attachInterrupt(digitalPinToInterrupt(CRASH_PIN), crashDetectionISR, RISING);
    Serial.println(" Interrupt handlers attached");

    lastSpeedCalcTime = millis();
    
    Serial.println("===============================================");
    Serial.println(" System Ready! Features enabled:");
    Serial.println("    Bluetooth communication (speed data & commands)");
    Serial.println("    Real-time speed monitoring & transmission");
    Serial.println("    Crash detection with emergency alerts");
    Serial.println("    Motor speed control with override capability");
    Serial.println("    Comprehensive message logging");
    Serial.println("===============================================");
    Serial.println("Waiting for Android app connection...");
    Serial.println("   Device Name: ESP32_BT");
    Serial.println("   Speed updates: Every 500ms");
    Serial.println("   Status reports: Every 2 seconds");
    Serial.println("===============================================");
}

void calculateSpeed() {
    // Check if we have a new pulse to process
    if (newPulseReceived) {
        newPulseReceived = false;
        
        // Only calculate if time difference is reasonable (avoid noise)
        if (timeBetweenPulses > 1000) { // Minimum 1ms between pulses
            // Calculate RPM from time between pulses
            // Time for one revolution = timeBetweenPulses * MAGNETS_COUNT
            float timePerRevolution = (timeBetweenPulses * MAGNETS_COUNT) / 1000000.0; // Convert to seconds
            
            if (timePerRevolution > 0) {
                currentRPM = 60.0 / timePerRevolution; // RPM = 60 / time_per_revolution_in_seconds
                
                // Calculate speed in km/h
                currentKMPH = ((currentRPM * WHEEL_CIRCUMFERENCE_M * 60.0) / 1000.0) * 2;
            }
        }
    }
    
    // Check if no pulses for more than 2 seconds, assume stopped
    unsigned long currentTime = millis();
    if (currentTime - (lastPulseTime / 1000) > 2000) {
        currentRPM = 0.0;
        currentKMPH = 0.0;
    }
}

void controlMotor() {
    // Read potentiometer value (0-4095 for 12-bit ADC)
    int potValue = analogRead(POT_PIN);
    
    // Map potentiometer value to desired speed (0-MAX_SPEED_LIMIT km/h)
    float desiredSpeed = map(potValue, 0, 4095, 0, MAX_SPEED_LIMIT);
    
    // Manual Override Logic
    if (manualOverrideActive) {
        // When override is active, ignore speed limit and use full potentiometer range
        // Motor can run at full speed based on potentiometer
        motorPWM = (int)((desiredSpeed/MAX_SPEED_LIMIT) * 255);
    } else {
        // Normal operation - ensure desired speed doesn't exceed speed limit
        if (desiredSpeed > currentSpeedLimit) {
            desiredSpeed = currentSpeedLimit;
        }
        
        // Calculate PWM based on speed limit-constrained desired speed
        motorPWM = (int)((desiredSpeed/(float)MAX_SPEED_LIMIT) * 255);
        
        // Additional safety check: if current speed exceeds speed limit, reduce motor power immediately
        if (currentKMPH > currentSpeedLimit) {
            // Reduce motor power aggressively when over speed limit (no tolerance)
            float overspeedRatio = currentKMPH / currentSpeedLimit;
            motorPWM = motorPWM / (overspeedRatio * 2.0); // More aggressive reduction
        }
    }
    
    // Ensure motor speed doesn't go below 0 or above 255
    if (motorPWM < 0) motorPWM = 0;
    if (motorPWM > 255) motorPWM = 255;
    
    // Set motor speed using PWM
    analogWrite(MOTOR_PIN, motorPWM);
}

void sendDataToSerial() {
    static unsigned long lastPrintTime = 0;
    static unsigned long lastSpeedSendTime = 0;
    unsigned long currentTime = millis();
    
    // Send current speed to Android app every 500ms for accident risk prediction
    if (currentTime - lastSpeedSendTime >= 500) {
        String speedMessage = "SPEED:" + String((int)currentKMPH);
        SerialBT.println(speedMessage);
        Serial.println("========== BLUETOOTH MESSAGE SENT ==========");
        Serial.print("Sent to Android App: '");
        Serial.print(speedMessage);
        Serial.println("'");
        Serial.printf("Current Vehicle Speed: %.2f km/h\n", currentKMPH);
        Serial.println("============================================");
        lastSpeedSendTime = currentTime;
    }
    
    // Send detailed data to serial monitor every 2 seconds
    if (currentTime - lastPrintTime >= 2000) {
        int potValue = analogRead(POT_PIN);
        float desiredSpeed = map(potValue, 0, 4095, 0, MAX_SPEED_LIMIT);
        
        float motorSpeedPercent = (motorPWM / 255.0) * 100.0;
        
        // Print to Serial Monitor
        Serial.println("=============== VEHICLE STATUS ===============");
        Serial.printf("Potentiometer: %d (%.1f%%)\n", potValue, (potValue/4095.0)*100);
        Serial.printf("Desired Speed: %.2f km/h\n", desiredSpeed);
        Serial.printf("Current Speed: %.2f km/h (sent to app)\n", currentKMPH);
        Serial.printf("Motor PWM: %d/255 (%.1f%%)\n", motorPWM, motorSpeedPercent);
        Serial.printf("RPM: %.2f\n", currentRPM);
        Serial.printf("Speed Limit: %d km/h\n", currentSpeedLimit);
        Serial.printf("Manual Override: %s\n", manualOverrideActive ? "ACTIVE" : "INACTIVE");
        if (manualOverrideActive) {
            unsigned long overrideDuration = (currentTime - overrideStartTime) / 1000; // Convert to seconds
            Serial.printf("Override Duration: %lu seconds\n", overrideDuration);
        }
        Serial.printf("Pulse Count: %lu\n", pulseCount);
        Serial.println("===============================================");
        
        lastPrintTime = currentTime;
    }
}

void bluetooth(){
    // Check if the app sends data
    if (SerialBT.available()) {
        String receivedData = SerialBT.readStringUntil('\n');
        receivedData.trim(); // Remove any whitespace or newline characters
        
        // Enhanced logging for all received messages
        Serial.println("========== BLUETOOTH DATA RECEIVED ==========");
        Serial.print("Received from Android App: '");
        Serial.print(receivedData);
        Serial.println("'");
        Serial.printf("Message Length: %d characters\n", receivedData.length());
        Serial.printf("Timestamp: %lu ms\n", millis());
        Serial.println("==============================================");

        // Robust comparison for manual override commands
        if (receivedData.equals("2")) {
            manualOverrideActive = true;
            overrideStartTime = millis();
            Serial.println(">>> COMMAND PROCESSED: Manual Override ACTIVATED");
            
            String response = "Manual Override: ON";
            SerialBT.println(response);
            Serial.println("========== BLUETOOTH RESPONSE SENT ==========");
            Serial.print("Sent to Android App: '");
            Serial.print(response);
            Serial.println("'");
            Serial.println("==============================================");
            
            // Update LCD to show override status
            lcd.clear();
            lcd.setCursor(0, 0);
            lcd.print("OVERRIDE ACTIVE");
            lcd.setCursor(0, 1);
            lcd.print("Speed Limit: OFF");
            updatedisplay = true;
            
        } else if (receivedData.equals("3")) {
            manualOverrideActive = false;
            Serial.println(">>> COMMAND PROCESSED: Manual Override DEACTIVATED");
            
            String response = "Manual Override: OFF";
            SerialBT.println(response);
            Serial.println("========== BLUETOOTH RESPONSE SENT ==========");
            Serial.print("Sent to Android App: '");
            Serial.print(response);
            Serial.println("'");
            Serial.println("==============================================");
            
            // Update LCD to show normal status
            lcd.clear();
            lcd.setCursor(0, 0);
            lcd.print("Normal Mode");
            lcd.setCursor(0, 1);
            lcd.print("Speed Limit: ON");
            updatedisplay = true;
            // Immediately apply the last speed limit again
            analogWrite(MOTOR_PIN, (int)((currentSpeedLimit/(float)MAX_SPEED_LIMIT) * 255));
            
        } else {
            // Try to convert received string to integer (speed limit)
            int newSpeedLimit = receivedData.toInt();
            // Validate speed limit (only process if it's a valid number)
            if (newSpeedLimit > 0 && newSpeedLimit <= MAX_SPEED_LIMIT) {
                currentSpeedLimit = newSpeedLimit;
                Serial.printf(">>> COMMAND PROCESSED: Speed Limit Updated to %d km/h\n", currentSpeedLimit);
                
                // Send acknowledgment back to Android app
                String response = "Speed Limit Received: " + receivedData + " km/h";
                SerialBT.println(response);
                Serial.println("========== BLUETOOTH RESPONSE SENT ==========");
                Serial.print("Sent to Android App: '");
                Serial.print(response);
                Serial.println("'");
                Serial.println("==============================================");
                
                // Update display if not in override mode
                if (!manualOverrideActive) {
                    updatedisplay = true;
                }
            } else {
                Serial.printf(">>> COMMAND IGNORED: Invalid or unrecognized command '%s'\n", receivedData.c_str());
                Serial.printf("    Expected: '2' (Override ON), '3' (Override OFF), or valid speed limit (1-%d)\n", MAX_SPEED_LIMIT);
            }
        }
    }
}

void crashSend(){
    if(crashDetected){
        Serial.println("CRASH DETECTION TRIGGERED ");
        Serial.printf("Crash detected at: %lu ms\n", millis());
        Serial.printf("Current Speed: %.2f km/h\n", currentKMPH);
        Serial.printf("Current RPM: %.2f\n", currentRPM);
        Serial.println("Sending crash alerts to Android app...");
        Serial.println("===============================================");
        
        // Send crash detection signal multiple times to ensure delivery
        for (int i = 0; i < 10; i++) {
            String crashMessage = "1";
            SerialBT.println(crashMessage);
            
            Serial.println("========== CRASH ALERT SENT ==========");
            Serial.printf("Attempt %d/10: Sent '%s' to Android App\n", i+1, crashMessage.c_str());
            Serial.printf("Timestamp: %lu ms\n", millis());
            Serial.println("======================================");
            
            delay(200); // 200ms between sends, total 2 seconds
        }
        
        Serial.println("All crash alerts sent to Android app! ");
        Serial.println("Motor stopped for safety.");
        Serial.println("===============================================");
        
        // Update LCD display
        lcd.clear();
        lcd.setCursor(0, 0);
        lcd.print("CRASH DETECTED!");
        lcd.setCursor(0, 1);
        lcd.print("ALERTS SENT!");
        updatedisplay = true; // Set flag to update display
        
        // Reset crash detection flag
        crashDetected = false;
        
        // Stop the motor for safety
        analogWrite(MOTOR_PIN, 0);
        Serial.println(">>> SAFETY: Motor stopped due to crash detection");
    }
}

void lcdUpdate() {
    static unsigned long lastPrintTime = 0;
    unsigned long currentTime = millis();
    int potValue = analogRead(POT_PIN);
    float Trottle = map(potValue, 0, 4095, 0, 100);

    // Always display throttle, RPM, current speed, and speed limit
    lcd.setCursor(0, 0);
    lcd.print("T:");
    lcd.setCursor(2, 0);
    lcd.print((int)Trottle);
    lcd.print("% R:");
    lcd.print((int)currentRPM);
    lcd.print("   "); // Clear any leftover chars

    lcd.setCursor(0, 1);
    lcd.print("S:");
    lcd.print((int)currentKMPH);
    lcd.print("km L:");
    lcd.print((int)currentSpeedLimit);
    lcd.print("km   "); // Clear any leftover chars

    // Update every 1 second
    if (currentTime - lastPrintTime >= 1000) {
        lastPrintTime = currentTime;
    }
}

void loop() {
    bluetooth();
    calculateSpeed();
    controlMotor();
    sendDataToSerial();
    crashSend();
    lcdUpdate();
    
    // Small delay to prevent overwhelming the system
    delay(10);
}