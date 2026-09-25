import * as path from "path";
import * as vscode from "vscode";
import {
  LanguageClient,
  LanguageClientOptions,
  ServerOptions
} from "vscode-languageclient/node";

let client: LanguageClient | undefined;
let outputChannel: vscode.OutputChannel | undefined;

const FORMULA_INFO_SAMPLE = `tags:NORMAL
description:CalculationContext の値に 2 を足す最初の FormulaInfo
calculatorName:welcomeScore
var:score
dependsOn:
resultType:float
executionBackend:P4_AST_EVALUATOR
formula:
var $base as float set if not exists 40;
$base + 2
---END_OF_PART---
`;

function getBundledJarPath(context: vscode.ExtensionContext): string {
  return context.asAbsolutePath(
    path.join("server-dist", "tinyexpression-p4-lsp-server.jar")
  );
}

/**
 * Language catalog (issue #201). The server bundles catalog/tinyexpression-catalog.json (the
 * single source of variable / function descriptions and TE error texts). Settings:
 * - catalog.path: legacy .tecatalog files/directories, or a catalog .json, for variables;
 * - catalog.overridePath: a (partial) catalog .json merged over the bundled catalog, e.g. one
 *   exported from the playground;
 * - catalog.useBundledDefault: when catalog.path is empty, use the bundled catalog's variables.
 */
function resolveCatalogOptions(
  config: vscode.WorkspaceConfiguration
): { catalogPath: string; overridePath: string; useBundledVariables: boolean } {
  const catalogPath = config.get<string>("catalog.path", "").trim();
  const overridePath = config.get<string>("catalog.overridePath", "").trim();
  const useBundled = config.get<boolean>("catalog.useBundledDefault", true);
  return {
    catalogPath,
    overridePath,
    useBundledVariables: catalogPath.length === 0 && useBundled
  };
}

export async function activate(
  context: vscode.ExtensionContext
): Promise<void> {
  const config = vscode.workspace.getConfiguration("tinyExpressionP4Lsp");

  const javaPath: string = config.get<string>("server.javaPath", "java");
  const configuredJar: string = config.get<string>("server.jarPath", "");
  const jvmArgs: string[] = config.get<string[]>("server.jvmArgs", []) ?? [];

  const jarPath: string =
    configuredJar.trim().length > 0
      ? configuredJar
      : getBundledJarPath(context);

  const catalog = resolveCatalogOptions(config);

  outputChannel = vscode.window.createOutputChannel("TinyExpression P4 LSP");

  // Log startup info so users can diagnose server startup issues
  outputChannel.appendLine("[TinyExpression P4 LSP] Starting server...");
  outputChannel.appendLine(`  java: ${javaPath}`);
  outputChannel.appendLine(`  jar:  ${jarPath}`);
  outputChannel.appendLine(
    `  catalog: bundled${catalog.overridePath ? ` + override ${catalog.overridePath}` : ""}` +
    (catalog.catalogPath ? `; variables from ${catalog.catalogPath}`
      : catalog.useBundledVariables ? "; bundled variables" : "; no variables")
  );

  // Build JVM args: add catalog path as system property if available
  const catalogJvmArgs: string[] = catalog.catalogPath.length > 0
    ? [`-Dtinyexpressionp4.catalog.path=${catalog.catalogPath}`]
    : [];

  // ── LSP server ──
  // Launched as a fat jar via -jar; main class is TinyExpressionP4LspLauncherExt
  const serverOptions: ServerOptions = {
    command: javaPath,
    args: [...jvmArgs, ...catalogJvmArgs, "--enable-preview", "-jar", jarPath],
    options: {}
  };

  // Pass catalog path via initializationOptions so the server can use it
  // even if system property is not available (e.g. wrapped JVM)
  const initializationOptions: Record<string, unknown> = {
    useBundledVariables: catalog.useBundledVariables
  };
  if (catalog.catalogPath.length > 0) {
    initializationOptions.catalogPath = catalog.catalogPath;
  }
  if (catalog.overridePath.length > 0) {
    initializationOptions.catalogOverridePath = catalog.overridePath;
  }

  const clientOptions: LanguageClientOptions = {
    documentSelector: [{ scheme: "file", language: "tinyexpressionP4" }],
    outputChannel,
    initializationOptions
  };

  client = new LanguageClient(
    "tinyexpressionP4LanguageServer",
    "TinyExpression P4 Language Server",
    serverOptions,
    clientOptions
  );

  // ── showServerOutput command — registered early so it works even if server fails to start ──
  context.subscriptions.push(
    vscode.commands.registerCommand(
      "tinyExpressionP4Lsp.showServerOutput",
      () => {
        outputChannel?.show(true);
      }
    )
  );

  context.subscriptions.push(
    vscode.commands.registerCommand(
      "tinyExpressionP4Lsp.openLanguageGuide",
      async () => {
        const guide = vscode.Uri.joinPath(
          context.extensionUri,
          "docs",
          "language-guide.ja.md"
        );
        const document = await vscode.workspace.openTextDocument(guide);
        await vscode.window.showTextDocument(document, { preview: true });
        try {
          await vscode.commands.executeCommand("markdown.showPreview", guide);
        } catch (err: unknown) {
          outputChannel?.appendLine(
            `[TinyExpression P4 LSP] Markdown preview unavailable; opened guide as text. (${String(err)})`
          );
        }
      }
    ),
    vscode.commands.registerCommand(
      "tinyExpressionP4Lsp.openWalkthrough",
      () => vscode.commands.executeCommand(
        "workbench.action.openWalkthrough",
        `${context.extension.id}#gettingStarted`,
        false
      )
    ),
    vscode.commands.registerCommand(
      "tinyExpressionP4Lsp.createFormulaInfoSample",
      async () => {
        const document = await vscode.workspace.openTextDocument({
          language: "tinyexpressionP4",
          content: FORMULA_INFO_SAMPLE
        });
        await vscode.window.showTextDocument(document, { preview: false });
      }
    )
  );

  client.start().then(() => {
    outputChannel?.appendLine("[TinyExpression P4 LSP] Server started successfully.");
  }).catch((err: unknown) => {
    outputChannel?.appendLine(`[TinyExpression P4 LSP] Failed to start server: ${String(err)}`);
    void vscode.window.showErrorMessage(
      `TinyExpression P4 LSP: Failed to start Java server. Check Output > TinyExpression P4 LSP for details. (${String(err)})`
    );
  });

  context.subscriptions.push({
    dispose: () => {
      void client?.stop();
    }
  });

  // ── DAP adapter ──
  // Uses -cp (not -jar) so we can pass TinyExpressionP4DapLauncherExt as the
  // main class while reusing the same fat jar for all classes.
  const dapFactory: vscode.DebugAdapterDescriptorFactory = {
    createDebugAdapterDescriptor(
      _session: vscode.DebugSession
    ): vscode.ProviderResult<vscode.DebugAdapterDescriptor> {
      return new vscode.DebugAdapterExecutable(javaPath, [
        ...jvmArgs,
        "--enable-preview",
        "-cp",
        jarPath,
        "org.unlaxer.tinyexpression.dap.p4.TinyExpressionP4DapLauncherExt"
      ]);
    }
  };

  context.subscriptions.push(
    vscode.debug.registerDebugAdapterDescriptorFactory(
      "tinyexpressionP4",
      dapFactory
    )
  );
}

export async function deactivate(): Promise<void> {
  if (client != null) {
    await client.stop();
  }
}
