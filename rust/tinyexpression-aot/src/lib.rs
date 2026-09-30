//! Build-only tools for trusted authors. Never called by parse/check/eval or IDE APIs.
//! This is not a sandbox: permitted source can use the permissions of the compiler/runner.
use serde_json::{json, Value};
use sha2::{Digest, Sha256};
use std::{
    fs,
    path::{Path, PathBuf},
    process::{Command, Output},
};
use tinyexpression_rs::{
    code_blocks::{self, CodeBlock, Target},
    runtime::bindings::BINDING_ABI,
};

pub const FORMAT: &str = "tinyexpression-rust-aot-v1";

#[derive(Debug)]
pub struct BuildError {
    pub diagnostics: Vec<Value>,
}
impl std::fmt::Display for BuildError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}", self.json())
    }
}
impl std::error::Error for BuildError {}
impl BuildError {
    pub fn json(&self) -> Value {
        json!({"ok":false,"stage":"build","diagnostics":self.diagnostics})
    }
    fn one(code: &str, origin: &str, message: impl ToString) -> Self {
        Self {
            diagnostics: vec![json!({"code":code,"origin":origin,"message":message.to_string()})],
        }
    }
}
impl From<std::io::Error> for BuildError {
    fn from(error: std::io::Error) -> Self {
        Self::one("CB103", "io", error)
    }
}

#[derive(Clone, Debug)]
pub struct BuildOptions {
    pub allow_rust_code: bool,
    pub rustc: PathBuf,
    pub target: Option<String>,
    pub opt_level: u8,
}
impl Default for BuildOptions {
    fn default() -> Self {
        Self {
            allow_rust_code: false,
            rustc: "rustc".into(),
            target: None,
            opt_level: 1,
        }
    }
}
#[derive(Debug)]
pub struct Artifact {
    pub directory: PathBuf,
    pub binary: PathBuf,
    pub build_id: String,
}
impl Artifact {
    pub fn json(&self) -> Value {
        json!({"ok":true,"binary":self.binary,"manifest":self.directory.join("artifact.json"),"buildId":self.build_id})
    }
}

struct RuntimeCache {
    key: String,
    path: PathBuf,
    hash: String,
}
/// Reuses only a verified runtime rlib within this builder instance. Formula
/// binaries are always rebuilt; there is no shared/persistent executable cache.
pub struct Builder {
    pub options: BuildOptions,
    runtime: Option<RuntimeCache>,
}
impl Builder {
    pub fn new(options: BuildOptions) -> Self {
        Self {
            options,
            runtime: None,
        }
    }

