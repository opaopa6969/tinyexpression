package org.unlaxer.tinyexpression.lsp.p4;

import static org.junit.Assert.*;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.*;
import org.junit.Test;

/** The same invalid-operator corpus is consumed by the real wasm playground. */
public class TinyExpressionP4OperatorDiagnosticTest {
    private record NativeFailure(int cp, List<String> hints) {}
    private static final class ProbeServer extends TinyExpressionP4LanguageServerExt {
        NativeFailure nativeFailure(String text) {
            try (var context = createParseContext(createRootSource(text))) {
                org.unlaxer.dsl.runtime.ScopeStore.registerDispatcher(context);
                org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4Parsers.getRootParser().parse(context);
                var failure = context.getParseFailureDiagnostics();
                var hints = failure.getExpectedHintCandidates().stream()
                    .map(org.unlaxer.context.ParseFailureDiagnostics.ExpectedHintCandidate::getDisplayHint)
                    .filter(h -> h != null && !h.isBlank()).distinct().toList();
                if (hints.isEmpty() && !failure.getExpectedParsers().isEmpty())
                    hints = List.of("Expected: " + failure.getExpectedParsers().get(0));
                return new NativeFailure(failure.getFarthestOffset(), hints);
            }
        }
    }
    @Test public void realDiagnosticsClassifyOnlyTheOperatorAtTheFailure() throws Exception {
        var server = new ProbeServer();
        var published = new ArrayList<PublishDiagnosticsParams>();
        server.connect(new org.eclipse.lsp4j.services.LanguageClient() {
            public void telemetryEvent(Object ignored) {}
            public void publishDiagnostics(PublishDiagnosticsParams params) { published.add(params); }
            public void showMessage(MessageParams ignored) {}
            public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams ignored) { return CompletableFuture.completedFuture(null); }
            public void logMessage(MessageParams ignored) {}
        });
        var rows = JsonParser.parseString(Files.readString(Path.of("../../catalog/fixtures/operator-diagnostics.json"))).getAsJsonArray();
        var errors = new ArrayList<String>();
        for (var raw : rows) {
            var row = raw.getAsJsonObject();
            String source = row.get("input").getAsString();
            server.parseDocument("file:///operator-diagnostic.tinyexp", source);
            var diagnostics = published.get(published.size() - 1).getDiagnostics();
            boolean expected = !row.get("operator").isJsonNull();
            boolean actual = diagnostics.stream().anyMatch(d -> d.getCode() != null && d.getCode().isLeft() && "TE023".equals(d.getCode().getLeft()));
            if (expected) {
                int cp = row.get("failureCp").getAsInt();
                int offset = source.offsetByCodePoints(0, cp);
                String before = source.substring(0, offset);
                int line = (int) before.chars().filter(c -> c == '\n').count();
                int column = offset - before.lastIndexOf('\n') - 1;
                var syntax = diagnostics.stream().filter(d -> "tinyexpression-p4".equals(d.getSource())).findFirst().orElseThrow();
                assertEquals(source, new Position(line, column), syntax.getRange().getStart());
                var nativeFailure = server.nativeFailure(source);
                assertEquals(source, cp, nativeFailure.cp());
                var data = (Map<?, ?>) syntax.getData();
                assertEquals(source, offset, data.get("offset"));
                assertEquals(source, nativeFailure.hints(), data.get("expectedHints"));
                String op = row.get("operator").getAsString();
                int first = source.indexOf(op);
                assertEquals(op, CatalogProvider.operatorAtFailure(source, first));
                assertEquals(op, CatalogProvider.operatorAtFailure(source, first + 1));
            }
            System.out.println("operator oracle: " + row + " => TE023=" + actual);
            if (expected != actual) errors.add(row + " => " + diagnostics);
        }
        assertEquals(String.join("\n", errors), List.of(), errors);
    }
}
