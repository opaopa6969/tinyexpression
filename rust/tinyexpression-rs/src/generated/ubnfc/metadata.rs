//! Grammar metadata; offsets in catalog sites refer to capture IDs, not input positions.
#[derive(Clone,Copy,Debug,PartialEq,Eq)] pub struct Catalog {pub rule_id:&'static str,pub rule:&'static str,pub context:&'static str,pub captures:&'static [&'static str],pub site_ids:&'static [&'static str]}
pub const CATALOGS:&[Catalog]=&[
Catalog{rule_id:"TinyExpressionP4::VariableRef",rule:"VariableRef",context:"variable",captures:&["name", "type"],site_ids:&["expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24337:24347:body/1/tokenRef/capture/0", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24365:24376:body/2/0/1/ruleRef/capture/0"]},
];
