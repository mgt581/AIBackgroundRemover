# AI Background Remover - Copilot Instructions

## Project Overview
AI Background Remover is an Android mobile application that uses artificial intelligence to automatically remove backgrounds from images. The app provides gallery integration, Firebase authentication with Google Sign-in, and a WebView-based UI for the background removal functionality.

## Technology Stack
- **Language**: Kotlin
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 36
- **Build System**: Gradle with Kotlin DSL
- **UI Framework**: Jetpack Compose with Material Design 3
- **Architecture**: Android SDK with WebView integration
- **Authentication**: Firebase Auth with Google Sign-in
- **Image Loading**: Glide 5.0.5
- **Key Libraries**:
  - AndroidX Core KTX 1.17.0
  - Jetpack Compose 1.7.8
  - Material3 1.4.0
  - Firebase BOM 34.8.0
  - Google Play Services Auth 21.5.0
  - AndroidX Credentials 1.5.0

## Project Structure
- `/app/src/main/java/com/aiphotostudio/bgremover/` - Main application code
  - `MainActivity.kt` - Main activity with WebView integration
  - `GalleryActivity.kt` - Gallery functionality
  - `SettingsActivity.kt` - App settings
  - `TermsActivity.kt` - Terms and conditions
  - `ui/theme/` - Compose theme definitions
- `/app/src/test/` - Unit tests
- `/app/src/androidTest/` - Instrumented tests
- `.github/workflows/` - CI/CD workflows

## Coding Standards

### Kotlin Style
- Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful, descriptive variable and function names
- Prefer `val` over `var` when possible
- Use Kotlin's null safety features (`?.`, `?:`, `!!` sparingly)
- Prefer functional programming patterns (map, filter, etc.) over imperative loops
- Use named parameters for function calls with multiple arguments
- Add KDoc comments for public APIs and complex logic

### Android Best Practices
- Follow Material Design 3 guidelines
- Use Jetpack Compose for new UI components
- Implement proper lifecycle awareness in Activities
- Use `lateinit` for properties initialized in onCreate/onCreateView
- Always request necessary permissions at runtime for SDK 23+
- Use AndroidX libraries instead of legacy support libraries
- Implement proper error handling with try-catch blocks
- Use appropriate logging levels (Log.d, Log.e, Log.w)

### File Organization
- One class per file (except for small related classes)
- Group imports logically (standard library, Android, third-party, project)
- Order class members: constants, properties, lifecycle methods, public methods, private methods
- Use package-private visibility by default, public only when necessary

### WebView Integration
- Always enable JavaScript when needed via WebSettings
- Implement WebChromeClient for file uploads and permissions
- Use WebViewClient for URL handling
- Handle file upload callbacks properly with ValueCallback
- Clear cache when necessary for updated content

### Firebase Authentication
- Check authentication state before performing auth-required operations
- Handle sign-in/sign-out flows with proper error handling
- Use FirebaseAuth.getInstance() to get auth instance
- Implement proper GoogleSignInOptions configuration

## Build and Test Commands

### Build
```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Clean build
./gradlew clean build
```

### Testing
```bash
# Run unit tests
./gradlew test

# Run instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest

# Run all tests
./gradlew test connectedAndroidTest
```

### Lint and Code Quality
```bash
# Run lint checks
./gradlew lintDebug

# Generate lint report
./gradlew lint
```

### Gradle Sync
- Always sync Gradle after modifying build files
- Use `./gradlew --refresh-dependencies` to refresh dependencies
- Clear Gradle cache if encountering build issues: `./gradlew clean`

## CI/CD Pipeline

### GitHub Actions Workflows
The project uses GitHub Actions for automated checks:

1. **Android CI** (`.github/workflows/android-ci.yml`)
   - Runs on: push to main/develop branches
   - Steps: Build app, run unit tests, upload build reports on failure

2. **PR Checks** (`.github/workflows/pr-checks.yml`)
   - Runs on: pull requests
   - Steps: Check merge conflicts, validate PR is up-to-date, lint checks, build debug APK
   - Posts status comment on PR

