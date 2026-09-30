mod common;
use common::{run, Temp};
use serde_json::{json, Value};
use std::{
    fs,
    io::Write,
    process::{Command, Stdio},
};
use tinyexpression_aot::{BuildOptions, Builder};

const BODY: &str = "use tinyexpression_rs::runtime::bindings::{ClassBindings,BindingError};\npub fn register(c: &mut ClassBindings) -> Result<(), BindingError> { c.bind::<(), f32, _>(\"answer\", |_, ()| Ok(42.0)) }\n";
fn source(body: &str) -> String {
    format!("```rust:Demo\n{body}```\nimport Demo#answer as answer; external returning as number answer()")
}
fn manifest(dir: &std::path::Path) -> Value {
    serde_json::from_str(&fs::read_to_string(dir.join("artifact.json")).unwrap()).unwrap()
}

#[test]
fn permission_and_preflight_precede_compiler_and_output() {
    let tmp = Temp::new();
    let out = tmp.0.join("never-created");
    let mut builder = Builder::new(BuildOptions {
        rustc: tmp.0.join("missing-rustc"),
        ..BuildOptions::default()
    });
    assert_eq!(
        builder.build(&source(BODY), &out).unwrap_err().diagnostics[0]["code"],
        "CB004"
    );
    builder.options.allow_rust_code = true;
    assert_eq!(
        builder
            .build(&source(BODY).replace("rust:Demo", "python:Demo"), &out)
            .unwrap_err()
            .diagnostics[0]["code"],
        "CB001"
    );
    assert_eq!(
        builder
            .build(&source(BODY).replace("rust:Demo", "java:Demo"), &out)
            .unwrap_err()
            .diagnostics[0]["code"],
        "CB003"
    );
    assert_eq!(
        builder
            .build(&format!("```rust:Demo\n{BODY}```\n{}", source(BODY)), &out)
            .unwrap_err()
            .diagnostics[0]["code"],
        "CB002"
    );
    assert_eq!(
        builder.build(&source(BODY), &out).unwrap_err().diagnostics[0]["code"],
        "CB102"
    );
    assert!(!out.exists());
}

