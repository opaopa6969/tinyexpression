//! 採用結果だけから構築する AST。数値 capture は字句 String。
#![allow(non_camel_case_types, non_snake_case)]
use super::api::Span;
#[derive(Clone,Debug,PartialEq)]
pub enum Ast { Text(String), Null,
g_FormulaInfoAST_2e_FormulaInfoDocument(g_FormulaInfoAST_2e_FormulaInfoDocument),
g_FormulaInfoAST_2e_FormulaInfoBlock(g_FormulaInfoAST_2e_FormulaInfoBlock),
g_FormulaInfoAST_2e_CommentLine(g_FormulaInfoAST_2e_CommentLine),
g_FormulaInfoAST_2e_BlankLine(g_FormulaInfoAST_2e_BlankLine),
g_FormulaInfoAST_2e_FormulaInfoEntry(g_FormulaInfoAST_2e_FormulaInfoEntry),
g_FormulaInfoAST_2e_FormulaInfoValue(g_FormulaInfoAST_2e_FormulaInfoValue),
g_FormulaInfoAST_2e_EndOfPart(g_FormulaInfoAST_2e_EndOfPart),
}
pub type g_FormulaInfoAST = Ast;
#[derive(Clone,Debug,PartialEq)] pub struct g_FormulaInfoAST_2e_FormulaInfoDocument { pub span:Span, pub node_id:usize,
pub g_blocks: Vec<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_FormulaInfoAST_2e_FormulaInfoBlock { pub span:Span, pub node_id:usize,
pub g_leading: Vec<Ast>,
pub g_entries: Vec<Ast>,
pub g_end: Option<Box<Ast>>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_FormulaInfoAST_2e_CommentLine { pub span:Span, pub node_id:usize,
pub g_text: String,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_FormulaInfoAST_2e_BlankLine { pub span:Span, pub node_id:usize,
pub g_text: String,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_FormulaInfoAST_2e_FormulaInfoEntry { pub span:Span, pub node_id:usize,
pub g_key: String,
pub g_value: Box<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_FormulaInfoAST_2e_FormulaInfoValue { pub span:Span, pub node_id:usize,
pub g_text: String,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_FormulaInfoAST_2e_EndOfPart { pub span:Span, pub node_id:usize,
pub g_mark: String,
}
impl Ast { pub fn span(&self)->Option<Span> { match self {Self::Text(_)|Self::Null=>None,
Self::g_FormulaInfoAST_2e_FormulaInfoDocument(node)=>Some(node.span),
Self::g_FormulaInfoAST_2e_FormulaInfoBlock(node)=>Some(node.span),
Self::g_FormulaInfoAST_2e_CommentLine(node)=>Some(node.span),
Self::g_FormulaInfoAST_2e_BlankLine(node)=>Some(node.span),
Self::g_FormulaInfoAST_2e_FormulaInfoEntry(node)=>Some(node.span),
Self::g_FormulaInfoAST_2e_FormulaInfoValue(node)=>Some(node.span),
Self::g_FormulaInfoAST_2e_EndOfPart(node)=>Some(node.span),
}}
pub fn type_name(&self)->&'static str { match self {Self::Text(_)=>"text",Self::Null=>"null",
Self::g_FormulaInfoAST_2e_FormulaInfoDocument(_)=>"FormulaInfoDocument",
Self::g_FormulaInfoAST_2e_FormulaInfoBlock(_)=>"FormulaInfoBlock",
Self::g_FormulaInfoAST_2e_CommentLine(_)=>"CommentLine",
Self::g_FormulaInfoAST_2e_BlankLine(_)=>"BlankLine",
Self::g_FormulaInfoAST_2e_FormulaInfoEntry(_)=>"FormulaInfoEntry",
Self::g_FormulaInfoAST_2e_FormulaInfoValue(_)=>"FormulaInfoValue",
Self::g_FormulaInfoAST_2e_EndOfPart(_)=>"EndOfPart",
}}
pub fn type_id(&self)->&'static str {match self {Self::Text(_)=>"text",Self::Null=>"null",
Self::g_FormulaInfoAST_2e_FormulaInfoDocument(_)=>"FormulaInfoAST.FormulaInfoDocument",
Self::g_FormulaInfoAST_2e_FormulaInfoBlock(_)=>"FormulaInfoAST.FormulaInfoBlock",
Self::g_FormulaInfoAST_2e_CommentLine(_)=>"FormulaInfoAST.CommentLine",
Self::g_FormulaInfoAST_2e_BlankLine(_)=>"FormulaInfoAST.BlankLine",
Self::g_FormulaInfoAST_2e_FormulaInfoEntry(_)=>"FormulaInfoAST.FormulaInfoEntry",
Self::g_FormulaInfoAST_2e_FormulaInfoValue(_)=>"FormulaInfoAST.FormulaInfoValue",
Self::g_FormulaInfoAST_2e_EndOfPart(_)=>"FormulaInfoAST.EndOfPart",
}}
#[cfg(feature="json")] pub fn canonical_value(&self)->serde_json::Value { match self { Self::Text(value)=>serde_json::Value::String(value.clone()),Self::Null=>serde_json::Value::Null,
Self::g_FormulaInfoAST_2e_FormulaInfoDocument(node)=>serde_json::json!({"fields":{
"blocks":node.g_blocks.iter().map(Ast::canonical_value).collect::<Vec<_>>(),
},"span":node.span,"type":"FormulaInfoDocument"}),
Self::g_FormulaInfoAST_2e_FormulaInfoBlock(node)=>serde_json::json!({"fields":{
"end":node.g_end.as_ref().map(|v|v.canonical_value()),
"entries":node.g_entries.iter().map(Ast::canonical_value).collect::<Vec<_>>(),
"leading":node.g_leading.iter().map(Ast::canonical_value).collect::<Vec<_>>(),
},"span":node.span,"type":"FormulaInfoBlock"}),
Self::g_FormulaInfoAST_2e_CommentLine(node)=>serde_json::json!({"fields":{
"text":node.g_text,
},"span":node.span,"type":"CommentLine"}),
Self::g_FormulaInfoAST_2e_BlankLine(node)=>serde_json::json!({"fields":{
"text":node.g_text,
},"span":node.span,"type":"BlankLine"}),
Self::g_FormulaInfoAST_2e_FormulaInfoEntry(node)=>serde_json::json!({"fields":{
"key":node.g_key,
"value":node.g_value.canonical_value(),
},"span":node.span,"type":"FormulaInfoEntry"}),
Self::g_FormulaInfoAST_2e_FormulaInfoValue(node)=>serde_json::json!({"fields":{
"text":node.g_text,
},"span":node.span,"type":"FormulaInfoValue"}),
Self::g_FormulaInfoAST_2e_EndOfPart(node)=>serde_json::json!({"fields":{
"mark":node.g_mark,
},"span":node.span,"type":"EndOfPart"}),
}}
#[cfg(feature="json")] pub fn canonical_json(&self)->String {serde_json::to_string(&self.canonical_value()).expect("finite canonical AST")}
}
#[cfg(feature="json")] impl serde::Serialize for Ast {fn serialize<S:serde::Serializer>(&self,serializer:S)->Result<S::Ok,S::Error> {self.canonical_value().serialize(serializer)}}
