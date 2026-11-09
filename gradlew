#!/bin/sh
set -eu
cd "$(dirname "$0")"
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVA="$JAVA_HOME/bin/java"
else
  JAVA=java
fi
exec "$JAVA" gradle/bootstrap/WrapperBootstrap.java "$@"
