#!/usr/bin/env bash
set -euo pipefail

GAME_DIRECTORY="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"

if [[ "${1:-}" == --help || "${1:-}" == -h ]]; then
  cat <<'HELP'
Usage: ./run-game.sh [BOOKING_RULES_FILE] [application arguments...]

Save the supplied booking-rules.json in this checkout, its runtime directory,
or your Downloads folder, then run ./run-game.sh without arguments.
An explicit file path or BOOKING_RULES_FILE overrides this discovery.

Examples:
  ./run-game.sh
  ./run-game.sh "$HOME/Downloads/booking-rules.json"
  ./run-game.sh --server.port=8081 --server.servlet.context-path=/petclinic

Java 17 or newer is needed to run Gradle. Gradle obtains the Java 17 toolchain
for the application automatically if it is not installed already.
Set APPGLASS_AGENT_JAR to attach an AppGlass agent; its port defaults to 9999.
HELP
  exit 0
fi

# An empty first argument (an unset variable in an older command) means: discover the file.
if [[ $# -gt 0 && -z "$1" ]]; then
  shift
fi

GAME_RULES="${BOOKING_RULES_FILE:-}"
if [[ $# -gt 0 && "$1" != --* ]]; then
  GAME_RULES="$1"
  shift
fi

if [[ -z "$GAME_RULES" ]]; then
  GAME_DOWNLOADS="${XDG_DOWNLOAD_DIR:-$HOME/Downloads}"
  for GAME_LOCATION in "$PWD/runtime" "$PWD" "$GAME_DIRECTORY/runtime" "$GAME_DIRECTORY" "$GAME_DOWNLOADS"; do
    if [[ -f "$GAME_LOCATION/booking-rules.json" && -r "$GAME_LOCATION/booking-rules.json" ]]; then
      GAME_RULES="$GAME_LOCATION/booking-rules.json"
      break
    fi
  done
  if [[ -z "$GAME_RULES" ]]; then
    printf 'No booking rules file was found.\n' >&2
    printf 'Save the supplied booking-rules.json in the project or your Downloads folder, then run ./run-game.sh.\n' >&2
    printf 'To use another location: ./run-game.sh "/full/path/to/booking-rules.json"\n' >&2
    exit 2
  fi
fi

if [[ ! -f "$GAME_RULES" || ! -r "$GAME_RULES" ]]; then
  printf 'Cannot read booking rules file: %s\n' "$GAME_RULES" >&2
  printf 'Paths are relative to your current directory: %s\n' "$PWD" >&2
  GAME_SUGGESTION="$(dirname -- "$GAME_RULES")/booking-rules.json"
  if [[ -f "$GAME_SUGGESTION" && -r "$GAME_SUGGESTION" ]]; then
    printf 'Did you mean: ./run-game.sh %q\n' "$GAME_SUGGESTION" >&2
  else
    printf 'Pass the path of the downloaded JSON file, or run ./run-game.sh to discover it automatically.\n' >&2
  fi
  exit 2
fi
if [[ ! -s "$GAME_RULES" ]]; then
  printf 'Booking rules file is empty: %s\n' "$GAME_RULES" >&2
  exit 2
fi
GAME_RULES="$(cd -- "$(dirname -- "$GAME_RULES")" && pwd -P)/$(basename -- "$GAME_RULES")"

GAME_JAVA=java
if [[ -n "${JAVA_HOME:-}" ]]; then
  GAME_JAVA="$JAVA_HOME/bin/java"
fi
if ! GAME_JAVA_VERSION="$("$GAME_JAVA" -version 2>&1)"; then
  printf 'Cannot run Java: %s\nInstall a JDK (17 or newer), or correct JAVA_HOME.\n' "$GAME_JAVA" >&2
  exit 2
fi
if [[ ! "$GAME_JAVA_VERSION" =~ version[[:space:]]+\"([0-9]+) ]] || [[ "${BASH_REMATCH[1]}" -lt 17 ]]; then
  printf 'Java 17 or newer is required to start Gradle. Set JAVA_HOME to a supported JDK.\n' >&2
  exit 2
fi

GAME_AGENT="${APPGLASS_AGENT_JAR:-}"
if [[ -n "${APPGLASS_AGENT_JAR:-}" ]]; then
  if [[ ! -f "$GAME_AGENT" || ! -r "$GAME_AGENT" ]]; then
    printf 'Cannot read APPGLASS_AGENT_JAR: %s\n' "$GAME_AGENT" >&2
    exit 2
  fi
  GAME_AGENT="$(cd -- "$(dirname -- "$GAME_AGENT")" && pwd -P)/$(basename -- "$GAME_AGENT")"
fi

cd "$GAME_DIRECTORY"
printf 'Using booking rules: %s\n' "$GAME_RULES"
if ! ./gradlew prepareGame --console=plain; then
  printf '\nThe game build failed; see the Gradle error above. PetClinic has not started.\n' >&2
  exit 1
fi

GAME_RUNTIME_JAVA="$(cat build/game/java)"
GAME_JAR="$(cat build/game/jar)"
GAME_COMMAND=("$GAME_RUNTIME_JAVA")
if [[ -n "$GAME_AGENT" ]]; then
  GAME_COMMAND+=("-javaagent:$GAME_AGENT=tracingServer=on,serverPort=${APPGLASS_AGENT_PORT:-9999}")
fi
GAME_COMMAND+=(-jar "$GAME_JAR" "--petclinic.booking-rules=file:$GAME_RULES")
printf '\nStarting PetClinic with %s\n' "$GAME_RUNTIME_JAVA"
exec "${GAME_COMMAND[@]}" "$@"
