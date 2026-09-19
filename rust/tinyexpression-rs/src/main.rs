use std::env;
use std::fs;
use std::io::{self, Read};
use std::process::ExitCode;

use tinyexpression_rs::{evaluate, parse, EvaluationError, FrontendError};

const EXIT_SUCCESS: u8 = 0;
const EXIT_USAGE: u8 = 2;
const EXIT_PARSE: u8 = 3;
const EXIT_MAPPING: u8 = 4;
const EXIT_EVALUATION: u8 = 5;
const EXIT_IO: u8 = 6;

fn usage() -> &'static str {
    "usage: tinyexpression-rs <parse|eval> [FILE|-]"
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
        unlaxer_runtime::json_string(stage),
        unlaxer_runtime::json_string(message)
    )
}

fn run() -> Result<(), u8> {
    let arguments: Vec<String> = env::args().skip(1).collect();
    if arguments.first().map(String::as_str) == Some("--help") {
        println!("{}", usage());
        return Ok(());
    }
    let command = arguments.first().map(String::as_str);
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
    }
}

fn main() -> ExitCode {
    match run() {
        Ok(()) => ExitCode::from(EXIT_SUCCESS),
        Err(code) => ExitCode::from(code),
    }
}