#[test]
fn builds_real_code_preserves_diagnostics_and_never_reuses_wrong_artifacts() {
    let tmp = Temp::new();
    let mut builder = Builder::new(BuildOptions {
        allow_rust_code: true,
        opt_level: 0,
        ..BuildOptions::default()
    });
    let first = builder.build(&source(BODY), &tmp.0.join("first")).unwrap();
    assert_eq!(run(&first.binary, "{}").1["text"], "42.0");
    assert!(!run(&first.binary, r#"{"formula":"2"}"#).0);
    let m = manifest(&first.directory);
    assert_eq!(m["status"], "complete");
    assert_eq!(m["runtimeCacheHit"], false);
    assert!(!m["runtimeFiles"].as_array().unwrap().is_empty());
    assert_eq!(m["runtimeDependencies"], json!([]));
    assert_eq!(m["builderLockSha256"].as_str().unwrap().len(), 64);
    let bytes = fs::read(&first.binary).unwrap();
    assert!(builder.build(&source(BODY), &first.directory).is_err());
    assert_eq!(fs::read(&first.binary).unwrap(), bytes);
    let second = builder
        .build(
            &source(&BODY.replace("42.0", "43.0")),
            &tmp.0.join("second"),
        )
        .unwrap();
    assert_eq!(run(&second.binary, "{}").1["text"], "43.0");
    assert_ne!(first.build_id, second.build_id);
    assert_eq!(manifest(&second.directory)["runtimeCacheHit"], true);
    // A hash-mismatching runtime is not a valid cache entry.
    fs::write(
        first.directory.join("libtinyexpression_rs.rlib"),
        b"corrupt",
    )
    .unwrap();
    let third = builder.build(&source(BODY), &tmp.0.join("third")).unwrap();
    assert_eq!(manifest(&third.directory)["runtimeCacheHit"], false);
    assert_eq!(first.build_id, third.build_id);
    // CRLF and non-ASCII text before the compiler's byte span, in block 2.
    let broken_body = BODY
        .replace("Ok(42.0)", "Ok(missing_value)")
        .replace('\n', "\r\n");
    let broken = format!(
        "// 😀\r\n```rust:Unused\r\n// あ\r\n{}\r\n```\r\n{}",
        BODY,
        source(&format!("// 😀日本語\r\n{broken_body}"))
    );
    let failed_dir = tmp.0.join("broken");
    let error = builder.build(&broken, &failed_dir).unwrap_err();
    let expected_start = broken[..broken.find("missing_value").unwrap()]
        .chars()
        .count();
    assert!(
        error
            .diagnostics
            .iter()
            .any(
                |d| d["locations"]
                    .as_array()
                    .is_some_and(|locations| locations.iter().any(|l| l["origin"] == "source"
                        && l["span"]
                            == json!([expected_start, expected_start + "missing_value".len()])))
            ),
        "{error}"
    );
    assert!(!failed_dir.join("artifact.json").exists());
    assert!(failed_dir.join("failure.json").exists());
    // Missing hook is a generated-wrapper error, not a Rust-body syntax error.
    let error = builder
        .build(&source("pub fn wrong() {}\n"), &tmp.0.join("no-register"))
        .unwrap_err();
    assert!(
        error
            .diagnostics
            .iter()
            .any(|d| d["locations"].as_array().is_some_and(|a| a
                .iter()
                .any(|l| l["origin"] == "wrapper" && l["span"] == json!([8, 12])))),
        "{error}"
    );
    let hostile_label = source(BODY).replace("rust:Demo", "rust:../../escape");
    assert!(builder
        .build(&hostile_label, &tmp.0.join("invalid-label"))
        .is_err());
    assert!(!tmp.0.join("escape.rs").exists());
    // A dotted opaque label is not interpolated as a Rust module/path.
    let dotted = source(BODY)
        .replace("rust:Demo", "rust:a.b.Demo")
        .replace("import Demo#", "import a.b.Demo#");
    let dotted = builder.build(&dotted, &tmp.0.join("dotted")).unwrap();
    assert_eq!(run(&dotted.binary, "{}").1["text"], "42.0");
    // Building never executes the registration hook. It fails structurally only
    // when the generated program is explicitly run.
    let panicking = BODY.replace(
        "c.bind::<(), f32, _>(\"answer\", |_, ()| Ok(42.0))",
        "panic!(\"hook executed\")",
    );
    let panicking = builder
        .build(&source(&panicking), &tmp.0.join("panic-hook"))
        .unwrap();
    let (success, diagnostic) = run(&panicking.binary, "{}");
    assert!(!success);
    assert_eq!(diagnostic["diagnostics"][0]["code"], "CB006");
    assert_eq!(diagnostic["diagnostics"][0]["span"], json!([8, 12]));
}

#[test]
fn cli_permission_and_real_binary_end_to_end() {
    let tmp = Temp::new();
    let binary = env!("CARGO_BIN_EXE_tinyexpression-aot");
    assert!(Command::new(binary)
        .arg("--help")
        .output()
        .unwrap()
        .status
        .success());
    for allowed in [false, true] {
        let dir = tmp.0.join(if allowed { "allow" } else { "deny" });
        let mut command = Command::new(binary);
        command.args(["build", "--out"]).arg(&dir);
        if allowed {
            command.arg("--allow-rust-code");
        } else {
            command.env("PATH", "");
        }
        let mut child = command
            .arg("-")
            .stdin(Stdio::piped())
            .stdout(Stdio::piped())
            .spawn()
            .unwrap();
        child
            .stdin
            .take()
            .unwrap()
            .write_all(source(BODY).as_bytes())
            .unwrap();
        let output = child.wait_with_output().unwrap();
        let value: Value = serde_json::from_slice(&output.stdout).unwrap();
        assert_eq!(output.status.success(), allowed, "{value}");
        if allowed {
            assert_eq!(
                run(
                    std::path::Path::new(value["binary"].as_str().unwrap()),
                    "{}"
                )
                .1["text"],
                "42.0"
            );
        } else {
            assert_eq!(value["diagnostics"][0]["code"], "CB004");
            assert!(!dir.exists());
        }
    }
}
