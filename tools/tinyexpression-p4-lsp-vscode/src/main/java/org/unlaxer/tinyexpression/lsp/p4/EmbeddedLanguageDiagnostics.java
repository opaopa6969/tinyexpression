package org.unlaxer.tinyexpression.lsp.p4;

import java.util.List;
import org.eclipse.lsp4j.Diagnostic;

/** Optional analysis of the untouched full document; implementations must never evaluate user code. */
public interface EmbeddedLanguageDiagnostics {
  List<Diagnostic> analyze(String uri, int version, String fullContent);
  /** Empty optional means no owned embedded region; a present empty list is an owned region without candidates. */
  default java.util.Optional<List<org.eclipse.lsp4j.CompletionItem>> complete(
      String uri, int version, String fullContent, org.eclipse.lsp4j.Position position) {
    return java.util.Optional.empty();
  }
}
