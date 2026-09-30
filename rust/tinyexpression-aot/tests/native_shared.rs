mod common;
use common::{run, Temp};
use serde_json::{json, Value};
use tinyexpression_aot::{BuildOptions, Builder};

#[test]
fn real_rust_bodies_match_java_shared_oracle_without_compilers_on_path() {
    let temp = Temp::new();
    let mut builder = Builder::new(BuildOptions {
        allow_rust_code: true,
        opt_level: 0,
        ..BuildOptions::default()
    });
    let rows: Value = serde_json::from_str(include_str!(
        "../../../src/test/resources/native-bindings/cases.json"
    ))
    .unwrap();
    let blocks = format!(
        "````rust:NativeDemo\n{}````\n```rust:NativeSecond\n{}```\n",
        include_str!("../../../src/test/resources/native-bindings/NativeDemo.rs"),
        include_str!("../../../src/test/resources/native-bindings/NativeSecond.rs")
    );
    let mut ids = std::collections::HashMap::new();
    let mut failures = vec![];
    for (index, row) in rows.as_array().unwrap().iter().enumerate() {
        let name = row["name"].as_str().unwrap();
        let source = format!("{blocks}{}", row["formula"].as_str().unwrap());
        let artifact = builder
            .build(&source, &temp.0.join(name))
            .unwrap_or_else(|e| panic!("{name}: {e}"));
        if let Some(previous) = ids.insert(artifact.build_id.clone(), source.clone()) {
            assert_eq!(
                previous, source,
                "identity reused for different source: {name}"
            );
        }
        let manifest: Value = serde_json::from_str(
            &std::fs::read_to_string(artifact.directory.join("artifact.json")).unwrap(),
        )
        .unwrap();
        assert_eq!(manifest["runtimeCacheHit"], index > 0);
        let request = row.get("request").cloned().unwrap_or(json!({}));
        let (success, actual) = run(&artifact.binary, &request.to_string());
        if let Some(error) = row.get("error") {
            if success || actual["stage"] != "apply" || actual["error"]["kind"] != *error {
                failures.push(format!("{name}: {actual}"));
            }
        } else if !success || actual["text"] != row["text"] {
            failures.push(format!("{name}: {actual}"));
        }
        eprintln!("native shared: {name}: {actual}");
    }
    assert!(failures.is_empty(), "{}", failures.join("\n"));
}
