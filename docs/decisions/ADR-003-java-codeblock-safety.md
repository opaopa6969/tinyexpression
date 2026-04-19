# ADR-003: Java コードブロック実行のセキュリティモデル

**Status**: Accepted  
**Date**: 2026-03-01  
**Deciders**: Project architect

---

## Context

TinyExpression supports embedding a full Java class directly inside a `formula` field using triple-backtick syntax:

~~~text
formula:
```java:package.ClassName
// arbitrary Java code
```
~~~

At evaluation time, the embedded class is:

1. Parsed from the formula body
2. Compiled in-memory via `javax.tools.JavaCompiler`
3. Loaded via `MemoryClassLoader` (scoped to the formula's class loader)
4. Instantiated and invoked with the `CalculationContext`

The compiled bytecode is not persisted to disk. It is discarded when the class loader is garbage collected.

### The Risk

The embedded Java code runs with **the same permissions as the host JVM process**. There is no sandbox. The code can:

- Read and write the file system
- Make network connections
- Call `System.exit()`
- Access environment variables and system properties
- Instantiate any class visible on the classpath
- Spawn threads

This is intentional — the feature exists to allow advanced integrations with full Java expressiveness. However, it is **only safe when the formula author is trusted**.

---

## Decision

The Java code block feature is **retained as-is** with the following explicit policy:

### Usage Policy

1. **Trusted authors only**: Java code blocks must only be enabled in environments where formula authors have equivalent trust to application developers.

2. **No sandboxing by the engine**: TinyExpression does not provide a sandbox. Sandboxing, if required, must be implemented at the JVM level (e.g., `SecurityManager` replacement, process isolation, container boundaries) by the host application.

3. **Explicit documentation**: All user-facing documentation (README, language guide, getting-started) must carry an explicit security warning on Java code blocks.

4. **Feature isolation**: The Java code block compilation path (`TripleBackTickParser`, `CodeParser`, `MemoryClassLoader`) is distinct from the standard formula path. Host applications can disable this feature by intercepting `FormulaInfo` blocks before evaluation if code blocks are not needed.

### Documentation Requirements

Every document that mentions Java code blocks must include this warning:

> **Warning**: Java code blocks compile and execute arbitrary code on the JVM. Only use this feature when formula authors are fully trusted. Do not expose this capability to untrusted users.

---

## Consequences

### Positive

- Advanced integrations remain possible — the feature is not removed
- The security model is explicit and documented rather than implied
- Host applications can audit their usage by searching for triple-backtick patterns in `formulaInfo.txt` files

### Negative

- Users who skim documentation may miss the warning and expose the feature to untrusted users
- There is no engine-level protection — all responsibility is on the host application

### Neutral

- The `MemoryClassLoader` scope means compiled classes are not permanently retained, reducing one class of risk (persistent backdoors via compiled classes survive only as long as the class loader)

---

## Alternatives Considered

**Remove the feature entirely**: Rejected. The feature enables legitimate advanced integrations and was an explicit design choice. Removing it would break existing users without an equivalent replacement.

**Add an opt-in flag to enable code blocks**: Deferred. This would be a meaningful improvement for future versions. `FormulaInfoAdditionalFields` is the natural place for such a flag. Not implemented in v1.4.10.

**Provide a SecurityManager-based sandbox**: Rejected. `SecurityManager` is deprecated and removed in recent Java versions. A meaningful sandbox would require process isolation, which is outside the scope of an embedded expression engine.

---

## Related

- [docs/language-guide.md — Java Code Blocks](../language-guide.md#java-code-blocks)
- `org.unlaxer.compiler.MemoryClassLoader`
- `org.unlaxer.tinyexpression.parser.javalang.TripleBackTickParser`
- `org.unlaxer.tinyexpression.parser.javalang.CodeParser`
