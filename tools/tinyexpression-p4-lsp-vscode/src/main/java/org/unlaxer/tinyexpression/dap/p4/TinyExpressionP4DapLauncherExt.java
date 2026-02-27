package org.unlaxer.tinyexpression.dap.p4;

import java.io.IOException;

import org.eclipse.lsp4j.debug.launch.DSPLauncher;
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient;
import org.eclipse.lsp4j.jsonrpc.Launcher;

/**
 * DAP launcher that starts {@link TinyExpressionP4DebugAdapterExt} instead of the
 * generated plain adapter.
 */
public class TinyExpressionP4DapLauncherExt {

  public static void main(String[] args) throws IOException {
    TinyExpressionP4DebugAdapterExt adapter = new TinyExpressionP4DebugAdapterExt();
    Launcher<IDebugProtocolClient> launcher =
        DSPLauncher.createServerLauncher(adapter, System.in, System.out);
    adapter.connect(launcher.getRemoteProxy());
    launcher.startListening();
  }
}
