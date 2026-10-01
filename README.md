# ⚡ Dua AI — Grok Cyberpunk & Gemini Edition

<p align="center">
  <img src="preview/dua_logo_transparent.png" alt="Dua AI Logo" width="120" />
</p>

<p align="center">
  <strong>Next-Generation High-Intelligence AI Assistant for Android</strong><br>
  Built with Jetpack Compose • Material 3 • Modern Kotlin • Google Gemini & Grok Dual Personality
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-green.svg" alt="Platform" />
  <img src="https://img.shields.io/badge/Language-Kotlin%202.1.0-blue.svg" alt="Kotlin" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-purple.svg" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/Min%20SDK-26%20(Android%208.0)-orange.svg" alt="Min SDK" />
  <img src="https://img.shields.io/badge/Target%20SDK-35%20(Android%2015)-red.svg" alt="Target SDK" />
  <img src="https://img.shields.io/badge/Build-Passing-brightgreen.svg" alt="Build" />
</p>

---

## 📖 Overview

**Dua AI** is a state-of-the-art conversational Android application engineered to deliver the best of two worlds:
1. **Gemini & ChatGPT Precision**: Clean, distraction-free minimalist ergonomics for deep coding, scientific analysis, and structured roadmaps.
2. **Grok Cyberpunk Personality ("Fun Mode 🌶️")**: High-energy, witty, and humorous AI responses for brainstorming, roasts, and outside-the-box thinking.

All wrapped in a bespoke metallic chrome brand identity with native Arabic calligraphy and modern English typography.

---

## 🎯 Implementation Plan — Dua AI (Grok Cyberpunk Edition)

### 📌 Phase 1: Core Architecture & Design System `[COMPLETED]`
- [x] **Edge-to-Edge Jetpack Compose Setup**:
  - `ComponentActivity` with full Android 15 Edge-to-Edge compliance (`WindowInsetsControllerCompat`).
  - Zero deprecated status bar / navigation bar API calls.
- [x] **Dual Theme System**:
  - **Minimalist Mode**: Deep charcoal (`#131314`), clean slate (`#1E1F20`), and pristine white (`#FFFFFF`) with Gemini electric blue & violet sparkle accents.
  - **Cyberpunk OLED Palette**: Pure black (`#000000`), Neon Cyan (`#00F0FF`), Fun Mode Orange (`#FF5500`), and Matrix Green (`#00FF66`).
  - 1-tap real-time theme toggler (Sun / Moon).
- [x] **Brand Asset Pipeline**:
  - Custom high-resolution **Metallic Chrome DUA Logo** processed into an anti-aliased transparent asset (`res/drawable/dua_logo.png`).
  - Integrated into Top Bar, Welcome Screen hero, History Drawer header, and AI message avatars.

### 📌 Phase 2: Conversational Engine & Components `[COMPLETED]`
- [x] **Model Selector Dialog**:
  - Instant switching between specialized intelligence models:
    - ⚡ **Dua 2.0 Flash**: Fast, agile for everyday questions.
    - 🧠 **Dua 1.5 Pro**: Advanced multi-step logic and deep synthesis.
    - ✨ **Dua DeepThink**: Rigorous mathematical reasoning & analysis.
    - 💻 **Dua Code Expert**: Specialized in modern Kotlin & Jetpack Compose.
- [x] **Interactive Welcome Screen**:
  - Hero metallic DUA branding with gradient greeting (*"Hello, there — How can I help you today?"*).
  - Quick prompt cards for Code Generation, Ideation, Concepts, and Roadmaps.
- [x] **Rich Message Stream**:
  - Right-aligned modern user message bubbles.
  - Left-aligned Dua AI cards with structured Markdown headers, bullet points, and code editor blocks.
  - 1-click **Copy Code** button with `"✓ Copied!"` visual feedback.
  - Message feedback toolbar: Like (Thumbs up), Dislike (Thumbs down), Regenerate, and Copy.
  - Animated 3-dot pulsing thinking indicator.
- [x] **Floating Capsule Input Bar**:
  - Auto-expanding text input with attachment (`+`) button, voice mic, and reactive circular send button.
  - ChatGPT/Gemini disclaimer note.
- [x] **History Navigation Drawer**:
  - Slide-out drawer with session search bar, `+ New chat` action button, recent conversation history, and profile footer.

### 📌 Phase 3: Grok Fun Mode & Real AI Backend `[CURRENT / IN PROGRESS]`
- [ ] **Grok "Fun Mode 🌶️" Toggle**:
  - Pill button in the top bar to switch between **Normal (Serious/Analytical)** and **Fun Mode (Witty/Humorous/Roast)**.
  - Neon orange glowing accents and spicy micro-animations when Fun Mode is active.
