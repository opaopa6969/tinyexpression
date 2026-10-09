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
    for source in [
        "```rust:demo\nnot valid Rust\n```\n1",
        "````rust:demo\nnot valid ``` Rust\n```\n````\n1",
    ] {
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

#[test]
fn detached_ast_keeps_body_and_rejects_uncompiled_rust() {
    use tinyexpression_rs::generated::ast::Ast;
    let ast = {
        let source = String::from("// 😀\n```rust:demo\r\n  // keep\r\n'\"\t \r\n```\r\n1");
        tinyexpression_rs::parse(&source).unwrap()
    };
    let blocks = code_blocks::from_ast(&ast).unwrap();
    assert_eq!(blocks[0].body, "  // keep\r\n'\"\t \r\n");
    let Ast::FormulaExpr { codeBlocks, .. } = &ast else {
        panic!("not a document")
    };
    for node in [&ast, &codeBlocks[0]] {
        let error = tinyexpression_rs::evaluate_ast(node).unwrap_err();
        assert!(error.to_string().contains("CB005"));
        assert_eq!(error.span(), Some(blocks[0].name_span));
    }
    let java = "```java:Demo\r\n// keep\r\n'\"\r\n```\r\n1";
    let program = tinyexpression_rs::runtime::Program::new(java, Default::default()).unwrap();
    assert_eq!(program.code_blocks()[0].body, "// keep\r\n'\"\r\n");
}

#[test]
fn document_reselection_keeps_every_block() {
    let source =
        "// 😀\n```java:One\n// one\n```\n```rust:Two\r\n'\r\n```\r\nvar $s as string;$s as string";
    let blocks = code_blocks::from_ast(&tinyexpression_rs::parse(source).unwrap()).unwrap();
    assert_eq!(
        blocks
            .iter()
            .map(|b| b.identifier.as_str())
            .collect::<Vec<_>>(),
        ["One", "Two"]
    );
    assert_eq!(
        blocks.iter().map(|b| b.body.as_str()).collect::<Vec<_>>(),
        ["// one\n", "'\r\n"]
    );
    assert!(
        tinyexpression_rs::runtime::Program::new(source, Default::default())
            .unwrap_err()
            .message
            .contains("CB005")
    );
}

#[test]
fn program_class_metadata_comes_from_committed_ast_not_line_scanning() {
    use tinyexpression_rs::runtime::{Options, Program};
    let false_block = "/*\n```java:Fake\nbody\n```\n*/\n1";
    assert!(Program::new(false_block, Options::default())
        .unwrap()
        .code_block_classes()
        .is_empty());
    let real = "```java:One\r// keep\r```\r```java:One\nagain\n```\n1";
    let program = Program::new(real, Options::default()).unwrap();
    assert_eq!(program.code_block_classes(), ["One"]);
    assert_eq!(program.code_blocks().len(), 2);
}
