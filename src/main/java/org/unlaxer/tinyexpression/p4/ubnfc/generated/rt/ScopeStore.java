// ubnfc runtime template: ScopeStore
package org.unlaxer.tinyexpression.p4.ubnfc.generated.rt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

/** 生成コードが所有する状態。checkpoint は O(1)、restore は巻き戻す変更数に比例する。 */
public final class ScopeStore {
    public record Decl(String name, int offsetCp) {}
    public record Reference(String name, int offset, int len) {}
    public record Diagnostic(String msg, int offset, int len, Severity severity) {}

    private static final class Scope {
        final LinkedHashMap<String, Decl> lookup = new LinkedHashMap<>();
        final ArrayList<Decl> declarations = new ArrayList<>();
    }

    private enum Kind { ENTER, LEAVE, DECLARE, REFERENCE, DIAGNOSTIC, CLEAR }

    private record Undo(Kind kind, int version, Scope scope, String name,
                        Decl previous, List<Diagnostic> cleared) {}

    private final Scope global = new Scope();
    private final ArrayList<Scope> scopes = new ArrayList<>();
    private final ArrayList<Decl> declarations = new ArrayList<>();
    private final ArrayList<Reference> references = new ArrayList<>();
    private final ArrayList<Diagnostic> diagnostics = new ArrayList<>();
    private final List<Decl> declarationView = Collections.unmodifiableList(declarations);
    private final List<Reference> referenceView = Collections.unmodifiableList(references);
    private final List<Diagnostic> diagnosticView = Collections.unmodifiableList(diagnostics);
    // 1 parse で数百件になるので、既定の 10 から 8 回育て直すのをやめる。
    private final ArrayList<Undo> journal = new ArrayList<>(64);
    private int stateVersion;
    private int epoch;

    public int depth() {
        return scopes.size();
    }

    public int stateVersion() {
        return stateVersion;
    }

    public void enter() {
        int version = freshVersion();
        journal.add(new Undo(Kind.ENTER, stateVersion, null, null, null, null));
        scopes.add(new Scope());
        stateVersion = version;
    }

    public void leave() {
        if (scopes.isEmpty()) {
            return;
        }
        int version = freshVersion();
        Scope leaving = scopes.get(scopes.size() - 1);
        journal.add(new Undo(Kind.LEAVE, stateVersion, leaving, null, null, null));
        scopes.remove(scopes.size() - 1);
        stateVersion = version;
    }

    /** name の trim と offset の code point 変換は capture を処理する生成コードの責務。 */
    public void declare(String name, int offsetCp) {
        if (name == null || name.isEmpty()) {
            return;
        }
        int version = freshVersion();
        Scope scope = currentScope();
        Decl declaration = new Decl(name, offsetCp);
        journal.add(new Undo(Kind.DECLARE, stateVersion, scope, name, scope.lookup.get(name), null));
        scope.lookup.put(name, declaration);
        scope.declarations.add(declaration);
        declarations.add(declaration);
        stateVersion = version;
    }

    public void addReference(String name, int offset, int len) {
        if (name == null || name.isEmpty()) {
            return;
        }
        journal.add(new Undo(Kind.REFERENCE, stateVersion, null, null, null, null));
        references.add(new Reference(name, offset, len));
    }

    public void addDiagnostic(String msg, int offset, int len, Severity severity) {
        journal.add(new Undo(Kind.DIAGNOSTIC, stateVersion, null, null, null, null));
        diagnostics.add(new Diagnostic(msg, offset, len, severity));
    }

    public void clearDiagnostics() {
        if (diagnostics.isEmpty()) {
            return;
        }
        int version = freshVersion();
        journal.add(new Undo(Kind.CLEAR, stateVersion, null, null, null, List.copyOf(diagnostics)));
        diagnostics.clear();
        stateVersion = version;
    }

    public boolean isDeclared(String name) {
        return resolve(name) != null;
    }

    /** 内側→外側→global の順で検索し、未定義なら null。 */
    public Decl resolve(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        for (int i = scopes.size() - 1; i >= 0; i--) {
            Decl found = scopes.get(i).lookup.get(name);
            if (found != null) {
                return found;
            }
        }
        return global.lookup.get(name);
    }

    /** 現在の lookup 値の独立したリスト。同名再宣言の旧値は含めない。 */
    public List<Decl> declaredInCurrentScope() {
        return List.copyOf(currentScope().lookup.values());
    }

    /** 挿入順の変更不可 live view。成功した leave の後も宣言を保持する。 */
    public List<Decl> allDeclarations() {
        return declarationView;
    }

    public List<Reference> references() {
        return referenceView;
    }

    public List<Diagnostic> diagnostics() {
        return diagnosticView;
    }

    /** 同一 store の現在の履歴に属する祖先 checkpoint のみ restore できる。 */
    public int checkpoint() {
        return journal.size();
    }

    public void restore(int checkpoint) {
        int size = journal.size();
        // 取消し記録が 1 件も増えていない巻戻しが大多数（P4 complex で 2,070 回中 1,930 回）。
        if (checkpoint == size) return;
        if (checkpoint < 0 || checkpoint > size) {
            throw new IllegalArgumentException("Checkpoint is outside the current undo history");
        }
        while (journal.size() > checkpoint) {
            Undo undo = journal.remove(journal.size() - 1);
            switch (undo.kind()) {
                case ENTER -> scopes.remove(scopes.size() - 1);
                case LEAVE -> scopes.add(undo.scope());
                case DECLARE -> {
                    if (undo.previous() == null) {
                        undo.scope().lookup.remove(undo.name());
                    } else {
                        undo.scope().lookup.put(undo.name(), undo.previous());
                    }
                    undo.scope().declarations.remove(undo.scope().declarations.size() - 1);
                    declarations.remove(declarations.size() - 1);
                }
                case REFERENCE -> references.remove(references.size() - 1);
                case DIAGNOSTIC -> diagnostics.remove(diagnostics.size() - 1);
                case CLEAR -> diagnostics.addAll(undo.cleared());
            }
            stateVersion = undo.version();
        }
    }

    private Scope currentScope() {
        return scopes.isEmpty() ? global : scopes.get(scopes.size() - 1);
    }

    private int freshVersion() {
        if (epoch == Integer.MAX_VALUE) {
            throw new IllegalStateException("Scope state version exhausted; start a new parse session");
        }
        return ++epoch;
    }
}
