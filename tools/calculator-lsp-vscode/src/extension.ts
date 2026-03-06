import * as fs from "fs";
import * as os from "os";
import * as path from "path";
import { spawnSync } from "child_process";
import * as vscode from "vscode";
import { LanguageClient, LanguageClientOptions, ServerOptions } from "vscode-languageclient/node";

let client: LanguageClient | undefined;

function getBundledJarPath(context: vscode.ExtensionContext): string {
  // Jar will be copied here by `npm run build:server`
  return context.asAbsolutePath(path.join("server-dist", "tinyexpression-lsp-server.jar"));
}

function getBundledCatalogPath(context: vscode.ExtensionContext): string {
  return context.asAbsolutePath(path.join("catalog", "default.tecatalog"));
}

function getBundledConfigCatalogPaths(context: vscode.ExtensionContext): string[] {
  const candidates = [
    context.asAbsolutePath(path.join("config", "nimt-allowed-variables-cfvar.txt")),
    context.asAbsolutePath(path.join("config", "nimt-allowed-variables-checkkind.txt")),
    context.asAbsolutePath(path.join("config", "fa-allowed-variables-cf-variable.txt")),
    context.asAbsolutePath(path.join("config", "fa-allowed-variables-checkkind.txt"))
  ];
  return candidates.filter((candidate) => fs.existsSync(candidate));
}

function parseJavaMajor(versionOutput: string): number | undefined {
  const quoted = versionOutput.match(/version\s+"([^"]+)"/i);
  const raw = quoted?.[1];
  if (raw == null) {
    return undefined;
  }
  if (raw.startsWith("1.")) {
    const legacy = Number.parseInt(raw.split(".")[1] ?? "", 10);
    return Number.isFinite(legacy) ? legacy : undefined;
  }
  const major = Number.parseInt(raw.split(".")[0] ?? "", 10);
  return Number.isFinite(major) ? major : undefined;
}

function probeJava(javaPath: string): { ok: boolean; major?: number; detail: string } {
  const result = spawnSync(javaPath, ["-version"], { encoding: "utf8" });
  if (result.error != null) {
    return {
      ok: false,
      detail: String(result.error)
    };
  }
  const detail = `${result.stdout ?? ""}\n${result.stderr ?? ""}`.trim();
  const major = parseJavaMajor(detail);
  if (major == null) {
    return {
      ok: false,
      detail
    };
  }
  return {
    ok: true,
    major,
    detail
  };
}

function resolveCatalogPath(rawCatalogPath: string): string {
  if (rawCatalogPath.trim().length === 0) {
    return "";
  }
  const workspaceFolder = vscode.workspace.workspaceFolders?.[0]?.uri?.fsPath;
  const home = os.homedir();
  return rawCatalogPath
    .split(",")
    .map((segment) => segment.trim())
    .filter((segment) => segment.length > 0)
    .map((segment) => {
      let resolved = segment;
      if (workspaceFolder != null) {
        resolved = resolved.replace(/\$\{workspaceFolder\}/g, workspaceFolder);
      }
      if (resolved === "~") {
        resolved = home;
      } else if (resolved.startsWith("~/")) {
        resolved = path.join(home, resolved.substring(2));
      }
      return resolved;
    })
    .join(",");
}

async function stopClientSafe(): Promise<void> {
  if (client == null) {
    return;
  }
  try {
    if (client.needsStop()) {
      await client.stop();
    }
  } catch {
    // ignore stop failures during shutdown/dispose
  }
}