- [ ] **Google Gemini API Integration**:
  - Add Google Generative AI SDK:
    ```kotlin
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")
    ```
  - Real-time token streaming (`Flow<GenerateContentResponse>`) for instant typewriter response effect.
  - Grok-style system instructions for persona tailoring.
- [ ] **Secure API Key Management**:
  - Inject API keys via `local.properties` / `BuildConfig` (zero credentials committed to Git).

### 📌 Phase 4: Local Storage & Offline History `[PLANNED]`
- [ ] **Room Database Persistence**:
  - Store chat conversations, messages, timestamps, and model selections locally on device.
  - Search conversation history offline with SQLite FTS (Full-Text Search).
- [ ] **Export & Share**:
  - Export chat transcripts as formatted Markdown (`.md`) or PDF.

### 📌 Phase 5: Multimodal & Voice Features `[PLANNED]`
- [ ] **Dua Vision**:
  - Attach images from camera or gallery for visual reasoning, OCR, and code generation from screenshots.
- [ ] **Speech-to-Text (STT) & Text-to-Speech (TTS)**:
  - On-device speech recognition for hands-free voice prompting.
  - Natural audio readout of bot responses.

---

## 🗂️ Project Structure

```
nova/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/dua/ai/
│   │   │   ├── MainActivity.kt           # Edge-to-edge entry point
│   │   │   ├── model/
│   │   │   │   └── ChatMessage.kt        # ChatMessage, ChatSession, Reactions
│   │   │   ├── viewmodel/
│   │   │   │   └── ChatViewModel.kt      # State management, models, streaming
│   │   │   ├── ui/
│   │   │   │   ├── components/
│   │   │   │   │   ├── DuaComponents.kt  # TopBar, Welcome, Cards, CodeBlock, Input
│   │   │   │   │   └── HistoryDrawer.kt  # ChatGPT-style history drawer
│   │   │   │   ├── screens/
│   │   │   │   │   └── ChatScreen.kt     # Master assembled chat screen
│   │   │   │   └── theme/
│   │   │   │       ├── Color.kt          # Minimalist & Cyberpunk color tokens
│   │   │   │       ├── Theme.kt          # Dynamic Dark/Light mode provider
│   │   │   │       └── Type.kt           # Clean typographic scale
│   │   └── res/
│   │       ├── drawable/
│   │       │   └── dua_logo.png          # Official metallic chrome brand logo
│   │       └── values/
│   │           └── strings.xml
│   └── build.gradle.kts
├── preview/
│   ├── index.html                        # 1:1 Interactive browser mockup
│   ├── dua_logo_transparent.png          # High-res preview asset
│   └── emulator_screenshot_updated.png   # Verified live emulator capture
├── gradle.properties                     # JDK 17 & JVM args
├── local.properties                      # Android SDK path
└── README.md
```

---

## 🚀 How to Run

### Option 1: Android Studio (Recommended)
1. Open **Android Studio** (Koala / Ladybug or newer).
2. Choose **Open** and select this directory (`c:\Users\Hp\.antigravity-ide\nova-compose-ui\nova`).
3. Select an emulator (e.g. `Pixel_10a`) or physical Android device.
4. Click **Run ▶️** (`Shift + F10`).

### Option 2: Command Line (Fast Build & Install)
Ensure your emulator or phone is connected (`adb devices`), then run:
```powershell
# Compile and install debug APK onto device
.\gradlew.bat installDebug

# Launch the app
adb shell am start -n com.dua.ai/.MainActivity
```

### Option 3: Instant Interactive Web Simulation
Double-click and open `preview/index.html` in any web browser to test the full UI, Dark/Light mode toggle, prompt suggestions, and simulated AI responses instantly.

---

## 🛠️ Tech Stack & Requirements

- **JDK**: Eclipse Temurin OpenJDK 17 (`jdk-17.0.15+6`)
- **Android Gradle Plugin**: `8.8.0`
- **Gradle**: `8.11.1`
- **Compile SDK**: `35` (Android 15)
- **Min SDK**: `26` (Android 8.0 Oreo)
- **Compose BOM**: `2024.12.01`

---

## 📄 License & Credits

- Developed by **Fareed** ([KRFbrothers](https://github.com/KRFbrothers))
- Repository: [github.com/KRFbrothers/Dua-ai](https://github.com/KRFbrothers/Dua-ai)
