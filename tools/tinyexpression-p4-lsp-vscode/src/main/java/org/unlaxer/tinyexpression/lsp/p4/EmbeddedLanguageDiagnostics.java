package org.unlaxer.tinyexpression.lsp.p4;

import java.util.List;
import org.eclipse.lsp4j.Diagnostic;

/** Optional analysis of the untouched full document; implementations must never evaluate user code. */
public interface EmbeddedLanguageDiagnostics {
  List<Diagnostic> analyze(String uri, int version, String fullContent);
}
