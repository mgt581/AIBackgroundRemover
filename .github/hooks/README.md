# Git Hooks Setup Guide

This directory contains helpful git hooks to prevent common git errors, including the push synchronization errors.

## Available Hooks

### pre-push

Prevents pushing when your branch is out of sync with the remote, which helps avoid errors like:
```
error: failed to push some refs
! [remote rejected] (cannot lock ref 'refs/heads/main': is at [commit] but expected [commit])
```

## Installation

### Option 1: Manual Installation

1. Copy the hook to your local `.git/hooks` directory:
   ```bash
   cp .github/hooks/pre-push .git/hooks/pre-push
   ```

2. Make it executable:
   ```bash
   chmod +x .git/hooks/pre-push
   ```

### Option 2: Automatic Installation Script

Run this command from the repository root:

```bash
# Linux/Mac
./install-hooks.sh

# Windows (Git Bash)
bash install-hooks.sh
```

### Option 3: Git Config (Git 2.9+)

You can configure git to use hooks from a directory:

```bash
git config core.hooksPath .github/hooks
```

This will use the hooks directly from the `.github/hooks` directory.

## What the pre-push Hook Does

1. **Fetches latest changes** from remote
2. **Checks sync status** - Counts commits ahead/behind
3. **Prevents push if behind** - Stops you from pushing when remote has advanced
4. **Warns on protected branches** - Asks for confirmation when pushing to main/master
5. **Provides helpful commands** - Shows exactly what to run to fix issues

## Bypassing Hooks (Use with Caution)

If you need to bypass the hook:

```bash
git push --no-verify
```

**Warning:** Only use this if you know what you're doing!

## Troubleshooting

### Hook not running

- Check if the hook file is executable: `ls -l .git/hooks/pre-push`
- Make it executable: `chmod +x .git/hooks/pre-push`
- Verify hook is in correct location: `.git/hooks/pre-push`

### Hook errors

If you get errors about git commands:
- Ensure you have git 2.0 or higher: `git --version`
- Check internet connection for fetch operations

## Additional Resources

- See `CONTRIBUTING.md` for full git workflow documentation
- See `README.md` for troubleshooting common git issues
