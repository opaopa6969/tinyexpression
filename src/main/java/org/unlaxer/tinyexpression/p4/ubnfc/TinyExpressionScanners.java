package org.unlaxer.tinyexpression.p4.ubnfc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.unlaxer.tinyexpression.codeblock.LongCodeFence;
import org.unlaxer.tinyexpression.p4.ubnfc.generated.api.TokenScanner;

/** Project-local extern bindings, layered over byte-identical pinned ubnfc scanners. */
public final class TinyExpressionScanners {
  private TinyExpressionScanners() {}
  public static final Map<String, TokenScanner> ALL;
  static {
    var scanners = new HashMap<>(P4Scanners.ALL);
    scanners.put("TinyExpressionP4::LONG_CODE_BLOCK", (input, state) -> {
      boolean matchOnly = state.mode() == TokenScanner.Mode.matchOnly;
      int start = matchOnly ? state.matched() : state.consumed();
      var layout = state.invertMatch() ? null : LongCodeFence.scan(input.length(), input::charAt, start);
      if (layout == null) return new TokenScanner.ScanResult(false, state.consumed(), state.matched(),
          start, start, List.of(new TokenScanner.ScanDiagnostic(start, "long code block")), List.of());
      return new TokenScanner.ScanResult(true, matchOnly ? state.consumed() : layout.end(), layout.end(),
          start, layout.fenceEnd(), List.of(), List.of());
    });
    ALL = Map.copyOf(scanners);
  }
}
