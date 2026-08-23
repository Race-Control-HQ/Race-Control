#!/usr/bin/env bash
# Start the RaceControl API locally.
set -euo pipefail
cd "$(dirname "$0")"

if [ ! -d ".venv" ]; then
  echo "Creating virtual environment..."
  python3 -m venv .venv
fi
# shellcheck disable=SC1091
source .venv/bin/activate

pip install --quiet --upgrade pip
pip install --quiet -r requirements.txt

echo "Starting RaceControl API on http://localhost:8000  (docs at /docs)"
exec uvicorn main:app --host 0.0.0.0 --port 8000 --reload
