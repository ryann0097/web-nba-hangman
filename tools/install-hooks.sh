SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
HOOKS_SRC="$SCRIPT_DIR/hooks"
HOOKS_DST="$(git rev-parse --show-toplevel)/.git/hooks"

cp "$HOOKS_SRC/pre-commit" "$HOOKS_DST/pre-commit"
chmod +x "$HOOKS_DST/pre-commit"
echo "Git hooks installed."
