import * as fs from "fs";
import * as path from "path";
import * as vscode from "vscode";
import {
  LanguageClient,
  LanguageClientOptions,
  ServerOptions
} from "vscode-languageclient/node";

let client: LanguageClient | undefined;
let outputChannel: vscode.OutputChannel | undefined;

function getBundledJarPath(context: vscode.ExtensionContext): string {
  return context.asAbsolutePath(
    path.join("server-dist", "tinyexpression-p4-lsp-server.jar")
  );
}

/**
 * Returns a path-separator-delimited list of bundled .tecatalog files
 * from the extension's config/ directory, or an empty string if none exist.
 */
function getBundledCatalogPaths(context: vscode.ExtensionContext): string {
  const configDir = context.asAbsolutePath("config");
  if (!fs.existsSync(configDir)) {
    return "";
  }
  try {
    const files = fs.readdirSync(configDir)
      .filter(f => f.endsWith(".tecatalog"))
      .map(f => path.join(configDir, f));
    return files.join(path.delimiter);
  } catch {
    return "";
  }
}

/**
 * Resolves the effective catalog path from VS Code configuration and bundled files.
 */
function resolveEffectiveCatalogPath(
  config: vscode.WorkspaceConfiguration,
  context: vscode.ExtensionContext
): string {
  const userPath = config.get<string>("catalog.path", "").trim();
  if (userPath.length > 0) {
    return userPath;
  }
  const useBundled = config.get<boolean>("catalog.useBundledDefault", true);
  if (useBundled) {
    return getBundledCatalogPaths(context);
  }
  return "";
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

  // Resolve catalog path: user setting → bundled config/*.tecatalog → empty
  const effectiveCatalogPath = resolveEffectiveCatalogPath(config, context);

  outputChannel = vscode.window.createOutputChannel("TinyExpression P4 LSP");

  // Log startup info so users can diagnose server startup issues
  outputChannel.appendLine("[TinyExpression P4 LSP] Starting server...");
  outputChannel.appendLine(`  java: ${javaPath}`);
  outputChannel.appendLine(`  jar:  ${jarPath}`);
  if (effectiveCatalogPath.length > 0) {
    outputChannel.appendLine(`  catalog: ${effectiveCatalogPath}`);
  }

  // Build JVM args: add catalog path as system property if available
  const catalogJvmArgs: string[] = effectiveCatalogPath.length > 0
    ? [`-Dtinyexpressionp4.catalog.path=${effectiveCatalogPath}`]
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
  const initializationOptions: Record<string, unknown> =
    effectiveCatalogPath.length > 0
      ? { catalogPath: effectiveCatalogPath }
      : {};

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
