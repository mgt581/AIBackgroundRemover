# Contributing to AI Background Remover

Thank you for your interest in contributing to AI Background Remover! This document provides guidelines and best practices for contributing to the project.

## Getting Started

### 1. Fork and Clone

```bash
# Fork the repository on GitHub, then clone your fork
git clone https://github.com/YOUR_USERNAME/AIBackgroundRemover.git
cd AIBackgroundRemoverbroken2

# Add the upstream repository
git remote add upstream https://github.com/mgt581/AIBackgroundRemover.git
```

### 2. Set Up Development Environment

- Install Android Studio (latest stable version)
- Install JDK 17 or higher
- Open the project in Android Studio
- Let Gradle sync and download dependencies

## Development Workflow

### Creating a Feature Branch

Always create a new branch for your work:

```bash
# Update your main branch
git checkout main
git pull upstream main

# Create and switch to a new feature branch
git checkout -b feature/your-feature-name
```

### Making Changes

1. Make your code changes
2. Test your changes locally
3. Run lint checks: `./gradlew lintDebug`
4. Run tests: `./gradlew test`
5. Build the app: `./gradlew assembleDebug`

### Committing Changes

Write clear, concise commit messages:

```bash
# Stage your changes
git add .

# Commit with a descriptive message
git commit -m "Add feature: description of what you did"
```

**Commit Message Format:**
- Use present tense ("Add feature" not "Added feature")
- Keep first line under 72 characters
- Be specific and descriptive
- Reference issues if applicable (e.g., "Fix #123: Bug description")

### Syncing with Upstream

Before pushing or opening a PR, sync with the upstream repository:

```bash
# Fetch upstream changes
git fetch upstream

# Merge or rebase on upstream main
git rebase upstream/main

# If there are conflicts, resolve them, then:
git add .
git rebase --continue
```

### Pushing Changes

```bash
# Push your branch to your fork
git push origin feature/your-feature-name
```

**If you encounter a push error:**

```bash
# Error: "cannot lock ref" or "failed to push some refs"
# This means the remote has changed. Fetch and rebase:

git fetch origin
git rebase origin/feature/your-feature-name

# Or if you need to incorporate upstream changes:
git fetch upstream
git rebase upstream/main

# Resolve any conflicts, then:
git push origin feature/your-feature-name
```

## Pull Request Process

### 1. Before Opening a PR

- [ ] Ensure your branch is up-to-date with upstream main
- [ ] All tests pass locally
- [ ] Lint checks pass
- [ ] Code builds successfully
- [ ] Changes are well-documented

### 2. Opening a Pull Request

1. Go to your fork on GitHub
2. Click "New Pull Request"
3. Select your feature branch
4. Fill out the PR template with:
   - Clear description of changes
   - Reference to any related issues
   - Screenshots (if UI changes)
   - Testing performed

### 3. After Opening a PR

- GitHub Actions will automatically run CI checks
- Address any automated feedback
- Respond to code review comments
- Keep your PR updated with main branch:

```bash
# If main has advanced while your PR is open
git fetch upstream
git checkout feature/your-feature-name
git rebase upstream/main
git push --force-with-lease origin feature/your-feature-name
```

**Note:** Use `--force-with-lease` instead of `--force` for safer force pushing.

## Code Standards

### Kotlin Style Guide

- Follow the [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful variable and function names
- Add comments for complex logic
- Keep functions focused and concise

### Android Best Practices

- Follow Material Design guidelines
- Use Jetpack Compose for UI components
- Implement proper error handling
- Add appropriate logging for debugging

### Testing

- Write unit tests for new features
- Ensure existing tests still pass
- Aim for meaningful test coverage
- Test edge cases and error conditions

## Handling Common Git Issues

### Merge Conflicts

If you encounter merge conflicts:

```bash
# Pull latest changes
git fetch upstream
git rebase upstream/main

# Git will pause on conflicts
# Edit conflicted files to resolve conflicts
# Look for markers: <<<<<<<, =======, >>>>>>>

# After resolving all conflicts:
git add .
git rebase --continue

# If you want to abort:
git rebase --abort
```

### Out of Sync Errors

**Error:** `cannot lock ref 'refs/heads/main': is at [commit] but expected [commit]`

**Solution:**
```bash
# This happens when remote has advanced
# Fetch the latest state
git fetch origin

# View the divergence
git log HEAD..origin/main --oneline

# Option 1: Merge
git merge origin/main

# Option 2: Rebase (preferred for cleaner history)
git rebase origin/main

# Push after resolving any conflicts
git push origin your-branch-name
```

### Accidentally Committed to Wrong Branch

```bash
# Create a new branch from current state
git branch feature/correct-branch-name

# Reset current branch to before your commits
git reset --hard origin/main

# Switch to the correct branch
git checkout feature/correct-branch-name
```

### Need to Undo Last Commit

```bash
# Undo commit but keep changes staged
git reset --soft HEAD~1

# Undo commit and unstage changes
git reset HEAD~1

# Undo commit and discard changes (careful!)
git reset --hard HEAD~1
```

## CI/CD Pipeline

Our repository uses GitHub Actions for automated checks:

### Android CI (runs on main/develop)
- Builds the application
- Runs all tests
- Uploads build reports if failures occur

### PR Checks (runs on pull requests)
- Checks for merge conflicts
- Validates PR is up-to-date
- Runs lint checks
- Builds debug APK
- Posts status comment on PR

Make sure all checks pass before requesting review.

## Code Review Process

1. **Automated Checks**: Must pass before review
2. **Peer Review**: At least one approval required
3. **Address Feedback**: Make requested changes
4. **Final Approval**: Maintainer will merge

## Getting Help

- **Questions**: Open a GitHub Discussion
- **Bugs**: Open a GitHub Issue
- **Security**: Email [security contact]
- **General**: Check existing issues and discussions

## Recognition

Contributors will be recognized in:
- Project README
- Release notes
- GitHub contributors page

Thank you for contributing to AI Background Remover! 🎉
