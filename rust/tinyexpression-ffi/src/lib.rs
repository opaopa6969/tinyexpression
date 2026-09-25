//! C ABI (and, compiled for `wasm32-unknown-unknown`, the wasm exports) of tinyexpression-rs
//! (issue #181). The header is `include/tinyexpression.h`.
//!
//! Every call takes UTF-8 bytes (`ptr`, `len`; not NUL-terminated) and hands back a
//! NUL-terminated UTF-8 JSON document through `out`, which the caller releases with
//! [`te_free`]. The return value is the exit code the `tinyexpression` CLI uses for the same
//! command. The JSON is the CLI's output byte for byte; it is the stable interface (see
//! `rust/README.md`, "ABI stability"), no Rust or C struct crosses the boundary.

use std::ffi::{c_char, CString};
use std::panic::{catch_unwind, AssertUnwindSafe};
use std::ptr;

use tinyexpression_rs::api::{self, Response, EXIT_INTERNAL, EXIT_IO, EXIT_USAGE};
use tinyexpression_rs::formula_info::LoaderOptions;

/// Version of the calling convention below. Bumped only if a signature changes; JSON fields
/// may be added without a bump.
pub const TE_ABI_VERSION: u32 = 1;

fn hand_out(response: Response, out: *mut *mut c_char) -> i32 {
    // The JSON never contains a NUL byte: json_string escapes every control character.
    let json = CString::new(response.json).unwrap_or_else(|_| {
        CString::new("{\"ok\":false,\"stage\":\"internal\",\"message\":\"NUL in output\"}")
            .expect("literal without NUL")
    });
    // SAFETY: the caller passes a writable `char **` (checked non-null by `call`).
    unsafe { *out = json.into_raw() };
    i32::from(response.exit_code)
}

/// Shared boundary: null checks, UTF-8 validation, and no unwinding into the caller.
fn call(
    source: *const u8,
    len: usize,
    out: *mut *mut c_char,
    body: impl FnOnce(&str) -> Response,
) -> i32 {
    if out.is_null() {
        return i32::from(EXIT_USAGE);
    }
    // SAFETY: `out` is non-null and points to a `char *` the caller owns.
    unsafe { *out = ptr::null_mut() };
    if source.is_null() && len != 0 {
        return hand_out(Response::error(EXIT_USAGE, "usage", "source is null"), out);
    }
    let bytes: &[u8] = if len == 0 {
        &[]
    } else {
        // SAFETY: the caller guarantees `source` points to `len` readable bytes.
        unsafe { std::slice::from_raw_parts(source, len) }
    };
    let response = match std::str::from_utf8(bytes) {
        Ok(text) => catch_unwind(AssertUnwindSafe(|| body(text))).unwrap_or_else(|_| {
            Response::error(EXIT_INTERNAL, "internal", "panic in tinyexpression")
        }),
        Err(error) => Response::error(EXIT_IO, "io", &format!("source is not UTF-8: {error}")),
    };
    hand_out(response, out)
}

/// `tinyexpression parse`: `{"ok":true,"ast":...}`.
///
/// # Safety
/// `source` points to `len` readable bytes (may be null when `len` is 0); `out` is writable.
#[no_mangle]
pub unsafe extern "C" fn te_parse(source: *const u8, len: usize, out: *mut *mut c_char) -> i32 {
    call(source, len, out, api::parse_json)
}

/// `tinyexpression check`: `{"ok":true}`.
///
/// # Safety
/// As [`te_parse`].
#[no_mangle]
pub unsafe extern "C" fn te_check(source: *const u8, len: usize, out: *mut *mut c_char) -> i32 {
    call(source, len, out, api::check_json)
}

/// `tinyexpression eval`: `{"ok":true,"value":...}`.
///
/// # Safety
/// As [`te_parse`].
#[no_mangle]
pub unsafe extern "C" fn te_eval(source: *const u8, len: usize, out: *mut *mut c_char) -> i32 {
    call(source, len, out, api::eval_json)
}

/// `tinyexpression load` (`run == 0`) / `run` (`run != 0`) on a FormulaInfo document;
/// `random()` is seeded with `seed`.
///
/// # Safety
/// As [`te_parse`].
#[no_mangle]
pub unsafe extern "C" fn te_formula_info(
    source: *const u8,
    len: usize,
    run: i32,
    seed: u64,
    out: *mut *mut c_char,
) -> i32 {
    call(source, len, out, |text| {
        api::formula_info_json(text, &LoaderOptions::java_tests(), run != 0, seed)
    })
}

/// `tinyexpression eval-context` (issue #201): `source` is a JSON request carrying the formula,
/// the calculator settings, the `CalculationContext` variables and stubbed externals.
///
/// # Safety
/// As [`te_parse`].
#[no_mangle]
pub unsafe extern "C" fn te_eval_context(
    source: *const u8,
    len: usize,
    out: *mut *mut c_char,
) -> i32 {
    call(source, len, out, api::eval_context_json)
}

/// `te_eval_trace` (issue #201, stage 3): `te_eval_context` plus the tree walker's evaluation
/// trace, `{...,"trace":{"steps","recorded","truncated","root"}}` (CLI `eval-context --trace`).
///
/// # Safety
/// As [`te_parse`].
#[no_mangle]
pub unsafe extern "C" fn te_eval_trace(
    source: *const u8,
    len: usize,
    out: *mut *mut c_char,
) -> i32 {
    call(source, len, out, api::eval_trace_json)
}

