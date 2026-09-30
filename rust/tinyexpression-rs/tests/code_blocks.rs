use tinyexpression_rs::code_blocks::{self, Target};

#[test]
fn normal_evaluation_rejects_rust_but_parse_and_preflight_never_execute_it() {
    use tinyexpression_rs::runtime::{Options, Program};
    let source = "```rust:demo\nnot valid Rust, and must never be compiled\n```\n1";
    assert!(tinyexpression_rs::parse(source).is_ok());
    let blocks = code_blocks::parse(source).unwrap();
    assert!(code_blocks::preflight(&blocks, Target::Rust, true).is_empty());
    assert!(Program::new(source, Options::default())
        .unwrap_err()
        .message
        .contains("CB005"));
    assert!(tinyexpression_rs::evaluate(source)
        .unwrap_err()
        .to_string()
        .contains("CB005"));
    let java = Program::new("```java:Demo\nclass Demo {}\n```\n1", Options::default()).unwrap();
    assert_eq!(java.code_blocks()[0].body, "class Demo {}\n");
}

#[test]
fn native_parse_and_eval_need_no_compiler_on_path() {
    use std::{
        io::Write,
        process::{Command, Stdio},
    };
    let source = "```rust:demo\nnot valid Rust\n```\n1";
    for operation in ["parse", "eval"] {
        let mut child = Command::new(env!("CARGO_BIN_EXE_tinyexpression"))
            .args([operation, "-"])
            .env("PATH", "")
            .stdin(Stdio::piped())
            .stdout(Stdio::piped())
            .spawn()
            .unwrap();
        child
            .stdin
            .take()
            .unwrap()
            .write_all(source.as_bytes())
            .unwrap();
        let output = child.wait_with_output().unwrap();
        assert_eq!(output.status.success(), operation == "parse");
        if operation == "eval" {
            assert!(String::from_utf8(output.stdout).unwrap().contains("CB005"));
        }
    }
}

#[test]
fn retains_body_and_spans() {
    let source = "// 😀\n```rust:demo\r\n  // keep\r\npub fn value() -> i32 { 7 }\r\n```\r\n1";
    let blocks = code_blocks::parse(source).unwrap();
    assert_eq!(blocks.len(), 1, "{blocks:?}");
    let block = &blocks[0];
    assert_eq!(block.scheme, "rust");
    assert_eq!(block.identifier, "demo");
    assert_eq!(block.body, "  // keep\r\npub fn value() -> i32 { 7 }\r\n");
    let chars: Vec<_> = source.chars().collect();
    assert_eq!(
        chars[block.body_span.start..block.body_span.end]
            .iter()
            .collect::<String>(),
        block.body
    );
    assert_eq!(
        chars[block.name_span.start..block.name_span.end]
            .iter()
            .collect::<String>(),
        "demo"
    );
    assert_eq!(block.span.start, 5);
    assert_eq!(
        code_blocks::preflight(&blocks, Target::Rust, false)[0].code,
        "CB004"
    );
    assert!(code_blocks::preflight(&blocks, Target::Rust, true).is_empty());
    assert_eq!(
        code_blocks::preflight(&blocks, Target::Java, true)[0].code,
        "CB003"
    );
}
