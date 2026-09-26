//! `eval-context` / `run-context` (issue #201): evaluation with a caller-supplied
//! `CalculationContext` and stubbed externals, the path the playground and the wasm export use.

use tinyexpression_rs::api::{eval_context_json, formula_info_context_json};
use tinyexpression_rs::formula_info::LoaderOptions;

fn eval(request: &str) -> (u8, String) {
    let response = eval_context_json(request);
    (response.exit_code, response.json)
}

#[test]
fn variables_of_every_map_are_visible() {
    let (code, json) = eval(
        r#"{"formula":"if($member & $name == 'alice'){$price * 2}else{0}",
            "variables":[{"name":"member","type":"boolean","value":true},
                         {"name":"name","type":"string","value":"alice"},
                         {"name":"price","type":"float","value":"1.5"}]}"#,
    );
    assert_eq!(code, 0, "{json}");
    assert_eq!(
        json,
        r#"{"ok":true,"value":{"kind":"number","value":"3","f32Bits":"0x40400000"},"text":"3.0"}"#
    );
}

#[test]
fn result_and_number_types_apply() {
    let (code, json) = eval(r#"{"formula":"7 / 2","resultType":"int","numberType":"int"}"#);
    assert_eq!(code, 0, "{json}");
    assert!(json.contains(r#""kind":"int","value":3"#), "{json}");
    let (_, json) = eval(
        r#"{"formula":"$s + '!'","resultType":"string","variables":[{"name":"s","type":"string","value":"hi"}]}"#,
    );
    assert!(json.contains(r#""text":"hi!""#), "{json}");
}

#[test]
fn now_hour_drives_in_time_range() {
    let request = |hour: &str| {
        format!(
            r#"{{"formula":"if(inTimeRange(9, 17)){{1}}else{{0}}","variables":[{{"name":"nowHour","type":"float","value":"{hour}"}}]}}"#
        )
    };
    assert!(eval(&request("10")).1.contains(r#""text":"1.0""#));
    assert!(eval(&request("20")).1.contains(r#""text":"0.0""#));
}

#[test]
fn externals_follow_the_java_reflection_outcomes() {
    let formula = "external returning as number sample.Fee#calculate(1)";
    let with = |externals: &str| {
        eval(&format!(
            r#"{{"formula":"{formula}","externals":{externals}}}"#
        ))
    };
    // Class not loadable: Class.forName fails.
    let (code, json) = with("[]");
    assert_eq!(code, 5);
    assert!(
        json.contains(r#""stage":"apply""#) && json.contains("UnsupportedOperationException"),
        "{json}"
    );
    // Loadable but no instance registered in the context.
    let (_, json) = with(r#"[{"class":"sample.Fee","method":"calculate","registered":false}]"#);
    assert!(json.contains("CalculationException"), "{json}");
    // Wrong arity: method not found.
    let (_, json) = with(
        r#"[{"class":"sample.Fee","method":"calculate","arity":2,"result":{"type":"float","value":"1"}}]"#,
    );
    assert!(json.contains("UnsupportedOperationException"), "{json}");
    let (code, json) = with(
        r#"[{"class":"sample.Fee","method":"calculate","arity":1,"result":{"type":"float","value":"42"}}]"#,
    );
    assert_eq!(code, 0, "{json}");
    assert!(json.contains(r#""text":"42.0""#), "{json}");
}

#[test]
fn failures_report_the_stage() {
    let (code, json) = eval(r#"{"formula":"1 +"}"#);
    assert_eq!(code, 3);
    assert!(
        json.starts_with(r#"{"ok":false,"stage":"create","error":{"kind":"ParseException""#),
        "{json}"
    );
    assert!(json.contains(r#""diagnostic":{"kind":"syntax""#), "{json}");
    let (code, json) = eval(r#"{"formula":"1 / 0","resultType":"int","numberType":"int"}"#);
    assert_eq!(code, 5, "{json}");
    assert!(
        json.contains(r#""stage":"apply""#) && json.contains("ArithmeticException"),
        "{json}"
    );
    let (code, json) = eval(r#"{"formula":1}"#);
    assert_eq!(code, 2);
    assert!(json.contains(r#""stage":"request""#), "{json}");
    let (code, _) =
        eval(r#"{"formula":"1","variables":[{"name":"x","type":"float","value":"abc"}]}"#);
    assert_eq!(code, 2);
}

#[test]
fn run_context_evaluates_formula_info_with_the_context() {
    let document = "calculatorName:base\\nformula:\\n$price + 1\\n---END_OF_PART---\\n";
    let request = format!(
        r#"{{"document":"{document}","variables":[{{"name":"price","type":"float","value":"2"}}]}}"#
    );
    let response = formula_info_context_json(&request, &LoaderOptions::java_tests());
    assert_eq!(response.exit_code, 0, "{}", response.json);
    assert!(
        response
            .json
            .contains(r#""value":{"kind":"number","value":"3""#),
        "{}",
        response.json
    );
}

// ------------------------------------------------------------------ java code blocks (#216)

const CODE_BLOCK_FORMULA: &str = "```java:CheckDigits\\npublic class CheckDigits{\\n  public boolean check(CalculationContext c, String target){ return target.matches(\\\"\\\\\\\\d+\\\"); }\\n}\\n```\\nimport CheckDigits#check as checkDigits;\\nvar $input as string set if not exists 'not number';\\nif(external returning as boolean checkDigits($input)){1}else{0}";

const MISSING_STUB_HINT: &str = "コードブロックのクラスは externals で値を指定してください";

#[test]
fn code_block_classes_resolve_through_the_externals_stubs() {
    // The block only declares the class; the call is answered by the stub (not by running the
    // Java code, which would say `false` for 'not number').
    let request = |value: bool| {
        format!(
            r#"{{"formula":"{CODE_BLOCK_FORMULA}","externals":[{{"class":"CheckDigits","method":"check","arity":1,"result":{{"type":"boolean","value":{value}}}}}]}}"#
        )
    };
    let (code, json) = eval(&request(true));
    assert_eq!(code, 0, "{json}");
    assert!(json.contains(r#""text":"1.0""#), "{json}");
    let (code, json) = eval(&request(false));
    assert_eq!(code, 0, "{json}");
    assert!(json.contains(r#""text":"0.0""#), "{json}");
}

#[test]
fn code_block_classes_without_a_stub_fail_like_class_for_name_with_a_hint() {
    let (code, json) = eval(&format!(r#"{{"formula":"{CODE_BLOCK_FORMULA}"}}"#));
    assert_eq!(code, 5, "{json}");
    assert!(
        json.contains(r#""kind":"UnsupportedOperationException","message":"External invocation failed: CheckDigits#check (the class is declared by a ```java:CheckDigits code block"#),
        "{json}"
    );
    assert!(json.contains(MISSING_STUB_HINT), "{json}");
    // A stub of another class does not load this one.
    let (code, json) = eval(&format!(
        r#"{{"formula":"{CODE_BLOCK_FORMULA}","externals":[{{"class":"Other","method":"check"}}]}}"#
    ));
    assert_eq!(code, 5, "{json}");
    assert!(json.contains(MISSING_STUB_HINT), "{json}");
    // A class no code block declares keeps the plain Java message.
    let (code, json) = eval(
        r#"{"formula":"import sample.Fee#calculate as fee;\nexternal returning as number fee(1)"}"#,
    );
    assert_eq!(code, 5, "{json}");
    assert!(
        json.contains(r#""message":"External invocation failed: sample.Fee#calculate"}"#),
        "{json}"
    );
}

fn json_escape(text: &str) -> String {
    let mut out = String::new();
    for c in text.chars() {
        match c {
            '"' => out.push_str("\\\""),
            '\\' => out.push_str("\\\\"),
            '\n' => out.push_str("\\n"),
            '\r' => out.push_str("\\r"),
            '\t' => out.push_str("\\t"),
            c if (c as u32) < 0x20 => out.push_str(&format!("\\u{:04x}", c as u32)),
            c => out.push(c),
        }
    }
    out
}

/// The acceptance example of issue #216: every block of `formulaInfo-test/69` evaluates once
/// its two code-block classes have stub values; without them, only those two blocks fail.
#[test]
fn formula_info_with_code_blocks_runs_with_stub_values() {
    let document = json_escape(include_str!(
        "../../../src/test/resources/formulaInfo-test/69/formulaInfo.txt"
    ));
    let stubs = r#"[{"class":"CheckDigits","method":"check","arity":1,"result":{"type":"boolean","value":true}},
                    {"class":"sample.v1.CheckAlphabets","method":"check","arity":1,"result":{"type":"boolean","value":false}}]"#;
    let response = formula_info_context_json(
        &format!(r#"{{"document":"{document}","externals":{stubs}}}"#),
        &LoaderOptions::java_tests(),
    );
    assert_eq!(response.exit_code, 0, "{}", response.json);
    assert!(!response.json.contains(r#""error""#), "{}", response.json);

    let response = formula_info_context_json(
        &format!(r#"{{"document":"{document}"}}"#),
        &LoaderOptions::java_tests(),
    );
    assert_eq!(response.exit_code, 5, "{}", response.json);
    assert_eq!(
        response.json.matches(MISSING_STUB_HINT).count(),
        2,
        "{}",
        response.json
    );
}
