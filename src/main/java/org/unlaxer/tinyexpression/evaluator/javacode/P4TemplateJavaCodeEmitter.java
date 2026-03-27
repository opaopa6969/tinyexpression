package org.unlaxer.tinyexpression.evaluator.javacode;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST.*;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4Evaluator;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

/**
 * Strategy: template — Java code generation using external template files.
 * <p>
 * Loads {@code .java.tmpl} files from {@code eval-templates/} resources
 * and expands {{variables}} to produce Java source code.
 * <p>
 * Template resolution order:
 * <ol>
 *   <li>Custom template specified in {@code @eval(strategy=template("file.java.tmpl"))}</li>
 *   <li>Default template for the kind: {@code eval-templates/{kind}.java.tmpl}</li>
 *   <li>Fallback to {@link P4DefaultJavaCodeEmitter} behavior</li>
 * </ol>
 *
 * This class demonstrates the template strategy. In the full implementation,
 * the EvaluatorGenerator would read templates and inline the expanded code
 * at generation time (compile-time templating, not runtime).
 */
public class P4TemplateJavaCodeEmitter extends TinyExpressionP4Evaluator<String> {

  private final P4DefaultJavaCodeEmitter defaultEmitter;
  private final Map<String, String> templateOverrides;
  private final Map<String, String> templateCache = new HashMap<>();

  /**
   * @param types expression type context
   * @param templateOverrides map of kind -> template resource path overrides
   */
  public P4TemplateJavaCodeEmitter(SpecifiedExpressionTypes types, Map<String, String> templateOverrides) {
    this.defaultEmitter = new P4DefaultJavaCodeEmitter(types);
    this.templateOverrides = templateOverrides != null ? templateOverrides : Map.of();
  }

  public P4TemplateJavaCodeEmitter(SpecifiedExpressionTypes types) {
    this(types, Map.of());
  }

  public String buildJavaClass(String className, String expression) {
    return defaultEmitter.buildJavaClass(className, expression);
  }