### Pre-push Checks
- Git hooks are available in `.github/hooks/`
- Install hooks with: `./install-hooks.sh`
- Pre-push hook validates build before pushing

## Git Workflow

### Branch Strategy
- `main` - Production-ready code
- `develop` - Development branch (if used)
- `feature/*` - Feature branches
- Always create feature branches from main/develop
- Keep branches up-to-date with base branch

### Commit Messages
- Use present tense ("Add feature" not "Added feature")
- First line under 72 characters
- Be specific and descriptive
- Reference issues when applicable (e.g., "Fix #123: Bug description")

### Before Committing
1. Run lint: `./gradlew lintDebug`
2. Run tests: `./gradlew test`
3. Ensure build succeeds: `./gradlew assembleDebug`
4. Review changes with `git diff`

## Security Guidelines

### Firebase Configuration
- `google-services.json` is committed to repository (public project configuration)
- Never commit private keys or API secrets
- Use Firebase App Check for security
- Implement proper authentication checks before sensitive operations

### Permissions
- Request only necessary permissions
- Check permissions at runtime for dangerous permissions
- Handle permission denial gracefully
- Provide clear explanations for permission requests

### Data Protection
- Never log sensitive user data
- Use HTTPS for all network communications
- Validate and sanitize all user inputs
- Use ProGuard/R8 for release builds (already configured)

## Testing Requirements

### Unit Tests
- Write unit tests for business logic and utility functions
- Use JUnit 4 for unit tests
- Mock external dependencies (Firebase, Android framework)
- Aim for meaningful test coverage of critical paths
- Place tests in `/app/src/test/`

### Instrumented Tests
- Write instrumented tests for UI and integration scenarios
- Use AndroidX Test libraries (JUnit, Espresso)
- Test on multiple API levels if possible
- Place tests in `/app/src/androidTest/`

### Test Naming
- Use descriptive test names: `methodName_stateUnderTest_expectedBehavior`
- Example: `signIn_withValidCredentials_setsUserAuthenticated`

## Documentation

### Code Comments
- Add comments for complex algorithms or business logic
- Explain "why" not "what" (code should be self-documenting)
- Keep comments up-to-date with code changes
- Use KDoc for public APIs

### README and Contributing
- Update README.md for significant feature additions
- Follow CONTRIBUTING.md guidelines for pull requests
- Document new build steps or dependencies
- Update troubleshooting section for common issues

## Dependencies

### Adding New Dependencies
- Use version catalog in `gradle/libs.versions.toml`
- Prefer AndroidX libraries over legacy support libraries
- Check for security vulnerabilities before adding
- Keep dependencies up-to-date with latest stable versions
- Use BOM (Bill of Materials) for Firebase dependencies

### Version Updates
- Test thoroughly after updating major versions
- Check release notes for breaking changes
- Update all related dependencies together (e.g., all Compose libraries)

## ProGuard/R8
- ProGuard rules configured in `app/proguard-rules.pro`
- Release builds use R8 for code shrinking and obfuscation
- Test release builds before deploying
- Add ProGuard rules for libraries that require them

## Common Patterns

### Activity Lifecycle
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // Initialize views and components
}

override fun onStart() {
    super.onStart()
    // Start listeners, animations
}

override fun onStop() {
    super.onStop()
    // Stop listeners, animations
}
```

### Permission Handling
```kotlin
private val requestPermissionLauncher = 
    registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            // Permission granted
        } else {
            // Permission denied
        }
    }
```

### Firebase Auth Check
```kotlin
val currentUser = auth.currentUser
if (currentUser != null) {
    // User is signed in
} else {
    // User is not signed in
}
```

## Performance Considerations
- Optimize image loading with Glide's caching
- Use appropriate image sizes and formats
- Avoid memory leaks by clearing references in onDestroy
- Use RecyclerView for lists instead of ScrollView
- Profile app performance with Android Profiler
- Monitor memory usage and avoid bitmap memory issues

## Accessibility
- Add content descriptions to UI elements
- Support TalkBack for visually impaired users
- Ensure touch targets are at least 48dp
- Provide sufficient color contrast
- Support dynamic text sizing
