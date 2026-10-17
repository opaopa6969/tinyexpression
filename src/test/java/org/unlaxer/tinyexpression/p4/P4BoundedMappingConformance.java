package org.unlaxer.tinyexpression.p4;

import static org.junit.Assert.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.Test;

/** Explicitly invoked by the fixed bounded-parser CI job; historical dependencies have no such cap. */
public class P4BoundedMappingConformance {
  @Test public void pinnedBoundedInventoryHasTheExactCorpusBoundary() throws Exception {
    var stats = P4ImplicitMappingTest.compareCorpus();
    assertEquals(324, stats.compared());
    assertEquals(12, stats.rejectedAtParse());
    assertEquals(4, stats.cappedInventory());
    assertEquals(Set.of(
        Files.readString(Path.of("benchmarks/fixtures/complex-x64.tiny")),
        Files.readString(Path.of("benchmarks/fixtures/comparison-heavy-x64.tiny"))), stats.cappedSources());
  }

  @Test public void pinnedBoundedInventoryRejectsBothEstablishedLargeInputs() throws Exception {
    int checked = 0;
    for (String line : Files.readAllLines(Path.of(
        "rust/tinyexpression-rs/tests/fixtures/default-mapping-inputs.tsv"))) {
      if (line.isBlank() || line.startsWith("#")) continue;
      String[] row = line.split("\\t");
      assertEquals(8, row.length);
      String source = Files.readString(Path.of(row[1]));
      var root = P4ImplicitMappingTest.parse(source);
      assertEquals("E-MAPPING-LIMIT: preferred candidates", assertThrows(IllegalArgumentException.class,
          () -> P4SourceMapping.mapOnce(root.token(), root.legacy(), source, root.entry())).getMessage());
      checked++;
    }
    assertEquals(2, checked);
  }
}
