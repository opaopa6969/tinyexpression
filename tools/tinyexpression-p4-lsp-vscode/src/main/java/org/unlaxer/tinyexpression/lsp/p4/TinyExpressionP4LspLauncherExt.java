package org.unlaxer.tinyexpression.lsp.p4;

import java.io.IOException;

import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;

/**
 * LSP launcher that starts {@link TinyExpressionP4LanguageServerExt} instead of the
 * generated plain server. This is the main entry point for the fat JAR.
 */
public class TinyExpressionP4LspLauncherExt {

  public static void main(String[] args) throws IOException {
    TinyExpressionP4LanguageServerExt server = new TinyExpressionP4LanguageServerExt();
    Launcher<LanguageClient> launcher =
        LSPLauncher.createServerLauncher(server, System.in, System.out);
    server.connect(launcher.getRemoteProxy());
    launcher.startListening();
  }
}
