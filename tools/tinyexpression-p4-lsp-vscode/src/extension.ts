import * as fs from "fs";
import * as os from "os";
import * as path from "path";
import * as vscode from "vscode";
import {
  WORKSPACE_OVERRIDE_PATH,
  catalogLabel,
  checkCatalogExport,
  formulaAt,
  nonce,
  resolveOverridePath,
  rewritePlaygroundHtml
} from "./playground-support";
import {
  LanguageClient,
  LanguageClientOptions,
  ServerOptions
} from "vscode-languageclient/node";

let client: LanguageClient | undefined;
let outputChannel: vscode.OutputChannel | undefined;
let catalogStatus: vscode.StatusBarItem | undefined;
let playgroundPanel: vscode.WebviewPanel | undefined;

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
 *   exported from the playground (a relative path is relative to the first workspace folder);
 * - catalog.useBundledDefault: when catalog.path is empty, use the bundled catalog's variables.
 */
function resolveCatalogOptions(
  config: vscode.WorkspaceConfiguration
): { catalogPath: string; overridePath: string; useBundledVariables: boolean } {
  const catalogPath = config.get<string>("catalog.path", "").trim();
  const overridePath = resolveOverridePath(
    config.get<string>("catalog.overridePath", ""),
    vscode.workspace.workspaceFolders?.[0]?.uri.fsPath,
    path.join,
    path.isAbsolute
  );
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

  outputChannel = vscode.window.createOutputChannel("TinyExpression P4 LSP");

  const startClient = async (): Promise<void> => {
    const catalog = resolveCatalogOptions(vscode.workspace.getConfiguration("tinyExpressionP4Lsp"));

    // Log startup info so users can diagnose server startup issues
    outputChannel?.appendLine("[TinyExpression P4 LSP] Starting server...");
    outputChannel?.appendLine(`  java: ${javaPath}`);
    outputChannel?.appendLine(`  jar:  ${jarPath}`);
    outputChannel?.appendLine(
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
    updateCatalogStatus();

    await client.start().then(() => {
      outputChannel?.appendLine("[TinyExpression P4 LSP] Server started successfully.");
    }).catch((err: unknown) => {
      outputChannel?.appendLine(`[TinyExpression P4 LSP] Failed to start server: ${String(err)}`);
      void vscode.window.showErrorMessage(
        `TinyExpression P4 LSP: Failed to start Java server. Check Output > TinyExpression P4 LSP for details. (${String(err)})`
      );
    });
  };

  /** The catalog settings are initialization options: a change restarts the server. */
  const restartClient = async (): Promise<void> => {
    const previous = client;
    client = undefined;
    if (previous) {
      try {
        await previous.stop();
      } catch (err: unknown) {
        outputChannel?.appendLine(`[TinyExpression P4 LSP] stop: ${String(err)}`);
      }
    }
    await startClient();
  };

  registerCatalogFeatures(context, restartClient);

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

  void startClient();

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

// ── language catalog: status bar, import, playground webview (issue #201, stages 4 and 5) ──

function activeOverridePath(): string {
  return resolveCatalogOptions(vscode.workspace.getConfiguration("tinyExpressionP4Lsp")).overridePath;
}

function updateCatalogStatus(): void {
  if (!catalogStatus) return;
  const override = activeOverridePath();
  const exists = override === "" || fs.existsSync(override);
  catalogStatus.text = `$(book) TE catalog: ${catalogLabel(override)}${exists ? "" : " (missing)"}`;
  catalogStatus.tooltip = override
    ? `Language catalog: the bundled catalog + override ${override}${exists ? "" : " (file not found)"}\nClick for catalog actions.`
    : "Language catalog: the bundled catalog/tinyexpression-catalog.json\nClick for catalog actions.";
  catalogStatus.show();
}

/** Writes a catalog export into the workspace and makes it the active override. */
async function installCatalogOverride(text: string, restartClient: () => Promise<void>): Promise<string | undefined> {
  const check = checkCatalogExport(text);
  if (!check.ok) {
    void vscode.window.showErrorMessage(`TinyExpression: not a catalog export: ${check.problems.join("; ")}`);
    return undefined;
  }
  const folder = vscode.workspace.workspaceFolders?.[0];
  const config = vscode.workspace.getConfiguration("tinyExpressionP4Lsp");
  const previousSetting = config.get<string>("catalog.overridePath", "");
  let target: vscode.Uri;
  if (folder) {
    target = vscode.Uri.joinPath(folder.uri, ...WORKSPACE_OVERRIDE_PATH.split("/"));
    await vscode.workspace.fs.writeFile(target, Buffer.from(text.endsWith("\n") ? text : `${text}\n`, "utf8"));
    await config.update("catalog.overridePath", WORKSPACE_OVERRIDE_PATH, vscode.ConfigurationTarget.Workspace);
  } else {
    const picked = await vscode.window.showSaveDialog({
      title: "Save the catalog override",
      defaultUri: vscode.Uri.file(path.join(os.homedir(), "tinyexpression-catalog.override.json")),
      filters: { JSON: ["json"] }
    });
    if (!picked) return undefined;
    target = picked;
    await vscode.workspace.fs.writeFile(target, Buffer.from(text, "utf8"));
    await config.update("catalog.overridePath", target.fsPath, vscode.ConfigurationTarget.Global);
  }
  // A changed setting restarts the server through onDidChangeConfiguration; the same setting
  // with new file contents needs an explicit restart.
  if (vscode.workspace.getConfiguration("tinyExpressionP4Lsp").get<string>("catalog.overridePath", "") === previousSetting) {
    await restartClient();
  }
  updateCatalogStatus();
  const summary = `${check.kind === "full" ? "full catalog" : "override"} (${check.sections.join(", ")}; ${check.entries} entries)`;
  outputChannel?.appendLine(`[TinyExpression P4 LSP] catalog override ${target.fsPath}: ${summary}`);
  return `Catalog ${summary} written to ${vscode.workspace.asRelativePath(target)} and active (catalog.overridePath).`;
}

function registerCatalogFeatures(context: vscode.ExtensionContext, restartClient: () => Promise<void>): void {
  catalogStatus = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Right, 100);
  catalogStatus.command = "tinyExpressionP4Lsp.catalogActions";
  context.subscriptions.push(catalogStatus);
  updateCatalogStatus();

  context.subscriptions.push(
    vscode.workspace.onDidChangeConfiguration((event) => {
      if (event.affectsConfiguration("tinyExpressionP4Lsp.catalog")) {
        updateCatalogStatus();
        void restartClient();
      }
    }),
    vscode.commands.registerCommand("tinyExpressionP4Lsp.importCatalog", async (uri?: vscode.Uri) => {
      const source = uri ?? (await vscode.window.showOpenDialog({
        title: "Import catalog from playground export",
        canSelectMany: false,
        filters: { "Catalog JSON": ["json"] },
        openLabel: "Import"
      }))?.[0];
      if (!source) return;
      const text = Buffer.from(await vscode.workspace.fs.readFile(source)).toString("utf8");
      const message = await installCatalogOverride(text, restartClient);
      if (message) {
        void vscode.window.showInformationMessage(message);
        void postPlaygroundInit();
      }
    }),
    vscode.commands.registerCommand("tinyExpressionP4Lsp.clearCatalogOverride", async () => {
      const config = vscode.workspace.getConfiguration("tinyExpressionP4Lsp");
      for (const target of [vscode.ConfigurationTarget.Workspace, vscode.ConfigurationTarget.Global]) {
        await config.update("catalog.overridePath", undefined, target);
      }
      updateCatalogStatus();
      void postPlaygroundInit();
    }),
    vscode.commands.registerCommand("tinyExpressionP4Lsp.catalogActions", async () => {
      const override = activeOverridePath();
      const items: (vscode.QuickPickItem & { run: () => unknown })[] = [
        { label: "$(play) Open playground", description: "trace / step / catalog editing", run: () => vscode.commands.executeCommand("tinyExpressionP4Lsp.openPlayground") },
        { label: "$(cloud-download) Import catalog from playground export…", run: () => vscode.commands.executeCommand("tinyExpressionP4Lsp.importCatalog") }
      ];
      if (override) {
        items.push(
          { label: "$(go-to-file) Open the override file", description: override, run: async () => vscode.window.showTextDocument(vscode.Uri.file(override)) },
          { label: "$(discard) Use the bundled catalog only", description: "clear catalog.overridePath", run: () => vscode.commands.executeCommand("tinyExpressionP4Lsp.clearCatalogOverride") }
        );
      }
      const picked = await vscode.window.showQuickPick(items, { title: `TinyExpression catalog: ${catalogLabel(override)}` });
      await picked?.run();
    }),
    vscode.commands.registerCommand("tinyExpressionP4Lsp.openPlayground", () => openPlayground(context, restartClient))
  );
}

function readOverride(): { override: unknown; label: string } {
  const overridePath = activeOverridePath();
  if (overridePath === "" || !fs.existsSync(overridePath)) return { override: {}, label: catalogLabel("") };
  try {
    return { override: JSON.parse(fs.readFileSync(overridePath, "utf8")), label: catalogLabel(overridePath) };
  } catch (err: unknown) {
    outputChannel?.appendLine(`[TinyExpression P4 LSP] cannot read catalog override ${overridePath}: ${String(err)}`);
    return { override: {}, label: `${catalogLabel(overridePath)} (unreadable)` };
  }
}

let playgroundSource: { formula: string; formulaInfo: string | null; documentName: string } | undefined;

async function postPlaygroundInit(): Promise<void> {
  if (!playgroundPanel) return;
  const { override, label } = readOverride();
  await playgroundPanel.webview.postMessage({
    type: "init",
    formula: playgroundSource?.formula ?? "",
    formulaInfo: playgroundSource?.formulaInfo ?? null,
    documentName: playgroundSource?.documentName ?? "",
    catalogOverride: override,
    catalogLabel: label
  });
}

/** "TinyExpression: Open playground": the packaged playground/dist in a webview. */
function openPlayground(context: vscode.ExtensionContext, restartClient: () => Promise<void>): void {
  const editor = vscode.window.activeTextEditor;
  if (editor && editor.document.languageId === "tinyexpressionP4") {
    const text = editor.document.getText();
    const selected = editor.document.getText(editor.selection);
    const at = formulaAt(text, editor.document.offsetAt(editor.selection.active));
    playgroundSource = {
      formula: selected.trim() !== "" ? selected : at.formula,
      formulaInfo: at.formulaInfo,
      documentName: path.basename(editor.document.fileName)
    };
  }
  if (playgroundPanel) {
    playgroundPanel.reveal(vscode.ViewColumn.Beside);
    void postPlaygroundInit();
    return;
  }
  const distRoot = vscode.Uri.joinPath(context.extensionUri, "playground-dist");
  const panel = vscode.window.createWebviewPanel(
    "tinyexpressionPlayground",
    "TinyExpression Playground",
    vscode.ViewColumn.Beside,
    { enableScripts: true, retainContextWhenHidden: true, localResourceRoots: [distRoot] }
  );
  playgroundPanel = panel;
  const indexPath = path.join(distRoot.fsPath, "index.html");
  const html = fs.existsSync(indexPath) ? fs.readFileSync(indexPath, "utf8") : "<!doctype html><html><head></head><body>playground-dist is missing</body></html>";
  panel.webview.html = rewritePlaygroundHtml(html, {
    baseUri: panel.webview.asWebviewUri(distRoot).toString(),
    cspSource: panel.webview.cspSource,
    nonce: nonce()
  });
  panel.onDidDispose(() => { playgroundPanel = undefined; }, undefined, context.subscriptions);
  panel.webview.onDidReceiveMessage(async (message: { type?: string; kind?: string; name?: string; text?: string; url?: string }) => {
    switch (message.type) {
      case "ready":
        await postPlaygroundInit();
        break;
      case "saveCatalog": {
        const text = String(message.text ?? "");
        if (message.kind === "override" || message.kind === "full") {
          const done = await installCatalogOverride(text, restartClient);
          if (done) {
            void vscode.window.showInformationMessage(done);
            await panel.webview.postMessage({ type: "saved", message: done, catalogLabel: readOverride().label });
          }
        } else {
          const target = await vscode.window.showSaveDialog({
            defaultUri: vscode.workspace.workspaceFolders?.[0]
              ? vscode.Uri.joinPath(vscode.workspace.workspaceFolders[0].uri, message.name ?? "catalog.txt")
              : undefined
          });
          if (target) {
            await vscode.workspace.fs.writeFile(target, Buffer.from(text, "utf8"));
            await panel.webview.postMessage({ type: "saved", message: `${vscode.workspace.asRelativePath(target)} に保存しました。` });
          }
        }
        break;
      }
      case "openExternal":
        if (typeof message.url === "string" && /^https:\/\//.test(message.url)) {
          await vscode.env.openExternal(vscode.Uri.parse(message.url));
        }
        break;
      case "copy":
        await vscode.env.clipboard.writeText(String(message.text ?? ""));
        break;
      default:
        break;
    }
  }, undefined, context.subscriptions);
}
