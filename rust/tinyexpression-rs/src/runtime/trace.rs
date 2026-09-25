//! Evaluation trace of the tree walker (issue #201, stage 3).
//!
//! [`Program::eval_tree_traced`](super::Program::eval_tree_traced) calls a [`TraceHook`] around
//! every step of the tree walker: `on_enter` before a node (or a text leaf such as `$price` or
//! `3`) is evaluated, `on_exit` with its value or error afterwards. The hook only observes: the
//! walker computes exactly what [`Program::eval_tree`](super::Program::eval_tree) computes
//! (the Java differential gate runs every row with and without a hook and requires the same
//! outcome). The closure-compiled form ([`super::Compiled`]) has no hook.
//!
//! [`TraceRecorder`] is the hook the JSON API uses: it builds the trace tree (node kind, source
//! span in code points, value or error) and renders it as JSON for `te_eval_trace` /
//! `tinyexpression eval --trace`. It lines up with ubnfc's grammar debugger (DAP design D-067):
//! the `on_enter` order is D-067's step sequence (typed AST in pre-order, children in order —
//! here only the nodes the walker actually evaluates), the chain of entered steps is its
//! `stackTrace`, and the value at `on_exit` is what D-067 leaves to an evaluator bridge.

use std::fmt::{self, Write};

use super::{java_string, EvalError};
use crate::generated::ast::Ast;
use crate::{json_string, Span, Value};

/// What the walker is about to evaluate.
#[derive(Clone, Copy, Debug)]
pub enum TraceSite<'a> {
    /// A typed AST node.
    Node(&'a Ast),
    /// A text operand of a `BinaryExpr` / `StringConcatExpr` (a number literal, a `$variable`,
    /// a quoted string) that the walker resolves without a node of its own.
    Leaf { text: &'a str, span: Span },
}

impl TraceSite<'_> {
    pub fn span(&self) -> Span {
        match self {
            Self::Node(node) => node.span(),
            Self::Leaf { span, .. } => *span,
        }
    }

    /// The AST node kind (`ComparisonExpr`, ...), or `Leaf` for a text operand.
    pub fn kind(&self) -> String {
        match self {
            Self::Node(node) => node_kind(node),
            Self::Leaf { .. } => "Leaf".to_owned(),
        }
    }
}

/// Observes the tree walker. Calls are properly nested: every `on_enter` is followed by exactly
/// one `on_exit` after the `on_enter`/`on_exit` pairs of the steps it contains.
pub trait TraceHook {
    fn on_enter(&mut self, site: TraceSite<'_>);
    fn on_exit(&mut self, outcome: Result<&Value, &EvalError>);
}

/// The variant name of an AST node, read from its `Debug` output without formatting the
/// subtree (the writer stops at the first delimiter).
pub fn node_kind(node: &Ast) -> String {
    struct Name(String);
    impl Write for Name {
        fn write_str(&mut self, s: &str) -> fmt::Result {
            for c in s.chars() {
                if c == ' ' || c == '{' || c == '(' {
                    return Err(fmt::Error);
                }
                self.0.push(c);
            }
            Ok(())
        }
    }
    let mut name = Name(String::new());
    let _ = write!(name, "{node:?}");
    name.0
}

/// One recorded step.
#[derive(Clone, Debug, PartialEq)]
pub struct TraceNode {
    pub kind: String,
    pub span: Span,
    /// The operand text for a [`TraceSite::Leaf`].
    pub leaf: Option<String>,
    /// `None` while the step runs; afterwards the value or the error.
    pub outcome: Option<Result<Value, EvalError>>,
    pub children: Vec<usize>,
}

/// Records the trace tree. Steps beyond `max_nodes` are counted but not stored (the recorded
/// tree is then marked truncated), so a runaway recursion cannot exhaust memory.
#[derive(Debug)]
pub struct TraceRecorder {
    nodes: Vec<TraceNode>,
    stack: Vec<Option<usize>>,
    roots: Vec<usize>,
    max_nodes: usize,
    steps: usize,
}

/// Default bound of recorded steps for the JSON API.
pub const DEFAULT_MAX_TRACE_NODES: usize = 20_000;

impl Default for TraceRecorder {
    fn default() -> Self {
        Self::new(DEFAULT_MAX_TRACE_NODES)
    }
}

impl TraceRecorder {
    pub fn new(max_nodes: usize) -> Self {
        Self {
            nodes: Vec::new(),
            stack: Vec::new(),
            roots: Vec::new(),
            max_nodes,
            steps: 0,
        }
    }

