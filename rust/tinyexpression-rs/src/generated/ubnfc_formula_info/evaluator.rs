//! Typed evaluator dispatch. Rust numeric captures remain lexemes.
#![allow(non_snake_case,unused_imports)]
use super::ast::*;
#[derive(Clone,Debug,PartialEq)] pub enum Value {Text(String),Number(f64),Bool(bool),Node(Box<Ast>),Null}
#[derive(Clone,Debug,PartialEq,Eq)] pub struct EvalError(pub String);
impl std::fmt::Display for EvalError {fn fmt(&self,f:&mut std::fmt::Formatter<'_>)->std::fmt::Result {self.0.fmt(f)}}
impl std::error::Error for EvalError {}
pub type EvalResult=Result<Value,EvalError>;
pub trait Evaluator {
fn eval(&mut self,node:&Ast)->EvalResult {match node {Ast::Text(text)=>Ok(Value::Text(text.clone())),Ast::Null=>Ok(Value::Null),
Ast::g_FormulaInfoAST_2e_FormulaInfoDocument(node)=>self.eval_g_FormulaInfoAST_2e_FormulaInfoDocument(node),
Ast::g_FormulaInfoAST_2e_FormulaInfoBlock(node)=>self.eval_g_FormulaInfoAST_2e_FormulaInfoBlock(node),
Ast::g_FormulaInfoAST_2e_CommentLine(node)=>self.eval_g_FormulaInfoAST_2e_CommentLine(node),
Ast::g_FormulaInfoAST_2e_BlankLine(node)=>self.eval_g_FormulaInfoAST_2e_BlankLine(node),
Ast::g_FormulaInfoAST_2e_FormulaInfoEntry(node)=>self.eval_g_FormulaInfoAST_2e_FormulaInfoEntry(node),
Ast::g_FormulaInfoAST_2e_FormulaInfoValue(node)=>self.eval_g_FormulaInfoAST_2e_FormulaInfoValue(node),
Ast::g_FormulaInfoAST_2e_EndOfPart(node)=>self.eval_g_FormulaInfoAST_2e_EndOfPart(node),
}}
fn eval_g_FormulaInfoAST_2e_FormulaInfoDocument(&mut self,node:&g_FormulaInfoAST_2e_FormulaInfoDocument)->EvalResult;
fn eval_g_FormulaInfoAST_2e_FormulaInfoBlock(&mut self,node:&g_FormulaInfoAST_2e_FormulaInfoBlock)->EvalResult;
fn eval_g_FormulaInfoAST_2e_CommentLine(&mut self,node:&g_FormulaInfoAST_2e_CommentLine)->EvalResult;
fn eval_g_FormulaInfoAST_2e_BlankLine(&mut self,node:&g_FormulaInfoAST_2e_BlankLine)->EvalResult;
fn eval_g_FormulaInfoAST_2e_FormulaInfoEntry(&mut self,node:&g_FormulaInfoAST_2e_FormulaInfoEntry)->EvalResult;
fn eval_g_FormulaInfoAST_2e_FormulaInfoValue(&mut self,node:&g_FormulaInfoAST_2e_FormulaInfoValue)->EvalResult;
fn eval_g_FormulaInfoAST_2e_EndOfPart(&mut self,node:&g_FormulaInfoAST_2e_EndOfPart)->EvalResult;
}
/// Automatic kinds work without overrides; semantic callbacks report a missing implementation.
pub struct DefaultEvaluator;
impl Evaluator for DefaultEvaluator {
fn eval_g_FormulaInfoAST_2e_BlankLine(&mut self,node:&g_FormulaInfoAST_2e_BlankLine)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_FormulaInfoAST_2e_BlankLine".into()))}
fn eval_g_FormulaInfoAST_2e_CommentLine(&mut self,node:&g_FormulaInfoAST_2e_CommentLine)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_FormulaInfoAST_2e_CommentLine".into()))}
fn eval_g_FormulaInfoAST_2e_EndOfPart(&mut self,node:&g_FormulaInfoAST_2e_EndOfPart)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_FormulaInfoAST_2e_EndOfPart".into()))}
fn eval_g_FormulaInfoAST_2e_FormulaInfoBlock(&mut self,node:&g_FormulaInfoAST_2e_FormulaInfoBlock)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_FormulaInfoAST_2e_FormulaInfoBlock".into()))}
fn eval_g_FormulaInfoAST_2e_FormulaInfoDocument(&mut self,node:&g_FormulaInfoAST_2e_FormulaInfoDocument)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_FormulaInfoAST_2e_FormulaInfoDocument".into()))}
fn eval_g_FormulaInfoAST_2e_FormulaInfoEntry(&mut self,node:&g_FormulaInfoAST_2e_FormulaInfoEntry)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_FormulaInfoAST_2e_FormulaInfoEntry".into()))}
fn eval_g_FormulaInfoAST_2e_FormulaInfoValue(&mut self,node:&g_FormulaInfoAST_2e_FormulaInfoValue)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_FormulaInfoAST_2e_FormulaInfoValue".into()))}
}
pub fn evaluate<E:Evaluator+?Sized>(node:&Ast,evaluator:&mut E)->EvalResult {evaluator.eval(node)}
