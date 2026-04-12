<p align="center">
  <img src="logo.png" alt="Athena Logo" width="200">
</p>

# Athena Android Client

A minimalistic Android voice assistant client for Athena. Speak to the app, and it sends your prompt to the Athena server, displays the markdown-formatted response, and plays back the audio.

## Features

- **Conversations** - Multi-turn AI conversations with context persistence
- **Council Mode** - Multi-advisor AI consultation with multiple perspectives synthesized into one response
- **Personalities** - Select AI personality per conversation (pirate, nerdy, professional, etc.) or create custom ones
- **Transcripts** - Convert spoken or typed text to audio in selected voice
- **Persistent Storage** - All conversations, councils, and transcripts saved locally (Room database)
- **Job Recovery** - Automatically resumes interrupted requests when app restarts
- **Voice Input** - Speak prompts via Android SpeechRecognizer
- **Voice Selection** - Dropdown to select TTS voice (queries server for available voices)
- **Streaming Mode** - Sentence-by-sentence audio playback for faster perceived response
- **Mute Streaming** - Stop audio playback mid-stream while job completes in background
- **AI Text Formatting** - Spoken prompts are cleaned up with proper punctuation
- **Smart Titles** - AI-generated titles for conversations based on initial prompt
- **Navigation Drawer** - Slide-out menu to manage and switch between conversations
- **Markdown Rendering** - AI responses displayed with formatting
- **Audio Playback** - Auto-plays TTS audio, with replay button
- **Settings Menu** - Configure defaults, streaming mode, API credentials, and preferences
- **Runtime Configuration** - API key and server URLs configurable in settings (no rebuild required)
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

**Option A: Build-time configuration (optional)**

Create a `local.properties` file in the project root:

```properties
api.servers=https://your-athena-server.com,http://fallback-server.local
api.token=your-auth-token
```

**Option B: Runtime configuration**

Leave `local.properties` empty or skip it entirely. Configure API credentials in the app's Settings screen after installation.

Multiple servers can be specified as a comma-separated list. The app continuously polls all servers' `/health` endpoints every 5 seconds and selects the first healthy server when making requests.

> **Note**: Runtime configuration takes precedence over build-time configuration. The `local.properties` file is gitignored and should never be committed.

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

### Streaming Mode

- Tap the settings icon (gear) in the top right to open settings
- Enable "Streaming Mode" to receive and play audio sentence-by-sentence
- With streaming enabled, audio begins playing as soon as the first sentence is ready
- Download/share button appears only after all sentences have completed

### Getting Started

1. **Launch the app** - You'll see the home screen with two options
2. **Start a Conversation** - Tap "New Conversation" to chat with the AI
3. **Start a Transcript** - Tap "New Transcript" to convert text to speech

### Conversations

- Tap the mic button and speak to send a message
- Your spoken text is automatically cleaned up (punctuation, grammar)
- The AI responds with context from previous messages in the conversation
- First message generates an AI title for the conversation
- Select a voice to hear responses spoken aloud

### Transcripts

- Type text using the Speak button, or speak it using the Mimic button
- Text is converted to audio in the selected voice
- Great for hearing how text sounds in different voices

### Council Mode

- Multiple AI advisors discuss your question from different perspectives
- Each council member provides their viewpoint, then reviews others' responses
- An Advisor synthesizes all perspectives into a unified response
- Tap "View details" on any council response to see individual member contributions
- Configure user traits and goals in Settings for personalized advice
- Select which council members participate per session

### Navigation

- **Home screen** - Only shown on fresh launch
- **Swipe right** - Opens the navigation drawer from any screen
- **Navigation drawer** - Create new conversations/transcripts, switch between existing ones
- **Delete** - Tap X next to any item to delete, or "Delete All" at the bottom

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
│   │   ├── AthenaApplication.kt     # Application class with DB init
│   │   ├── MainActivity.kt          # Single activity
│   │   ├── data/                    # Data layer
│   │   │   ├── ApiClient.kt         # Retrofit setup
│   │   │   ├── AthenaApi.kt         # API interface
│   │   │   ├── models/              # Request/response models
│   │   │   ├── PersonalityRepository.kt  # Personality management
│   │   │   └── local/               # Room database
│   │   │       ├── AppDatabase.kt
│   │   │       ├── ConversationDao.kt
│   │   │       ├── ConversationEntity.kt
│   │   │       ├── ConversationRepository.kt
│   │   │       ├── MessageEntity.kt
│   │   │       ├── PersonalityDao.kt
│   │   │       └── PersonalityEntity.kt
│   │   ├── audio/                   # Audio playback
│   │   │   └── AudioPlayer.kt
│   │   ├── speech/                  # Voice recognition
│   │   │   └── SpeechRecognizerManager.kt
│   │   ├── ui/                      # Compose UI
│   │   │   ├── HomeScreen.kt        # Launch screen
│   │   │   ├── ConversationScreen.kt # AI conversation
│   │   │   ├── TranscriptScreen.kt  # TTS transcripts
│   │   │   ├── navigation/          # Navigation
│   │   │   │   ├── AthenaNavHost.kt
│   │   │   │   └── NavRoutes.kt
│   │   │   ├── components/          # UI components
│   │   │   │   ├── AppNavigationDrawer.kt
│   │   │   │   ├── MicButton.kt
│   │   │   │   ├── SpeakButton.kt
│   │   │   │   ├── MimicButton.kt
│   │   │   │   ├── VoiceSelector.kt
│   │   │   │   └── PersonalitySelector.kt
│   │   │   └── theme/               # App theme
│   │   └── viewmodel/
│   │       ├── AppViewModel.kt      # Global state
│   │       ├── ConversationViewModel.kt
│   │       └── TranscriptViewModel.kt
│   └── res/                         # Android resources
├── gradle/                          # Gradle wrapper
├── build.gradle.kts                 # Root build config
├── app/build.gradle.kts             # App build config
└── Makefile                         # Build commands
```

## API Endpoints Used

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/conversation/job` | POST | Submit async conversation job with history |
| `/api/conversation/job/{id}` | GET | Poll for conversation result |
| `/api/conversation/stream/job` | POST | Submit streaming conversation job |
| `/api/conversation/stream/job/{id}` | GET | Poll for streaming conversation result |
| `/api/council/job` | POST | Submit async council job |
| `/api/council/job/{id}` | GET | Poll for council result |
| `/api/council/stream/job` | POST | Submit streaming council job |
| `/api/council/stream/job/{id}` | GET | Poll for streaming council result |
| `/api/council/members` | GET | List available council members |
| `/api/speak/job` | POST | Submit async TTS-only job |
| `/api/speak/job/{id}` | GET | Poll for TTS result |
| `/api/format/text` | POST | Clean up STT text (punctuation, grammar) |
| `/api/summarize` | POST | Generate short title from text |
| `/api/voices` | GET | List available voices |
| `/api/personalities` | GET | List available personalities |
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
