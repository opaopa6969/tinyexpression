use std::{
    io::{self, Read},
    path::PathBuf,
};
use tinyexpression_aot::{BuildOptions, Builder};

fn main() {
    let mut args = std::env::args().skip(1);
    let command = args.next();
    if command.as_deref() == Some("--help") {
        println!("tinyexpression-aot build --allow-rust-code --out NEW_DIRECTORY [--rustc PATH] [--target TRIPLE] [FILE|-]\n\nBuilds trusted Rust code blocks into a native program. Requires rustc at build time,\nnot Java or Cargo. The resulting program reads the normal context JSON on stdin\nand needs no compiler at runtime. TinyExpression itself still uses the typed-AST\nevaluator; this is not direct machine-code generation for every DSL expression.\nNo sandbox: only enable for fully trusted authors. Existing output directories\nare never overwritten. Without --allow-rust-code, nothing is compiled or written.");
        return;
    }
    let mut options = BuildOptions::default();
    let mut out = None;
    let mut input = None;
    let mut valid = command.as_deref() == Some("build");
    while let Some(arg) = args.next() {
        match arg.as_str() {
            "--allow-rust-code" => options.allow_rust_code = true,
            "--out" => {
                out = args.next().map(PathBuf::from);
                valid &= out.is_some();
            }
            "--rustc" => match args.next() {
                Some(p) => options.rustc = p.into(),
                None => valid = false,
            },
            "--target" => {
                options.target = args.next();
                valid &= options.target.is_some();
            }
            _ if input.is_none() && (!arg.starts_with('-') || arg == "-") => input = Some(arg),
            _ => valid = false,
        }
    }
    if !valid || out.is_none() {
        eprintln!("use tinyexpression-aot --help");
        std::process::exit(2);
    }
    let source = match input.as_deref() {
        Some(path) if path != "-" => std::fs::read_to_string(path),
        _ => {
            let mut text = String::new();
            io::stdin().read_to_string(&mut text).map(|_| text)
        }
    };
    let source = match source {
        Ok(s) => s,
        Err(e) => {
            eprintln!("{e}");
            std::process::exit(6)
        }
    };
    match Builder::new(options).build(&source, &out.unwrap()) {
        Ok(artifact) => println!("{}", artifact.json()),
        Err(error) => {
            println!("{}", error.json());
            std::process::exit(4)
        }
    }
}
