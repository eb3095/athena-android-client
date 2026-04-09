<p align="center">
  <img src="logo.png" alt="Athena Logo" width="200">
</p>

# Athena Android Client

A minimalistic Android voice assistant client for Athena. Speak to the app, and it sends your prompt to the Athena server, displays the markdown-formatted response, and plays back the audio.

## Features

- **Voice Input** - Speak prompts via Android SpeechRecognizer
- **Voice Selection** - Dropdown to select TTS voice (queries server for available voices)
- **Three Input Modes**:
  - **Mic Button** - Speak a prompt, get AI response with optional TTS
  - **Speak Button** - Type text and have it spoken in selected voice
  - **Mimic Button** - Speak text directly, STT converts it, then TTS speaks it back in selected voice
- **Markdown Rendering** - AI responses displayed with formatting
- **Audio Playback** - Auto-plays TTS audio, with replay button
- **Async Job Polling** - Uses job-based API for reliable long-running requests
- **Multi-Server Support** - Continuous health monitoring with automatic failover
- **Connection Status** - Visual indicator with automatic retry
- **Dark Theme** - Material 3 dark theme by default

## Requirements

- Docker (for building)
- Android device or emulator (API 26+)
- ADB (for installing)

## Setup

### 1. Clone the repository

```bash
git clone <repository-url>
cd athena-android-client
```

### 2. Configure API credentials

Create a `local.properties` file in the project root with your Athena server details:

```properties
api.servers=https://your-athena-server.com,http://fallback-server.local
api.token=your-auth-token
```

Multiple servers can be specified as a comma-separated list. The app continuously polls all servers' `/health` endpoints every 5 seconds and selects the first healthy server when making requests.

> **Security Note**: The `local.properties` file is gitignored and should never be committed. The API credentials are baked into the APK at build time. This approach is suitable for personal use only.

### 3. Build the app

```bash
# Build debug APK (uses Docker, no Java required)
make build

# Or build release APK
make release
```

### 4. Install on device

```bash
# Install debug build on connected device
make install

# If upgrade fails due to signature mismatch
make uninstall
make install
```

## Usage

### Input Modes

| Button | Icon | Function |
|--------|------|----------|
| **Mic** | Microphone | Voice-to-prompt: Speak → STT → LLM → TTS → Audio |
| **Speak** | Volume | Text-to-speech: Type text → TTS → Audio |
| **Mimic** | Copy | Voice cloning: Speak → STT → TTS → Audio (no LLM) |

### Voice Selection

- Tap the voice dropdown to see available voices from active TTS agents
- Select "None" for text-only responses (no audio)
- Voice list refreshes each time the dropdown opens

### Workflow

1. Select a voice (or "None" for text-only)
2. **For AI conversation**: Tap the mic button, speak your question
3. **For TTS only**: Type in the text field, tap the speak button
4. **For voice mimicking**: Tap the mimic button, speak what you want repeated
5. Wait for "Thinking..." to complete
6. View markdown response and hear audio playback
7. Tap replay button to hear audio again

## Build Commands

| Command | Description |
|---------|-------------|
| `make build` | Build debug APK using Docker |
| `make debug` | Build debug APK using Docker |
| `make release` | Build release APK using Docker |
| `make install` | Install debug APK on connected device |
| `make uninstall` | Uninstall app from connected device |
| `make clean` | Clean build artifacts |
| `make docker-clean` | Remove Docker build image |
| `make icons` | Regenerate app icons from logo.png |

## Project Structure

```
athena-android-client/
├── app/src/main/
│   ├── java/com/athena/client/
│   │   ├── AthenaApplication.kt     # Application class
│   │   ├── MainActivity.kt          # Single activity
│   │   ├── data/                    # API layer
│   │   │   ├── ApiClient.kt         # Retrofit setup
│   │   │   ├── AthenaApi.kt         # API interface
│   │   │   └── models/              # Request/response models
│   │   ├── audio/                   # Audio playback
│   │   │   ├── AudioPlayer.kt       # WAV playback from base64
│   │   │   └── ByteArrayMediaDataSource.kt
│   │   ├── speech/                  # Voice recognition
│   │   │   └── SpeechRecognizerManager.kt
│   │   ├── ui/                      # Compose UI
│   │   │   ├── MainScreen.kt        # Main screen composable
│   │   │   ├── components/          # UI components
│   │   │   │   ├── MicButton.kt     # Voice prompt button
│   │   │   │   ├── SpeakButton.kt   # TTS-only button
│   │   │   │   ├── MimicButton.kt   # Voice mimic button
│   │   │   │   └── VoiceSelector.kt # Voice dropdown
│   │   │   └── theme/               # App theme
│   │   └── viewmodel/
│   │       └── MainViewModel.kt     # State management
│   └── res/                         # Android resources
├── gradle/                          # Gradle wrapper
├── build.gradle.kts                 # Root build config
├── app/build.gradle.kts             # App build config
└── Makefile                         # Build commands
```

## API Endpoints Used

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/prompt/job` | POST | Submit async prompt job |
| `/api/prompt/job/{id}` | GET | Poll for prompt result |
| `/api/speak/job` | POST | Submit async TTS-only job |
| `/api/speak/job/{id}` | GET | Poll for TTS result |
| `/api/voices` | GET | List available voices |
| `/health` | GET | Server health check |

### Request Format (Prompt)

```json
{
  "prompt": "What's the weather like?",
  "speaker": true,
  "speaker_voice": "selected-voice"
}
```

### Request Format (Speak)

```json
{
  "text": "Hello, this is a test.",
  "speaker_voice": "selected-voice"
}
```

### Response Format

```json
{
  "job_id": "...",
  "status": "completed",
  "response": "The weather is sunny with a high of 75°F.",
  "audio": "<base64-encoded WAV>"
}
```

## Configuration

### Environment Variables (CI/CD)

For CI/CD builds, you can set credentials via environment variables:

```bash
export API_URL="https://your-server.com"
export API_TOKEN="your-token"
```

Then modify `app/build.gradle.kts` to read from environment:

```kotlin
buildConfigField(
    "String", "API_URL",
    "\"${System.getenv("API_URL") ?: localProperties.getProperty("api.url", "")}\""
)
```

### Release Signing

For release builds, configure signing in `app/build.gradle.kts`. **Do not commit credentials to source control** - use `local.properties` or environment variables.

## Troubleshooting

### Voice recognition not working

1. Ensure microphone permission is granted
2. Check that Google Speech Services is installed on the device
3. Try restarting the app

### Connection errors

1. Verify the API URLs in `local.properties`
2. Ensure the device has internet connectivity
3. Check that at least one server is reachable

### Audio not playing

1. Check device volume settings
2. Verify a voice is selected (not "None")
3. Check logcat for AudioPlayer errors

### Job stuck in "Thinking..."

1. Jobs timeout after 30 minutes on the server
2. If no TTS agents are available, job will fail with "No agents available"
3. Restart the app to clear local state

## Credits

App icon generated with [Easy-Peasy.AI](https://easy-peasy.ai/ai-image-generator/images/pegatina-sobre-ai-d0ee6d86-5dec-4a7f-b17d-f011196a078c).

## License

MIT License - See [LICENSE](LICENSE) for details.
