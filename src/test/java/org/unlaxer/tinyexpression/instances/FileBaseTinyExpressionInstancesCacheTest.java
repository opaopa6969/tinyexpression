package org.unlaxer.tinyexpression.instances;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

public class FileBaseTinyExpressionInstancesCacheTest {

  @Test
  public void resolveUnderRoot_acceptsSimpleId() {
    Path root = Paths.get("/tmp/formula-root");
    Path resolved = FileBaseTinyExpressionInstancesCache.resolveUnderRoot(root, "69");
    assertEquals(root.resolve("69"), resolved);
    assertTrue(resolved.startsWith(root));
  }

  @Test
  public void resolveUnderRoot_rejectsTraversal() {
    Path root = Paths.get("/tmp/formula-root");
    assertThrows(IllegalArgumentException.class,
        () -> FileBaseTinyExpressionInstancesCache.resolveUnderRoot(root, "../../etc"));
  }

  @Test
  public void resolveUnderRoot_rejectsAbsolutePath() {
    Path root = Paths.get("/tmp/formula-root");
    assertThrows(IllegalArgumentException.class,
        () -> FileBaseTinyExpressionInstancesCache.resolveUnderRoot(root, "/etc"));
  }

  @Test
  public void resolveUnderRoot_rejectsNullRoot() {
    assertThrows(IllegalArgumentException.class,
        () -> FileBaseTinyExpressionInstancesCache.resolveUnderRoot(null, "69"));
  }
}
