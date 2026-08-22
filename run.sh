#!/bin/bash
# tinyexpression MCP server launcher
set -euo pipefail
cd /home/opa/tinyexpression
export JAVA_HOME="${JAVA_HOME:-/home/opa/.sdkman/candidates/java/current}"
exec "$JAVA_HOME/bin/java" \
  --add-opens=java.base/java.util=ALL-UNNAMED \
  --add-opens=java.base/java.lang=ALL-UNNAMED \
  -cp "target/classes:$(find /home/opa/.m2/repository/org/unlaxer -name '*.jar' ! -name '*sources*' ! -name '*javadoc*' | tr '\n' ':')$(find /home/opa/.m2/repository/com/fasterxml/jackson -name '*.jar' ! -name '*sources*' ! -name '*javadoc*' | tr '\n' ':')$(find /home/opa/.m2/repository/org/jetbrains -name 'annotations*.jar' ! -name '*sources*' | tr '\n' ':')$(find /home/opa/.m2/repository/net/arnx -name '*.jar' ! -name '*sources*' | tr '\n' ':')" \
  org.unlaxer.tinyexpression.mcp.McpServer