/// `tinyexpression run-context` (issue #201): `run` on the request's FormulaInfo `document`
/// with the request's context and externals.
///
/// # Safety
/// As [`te_parse`].
#[no_mangle]
pub unsafe extern "C" fn te_formula_info_context(
    source: *const u8,
    len: usize,
    out: *mut *mut c_char,
) -> i32 {
    call(source, len, out, |text| {
        api::formula_info_context_json(text, &LoaderOptions::java_tests())
    })
}

/// `{"name":"tinyexpression","version":"2.0.0","ubnfc":"<commit>","abi":1}`, released with
/// [`te_free`].
#[no_mangle]
pub extern "C" fn te_version() -> *mut c_char {
    let json = api::version_json();
    let json = format!("{},\"abi\":{TE_ABI_VERSION}}}", &json[..json.len() - 1]);
    CString::new(json).expect("no NUL").into_raw()
}

/// Releases a JSON document returned by this library. Null is ignored.
///
/// # Safety
/// `json` is null or a pointer this library returned and has not been freed.
#[no_mangle]
pub unsafe extern "C" fn te_free(json: *mut c_char) {
    if !json.is_null() {
        // SAFETY: produced by CString::into_raw in this library.
        drop(unsafe { CString::from_raw(json) });
    }
}

/// wasm hosts write the source into linear memory: `te_alloc(len)` returns `len` writable
/// bytes, released with `te_dealloc(ptr, len)`. (Available natively too; C callers can pass
/// their own buffers instead.)
#[no_mangle]
pub extern "C" fn te_alloc(len: usize) -> *mut u8 {
    let mut buffer = Vec::<u8>::with_capacity(len.max(1));
    let pointer = buffer.as_mut_ptr();
    std::mem::forget(buffer);
    pointer
}

/// Releases a buffer from [`te_alloc`].
///
/// # Safety
/// `pointer` came from `te_alloc(len)` with the same `len` and has not been released.
#[no_mangle]
pub unsafe extern "C" fn te_dealloc(pointer: *mut u8, len: usize) {
    if !pointer.is_null() {
        // SAFETY: rebuilds the Vec te_alloc forgot, with the same capacity.
        drop(unsafe { Vec::from_raw_parts(pointer, 0, len.max(1)) });
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::ffi::CStr;

    fn eval(source: &str) -> (i32, String) {
        let mut out = ptr::null_mut();
        let code = unsafe { te_eval(source.as_ptr(), source.len(), &mut out) };
        let json = unsafe { CStr::from_ptr(out) }.to_str().unwrap().to_owned();
        unsafe { te_free(out) };
        (code, json)
    }

    #[test]
    fn eval_matches_the_cli_contract() {
        assert_eq!(eval("1 + 2").0, 0);
        assert!(eval("1 + 2")
            .1
            .starts_with("{\"ok\":true,\"value\":{\"kind\":\"number\""));
        let (code, json) = eval("1 +");
        assert_eq!(code, 3);
        assert!(json.starts_with("{\"ok\":false,\"stage\":\"parse\""));
        assert_eq!(api::eval_json("(1+2)*3").json, eval("(1+2)*3").1);
    }

    #[test]
    fn eval_context_reads_variables_and_externals() {
        let request = r#"{"formula":"if($member){external returning as number sample.Fee#calculate($price)}else{$price}","variables":[{"name":"member","type":"boolean","value":true},{"name":"price","type":"float","value":"100"}],"externals":[{"class":"sample.Fee","method":"calculate","result":{"type":"float","value":"12.5"}}]}"#;
        let mut out = ptr::null_mut();
        let code = unsafe { te_eval_context(request.as_ptr(), request.len(), &mut out) };
        let json = unsafe { CStr::from_ptr(out) }.to_str().unwrap().to_owned();
        unsafe { te_free(out) };
        assert_eq!(code, 0, "{json}");
        assert_eq!(json, api::eval_context_json(request).json);
        assert!(json.contains("\"text\":\"12.5\""), "{json}");
    }

    #[test]
    fn eval_trace_matches_the_api() {
        let request =
            r#"{"formula":"$x * 2","variables":[{"name":"x","type":"float","value":"4"}]}"#;
        let mut out = ptr::null_mut();
        let code = unsafe { te_eval_trace(request.as_ptr(), request.len(), &mut out) };
        let json = unsafe { CStr::from_ptr(out) }.to_str().unwrap().to_owned();
        unsafe { te_free(out) };
        assert_eq!(code, 0, "{json}");
        assert_eq!(json, api::eval_trace_json(request).json);
        assert!(json.contains("\"text\":\"8.0\",\"trace\":{"), "{json}");
    }

    #[test]
    fn boundary_errors_use_cli_codes() {
        let mut out = ptr::null_mut();
        let bytes = [0xffu8, 0xfe];
        assert_eq!(unsafe { te_eval(bytes.as_ptr(), 2, &mut out) }, 6);
        unsafe { te_free(out) };
        assert_eq!(unsafe { te_eval(ptr::null(), 3, &mut out) }, 2);
        unsafe { te_free(out) };
        assert_eq!(unsafe { te_eval(ptr::null(), 0, ptr::null_mut()) }, 2);
    }

    #[test]
    fn version_reports_the_pin_and_abi() {
        let version = te_version();
        let json = unsafe { CStr::from_ptr(version) }
            .to_str()
            .unwrap()
            .to_owned();
        unsafe { te_free(version) };
        assert!(json.contains("\"version\":\"2.0.0\""));
        assert!(json.contains(api::UBNFC_COMMIT));
        assert!(json.ends_with(",\"abi\":1}"));
    }
}