    pub fn build(&mut self, source: &str, destination: &Path) -> Result<Artifact, BuildError> {
        let blocks =
            code_blocks::parse(source).map_err(|e| BuildError::one("CB100", "parser", e))?;
        let errors = code_blocks::preflight(&blocks, Target::Rust, self.options.allow_rust_code);
        if !errors.is_empty() {
            return Err(BuildError { diagnostics: errors.iter().map(|e| json!({
                "code":e.code,"origin":"preflight","message":e.to_string(),"span":[e.span.start,e.span.end]
            })).collect() });
        }
        if !self.options.allow_rust_code {
            return Err(BuildError::one(
                "CB004",
                "preflight",
                "AOT build requires explicit host permission",
            ));
        }
        if self.options.opt_level > 3 {
            return Err(BuildError::one(
                "CB104",
                "options",
                "opt level must be 0..3",
            ));
        }
        // No compiler or filesystem mutation before permission/preflight succeeds.
        // rustc runs in the output directory later. Preserve a caller-relative
        // executable path without canonicalizing rustup's name-sensitive symlink.
        if self.options.rustc.is_relative() && self.options.rustc.components().count() > 1 {
            self.options.rustc = std::env::current_dir()?.join(&self.options.rustc);
        }
        let version = Command::new(&self.options.rustc)
            .arg("-vV")
            .output()
            .map_err(|e| BuildError::one("CB102", "toolchain", e))?;
        if !version.status.success() {
            return Err(BuildError::one(
                "CB102",
                "toolchain",
                String::from_utf8_lossy(&version.stderr),
            ));
        }
        let version = String::from_utf8_lossy(&version.stdout).into_owned();
        let target = self
            .options
            .target
            .clone()
            .or_else(|| {
                version
                    .lines()
                    .find_map(|l| l.strip_prefix("host: ").map(str::to_owned))
            })
            .ok_or_else(|| {
                BuildError::one("CB102", "toolchain", "rustc did not report its host target")
            })?;
        let inventory: Vec<_> = tinyexpression_rs::runtime_bundle::FILES
            .iter()
            .map(|(path, body)| json!({"path":path,"sha256":digest(body.as_bytes())}))
            .collect();
        let runtime_id = digest(json!(inventory).to_string().as_bytes());
        let runtime_key = digest(
            json!([
                runtime_id,
                version,
                target,
                self.options.opt_level,
                "rlib",
                "edition2021"
            ])
            .to_string()
            .as_bytes(),
        );
        let mut manifest = json!({
            "format":FORMAT,"bindingAbi":BINDING_ABI,"status":"building",
            "sourceSha256":digest(source.as_bytes()),"runtimeSha256":runtime_id,
            "runtimeVersion":tinyexpression_rs::api::VERSION,"ubnfcCommit":tinyexpression_rs::api::UBNFC_COMMIT,
            "grammarSha256":tinyexpression_rs::api::GRAMMAR_SHA256,
            "builderVersion":env!("CARGO_PKG_VERSION"),"builderSourceSha256":digest(include_str!("lib.rs").as_bytes()),
            "rustc":version,"target":target,"optLevel":self.options.opt_level,
            "runtimeDependencies":[],"runtimeFiles":inventory,
            "builderDependencies":{"sha2":"0.10.9","serde_json":"1.0.151"},
            "builderLockSha256":digest(include_bytes!(concat!(env!("OUT_DIR"), "/builder.lock")))
        });
        let build_id = digest(manifest.to_string().as_bytes());
        manifest["buildId"] = json!(build_id);
        // Exact fresh destination, never overwrite/clean a caller's existing directory.
        fs::create_dir(destination)?;
        let out = fs::canonicalize(destination)?;
        fs::write(out.join("source.tiny"), source)?;
        fs::write(
            out.join("builder.lock"),
            include_bytes!(concat!(env!("OUT_DIR"), "/builder.lock")),
        )?;
        fs::write(out.join("building.json"), manifest.to_string())?;
        let result = self.compile(source, &blocks, &out, &target, &runtime_key, &mut manifest);
        match result {
            Ok(binary) => {
                manifest["status"] = json!("complete");
                manifest["binarySha256"] = json!(digest(&fs::read(&binary)?));
                fs::write(out.join("artifact.json"), manifest.to_string())?;
                Ok(Artifact {
                    directory: out,
                    binary,
                    build_id,
                })
            }
            Err(error) => {
                // Preserve failed-build evidence, but never write the success manifest.
                let _ = fs::write(out.join("failure.json"), error.json().to_string());
                Err(error)
            }
        }
    }

    fn rustc(&self, out: &Path, target: &str) -> Command {
        let mut c = Command::new(&self.options.rustc);
        c.current_dir(out)
            .args([
                "--edition=2021",
                "--error-format=json",
                "--target",
                target,
                "-C",
            ])
            .arg(format!("opt-level={}", self.options.opt_level))
            .env("CARGO_PKG_VERSION", tinyexpression_rs::api::VERSION);
        c
    }