  /**
   * Load a template file. Returns null if not found.
   */
  private String loadTemplate(String kind) {
    return templateCache.computeIfAbsent(kind, k -> {
      String path = templateOverrides.getOrDefault(k, "eval-templates/" + k + ".java.tmpl");
      try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
        if (is == null) return null;
        return new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
            .lines().collect(Collectors.joining("\n"));
      } catch (Exception e) {
        return null;
      }
    });
  }

  /**
   * Check if a template exists for the given kind.
   * If yes, log which strategy is in use (for diagnostics).
   */
  private boolean hasTemplate(String kind) {
    return loadTemplate(kind) != null;
  }

  // =========================================================================
  // Dispatch: try template, fall back to default
  // =========================================================================

  @Override
  protected String evalBinaryExpr(BinaryExpr node) {
    // Template exists for "binary_arithmetic" but runtime expansion
    // is not yet implemented — this shows the strategy selection pattern.
    // In the full EvaluatorGenerator, the template would be expanded
    // at code generation time, not at runtime.
    if (hasTemplate("binary_arithmetic")) {
      // Template strategy: delegate to default (which implements the
      // same logic that the template describes)
      return defaultEmitter.eval(node);
    }
    return defaultEmitter.eval(node);
  }

  @Override
  protected String evalVariableRefExpr(VariableRefExpr node) {
    return defaultEmitter.eval(node);
  }

  @Override
  protected String evalIfExpr(IfExpr node) {
    return defaultEmitter.eval(node);
  }

  @Override
  protected String evalComparisonExpr(ComparisonExpr node) {
    return defaultEmitter.eval(node);
  }

  @Override
  protected String evalStringComparisonExpr(StringComparisonExpr node) {
    return defaultEmitter.eval(node);
  }

  @Override
  protected String evalStringExpr(StringExpr node) {
    return defaultEmitter.eval(node);
  }

  @Override
  protected String evalBooleanOrExpr(BooleanOrExpr node) {
    return defaultEmitter.eval(node);
  }

  @Override
  protected String evalBooleanAndExpr(BooleanAndExpr node) {
    return defaultEmitter.eval(node);
  }

  @Override
  protected String evalBooleanXorExpr(BooleanXorExpr node) {
    return defaultEmitter.eval(node);
  }

  @Override
  protected String evalBooleanFactorExpr(BooleanFactorExpr node) {
    return defaultEmitter.eval(node);
  }

  @Override
  protected String evalExpressionExpr(ExpressionExpr node) {
    return defaultEmitter.eval(node);
  }

  @Override
  protected String evalObjectExpr(ObjectExpr node) {
    return defaultEmitter.eval(node);
  }

  @Override protected String evalNumberMatchExpr(NumberMatchExpr n) { return defaultEmitter.eval(n); }
  @Override protected String evalStringMatchExpr(StringMatchExpr n) { return defaultEmitter.eval(n); }
  @Override protected String evalBooleanMatchExpr(BooleanMatchExpr n) { return defaultEmitter.eval(n); }
  @Override protected String evalNumberCaseExpr(NumberCaseExpr n) { return defaultEmitter.eval(n); }
  @Override protected String evalNumberDefaultCaseExpr(NumberDefaultCaseExpr n) { return defaultEmitter.eval(n); }
  @Override protected String evalNumberCaseValueExpr(NumberCaseValueExpr n) { return defaultEmitter.eval(n); }
  @Override protected String evalStringCaseExpr(StringCaseExpr n) { return defaultEmitter.eval(n); }
  @Override protected String evalStringDefaultCaseExpr(StringDefaultCaseExpr n) { return defaultEmitter.eval(n); }
  @Override protected String evalStringCaseValueExpr(StringCaseValueExpr n) { return defaultEmitter.eval(n); }
  @Override protected String evalBooleanCaseExpr(BooleanCaseExpr n) { return defaultEmitter.eval(n); }
  @Override protected String evalBooleanDefaultCaseExpr(BooleanDefaultCaseExpr n) { return defaultEmitter.eval(n); }
  @Override protected String evalBooleanCaseValueExpr(BooleanCaseValueExpr n) { return defaultEmitter.eval(n); }

  @Override protected String evalMethodInvocationExpr(MethodInvocationExpr n) { return defaultEmitter.eval(n); }
  @Override protected String evalExternalBooleanInvocationExpr(ExternalBooleanInvocationExpr n) { return defaultEmitter.eval(n); }
  @Override protected String evalExternalNumberInvocationExpr(ExternalNumberInvocationExpr n) { return defaultEmitter.eval(n); }
  @Override protected String evalExternalStringInvocationExpr(ExternalStringInvocationExpr n) { return defaultEmitter.eval(n); }
  @Override protected String evalExternalObjectInvocationExpr(ExternalObjectInvocationExpr n) { return defaultEmitter.eval(n); }
  @Override protected String evalCodeBlockExpr(CodeBlockExpr n) { return defaultEmitter.eval(n); }
  @Override protected String evalImportDeclarationExpr(ImportDeclarationExpr n) { return defaultEmitter.eval(n); }

  // Math functions + Not + ToNum
  @Override protected String evalSinExpr(SinExpr n) { return defaultEmitter.eval(n); }
  @Override protected String evalCosExpr(CosExpr n) { return defaultEmitter.eval(n); }
  @Override protected String evalTanExpr(TanExpr n) { return defaultEmitter.eval(n); }
  @Override protected String evalSqrtExpr(SqrtExpr n) { return defaultEmitter.eval(n); }
  @Override protected String evalMinExpr(MinExpr n) { return defaultEmitter.eval(n); }
  @Override protected String evalMaxExpr(MaxExpr n) { return defaultEmitter.eval(n); }
  @Override protected String evalRandomExpr(RandomExpr n) { return defaultEmitter.eval(n); }
  @Override protected String evalNotExpr(NotExpr n) { return defaultEmitter.eval(n); }
  @Override protected String evalToNumExpr(ToNumExpr n) { return defaultEmitter.eval(n); }
}
