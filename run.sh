#!/bin/bash
# tinyexpression MCP server launcher
set -euo pipefail
cd /home/opa/tinyexpression

# Try JAVA_HOME, then common Java 21+ locations
if [ -z "${JAVA_HOME:-}" ]; then
  for candidate in \
    /home/opa/.sdkman/candidates/java/current \
    /home/opa/opt/jdk-21.0.12.1 \
    /home/opa/.jdks/openjdk-26; do
    if [ -x "$candidate/bin/java" ]; then
      export JAVA_HOME="$candidate"
      break
    fi
  done
fi

exec "$JAVA_HOME/bin/java" \
  -Xmx512m \
  -XX:+ExitOnOutOfMemoryError \
  --add-opens=java.base/java.util=ALL-UNNAMED \
  --add-opens=java.base/java.lang=ALL-UNNAMED \
  -cp "target/classes:$(find /home/opa/.m2/repository/org/unlaxer -name '*.jar' ! -name '*sources*' ! -name '*javadoc*' 2>/dev/null | tr '\n' ':')$(find /home/opa/.m2/repository/com/fasterxml/jackson -name '*.jar' ! -name '*sources*' ! -name '*javadoc*' 2>/dev/null | tr '\n' ':')$(find /home/opa/.m2/repository/org/jetbrains -name 'annotations*.jar' ! -name '*sources*' 2>/dev/null | tr '\n' ':')$(find /home/opa/.m2/repository/net/arnx -name '*.jar' ! -name '*sources*' 2>/dev/null | tr '\n' ':')" \
  org.unlaxer.tinyexpression.mcp.McpServer
