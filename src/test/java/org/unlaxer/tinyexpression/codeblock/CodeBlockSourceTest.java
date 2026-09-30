package org.unlaxer.tinyexpression.codeblock;

import static org.junit.Assert.*;
import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.unlaxer.tinyexpression.p4.P4ParserEngine;
import org.unlaxer.tinyexpression.p4.P4PreferredAstMapper;

public class CodeBlockSourceTest {
  private String fixture(String name) throws Exception {
    try (var input = getClass().getResourceAsStream("/" + name + ".tsv")) {
      assertNotNull(input);
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
  private static String unescape(String text) {
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      out.append(c == '\\' ? switch (text.charAt(++i)) {
        case 'n' -> '\n'; case 'r' -> '\r'; case 't' -> '\t'; case '\\' -> '\\';
        default -> throw new AssertionError("invalid fixture escape");
      } : c);
    }
    return out.toString();
  }

  @Test public void sharedSourceCorpus() throws Exception {
    for (String row : fixture("code-block-source").lines().filter(r -> !r.startsWith("#")).toList()) {
      String[] f = row.split("\t", -1);
      String source = unescape(f[1]);
      if (f[2].equals("invalid")) {
        for (var engine : P4ParserEngine.values()) {
          assertThrows(f[0] + " " + engine, IllegalArgumentException.class, () ->
              P4ParserEngine.with(engine, () -> CodeBlockSource.parse(source)));
        }
        continue;
      }
      var parsed = P4PreferredAstMapper.parseDetailed(source, null);
      var blocks = CodeBlockSource.fromAst(parsed);
      assertEquals(f[0], blocks, CodeBlockSource.parse(source));
      for (var engine : P4ParserEngine.values()) {
        var other = P4ParserEngine.with(engine, () -> P4PreferredAstMapper.parseDetailed(source, null));
        assertEquals(f[0] + " " + engine, blocks, CodeBlockSource.fromAst(other));
      }
      if (f[2].equals("none")) { assertTrue(f[0], blocks.isEmpty()); continue; }
      assertEquals(f[0], 1, blocks.size());
      var b = blocks.get(0);
      assertEquals(f[3], b.scheme()); assertEquals(f[4], b.identifier()); assertEquals(unescape(f[5]), b.body());
      int[] positions = { b.span().start(), b.span().end(), b.bodySpan().start(), b.bodySpan().end(), b.nameSpan().start(), b.nameSpan().end() };
      assertArrayEquals(f[0], Arrays.stream(f, 6, f.length).mapToInt(Integer::parseInt).toArray(), positions);
    }
  }

  @Test public void sharedPreflightCorpus() throws Exception {
    for (String row : fixture("code-block-preflight").lines().filter(r -> !r.startsWith("#")).toList()) {
      String[] f = row.split("\t", -1);
      var blocks = CodeBlockSource.parse(unescape(f[1]));
      var errors = CodeBlockSource.preflight(blocks, f[2].equals("rust") ? CodeBlockSource.Target.RUST : CodeBlockSource.Target.JAVA,
          Boolean.parseBoolean(f[3]));
      String actual = errors.isEmpty() ? "ok" : errors.stream()
          .map(d -> d.code() + ":" + d.span().start() + ":" + d.span().end()).collect(Collectors.joining(";"));
      assertEquals(f[0], f[4], actual);
    }
  }

  @Test public void allSourceCalculatorBackendsRejectUncompiledRust() {
    String source = "```rust:demo\nnot valid Rust\n```\n1";
    assertNotNull(org.unlaxer.tinyexpression.p4.P4PreferredAstMapper.parse(source));
    for (var backend : org.unlaxer.tinyexpression.runtime.ExecutionBackend.values()) {
      var creator = org.unlaxer.tinyexpression.loader.model.CalculatorCreatorRegistry.forBackend(backend);
      var error = assertThrows(Exception.class, () -> creator.create(
          new org.unlaxer.tinyexpression.Source(source), "NoRustExecution",
          new org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes(
              org.unlaxer.tinyexpression.parser.ExpressionTypes._float,
              org.unlaxer.tinyexpression.parser.ExpressionTypes._float),
          getClass().getClassLoader()));
      assertTrue(backend.toString(), error.getMessage().contains("CB005"));
      Throwable cause = error;
      while (cause.getCause() != null) cause = cause.getCause();
      assertTrue(backend.toString(), cause instanceof UnsupportedOperationException);
    }
  }
  @Test public void retainsBodyAndSpans() {
    String source = "// 😀\n```rust:demo\r\n  // keep\r\npub fn value() -> i32 { 7 }\r\n```\r\n1";
    var blocks = CodeBlockSource.parse(source);
    assertEquals(1, blocks.size());
    var block = blocks.get(0);
    assertEquals("rust", block.scheme());
    assertEquals("demo", block.identifier());
    assertEquals("  // keep\r\npub fn value() -> i32 { 7 }\r\n", block.body());
    assertEquals(5, block.span().start());
    assertEquals("CB004", CodeBlockSource.preflight(blocks, CodeBlockSource.Target.RUST, false).get(0).code());
    assertTrue(CodeBlockSource.preflight(blocks, CodeBlockSource.Target.RUST, true).isEmpty());
    assertEquals("CB003", CodeBlockSource.preflight(blocks, CodeBlockSource.Target.JAVA, true).get(0).code());
  }

  @Test public void detachedAstRejectsRustBeforeEffects() {
    var types = new org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes(
        org.unlaxer.tinyexpression.parser.ExpressionTypes._float,
        org.unlaxer.tinyexpression.parser.ExpressionTypes._float);
    for (var engine : P4ParserEngine.values()) {
      var parsed = P4ParserEngine.with(engine, () -> P4PreferredAstMapper.parseDetailed(
          "```rust:demo\n// retained\n```\nvar $x set 9; 1", null));
      var formula = (org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST.FormulaExpr) parsed.ast();
      assertEquals("```rust:demo\n// retained\n```", formula.codeBlocks().get(0).source());
      var context = new org.unlaxer.tinyexpression.NormalCalculationContext(
          2, java.math.RoundingMode.HALF_UP, org.unlaxer.tinyexpression.CalculationContext.Angle.DEGREE);
      context.set("x", 3f);
      var evaluator = new org.unlaxer.tinyexpression.evaluator.ast.P4TypedAstEvaluator(types, context);
      for (var ast : java.util.List.of(formula, formula.codeBlocks().get(0))) {
        assertTrue(assertThrows(UnsupportedOperationException.class, () -> evaluator.eval(ast))
            .getMessage().contains("CB005"));
        assertTrue(assertThrows(UnsupportedOperationException.class, () ->
            new org.unlaxer.tinyexpression.evaluator.javacode.P4DefaultJavaCodeEmitter(types).eval(ast))
            .getMessage().contains("CB005"));
        assertTrue(assertThrows(UnsupportedOperationException.class, () ->
            new org.unlaxer.tinyexpression.evaluator.javacode.P4TemplateJavaCodeEmitter(types).eval(ast))
            .getMessage().contains("CB005"));
        assertTrue(assertThrows(UnsupportedOperationException.class, () ->
            new org.unlaxer.tinyexpression.evaluator.javacode.P4TypedJavaCodeEmitter(types).eval(ast))
            .getMessage().contains("CB005"));
      }
      assertEquals(3f, context.getNumber("x").orElseThrow().floatValue(), 0);
      // Position lookup is a snapshot, not the latest generated mapper's global state.
      P4PreferredAstMapper.parse("1+2");
      assertEquals("// retained\n", CodeBlockSource.fromAst(parsed).get(0).body());
      assertThrows(IllegalArgumentException.class, () -> CodeBlockSource.fromAst(formula,
          org.unlaxer.tinyexpression.p4.P4SourceText.lexicalOnly()));
    }
  }

  @Test public void documentReselectionKeepsEveryBlock() {
    String source = "// 😀\n```java:One\n// one\n```\n```rust:Two\r\n'\r\n```\r\n"
        + "var $s as string;$s as string";
    for (var engine : P4ParserEngine.values()) {
      var parsed = P4ParserEngine.with(engine, () -> P4PreferredAstMapper.parseDetailed(source, null));
      var blocks = CodeBlockSource.fromAst(parsed);
      assertEquals(java.util.List.of("One", "Two"), blocks.stream().map(CodeBlockSource.Block::identifier).toList());
      assertEquals(java.util.List.of("// one\n", "'\r\n"), blocks.stream().map(CodeBlockSource.Block::body).toList());
      assertEquals("CB005", assertThrows(UnsupportedOperationException.class, () ->
          CodeBlockSource.rejectUncompiledRust(parsed.ast())).getMessage().substring(0, 5));
      for (var block : blocks) {
        int[] chars = source.codePoints().toArray();
        assertEquals(block.body(), new String(chars, block.bodySpan().start(), block.bodySpan().end() - block.bodySpan().start()));
      }
    }
  }
}
