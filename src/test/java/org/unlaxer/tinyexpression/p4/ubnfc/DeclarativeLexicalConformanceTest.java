package org.unlaxer.tinyexpression.p4.ubnfc;

import static org.junit.Assert.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.Test;
import org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4Parser;
import org.unlaxer.tinyexpression.p4.ubnfc.generated.api.ParseOptions;

/** Same source, cursor and AST-value cases as Rust, with no external scanner registry. */
public class DeclarativeLexicalConformanceTest {
    @Test public void sharedLexicalCasesNeedNoHostParserMapping() throws Exception {
        var rows = new ObjectMapper().readTree(Files.readString(Path.of("src/test/resources/p4-lexical-conformance.json")));
        for (var row : rows) {
            String id = row.get("id").asText(), input = row.get("input").asText();
            boolean ok = row.get("ok").asBoolean();
            int end = row.has("prefix") ? row.get("prefix").asInt() : ok ? input.codePointCount(0, input.length()) : 0;
            var result = TinyExpressionP4Parser.parseEntry("TinyExpressionP4", row.get("entry").asText(), input,
                new ParseOptions(true, false, false, true, Map.of()));
            assertEquals(id, ok, result.ok());
            assertEquals(id, end, result.consumedCp());
            assertEquals(id, end, result.matchedCp());
            if (row.has("value")) {
                var raw = (org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.StringConcatExpr) result.ast().orElseThrow();
                assertEquals(id, input, raw.left());
                var converter = new UbnfcAstConverter(result.nodeSpans(), input);
                var ast = (org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST.StringConcatExpr) converter.convert(raw);
                assertEquals(id, row.get("value").asText(), ast.left());
                assertArrayEquals(id, new int[]{0, end}, converter.spans().get(ast));
            }
        }
    }
}
