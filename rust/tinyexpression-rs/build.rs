use std::{env, fs, path::Path};

fn collect(dir: &Path, files: &mut Vec<std::path::PathBuf>) {
    for entry in fs::read_dir(dir).expect("runtime source directory") {
        let entry = entry.expect("runtime source entry");
        let kind = entry.file_type().expect("runtime source type");
        assert!(
            !kind.is_symlink(),
            "runtime bundle must not follow symlinks"
        );
        let path = entry.path();
        if kind.is_dir() {
            collect(&path, files);
        } else if path.extension().is_some_and(|e| e == "rs") {
            files.push(path);
        }
    }
}

fn main() {
    println!("cargo:rerun-if-changed=src");
    if env::var_os("CARGO_FEATURE_RUNTIME_BUNDLE").is_none() {
        return;
    }
    let root = std::path::PathBuf::from(env::var_os("CARGO_MANIFEST_DIR").unwrap());
    let mut files = vec![];
    collect(&root.join("src"), &mut files);
    files.sort();
    let mut out = String::from("&[\n");
    for path in files {
        let relative = path
            .strip_prefix(&root)
            .unwrap()
            .to_string_lossy()
            .replace('\\', "/");
        out.push_str(&format!(
            "({relative:?}, include_str!({:?})),\n",
            path.to_str().unwrap()
        ));
    }
    out.push_str("]\n");
    fs::write(
        Path::new(&env::var_os("OUT_DIR").unwrap()).join("runtime_bundle.rs"),
        out,
    )
    .unwrap();
}
