//! Fenced Java code blocks (```` ```java:ClassName ````) in a formula (issue #216).
//!
//! Java compiles such a block and loads the class, so `import ClassName#method as alias;` plus
//! `external returning as T alias(...)` calls it. This evaluator never compiles or runs the
//! block (no JVM, and a Rust counterpart would need a compiler at run time — the same risk
//! ADR-003 keeps behind an explicit opt-in on the Java side). The block only *declares* the
//! class: it evaluates to nothing, and calls to the class go through the [`super::ExternalHost`]
//! like any other external class — in a JSON request, the `externals[]` constant stubs. When
//! the host cannot load a class that a code block declares, the usual `Class.forName` failure
//! (`UnsupportedOperationException("External invocation failed: ...")`) carries a hint saying
//! so (see [`missing_stub_hint`]).
//!
//! The parser keeps the block out of the AST (`Formula ::= { CodeBlock } ...`, the grammar's
//! `CODE_BLOCK` token), so the classes are read from the source here, with the rules of the
//! Java `CodeStartParser` / `CodeEndParser`: a line that is exactly
//! ```` ```scheme:Class.Name ```` opens a block, a line that is exactly ```` ``` ```` closes it.

use super::{EvalError, ExternalError};

/// Fully qualified class names of the code blocks of `source`, in source order, without
/// duplicates. Every scheme counts (Java dispatches on it; `java` is the one in use).
pub fn code_block_classes(source: &str) -> Vec<String> {
    let mut classes: Vec<String> = Vec::new();
    let mut inside = false;
    for line in source.split('\n') {
        let line = line.strip_suffix('\r').unwrap_or(line);
        if inside {
            if line == "```" {
                inside = false;
            }
            continue;
        }
        if let Some(class) = opening_fence_class(line) {
            inside = true;
            if !classes.iter().any(|c| c == class) {
                classes.push(class.to_owned());
            }
        }
    }
    classes
}

fn is_ident_start(c: char) -> bool {
    c.is_ascii_alphabetic() || c == '_' || c == '$'
}

fn is_ident_part(c: char) -> bool {
    is_ident_start(c) || c.is_ascii_digit()
}

/// End (byte index) of the identifier starting at `from`, or `None` when none starts there.
fn identifier_end(line: &str, from: usize) -> Option<usize> {
    let mut chars = line[from..].char_indices();
    match chars.next() {
        Some((_, c)) if is_ident_start(c) => {}
        _ => return None,
    }
    Some(
        chars
            .find(|(_, c)| !is_ident_part(*c))
            .map_or(line.len(), |(i, _)| from + i),
    )
}

/// The class of an opening fence line ```` ```scheme:a.b.C ````, or `None`.
fn opening_fence_class(line: &str) -> Option<&str> {
    let rest = line.strip_prefix("```")?;
    let offset = line.len() - rest.len();
    let scheme_end = identifier_end(line, offset)?;
    if !line[scheme_end..].starts_with(':') {
        return None;
    }
    let class_from = scheme_end + 1;
    let mut end = identifier_end(line, class_from)?;
    while line[end..].starts_with('.') {
        end = identifier_end(line, end + 1)?;
    }
    (end == line.len()).then(|| &line[class_from..end])
}

/// The error of a failed external call ([`super::ExternalError`] → the Java exception), with
/// [`missing_stub_hint`] when the class the host cannot load is declared by a code block.
pub(crate) fn external_error(
    error: ExternalError,
    class: &str,
    method: &str,
    declared_by_code_block: bool,
) -> EvalError {
    let missing = error == ExternalError::ClassNotFound;
    let mut out = super::ops::external_error(error, class, method);
    if missing && declared_by_code_block {
        out.message.push_str(&missing_stub_hint(class, method));
    }
    out
}

/// What an evaluation error about a code-block class without a host stub says after the
/// Java message.
pub fn missing_stub_hint(class: &str, method: &str) -> String {
    format!(
        " (the class is declared by a ```java:{class} code block, which this evaluator does not \
         compile or run; コードブロックのクラスは externals で値を指定してください: add \
         {{\"class\":\"{class}\",\"method\":\"{method}\",\"result\":{{\"type\":...,\"value\":...}}}} \
         to the request's externals[])"
    )
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::runtime::{
        Context, ContextClock, ErrorKind, ExternalCall, ExternalHost, Host, Options, Program,
        ResultType, Variables, XorShiftRandom,
    };
    use crate::Value;

    /// A host that loads no class (every `Class.forName` fails).
    struct NoClasses;

    impl ExternalHost for NoClasses {
        fn class_exists(&self, _: &str) -> bool {
            false
        }

        fn invoke(
            &mut self,
            _: &ExternalCall<'_>,
            _: &dyn Variables,
        ) -> Result<Value, ExternalError> {
            Err(ExternalError::ClassNotFound)
        }
    }

    #[test]
    fn tree_and_closure_give_the_same_hint() {
        let source = "```java:a.B\nclass B{}\n```\nimport a.B#m as m;\nimport c.D#m as d;\n\
                      if(external returning as boolean m()){1}else{external returning as number d()}";
        let program = Program::new(source, Options::new(ResultType::Float)).unwrap();
        assert_eq!(program.code_block_classes(), ["a.B".to_owned()]);
        let run = |closure: bool| {
            let mut context = Context::new();
            let mut external = NoClasses;
            let mut random = XorShiftRandom::default();
            let mut host = Host {
                external: &mut external,
                clock: &ContextClock,
                random: &mut random,
            };
            if closure {
                program.compile().eval(&mut context, &mut host)
            } else {
                program.eval_tree(&mut context, &mut host)
            }
        };
        let tree = run(false).unwrap_err();
        let closure = run(true).unwrap_err();
        assert_eq!(tree.kind, ErrorKind::UnsupportedOperation);
        assert_eq!(tree.message, closure.message);
        assert_eq!(
            tree.message,
            format!(
                "External invocation failed: a.B#m{}",
                missing_stub_hint("a.B", "m")
            )
        );
    }

    #[test]
    fn reads_the_classes_of_the_fences() {
        let source = "```java:CheckDigits\npublic class CheckDigits{}\n```\r\n\
                      ```java:sample.v1.CheckAlphabets\r\nclass X{ String s = \"```java:Nope\"; }\n```\n\
                      import CheckDigits#check as c;\n1";
        assert_eq!(
            code_block_classes(source),
            vec![
                "CheckDigits".to_owned(),
                "sample.v1.CheckAlphabets".to_owned()
            ]
        );
    }

    #[test]
    fn ignores_what_is_not_a_fence() {
        assert!(code_block_classes(" ```java:A\n```").is_empty());
        assert!(code_block_classes("```java:A trailing\n```").is_empty());
        assert!(code_block_classes("```java:a..b\n```").is_empty());
        assert!(code_block_classes("```java\n```").is_empty());
        assert!(code_block_classes("1 + 2").is_empty());
        // An unclosed block still declares its class (the parser reports the syntax error).
        assert_eq!(
            code_block_classes("```java:A\nclass A{}"),
            vec!["A".to_owned()]
        );
        // A class inside a block body is not a fence.
        assert_eq!(
            code_block_classes("```java:A\n```java:B\n```\n```java:A\n```"),
            vec!["A".to_owned()]
        );
    }
}
