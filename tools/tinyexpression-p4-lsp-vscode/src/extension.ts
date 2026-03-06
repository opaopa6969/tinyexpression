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

  outputChannel = vscode.window.createOutputChannel("TinyExpression P4 LSP");

  // Log startup info so users can diagnose server startup issues
  outputChannel.appendLine("[TinyExpression P4 LSP] Starting server...");
  outputChannel.appendLine(`  java: ${javaPath}`);
  outputChannel.appendLine(`  jar:  ${jarPath}`);
  outputChannel.appendLine(`  args: --enable-preview -jar ${jarPath}`);

  // ── LSP server ──
  // Launched as a fat jar via -jar; main class is TinyExpressionP4LspLauncherExt
  const serverOptions: ServerOptions = {
    command: javaPath,
    args: [...jvmArgs, "--enable-preview", "-jar", jarPath],
    options: {}
  };

  const clientOptions: LanguageClientOptions = {
    documentSelector: [{ scheme: "file", language: "tinyexpressionP4" }],
    outputChannel
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
