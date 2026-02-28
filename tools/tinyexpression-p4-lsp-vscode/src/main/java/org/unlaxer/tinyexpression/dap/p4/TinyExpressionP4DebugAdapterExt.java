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
 * </ul>
 * The parity probe (6-backend comparison) is already provided by
 * {@code TinyExpressionDapRuntimeBridge}, so no additional work is needed for P4-4.
 */
public class TinyExpressionP4DebugAdapterExt extends TinyExpressionP4DebugAdapter {

  // ── P4 probe state (populated in launch/configurationDone flow) ──

  private String capturedProgram = "";
  private String capturedRuntimeMode = "token";
  private boolean p4ParserUsed = false;
  private String p4AstNodeType = "not-evaluated";
  private String p4AstNodePath = "";

  // =========================================================================
  // launch — capture program path and runtime mode for our own use
  // =========================================================================

  @Override
  public CompletableFuture<Void> launch(Map<String, Object> args) {
    capturedProgram = String.valueOf(args.getOrDefault("program", ""));
    capturedRuntimeMode = String.valueOf(args.getOrDefault("runtimeMode", "token"));
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
      if (!"not-evaluated".equals(p4AstNodeType)) {
        List<Variable> vars = new ArrayList<>(Arrays.asList(response.getVariables()));
        vars.add(makeVar("_tinyP4ParserUsed", String.valueOf(p4ParserUsed)));
        vars.add(makeVar("_tinyP4AstNodeType", p4AstNodeType));
        if (!p4AstNodePath.isEmpty()) {
          vars.add(makeVar("_tinyP4AstNodePath", p4AstNodePath));
        }
        response.setVariables(vars.toArray(new Variable[0]));
      }
      return response;
    });
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
      case TinyExpressionP4AST.BooleanExpr be -> {}
      case TinyExpressionP4AST.ObjectExpr oe -> {}
      case TinyExpressionP4AST.ImportDeclarationExpr id -> {}
      case TinyExpressionP4AST.ExternalBooleanInvocationExpr eb -> {}
      case TinyExpressionP4AST.ExternalNumberInvocationExpr en -> {}
      case TinyExpressionP4AST.ExternalStringInvocationExpr es -> {}
      case TinyExpressionP4AST.ExternalObjectInvocationExpr eo -> {}
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
