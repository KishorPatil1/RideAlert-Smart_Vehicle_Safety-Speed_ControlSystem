#include <Wire.h>
#include <Adafruit_MPU6050.h>
#include <Adafruit_Sensor.h>
#include <math.h>

TwoWire Wire1(2, I2C_FAST_MODE); // I2C2 (PB11 SDA, PB10 SCL)
Adafruit_MPU6050 mpu;

#define CRASH_PIN PB15
#define LED_PIN PA8

// Thresholds
#define FREEFALL_THRESHOLD 5.0     // Accel magnitude < this = falling
#define IMPACT_THRESHOLD   14.0    // After falling, spike > this = crash
#define MAX_FALL_TIME      1000    // Max time between fall and impact (ms)
#define COOLDOWN_TIME      1500    // Prevent retriggering (ms)

bool falling = false;
unsigned long fallStartTime = 0;
unsigned long lastCrashTime = 0;
unsigned long crashPinHighTime = 0; // Track when CRASH_PIN was set HIGH

void setup() {
  Serial.begin(115200);
  delay(500);
  Serial.println("Fall + Impact Based Crash Detection");

  Wire1.begin();
  if (!mpu.begin(MPU6050_I2CADDR_DEFAULT, &Wire1)) {
    Serial.println("MPU6050 not found!");
    while (1) delay(10);
  }

  mpu.setAccelerometerRange(MPU6050_RANGE_16_G);
  mpu.setFilterBandwidth(MPU6050_BAND_5_HZ);

  pinMode(CRASH_PIN, OUTPUT);
  digitalWrite(CRASH_PIN, LOW);
  pinMode(LED_PIN, OUTPUT);
  digitalWrite(LED_PIN, LOW);
}

void loop() {
  sensors_event_t a, g, temp;
  mpu.getEvent(&a, &g, &temp);

  float ax = a.acceleration.x;
  float ay = a.acceleration.y;
  float az = a.acceleration.z;
  float accel_mag = sqrt(ax * ax + ay * ay + az * az);

  Serial.print("Accel Mag: ");
  Serial.println(accel_mag);

  unsigned long now = millis();

  // Step 1: Detect free-fall (below gravity)
  if (!falling && accel_mag < FREEFALL_THRESHOLD) {
    falling = true;
    fallStartTime = now;
    Serial.println("Free-fall detected, watching for impact...");
  }

  // Step 2: Detect impact only if falling was detected before
  if (falling && (now - fallStartTime < MAX_FALL_TIME) && accel_mag > IMPACT_THRESHOLD) {
    if (now - lastCrashTime > COOLDOWN_TIME) {
      digitalWrite(CRASH_PIN, HIGH);
      digitalWrite(LED_PIN, HIGH);
      crashPinHighTime = now; // Record the time when pin set HIGH
      lastCrashTime = now;
    }
    falling = false; // reset
  }

  // Step 3: Timeout fall detection if no impact happened
  if (falling && (now - fallStartTime >= MAX_FALL_TIME)) {
    falling = false;
  Serial.println("Fall timeout — no crash.");
  }

  // Step 4: Reset crash pin after 3 seconds
  if (digitalRead(CRASH_PIN) == HIGH && (now - crashPinHighTime >= 3000)) {
    digitalWrite(CRASH_PIN, LOW);
    digitalWrite(LED_PIN, LOW);
  }

  delay(20);
}