    pub fn nodes(&self) -> &[TraceNode] {
        &self.nodes
    }

    /// Top-level steps (normally one: the evaluation root).
    pub fn roots(&self) -> &[usize] {
        &self.roots
    }

    /// Every step the walker took, recorded or not.
    pub fn steps(&self) -> usize {
        self.steps
    }

    pub fn truncated(&self) -> bool {
        self.steps > self.nodes.len()
    }

    /// `{"steps":n,"recorded":n,"truncated":bool,"root":<node>|null}` where a node is
    /// `{"kind","span":[start,end],"leaf"?,"value"?|"error"?,"text"?,"children":[...]}`.
    pub fn to_json(&self) -> String {
        let mut out = String::with_capacity(64 + self.nodes.len() * 96);
        let _ = write!(
            out,
            "{{\"steps\":{},\"recorded\":{},\"truncated\":{},\"root\":",
            self.steps,
            self.nodes.len(),
            self.truncated()
        );
        match self.roots.first() {
            Some(&root) => self.node_json(root, &mut out),
            None => out.push_str("null"),
        }
        out.push('}');
        out
    }

    fn node_json(&self, index: usize, out: &mut String) {
        // Iterative to stay within the (wasm) stack on deeply nested formulas.
        enum Step {
            Open(usize),
            Close,
            Comma,
        }
        let mut work = vec![Step::Open(index)];
        while let Some(step) = work.pop() {
            match step {
                Step::Comma => out.push(','),
                Step::Close => out.push_str("]}"),
                Step::Open(i) => {
                    let node = &self.nodes[i];
                    let _ = write!(
                        out,
                        "{{\"kind\":{},\"span\":[{},{}]",
                        json_string(&node.kind),
                        node.span.start,
                        node.span.end
                    );
                    if let Some(leaf) = &node.leaf {
                        let _ = write!(out, ",\"leaf\":{}", json_string(leaf));
                    }
                    match &node.outcome {
                        Some(Ok(value)) => {
                            let _ = write!(
                                out,
                                ",\"value\":{},\"text\":{}",
                                value.canonical_json(),
                                json_string(&java_string(value))
                            );
                        }
                        Some(Err(error)) => {
                            let _ = write!(out, ",\"error\":{}", error.canonical_json());
                        }
                        None => {}
                    }
                    out.push_str(",\"children\":[");
                    work.push(Step::Close);
                    for (n, &child) in node.children.iter().enumerate().rev() {
                        work.push(Step::Open(child));
                        if n > 0 {
                            work.push(Step::Comma);
                        }
                    }
                }
            }
        }
    }
}

impl TraceHook for TraceRecorder {
    fn on_enter(&mut self, site: TraceSite<'_>) {
        self.steps += 1;
        let parent_recorded = !matches!(self.stack.last(), Some(None));
        if self.nodes.len() >= self.max_nodes || !parent_recorded {
            self.stack.push(None);
            return;
        }
        let index = self.nodes.len();
        self.nodes.push(TraceNode {
            kind: site.kind(),
            span: site.span(),
            leaf: match site {
                TraceSite::Leaf { text, .. } => Some(text.trim().to_owned()),
                TraceSite::Node(_) => None,
            },
            outcome: None,
            children: Vec::new(),
        });
        match self.stack.last() {
            Some(Some(parent)) => self.nodes[*parent].children.push(index),
            _ => self.roots.push(index),
        }
        self.stack.push(Some(index));
    }

    fn on_exit(&mut self, outcome: Result<&Value, &EvalError>) {
        if let Some(Some(index)) = self.stack.pop() {
            self.nodes[index].outcome = Some(outcome.cloned().map_err(Clone::clone));
        }
    }
}
