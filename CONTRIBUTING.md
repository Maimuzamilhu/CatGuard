# Contributing to CatGuard

Thank you for your interest in improving CatGuard! We welcome contributions ranging from bug fixes, documentation improvements, audio mastering, to machine learning performance tuning.

## Development Setup

### Prerequisites
- **JDK 17** (Temurin, Zulu, or Android Studio bundled JDK)
- **Android Studio** Jellyfish | 2023.3.1 or newer
- **Android SDK Platform 35** and Build Tools

### Getting the Code
```bash
git clone https://github.com/Maimuzamilhu/CatGuard.git
cd CatGuard
```

### Building the Project
You can build the debug APK directly via the Gradle wrapper without configuring any keystore:

```bash
# On Linux / macOS
./gradlew assembleDebug

# On Windows PowerShell
.\gradlew.bat assembleDebug
```

The output APK will be located at `app/build/outputs/apk/debug/app-debug.apk`.

### Running Tests
```bash
# Unit tests
./gradlew testDebugUnitTest

# Lint check
./gradlew lintDebug
```

---

## Architectural Principles

When submitting code changes, keep these core principles in mind:

1. **Strictly Offline**: Never add the `INTERNET` permission or include third-party network SDKs (analytics, ad networks, tracking, or remote error reporting).
2. **Thermal & Battery Awareness**: Computer vision inference is throttled (~3 analyses per second) to prevent thermal degradation and battery swelling on 24/7 plugged-in phones. Keep inference efficient.
3. **Animal Welfare First**: Deterrents are designed to startle and redirect cats safely (using realistic dog barks or benign noises), never to harm animals or humans.

---

## Submitting Pull Requests

1. **Fork the repository** and create a feature branch (`git checkout -b feature/awesome-addition`).
2. Ensure your changes compile cleanly with `./gradlew assembleDebug`.
3. Follow idiomatic Kotlin and Jetpack Compose practices.
4. Commit with descriptive messages.
5. Push to your fork and submit a Pull Request against the `main` branch.
