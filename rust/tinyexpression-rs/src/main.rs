use std::env;
use std::fs;
use std::io::{self, Read};
use std::process::ExitCode;

use tinyexpression_rs::{parse, FrontendError};

const EXIT_SUCCESS: u8 = 0;
const EXIT_USAGE: u8 = 2;
const EXIT_PARSE: u8 = 3;
const EXIT_MAPPING_OR_IO: u8 = 4;

fn usage() -> &'static str {
    "usage: tinyexpression-rs parse [FILE|-]"
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
    if arguments.is_empty()
        || arguments.first().map(String::as_str) != Some("parse")
        || arguments.len() > 2
    {
        eprintln!("{}", usage());
        return Err(EXIT_USAGE);
    }
    let input = match source(arguments.get(1).map(String::as_str)) {
        Ok(source) => source,
        Err(error) => {
            println!("{}", json_error("io", &error.to_string()));
            return Err(EXIT_MAPPING_OR_IO);
        }
    };
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
            Err(EXIT_MAPPING_OR_IO)
        }
    }
}

fn main() -> ExitCode {
    match run() {
        Ok(()) => ExitCode::from(EXIT_SUCCESS),
        Err(code) => ExitCode::from(code),
    }
}
