//! The Rust half of the Java/Rust contract-equality test of `te_eval_context` /
//! `te_eval_trace` / `te_formula_info_context` (issue #221).
//!
//! `src/test/resources/eval-context-contract/requests.tsv` (repository root) holds the requests
//! (`id<TAB>operation<TAB>request JSON`); `rust-responses.tsv` next to it holds what this crate
//! answers (`id<TAB>exit code<TAB>response JSON`). This test requires the recorded responses to
//! be exactly the current ones; the Java `EvalContextContractTest` evaluates the same requests
//! with `EvalContextService` and compares its responses with the recorded Rust ones (success
//! values, failure stage and kind). Neither build needs the other toolchain.
//!
//! After changing the requests or the Rust responses, rewrite the recording with
//! `TE_CONTRACT_UPDATE=1 cargo test --test eval_context_contract`.

use std::fs;
use std::path::PathBuf;

use tinyexpression_rs::api::{eval_context_json, eval_trace_json, formula_info_context_json};
use tinyexpression_rs::formula_info::LoaderOptions;

fn contract_dir() -> PathBuf {
    PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("../../src/test/resources/eval-context-contract")
}

#[test]
fn recorded_rust_responses_are_current() {
    let dir = contract_dir();
    let requests = fs::read_to_string(dir.join("requests.tsv")).expect("requests.tsv");
    let mut actual = String::new();
    for (number, line) in requests.lines().enumerate() {
        if line.is_empty() {
            continue;
        }
        let mut fields = line.splitn(3, '\t');
        let (Some(id), Some(operation), Some(request)) =
            (fields.next(), fields.next(), fields.next())
        else {
            panic!(
                "requests.tsv line {}: expected id<TAB>operation<TAB>request",
                number + 1
            );
        };
        let response = match operation {
            "evalContext" => eval_context_json(request),
            "evalTrace" => eval_trace_json(request),
            "runContext" => formula_info_context_json(request, &LoaderOptions::java_tests()),
            other => panic!(
                "requests.tsv line {}: unknown operation {other}",
                number + 1
            ),
        };
        actual.push_str(&format!(
            "{id}\t{}\t{}\n",
            response.exit_code, response.json
        ));
    }
    let path = dir.join("rust-responses.tsv");
    if std::env::var_os("TE_CONTRACT_UPDATE").is_some() {
        fs::write(&path, &actual).expect("write rust-responses.tsv");
        return;
    }
    let recorded = fs::read_to_string(&path).unwrap_or_default();
    for (expected, got) in recorded.lines().zip(actual.lines()) {
        assert_eq!(
            expected, got,
            "rust-responses.tsv is stale; rerun with TE_CONTRACT_UPDATE=1"
        );
    }
    assert_eq!(
        recorded.lines().count(),
        actual.lines().count(),
        "rust-responses.tsv is stale; rerun with TE_CONTRACT_UPDATE=1"
    );
}
