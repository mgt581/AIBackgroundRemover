# AI Background Remover

An Android application for AI-powered background removal from photos.

## Project Status

[![Android CI](https://github.com/mgt581/AIBackgroundRemover/workflows/Android%20CI/badge.svg)](https://github.com/mgt581/AIBackgroundRemover/actions)

## Overview

AI Background Remover is a mobile application that uses artificial intelligence to automatically remove backgrounds from images. The app is built for Android using Kotlin and Jetpack Compose.

### Features

- AI-powered background removal
- Gallery integration
- User authentication with Firebase
- Google Sign-in support
- Settings and customization options

## Technical Specifications

- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 36
- **Language**: Kotlin
- **Build System**: Gradle with Kotlin DSL
- **Architecture**: Android SDK with Jetpack Compose

## Building the Project

### Prerequisites

- Android Studio (latest stable version)
- JDK 17 or higher
- Android SDK

### Build Instructions

1. Clone the repository:
   ```bash
   git clone https://github.com/mgt581/AIBackgroundRemover.git
   cd AIBackgroundRemover
   ```

2. Open the project in Android Studio

3. Build the project:
   ```bash
   ./gradlew build
   ```

4. Run tests:
   ```bash
   ./gradlew test
   ```

## Git Workflow

### Preventing Push Errors

This project uses GitHub Actions to automatically validate builds and prevent common git issues:

1. **Always pull before push**: Ensure your local branch is up-to-date
   ```bash
   git pull origin main
   ```

2. **Resolve conflicts locally**: If you encounter merge conflicts, resolve them before pushing
   ```bash
   git fetch origin
   git merge origin/main
   # Resolve any conflicts
   git commit
   ```

3. **Use feature branches**: Create feature branches for development
   ```bash
   git checkout -b feature/your-feature-name
   ```

4. **Keep branches synchronized**: Regularly sync with the main branch
   ```bash
   git fetch origin
   git rebase origin/main
   ```

### CI/CD Pipeline

The project includes automated workflows:

- **Android CI**: Runs on push to main/develop branches
  - Builds the application
  - Runs unit tests
  - Uploads build reports on failure

- **PR Checks**: Runs on pull requests
  - Validates merge conflicts
  - Checks if PR is up-to-date
  - Runs lint checks
  - Builds debug APK
  - Posts status comments

## Firebase Configuration

The app uses Firebase for authentication and other services. The `google-services.json` file is included in the repository for the project configuration.

### Setting Up Firebase Authentication

To enable sign-in functionality in debug builds:

1. **Firebase Console Setup**:
   - Go to the [Firebase Console](https://console.firebase.google.com/)
   - Select the project: `pwa-ai-photo-studio-pro`
   - Enable Email/Password authentication in Authentication > Sign-in methods
   - Enable Google Sign-In in Authentication > Sign-in methods

2. **App Check Debug Token (Required for Development)**:
   
   When running the app in debug mode, you **must** register your debug token with Firebase:
   
   a. Run the app in debug mode
   
   b. Check Logcat for a message like:
   ```
   D/DebugAppCheckProvider: Enter this debug secret into the allow list in the Firebase Console: XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX
   ```
   
   c. Copy the debug token and add it to Firebase:
   - Go to Firebase Console > Project Settings > App Check
   - Click "Manage debug tokens"
   - Add your debug token
   
   **Without this step, all authentication requests will fail in debug builds.**

3. **Google Sign-In SHA-1 Certificate**:
   
   The SHA-1 certificate hash in `google-services.json` must match your signing keystore:
   
   ```bash
   # Get your debug keystore SHA-1
   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
   ```
   
   If it doesn't match, add your SHA-1 to Firebase Console > Project Settings > Your apps.

### Troubleshooting Authentication Issues

If sign-in is not working:

1. **Check Logcat** for detailed error messages (search for `LoginActivity` or `AIApplication`)
2. **Verify App Check debug token** is registered (see step 2 above)
3. **Confirm authentication methods** are enabled in Firebase Console
4. **Validate SHA-1 certificate** matches your keystore
5. **Check network connectivity** - Firebase requires internet access

## Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Commit Message Guidelines

- Use clear, descriptive commit messages
- Start with a verb in present tense (e.g., "Add", "Fix", "Update")
- Keep the first line under 72 characters
- Add detailed description if necessary

## Troubleshooting

### Common Git Issues

**Error: "cannot lock ref 'refs/heads/main'"**

This error occurs when the remote branch has changed since you last fetched. To fix:

```bash
# Fetch the latest changes
git fetch origin

# If on main branch, pull and rebase
git pull --rebase origin main

# If on feature branch, rebase on latest main
git rebase origin/main

# After resolving any conflicts, push
git push origin your-branch-name
```

**Error: "failed to push some refs"**

This usually means the remote has commits you don't have locally:

```bash
# Fetch and merge
git fetch origin
git merge origin/main

# Or fetch and rebase (cleaner history)
git fetch origin
git rebase origin/main
```

## License

[Add your license information here]

## Contact

[Add contact information here]
