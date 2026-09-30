use std::{env, fs, path::PathBuf};
fn main() {
    let root = PathBuf::from(env::var_os("CARGO_MANIFEST_DIR").unwrap());
    let lock = root
        .ancestors()
        .map(|p| p.join("Cargo.lock"))
        .find(|p| p.is_file())
        .expect("build the AOT tool with its pinned Cargo.lock");
    println!("cargo:rerun-if-changed={}", lock.display());
    fs::copy(
        lock,
        PathBuf::from(env::var_os("OUT_DIR").unwrap()).join("builder.lock"),
    )
    .unwrap();
}