    fn compile(
        &mut self,
        source: &str,
        blocks: &[CodeBlock],
        out: &Path,
        target: &str,
        runtime_key: &str,
        manifest: &mut Value,
    ) -> Result<PathBuf, BuildError> {
        for (path, body) in tinyexpression_rs::runtime_bundle::FILES {
            let path = out.join("runtime").join(path);
            fs::create_dir_all(path.parent().unwrap())?;
            fs::write(path, body)?;
        }
        let rlib = out.join("libtinyexpression_rs.rlib");
        let cached = self
            .runtime
            .as_ref()
            .filter(|r| r.key == runtime_key)
            .filter(|r| fs::read(&r.path).is_ok_and(|b| digest(&b) == r.hash));
        if let Some(cache) = cached {
            fs::copy(&cache.path, &rlib)?;
            manifest["runtimeCacheHit"] = json!(true);
        } else {
            let output = self
                .rustc(out, target)
                .args([
                    "--crate-name",
                    "tinyexpression_rs",
                    "--crate-type=rlib",
                    "runtime/src/lib.rs",
                    "-o",
                    "libtinyexpression_rs.rlib",
                ])
                .output()
                .map_err(|e| BuildError::one("CB102", "toolchain", e))?;
            compiler_result(output, blocks, &[])?;
            self.runtime = Some(RuntimeCache {
                key: runtime_key.into(),
                hash: digest(&fs::read(&rlib)?),
                path: rlib.clone(),
            });
            manifest["runtimeCacheHit"] = json!(false);
        }
        fs::create_dir(out.join("blocks"))?;
        let mut generated =
            String::from("use tinyexpression_rs::runtime::bindings::{LinkedCode,CompiledBlock};\n");
        for (i, block) in blocks.iter().enumerate() {
            fs::write(out.join(format!("blocks/block_{i}.rs")), &block.body)?;
            generated.push_str(&format!("#[path=\"blocks/block_{i}.rs\"] mod block_{i};\n"));
        }
        generated.push_str(&format!(
            "const SOURCE: &str = {source:?};\nfn main() {{\nlet declarations = [\n"
        ));
        let mut wrapper_spans = vec![];
        for (i, block) in blocks.iter().enumerate() {
            let start = generated.len();
            generated.push_str(&format!("CompiledBlock {{ identifier: {:?}, body: {:?}, register: block_{i}::register }},\n", block.identifier, block.body));
            wrapper_spans.push((start, generated.len(), block.name_span));
        }
        generated.push_str("] ;\nlet mut linked = match LinkedCode::new(SOURCE, &declarations) {\nOk(v)=>v, Err(e)=>{println!(\"{}\", e.canonical_json());std::process::exit(4)}};\n");
        generated.push_str("use std::io::Read;\nlet mut input=String::new();\nif let Err(e)=std::io::stdin().read_to_string(&mut input){eprintln!(\"{e}\");std::process::exit(6)}\nif input.trim().is_empty(){input=\"{}\".into();}\nlet response=linked.evaluate_request(&input);\nprintln!(\"{}\",response.json);\nstd::process::exit(i32::from(response.exit_code));\n}\n");
        fs::write(out.join("main.rs"), generated)?;
        let output = self
            .rustc(out, target)
            .args([
                "--crate-name",
                "tinyexpression_program",
                "main.rs",
                "--extern",
                "tinyexpression_rs=libtinyexpression_rs.rlib",
                "-o",
                "program.building",
            ])
            .output()
            .map_err(|e| BuildError::one("CB102", "toolchain", e))?;
        compiler_result(output, blocks, &wrapper_spans)?;
        let binary = out.join(if target.contains("windows") {
            "program.exe"
        } else {
            "program"
        });
        fs::rename(out.join("program.building"), &binary)?;
        Ok(binary)
    }
}

fn digest(bytes: &[u8]) -> String {
    format!("{:x}", Sha256::digest(bytes))
}

fn compiler_result(
    output: Output,
    blocks: &[CodeBlock],
    wrappers: &[(usize, usize, tinyexpression_rs::Span)],
) -> Result<(), BuildError> {
    if output.status.success() {
        return Ok(());
    }
    let mut diagnostics = vec![];
    for line in String::from_utf8_lossy(&output.stderr).lines() {
        let Ok(error) = serde_json::from_str::<Value>(line) else {
            continue;
        };
        if error["level"] != "error" {
            continue;
        }
        let mut locations = vec![];
        if let Some(spans) = error["spans"].as_array() {
            for span in spans {
                let file = span["file_name"].as_str().unwrap_or("").replace('\\', "/");
                let start = span["byte_start"].as_u64().unwrap_or(0) as usize;
                let end = span["byte_end"].as_u64().unwrap_or(0) as usize;
                let mut location =
                    json!({"file":file,"byteSpan":[start,end],"primary":span["is_primary"]});
                let block_index = file
                    .strip_prefix("blocks/block_")
                    .and_then(|s| s.strip_suffix(".rs"))
                    .and_then(|s| s.parse::<usize>().ok());
                if let Some(b) = block_index.and_then(|i| blocks.get(i)) {
                    if let (Some(before), Some(through)) = (b.body.get(..start), b.body.get(..end))
                    {
                        location["origin"] = json!("source");
                        location["span"] = json!([
                            b.body_span.start + before.chars().count(),
                            b.body_span.start + through.chars().count()
                        ]);
                    }
                } else if file == "main.rs" {
                    location["origin"] = json!("wrapper");
                    if let Some((_, _, name)) =
                        wrappers.iter().find(|(a, b, _)| *a <= start && end <= *b)
                    {
                        location["span"] = json!([name.start, name.end]);
                    }
                } else {
                    location["origin"] = json!("runtime");
                }
                locations.push(location);
            }
        }
        diagnostics.push(json!({"code":"CB101","origin":"compiler","message":error["message"],"compilerCode":error["code"],"locations":locations}));
    }
    if diagnostics.is_empty() {
        return Err(BuildError::one(
            "CB101",
            "compiler",
            String::from_utf8_lossy(&output.stderr),
        ));
    }
    Err(BuildError { diagnostics })
}
