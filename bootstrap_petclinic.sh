#!/usr/bin/env bash
set -euo pipefail

PETCLINIC_DIRECTORY="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"

if [[ "${1:-}" == --help || "${1:-}" == -h ]]; then
  cat <<'HELP'
Usage: ./bootstrap_petclinic.sh [POLICY_FILE] [application arguments...]

Save the supplied appointments.json in this checkout, its runtime directory,
or your Downloads folder, then run ./bootstrap_petclinic.sh without arguments.
An explicit file path or APPOINTMENT_POLICY_FILE overrides this discovery.

Examples:
  ./bootstrap_petclinic.sh
  ./bootstrap_petclinic.sh "$HOME/Downloads/appointments.json"
  ./bootstrap_petclinic.sh --server.port=8081 --server.servlet.context-path=/petclinic

Java 17 or newer is needed to run Gradle. Gradle obtains the Java 17 toolchain
for the application automatically if it is not installed already.
Set APPGLASS_AGENT_JAR to attach an AppGlass agent; its port defaults to 9999.
HELP
  exit 0
fi

# Accept the earlier documented command when APPOINTMENT_POLICY_FILE is unset.
if [[ $# -gt 0 && -z "$1" ]]; then
  shift
fi

PETCLINIC_POLICY="${APPOINTMENT_POLICY_FILE:-}"
if [[ $# -gt 0 && "$1" != --* ]]; then
  PETCLINIC_POLICY="$1"
  shift
fi

if [[ -z "$PETCLINIC_POLICY" ]]; then
  PETCLINIC_DOWNLOADS="${XDG_DOWNLOAD_DIR:-$HOME/Downloads}"
  for PETCLINIC_LOCATION in "$PWD/runtime" "$PWD" "$PETCLINIC_DIRECTORY/runtime" "$PETCLINIC_DIRECTORY" "$PETCLINIC_DOWNLOADS"; do
    for PETCLINIC_FILENAME in appointments.json appointements.json; do
      if [[ -f "$PETCLINIC_LOCATION/$PETCLINIC_FILENAME" && -r "$PETCLINIC_LOCATION/$PETCLINIC_FILENAME" ]]; then
        PETCLINIC_POLICY="$PETCLINIC_LOCATION/$PETCLINIC_FILENAME"
        break 2
      fi
    done
  done
  if [[ -z "$PETCLINIC_POLICY" ]]; then
    printf 'No room policy file was found.\n' >&2
    printf 'Save the supplied appointments.json in the project or your Downloads folder, then run ./bootstrap_petclinic.sh.\n' >&2
    printf 'To use another location: ./bootstrap_petclinic.sh "/full/path/to/appointments.json"\n' >&2
    exit 2
  fi
fi

if [[ ! -f "$PETCLINIC_POLICY" || ! -r "$PETCLINIC_POLICY" ]]; then
  printf 'Cannot read room policy file: %s\n' "$PETCLINIC_POLICY" >&2
  printf 'Paths are relative to your current directory: %s\n' "$PWD" >&2
  PETCLINIC_SUGGESTION="$(dirname -- "$PETCLINIC_POLICY")/appointments.json"
  if [[ -f "$PETCLINIC_SUGGESTION" && -r "$PETCLINIC_SUGGESTION" ]]; then
    printf 'Did you mean: ./bootstrap_petclinic.sh %q\n' "$PETCLINIC_SUGGESTION" >&2
  else
    printf 'Pass the path of the downloaded JSON file, or run ./bootstrap_petclinic.sh to discover it automatically.\n' >&2
  fi
  exit 2
fi
if [[ ! -s "$PETCLINIC_POLICY" ]]; then
  printf 'Room policy file is empty: %s\n' "$PETCLINIC_POLICY" >&2
  exit 2
fi
PETCLINIC_POLICY="$(cd -- "$(dirname -- "$PETCLINIC_POLICY")" && pwd -P)/$(basename -- "$PETCLINIC_POLICY")"

PETCLINIC_JAVA=java
if [[ -n "${JAVA_HOME:-}" ]]; then
  PETCLINIC_JAVA="$JAVA_HOME/bin/java"
fi
if ! PETCLINIC_JAVA_VERSION="$("$PETCLINIC_JAVA" -version 2>&1)"; then
  printf 'Cannot run Java: %s\nInstall a JDK (17 or newer), or correct JAVA_HOME.\n' "$PETCLINIC_JAVA" >&2
  exit 2
fi
if [[ ! "$PETCLINIC_JAVA_VERSION" =~ version[[:space:]]+\"([0-9]+) ]] || [[ "${BASH_REMATCH[1]}" -lt 17 ]]; then
  printf 'Java 17 or newer is required to start Gradle. Set JAVA_HOME to a supported JDK.\n' >&2
  exit 2
fi

PETCLINIC_AGENT="${APPGLASS_AGENT_JAR:-}"
if [[ -n "${APPGLASS_AGENT_JAR:-}" ]]; then
  if [[ ! -f "$PETCLINIC_AGENT" || ! -r "$PETCLINIC_AGENT" ]]; then
    printf 'Cannot read APPGLASS_AGENT_JAR: %s\n' "$PETCLINIC_AGENT" >&2
    exit 2
  fi
  PETCLINIC_AGENT="$(cd -- "$(dirname -- "$PETCLINIC_AGENT")" && pwd -P)/$(basename -- "$PETCLINIC_AGENT")"
fi

cd "$PETCLINIC_DIRECTORY"
printf 'Using room policy: %s\n' "$PETCLINIC_POLICY"
if ! ./gradlew preparePetclinic --console=plain; then
  printf '\nThe PetClinic build failed; see the Gradle error above. PetClinic has not started.\n' >&2
  exit 1
fi

PETCLINIC_RUNTIME_JAVA="$(cat build/petclinic/java)"
PETCLINIC_JAR="$(cat build/petclinic/jar)"
PETCLINIC_COMMAND=("$PETCLINIC_RUNTIME_JAVA")
if [[ -n "$PETCLINIC_AGENT" ]]; then
  PETCLINIC_COMMAND+=("-javaagent:$PETCLINIC_AGENT=tracingServer=on,serverPort=${APPGLASS_AGENT_PORT:-9999}")
fi
PETCLINIC_COMMAND+=(-jar "$PETCLINIC_JAR" "--petclinic.appointment-policy=file:$PETCLINIC_POLICY")
printf '\nStarting PetClinic with %s\n' "$PETCLINIC_RUNTIME_JAVA"
exec "${PETCLINIC_COMMAND[@]}" "$@"
