package org.unlaxer.tinyexpression.p4.ubnfc.generated.api;
import java.util.List;
import org.unlaxer.tinyexpression.p4.ubnfc.generated.rt.Input;
/** Scanner positions and value spans use UTF-16; effects are replayable, never direct mutations. */
@FunctionalInterface
public interface TokenScanner {
    ScanResult scan(Input input, ScanState state);
    enum Mode { consumed, matchOnly }
    record ScanState(int consumed, int matched, Mode mode, boolean invertMatch, boolean resetMatchedWithConsumed) {}
    record Effect(String action, String name, int offsetCp, int lengthCp, String mode) {
        public Effect(String action, String name, int offsetCp, int lengthCp) { this(action, name, offsetCp, lengthCp, "lexical"); }
    }
    record ValueSpan(int start, int end) {}
    record ScanDiagnostic(int offsetUtf16, String expected) {}
    record ScanResult(boolean ok, int consumedEnd, int matchedEnd, ValueSpan valueSpan,
                      List<ScanDiagnostic> diagnostics, List<Effect> effects) {
        /** Null valueSpan uses the selected cursor extent; null lists mean no diagnostics/effects. */
        public ScanResult {
            diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
            effects = effects == null ? List.of() : List.copyOf(effects);
        }
        public ScanResult(boolean ok, int consumedEnd, int matchedEnd, int valueStart, int valueEnd,
                          List<ScanDiagnostic> diagnostics, List<Effect> effects) {
            this(ok, consumedEnd, matchedEnd, new ValueSpan(valueStart, valueEnd), diagnostics, effects);
        }
    }
}