export async function activate(context: vscode.ExtensionContext): Promise<void> {
  const config: vscode.WorkspaceConfiguration = vscode.workspace.getConfiguration("tinyExpressionLsp");
  const outputChannel = vscode.window.createOutputChannel("TinyExpression LSP");
  context.subscriptions.push(outputChannel);
  outputChannel.appendLine("[tinyExpressionLsp] activate() called");

  context.subscriptions.push(
    vscode.commands.registerCommand("tinyExpressionLsp.showServerOutput", async () => {
      outputChannel.show(true);
    })
  );

  const javaPath: string = config.get<string>("server.javaPath", "java");
  const configuredJarPath: string = config.get<string>("server.jarPath", "");
  const jvmArgs: string[] = config.get<string[]>("server.jvmArgs", []) ?? [];
  const runtimeMode: string = config.get<string>("runtimeMode", "token");
  const useBundledCatalog: boolean = config.get<boolean>("catalog.useBundledDefault", true);
  const configuredCatalogPath: string = (config.get<string>("catalog.path", "") ?? "").trim();
  const resolvedConfiguredCatalogPath: string = resolveCatalogPath(configuredCatalogPath);
  const bundledCatalogPath = getBundledCatalogPath(context);
  const bundledConfigCatalogPaths = getBundledConfigCatalogPaths(context);
  const bundledFallbackCatalogPaths = fs.existsSync(bundledCatalogPath) ? [bundledCatalogPath] : [];
  const bundledCatalogCandidates = bundledConfigCatalogPaths.length > 0
    ? bundledConfigCatalogPaths
    : bundledFallbackCatalogPaths;
  const useBundledCatalogPath = useBundledCatalog
    && resolvedConfiguredCatalogPath.length === 0
    && bundledCatalogCandidates.length > 0;
  const catalogPath: string = useBundledCatalogPath
    ? bundledCatalogCandidates.join(",")
    : resolvedConfiguredCatalogPath;
  const catalogProviderClass: string = (config.get<string>("catalog.providerClass", "") ?? "").trim();

  const jarPath: string = configuredJarPath.trim().length > 0
    ? configuredJarPath
    : getBundledJarPath(context);

  if (fs.existsSync(jarPath) === false) {
    const message = `TinyExpression LSP jar not found: ${jarPath}`;
    outputChannel.appendLine(message);
    const action = await vscode.window.showErrorMessage(message, "Open Output");
    if (action === "Open Output") {
      outputChannel.show(true);
    }
    return;
  }

  const javaProbe = probeJava(javaPath);
  outputChannel.appendLine(`[tinyExpressionLsp] javaPath=${javaPath}`);
  outputChannel.appendLine(`[tinyExpressionLsp] jarPath=${jarPath}`);
  outputChannel.appendLine(`[tinyExpressionLsp] catalogPath=${catalogPath.length > 0 ? catalogPath : "(default/empty)"}`);
  outputChannel.appendLine(`[tinyExpressionLsp] usingBundledCatalog=${String(useBundledCatalogPath)}`);
  if (useBundledCatalogPath) {
    outputChannel.appendLine(`[tinyExpressionLsp] bundledCatalogSources=${bundledCatalogCandidates.join(",")}`);
  }
  outputChannel.appendLine(`[tinyExpressionLsp] catalogProviderClass=${catalogProviderClass.length > 0 ? catalogProviderClass : "(default/empty)"}`);
  outputChannel.appendLine(`[tinyExpressionLsp] javaVersionProbe=${javaProbe.detail}`);
  if (javaProbe.ok === false) {
    const message = `TinyExpression LSP failed to probe Java at '${javaPath}'. Check tinyExpressionLsp.server.javaPath.`;
    const action = await vscode.window.showErrorMessage(message, "Open Output");
    if (action === "Open Output") {
      outputChannel.show(true);
    }
    return;
  }
  if ((javaProbe.major ?? 0) < 21) {
    const message = `TinyExpression LSP requires Java 21+ but detected Java ${javaProbe.major}. Update tinyExpressionLsp.server.javaPath.`;
    const action = await vscode.window.showErrorMessage(message, "Open Output");
    if (action === "Open Output") {
      outputChannel.show(true);
    }
    return;
  }

  const effectiveJvmArgs: string[] = [...jvmArgs];
  if (catalogPath.length > 0
    && effectiveJvmArgs.some((arg) => arg.startsWith("-Dtinyexpression.catalog.path=")) === false) {
    effectiveJvmArgs.push(`-Dtinyexpression.catalog.path=${catalogPath}`);
  }
  if (catalogProviderClass.length > 0
    && effectiveJvmArgs.some((arg) => arg.startsWith("-Dtinyexpression.catalog.provider.class=")) === false) {
    effectiveJvmArgs.push(`-Dtinyexpression.catalog.provider.class=${catalogProviderClass}`);
  }

  // Start LSP server via stdio:
  //   java [jvmArgs...] -jar <jarPath>
  const serverOptions: ServerOptions = {
    command: javaPath,
    args: [...effectiveJvmArgs, "-jar", jarPath],
    options: {}
  };

  const clientOptions: LanguageClientOptions = {
    documentSelector: [{ scheme: "file", language: "tinyexpression" }],
    outputChannel,
    initializationOptions: {
      runtimeMode,
      catalogPath
    }
  };

  client = new LanguageClient(
    "tinyExpressionLanguageServer",
    "TinyExpression Language Server",
    serverOptions,
    clientOptions
  );

  client.onDidChangeState((event) => {
    outputChannel.appendLine(
      `[tinyExpressionLsp] state ${event.oldState} -> ${event.newState}`
    );
  });
  try {
    await client.start();
  } catch (error) {
    const message = `TinyExpression LSP failed to start. See output for details.`;
    outputChannel.appendLine(`[tinyExpressionLsp] start error: ${String(error)}`);
    const action = await vscode.window.showErrorMessage(message, "Open Output");
    if (action === "Open Output") {
      outputChannel.show(true);
    }
    client = undefined;
    return;
  }

  context.subscriptions.push({
    dispose: () => {
      void stopClientSafe();
    }
  });
}

export async function deactivate(): Promise<void> {
  await stopClientSafe();
}
