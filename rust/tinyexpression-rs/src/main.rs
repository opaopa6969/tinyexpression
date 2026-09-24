use std::env;
use std::fs;
use std::io::{self, Read};
use std::process::ExitCode;

use tinyexpression_rs::formula_info::{self, ExecutionBackend, LoadError, LoaderOptions};
use tinyexpression_rs::runtime::{Context, ContextClock, Host, NoExternals, XorShiftRandom};
use tinyexpression_rs::{evaluate, parse, EvaluationError, FrontendError};

const EXIT_SUCCESS: u8 = 0;
const EXIT_USAGE: u8 = 2;
const EXIT_PARSE: u8 = 3;
const EXIT_MAPPING: u8 = 4;
const EXIT_EVALUATION: u8 = 5;
const EXIT_IO: u8 = 6;
const EXIT_LOAD: u8 = 7;

fn usage() -> &'static str {
    "usage: tinyexpression-rs <parse|eval> [FILE|-]\n       tinyexpression-rs <load|run> [--default-backend NAME] [FILE|-]"
}

fn source(argument: Option<&str>) -> io::Result<String> {
    match argument {
        Some(path) if path != "-" => fs::read_to_string(path),
        _ => {
            let mut source = String::new();
            io::stdin().read_to_string(&mut source)?;
            Ok(source)
        }
    }
}

fn json_error(stage: &str, message: &str) -> String {
    format!(
        "{{\"ok\":false,\"stage\":{},\"message\":{}}}",
        tinyexpression_rs::json_string(stage),
        tinyexpression_rs::json_string(message)
    )
}

fn run() -> Result<(), u8> {
    let arguments: Vec<String> = env::args().skip(1).collect();
    if arguments.first().map(String::as_str) == Some("--help") {
        println!("{}", usage());
        return Ok(());
    }
    let command = arguments.first().map(String::as_str);
    if matches!(command, Some("load" | "run")) {
        return formula_info_command(command == Some("run"), &arguments[1..]);
    }
    if arguments.is_empty() || !matches!(command, Some("parse" | "eval")) || arguments.len() > 2 {
        eprintln!("{}", usage());
        return Err(EXIT_USAGE);
    }
    let input = match source(arguments.get(1).map(String::as_str)) {
        Ok(source) => source,
        Err(error) => {
            println!("{}", json_error("io", &error.to_string()));
            return Err(EXIT_IO);
        }
    };
    if command == Some("eval") {
        return match evaluate(&input) {
            Ok(value) => {
                println!("{{\"ok\":true,\"value\":{}}}", value.canonical_json());
                Ok(())
            }
            Err(EvaluationError::Parse(error)) => {
                println!(
                    "{{\"ok\":false,\"stage\":\"parse\",\"diagnostic\":{}}}",
                    error.canonical_json()
                );
                Err(EXIT_PARSE)
            }
            Err(EvaluationError::Mapping(error)) => {
                println!("{}", json_error("mapping", &error));
                Err(EXIT_MAPPING)
            }
            Err(error @ EvaluationError::TypeMismatch { .. }) => {
                println!(
                    "{{\"ok\":false,\"stage\":\"type\",\"error\":{}}}",
                    error.canonical_json()
                );
                Err(EXIT_MAPPING)
            }
            Err(error) => {
                println!(
                    "{{\"ok\":false,\"stage\":\"evaluation\",\"error\":{}}}",
                    error.canonical_json()
                );
                Err(EXIT_EVALUATION)
            }
        };
    }

    match parse(&input) {
        Ok(ast) => {
            println!("{{\"ok\":true,\"ast\":{}}}", ast.canonical_json());
            Ok(())
        }
        Err(FrontendError::Parse(error)) => {
            println!(
                "{{\"ok\":false,\"stage\":\"parse\",\"diagnostic\":{}}}",
                error.canonical_json()
            );
            Err(EXIT_PARSE)
        }
        Err(FrontendError::Mapping(error)) => {
            println!("{}", json_error("mapping", &error));
            Err(EXIT_MAPPING)
        }
        Err(FrontendError::TypeMismatch { message, span }) => {
            println!(
                "{{\"ok\":false,\"stage\":\"type\",\"span\":[{},{}],\"message\":{}}}",
                span.start,
                span.end,
                tinyexpression_rs::json_string(&message)
            );
            Err(EXIT_MAPPING)
        }
    }
}

/// `load` / `run`: a FormulaInfo document (issue #180). The loader is configured as the Java
/// loader tests configure it (`siteId` multi-tenancy attribute, `checkKind` else
/// `calculatorName` as the name); `run` also evaluates every formula once on an empty context.
fn formula_info_command(run: bool, arguments: &[String]) -> Result<(), u8> {
    let mut options = LoaderOptions::java_tests();
    let mut path = None;
    let mut rest = arguments.iter();
    while let Some(argument) = rest.next() {
        if argument == "--default-backend" {
            let Some(backend) = rest.next().and_then(|name| ExecutionBackend::parse(name)) else {
                eprintln!("{}", usage());
                return Err(EXIT_USAGE);
            };
            options.default_backend = backend;
        } else if path.is_none() {
            path = Some(argument.as_str());
        } else {
            eprintln!("{}", usage());
            return Err(EXIT_USAGE);
        }
    }
    let input = match source(path) {
        Ok(source) => source,
        Err(error) => {
            println!("{}", json_error("io", &error.to_string()));
            return Err(EXIT_IO);
        }
    };
    let formulas = match formula_info::load(&input, &options) {
        Ok(formulas) => formulas,
        Err(error) => {
            println!(
                "{{\"ok\":false,\"stage\":\"load\",\"error\":{}}}",
                error.canonical_json()
            );
            return Err(if matches!(error, LoadError::Syntax(_)) {
                EXIT_PARSE
            } else {
                EXIT_LOAD
            });
        }
    };
    let mut failed = false;
    let mut items = Vec::with_capacity(formulas.len());
    if run {
        let mut external = NoExternals;
        let mut random = XorShiftRandom::default();
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
    println!("{{\"ok\":{},\"formulas\":[{}]}}", !failed, items.join(","));
    if failed {
        Err(EXIT_EVALUATION)
    } else {
        Ok(())
    }
}

fn main() -> ExitCode {
    match run() {
        Ok(()) => ExitCode::from(EXIT_SUCCESS),
        Err(code) => ExitCode::from(code),
    }
}
