use tinyexpression_rs::code_blocks::{self, Target};

fn unescape(text: &str) -> String {
    let mut out = String::new();
    let mut chars = text.chars();
    while let Some(c) = chars.next() {
        out.push(if c == '\\' {
            match chars.next().unwrap() {
                'n' => '\n',
                'r' => '\r',
                't' => '\t',
                '\\' => '\\',
                _ => panic!("invalid fixture escape"),
            }
        } else {
            c
        });
    }
    out
}

#[test]
fn shared_source_corpus() {
    for row in include_str!("../../../src/test/resources/code-block-source.tsv")
        .lines()
        .filter(|r| !r.starts_with('#'))
    {
        let f: Vec<_> = row.split('\t').collect();
        let source = unescape(f[1]);
        let parsed = code_blocks::parse(&source);
        if f[2] == "invalid" {
            assert!(parsed.is_err(), "{}", f[0]);
            continue;
        }
        let blocks = parsed.unwrap_or_else(|e| panic!("{}: {e}", f[0]));
        let ast = tinyexpression_rs::parse(&source).unwrap();
        assert_eq!(blocks, code_blocks::from_ast(&ast).unwrap(), "{}", f[0]);
        if f[2] == "none" {
            assert!(blocks.is_empty(), "{}", f[0]);
            continue;
        }
        assert_eq!(blocks.len(), 1, "{}", f[0]);
        let b = &blocks[0];
        assert_eq!(b.scheme, f[3]);
        assert_eq!(b.identifier, f[4]);
        assert_eq!(b.body, unescape(f[5]));
        let values = [
            b.span.start,
            b.span.end,
            b.body_span.start,
            b.body_span.end,
            b.name_span.start,
            b.name_span.end,
        ];
        assert_eq!(
            values.to_vec(),
            f[6..]
                .iter()
                .map(|s| s.parse::<usize>().unwrap())
                .collect::<Vec<_>>(),
            "{}",
            f[0]
        );
    }
}

#[test]
fn extended_scanner_states_and_failures_are_atomic() {
    use tinyexpression_rs::generated::{
        scanners,
        ubnfc::{rt::scope::ScopeStore, State, TokenScanner},
    };
    for row in include_str!("../../../src/test/resources/code-block-source.tsv")
        .lines()
        .filter(|r| r.starts_with("long_"))
    {
        let f: Vec<_> = row.split('\t').collect();
        if f[2] == "none" {
            continue;
        }
        let source = unescape(f[1]);
        let valid = f[2] == "block";
        let byte = |cp: usize| {
            source
                .char_indices()
                .nth(cp)
                .map_or(source.len(), |(b, _)| b)
        };
        let start = if valid {
            byte(f[6].parse().unwrap())
        } else {
            0
        };
        let end = if valid {
            byte(f[7].parse().unwrap())
        } else {
            start
        };
        for match_only in [false, true] {
            for invert in [false, true] {
                for reset in [false, true] {
                    let state = State {
                        consumed: if match_only { 0 } else { start },
                        matched: if match_only { start } else { 0 },
                        invert,
                        reset,
                    };
                    let result = scanners::registry().scan(
                        "TinyExpressionP4::LONG_CODE_BLOCK",
                        &source,
                        state,
                        match_only,
                        &ScopeStore::new(),
                    );
                    let ok = valid && !invert;
                    assert_eq!(result.ok, ok, "{}", f[0]);
                    assert_eq!(
                        result.consumed_end,
                        if ok && !match_only {
                            end
                        } else {
                            state.consumed
                        }
                    );
                    assert_eq!(result.matched_end, if ok { end } else { state.matched });
                    assert!(result.effects.is_empty());
                    if !ok {
                        assert_eq!(result.diagnostics.len(), 1);
                        assert_eq!(result.diagnostics[0].offset, start);
                        assert_eq!(result.diagnostics[0].expected, "long code block");
                    }
                }
            }
        }
    }
}

#[test]
fn shared_preflight_corpus() {
    for row in include_str!("../../../src/test/resources/code-block-preflight.tsv")
        .lines()
        .filter(|r| !r.starts_with('#'))
    {
        let f: Vec<_> = row.split('\t').collect();
        let blocks = code_blocks::parse(&unescape(f[1])).unwrap();
        let target = if f[2] == "rust" {
            Target::Rust
        } else {
            Target::Java
        };
        let errors = code_blocks::preflight(&blocks, target, f[3] == "true");
        let actual = errors
            .iter()
            .map(|d| format!("{}:{}:{}", d.code, d.span.start, d.span.end))
            .collect::<Vec<_>>()
            .join(";");
        assert_eq!(
            if errors.is_empty() { "ok" } else { &actual },
            f[4],
            "{}",
            f[0]
        );
    }
}
