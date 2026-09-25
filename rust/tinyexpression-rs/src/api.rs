//! The JSON contract shared by the `tinyexpression` CLI, the C ABI and the wasm32 exports
//! (issue #181).
//!
//! Every entry point takes UTF-8 source text and returns a [`Response`]: the exit code the CLI
//! uses and one line of JSON. The CLI prints `json` and exits with `exit_code`;
//! `tinyexpression-ffi` hands both to C and wasm callers unchanged. The JSON document, not a
//! Rust or C struct, is the stable interface: fields may be added, existing fields keep their
//! meaning (see `rust/README.md`, "ABI stability").

use crate::formula_info::{self, LoadError, LoadedFormula, LoaderOptions};
use crate::request::{self, Request};
use crate::runtime::{
    calculator_result, java_string, Context, ContextClock, ErrorKind, EvalError, ExternalHost,
    Host, NoExternals, Options, Program, TraceRecorder, XorShiftRandom,
};
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
        Err(error) => return load_failure(error),
    };
    if run {
        let mut external = NoExternals;
        formulas_json(&formulas, Some((&Context::new(), &mut external, seed)))
    } else {
        formulas_json(&formulas, None)
    }
}

/// The `formulas` response of `load` (no evaluation) and `run` (every formula once on its own
/// copy of `context`).
fn formulas_json(
    formulas: &[LoadedFormula],
    evaluation: Option<(&Context, &mut dyn ExternalHost, u64)>,
) -> Response {
    let mut failed = false;
    let mut items = Vec::with_capacity(formulas.len());
    if let Some((context, external, seed)) = evaluation {
        let mut random = XorShiftRandom::new(seed);
        let mut host = Host {
            external,
            clock: &ContextClock,
            random: &mut random,
        };
        let results = formula_info::evaluate_all(formulas, context, &mut host);
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
        for formula in formulas {
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

fn read_request(text: &str, source_field: &str) -> Result<(Request, String), Response> {
    let usage = |message: String| Response::error(EXIT_USAGE, "request", &message);
    let json = request::parse_json(text).map_err(|e| usage(format!("invalid JSON: {e}")))?;
    let source = json
        .str_field(source_field)
        .ok_or_else(|| usage(format!("the request needs a string field {source_field:?}")))?
        .to_owned();
    let request = request::read_request(&json).map_err(usage)?;
    Ok((request, source))
}

/// `{"kind":<Java exception>,"message":...}` plus the parser diagnostic when there is one.
fn eval_error_json(stage: &str, error: &EvalError) -> String {
    let diagnostic = error
        .diagnostic
        .as_ref()
        .map(|d| format!(",\"diagnostic\":{}", d.canonical_json()))
        .unwrap_or_default();
    format!(
        "{{\"ok\":false,\"stage\":{},\"error\":{}{diagnostic}}}",
        json_string(stage),
        error.canonical_json()
    )
}

/// `eval-context` (issue #201): evaluates `formula` as the Java `P4_AST_EVALUATOR` calculator
/// does, with the request's result type, number type, `CalculationContext` variables and
/// stubbed externals (the request format is documented in `request.rs` and the crate README).
///
/// Success is `{"ok":true,"value":...,"text":<String.valueOf(result)>}`. A formula the
/// calculator cannot be built from fails with `"stage":"create"` (exit 3 for a parse error,
/// 4 otherwise), an evaluation failure with `"stage":"apply"` (exit 5); both carry
/// `"error":{"kind":<Java exception>,"message":...}` and, for parse errors, `"diagnostic"`.
pub fn eval_context_json(request_text: &str) -> Response {
    eval_context_response(request_text, None)
}

/// `eval-context --trace` / `te_eval_trace` (issue #201, stage 3): [`eval_context_json`] with
/// the tree walker's evaluation trace. The response is the `eval-context` response plus
/// `"trace":{"steps","recorded","truncated","root"}` (see [`crate::runtime::trace`]); a
/// formula that cannot be built has `"trace":null`. The value is the one `eval-context`
/// returns: tracing only observes the walker.
pub fn eval_trace_json(request_text: &str) -> Response {
    let mut recorder = TraceRecorder::default();
    eval_context_response(request_text, Some(&mut recorder))
}

/// `eval --trace`: a plain formula traced on an empty context (float result and numbers), the
/// `eval-context` defaults.
pub fn eval_formula_trace_json(source: &str) -> Response {
    eval_trace_json(&format!("{{\"formula\":{}}}", json_string(source)))
}

fn with_trace(json: String, trace: Option<&TraceRecorder>) -> String {
    match trace {
        None => json,
        Some(recorder) => format!(
            "{},\"trace\":{}}}",
            &json[..json.len() - 1],
            recorder.to_json()
        ),
    }
}

fn eval_context_response(request_text: &str, mut trace: Option<&mut TraceRecorder>) -> Response {
    let (mut request, formula) = match read_request(request_text, "formula") {
        Ok(read) => read,
        Err(response) => return response,
    };
    let options = Options::new(request.result_type).with_number_type(request.number_type);
    let program = match Program::new(&formula, options) {
        Ok(program) => program,
        Err(error) => {
            let exit = if error.kind == ErrorKind::Parse && error.diagnostic.is_some() {
                EXIT_PARSE
            } else {
                EXIT_MAPPING
            };
            let json = eval_error_json("create", &error);
            let json = if trace.is_some() {
                format!("{},\"trace\":null}}", &json[..json.len() - 1])
            } else {
                json
            };
            return Response::failure(exit, json);
        }
    };
    let mut random = XorShiftRandom::new(request.seed);
    let mut host = Host {
        external: &mut request.externals,
        clock: &ContextClock,
        random: &mut random,
    };
    let result = match trace.as_deref_mut() {
        Some(recorder) => program.eval_tree_traced(&mut request.context, &mut host, recorder),
        None => program.eval_tree(&mut request.context, &mut host),
    };
    let trace = trace.as_deref();
    match calculator_result(result) {
        Ok(value) => Response::ok(with_trace(
            format!(
                "{{\"ok\":true,\"value\":{},\"text\":{}}}",
                value.canonical_json(),
                json_string(&java_string(&value))
            ),
            trace,
        )),
        Err(error) => Response::failure(
            EXIT_EVALUATION,
            with_trace(eval_error_json("apply", &error), trace),
        ),
    }
}

/// `run-context` (issue #201): `run` on the FormulaInfo `document` of the request, every formula
/// evaluated once on its own copy of the request's context with its stubbed externals.
pub fn formula_info_context_json(request_text: &str, options: &LoaderOptions) -> Response {
    let (mut request, document) = match read_request(request_text, "document") {
        Ok(read) => read,
        Err(response) => return response,
    };
    match formula_info::load(&document, options) {
        Ok(formulas) => formulas_json(
            &formulas,
            Some((&request.context, &mut request.externals, request.seed)),
        ),
        Err(error) => load_failure(error),
    }
}

fn load_failure(error: LoadError) -> Response {
    Response::failure(
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
