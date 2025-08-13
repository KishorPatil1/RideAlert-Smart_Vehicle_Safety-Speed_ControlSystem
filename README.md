# RideAlert: Smart Vehicle Safety & Speed Control System

A comprehensive multi-component vehicle safety system that combines Android mobile application, ESP32/STM32 microcontrollers, and machine learning algorithms for real-time accident prevention and emergency response.

## 🚀 Features

### Android Application (Kotlin/Jetpack Compose)
- **Real-time GPS tracking** with speed limit detection via Geoapify API
- **Google Maps integration** for location visualization
- **Emergency response system** with automated hospital search and SMS alerts
- **Machine learning-based accident risk prediction**
- **Manual override system** with regulatory compliance logging
- **Bluetooth communication** with ESP32 for vehicle control

### ESP32 Microcontroller (C++)
- **Motor speed control** via PWM based on speed limits
- **Hall effect sensor integration** for real-time speed monitoring
- **Bluetooth communication** with Android app
- **LCD display** for real-time status
- **Manual override capability** with safety mechanisms

### STM32 Microcontroller (C++)
- **Crash detection** using MPU6050 accelerometer
- **Two-stage detection algorithm**: free-fall + impact detection
- **I2C communication** with sensors
- **Safety features** with cooldown periods and automatic reset

### Machine Learning Model (Kotlin)
- **Random Forest algorithm** for accident risk prediction
- **Feature engineering** based on weather, road type, time, and traffic density
- **Real-time risk assessment** with 82% R² score
- **28ms average calculation time**

## 📊 Performance Metrics

- **95.2%** Crash Detection Accuracy
- **97.1%** Speed Limit Compliance Rate
- **98.4%** Emergency SMS Delivery Success
- **0.82** R² Score for ML Risk Prediction
- **700ms** End-to-End System Response Time
- **99.2%** Overall System Uptime

## 🏗️ System Architecture

```
┌─────────────────┐    Bluetooth    ┌─────────────────┐    I2C    ┌─────────────────┐
│   Android App   │ ◄─────────────► │      ESP32      │ ◄───────► │      STM32      │
│   (RideAlert)   │                 │   (Speed Ctrl)  │           │ (Crash Detect)  │
└─────────────────┘                 └─────────────────┘           └─────────────────┘
         │                                   │                             │
         │ APIs                              │ PWM                         │ MPU6050
         ▼                                   ▼                             ▼
┌─────────────────┐                 ┌─────────────────┐           ┌─────────────────┐
│   Cloud APIs    │                 │   Motor/LCD     │           │  Accelerometer  │
│ (Maps/Hospital) │                 │   Hall Sensor   │           │   (3-axis)      │
└─────────────────┘                 └─────────────────┘           └─────────────────┘
```

## 🛠️ Technologies Used

- **Android**: Kotlin, Jetpack Compose, Google Maps API, Firebase Firestore
- **Microcontrollers**: ESP32, STM32, C/C++
- **Communication**: Bluetooth, I2C
- **Sensors**: MPU6050 Accelerometer, Hall Effect Sensor
- **APIs**: Geoapify (Speed Limits), GoMaps (Hospital Data)
- **Machine Learning**: Random Forest, Feature Engineering
- **Analysis**: Python, Matplotlib, Pandas

## 📋 Prerequisites

### Android Development
- Android Studio Arctic Fox or later
- Kotlin 2.0.0+
- Target SDK 35 (Android 15)
- Min SDK 24 (Android 7.0)

### Microcontroller Development
- Arduino IDE or PlatformIO
- ESP32 Development Board
- STM32 Development Board
- MPU6050 Accelerometer Module

### Hardware Components
- ESP32 DevKit
- STM32 (with MPU6050)
- Hall Effect Sensor
- Motor (for speed control)
- LCD Display (I2C)
- Connecting wires and breadboard

## 🚀 Getting Started

### 1. Clone the Repository
```bash
git clone https://github.com/yourusername/RideAlert-Smart-Vehicle-Safety-System.git
cd RideAlert-Smart-Vehicle-Safety-System
```

### 2. Android App Setup
1. Open the project in Android Studio
2. Sync Gradle files
3. **Important**: Replace API keys in:
   - `GeoapifyService.kt` - Add your Geoapify API key
   - `HospitalService.kt` - Add your GoMaps API key
   - `AndroidManifest.xml` - Add your Google Maps API key
4. Build and run the app

### 3. ESP32 Setup
1. Open `ESP32/ESP32.ino` in Arduino IDE
2. Install required libraries:
   - BluetoothSerial
   - LiquidCrystal_I2C
3. Upload to ESP32 board

### 4. STM32 Setup
1. Open `STM/STM.ino` in Arduino IDE (with STM32 core)
2. Install required libraries:
   - Adafruit MPU6050
   - Wire
3. Upload to STM32 board

## 📱 Usage

1. **Pair Devices**: Enable Bluetooth and pair Android device with ESP32
2. **Launch App**: Open RideAlert app on Android
3. **Configure Emergency Contacts**: Add emergency contact numbers in Settings
4. **Enable Permissions**: Grant location and SMS permissions
5. **Connect to ESP32**: Tap "Connect to ESP32" button
6. **Monitor**: The system will automatically monitor speed and detect accidents

## 🔧 Configuration

### API Keys (Required)
Before using the app, you must obtain and configure:

1. **Geoapify API Key** (for speed limits)
   - Get from: https://www.geoapify.com/
   - Replace in: `GeoapifyService.kt`

2. **GoMaps API Key** (for hospital data)
   - Get from: https://gomaps.pro/
   - Replace in: `HospitalService.kt`

3. **Google Maps API Key** (for maps)
   - Get from: Google Cloud Console
   - Replace in: `AndroidManifest.xml`

### Emergency Contacts
- Configure in app Settings
- Primary and secondary emergency contacts supported
- SMS alerts sent automatically on crash detection

## 📊 Analysis Scripts

The project includes Python scripts for performance analysis:

- `system_evaluation.py` - Comprehensive performance testing
- `quick_results.py` - Quick results summary
- `simple_table_matplotlib.py` - Visual performance charts
- `generate_results_table.py` - Results table generation

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## ⚠️ Important Notes

- **API Keys**: Remove or replace all API keys before pushing to public repositories
- **Emergency Use**: This is a prototype system. Do not rely solely on this for emergency situations
- **Testing**: Thoroughly test all components before deployment
- **Compliance**: Ensure compliance with local traffic and safety regulations


