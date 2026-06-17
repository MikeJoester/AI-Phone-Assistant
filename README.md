# 🤖 On-Device AI Chat Assistant

An absolutely private, fully on-device AI Assistant built with React Native and Native Android Kotlin. 

This application uses Android's Accessibility Services to invisibly read incoming chat messages (such as Discord) and process them through a local LLM running entirely on your phone's hardware. It features a custom floating UI overlay, a native Android file picker for dynamic model swapping, and automatic clipboard injection to seamlessly paste generated replies back into your chat.

Because the AI runs 100% locally on your device's CPU/GPU via `llama.rn`, your private chat logs are never sent to a cloud server or external API.

## ⚙️ Software Requirements

To build and run this project locally, your development environment must meet the following requirements:

*   **Node.js**: v22.11.0 or newer
*   **Java Development Kit (JDK)**: JDK 17
*   **Android SDK**: API Level 33 or higher (Targeting Android 13+)
*   **Android NDK**: Required for compiling the C++ `llama.cpp` backend (auto-installed via Gradle).
*   **React Native CLI**: v0.86.0
*   **LLM Model**: A downloaded `.gguf` format AI model (must fit within your phone's available RAM).

## 🧠 Tested AI Model Specifications

The application was built, tested, and optimized using the following specific model configuration:

*   **Model Name**: `gemma-4-E2B-it-Q4_K_M.gguf`
*   **Base Family**: Gemma 4 E2B
*   **Format**: GGUF v3
*   **Quantization**: `Q4_K_M` (quantized via [Unsloth.ai](https://unsloth.ai))
*   **Mode**: Text-only language model
*   **Tensor Count**: 601
*   **Size**: ~3.11 GB
*   **Chat Template**: Embedded Gemma 4 template

## 🚀 Full Installation Guide

Follow these steps to clone the repository, install dependencies, and run the app on an Android emulator or physical device.

### 1. Clone the Repository
```bash
git clone https://github.com/MikeJoester/AI-Phone-Assistant.git
cd AI-Phone-Assistant
```

### 2. Install Dependencies
```bash
npm install
```

### 3. Prepare the AI Model
1. Download an instruction-tuned model in `.gguf` format (e.g., from HuggingFace).
2. Transfer the `.gguf` file to your Android phone or emulator. The easiest location is directly in your device's root `Downloads` folder.

### 4. Build and Run the App
Ensure your Android device is plugged in via USB (with USB Debugging enabled) or your Android Emulator is running.

**To run the debug development server:**
```bash
npx react-native start
npx react-native run-android
```

**To build a standalone Release APK (Recommended for performance):**
```bash
cd android
./gradlew assembleRelease
```
*You can then install the resulting `app-release.apk` located in `android/app/build/outputs/apk/release/`.*

### 5. Post-Installation Device Setup
When you launch the application for the first time, you must grant it three critical Android permissions for it to function:
1.  **All Files Access / Storage**: Required to locate and load the massive 3GB+ `.gguf` model file from your file system.
2.  **Display Over Other Apps**: Required to render the floating `🤖 Draft AI` trigger button and the Accept/Reject overlay over your chat apps.
3.  **Accessibility Services**: You must manually navigate to your phone's Accessibility Settings and enable the service for the AI Assistant. This allows the app to read incoming text and perform the `PASTE` gesture to inject replies.