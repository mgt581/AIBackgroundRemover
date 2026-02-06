#!/bin/bash
# Install git hooks for AI Background Remover repository
# This script copies hooks from .github/hooks to .git/hooks

set -e

HOOKS_DIR=".github/hooks"
GIT_HOOKS_DIR=".git/hooks"

echo "Installing git hooks for AI Background Remover..."
echo ""

# Check if we're in a git repository
if [ ! -d ".git" ]; then
    echo "❌ Error: Not in a git repository root directory"
    echo "Please run this script from the repository root"
    exit 1
fi

# Check if hooks directory exists
if [ ! -d "$HOOKS_DIR" ]; then
    echo "❌ Error: Hooks directory not found at $HOOKS_DIR"
    exit 1
fi

# Create .git/hooks directory if it doesn't exist
mkdir -p "$GIT_HOOKS_DIR"

# Install each hook
hooks_installed=0
for hook in "$HOOKS_DIR"/*; do
    if [ -f "$hook" ] && [ "$(basename "$hook")" != "README.md" ]; then
        hook_name=$(basename "$hook")
        
        # Copy hook
        cp "$hook" "$GIT_HOOKS_DIR/$hook_name"
        
        # Make executable
        chmod +x "$GIT_HOOKS_DIR/$hook_name"
        
        echo "✅ Installed: $hook_name"
        hooks_installed=$((hooks_installed + 1))
    fi
done

echo ""
if [ $hooks_installed -eq 0 ]; then
    echo "⚠️  No hooks were found to install"
else
    echo "🎉 Successfully installed $hooks_installed hook(s)"
    echo ""
    echo "These hooks will help prevent common git errors, including:"
    echo "  - Pushing when your branch is out of sync"
    echo "  - Accidentally pushing to protected branches"
    echo ""
    echo "To bypass hooks (use with caution):"
    echo "  git push --no-verify"
fi

echo ""
echo "For more information, see .github/hooks/README.md"
