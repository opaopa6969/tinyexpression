package org.unlaxer.tinyexpression.dap.p4;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.debug.Variable;
import org.eclipse.lsp4j.debug.VariablesArguments;
import org.eclipse.lsp4j.debug.VariablesResponse;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4DebugAdapter;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4Mapper;

/**
 * Extended DAP adapter for TinyExpression P4.
 * <p>
 * Extends the generated {@link TinyExpressionP4DebugAdapter} with:
 * <ul>
 *   <li>P4 runtime markers ({@code _tinyP4ParserUsed}, {@code _tinyP4AstNodeType}) in the
 *       variables response</li>
 *   <li>P4 AST node path visible in the variables panel when parse succeeds</li>
 *   <li>{@code variables} in {@code launch.json} — inject variable values into the debug session</li>
 *   <li>{@code evaluate()} — evaluate TinyExpression P4 expressions in the Debug Console
 *       using the injected variables</li>
 * </ul>
 * The parity probe (6-backend comparison) is already provided by
 * {@code TinyExpressionDapRuntimeBridge}, so no additional work is needed for P4-4.
 */
public class TinyExpressionP4DebugAdapterExt extends TinyExpressionP4DebugAdapter {

  // ── P4 probe state (populated in launch/configurationDone flow) ──

  private String capturedProgram = "";
  private String capturedRuntimeMode = "ast-evaluator";
  private boolean p4ParserUsed = false;
  private String p4AstNodeType = "not-evaluated";
  private String p4AstNodePath = "";

  // ── Injected variables from launch.json "variables" map ──

  private final java.util.Map<String, String> injectedVariables = new java.util.LinkedHashMap<>();

  // =========================================================================
  // launch — capture program path and runtime mode for our own use
  // =========================================================================

  @Override
  public CompletableFuture<Void> launch(Map<String, Object> args) {
    capturedProgram = String.valueOf(args.getOrDefault("program", ""));
    capturedRuntimeMode = String.valueOf(args.getOrDefault("runtimeMode", "ast-evaluator"));

    // Read "variables" map from launch.json and store for evaluate() / variables()
    injectedVariables.clear();
    Object rawVariables = args.get("variables");
    if (rawVariables instanceof Map<?, ?> varMap) {
      for (Map.Entry<?, ?> entry : varMap.entrySet()) {
        if (entry.getKey() instanceof String key && entry.getValue() != null) {
          injectedVariables.put(key, String.valueOf(entry.getValue()));
        }
      }
    }

    return super.launch(args);
  }

  // =========================================================================
  // configurationDone — run P4 probe after base parse
  // =========================================================================

  @Override
  public CompletableFuture<Void> configurationDone(
      org.eclipse.lsp4j.debug.ConfigurationDoneArguments args) {
    CompletableFuture<Void> base = super.configurationDone(args);
    return base.thenRun(this::runP4Probe);
  }

  // =========================================================================
  // variables — append P4 runtime markers
  // =========================================================================

  @Override
  public CompletableFuture<VariablesResponse> variables(VariablesArguments args) {
    return super.variables(args).thenApply(response -> {
      List<Variable> vars = new ArrayList<>(Arrays.asList(response.getVariables()));

      // Show injected variables (from launch.json "variables" map) in the Variables view
      for (Map.Entry<String, String> entry : injectedVariables.entrySet()) {
        vars.add(makeVar("$" + entry.getKey(), entry.getValue()));
      }

      // Show P4 probe metadata
      if (!"not-evaluated".equals(p4AstNodeType)) {
        vars.add(makeVar("_tinyP4ParserUsed", String.valueOf(p4ParserUsed)));
        vars.add(makeVar("_tinyP4AstNodeType", p4AstNodeType));
        if (!p4AstNodePath.isEmpty()) {
          vars.add(makeVar("_tinyP4AstNodePath", p4AstNodePath));
        }
      }

      response.setVariables(vars.toArray(new Variable[0]));
      return response;
    });
  }

  // =========================================================================
  // evaluate — Debug Console expression evaluation with variable substitution
  // =========================================================================

  @Override
  public CompletableFuture<org.eclipse.lsp4j.debug.EvaluateResponse> evaluate(
      org.eclipse.lsp4j.debug.EvaluateArguments args) {
    String expression = args.getExpression();
    if (expression == null || expression.isBlank()) {
      org.eclipse.lsp4j.debug.EvaluateResponse response = new org.eclipse.lsp4j.debug.EvaluateResponse();
      response.setResult("");
      response.setVariablesReference(0);
      return CompletableFuture.completedFuture(response);
    }

    // Substitute $variableName → injected value
    String substituted = substituteVariables(expression.strip());

    // Try to evaluate as arithmetic expression
    String result;
    try {
      result = evaluateArithmetic(substituted);
    } catch (Exception e) {
      // Not a numeric expression — return the substituted text as-is
      result = substituted;
    }

    org.eclipse.lsp4j.debug.EvaluateResponse response = new org.eclipse.lsp4j.debug.EvaluateResponse();
    response.setResult(result);
    response.setVariablesReference(0);
    return CompletableFuture.completedFuture(response);
  }

