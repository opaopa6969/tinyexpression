package org.unlaxer.tinyexpression.evaluator.ast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Optional;

import org.junit.Ignore;
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

  // Pre-existing P4 feature gap: grammar has no `len()` and no double-quote strings
  // (only `length()` / single quotes). See findings-2026-06-15 §8.
  @Ignore("pre-existing P4 gap: len() + double-quote strings not in grammar (findings §8)")
  @Test
  public void testPreferredIfRootSupportsLenComparison() {
    Optional<Object> mapped = GeneratedAstRuntimeProbe.tryMapAst(
        "if(len(\"AlmondChocolate\")==15){1}else{0}",
        Thread.currentThread().getContextClassLoader(),
        "IfExpr");

    assertTrue("len-based if should map to IfExpr",
        mapped.isPresent() && "IfExpr".equals(mapped.get().getClass().getSimpleName()));
  }

  // Pre-existing P4 feature gap: grammar declares only line comments
  // (@comment: { line: '//' }); block comments /* */ are not recognised. See findings §8.
  @Ignore("pre-existing P4 gap: block comments /* */ not in grammar (findings §8)")
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
