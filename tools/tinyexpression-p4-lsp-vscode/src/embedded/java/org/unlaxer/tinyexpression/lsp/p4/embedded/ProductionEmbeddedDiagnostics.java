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
      new ProviderProtocol.Identity("javac", "21.0.9"), Set.of(Operation.VALIDATE, Operation.COMPLETION), Duration.ofSeconds(30));

  @Override public List<Diagnostic> analyze(String uri, int version, String fullContent) {
    if (!fullContent.lines().anyMatch(line -> line.stripTrailing().equals("formula:"))) return List.of();
    var host = new DocumentSnapshot(uri, version, fullContent);
    var binding = TinyProductionBridge.parseEditor(host);
    var project = new LanguageQueries.Project("tiny-lsp", version, Map.of(uri, host), Map.of("classpath", classpath));
    var queries = binding.queries(project, provider);
    var diagnostics = new ArrayList<Diagnostic>();
    for (var region : binding.regions()) if (region.parseState() == org.unlaxer.source.LanguageRegions.State.FAILED)
      diagnostics.add(unavailable(host, region.body().start(), "Invalid production region: " + region.id()));
    for (var result : queries.diagnosticsAll(host, project, Map.of())) {
      if (!binding.javaFiles().containsKey(result.region())) continue;
      if (result.state() != org.unlaxer.source.LanguageRegions.State.COMPLETE && result.state() != org.unlaxer.source.LanguageRegions.State.PARTIAL) {
        diagnostics.add(unavailable(host, 0, result.region() + ": " + result.state()));
        continue;
      }
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
  @Override public Optional<List<CompletionItem>> complete(String uri, int version, String fullContent, Position position) {
    if (!fullContent.lines().anyMatch(line -> line.stripTrailing().equals("formula:"))) return Optional.empty();
    var host = new DocumentSnapshot(uri, version, fullContent);
    int cursor=host.fromLsp(new DocumentSnapshot.Position(position.getLine(),position.getCharacter()));
    var binding=TinyProductionBridge.parseEditor(host);var owner=binding.tree().at(cursor);
    if (owner==null || !owner.language().equals(TinyProductionBridge.JAVA)) return Optional.empty();
    int end=host.utf16(cursor),start=end;
    while(start>0 && Character.isJavaIdentifierPart(fullContent.codePointBefore(start))) start-=Character.charCount(fullContent.codePointBefore(start));
    var project=new LanguageQueries.Project("tiny-lsp",version,Map.of(uri,host),Map.of("classpath",classpath));
    var result=binding.queries(project,provider).query(host,project,cursor,Operation.COMPLETION,Map.of("prefix",fullContent.substring(start,end)));
    if(result.state()!=org.unlaxer.source.LanguageRegions.State.COMPLETE && result.state()!=org.unlaxer.source.LanguageRegions.State.PARTIAL)
      throw new IllegalArgumentException(result.state().name());
    var items=new ArrayList<CompletionItem>();
    for(var candidate:result.items()) {
      var item=new CompletionItem(candidate.label());item.setDetail(candidate.detail());
      item.setData(Map.of("region",result.region(),"version",Integer.toString(version),"state",result.state().name()));
      var edits=new ArrayList<TextEdit>();
      for(var edit:candidate.edits()) edits.add(new TextEdit(range(host,edit.span()),edit.replacement()));
      if(edits.isEmpty()) item.setInsertText("");
      else {
        item.setTextEdit(org.eclipse.lsp4j.jsonrpc.messages.Either.forLeft(edits.get(0)));
        if(edits.size()>1)item.setAdditionalTextEdits(edits.subList(1,edits.size()));
      }
      items.add(item);
    }
    return Optional.of(items);
  }
  private static Range range(DocumentSnapshot host,DocumentSnapshot.Span span) {
    var start=host.lsp(span.start());var end=host.lsp(span.end());
    return new Range(new Position(start.line(),start.character()),new Position(end.line(),end.character()));
  }
  private static Diagnostic unavailable(DocumentSnapshot host,int point,String message) {
    var diagnostic=new Diagnostic(range(host,new DocumentSnapshot.Span(point,point)),message,DiagnosticSeverity.Warning,"tinyexpression-embedded");
    diagnostic.setCode("EMBEDDED_UNAVAILABLE");return diagnostic;
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
