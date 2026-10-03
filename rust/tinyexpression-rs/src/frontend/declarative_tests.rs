//! Cross-language input/position/value contract; no legacy token registry is registered.
use crate::generated::{
    ast::{Ast, AstValue},
    compat, ubnfc,
};

#[test]
fn shared_lexical_cases_need_no_host_parser_mapping() {
    use crate::request::{parse_json, Json};
    let Json::Arr(rows) = parse_json(include_str!(
        "../../../../src/test/resources/p4-lexical-conformance.json"
    ))
    .unwrap() else {
        panic!("array");
    };
    for row in rows {
        let id = row.str_field("id").unwrap();
        let input = row.str_field("input").unwrap();
        let Some(Json::Bool(ok)) = row.get("ok") else {
            panic!("{id}")
        };
        let ok = *ok;
        let end = row
            .get("prefix")
            .map(|v| {
                if let Json::Num(text) = v {
                    text.parse::<usize>().unwrap()
                } else {
                    panic!("{id}")
                }
            })
            .unwrap_or(if ok { input.chars().count() } else { 0 });
        let result = ubnfc::parse_entry_with_options(
            "TinyExpressionP4",
            row.str_field("entry"),
            input,
            ubnfc::ParseOptions {
                require_eof: false,
                ..Default::default()
            },
        )
        .unwrap();
        assert_eq!(
            (result.ok, result.consumed_cp, result.matched_cp),
            (ok, end, end),
            "{id}"
        );
        if let Some(expected) = row.str_field("value") {
            let raw = result.ast.unwrap();
            let ubnfc::Ast::g_TinyExpressionP4AST_2e_StringConcatExpr(node) = &raw else {
                panic!("{id}")
            };
            assert_eq!(
                node.g_left.as_ref(),
                &ubnfc::Ast::Text(input.to_owned()),
                "{id}"
            );
            let Ast::StringConcatExpr {
                span,
                left: AstValue::Text { text, .. },
                ..
            } = compat::convert(&raw).unwrap()
            else {
                panic!("{id}")
            };
            assert_eq!(text, expected, "{id}");
            assert_eq!((span.start, span.end), (0, end), "{id}");
        }
    }
}
