use super::*;
fn scan(
    class: &'static str,
    input: &str,
    consumed: usize,
    matched: usize,
    match_only: bool,
    invert: bool,
    reset: bool,
) -> ScanResult {
    let scan = Scan {
        input,
        state: State {
            consumed,
            matched,
            invert,
            reset,
        },
        match_only,
        p: if match_only { matched } else { consumed },
        value_start: 0,
        value_end: 0,
        failure: 0,
        expected: "token",
    };
    // 実 registry と同じクラス解決。未知クラスは登録時に拒否する。
    scan.run(kind(class).unwrap())
}
fn result(r: ScanResult, ok: bool, consumed: usize, matched: usize, value: [usize; 2]) {
    assert_eq!(
        (r.ok, r.consumed_end, r.matched_end),
        (ok, consumed, matched),
        "{r:?}"
    );
    assert!(r.effects.is_empty());
    if ok {
        assert_eq!(r.value_span, value);
    } else {
        assert!(!r.diagnostics.is_empty());
    }
}
#[test]
fn external_failure_keeps_following_match_only_expectation() {
    let external = "oracle.ExternalCursorParser";
    for match_only in [false, true] {
        for (input, expected) in [
            ("a", vec![(0, "'😀'"), (0, "'x'")]),
            ("", vec![(0, "'😀'"), (0, "'x'")]),
            ("😀y", vec![(4, "'x'")]),
        ] {
            let r = scan(external, input, 0, 0, match_only, false, true);
            assert_eq!(
                r.diagnostics
                    .iter()
                    .map(|d| (d.offset, d.expected))
                    .collect::<Vec<_>>(),
                expected
            );
            result(r, false, 0, 0, [0, 0]);
        }
        assert!(scan(external, "😀x", 0, 0, match_only, false, true)
            .diagnostics
            .is_empty());
    }
}
#[test]
fn five_states_unicode_spans_and_transactional_failure() {
    let string = "org.unlaxer.tinyexpression.parser.StringLiteralParser";
    let start = "org.unlaxer.tinyexpression.parser.javalang.CodeStartParser";
    let end = "org.unlaxer.tinyexpression.parser.javalang.CodeEndParser";
    let external = "oracle.ExternalCursorParser";
    result(
        scan(string, "'😀'", 0, 0, false, false, true),
        true,
        6,
        6,
        [1, 5],
    );
    result(
        scan("DoubleQuotedParser", "\"\\😀\"", 0, 0, true, false, false),
        true,
        0,
        7,
        [0, 7],
    );
    result(
        scan(string, "''\"b\"", 0, 2, true, false, true),
        true,
        0,
        2,
        [1, 1],
    );
    result(
        scan(string, "''\"b\"", 0, 2, true, false, false),
        true,
        0,
        5,
        [2, 5],
    );
    result(
        scan(string, "'x", 0, 0, false, false, true),
        false,
        0,
        0,
        [0, 0],
    );
    result(
        scan(string, "'x'", 0, 0, false, true, true),
        false,
        0,
        0,
        [0, 0],
    );
    result(
        scan(external, "😀x", 0, 0, false, false, true),
        true,
        4,
        5,
        [0, 4],
    );
    result(
        scan(external, "😀x", 0, 0, true, false, false),
        true,
        0,
        5,
        [0, 4],
    );
    result(
        scan(external, "😀y", 0, 0, false, false, true),
        false,
        0,
        0,
        [0, 0],
    );
    result(
        scan(external, "ay", 0, 0, false, true, false),
        true,
        1,
        2,
        [0, 1],
    );
    result(
        scan(start, "x\r\n```java:a.B\r\n", 3, 3, false, false, true),
        true,
        16,
        16,
        [3, 14],
    );
    result(
        scan(end, "```\n", 0, 0, true, false, true),
        true,
        0,
        4,
        [0, 3],
    );
    for input in [" ```", "```x", "``", "````"] {
        result(
            scan(end, input, 0, 0, false, false, true),
            false,
            0,
            0,
            [0, 0],
        );
    }
    for input in [
        "```λ:a.B\n",
        "```java:a.$B\n",
        "```java:a..B\n",
        "```java:a.B.\n",
    ] {
        result(
            scan(start, input, 0, 0, false, false, true),
            false,
            0,
            0,
            [0, 0],
        );
    }
    assert!(Registry::new(&[("T", "other.StringLiteralParser")]).is_err());
}
