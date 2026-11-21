#!/bin/sh
# Lightweight Gradle launcher for PvPMacro. Downloads the configured Gradle distribution on first run.
set -eu
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROPS="$APP_HOME/gradle/wrapper/gradle-wrapper.properties"
if [ ! -f "$PROPS" ]; then
  echo "Missing $PROPS" >&2
  exit 1
fi
DIST_URL=$(sed -n 's/^distributionUrl=//p' "$PROPS" | sed 's/\\:/:/g' | tr -d '\r')
if [ -z "$DIST_URL" ]; then
  echo "distributionUrl is missing in $PROPS" >&2
  exit 1
fi
DIST_NAME=${DIST_URL##*/}
GRADLE_VERSION=${DIST_NAME%-bin.zip}
GRADLE_USER_HOME=${GRADLE_USER_HOME:-"$HOME/.gradle"}
INSTALL_DIR="$GRADLE_USER_HOME/wrapper/manual-dists/$GRADLE_VERSION"
GRADLE_BIN="$INSTALL_DIR/gradle-$GRADLE_VERSION/bin/gradle"
if [ ! -x "$GRADLE_BIN" ]; then
  mkdir -p "$INSTALL_DIR"
  ZIP="$INSTALL_DIR/$DIST_NAME"
  echo "Downloading Gradle $GRADLE_VERSION..."
  if command -v curl >/dev/null 2>&1; then
    curl -fL --retry 3 "$DIST_URL" -o "$ZIP"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$ZIP" "$DIST_URL"
  else
    echo "curl or wget is required to download Gradle" >&2
    exit 1
  fi
  if command -v unzip >/dev/null 2>&1; then
    unzip -q -o "$ZIP" -d "$INSTALL_DIR"
  else
    echo "unzip is required to extract Gradle" >&2
    exit 1
  fi
  rm -f "$ZIP"
fi
exec "$GRADLE_BIN" -p "$APP_HOME" "$@"