  /**
   * Substitutes {@code $varName} references with values from {@link #injectedVariables}.
   */
  private String substituteVariables(String expr) {
    java.util.regex.Matcher m = java.util.regex.Pattern
        .compile("\\$([a-zA-Z_][a-zA-Z0-9_]*)").matcher(expr);
    StringBuilder sb = new StringBuilder();
    while (m.find()) {
      String varName = m.group(1);
      String value = injectedVariables.getOrDefault(varName, m.group(0));
      m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(value));
    }
    m.appendTail(sb);
    return sb.toString();
  }

  /**
   * Evaluates a simple arithmetic expression (supports +, -, *, / with integer and decimal
   * operands). Delegates to a minimal recursive-descent evaluator to avoid external deps.
   */
  private static String evaluateArithmetic(String expr) {
    double result = parseAddSub(new int[]{0}, expr.replaceAll("\\s+", ""));
    if (result == Math.floor(result) && !Double.isInfinite(result)) {
      return String.valueOf((long) result);
    }
    return String.valueOf(result);
  }

  private static double parseAddSub(int[] pos, String expr) {
    double left = parseMulDiv(pos, expr);
    while (pos[0] < expr.length()) {
      char op = expr.charAt(pos[0]);
      if (op == '+' || op == '-') {
        pos[0]++;
        double right = parseMulDiv(pos, expr);
        left = (op == '+') ? left + right : left - right;
      } else {
        break;
      }
    }
    return left;
  }

  private static double parseMulDiv(int[] pos, String expr) {
    double left = parseAtom(pos, expr);
    while (pos[0] < expr.length()) {
      char op = expr.charAt(pos[0]);
      if (op == '*' || op == '/') {
        pos[0]++;
        double right = parseAtom(pos, expr);
        left = (op == '*') ? left * right : left / right;
      } else {
        break;
      }
    }
    return left;
  }

  private static double parseAtom(int[] pos, String expr) {
    if (pos[0] >= expr.length()) throw new IllegalArgumentException("Unexpected end");
    if (expr.charAt(pos[0]) == '(') {
      pos[0]++; // skip '('
      double val = parseAddSub(pos, expr);
      if (pos[0] < expr.length() && expr.charAt(pos[0]) == ')') pos[0]++;
      return val;
    }
    if (expr.charAt(pos[0]) == '-') {
      pos[0]++;
      return -parseAtom(pos, expr);
    }
    int start = pos[0];
    while (pos[0] < expr.length() && (Character.isDigit(expr.charAt(pos[0])) || expr.charAt(pos[0]) == '.')) {
      pos[0]++;
    }
    if (pos[0] == start) throw new IllegalArgumentException("Expected number at " + pos[0]);
    return Double.parseDouble(expr.substring(start, pos[0]));
  }

  // =========================================================================
  // Private helpers
  // =========================================================================

  private void runP4Probe() {
    if (capturedProgram == null || capturedProgram.isEmpty()) return;
    String content;
    try {
      content = Files.readString(Path.of(capturedProgram));
    } catch (IOException e) {
      p4AstNodeType = "file-read-error";
      return;
    }
    try {
      TinyExpressionP4AST ast = TinyExpressionP4Mapper.parse(content.strip());
      p4ParserUsed = true;
      p4AstNodeType = ast.getClass().getSimpleName();
      p4AstNodePath = buildAstNodePath(ast);
    } catch (Exception e) {
      p4ParserUsed = false;
      p4AstNodeType = "parse-failed";
      p4AstNodePath = "";
    }
  }

  /**
   * Builds a breadth-first type-path string for the AST root node.
   * Uses the sealed {@link TinyExpressionP4AST} hierarchy — no reflection.
   */
  private static String buildAstNodePath(TinyExpressionP4AST ast) {
    List<String> path = new ArrayList<>();
    collectAstTypeNames(ast, path, 0, 3); // depth limit 3
    return String.join(" > ", path);
  }

  /** Type-safe AST visitor using sealed interface pattern matching. */
  private static void collectAstTypeNames(TinyExpressionP4AST node, List<String> path,
      int depth, int maxDepth) {
    if (node == null || depth > maxDepth) return;
    path.add(node.getClass().getSimpleName());
    // Recurse into meaningful child nodes (type-safe, no reflection)
    switch (node) {
      case TinyExpressionP4AST.BinaryExpr b -> {
        collectAstTypeNames(b.left(), path, depth + 1, maxDepth);
        b.right().stream().limit(1)
            .forEach(r -> collectAstTypeNames(r, path, depth + 1, maxDepth));
      }
      case TinyExpressionP4AST.StringComparisonExpr sc2 -> {
        collectAstTypeNames(sc2.left(), path, depth + 1, maxDepth);
        collectAstTypeNames(sc2.right(), path, depth + 1, maxDepth);
      }
      case TinyExpressionP4AST.ComparisonExpr c -> {
        collectAstTypeNames(c.left(), path, depth + 1, maxDepth);
        collectAstTypeNames(c.right(), path, depth + 1, maxDepth);
      }
      case TinyExpressionP4AST.IfExpr i -> {
        collectAstTypeNames(i.condition(), path, depth + 1, maxDepth);
        collectAstTypeNames(i.thenExpr(), path, depth + 1, maxDepth);
      }
      case TinyExpressionP4AST.ExpressionExpr e -> {}
      case TinyExpressionP4AST.MethodInvocationExpr m -> {}
      case TinyExpressionP4AST.VariableRefExpr v -> {}
      case TinyExpressionP4AST.NumberMatchExpr nm -> {
        collectAstTypeNames(nm.firstCase(), path, depth + 1, maxDepth);
      }
      case TinyExpressionP4AST.StringMatchExpr sm -> {
        collectAstTypeNames(sm.firstCase(), path, depth + 1, maxDepth);
      }
      case TinyExpressionP4AST.BooleanMatchExpr bm -> {
        collectAstTypeNames(bm.firstCase(), path, depth + 1, maxDepth);
      }
      case TinyExpressionP4AST.NumberCaseExpr nc -> {
        collectAstTypeNames(nc.condition(), path, depth + 1, maxDepth);
      }
      case TinyExpressionP4AST.StringCaseExpr sc -> {
        collectAstTypeNames(sc.condition(), path, depth + 1, maxDepth);
      }
      case TinyExpressionP4AST.BooleanCaseExpr bc -> {
        collectAstTypeNames(bc.condition(), path, depth + 1, maxDepth);
      }
      case TinyExpressionP4AST.NumberDefaultCaseExpr nd -> {}
      case TinyExpressionP4AST.StringDefaultCaseExpr sd -> {}
      case TinyExpressionP4AST.BooleanDefaultCaseExpr bd -> {}
      case TinyExpressionP4AST.NumberCaseValueExpr nv -> {
        collectAstTypeNames(nv.value(), path, depth + 1, maxDepth);
      }
      case TinyExpressionP4AST.StringCaseValueExpr sv -> {
        collectAstTypeNames(sv.value(), path, depth + 1, maxDepth);
      }
      case TinyExpressionP4AST.BooleanCaseValueExpr bv -> {
        collectAstTypeNames(bv.value(), path, depth + 1, maxDepth);
      }
      case TinyExpressionP4AST.StringExpr se -> {}
      case TinyExpressionP4AST.BooleanOrExpr be -> {}
      case TinyExpressionP4AST.ObjectExpr oe -> {}
      case TinyExpressionP4AST.CodeBlockExpr cb -> {}
      case TinyExpressionP4AST.ImportDeclarationExpr id -> {}
      case TinyExpressionP4AST.ExternalBooleanInvocationExpr eb -> {}
      case TinyExpressionP4AST.ExternalNumberInvocationExpr en -> {}
      case TinyExpressionP4AST.ExternalStringInvocationExpr es -> {}
      case TinyExpressionP4AST.ExternalObjectInvocationExpr eo -> {}
      case TinyExpressionP4AST.BooleanAndExpr ba -> {}
      case TinyExpressionP4AST.BooleanXorExpr bx -> {}
      case TinyExpressionP4AST.BooleanFactorExpr bf -> {}
      case TinyExpressionP4AST.SinExpr s -> {}
      case TinyExpressionP4AST.CosExpr s -> {}
      case TinyExpressionP4AST.TanExpr s -> {}
      case TinyExpressionP4AST.SqrtExpr s -> {}
      case TinyExpressionP4AST.MinExpr s -> {}
      case TinyExpressionP4AST.MaxExpr s -> {}
      case TinyExpressionP4AST.RandomExpr s -> {}
      case TinyExpressionP4AST.AbsExpr s -> {}
      case TinyExpressionP4AST.RoundExpr s -> {}
      case TinyExpressionP4AST.CeilExpr s -> {}
      case TinyExpressionP4AST.FloorExpr s -> {}
      case TinyExpressionP4AST.PowExpr s -> {}
      case TinyExpressionP4AST.LogExpr s -> {}
      case TinyExpressionP4AST.ExpExpr s -> {}
      case TinyExpressionP4AST.NotExpr s -> {}
      case TinyExpressionP4AST.ToNumExpr s -> {}
      case TinyExpressionP4AST.ToUpperCaseExpr s -> {}
      case TinyExpressionP4AST.ToLowerCaseExpr s -> {}
      case TinyExpressionP4AST.TrimExpr s -> {}
      case TinyExpressionP4AST.LengthExpr s -> {}
      case TinyExpressionP4AST.ToUpperCaseDotExpr s -> {}
      case TinyExpressionP4AST.ToLowerCaseDotExpr s -> {}
      case TinyExpressionP4AST.TrimDotExpr s -> {}
      case TinyExpressionP4AST.LengthDotExpr s -> {}
      case TinyExpressionP4AST.ArgumentExpressionExpr s -> {}
    }
  }

  private static Variable makeVar(String name, String value) {
    Variable v = new Variable();
    v.setName(name);
    v.setValue(value);
    v.setType("String");
    v.setVariablesReference(0);
    return v;
  }
}
