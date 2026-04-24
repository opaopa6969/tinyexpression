package org.unlaxer.tinyexpression.evaluator.ast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Optional;

import org.junit.Test;

public class GeneratedAstRuntimeProbeTest {

  @Test
  public void testPreferredIfRootDoesNotAcceptShallowMaxExpr() {
    Optional<Object> mapped = GeneratedAstRuntimeProbe.tryMapAst(
        "if(max(-1,-2)==-1){1}else{0}",
        Thread.currentThread().getContextClassLoader(),
        "IfExpr");

    assertTrue("preferred IfExpr mapping should be exact or rejected",
        mapped.isEmpty() || "IfExpr".equals(mapped.get().getClass().getSimpleName()));
  }

  @Test
  public void testPreferredIfRootDoesNotAcceptShallowBooleanExpr() {
    Optional<Object> mapped = GeneratedAstRuntimeProbe.tryMapAst(
        "if(false|false|false|(true&true)){1}else{0}",
        Thread.currentThread().getContextClassLoader(),
        "IfExpr");

    assertTrue("preferred IfExpr mapping should be exact or rejected",
        mapped.isEmpty() || "IfExpr".equals(mapped.get().getClass().getSimpleName()));
  }

  @Test
  public void testPreferredIfRootSupportsLenComparison() {
    Optional<Object> mapped = GeneratedAstRuntimeProbe.tryMapAst(
        "if(len(\"AlmondChocolate\")==15){1}else{0}",
        Thread.currentThread().getContextClassLoader(),
        "IfExpr");

    assertTrue("len-based if should map to IfExpr",
        mapped.isPresent() && "IfExpr".equals(mapped.get().getClass().getSimpleName()));
  }

  @Test
  public void testPreferredIfRootSupportsBlockCommentedIf() {
    Optional<Object> mapped = GeneratedAstRuntimeProbe.tryMapAst(
        "if(10==20 /*test*/) /*test*/{ /*test*/ 10/*test*/ }/*test*/ else/*test*/ {/*test*/ 0/*test*/}",
        Thread.currentThread().getContextClassLoader(),
        "IfExpr");

    assertTrue("block-commented if should map to IfExpr",
        mapped.isPresent() && "IfExpr".equals(mapped.get().getClass().getSimpleName()));
  }
}
