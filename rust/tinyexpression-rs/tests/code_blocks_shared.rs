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
