#!/usr/bin/env bash
# Pure TV one-command installer for macOS and Linux.
#   curl -fsSL https://raw.githubusercontent.com/Vardxn/pure-tv/main/install.sh | bash -s -- <tv-ip>
#   or, from a clone:  ./install.sh <tv-ip>        undo:  ./install.sh rollback <tv-ip>
# Sets up a private Python environment in ~/.puretv (nothing installed system-wide), then runs
# tools/puretv.py, which talks to the TV over the network and asks before every optional change.
set -euo pipefail
REPO="Vardxn/pure-tv"
HOME_DIR="${PURETV_HOME:-$HOME/.puretv}"

command -v python3 >/dev/null 2>&1 || {
  echo "Pure TV needs Python 3. On a Mac: install it from https://www.python.org/downloads/ (or 'brew install python')."
  exit 1
}

# Use this folder when run from a clone, otherwise fetch the latest source.
SRC=""
if [ -n "${BASH_SOURCE[0]:-}" ] && [ -f "$(dirname "${BASH_SOURCE[0]}")/tools/puretv.py" ]; then
  SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
else
  SRC="$HOME_DIR/src"
  rm -rf "$SRC" && mkdir -p "$SRC"
  echo "Downloading Pure TV tools..."
  curl -fsSL "https://github.com/$REPO/archive/refs/heads/main.tar.gz" | tar xz -C "$SRC" --strip-components 1
fi

if [ ! -x "$HOME_DIR/venv/bin/python" ]; then
  echo "Setting up a private Python environment in $HOME_DIR (one time)..."
  python3 -m venv "$HOME_DIR/venv"
fi
"$HOME_DIR/venv/bin/python" -m pip install -q --disable-pip-version-check -r "$SRC/tools/requirements.txt"

if [ $# -eq 0 ]; then
  read -r -p "Your TV's IP address (Settings > Network & Internet on the TV): " IP </dev/tty
  set -- install "$IP"
elif [ "$1" != "install" ] && [ "$1" != "rollback" ] && [ "$1" != "probe" ]; then
  set -- install "$@"
fi
exec "$HOME_DIR/venv/bin/python" -I "$SRC/tools/puretv.py" "$@" </dev/tty
