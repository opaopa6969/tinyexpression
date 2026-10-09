package org.unlaxer.tinyexpression.lsp.p4;

import static org.junit.Assert.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.junit.Test;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.LanguageClient;

public class ProductionEmbeddedDiagnosticsTest {
  @Test public void existingConsumerPublishesBothProductionJavaSectionsAndTracksSnapshots() throws Exception {
    var server = new TinyExpressionP4LanguageServerExt();
    var notifications = new ArrayList<PublishDiagnosticsParams>();
    server.connect(new LanguageClient() {
      public void publishDiagnostics(PublishDiagnosticsParams params) { notifications.add(params); }
      public void telemetryEvent(Object value) {}
      public void showMessage(MessageParams value) {}
      public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams value) { return CompletableFuture.completedFuture(null); }
      public void logMessage(MessageParams value) {}
    });
    var defaults = new com.google.gson.Gson().toJsonTree(server.initialize(new InitializeParams()).join().getCapabilities().getExperimental()).getAsJsonObject();
    assertFalse(defaults.getAsJsonObject("embeddedLanguageDiagnostics").get("enabled").getAsBoolean());
    var initialize = new InitializeParams();initialize.setInitializationOptions(Map.of("embeddedLanguageDiagnostics", true));
    var caps = server.initialize(initialize).join().getCapabilities();
    assertNotNull(caps.getExperimental());
    var service = server.getTextDocumentService();String uri="file:///production.formulainfo";
    Path fixtures=Path.of("src/embedded-test/resources");
    String error=Files.readString(fixtures.resolve("type-error.txt"));
    service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri,"tinyexpression",1,error)));
    var published=last(notifications);assertEquals(Integer.valueOf(1),published.getVersion());
    var java=javaDiagnostics(published);assertEquals(published.toString(),2,java.size());
    assertEquals(new Range(new Position(41,9),new Position(41,10)),java.get(0).getRange());
    assertEquals(new Range(new Position(69,9),new Position(69,10)),java.get(1).getRange());
    for(var diagnostic:java) { assertEquals("compiler.err.prob.found.req",diagnostic.getCode().getLeft());assertEquals(DiagnosticSeverity.Error,diagnostic.getSeverity()); }
    int count=notifications.size();
    service.didChange(new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(uri,1),List.of(new TextDocumentContentChangeEvent("stale"))));
    assertEquals(count,notifications.size());
    // The first formula remains byte-for-byte identical; the changed second section must be reanalyzed.
    String secondFixed = error.substring(0,error.lastIndexOf("return 1;")) + error.substring(error.lastIndexOf("return 1;")).replace("return 1;", "return true;");
    service.didChange(new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(uri,2),List.of(new TextDocumentContentChangeEvent(secondFixed))));
    assertEquals(1,javaDiagnostics(last(notifications)).size());
    assertEquals(41,javaDiagnostics(last(notifications)).get(0).getRange().getStart().getLine());
    String unicode=Files.readString(fixtures.resolve("unicode-crlf-error.txt"));
    service.didChange(new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(uri,3),List.of(new TextDocumentContentChangeEvent(unicode))));
    published=last(notifications);assertEquals(Integer.valueOf(3),published.getVersion());java=javaDiagnostics(published);
    assertEquals(published.toString(),1,java.size());assertEquals(new Range(new Position(42,9),new Position(42,10)),java.get(0).getRange());
    service.didSave(new DidSaveTextDocumentParams(new TextDocumentIdentifier(uri)));assertEquals(Integer.valueOf(3),last(notifications).getVersion());
    String valid=Files.readString(Path.of("../../src/test/resources/formulaInfo-test/69/formulaInfo.txt"));
    service.didChange(new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(uri,4),List.of(new TextDocumentContentChangeEvent(valid))));
    assertTrue(last(notifications).toString(),javaDiagnostics(last(notifications)).isEmpty());
    service.didChange(new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(uri,5),List.of(new TextDocumentContentChangeEvent("formula:\ninvalid\n"))));
    assertTrue(last(notifications).getDiagnostics().stream().anyMatch(d->d.getCode()!=null && "EMBEDDED_UNAVAILABLE".equals(d.getCode().getLeft())));
    service.didClose(new DidCloseTextDocumentParams(new TextDocumentIdentifier(uri)));assertTrue(last(notifications).getDiagnostics().isEmpty());
  }
  private static PublishDiagnosticsParams last(List<PublishDiagnosticsParams> values) { return values.get(values.size()-1); }
  private static List<Diagnostic> javaDiagnostics(PublishDiagnosticsParams value) { return value.getDiagnostics().stream().filter(d->"tinyexpression-java".equals(d.getSource())).toList(); }
}
