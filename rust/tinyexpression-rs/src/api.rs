//! The JSON contract shared by the `tinyexpression` CLI, the C ABI and the wasm32 exports
//! (issue #181).
//!
//! Every entry point takes UTF-8 source text and returns a [`Response`]: the exit code the CLI
//! uses and one line of JSON. The CLI prints `json` and exits with `exit_code`;
//! `tinyexpression-ffi` hands both to C and wasm callers unchanged. The JSON document, not a
//! Rust or C struct, is the stable interface: fields may be added, existing fields keep their
//! meaning (see `rust/README.md`, "ABI stability").

use crate::formula_info::{self, LoadError, LoaderOptions};
use crate::runtime::{Context, ContextClock, Host, NoExternals, XorShiftRandom};
use crate::{evaluate, json_string, parse, EvaluationError, FrontendError};

/// Success.
pub const EXIT_SUCCESS: u8 = 0;
/// Invalid command-line arguments (C ABI: a null pointer).
pub const EXIT_USAGE: u8 = 2;
/// Parse failure, including trailing input and FormulaInfo syntax errors.
pub const EXIT_PARSE: u8 = 3;
/// Mapping or type failure.
pub const EXIT_MAPPING: u8 = 4;
/// Evaluation failure.
pub const EXIT_EVALUATION: u8 = 5;
/// I/O failure (C ABI: input that is not UTF-8).
pub const EXIT_IO: u8 = 6;
/// FormulaInfo load failure that is not a syntax error.
pub const EXIT_LOAD: u8 = 7;
/// Internal error (C ABI only: a panic caught at the boundary).
pub const EXIT_INTERNAL: u8 = 70;

/// The crate version (shared with the Java release, see `rust/README.md`).
pub const VERSION: &str = env!("CARGO_PKG_VERSION");
/// The ubnfc commit the vendored parsers were generated from. `rust/check-generated.sh`
/// fails when it differs from `ubnfc_commit` in `rust/ubnfc-pin.txt`.
pub const UBNFC_COMMIT: &str = "cefdbd7be262c9ea7c58a56b28fa0319c354e446";

/// One JSON response and the exit code that goes with it.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Response {
    pub exit_code: u8,
    pub json: String,
}

impl Response {
    fn ok(json: String) -> Self {
        Self {
            exit_code: EXIT_SUCCESS,
            json,
        }
    }

    fn failure(exit_code: u8, json: String) -> Self {
        Self { exit_code, json }
    }

    /// `{"ok":false,"stage":<stage>,"message":<message>}`.
    pub fn error(exit_code: u8, stage: &str, message: &str) -> Self {
        Self::failure(
            exit_code,
            format!(
                "{{\"ok\":false,\"stage\":{},\"message\":{}}}",
                json_string(stage),
                json_string(message)
            ),
        )
    }
}

/// `{"name":"tinyexpression","version":...,"ubnfc":...}`.
pub fn version_json() -> String {
    format!(
        "{{\"name\":\"tinyexpression\",\"version\":{},\"ubnfc\":{}}}",
        json_string(VERSION),
        json_string(UBNFC_COMMIT)
    )
}

fn frontend_failure(error: FrontendError) -> Response {
    match error {
        FrontendError::Parse(error) => Response::failure(
            EXIT_PARSE,
            format!(
                "{{\"ok\":false,\"stage\":\"parse\",\"diagnostic\":{}}}",
                error.canonical_json()
            ),
        ),
        FrontendError::Mapping(error) => Response::error(EXIT_MAPPING, "mapping", &error),
        FrontendError::TypeMismatch { message, span } => Response::failure(
            EXIT_MAPPING,
            format!(
                "{{\"ok\":false,\"stage\":\"type\",\"span\":[{},{}],\"message\":{}}}",
                span.start,
                span.end,
                json_string(&message)
            ),
        ),
    }
}

/// `parse`: `{"ok":true,"ast":...}`.
pub fn parse_json(source: &str) -> Response {
    match parse(source) {
        Ok(ast) => Response::ok(format!("{{\"ok\":true,\"ast\":{}}}", ast.canonical_json())),
        Err(error) => frontend_failure(error),
    }
}

/// `check`: parse, map and type-check without printing the AST: `{"ok":true}`. Failures are
/// the `parse` failures.
pub fn check_json(source: &str) -> Response {
    match parse(source) {
        Ok(_) => Response::ok("{\"ok\":true}".to_owned()),
        Err(error) => frontend_failure(error),
    }
}

/// `eval`: the context-free evaluator, `{"ok":true,"value":...}`.
pub fn eval_json(source: &str) -> Response {
    match evaluate(source) {
        Ok(value) => Response::ok(format!(
            "{{\"ok\":true,\"value\":{}}}",
            value.canonical_json()
        )),
        Err(EvaluationError::Parse(error)) => frontend_failure(FrontendError::Parse(error)),
        Err(EvaluationError::Mapping(error)) => Response::error(EXIT_MAPPING, "mapping", &error),
        Err(error @ EvaluationError::TypeMismatch { .. }) => Response::failure(
            EXIT_MAPPING,
            format!(
                "{{\"ok\":false,\"stage\":\"type\",\"error\":{}}}",
                error.canonical_json()
            ),
        ),
        Err(error) => Response::failure(
            EXIT_EVALUATION,
            format!(
                "{{\"ok\":false,\"stage\":\"evaluation\",\"error\":{}}}",
                error.canonical_json()
            ),
        ),
    }
}

/// `load` / `run`: a FormulaInfo document (issue #180). The loader is configured as the Java
/// loader tests configure it (`siteId` multi-tenancy attribute, `checkKind` else
/// `calculatorName` as the name); `run` also evaluates every formula once on an empty context
/// with `random` seeded by `seed`.
pub fn formula_info_json(source: &str, options: &LoaderOptions, run: bool, seed: u64) -> Response {
    let formulas = match formula_info::load(source, options) {
        Ok(formulas) => formulas,
        Err(error) => {
            return Response::failure(
                if matches!(error, LoadError::Syntax(_)) {
                    EXIT_PARSE
                } else {
                    EXIT_LOAD
                },
                format!(
                    "{{\"ok\":false,\"stage\":\"load\",\"error\":{}}}",
                    error.canonical_json()
                ),
            )
        }
    };
    let mut failed = false;
    let mut items = Vec::with_capacity(formulas.len());
    if run {
        let mut external = NoExternals;
        let mut random = XorShiftRandom::new(seed);
        let mut host = Host {
            external: &mut external,
            clock: &ContextClock,
            random: &mut random,
        };
        let results = formula_info::evaluate_all(&formulas, &Context::new(), &mut host);
        for (formula, result) in formulas.iter().zip(results) {
            let result = match result {
                Ok(value) => format!("\"value\":{}", value.canonical_json()),
                Err(error) => {
                    failed = true;
                    format!("\"error\":{}", error.canonical_json())
                }
            };
            items.push(format!(
                "{{\"info\":{},{result}}}",
                formula.info.canonical_json()
            ));
        }
    } else {
        for formula in &formulas {
            items.push(format!("{{\"info\":{}}}", formula.info.canonical_json()));
        }
    }
    Response::failure(
        if failed {
            EXIT_EVALUATION
        } else {
            EXIT_SUCCESS
        },
        format!("{{\"ok\":{},\"formulas\":[{}]}}", !failed, items.join(",")),
    )
}
