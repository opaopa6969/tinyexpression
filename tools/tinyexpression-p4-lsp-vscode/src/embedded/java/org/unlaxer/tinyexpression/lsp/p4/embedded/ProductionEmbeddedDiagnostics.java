package org.unlaxer.tinyexpression.lsp.p4.embedded;

import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import org.eclipse.lsp4j.*;
import org.unlaxer.source.*;
import org.unlaxer.source.LanguageRegions.Operation;
import org.unlaxer.tinyexpression.lsp.p4.EmbeddedLanguageDiagnostics;

/** javac parse/analyze only (-proc:none); no class loading or user code evaluation. */
public final class ProductionEmbeddedDiagnostics implements EmbeddedLanguageDiagnostics {
  private final String classpath = System.getProperty("java.class.path");
  private final ProviderProcess provider = new ProviderProcess(
      List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp", classpath,
          "org.unlaxer.dsl.provider.JavacProvider"),
      new ProviderProtocol.Identity("javac", "21.0.9"), Set.of(Operation.VALIDATE), Duration.ofSeconds(30));

  @Override public List<Diagnostic> analyze(String uri, int version, String fullContent) {
    if (!fullContent.lines().anyMatch(line -> line.stripTrailing().equals("formula:"))) return List.of();
    var host = new DocumentSnapshot(uri, version, fullContent);
    var binding = TinyProductionBridge.parse(host);
    if (binding.regions().stream().anyMatch(region -> region.parseState() == org.unlaxer.source.LanguageRegions.State.FAILED))
      throw new IllegalArgumentException("incomplete or invalid production region; no speculative Java children retained");
    var project = new LanguageQueries.Project("tiny-lsp", version, Map.of(uri, host), Map.of("classpath", classpath));
    var queries = binding.queries(project, provider);
    var diagnostics = new ArrayList<Diagnostic>();
    for (var result : queries.diagnosticsAll(host, project, Map.of())) {
      if (!binding.javaFiles().containsKey(result.region())) continue;
      if (result.state() != org.unlaxer.source.LanguageRegions.State.COMPLETE && result.state() != org.unlaxer.source.LanguageRegions.State.PARTIAL)
        throw new IllegalArgumentException(result.region() + ": " + result.state());
      for (var item : result.diagnostics()) {
        for (var mapping : item.locations()) {
          var location = mapping.location();
          if (!location.snapshot().equals(host)) throw new IllegalArgumentException("foreign diagnostic requires workspace consumer");
          var start = host.lsp(location.span().start()); var end = host.lsp(location.span().end());
          var diagnostic = new Diagnostic(new Range(new Position(start.line(), start.character()), new Position(end.line(), end.character())),
              item.message(), severity(item.severity()), "tinyexpression-java");
          diagnostic.setCode(item.code());
          diagnostic.setData(Map.of("region", result.region(), "version", Integer.toString(version), "exact", mapping.exact(), "severity", item.severity()));
          diagnostics.add(diagnostic);
        }
      }
    }
    return diagnostics;
  }
  private static DiagnosticSeverity severity(String value) {
    return switch(value) {
      case "ERROR" -> DiagnosticSeverity.Error;
      case "WARNING", "MANDATORY_WARNING" -> DiagnosticSeverity.Warning;
      case "HINT", "HELP", "SUGGESTION" -> DiagnosticSeverity.Hint;
      default -> DiagnosticSeverity.Information;
    };
  }
}
