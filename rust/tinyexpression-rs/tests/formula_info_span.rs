//! Where a FormulaInfo load error is (issue #212): `formula_info_span::load_error_span` and the
//! `"span"` of a `load` failure, in code points of the document. The playground's FormulaInfo
//! editor underlines this span.

use std::fs;
use std::path::PathBuf;

use tinyexpression_rs::api;
use tinyexpression_rs::formula_info::{self, LoaderOptions};
use tinyexpression_rs::formula_info_span::load_error_span;

/// The text `[start, end)` of the span of the error `load` returns for `source`.
fn located(source: &str) -> (String, usize, usize) {
    let options = LoaderOptions::java_tests();
    let error = match formula_info::load(source, &options) {
        Ok(_) => panic!("loads: {source}"),
        Err(error) => error,
    };
    let span = load_error_span(source, &options, &error).expect("a span");
    assert!(span.start <= span.end, "{span:?}");
    let chars: Vec<char> = source.chars().collect();
    assert!(span.end <= chars.len(), "{span:?} beyond {}", chars.len());
    (
        chars[span.start..span.end].iter().collect(),
        span.start,
        span.end,
    )
}

#[test]
fn value_errors_point_at_the_value() {
    let doc = "calculatorName:a\nformula:\n1\n---END_OF_PART---\ncalculatorName:b\nexecutionBackend: P4_MAGIC \nformula:\n2\n";
    assert_eq!(located(doc).0, " P4_MAGIC");
    let doc = "calculatorName:a\nresultType:Double\nformula:\n1\n";
    assert_eq!(located(doc).0, "Double");
    let doc = "calculatorName:a\nbyteCode:0g\nformula:\n1\n";
    assert_eq!(located(doc).0, "0g");
}

#[test]
fn formula_parse_errors_map_through_dropped_comment_lines() {
    // The loader drops the `#` line and the blank line: the formula text is "1 +\n(2" and the
    // parser fails at its end; the span is the same place in the document.
    let doc = "calculatorName:ok\nformula:\n1\n---END_OF_PART---\n\
               calculatorName:broken\nformula:\n# note\n\n1 +\n(2\n---END_OF_PART---\n";
    let (text, start, _) = located(doc);
    assert_eq!(text, "");
    let before: String = doc.chars().take(start).collect();
    assert!(before.ends_with("1 +\n(2"), "{before:?}");
}

#[test]
fn unknown_depends_on_points_at_the_name() {
    let doc = "calculatorName:a\nformula:\n1\n---END_OF_PART---\n\
               calculatorName:b\ndependsOn:a,nowhere\nformula:\n2\n";
    assert_eq!(located(doc).0, "nowhere");
}

#[test]
fn block_errors_point_at_the_block() {
    let doc = "calculatorName:a\nformula:\n1\n---END_OF_PART---\n# lead\ncalculatorName:b\ntags:x\n---END_OF_PART---\n";
    // No formula: the first entry line of the failing block.
    assert_eq!(located(doc).0, "calculatorName:");
    let (text, start, _) = located("calculatorName:a\nformula:");
    assert_eq!((text.as_str(), start), ("formula:", 17));
}

#[test]
fn duplicate_calculator_name_points_at_the_second_one() {
    // Issue #211: the end mark line between two FormulaInfo is missing.
    let doc = "calculatorName:a\nformula:\n1\ncalculatorName:b\nformula:\n2\n---END_OF_PART---\n";
    let (text, start, _) = located(doc);
    assert_eq!((text.as_str(), start), ("calculatorName:", 28));
}

#[test]
fn spans_count_code_points() {
    // U+1F600 is two UTF-16 units and four UTF-8 bytes, one code point.
    let doc = "description:😀😀\ncalculatorName:a\nresultType:Nope\nformula:\n1\n";
    let (text, start, _) = located(doc);
    assert_eq!(text, "Nope");
    assert_eq!(
        start,
        "description:😀😀\ncalculatorName:a\nresultType:"
            .chars()
            .count()
    );
}

#[test]
fn syntax_errors_are_the_diagnostic_offset() {
    let doc = "calculatorName:x\nformula:\n1\n---END_OF_PART---\ngarbage\n";
    let (_, start, end) = located(doc);
    assert_eq!((start, end), (53, 53));
}

#[test]
fn every_rejected_fixture_has_a_span_in_the_json() {
    let dir =
        PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("../../src/test/resources/formulaInfo-ubnf");
    let mut seen = 0;
    for entry in fs::read_dir(&dir).expect("fixtures") {
        let path = entry.expect("entry").path();
        let name = path.file_name().unwrap().to_string_lossy().into_owned();
        if !name.starts_with("reject-") {
            continue;
        }
        let source = fs::read_to_string(&path).expect("read");
        let response = api::formula_info_json(&source, &LoaderOptions::java_tests(), false, 1);
        let json = response.json;
        assert!(json.contains("\"stage\":\"load\""), "{name}: {json}");
        let at = json
            .find(",\"span\":[")
            .unwrap_or_else(|| panic!("{name}: no span: {json}"));
        let numbers: Vec<usize> = json[at + 9..]
            .split(']')
            .next()
            .unwrap()
            .split(',')
            .map(|n| n.parse().unwrap())
            .collect();
        assert!(
            numbers[0] <= numbers[1] && numbers[1] <= source.chars().count(),
            "{name}: {numbers:?}"
        );
        seen += 1;
    }
    assert!(seen >= 10, "{seen} reject fixtures");
}
