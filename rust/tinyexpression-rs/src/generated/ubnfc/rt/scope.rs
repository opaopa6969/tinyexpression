// ubnfc runtime template: scope

use std::collections::BTreeMap;

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Decl {
    pub name: String,
    pub offset_cp: usize,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Reference {
    pub name: String,
    pub offset: usize,
    pub len: usize,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Severity {
    Error,
    Warning,
    Info,
    Hint,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Diagnostic {
    pub msg: String,
    pub offset: usize,
    pub len: usize,
    pub severity: Severity,
}

// 宣言の実体は全履歴だけが所有する。各 scope は現在 lookup と宣言順を index で持つ。
#[derive(Clone, Debug, Default)]
struct Scope {
    lookup: BTreeMap<String, usize>,
    order: Vec<usize>,
}

#[derive(Clone, Debug)]
enum Undo {
    Entered,
    Left(Scope),
    Declared { old_index: Option<usize> },
    ReferenceLength(usize),
    DiagnosticLength(usize),
    ClearedDiagnostics(Vec<Diagnostic>),
}

/// 同じ store の、現在の履歴の祖先にだけ restore できる O(1) の印。
/// 親への restore で捨てた枝の checkpoint は再利用しない。
#[derive(Clone, Copy, Debug)]
pub struct Checkpoint {
    undo_len: usize,
    state_version: u32,
}

/// パース中の lexical scope と、成功後も残す意味イベント。
/// offset/len は呼出し側が渡す code point 単位。trim・capture の選択・未定義警告は
/// 生成側の仕事であり、参照追加だけで警告は出さない。clone は独立した owned snapshot。
///
/// checkpoint は全体コピーをしない。逆操作は変更ごとに 1 件、append は旧長、
/// 再宣言は旧 index、leave/clear は取り外した値の所有権を保持する。
/// restore は変更件数に比例する。成功した子のログも親の rollback 用に残し、
/// journal の寿命は parse session とする。
#[derive(Clone, Debug, Default)]
pub struct ScopeStore {
    scopes: Vec<Scope>,
    global: Scope,
    declarations: Vec<Decl>,
    reference_log: Vec<Reference>,
    diagnostic_log: Vec<Diagnostic>,
    undo: Vec<Undo>,
    state_version: u32,
    epoch: u32,
}

impl ScopeStore {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn depth(&self) -> usize {
        self.scopes.len()
    }

    pub fn state_version(&self) -> u32 {
        self.state_version
    }

    // #269: rollback は current version だけを戻す。払い出し元は戻さない。
    // u32 の再利用は別状態の memo hit を起こすので、枯渇時は wrap せず停止する。
    fn change_version(&mut self) {
        self.epoch = self.epoch.checked_add(1).expect("scope epoch exhausted");
        self.state_version = self.epoch;
    }

    fn current(&self) -> &Scope {
        self.scopes.last().unwrap_or(&self.global)
    }

    fn current_mut(&mut self) -> &mut Scope {
        self.scopes.last_mut().unwrap_or(&mut self.global)
    }

    pub fn enter(&mut self) {
        self.change_version();
        self.scopes.push(Scope::default());
        self.undo.push(Undo::Entered);
    }

    pub fn leave(&mut self) {
        if self.scopes.is_empty() {
            return;
        }
        self.change_version();
        let left = self.scopes.pop().expect("depth を確認済み");
        self.undo.push(Undo::Left(left));
    }

    pub fn declare(&mut self, name: &str, offset_cp: usize) {
        if name.is_empty() {
            return;
        }
        self.change_version();
        let index = self.declarations.len();
        let scope = self.current_mut();
        let old_index = scope.lookup.insert(name.into(), index);
        scope.order.push(index);
        self.declarations.push(Decl {
            name: name.into(),
            offset_cp,
        });
        self.undo.push(Undo::Declared { old_index });
    }

    pub fn add_reference(&mut self, name: &str, offset: usize, len: usize) {
        if name.is_empty() {
            return;
        }
        self.undo
            .push(Undo::ReferenceLength(self.reference_log.len()));
        self.reference_log.push(Reference {
            name: name.into(),
            offset,
            len,
        });
    }

    pub fn add_diagnostic(&mut self, msg: &str, offset: usize, len: usize, severity: Severity) {
        self.undo
            .push(Undo::DiagnosticLength(self.diagnostic_log.len()));
        self.diagnostic_log.push(Diagnostic {
            msg: msg.into(),
            offset,
            len,
            severity,
        });
    }

    /// 明示的な clear は空でも version を進める（このテンプレートの API 契約）。
    pub fn clear_diagnostics(&mut self) {
        self.change_version();
        self.undo.push(Undo::ClearedDiagnostics(std::mem::take(
            &mut self.diagnostic_log,
        )));
    }

    pub fn is_declared(&self, name: &str) -> bool {
        self.resolve(name).is_some()
    }

    pub fn resolve(&self, name: &str) -> Option<&Decl> {
        for scope in self
            .scopes
            .iter()
            .rev()
            .chain(std::iter::once(&self.global))
        {
            if let Some(&index) = scope.lookup.get(name) {
                return self.declarations.get(index);
            }
        }
        None
    }

    /// 同じ名前は最新の宣言だけを返す。BTreeMap により名前順。
    pub fn declared_in_current_scope(&self) -> Vec<&Decl> {
        self.current()
            .lookup
            .values()
            .map(|&i| &self.declarations[i])
            .collect()
    }

    pub fn all_declarations(&self) -> &[Decl] {
        &self.declarations
    }

    pub fn references(&self) -> &[Reference] {
        &self.reference_log
    }

    pub fn diagnostics(&self) -> &[Diagnostic] {
        &self.diagnostic_log
    }

    pub fn checkpoint(&self) -> Checkpoint {
        Checkpoint {
            undo_len: self.undo.len(),
            state_version: self.state_version,
        }
    }

    pub fn restore(&mut self, checkpoint: Checkpoint) {
        assert!(
            checkpoint.undo_len <= self.undo.len(),
            "checkpoint は現在の履歴の祖先であること"
        );
        while self.undo.len() > checkpoint.undo_len {
            match self.undo.pop().expect("ログ長を確認済み") {
                Undo::Entered => {
                    self.scopes.pop();
                }
                Undo::Left(scope) => self.scopes.push(scope),
                Undo::Declared { old_index } => {
                    // 後続の enter/leave は先に復元されるため、この宣言の scope が現在になる。
                    let decl = self.declarations.pop().expect("宣言の逆操作");
                    let scope = self.current_mut();
                    scope.order.pop();
                    match old_index {
                        Some(index) => {
                            scope.lookup.insert(decl.name, index);
                        }
                        None => {
                            scope.lookup.remove(&decl.name);
                        }
                    }
                }
                Undo::ReferenceLength(len) => self.reference_log.truncate(len),
                Undo::DiagnosticLength(len) => self.diagnostic_log.truncate(len),
                Undo::ClearedDiagnostics(log) => self.diagnostic_log = log,
            }
        }
        self.state_version = checkpoint.state_version;
    }
}
