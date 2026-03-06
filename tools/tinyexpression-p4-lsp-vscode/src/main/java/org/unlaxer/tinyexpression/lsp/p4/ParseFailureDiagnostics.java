package org.unlaxer.tinyexpression.lsp.p4;

import java.util.List;

/**
 * Type-safe wrapper for P4 parser failure diagnostics.
 * <p>
 * Sealed to ensure exhaustive handling at call sites. Replaces the
 * {@code Object failureDiagnostics} pattern used by the existing reflection-based LSP server.
 */
public sealed interface ParseFailureDiagnostics
    permits ParseFailureDiagnostics.Absent, ParseFailureDiagnostics.Present {

  /** Returns true when the parser detected a failure. */
  boolean hasFailure();

  /** Returns the character offset at which parsing failed (0 when absent). */
  int failureOffset();

  /**
   * Returns a list of human-readable hints describing what was expected at the
   * failure offset. May be empty if no specific hints are available.
   */
  List<String> expectedHints();

  /** Factory: no failure. */
  static ParseFailureDiagnostics absent() {
    return new Absent();
  }

  /** Factory: failure at the given offset with optional expected hints. */
  static ParseFailureDiagnostics present(int failureOffset, List<String> expectedHints) {
    return new Present(failureOffset, List.copyOf(expectedHints));
  }

  /** Represents a successful parse — no diagnostics to report. */
  record Absent() implements ParseFailureDiagnostics {
    @Override public boolean hasFailure() { return false; }
    @Override public int failureOffset() { return 0; }
    @Override public List<String> expectedHints() { return List.of(); }
  }

  /**
   * Represents a failed parse.
   *
   * @param failureOffset  character offset at which parsing stopped
   * @param expectedHints  human-readable list of expected tokens/constructs
   */
  record Present(int failureOffset, List<String> expectedHints)
      implements ParseFailureDiagnostics {
    @Override public boolean hasFailure() { return true; }
  }
}
