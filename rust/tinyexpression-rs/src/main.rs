//! The `tinyexpression` command-line tool. Every command prints one line of JSON on stdout
//! (the contract in `tinyexpression_rs::api`, shared with the C ABI and wasm32) and exits
//! with the code listed in `--help`.

use std::env;
use std::fs;
use std::io::{self, Read};
use std::process::ExitCode;

use tinyexpression_rs::api::{self, Response, EXIT_IO, EXIT_USAGE};
use tinyexpression_rs::formula_info::{ExecutionBackend, LoaderOptions};

fn usage() -> &'static str {
    "usage: tinyexpression <parse|check|eval> [FILE|-]\n       tinyexpression <load|run> [--default-backend NAME] [FILE|-]"
}

fn help() -> String {
    format!(
        "tinyexpression {version} (ubnfc {ubnfc}) - TinyExpression without a JVM

{usage}
       tinyexpression --help | --version

Commands (input is FILE, or stdin when FILE is `-` or omitted):
  parse   parse a formula and print its typed AST        {{\"ok\":true,\"ast\":...}}
  check   parse, map and type-check a formula            {{\"ok\":true}}
  eval    evaluate a formula with the context-free evaluator
                                                         {{\"ok\":true,\"value\":...}}
  load    load a FormulaInfo document                    {{\"ok\":true,\"formulas\":[{{\"info\":...}}]}}
  run     load a FormulaInfo document and evaluate every formula once on an empty context
                                                         {{\"ok\":...,\"formulas\":[{{\"info\":...,\"value\"|\"error\":...}}]}}

Options:
  --default-backend NAME   load/run: the backend for formulas without an explicit one
  --help                   print this help
  --version                print the version and the ubnfc commit of the vendored parsers

Failures are printed as {{\"ok\":false,\"stage\":...}} on stdout (usage errors on stderr).

Exit codes:
  0  success
  2  invalid arguments
  3  parse failure (including trailing input and FormulaInfo syntax errors)
  4  mapping or type failure
  5  evaluation failure (run: at least one formula failed)
  6  I/O failure
  7  FormulaInfo load failure that is not a syntax error",
        version = api::VERSION,
        ubnfc = api::UBNFC_COMMIT,
        usage = usage(),
    )
}

fn source(argument: Option<&str>) -> Result<String, Response> {
    let result = match argument {
        Some(path) if path != "-" => fs::read_to_string(path),
        _ => {
            let mut source = String::new();
            io::stdin().read_to_string(&mut source).map(|_| source)
        }
    };
    result.map_err(|error| Response::error(EXIT_IO, "io", &error.to_string()))
}

fn time_seed() -> u64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_nanos() as u64)
        .unwrap_or(0x9e37_79b9_7f4a_7c15)
}

fn usage_error() -> Result<Response, u8> {
    eprintln!("{}", usage());
    Err(EXIT_USAGE)
}

fn run(arguments: &[String]) -> Result<Response, u8> {
    let command = arguments.first().map(String::as_str);
    match command {
        Some("--help" | "-h" | "help") if arguments.len() == 1 => {
            println!("{}", help());
            Ok(Response {
                exit_code: 0,
                json: String::new(),
            })
        }
        Some("--version" | "-V") if arguments.len() == 1 => {
            println!(
                "tinyexpression {} (ubnfc {})",
                api::VERSION,
                api::UBNFC_COMMIT
            );
            Ok(Response {
                exit_code: 0,
                json: String::new(),
            })
        }
        Some("load" | "run") => {
            let mut options = LoaderOptions::java_tests();
            let mut path = None;
            let mut rest = arguments[1..].iter();
            while let Some(argument) = rest.next() {
                if argument == "--default-backend" {
                    let Some(backend) = rest.next().and_then(|name| ExecutionBackend::parse(name))
                    else {
                        return usage_error();
                    };
                    options.default_backend = backend;
                } else if path.is_none() {
                    path = Some(argument.as_str());
                } else {
                    return usage_error();
                }
            }
            Ok(match source(path) {
                Ok(input) => {
                    api::formula_info_json(&input, &options, command == Some("run"), time_seed())
                }
                Err(response) => response,
            })
        }
        Some("parse" | "check" | "eval") if arguments.len() <= 2 => {
            Ok(match source(arguments.get(1).map(String::as_str)) {
                Ok(input) => match command {
                    Some("parse") => api::parse_json(&input),
                    Some("check") => api::check_json(&input),
                    _ => api::eval_json(&input),
                },
                Err(response) => response,
            })
        }
        _ => usage_error(),
    }
}

fn main() -> ExitCode {
    let arguments: Vec<String> = env::args().skip(1).collect();
    match run(&arguments) {
        Ok(response) => {
            if !response.json.is_empty() {
                println!("{}", response.json);
            }
            ExitCode::from(response.exit_code)
        }
        Err(code) => ExitCode::from(code),
    }
}
