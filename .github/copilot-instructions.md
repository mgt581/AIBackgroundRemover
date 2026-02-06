# AI Background Remover - GitHub Copilot Instructions

## Project Overview

AI Background Remover is an Android mobile application that uses artificial intelligence to automatically remove backgrounds from images. The app provides a seamless user experience with Firebase authentication and Google Sign-in integration.

## Tech Stack

- **Platform**: Android
- **Language**: Kotlin
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 36
- **Build System**: Gradle with Kotlin DSL
- **UI Framework**: Jetpack Compose
- **Authentication**: Firebase Auth with Google Sign-in
- **Backend Services**: Firebase (App Check, Authentication)
- **Image Loading**: Glide
- **Testing**: JUnit, Espresso

## Coding Conventions

### Kotlin Style
- Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful, descriptive variable and function names
- Prefer `val` over `var` when possible
- Use data classes for simple data holders
- Leverage Kotlin extension functions for cleaner code
- Use null-safety features (`?.`, `?:`, `!!` sparingly)

### Android Best Practices
- Follow Material Design guidelines
- Use Jetpack Compose for all UI components
- Implement proper lifecycle awareness
- Use ViewModel for UI-related data
- Handle configuration changes appropriately
- Implement proper error handling and logging

### Code Structure
- Keep functions focused and concise (single responsibility)
- Add comments only for complex logic or non-obvious decisions
- Use proper access modifiers (private, internal, public)
- Group related functions together
- Organize imports properly (remove unused imports)

## Architecture Patterns

### Compose UI
- Use `@Composable` functions for UI components
- Follow declarative UI patterns
- Use `remember` and `rememberSaveable` appropriately
- Handle state with `mutableStateOf` or ViewModel
- Use proper modifiers for layout and styling
- Leverage Material3 components

### Firebase Integration
- Initialize Firebase in Application class
- Use Firebase App Check for security
- Handle authentication state changes
- Implement proper error handling for Firebase operations
- Never commit Firebase debug tokens or credentials

## Security Practices

### Authentication
- Always validate Firebase authentication state
- Use Firebase App Check for API protection
- Never hardcode credentials or API keys
- Store sensitive data using secure methods (KeyStore, EncryptedSharedPreferences)
- Implement proper session management

### App Check
- Register debug tokens in Firebase Console during development
- Never commit debug tokens to repository
- Validate App Check initialization before making API calls
- Handle App Check failures gracefully

### ProGuard/R8
- Keep ProGuard rules up-to-date in `proguard-rules.pro`
- Test release builds thoroughly
- Ensure Firebase and Google Play Services classes are not obfuscated incorrectly

## Testing Guidelines

### Unit Tests
- Write unit tests for business logic
- Use JUnit for unit testing
- Mock Firebase dependencies appropriately
- Test edge cases and error conditions
- Aim for meaningful test coverage

### Instrumented Tests
- Use Espresso for UI testing
- Test critical user flows (login, gallery, settings)
- Test on multiple API levels when possible
- Use AndroidJUnitRunner as test runner

### Running Tests
```bash
# Run unit tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest

# Run lint checks
./gradlew lintDebug
```

## Build and Deployment

### Build Commands
```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Clean and build
./gradlew clean build
```

### Gradle Configuration
- Use Kotlin DSL for Gradle files (`build.gradle.kts`)
- Keep dependencies up-to-date but test thoroughly
- Use version catalogs (`libs.versions.toml`) for dependency management
- Configure build types appropriately (debug/release)

### Release Process
- Version code and name are in `app/build.gradle.kts`
- Release builds use ProGuard/R8 for code optimization
- Debug symbols are stripped but kept for Play Console
- Test release builds before deployment

## Git Workflow

### Branch Strategy
- `main`: Stable production code
- `develop`: Integration branch (if used)
- `feature/*`: New features
- `fix/*`: Bug fixes

### Commit Messages
- Use present tense ("Add feature" not "Added feature")
- Keep first line under 50 characters (subject line)
- Wrap body text at 72 characters
- Be specific and descriptive
- Reference issues when applicable (e.g., "Fix #123: Bug description")

### Before Committing
- Run lint checks: `./gradlew lintDebug`
- Run tests: `./gradlew test`
- Ensure code builds successfully
- Remove any debugging code or console logs

### Pull Request Guidelines
- Ensure branch is up-to-date with main
- All tests pass
- Lint checks pass
- Add description of changes
- Reference related issues
- Include screenshots for UI changes

## CI/CD Pipeline

### GitHub Actions Workflows
- **Android CI**: Runs on main/develop branches
  - Builds the application
  - Runs unit tests
  - Uploads build reports on failure

- **PR Checks**: Runs on pull requests
  - Validates merge conflicts
  - Checks if PR is up-to-date
  - Runs lint checks
  - Builds debug APK
  - Posts status comments

### Handling CI Failures
- Check logs in GitHub Actions
- Fix issues locally before pushing
- Re-run failed jobs if needed
- Don't merge PRs with failing checks

## Common Patterns

### Activity Structure
```kotlin
class MyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MyScreen()
            }
        }
    }
}
```

### Firebase Authentication
```kotlin
val auth = FirebaseAuth.getInstance()
val currentUser = auth.currentUser
if (currentUser != null) {
    // User is signed in
} else {
    // User is not signed in
}
```

### Error Handling
```kotlin
try {
    // Firebase operation
} catch (e: Exception) {
    Log.e(TAG, "Error: ${e.message}", e)
    // Show user-friendly error message
}
```

## Dependencies

### Core Libraries
- AndroidX Core KTX
- AndroidX Activity Compose
- Jetpack Compose (UI, Material3, Icons)
- Firebase (Auth, App Check)
- Google Play Services (Auth)
- Glide for image loading

### When Adding Dependencies
- Check for security vulnerabilities
- Verify compatibility with existing dependencies
- Update `libs.versions.toml` version catalog
- Test thoroughly after adding
- Document if it's a critical dependency

## Troubleshooting

### Common Issues

**Firebase Authentication Not Working**
- Verify Firebase is initialized in Application class
- Check App Check debug token is registered
- Verify SHA-1 certificate matches Firebase Console
- Check network connectivity
- Review Logcat for detailed error messages

**Build Failures**
- Clean and rebuild: `./gradlew clean build`
- Invalidate caches and restart Android Studio
- Check Gradle version compatibility
- Verify all dependencies are available

**Git Push Errors**
- Fetch latest changes: `git fetch origin`
- Rebase or merge: `git rebase origin/main`
- Resolve conflicts if any
- Push again

## Documentation Standards

- Keep README.md up-to-date
- Document significant changes in commit messages
- Add KDoc comments for public APIs
- Update CONTRIBUTING.md for workflow changes
- Document Firebase configuration requirements

## Performance Considerations

- Lazy load images using Glide
- Avoid heavy operations on main thread
- Use coroutines for asynchronous operations
- Optimize Compose recomposition
- Monitor memory usage
- Test on low-end devices (API 24)

## Accessibility

- Use proper content descriptions for images
- Ensure sufficient color contrast
- Support different text sizes
- Test with TalkBack enabled
- Follow Material Design accessibility guidelines
