package org.unlaxer.tinyexpression.p4.ubnfc;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST;
import org.unlaxer.tinyexpression.p4.P4ParserEngine;
import org.unlaxer.tinyexpression.p4.P4PreferredAstMapper;
import org.unlaxer.tinyexpression.p4.ubnfc.generated.api.Json;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

/** Test-only production facade observer; never retries or catches parser failures. */
public final class FraudAcceptance {
  private FraudAcceptance() {}
  public static final long LIMIT_NS = 1_000_000_000L;
  private static final int[] LENGTHS = {404, 209, 68, 707, 459};

  public static String validated(P4PreferredAstMapper.ParsedAst parsed, String source) {
    assertNotNull("null parse result", parsed);
    assertNotNull("null mapped AST", parsed.ast());
    assertEquals("expected numeric conditional", TinyExpressionP4AST.IfExpr.class, parsed.ast().getClass());
    assertArrayEquals("partial source span", new int[] {0, source.codePointCount(0, source.length())},
        parsed.sourceText().spanOf(parsed.ast()).orElseThrow());
    assertEquals(source, parsed.sourceText().text(parsed.ast()));
    String canonical = CanonicalAst.json(parsed);
    validateSpans(Json.read(canonical), source.codePointCount(0, source.length()));
    return canonical;
  }

  private static void validateSpans(Object value, int length) {
    if (value instanceof Map<?, ?> map) {
      if (map.containsKey("fields") && map.get("type") instanceof String) {
        assertTrue("missing/invalid node span: " + map.get("type"), map.get("span") instanceof List<?>);
        var span = (List<?>) map.get("span");
        int start = ((Number) span.get(0)).intValue();
        int end = ((Number) span.get(1)).intValue();
        assertTrue("out of source CP span", start >= 0 && start <= end && end <= length);
      }
      map.values().forEach(child -> validateSpans(child, length));
    } else if (value instanceof List<?> list) {
      list.forEach(child -> validateSpans(child, length));
    }
  }

  public static void run() throws Exception {
    var records = new ArrayList<String>();
    var formulas = Files.readAllLines(Path.of("rust/tinyexpression-rs/tests/fixtures/fraud-alert.tsv"), StandardCharsets.UTF_8)
        .stream().filter(line -> !line.isBlank() && !line.startsWith("#")).toList();
    assertEquals("exact original five inputs", 5, formulas.size());
    String selectedId = System.getProperty("tinyexpression.fraud.id");
    if (selectedId != null) assertTrue("unknown fraud id", formulas.stream().anyMatch(line -> line.startsWith(selectedId + "\t")));
    Path output = Path.of(System.getProperty("tinyexpression.fraud.output", "target/fraud-acceptance/java.jsonl"));
    Files.createDirectories(output.getParent());
    Files.deleteIfExists(output); // no stale success record after a failed run
    for (int index = 0; index < formulas.size(); index++) {
      String[] columns = formulas.get(index).split("\t", -1);
      String id = columns[0], source = columns[1];
      if (selectedId != null && !selectedId.equals(id)) continue;
      assertEquals("pinned fraud CP length", LENGTHS[index], source.codePointCount(0, source.length()));
      String expected = null;
      for (String phase : List.of("cold", "warmup", "warm")) {
        // Only the production parse+map call is timed. Validation, serialization and I/O are outside.
        long start = System.nanoTime();
        var parsed = P4ParserEngine.with(P4ParserEngine.UBNFC,
            () -> P4PreferredAstMapper.parseDetailed(source, ExpressionTypes._float));
        long elapsed = System.nanoTime() - start;
        String canonical = validated(parsed, source);
        if (expected != null) assertEquals("phase changed AST/spans", expected, canonical);
        expected = canonical;
        var row = new LinkedHashMap<String, Object>();
        row.put("id", id); row.put("phase", phase);
        row.put("pid", ProcessHandle.current().pid());
        row.put("first_observation_in_process", records.isEmpty()); row.put("backend", "java-ubnfc");
        row.put("entry", "P4PreferredAstMapper.parseDetailed(_float)");
        row.put("memo", "ubnfc-default-packrat"); row.put("elapsed_ns", elapsed);
        row.put("observations", Map.of("diagnostics", true, "occurrences", true));
        row.put("sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8))));
        row.put("cp", LENGTHS[index]); row.put("accepted", true); row.put("diagnostic", null);
        row.put("selectionMode", parsed.selectionMode()); row.put("ast", Json.read(canonical));
        records.add(Json.write(row));
        // Persist even an over-budget sample; a slow/failed run is never a missing-data pass.
        Files.write(output, records, StandardCharsets.UTF_8);
        System.out.println("fraud production " + id + " " + phase + " " + elapsed + " ns");
        assertTrue(id + " " + phase + " must parse+map in < 1 second, was " + elapsed + " ns", elapsed < LIMIT_NS);
      }
    }
  }
}
