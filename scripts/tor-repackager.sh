#!/bin/bash

DIR=$(dirname $0)
LIBS="$DIR/../build/lib-run"
PROJECT_DIR=$(readlink -f "$DIR/../")

if [ ! -d "$LIBS" ]; then
	echo "Please run './gradlew createRuntime'"
	exit 1
fi

CLASSPATH="$LIBS/*"

exec java -Dprojectdir="$PROJECT_DIR" -cp "$CLASSPATH" "$@"
