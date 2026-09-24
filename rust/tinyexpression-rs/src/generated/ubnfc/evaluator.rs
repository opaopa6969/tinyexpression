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
Ast::g_TinyExpressionP4AST_2e_FormulaExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_FormulaExpr(node),
Ast::g_TinyExpressionP4AST_2e_CodeBlockExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_CodeBlockExpr(node),
Ast::g_TinyExpressionP4AST_2e_ImportDeclarationExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_ImportDeclarationExpr(node),
Ast::g_TinyExpressionP4AST_2e_QualifiedNameExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_QualifiedNameExpr(node),
Ast::g_TinyExpressionP4AST_2e_NumberVariableDeclarationExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_NumberVariableDeclarationExpr(node),
Ast::g_TinyExpressionP4AST_2e_StringVariableDeclarationExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_StringVariableDeclarationExpr(node),
Ast::g_TinyExpressionP4AST_2e_BooleanVariableDeclarationExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_BooleanVariableDeclarationExpr(node),
Ast::g_TinyExpressionP4AST_2e_ObjectVariableDeclarationExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_ObjectVariableDeclarationExpr(node),
Ast::g_TinyExpressionP4AST_2e_OnlyIfAbsentExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_OnlyIfAbsentExpr(node),
Ast::g_TinyExpressionP4AST_2e_NumberMethodDeclarationExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_NumberMethodDeclarationExpr(node),
Ast::g_TinyExpressionP4AST_2e_StringMethodDeclarationExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_StringMethodDeclarationExpr(node),
Ast::g_TinyExpressionP4AST_2e_BooleanMethodDeclarationExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_BooleanMethodDeclarationExpr(node),
Ast::g_TinyExpressionP4AST_2e_ObjectMethodDeclarationExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_ObjectMethodDeclarationExpr(node),
Ast::g_TinyExpressionP4AST_2e_MethodParametersExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_MethodParametersExpr(node),
Ast::g_TinyExpressionP4AST_2e_MethodParameterExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_MethodParameterExpr(node),
Ast::g_TinyExpressionP4AST_2e_ExternalBooleanInvocationExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_ExternalBooleanInvocationExpr(node),
Ast::g_TinyExpressionP4AST_2e_ExternalNumberInvocationExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_ExternalNumberInvocationExpr(node),
Ast::g_TinyExpressionP4AST_2e_ExternalStringInvocationExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_ExternalStringInvocationExpr(node),
Ast::g_TinyExpressionP4AST_2e_ExternalObjectInvocationExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_ExternalObjectInvocationExpr(node),
Ast::g_TinyExpressionP4AST_2e_MethodInvocationExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_MethodInvocationExpr(node),
Ast::g_TinyExpressionP4AST_2e_TernaryExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_TernaryExpr(node),
Ast::g_TinyExpressionP4AST_2e_ArgumentExpressionExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_ArgumentExpressionExpr(node),
Ast::g_TinyExpressionP4AST_2e_ArgumentsExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_ArgumentsExpr(node),
Ast::g_TinyExpressionP4AST_2e_BinaryExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_BinaryExpr(node),
Ast::g_TinyExpressionP4AST_2e_SinExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_SinExpr(node),
Ast::g_TinyExpressionP4AST_2e_CosExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_CosExpr(node),
Ast::g_TinyExpressionP4AST_2e_TanExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_TanExpr(node),
Ast::g_TinyExpressionP4AST_2e_SqrtExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_SqrtExpr(node),
Ast::g_TinyExpressionP4AST_2e_MinExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_MinExpr(node),
Ast::g_TinyExpressionP4AST_2e_MaxExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_MaxExpr(node),
Ast::g_TinyExpressionP4AST_2e_RandomExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_RandomExpr(node),
Ast::g_TinyExpressionP4AST_2e_AbsExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_AbsExpr(node),
Ast::g_TinyExpressionP4AST_2e_RoundExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_RoundExpr(node),
Ast::g_TinyExpressionP4AST_2e_CeilExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_CeilExpr(node),
Ast::g_TinyExpressionP4AST_2e_FloorExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_FloorExpr(node),
Ast::g_TinyExpressionP4AST_2e_PowExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_PowExpr(node),
Ast::g_TinyExpressionP4AST_2e_LogExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_LogExpr(node),
Ast::g_TinyExpressionP4AST_2e_ExpExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_ExpExpr(node),
Ast::g_TinyExpressionP4AST_2e_ToNumExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_ToNumExpr(node),
Ast::g_TinyExpressionP4AST_2e_ToUpperCaseExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_ToUpperCaseExpr(node),
Ast::g_TinyExpressionP4AST_2e_ToLowerCaseExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_ToLowerCaseExpr(node),
Ast::g_TinyExpressionP4AST_2e_TrimExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_TrimExpr(node),
Ast::g_TinyExpressionP4AST_2e_LengthExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_LengthExpr(node),
Ast::g_TinyExpressionP4AST_2e_ToUpperCaseDotExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_ToUpperCaseDotExpr(node),
Ast::g_TinyExpressionP4AST_2e_ToLowerCaseDotExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_ToLowerCaseDotExpr(node),
Ast::g_TinyExpressionP4AST_2e_TrimDotExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_TrimDotExpr(node),
Ast::g_TinyExpressionP4AST_2e_LengthDotExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_LengthDotExpr(node),
Ast::g_TinyExpressionP4AST_2e_StartsWithExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_StartsWithExpr(node),
Ast::g_TinyExpressionP4AST_2e_EndsWithExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_EndsWithExpr(node),
Ast::g_TinyExpressionP4AST_2e_ContainsExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_ContainsExpr(node),
Ast::g_TinyExpressionP4AST_2e_InExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_InExpr(node),
Ast::g_TinyExpressionP4AST_2e_StartsWithDotExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_StartsWithDotExpr(node),
Ast::g_TinyExpressionP4AST_2e_EndsWithDotExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_EndsWithDotExpr(node),
Ast::g_TinyExpressionP4AST_2e_ContainsDotExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_ContainsDotExpr(node),
Ast::g_TinyExpressionP4AST_2e_IsPresentExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_IsPresentExpr(node),
Ast::g_TinyExpressionP4AST_2e_InTimeRangeExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_InTimeRangeExpr(node),
Ast::g_TinyExpressionP4AST_2e_InDayTimeRangeExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_InDayTimeRangeExpr(node),
Ast::g_TinyExpressionP4AST_2e_SliceExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_SliceExpr(node),
Ast::g_TinyExpressionP4AST_2e_StringConcatExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_StringConcatExpr(node),
Ast::g_TinyExpressionP4AST_2e_StringCastVariableRefExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_StringCastVariableRefExpr(node),
Ast::g_TinyExpressionP4AST_2e_StringTypedVariableRefExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_StringTypedVariableRefExpr(node),
Ast::g_TinyExpressionP4AST_2e_BooleanOrExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_BooleanOrExpr(node),
Ast::g_TinyExpressionP4AST_2e_BooleanAndExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_BooleanAndExpr(node),
Ast::g_TinyExpressionP4AST_2e_BooleanXorExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_BooleanXorExpr(node),
Ast::g_TinyExpressionP4AST_2e_NotExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_NotExpr(node),
Ast::g_TinyExpressionP4AST_2e_BooleanEqualityExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_BooleanEqualityExpr(node),
Ast::g_TinyExpressionP4AST_2e_BooleanFactorExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_BooleanFactorExpr(node),
Ast::g_TinyExpressionP4AST_2e_StringComparisonExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_StringComparisonExpr(node),
Ast::g_TinyExpressionP4AST_2e_ComparisonExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_ComparisonExpr(node),
Ast::g_TinyExpressionP4AST_2e_ObjectExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_ObjectExpr(node),
Ast::g_TinyExpressionP4AST_2e_IfExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_IfExpr(node),
Ast::g_TinyExpressionP4AST_2e_BranchExpressionExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_BranchExpressionExpr(node),
Ast::g_TinyExpressionP4AST_2e_NumberMatchExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_NumberMatchExpr(node),
Ast::g_TinyExpressionP4AST_2e_NumberCaseExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_NumberCaseExpr(node),
Ast::g_TinyExpressionP4AST_2e_NumberDefaultCaseExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_NumberDefaultCaseExpr(node),
Ast::g_TinyExpressionP4AST_2e_NumberCaseValueExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_NumberCaseValueExpr(node),
Ast::g_TinyExpressionP4AST_2e_StringMatchExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_StringMatchExpr(node),
Ast::g_TinyExpressionP4AST_2e_StringCaseExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_StringCaseExpr(node),
Ast::g_TinyExpressionP4AST_2e_StringDefaultCaseExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_StringDefaultCaseExpr(node),
Ast::g_TinyExpressionP4AST_2e_StringCaseValueExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_StringCaseValueExpr(node),
Ast::g_TinyExpressionP4AST_2e_BooleanMatchExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_BooleanMatchExpr(node),
Ast::g_TinyExpressionP4AST_2e_BooleanCaseExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_BooleanCaseExpr(node),
Ast::g_TinyExpressionP4AST_2e_BooleanDefaultCaseExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_BooleanDefaultCaseExpr(node),
Ast::g_TinyExpressionP4AST_2e_BooleanCaseValueExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_BooleanCaseValueExpr(node),
Ast::g_TinyExpressionP4AST_2e_VariableRefExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_VariableRefExpr(node),
Ast::g_TinyExpressionP4AST_2e_ExpressionExpr(node)=>self.eval_g_TinyExpressionP4AST_2e_ExpressionExpr(node),
}}
fn eval_g_TinyExpressionP4AST_2e_FormulaExpr(&mut self,node:&g_TinyExpressionP4AST_2e_FormulaExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_CodeBlockExpr(&mut self,node:&g_TinyExpressionP4AST_2e_CodeBlockExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_ImportDeclarationExpr(&mut self,node:&g_TinyExpressionP4AST_2e_ImportDeclarationExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_QualifiedNameExpr(&mut self,node:&g_TinyExpressionP4AST_2e_QualifiedNameExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_NumberVariableDeclarationExpr(&mut self,node:&g_TinyExpressionP4AST_2e_NumberVariableDeclarationExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_StringVariableDeclarationExpr(&mut self,node:&g_TinyExpressionP4AST_2e_StringVariableDeclarationExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_BooleanVariableDeclarationExpr(&mut self,node:&g_TinyExpressionP4AST_2e_BooleanVariableDeclarationExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_ObjectVariableDeclarationExpr(&mut self,node:&g_TinyExpressionP4AST_2e_ObjectVariableDeclarationExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_OnlyIfAbsentExpr(&mut self,node:&g_TinyExpressionP4AST_2e_OnlyIfAbsentExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_NumberMethodDeclarationExpr(&mut self,node:&g_TinyExpressionP4AST_2e_NumberMethodDeclarationExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_StringMethodDeclarationExpr(&mut self,node:&g_TinyExpressionP4AST_2e_StringMethodDeclarationExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_BooleanMethodDeclarationExpr(&mut self,node:&g_TinyExpressionP4AST_2e_BooleanMethodDeclarationExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_ObjectMethodDeclarationExpr(&mut self,node:&g_TinyExpressionP4AST_2e_ObjectMethodDeclarationExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_MethodParametersExpr(&mut self,node:&g_TinyExpressionP4AST_2e_MethodParametersExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_MethodParameterExpr(&mut self,node:&g_TinyExpressionP4AST_2e_MethodParameterExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_ExternalBooleanInvocationExpr(&mut self,node:&g_TinyExpressionP4AST_2e_ExternalBooleanInvocationExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_ExternalNumberInvocationExpr(&mut self,node:&g_TinyExpressionP4AST_2e_ExternalNumberInvocationExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_ExternalStringInvocationExpr(&mut self,node:&g_TinyExpressionP4AST_2e_ExternalStringInvocationExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_ExternalObjectInvocationExpr(&mut self,node:&g_TinyExpressionP4AST_2e_ExternalObjectInvocationExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_MethodInvocationExpr(&mut self,node:&g_TinyExpressionP4AST_2e_MethodInvocationExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_TernaryExpr(&mut self,node:&g_TinyExpressionP4AST_2e_TernaryExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_ArgumentExpressionExpr(&mut self,node:&g_TinyExpressionP4AST_2e_ArgumentExpressionExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_ArgumentsExpr(&mut self,node:&g_TinyExpressionP4AST_2e_ArgumentsExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_BinaryExpr(&mut self,node:&g_TinyExpressionP4AST_2e_BinaryExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_SinExpr(&mut self,node:&g_TinyExpressionP4AST_2e_SinExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_CosExpr(&mut self,node:&g_TinyExpressionP4AST_2e_CosExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_TanExpr(&mut self,node:&g_TinyExpressionP4AST_2e_TanExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_SqrtExpr(&mut self,node:&g_TinyExpressionP4AST_2e_SqrtExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_MinExpr(&mut self,node:&g_TinyExpressionP4AST_2e_MinExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_MaxExpr(&mut self,node:&g_TinyExpressionP4AST_2e_MaxExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_RandomExpr(&mut self,node:&g_TinyExpressionP4AST_2e_RandomExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_AbsExpr(&mut self,node:&g_TinyExpressionP4AST_2e_AbsExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_RoundExpr(&mut self,node:&g_TinyExpressionP4AST_2e_RoundExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_CeilExpr(&mut self,node:&g_TinyExpressionP4AST_2e_CeilExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_FloorExpr(&mut self,node:&g_TinyExpressionP4AST_2e_FloorExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_PowExpr(&mut self,node:&g_TinyExpressionP4AST_2e_PowExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_LogExpr(&mut self,node:&g_TinyExpressionP4AST_2e_LogExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_ExpExpr(&mut self,node:&g_TinyExpressionP4AST_2e_ExpExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_ToNumExpr(&mut self,node:&g_TinyExpressionP4AST_2e_ToNumExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_ToUpperCaseExpr(&mut self,node:&g_TinyExpressionP4AST_2e_ToUpperCaseExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_ToLowerCaseExpr(&mut self,node:&g_TinyExpressionP4AST_2e_ToLowerCaseExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_TrimExpr(&mut self,node:&g_TinyExpressionP4AST_2e_TrimExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_LengthExpr(&mut self,node:&g_TinyExpressionP4AST_2e_LengthExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_ToUpperCaseDotExpr(&mut self,node:&g_TinyExpressionP4AST_2e_ToUpperCaseDotExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_ToLowerCaseDotExpr(&mut self,node:&g_TinyExpressionP4AST_2e_ToLowerCaseDotExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_TrimDotExpr(&mut self,node:&g_TinyExpressionP4AST_2e_TrimDotExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_LengthDotExpr(&mut self,node:&g_TinyExpressionP4AST_2e_LengthDotExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_StartsWithExpr(&mut self,node:&g_TinyExpressionP4AST_2e_StartsWithExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_EndsWithExpr(&mut self,node:&g_TinyExpressionP4AST_2e_EndsWithExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_ContainsExpr(&mut self,node:&g_TinyExpressionP4AST_2e_ContainsExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_InExpr(&mut self,node:&g_TinyExpressionP4AST_2e_InExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_StartsWithDotExpr(&mut self,node:&g_TinyExpressionP4AST_2e_StartsWithDotExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_EndsWithDotExpr(&mut self,node:&g_TinyExpressionP4AST_2e_EndsWithDotExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_ContainsDotExpr(&mut self,node:&g_TinyExpressionP4AST_2e_ContainsDotExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_IsPresentExpr(&mut self,node:&g_TinyExpressionP4AST_2e_IsPresentExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_InTimeRangeExpr(&mut self,node:&g_TinyExpressionP4AST_2e_InTimeRangeExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_InDayTimeRangeExpr(&mut self,node:&g_TinyExpressionP4AST_2e_InDayTimeRangeExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_SliceExpr(&mut self,node:&g_TinyExpressionP4AST_2e_SliceExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_StringConcatExpr(&mut self,node:&g_TinyExpressionP4AST_2e_StringConcatExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_StringCastVariableRefExpr(&mut self,node:&g_TinyExpressionP4AST_2e_StringCastVariableRefExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_StringTypedVariableRefExpr(&mut self,node:&g_TinyExpressionP4AST_2e_StringTypedVariableRefExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_BooleanOrExpr(&mut self,node:&g_TinyExpressionP4AST_2e_BooleanOrExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_BooleanAndExpr(&mut self,node:&g_TinyExpressionP4AST_2e_BooleanAndExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_BooleanXorExpr(&mut self,node:&g_TinyExpressionP4AST_2e_BooleanXorExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_NotExpr(&mut self,node:&g_TinyExpressionP4AST_2e_NotExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_BooleanEqualityExpr(&mut self,node:&g_TinyExpressionP4AST_2e_BooleanEqualityExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_BooleanFactorExpr(&mut self,node:&g_TinyExpressionP4AST_2e_BooleanFactorExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_StringComparisonExpr(&mut self,node:&g_TinyExpressionP4AST_2e_StringComparisonExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_ComparisonExpr(&mut self,node:&g_TinyExpressionP4AST_2e_ComparisonExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_ObjectExpr(&mut self,node:&g_TinyExpressionP4AST_2e_ObjectExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_IfExpr(&mut self,node:&g_TinyExpressionP4AST_2e_IfExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_BranchExpressionExpr(&mut self,node:&g_TinyExpressionP4AST_2e_BranchExpressionExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_NumberMatchExpr(&mut self,node:&g_TinyExpressionP4AST_2e_NumberMatchExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_NumberCaseExpr(&mut self,node:&g_TinyExpressionP4AST_2e_NumberCaseExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_NumberDefaultCaseExpr(&mut self,node:&g_TinyExpressionP4AST_2e_NumberDefaultCaseExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_NumberCaseValueExpr(&mut self,node:&g_TinyExpressionP4AST_2e_NumberCaseValueExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_StringMatchExpr(&mut self,node:&g_TinyExpressionP4AST_2e_StringMatchExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_StringCaseExpr(&mut self,node:&g_TinyExpressionP4AST_2e_StringCaseExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_StringDefaultCaseExpr(&mut self,node:&g_TinyExpressionP4AST_2e_StringDefaultCaseExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_StringCaseValueExpr(&mut self,node:&g_TinyExpressionP4AST_2e_StringCaseValueExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_BooleanMatchExpr(&mut self,node:&g_TinyExpressionP4AST_2e_BooleanMatchExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_BooleanCaseExpr(&mut self,node:&g_TinyExpressionP4AST_2e_BooleanCaseExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_BooleanDefaultCaseExpr(&mut self,node:&g_TinyExpressionP4AST_2e_BooleanDefaultCaseExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_BooleanCaseValueExpr(&mut self,node:&g_TinyExpressionP4AST_2e_BooleanCaseValueExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_VariableRefExpr(&mut self,node:&g_TinyExpressionP4AST_2e_VariableRefExpr)->EvalResult;
fn eval_g_TinyExpressionP4AST_2e_ExpressionExpr(&mut self,node:&g_TinyExpressionP4AST_2e_ExpressionExpr)->EvalResult;
}
/// Automatic kinds work without overrides; semantic callbacks report a missing implementation.
pub struct DefaultEvaluator;
impl Evaluator for DefaultEvaluator {
fn eval_g_TinyExpressionP4AST_2e_AbsExpr(&mut self,node:&g_TinyExpressionP4AST_2e_AbsExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_AbsExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_ArgumentExpressionExpr(&mut self,node:&g_TinyExpressionP4AST_2e_ArgumentExpressionExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_ArgumentExpressionExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_ArgumentsExpr(&mut self,node:&g_TinyExpressionP4AST_2e_ArgumentsExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_ArgumentsExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_BinaryExpr(&mut self,node:&g_TinyExpressionP4AST_2e_BinaryExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_BinaryExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_BooleanAndExpr(&mut self,node:&g_TinyExpressionP4AST_2e_BooleanAndExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_BooleanAndExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_BooleanCaseExpr(&mut self,node:&g_TinyExpressionP4AST_2e_BooleanCaseExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_BooleanCaseExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_BooleanCaseValueExpr(&mut self,node:&g_TinyExpressionP4AST_2e_BooleanCaseValueExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_BooleanCaseValueExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_BooleanDefaultCaseExpr(&mut self,node:&g_TinyExpressionP4AST_2e_BooleanDefaultCaseExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_BooleanDefaultCaseExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_BooleanEqualityExpr(&mut self,node:&g_TinyExpressionP4AST_2e_BooleanEqualityExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_BooleanEqualityExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_BooleanFactorExpr(&mut self,node:&g_TinyExpressionP4AST_2e_BooleanFactorExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_BooleanFactorExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_BooleanMatchExpr(&mut self,node:&g_TinyExpressionP4AST_2e_BooleanMatchExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_BooleanMatchExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_BooleanMethodDeclarationExpr(&mut self,node:&g_TinyExpressionP4AST_2e_BooleanMethodDeclarationExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_BooleanMethodDeclarationExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_BooleanOrExpr(&mut self,node:&g_TinyExpressionP4AST_2e_BooleanOrExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_BooleanOrExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_BooleanVariableDeclarationExpr(&mut self,node:&g_TinyExpressionP4AST_2e_BooleanVariableDeclarationExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_BooleanVariableDeclarationExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_BooleanXorExpr(&mut self,node:&g_TinyExpressionP4AST_2e_BooleanXorExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_BooleanXorExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_BranchExpressionExpr(&mut self,node:&g_TinyExpressionP4AST_2e_BranchExpressionExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_BranchExpressionExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_CeilExpr(&mut self,node:&g_TinyExpressionP4AST_2e_CeilExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_CeilExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_CodeBlockExpr(&mut self,node:&g_TinyExpressionP4AST_2e_CodeBlockExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_CodeBlockExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_ComparisonExpr(&mut self,node:&g_TinyExpressionP4AST_2e_ComparisonExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_ComparisonExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_ContainsDotExpr(&mut self,node:&g_TinyExpressionP4AST_2e_ContainsDotExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_ContainsDotExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_ContainsExpr(&mut self,node:&g_TinyExpressionP4AST_2e_ContainsExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_ContainsExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_CosExpr(&mut self,node:&g_TinyExpressionP4AST_2e_CosExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_CosExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_EndsWithDotExpr(&mut self,node:&g_TinyExpressionP4AST_2e_EndsWithDotExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_EndsWithDotExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_EndsWithExpr(&mut self,node:&g_TinyExpressionP4AST_2e_EndsWithExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_EndsWithExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_ExpExpr(&mut self,node:&g_TinyExpressionP4AST_2e_ExpExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_ExpExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_ExpressionExpr(&mut self,node:&g_TinyExpressionP4AST_2e_ExpressionExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_ExpressionExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_ExternalBooleanInvocationExpr(&mut self,node:&g_TinyExpressionP4AST_2e_ExternalBooleanInvocationExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_ExternalBooleanInvocationExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_ExternalNumberInvocationExpr(&mut self,node:&g_TinyExpressionP4AST_2e_ExternalNumberInvocationExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_ExternalNumberInvocationExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_ExternalObjectInvocationExpr(&mut self,node:&g_TinyExpressionP4AST_2e_ExternalObjectInvocationExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_ExternalObjectInvocationExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_ExternalStringInvocationExpr(&mut self,node:&g_TinyExpressionP4AST_2e_ExternalStringInvocationExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_ExternalStringInvocationExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_FloorExpr(&mut self,node:&g_TinyExpressionP4AST_2e_FloorExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_FloorExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_FormulaExpr(&mut self,node:&g_TinyExpressionP4AST_2e_FormulaExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_FormulaExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_IfExpr(&mut self,node:&g_TinyExpressionP4AST_2e_IfExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_IfExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_ImportDeclarationExpr(&mut self,node:&g_TinyExpressionP4AST_2e_ImportDeclarationExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_ImportDeclarationExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_InDayTimeRangeExpr(&mut self,node:&g_TinyExpressionP4AST_2e_InDayTimeRangeExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_InDayTimeRangeExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_InExpr(&mut self,node:&g_TinyExpressionP4AST_2e_InExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_InExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_InTimeRangeExpr(&mut self,node:&g_TinyExpressionP4AST_2e_InTimeRangeExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_InTimeRangeExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_IsPresentExpr(&mut self,node:&g_TinyExpressionP4AST_2e_IsPresentExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_IsPresentExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_LengthDotExpr(&mut self,node:&g_TinyExpressionP4AST_2e_LengthDotExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_LengthDotExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_LengthExpr(&mut self,node:&g_TinyExpressionP4AST_2e_LengthExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_LengthExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_LogExpr(&mut self,node:&g_TinyExpressionP4AST_2e_LogExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_LogExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_MaxExpr(&mut self,node:&g_TinyExpressionP4AST_2e_MaxExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_MaxExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_MethodInvocationExpr(&mut self,node:&g_TinyExpressionP4AST_2e_MethodInvocationExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_MethodInvocationExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_MethodParameterExpr(&mut self,node:&g_TinyExpressionP4AST_2e_MethodParameterExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_MethodParameterExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_MethodParametersExpr(&mut self,node:&g_TinyExpressionP4AST_2e_MethodParametersExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_MethodParametersExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_MinExpr(&mut self,node:&g_TinyExpressionP4AST_2e_MinExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_MinExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_NotExpr(&mut self,node:&g_TinyExpressionP4AST_2e_NotExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_NotExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_NumberCaseExpr(&mut self,node:&g_TinyExpressionP4AST_2e_NumberCaseExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_NumberCaseExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_NumberCaseValueExpr(&mut self,node:&g_TinyExpressionP4AST_2e_NumberCaseValueExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_NumberCaseValueExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_NumberDefaultCaseExpr(&mut self,node:&g_TinyExpressionP4AST_2e_NumberDefaultCaseExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_NumberDefaultCaseExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_NumberMatchExpr(&mut self,node:&g_TinyExpressionP4AST_2e_NumberMatchExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_NumberMatchExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_NumberMethodDeclarationExpr(&mut self,node:&g_TinyExpressionP4AST_2e_NumberMethodDeclarationExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_NumberMethodDeclarationExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_NumberVariableDeclarationExpr(&mut self,node:&g_TinyExpressionP4AST_2e_NumberVariableDeclarationExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_NumberVariableDeclarationExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_ObjectExpr(&mut self,node:&g_TinyExpressionP4AST_2e_ObjectExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_ObjectExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_ObjectMethodDeclarationExpr(&mut self,node:&g_TinyExpressionP4AST_2e_ObjectMethodDeclarationExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_ObjectMethodDeclarationExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_ObjectVariableDeclarationExpr(&mut self,node:&g_TinyExpressionP4AST_2e_ObjectVariableDeclarationExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_ObjectVariableDeclarationExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_OnlyIfAbsentExpr(&mut self,node:&g_TinyExpressionP4AST_2e_OnlyIfAbsentExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_OnlyIfAbsentExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_PowExpr(&mut self,node:&g_TinyExpressionP4AST_2e_PowExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_PowExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_QualifiedNameExpr(&mut self,node:&g_TinyExpressionP4AST_2e_QualifiedNameExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_QualifiedNameExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_RandomExpr(&mut self,node:&g_TinyExpressionP4AST_2e_RandomExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_RandomExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_RoundExpr(&mut self,node:&g_TinyExpressionP4AST_2e_RoundExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_RoundExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_SinExpr(&mut self,node:&g_TinyExpressionP4AST_2e_SinExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_SinExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_SliceExpr(&mut self,node:&g_TinyExpressionP4AST_2e_SliceExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_SliceExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_SqrtExpr(&mut self,node:&g_TinyExpressionP4AST_2e_SqrtExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_SqrtExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_StartsWithDotExpr(&mut self,node:&g_TinyExpressionP4AST_2e_StartsWithDotExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_StartsWithDotExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_StartsWithExpr(&mut self,node:&g_TinyExpressionP4AST_2e_StartsWithExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_StartsWithExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_StringCaseExpr(&mut self,node:&g_TinyExpressionP4AST_2e_StringCaseExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_StringCaseExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_StringCaseValueExpr(&mut self,node:&g_TinyExpressionP4AST_2e_StringCaseValueExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_StringCaseValueExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_StringCastVariableRefExpr(&mut self,node:&g_TinyExpressionP4AST_2e_StringCastVariableRefExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_StringCastVariableRefExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_StringComparisonExpr(&mut self,node:&g_TinyExpressionP4AST_2e_StringComparisonExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_StringComparisonExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_StringConcatExpr(&mut self,node:&g_TinyExpressionP4AST_2e_StringConcatExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_StringConcatExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_StringDefaultCaseExpr(&mut self,node:&g_TinyExpressionP4AST_2e_StringDefaultCaseExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_StringDefaultCaseExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_StringMatchExpr(&mut self,node:&g_TinyExpressionP4AST_2e_StringMatchExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_StringMatchExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_StringMethodDeclarationExpr(&mut self,node:&g_TinyExpressionP4AST_2e_StringMethodDeclarationExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_StringMethodDeclarationExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_StringTypedVariableRefExpr(&mut self,node:&g_TinyExpressionP4AST_2e_StringTypedVariableRefExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_StringTypedVariableRefExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_StringVariableDeclarationExpr(&mut self,node:&g_TinyExpressionP4AST_2e_StringVariableDeclarationExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_StringVariableDeclarationExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_TanExpr(&mut self,node:&g_TinyExpressionP4AST_2e_TanExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_TanExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_TernaryExpr(&mut self,node:&g_TinyExpressionP4AST_2e_TernaryExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_TernaryExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_ToLowerCaseDotExpr(&mut self,node:&g_TinyExpressionP4AST_2e_ToLowerCaseDotExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_ToLowerCaseDotExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_ToLowerCaseExpr(&mut self,node:&g_TinyExpressionP4AST_2e_ToLowerCaseExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_ToLowerCaseExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_ToNumExpr(&mut self,node:&g_TinyExpressionP4AST_2e_ToNumExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_ToNumExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_ToUpperCaseDotExpr(&mut self,node:&g_TinyExpressionP4AST_2e_ToUpperCaseDotExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_ToUpperCaseDotExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_ToUpperCaseExpr(&mut self,node:&g_TinyExpressionP4AST_2e_ToUpperCaseExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_ToUpperCaseExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_TrimDotExpr(&mut self,node:&g_TinyExpressionP4AST_2e_TrimDotExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_TrimDotExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_TrimExpr(&mut self,node:&g_TinyExpressionP4AST_2e_TrimExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_TrimExpr".into()))}
fn eval_g_TinyExpressionP4AST_2e_VariableRefExpr(&mut self,node:&g_TinyExpressionP4AST_2e_VariableRefExpr)->EvalResult {let _=(node,);Err(EvalError("callback required: eval_g_TinyExpressionP4AST_2e_VariableRefExpr".into()))}
}
pub fn evaluate<E:Evaluator+?Sized>(node:&Ast,evaluator:&mut E)->EvalResult {evaluator.eval(node)}
