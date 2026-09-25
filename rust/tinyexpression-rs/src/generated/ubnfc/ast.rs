//! 採用結果だけから構築する AST。数値 capture は字句 String。
#![allow(non_camel_case_types, non_snake_case)]
use super::api::Span;
#[derive(Clone,Debug,PartialEq)]
pub enum Ast { Text(String), Null,
g_TinyExpressionP4AST_2e_FormulaExpr(g_TinyExpressionP4AST_2e_FormulaExpr),
g_TinyExpressionP4AST_2e_CodeBlockExpr(g_TinyExpressionP4AST_2e_CodeBlockExpr),
g_TinyExpressionP4AST_2e_ImportDeclarationExpr(g_TinyExpressionP4AST_2e_ImportDeclarationExpr),
g_TinyExpressionP4AST_2e_QualifiedNameExpr(g_TinyExpressionP4AST_2e_QualifiedNameExpr),
g_TinyExpressionP4AST_2e_NumberVariableDeclarationExpr(g_TinyExpressionP4AST_2e_NumberVariableDeclarationExpr),
g_TinyExpressionP4AST_2e_StringVariableDeclarationExpr(g_TinyExpressionP4AST_2e_StringVariableDeclarationExpr),
g_TinyExpressionP4AST_2e_BooleanVariableDeclarationExpr(g_TinyExpressionP4AST_2e_BooleanVariableDeclarationExpr),
g_TinyExpressionP4AST_2e_ObjectVariableDeclarationExpr(g_TinyExpressionP4AST_2e_ObjectVariableDeclarationExpr),
g_TinyExpressionP4AST_2e_OnlyIfAbsentExpr(g_TinyExpressionP4AST_2e_OnlyIfAbsentExpr),
g_TinyExpressionP4AST_2e_NumberMethodDeclarationExpr(g_TinyExpressionP4AST_2e_NumberMethodDeclarationExpr),
g_TinyExpressionP4AST_2e_StringMethodDeclarationExpr(g_TinyExpressionP4AST_2e_StringMethodDeclarationExpr),
g_TinyExpressionP4AST_2e_BooleanMethodDeclarationExpr(g_TinyExpressionP4AST_2e_BooleanMethodDeclarationExpr),
g_TinyExpressionP4AST_2e_ObjectMethodDeclarationExpr(g_TinyExpressionP4AST_2e_ObjectMethodDeclarationExpr),
g_TinyExpressionP4AST_2e_MethodParametersExpr(g_TinyExpressionP4AST_2e_MethodParametersExpr),
g_TinyExpressionP4AST_2e_MethodParameterExpr(g_TinyExpressionP4AST_2e_MethodParameterExpr),
g_TinyExpressionP4AST_2e_ExternalBooleanInvocationExpr(g_TinyExpressionP4AST_2e_ExternalBooleanInvocationExpr),
g_TinyExpressionP4AST_2e_ExternalNumberInvocationExpr(g_TinyExpressionP4AST_2e_ExternalNumberInvocationExpr),
g_TinyExpressionP4AST_2e_ExternalStringInvocationExpr(g_TinyExpressionP4AST_2e_ExternalStringInvocationExpr),
g_TinyExpressionP4AST_2e_ExternalObjectInvocationExpr(g_TinyExpressionP4AST_2e_ExternalObjectInvocationExpr),
g_TinyExpressionP4AST_2e_MethodInvocationExpr(g_TinyExpressionP4AST_2e_MethodInvocationExpr),
g_TinyExpressionP4AST_2e_TernaryExpr(g_TinyExpressionP4AST_2e_TernaryExpr),
g_TinyExpressionP4AST_2e_ArgumentExpressionExpr(g_TinyExpressionP4AST_2e_ArgumentExpressionExpr),
g_TinyExpressionP4AST_2e_ArgumentsExpr(g_TinyExpressionP4AST_2e_ArgumentsExpr),
g_TinyExpressionP4AST_2e_BinaryExpr(g_TinyExpressionP4AST_2e_BinaryExpr),
g_TinyExpressionP4AST_2e_SinExpr(g_TinyExpressionP4AST_2e_SinExpr),
g_TinyExpressionP4AST_2e_CosExpr(g_TinyExpressionP4AST_2e_CosExpr),
g_TinyExpressionP4AST_2e_TanExpr(g_TinyExpressionP4AST_2e_TanExpr),
g_TinyExpressionP4AST_2e_SqrtExpr(g_TinyExpressionP4AST_2e_SqrtExpr),
g_TinyExpressionP4AST_2e_MinExpr(g_TinyExpressionP4AST_2e_MinExpr),
g_TinyExpressionP4AST_2e_MaxExpr(g_TinyExpressionP4AST_2e_MaxExpr),
g_TinyExpressionP4AST_2e_RandomExpr(g_TinyExpressionP4AST_2e_RandomExpr),
g_TinyExpressionP4AST_2e_AbsExpr(g_TinyExpressionP4AST_2e_AbsExpr),
g_TinyExpressionP4AST_2e_RoundExpr(g_TinyExpressionP4AST_2e_RoundExpr),
g_TinyExpressionP4AST_2e_CeilExpr(g_TinyExpressionP4AST_2e_CeilExpr),
g_TinyExpressionP4AST_2e_FloorExpr(g_TinyExpressionP4AST_2e_FloorExpr),
g_TinyExpressionP4AST_2e_PowExpr(g_TinyExpressionP4AST_2e_PowExpr),
g_TinyExpressionP4AST_2e_LogExpr(g_TinyExpressionP4AST_2e_LogExpr),
g_TinyExpressionP4AST_2e_ExpExpr(g_TinyExpressionP4AST_2e_ExpExpr),
g_TinyExpressionP4AST_2e_ToNumExpr(g_TinyExpressionP4AST_2e_ToNumExpr),
g_TinyExpressionP4AST_2e_ToUpperCaseExpr(g_TinyExpressionP4AST_2e_ToUpperCaseExpr),
g_TinyExpressionP4AST_2e_ToLowerCaseExpr(g_TinyExpressionP4AST_2e_ToLowerCaseExpr),
g_TinyExpressionP4AST_2e_TrimExpr(g_TinyExpressionP4AST_2e_TrimExpr),
g_TinyExpressionP4AST_2e_LengthExpr(g_TinyExpressionP4AST_2e_LengthExpr),
g_TinyExpressionP4AST_2e_ToUpperCaseDotExpr(g_TinyExpressionP4AST_2e_ToUpperCaseDotExpr),
g_TinyExpressionP4AST_2e_ToLowerCaseDotExpr(g_TinyExpressionP4AST_2e_ToLowerCaseDotExpr),
g_TinyExpressionP4AST_2e_TrimDotExpr(g_TinyExpressionP4AST_2e_TrimDotExpr),
g_TinyExpressionP4AST_2e_LengthDotExpr(g_TinyExpressionP4AST_2e_LengthDotExpr),
g_TinyExpressionP4AST_2e_StartsWithExpr(g_TinyExpressionP4AST_2e_StartsWithExpr),
g_TinyExpressionP4AST_2e_EndsWithExpr(g_TinyExpressionP4AST_2e_EndsWithExpr),
g_TinyExpressionP4AST_2e_ContainsExpr(g_TinyExpressionP4AST_2e_ContainsExpr),
g_TinyExpressionP4AST_2e_InExpr(g_TinyExpressionP4AST_2e_InExpr),
g_TinyExpressionP4AST_2e_StartsWithDotExpr(g_TinyExpressionP4AST_2e_StartsWithDotExpr),
g_TinyExpressionP4AST_2e_EndsWithDotExpr(g_TinyExpressionP4AST_2e_EndsWithDotExpr),
g_TinyExpressionP4AST_2e_ContainsDotExpr(g_TinyExpressionP4AST_2e_ContainsDotExpr),
g_TinyExpressionP4AST_2e_IsPresentExpr(g_TinyExpressionP4AST_2e_IsPresentExpr),
g_TinyExpressionP4AST_2e_InTimeRangeExpr(g_TinyExpressionP4AST_2e_InTimeRangeExpr),
g_TinyExpressionP4AST_2e_InDayTimeRangeExpr(g_TinyExpressionP4AST_2e_InDayTimeRangeExpr),
g_TinyExpressionP4AST_2e_SliceExpr(g_TinyExpressionP4AST_2e_SliceExpr),
g_TinyExpressionP4AST_2e_StringConcatExpr(g_TinyExpressionP4AST_2e_StringConcatExpr),
g_TinyExpressionP4AST_2e_StringCastVariableRefExpr(g_TinyExpressionP4AST_2e_StringCastVariableRefExpr),
g_TinyExpressionP4AST_2e_StringTypedVariableRefExpr(g_TinyExpressionP4AST_2e_StringTypedVariableRefExpr),
g_TinyExpressionP4AST_2e_BooleanOrExpr(g_TinyExpressionP4AST_2e_BooleanOrExpr),
g_TinyExpressionP4AST_2e_BooleanAndExpr(g_TinyExpressionP4AST_2e_BooleanAndExpr),
g_TinyExpressionP4AST_2e_BooleanXorExpr(g_TinyExpressionP4AST_2e_BooleanXorExpr),
g_TinyExpressionP4AST_2e_NotExpr(g_TinyExpressionP4AST_2e_NotExpr),
g_TinyExpressionP4AST_2e_BooleanEqualityExpr(g_TinyExpressionP4AST_2e_BooleanEqualityExpr),
g_TinyExpressionP4AST_2e_BooleanFactorExpr(g_TinyExpressionP4AST_2e_BooleanFactorExpr),
g_TinyExpressionP4AST_2e_StringComparisonExpr(g_TinyExpressionP4AST_2e_StringComparisonExpr),
g_TinyExpressionP4AST_2e_ComparisonExpr(g_TinyExpressionP4AST_2e_ComparisonExpr),
g_TinyExpressionP4AST_2e_ObjectExpr(g_TinyExpressionP4AST_2e_ObjectExpr),
g_TinyExpressionP4AST_2e_IfExpr(g_TinyExpressionP4AST_2e_IfExpr),
g_TinyExpressionP4AST_2e_BranchExpressionExpr(g_TinyExpressionP4AST_2e_BranchExpressionExpr),
g_TinyExpressionP4AST_2e_NumberMatchExpr(g_TinyExpressionP4AST_2e_NumberMatchExpr),
g_TinyExpressionP4AST_2e_NumberCaseExpr(g_TinyExpressionP4AST_2e_NumberCaseExpr),
g_TinyExpressionP4AST_2e_NumberDefaultCaseExpr(g_TinyExpressionP4AST_2e_NumberDefaultCaseExpr),
g_TinyExpressionP4AST_2e_NumberCaseValueExpr(g_TinyExpressionP4AST_2e_NumberCaseValueExpr),
g_TinyExpressionP4AST_2e_StringMatchExpr(g_TinyExpressionP4AST_2e_StringMatchExpr),
g_TinyExpressionP4AST_2e_StringCaseExpr(g_TinyExpressionP4AST_2e_StringCaseExpr),
g_TinyExpressionP4AST_2e_StringDefaultCaseExpr(g_TinyExpressionP4AST_2e_StringDefaultCaseExpr),
g_TinyExpressionP4AST_2e_StringCaseValueExpr(g_TinyExpressionP4AST_2e_StringCaseValueExpr),
g_TinyExpressionP4AST_2e_BooleanMatchExpr(g_TinyExpressionP4AST_2e_BooleanMatchExpr),
g_TinyExpressionP4AST_2e_BooleanCaseExpr(g_TinyExpressionP4AST_2e_BooleanCaseExpr),
g_TinyExpressionP4AST_2e_BooleanDefaultCaseExpr(g_TinyExpressionP4AST_2e_BooleanDefaultCaseExpr),
g_TinyExpressionP4AST_2e_BooleanCaseValueExpr(g_TinyExpressionP4AST_2e_BooleanCaseValueExpr),
g_TinyExpressionP4AST_2e_VariableRefExpr(g_TinyExpressionP4AST_2e_VariableRefExpr),
g_TinyExpressionP4AST_2e_ExpressionExpr(g_TinyExpressionP4AST_2e_ExpressionExpr),
}
pub type g_TinyExpressionP4AST = Ast;
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_FormulaExpr { pub span:Span, pub node_id:usize,
pub g_imports: Vec<Ast>,
pub g_declarations: Vec<Ast>,
pub g_expression: Box<Ast>,
pub g_methods: Vec<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_CodeBlockExpr { pub span:Span, pub node_id:usize,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_ImportDeclarationExpr { pub span:Span, pub node_id:usize,
pub g_className: Box<Ast>,
pub g_method: Option<String>,
pub g_alias: String,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_QualifiedNameExpr { pub span:Span, pub node_id:usize,
pub g_head: String,
pub g_tail: Vec<String>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_NumberVariableDeclarationExpr { pub span:Span, pub node_id:usize,
pub g_varName: String,
pub g_onlyIfAbsent: Option<Box<Ast>>,
pub g_value: Option<Box<Ast>>,
pub g_desc: Option<String>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_StringVariableDeclarationExpr { pub span:Span, pub node_id:usize,
pub g_varName: String,
pub g_onlyIfAbsent: Option<Box<Ast>>,
pub g_value: Option<Box<Ast>>,
pub g_desc: Option<String>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_BooleanVariableDeclarationExpr { pub span:Span, pub node_id:usize,
pub g_varName: String,
pub g_onlyIfAbsent: Option<Box<Ast>>,
pub g_value: Option<Box<Ast>>,
pub g_desc: Option<String>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_ObjectVariableDeclarationExpr { pub span:Span, pub node_id:usize,
pub g_varName: String,
pub g_onlyIfAbsent: Option<Box<Ast>>,
pub g_value: Option<Box<Ast>>,
pub g_desc: Option<String>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_OnlyIfAbsentExpr { pub span:Span, pub node_id:usize,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_NumberMethodDeclarationExpr { pub span:Span, pub node_id:usize,
pub g_methodName: String,
pub g_parameters: Option<Box<Ast>>,
pub g_expression: Box<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_StringMethodDeclarationExpr { pub span:Span, pub node_id:usize,
pub g_methodName: String,
pub g_parameters: Option<Box<Ast>>,
pub g_expression: Box<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_BooleanMethodDeclarationExpr { pub span:Span, pub node_id:usize,
pub g_methodName: String,
pub g_parameters: Option<Box<Ast>>,
pub g_expression: Box<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_ObjectMethodDeclarationExpr { pub span:Span, pub node_id:usize,
pub g_methodName: String,
pub g_parameters: Option<Box<Ast>>,
pub g_expression: Box<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_MethodParametersExpr { pub span:Span, pub node_id:usize,
pub g_values: Vec<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_MethodParameterExpr { pub span:Span, pub node_id:usize,
pub g_paramName: String,
pub g_type: Option<String>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_ExternalBooleanInvocationExpr { pub span:Span, pub node_id:usize,
pub g_className: Option<Box<Ast>>,
pub g_name: String,
pub g_args: Option<Box<Ast>>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_ExternalNumberInvocationExpr { pub span:Span, pub node_id:usize,
pub g_className: Option<Box<Ast>>,
pub g_name: String,
pub g_args: Option<Box<Ast>>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_ExternalStringInvocationExpr { pub span:Span, pub node_id:usize,
pub g_className: Option<Box<Ast>>,
pub g_name: String,
pub g_args: Option<Box<Ast>>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_ExternalObjectInvocationExpr { pub span:Span, pub node_id:usize,
pub g_className: Option<Box<Ast>>,
pub g_name: String,
pub g_args: Option<Box<Ast>>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_MethodInvocationExpr { pub span:Span, pub node_id:usize,
pub g_name: String,
pub g_args: Option<Box<Ast>>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_TernaryExpr { pub span:Span, pub node_id:usize,
pub g_condition: Box<Ast>,
pub g_thenExpr: Box<Ast>,
pub g_elseExpr: Box<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_ArgumentExpressionExpr { pub span:Span, pub node_id:usize,
pub g_value: Box<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_ArgumentsExpr { pub span:Span, pub node_id:usize,
pub g_values: Vec<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_BinaryExpr { pub span:Span, pub node_id:usize,
pub g_left: Option<Box<Ast>>,
pub g_op: Vec<String>,
pub g_right: Vec<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_SinExpr { pub span:Span, pub node_id:usize,
pub g_arg: Box<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_CosExpr { pub span:Span, pub node_id:usize,
pub g_arg: Box<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_TanExpr { pub span:Span, pub node_id:usize,
pub g_arg: Box<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_SqrtExpr { pub span:Span, pub node_id:usize,
pub g_arg: Box<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_MinExpr { pub span:Span, pub node_id:usize,
pub g_first: Box<Ast>,
pub g_rest: Vec<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_MaxExpr { pub span:Span, pub node_id:usize,
pub g_first: Box<Ast>,
pub g_rest: Vec<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_RandomExpr { pub span:Span, pub node_id:usize,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_AbsExpr { pub span:Span, pub node_id:usize,
pub g_arg: Box<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_RoundExpr { pub span:Span, pub node_id:usize,
pub g_arg: Box<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_CeilExpr { pub span:Span, pub node_id:usize,
pub g_arg: Box<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_FloorExpr { pub span:Span, pub node_id:usize,
pub g_arg: Box<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_PowExpr { pub span:Span, pub node_id:usize,
pub g_base: Box<Ast>,
pub g_exponent: Box<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_LogExpr { pub span:Span, pub node_id:usize,
pub g_arg: Box<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_ExpExpr { pub span:Span, pub node_id:usize,
pub g_arg: Box<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_ToNumExpr { pub span:Span, pub node_id:usize,
pub g_value: Box<Ast>,
pub g_defaultValue: Box<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_ToUpperCaseExpr { pub span:Span, pub node_id:usize,
pub g_value: Box<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_ToLowerCaseExpr { pub span:Span, pub node_id:usize,
pub g_value: Box<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_TrimExpr { pub span:Span, pub node_id:usize,
pub g_value: Box<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_LengthExpr { pub span:Span, pub node_id:usize,
pub g_value: Box<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_ToUpperCaseDotExpr { pub span:Span, pub node_id:usize,
pub g_value: Box<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_ToLowerCaseDotExpr { pub span:Span, pub node_id:usize,
pub g_value: Box<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_TrimDotExpr { pub span:Span, pub node_id:usize,
pub g_value: Box<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_LengthDotExpr { pub span:Span, pub node_id:usize,
pub g_value: Box<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_StartsWithExpr { pub span:Span, pub node_id:usize,
pub g_value: Box<Ast>,
pub g_patterns: Vec<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_EndsWithExpr { pub span:Span, pub node_id:usize,
pub g_value: Box<Ast>,
pub g_patterns: Vec<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_ContainsExpr { pub span:Span, pub node_id:usize,
pub g_value: Box<Ast>,
pub g_patterns: Vec<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_InExpr { pub span:Span, pub node_id:usize,
pub g_value: Box<Ast>,
pub g_candidates: Vec<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_StartsWithDotExpr { pub span:Span, pub node_id:usize,
pub g_value: Box<Ast>,
pub g_patterns: Vec<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_EndsWithDotExpr { pub span:Span, pub node_id:usize,
pub g_value: Box<Ast>,
pub g_patterns: Vec<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_ContainsDotExpr { pub span:Span, pub node_id:usize,
pub g_value: Box<Ast>,
pub g_patterns: Vec<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_IsPresentExpr { pub span:Span, pub node_id:usize,
pub g_value: Box<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_InTimeRangeExpr { pub span:Span, pub node_id:usize,
pub g_startHour: Box<Ast>,
pub g_endHour: Box<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_InDayTimeRangeExpr { pub span:Span, pub node_id:usize,
pub g_startDay: String,
pub g_startHour: Box<Ast>,
pub g_endDay: String,
pub g_endHour: Box<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_SliceExpr { pub span:Span, pub node_id:usize,
pub g_value: Box<Ast>,
pub g_start: Option<Box<Ast>>,
pub g_end: Option<Box<Ast>>,
pub g_step: Option<Box<Ast>>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_StringConcatExpr { pub span:Span, pub node_id:usize,
pub g_left: Box<Ast>,
pub g_op: Vec<String>,
pub g_right: Vec<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_StringCastVariableRefExpr { pub span:Span, pub node_id:usize,
pub g_name: String,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_StringTypedVariableRefExpr { pub span:Span, pub node_id:usize,
pub g_name: String,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_BooleanOrExpr { pub span:Span, pub node_id:usize,
pub g_left: Box<Ast>,
pub g_op: Vec<String>,
pub g_right: Vec<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_BooleanAndExpr { pub span:Span, pub node_id:usize,
pub g_left: Box<Ast>,
pub g_op: Vec<String>,
pub g_right: Vec<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_BooleanXorExpr { pub span:Span, pub node_id:usize,
pub g_left: Box<Ast>,
pub g_op: Vec<String>,
pub g_right: Vec<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_NotExpr { pub span:Span, pub node_id:usize,
pub g_value: Box<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_BooleanEqualityExpr { pub span:Span, pub node_id:usize,
pub g_left: Box<Ast>,
pub g_op: String,
pub g_right: Box<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_BooleanFactorExpr { pub span:Span, pub node_id:usize,
pub g_value: Box<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_StringComparisonExpr { pub span:Span, pub node_id:usize,
pub g_left: Box<Ast>,
pub g_op: String,
pub g_right: Box<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_ComparisonExpr { pub span:Span, pub node_id:usize,
pub g_left: Box<Ast>,
pub g_op: String,
pub g_right: Box<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_ObjectExpr { pub span:Span, pub node_id:usize,
pub g_value: Box<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_IfExpr { pub span:Span, pub node_id:usize,
pub g_condition: Box<Ast>,
pub g_thenExpr: Box<Ast>,
pub g_elseExpr: Box<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_BranchExpressionExpr { pub span:Span, pub node_id:usize,
pub g_value: Box<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_NumberMatchExpr { pub span:Span, pub node_id:usize,
pub g_firstCase: Box<Ast>,
pub g_moreCases: Vec<Ast>,
pub g_defaultCase: Box<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_NumberCaseExpr { pub span:Span, pub node_id:usize,
pub g_condition: Box<Ast>,
pub g_value: Box<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_NumberDefaultCaseExpr { pub span:Span, pub node_id:usize,
pub g_value: Box<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_NumberCaseValueExpr { pub span:Span, pub node_id:usize,
pub g_value: Box<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_StringMatchExpr { pub span:Span, pub node_id:usize,
pub g_firstCase: Box<Ast>,
pub g_moreCases: Vec<Ast>,
pub g_defaultCase: Box<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_StringCaseExpr { pub span:Span, pub node_id:usize,
pub g_condition: Box<Ast>,
pub g_value: Box<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_StringDefaultCaseExpr { pub span:Span, pub node_id:usize,
pub g_value: Box<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_StringCaseValueExpr { pub span:Span, pub node_id:usize,
pub g_value: Box<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_BooleanMatchExpr { pub span:Span, pub node_id:usize,
pub g_firstCase: Box<Ast>,
pub g_moreCases: Vec<Ast>,
pub g_defaultCase: Box<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_BooleanCaseExpr { pub span:Span, pub node_id:usize,
pub g_condition: Box<Ast>,
pub g_value: Box<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_BooleanDefaultCaseExpr { pub span:Span, pub node_id:usize,
pub g_value: Box<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_BooleanCaseValueExpr { pub span:Span, pub node_id:usize,
pub g_value: Box<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_VariableRefExpr { pub span:Span, pub node_id:usize,
pub g_name: String,
pub g_type: Option<String>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_TinyExpressionP4AST_2e_ExpressionExpr { pub span:Span, pub node_id:usize,
pub g_value: Box<Ast>,
}
impl Ast { pub fn span(&self)->Option<Span> { match self {Self::Text(_)|Self::Null=>None,
Self::g_TinyExpressionP4AST_2e_FormulaExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_CodeBlockExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_ImportDeclarationExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_QualifiedNameExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_NumberVariableDeclarationExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_StringVariableDeclarationExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_BooleanVariableDeclarationExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_ObjectVariableDeclarationExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_OnlyIfAbsentExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_NumberMethodDeclarationExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_StringMethodDeclarationExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_BooleanMethodDeclarationExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_ObjectMethodDeclarationExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_MethodParametersExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_MethodParameterExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_ExternalBooleanInvocationExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_ExternalNumberInvocationExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_ExternalStringInvocationExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_ExternalObjectInvocationExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_MethodInvocationExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_TernaryExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_ArgumentExpressionExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_ArgumentsExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_BinaryExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_SinExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_CosExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_TanExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_SqrtExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_MinExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_MaxExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_RandomExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_AbsExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_RoundExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_CeilExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_FloorExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_PowExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_LogExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_ExpExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_ToNumExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_ToUpperCaseExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_ToLowerCaseExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_TrimExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_LengthExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_ToUpperCaseDotExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_ToLowerCaseDotExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_TrimDotExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_LengthDotExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_StartsWithExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_EndsWithExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_ContainsExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_InExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_StartsWithDotExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_EndsWithDotExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_ContainsDotExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_IsPresentExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_InTimeRangeExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_InDayTimeRangeExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_SliceExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_StringConcatExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_StringCastVariableRefExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_StringTypedVariableRefExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_BooleanOrExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_BooleanAndExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_BooleanXorExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_NotExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_BooleanEqualityExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_BooleanFactorExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_StringComparisonExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_ComparisonExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_ObjectExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_IfExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_BranchExpressionExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_NumberMatchExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_NumberCaseExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_NumberDefaultCaseExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_NumberCaseValueExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_StringMatchExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_StringCaseExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_StringDefaultCaseExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_StringCaseValueExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_BooleanMatchExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_BooleanCaseExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_BooleanDefaultCaseExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_BooleanCaseValueExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_VariableRefExpr(node)=>Some(node.span),
Self::g_TinyExpressionP4AST_2e_ExpressionExpr(node)=>Some(node.span),
}}
pub fn type_name(&self)->&'static str { match self {Self::Text(_)=>"text",Self::Null=>"null",
Self::g_TinyExpressionP4AST_2e_FormulaExpr(_)=>"FormulaExpr",
Self::g_TinyExpressionP4AST_2e_CodeBlockExpr(_)=>"CodeBlockExpr",
Self::g_TinyExpressionP4AST_2e_ImportDeclarationExpr(_)=>"ImportDeclarationExpr",
Self::g_TinyExpressionP4AST_2e_QualifiedNameExpr(_)=>"QualifiedNameExpr",
Self::g_TinyExpressionP4AST_2e_NumberVariableDeclarationExpr(_)=>"NumberVariableDeclarationExpr",
Self::g_TinyExpressionP4AST_2e_StringVariableDeclarationExpr(_)=>"StringVariableDeclarationExpr",
Self::g_TinyExpressionP4AST_2e_BooleanVariableDeclarationExpr(_)=>"BooleanVariableDeclarationExpr",
Self::g_TinyExpressionP4AST_2e_ObjectVariableDeclarationExpr(_)=>"ObjectVariableDeclarationExpr",
Self::g_TinyExpressionP4AST_2e_OnlyIfAbsentExpr(_)=>"OnlyIfAbsentExpr",
Self::g_TinyExpressionP4AST_2e_NumberMethodDeclarationExpr(_)=>"NumberMethodDeclarationExpr",
Self::g_TinyExpressionP4AST_2e_StringMethodDeclarationExpr(_)=>"StringMethodDeclarationExpr",
Self::g_TinyExpressionP4AST_2e_BooleanMethodDeclarationExpr(_)=>"BooleanMethodDeclarationExpr",
Self::g_TinyExpressionP4AST_2e_ObjectMethodDeclarationExpr(_)=>"ObjectMethodDeclarationExpr",
Self::g_TinyExpressionP4AST_2e_MethodParametersExpr(_)=>"MethodParametersExpr",
Self::g_TinyExpressionP4AST_2e_MethodParameterExpr(_)=>"MethodParameterExpr",
Self::g_TinyExpressionP4AST_2e_ExternalBooleanInvocationExpr(_)=>"ExternalBooleanInvocationExpr",
Self::g_TinyExpressionP4AST_2e_ExternalNumberInvocationExpr(_)=>"ExternalNumberInvocationExpr",
Self::g_TinyExpressionP4AST_2e_ExternalStringInvocationExpr(_)=>"ExternalStringInvocationExpr",
Self::g_TinyExpressionP4AST_2e_ExternalObjectInvocationExpr(_)=>"ExternalObjectInvocationExpr",
Self::g_TinyExpressionP4AST_2e_MethodInvocationExpr(_)=>"MethodInvocationExpr",
Self::g_TinyExpressionP4AST_2e_TernaryExpr(_)=>"TernaryExpr",
Self::g_TinyExpressionP4AST_2e_ArgumentExpressionExpr(_)=>"ArgumentExpressionExpr",
Self::g_TinyExpressionP4AST_2e_ArgumentsExpr(_)=>"ArgumentsExpr",
Self::g_TinyExpressionP4AST_2e_BinaryExpr(_)=>"BinaryExpr",
Self::g_TinyExpressionP4AST_2e_SinExpr(_)=>"SinExpr",
Self::g_TinyExpressionP4AST_2e_CosExpr(_)=>"CosExpr",
Self::g_TinyExpressionP4AST_2e_TanExpr(_)=>"TanExpr",
Self::g_TinyExpressionP4AST_2e_SqrtExpr(_)=>"SqrtExpr",
Self::g_TinyExpressionP4AST_2e_MinExpr(_)=>"MinExpr",
Self::g_TinyExpressionP4AST_2e_MaxExpr(_)=>"MaxExpr",
Self::g_TinyExpressionP4AST_2e_RandomExpr(_)=>"RandomExpr",
Self::g_TinyExpressionP4AST_2e_AbsExpr(_)=>"AbsExpr",
Self::g_TinyExpressionP4AST_2e_RoundExpr(_)=>"RoundExpr",
Self::g_TinyExpressionP4AST_2e_CeilExpr(_)=>"CeilExpr",
Self::g_TinyExpressionP4AST_2e_FloorExpr(_)=>"FloorExpr",
Self::g_TinyExpressionP4AST_2e_PowExpr(_)=>"PowExpr",
Self::g_TinyExpressionP4AST_2e_LogExpr(_)=>"LogExpr",
Self::g_TinyExpressionP4AST_2e_ExpExpr(_)=>"ExpExpr",
Self::g_TinyExpressionP4AST_2e_ToNumExpr(_)=>"ToNumExpr",
Self::g_TinyExpressionP4AST_2e_ToUpperCaseExpr(_)=>"ToUpperCaseExpr",
Self::g_TinyExpressionP4AST_2e_ToLowerCaseExpr(_)=>"ToLowerCaseExpr",
Self::g_TinyExpressionP4AST_2e_TrimExpr(_)=>"TrimExpr",
Self::g_TinyExpressionP4AST_2e_LengthExpr(_)=>"LengthExpr",
Self::g_TinyExpressionP4AST_2e_ToUpperCaseDotExpr(_)=>"ToUpperCaseDotExpr",
Self::g_TinyExpressionP4AST_2e_ToLowerCaseDotExpr(_)=>"ToLowerCaseDotExpr",
Self::g_TinyExpressionP4AST_2e_TrimDotExpr(_)=>"TrimDotExpr",
Self::g_TinyExpressionP4AST_2e_LengthDotExpr(_)=>"LengthDotExpr",
Self::g_TinyExpressionP4AST_2e_StartsWithExpr(_)=>"StartsWithExpr",
Self::g_TinyExpressionP4AST_2e_EndsWithExpr(_)=>"EndsWithExpr",
Self::g_TinyExpressionP4AST_2e_ContainsExpr(_)=>"ContainsExpr",
Self::g_TinyExpressionP4AST_2e_InExpr(_)=>"InExpr",
Self::g_TinyExpressionP4AST_2e_StartsWithDotExpr(_)=>"StartsWithDotExpr",
Self::g_TinyExpressionP4AST_2e_EndsWithDotExpr(_)=>"EndsWithDotExpr",
Self::g_TinyExpressionP4AST_2e_ContainsDotExpr(_)=>"ContainsDotExpr",
Self::g_TinyExpressionP4AST_2e_IsPresentExpr(_)=>"IsPresentExpr",
Self::g_TinyExpressionP4AST_2e_InTimeRangeExpr(_)=>"InTimeRangeExpr",
Self::g_TinyExpressionP4AST_2e_InDayTimeRangeExpr(_)=>"InDayTimeRangeExpr",
Self::g_TinyExpressionP4AST_2e_SliceExpr(_)=>"SliceExpr",
Self::g_TinyExpressionP4AST_2e_StringConcatExpr(_)=>"StringConcatExpr",
Self::g_TinyExpressionP4AST_2e_StringCastVariableRefExpr(_)=>"StringCastVariableRefExpr",
Self::g_TinyExpressionP4AST_2e_StringTypedVariableRefExpr(_)=>"StringTypedVariableRefExpr",
Self::g_TinyExpressionP4AST_2e_BooleanOrExpr(_)=>"BooleanOrExpr",
Self::g_TinyExpressionP4AST_2e_BooleanAndExpr(_)=>"BooleanAndExpr",
Self::g_TinyExpressionP4AST_2e_BooleanXorExpr(_)=>"BooleanXorExpr",
Self::g_TinyExpressionP4AST_2e_NotExpr(_)=>"NotExpr",
Self::g_TinyExpressionP4AST_2e_BooleanEqualityExpr(_)=>"BooleanEqualityExpr",
Self::g_TinyExpressionP4AST_2e_BooleanFactorExpr(_)=>"BooleanFactorExpr",
Self::g_TinyExpressionP4AST_2e_StringComparisonExpr(_)=>"StringComparisonExpr",
Self::g_TinyExpressionP4AST_2e_ComparisonExpr(_)=>"ComparisonExpr",
Self::g_TinyExpressionP4AST_2e_ObjectExpr(_)=>"ObjectExpr",
Self::g_TinyExpressionP4AST_2e_IfExpr(_)=>"IfExpr",
Self::g_TinyExpressionP4AST_2e_BranchExpressionExpr(_)=>"BranchExpressionExpr",
Self::g_TinyExpressionP4AST_2e_NumberMatchExpr(_)=>"NumberMatchExpr",
Self::g_TinyExpressionP4AST_2e_NumberCaseExpr(_)=>"NumberCaseExpr",
Self::g_TinyExpressionP4AST_2e_NumberDefaultCaseExpr(_)=>"NumberDefaultCaseExpr",
Self::g_TinyExpressionP4AST_2e_NumberCaseValueExpr(_)=>"NumberCaseValueExpr",
Self::g_TinyExpressionP4AST_2e_StringMatchExpr(_)=>"StringMatchExpr",
Self::g_TinyExpressionP4AST_2e_StringCaseExpr(_)=>"StringCaseExpr",
Self::g_TinyExpressionP4AST_2e_StringDefaultCaseExpr(_)=>"StringDefaultCaseExpr",
Self::g_TinyExpressionP4AST_2e_StringCaseValueExpr(_)=>"StringCaseValueExpr",
Self::g_TinyExpressionP4AST_2e_BooleanMatchExpr(_)=>"BooleanMatchExpr",
Self::g_TinyExpressionP4AST_2e_BooleanCaseExpr(_)=>"BooleanCaseExpr",
Self::g_TinyExpressionP4AST_2e_BooleanDefaultCaseExpr(_)=>"BooleanDefaultCaseExpr",
Self::g_TinyExpressionP4AST_2e_BooleanCaseValueExpr(_)=>"BooleanCaseValueExpr",
Self::g_TinyExpressionP4AST_2e_VariableRefExpr(_)=>"VariableRefExpr",
Self::g_TinyExpressionP4AST_2e_ExpressionExpr(_)=>"ExpressionExpr",
}}
pub fn type_id(&self)->&'static str {match self {Self::Text(_)=>"text",Self::Null=>"null",
Self::g_TinyExpressionP4AST_2e_FormulaExpr(_)=>"TinyExpressionP4AST.FormulaExpr",
Self::g_TinyExpressionP4AST_2e_CodeBlockExpr(_)=>"TinyExpressionP4AST.CodeBlockExpr",
Self::g_TinyExpressionP4AST_2e_ImportDeclarationExpr(_)=>"TinyExpressionP4AST.ImportDeclarationExpr",
Self::g_TinyExpressionP4AST_2e_QualifiedNameExpr(_)=>"TinyExpressionP4AST.QualifiedNameExpr",
Self::g_TinyExpressionP4AST_2e_NumberVariableDeclarationExpr(_)=>"TinyExpressionP4AST.NumberVariableDeclarationExpr",
Self::g_TinyExpressionP4AST_2e_StringVariableDeclarationExpr(_)=>"TinyExpressionP4AST.StringVariableDeclarationExpr",
Self::g_TinyExpressionP4AST_2e_BooleanVariableDeclarationExpr(_)=>"TinyExpressionP4AST.BooleanVariableDeclarationExpr",
Self::g_TinyExpressionP4AST_2e_ObjectVariableDeclarationExpr(_)=>"TinyExpressionP4AST.ObjectVariableDeclarationExpr",
Self::g_TinyExpressionP4AST_2e_OnlyIfAbsentExpr(_)=>"TinyExpressionP4AST.OnlyIfAbsentExpr",
Self::g_TinyExpressionP4AST_2e_NumberMethodDeclarationExpr(_)=>"TinyExpressionP4AST.NumberMethodDeclarationExpr",
Self::g_TinyExpressionP4AST_2e_StringMethodDeclarationExpr(_)=>"TinyExpressionP4AST.StringMethodDeclarationExpr",
Self::g_TinyExpressionP4AST_2e_BooleanMethodDeclarationExpr(_)=>"TinyExpressionP4AST.BooleanMethodDeclarationExpr",
Self::g_TinyExpressionP4AST_2e_ObjectMethodDeclarationExpr(_)=>"TinyExpressionP4AST.ObjectMethodDeclarationExpr",
Self::g_TinyExpressionP4AST_2e_MethodParametersExpr(_)=>"TinyExpressionP4AST.MethodParametersExpr",
Self::g_TinyExpressionP4AST_2e_MethodParameterExpr(_)=>"TinyExpressionP4AST.MethodParameterExpr",
Self::g_TinyExpressionP4AST_2e_ExternalBooleanInvocationExpr(_)=>"TinyExpressionP4AST.ExternalBooleanInvocationExpr",
Self::g_TinyExpressionP4AST_2e_ExternalNumberInvocationExpr(_)=>"TinyExpressionP4AST.ExternalNumberInvocationExpr",
Self::g_TinyExpressionP4AST_2e_ExternalStringInvocationExpr(_)=>"TinyExpressionP4AST.ExternalStringInvocationExpr",
Self::g_TinyExpressionP4AST_2e_ExternalObjectInvocationExpr(_)=>"TinyExpressionP4AST.ExternalObjectInvocationExpr",
Self::g_TinyExpressionP4AST_2e_MethodInvocationExpr(_)=>"TinyExpressionP4AST.MethodInvocationExpr",
Self::g_TinyExpressionP4AST_2e_TernaryExpr(_)=>"TinyExpressionP4AST.TernaryExpr",
Self::g_TinyExpressionP4AST_2e_ArgumentExpressionExpr(_)=>"TinyExpressionP4AST.ArgumentExpressionExpr",
Self::g_TinyExpressionP4AST_2e_ArgumentsExpr(_)=>"TinyExpressionP4AST.ArgumentsExpr",
Self::g_TinyExpressionP4AST_2e_BinaryExpr(_)=>"TinyExpressionP4AST.BinaryExpr",
Self::g_TinyExpressionP4AST_2e_SinExpr(_)=>"TinyExpressionP4AST.SinExpr",
Self::g_TinyExpressionP4AST_2e_CosExpr(_)=>"TinyExpressionP4AST.CosExpr",
Self::g_TinyExpressionP4AST_2e_TanExpr(_)=>"TinyExpressionP4AST.TanExpr",
Self::g_TinyExpressionP4AST_2e_SqrtExpr(_)=>"TinyExpressionP4AST.SqrtExpr",
Self::g_TinyExpressionP4AST_2e_MinExpr(_)=>"TinyExpressionP4AST.MinExpr",
Self::g_TinyExpressionP4AST_2e_MaxExpr(_)=>"TinyExpressionP4AST.MaxExpr",
Self::g_TinyExpressionP4AST_2e_RandomExpr(_)=>"TinyExpressionP4AST.RandomExpr",
Self::g_TinyExpressionP4AST_2e_AbsExpr(_)=>"TinyExpressionP4AST.AbsExpr",
Self::g_TinyExpressionP4AST_2e_RoundExpr(_)=>"TinyExpressionP4AST.RoundExpr",
Self::g_TinyExpressionP4AST_2e_CeilExpr(_)=>"TinyExpressionP4AST.CeilExpr",
Self::g_TinyExpressionP4AST_2e_FloorExpr(_)=>"TinyExpressionP4AST.FloorExpr",
Self::g_TinyExpressionP4AST_2e_PowExpr(_)=>"TinyExpressionP4AST.PowExpr",
Self::g_TinyExpressionP4AST_2e_LogExpr(_)=>"TinyExpressionP4AST.LogExpr",
Self::g_TinyExpressionP4AST_2e_ExpExpr(_)=>"TinyExpressionP4AST.ExpExpr",
Self::g_TinyExpressionP4AST_2e_ToNumExpr(_)=>"TinyExpressionP4AST.ToNumExpr",
Self::g_TinyExpressionP4AST_2e_ToUpperCaseExpr(_)=>"TinyExpressionP4AST.ToUpperCaseExpr",
Self::g_TinyExpressionP4AST_2e_ToLowerCaseExpr(_)=>"TinyExpressionP4AST.ToLowerCaseExpr",
Self::g_TinyExpressionP4AST_2e_TrimExpr(_)=>"TinyExpressionP4AST.TrimExpr",
Self::g_TinyExpressionP4AST_2e_LengthExpr(_)=>"TinyExpressionP4AST.LengthExpr",
Self::g_TinyExpressionP4AST_2e_ToUpperCaseDotExpr(_)=>"TinyExpressionP4AST.ToUpperCaseDotExpr",
Self::g_TinyExpressionP4AST_2e_ToLowerCaseDotExpr(_)=>"TinyExpressionP4AST.ToLowerCaseDotExpr",
Self::g_TinyExpressionP4AST_2e_TrimDotExpr(_)=>"TinyExpressionP4AST.TrimDotExpr",
Self::g_TinyExpressionP4AST_2e_LengthDotExpr(_)=>"TinyExpressionP4AST.LengthDotExpr",
Self::g_TinyExpressionP4AST_2e_StartsWithExpr(_)=>"TinyExpressionP4AST.StartsWithExpr",
Self::g_TinyExpressionP4AST_2e_EndsWithExpr(_)=>"TinyExpressionP4AST.EndsWithExpr",
Self::g_TinyExpressionP4AST_2e_ContainsExpr(_)=>"TinyExpressionP4AST.ContainsExpr",
Self::g_TinyExpressionP4AST_2e_InExpr(_)=>"TinyExpressionP4AST.InExpr",
Self::g_TinyExpressionP4AST_2e_StartsWithDotExpr(_)=>"TinyExpressionP4AST.StartsWithDotExpr",
Self::g_TinyExpressionP4AST_2e_EndsWithDotExpr(_)=>"TinyExpressionP4AST.EndsWithDotExpr",
Self::g_TinyExpressionP4AST_2e_ContainsDotExpr(_)=>"TinyExpressionP4AST.ContainsDotExpr",
Self::g_TinyExpressionP4AST_2e_IsPresentExpr(_)=>"TinyExpressionP4AST.IsPresentExpr",
Self::g_TinyExpressionP4AST_2e_InTimeRangeExpr(_)=>"TinyExpressionP4AST.InTimeRangeExpr",
Self::g_TinyExpressionP4AST_2e_InDayTimeRangeExpr(_)=>"TinyExpressionP4AST.InDayTimeRangeExpr",
Self::g_TinyExpressionP4AST_2e_SliceExpr(_)=>"TinyExpressionP4AST.SliceExpr",
Self::g_TinyExpressionP4AST_2e_StringConcatExpr(_)=>"TinyExpressionP4AST.StringConcatExpr",
Self::g_TinyExpressionP4AST_2e_StringCastVariableRefExpr(_)=>"TinyExpressionP4AST.StringCastVariableRefExpr",
Self::g_TinyExpressionP4AST_2e_StringTypedVariableRefExpr(_)=>"TinyExpressionP4AST.StringTypedVariableRefExpr",
Self::g_TinyExpressionP4AST_2e_BooleanOrExpr(_)=>"TinyExpressionP4AST.BooleanOrExpr",
Self::g_TinyExpressionP4AST_2e_BooleanAndExpr(_)=>"TinyExpressionP4AST.BooleanAndExpr",
Self::g_TinyExpressionP4AST_2e_BooleanXorExpr(_)=>"TinyExpressionP4AST.BooleanXorExpr",
Self::g_TinyExpressionP4AST_2e_NotExpr(_)=>"TinyExpressionP4AST.NotExpr",
Self::g_TinyExpressionP4AST_2e_BooleanEqualityExpr(_)=>"TinyExpressionP4AST.BooleanEqualityExpr",
Self::g_TinyExpressionP4AST_2e_BooleanFactorExpr(_)=>"TinyExpressionP4AST.BooleanFactorExpr",
Self::g_TinyExpressionP4AST_2e_StringComparisonExpr(_)=>"TinyExpressionP4AST.StringComparisonExpr",
Self::g_TinyExpressionP4AST_2e_ComparisonExpr(_)=>"TinyExpressionP4AST.ComparisonExpr",
Self::g_TinyExpressionP4AST_2e_ObjectExpr(_)=>"TinyExpressionP4AST.ObjectExpr",
Self::g_TinyExpressionP4AST_2e_IfExpr(_)=>"TinyExpressionP4AST.IfExpr",
Self::g_TinyExpressionP4AST_2e_BranchExpressionExpr(_)=>"TinyExpressionP4AST.BranchExpressionExpr",
Self::g_TinyExpressionP4AST_2e_NumberMatchExpr(_)=>"TinyExpressionP4AST.NumberMatchExpr",
Self::g_TinyExpressionP4AST_2e_NumberCaseExpr(_)=>"TinyExpressionP4AST.NumberCaseExpr",
Self::g_TinyExpressionP4AST_2e_NumberDefaultCaseExpr(_)=>"TinyExpressionP4AST.NumberDefaultCaseExpr",
Self::g_TinyExpressionP4AST_2e_NumberCaseValueExpr(_)=>"TinyExpressionP4AST.NumberCaseValueExpr",
Self::g_TinyExpressionP4AST_2e_StringMatchExpr(_)=>"TinyExpressionP4AST.StringMatchExpr",
Self::g_TinyExpressionP4AST_2e_StringCaseExpr(_)=>"TinyExpressionP4AST.StringCaseExpr",
Self::g_TinyExpressionP4AST_2e_StringDefaultCaseExpr(_)=>"TinyExpressionP4AST.StringDefaultCaseExpr",
Self::g_TinyExpressionP4AST_2e_StringCaseValueExpr(_)=>"TinyExpressionP4AST.StringCaseValueExpr",
Self::g_TinyExpressionP4AST_2e_BooleanMatchExpr(_)=>"TinyExpressionP4AST.BooleanMatchExpr",
Self::g_TinyExpressionP4AST_2e_BooleanCaseExpr(_)=>"TinyExpressionP4AST.BooleanCaseExpr",
Self::g_TinyExpressionP4AST_2e_BooleanDefaultCaseExpr(_)=>"TinyExpressionP4AST.BooleanDefaultCaseExpr",
Self::g_TinyExpressionP4AST_2e_BooleanCaseValueExpr(_)=>"TinyExpressionP4AST.BooleanCaseValueExpr",
Self::g_TinyExpressionP4AST_2e_VariableRefExpr(_)=>"TinyExpressionP4AST.VariableRefExpr",
Self::g_TinyExpressionP4AST_2e_ExpressionExpr(_)=>"TinyExpressionP4AST.ExpressionExpr",
}}
#[cfg(feature="json")] pub fn canonical_value(&self)->serde_json::Value { match self { Self::Text(value)=>serde_json::Value::String(value.clone()),Self::Null=>serde_json::Value::Null,
Self::g_TinyExpressionP4AST_2e_FormulaExpr(node)=>serde_json::json!({"fields":{
"declarations":node.g_declarations.iter().map(Ast::canonical_value).collect::<Vec<_>>(),
"expression":node.g_expression.canonical_value(),
"imports":node.g_imports.iter().map(Ast::canonical_value).collect::<Vec<_>>(),
"methods":node.g_methods.iter().map(Ast::canonical_value).collect::<Vec<_>>(),
},"span":node.span,"type":"FormulaExpr"}),
Self::g_TinyExpressionP4AST_2e_CodeBlockExpr(node)=>serde_json::json!({"fields":{
},"span":node.span,"type":"CodeBlockExpr"}),
Self::g_TinyExpressionP4AST_2e_ImportDeclarationExpr(node)=>serde_json::json!({"fields":{
"alias":node.g_alias,
"className":node.g_className.canonical_value(),
"method":node.g_method,
},"span":node.span,"type":"ImportDeclarationExpr"}),
Self::g_TinyExpressionP4AST_2e_QualifiedNameExpr(node)=>serde_json::json!({"fields":{
"head":node.g_head,
"tail":node.g_tail,
},"span":node.span,"type":"QualifiedNameExpr"}),
Self::g_TinyExpressionP4AST_2e_NumberVariableDeclarationExpr(node)=>serde_json::json!({"fields":{
"desc":node.g_desc,
"onlyIfAbsent":node.g_onlyIfAbsent.as_ref().map(|v|v.canonical_value()),
"value":node.g_value.as_ref().map(|v|v.canonical_value()),
"varName":node.g_varName,
},"span":node.span,"type":"NumberVariableDeclarationExpr"}),
Self::g_TinyExpressionP4AST_2e_StringVariableDeclarationExpr(node)=>serde_json::json!({"fields":{
"desc":node.g_desc,
"onlyIfAbsent":node.g_onlyIfAbsent.as_ref().map(|v|v.canonical_value()),
"value":node.g_value.as_ref().map(|v|v.canonical_value()),
"varName":node.g_varName,
},"span":node.span,"type":"StringVariableDeclarationExpr"}),
Self::g_TinyExpressionP4AST_2e_BooleanVariableDeclarationExpr(node)=>serde_json::json!({"fields":{
"desc":node.g_desc,
"onlyIfAbsent":node.g_onlyIfAbsent.as_ref().map(|v|v.canonical_value()),
"value":node.g_value.as_ref().map(|v|v.canonical_value()),
"varName":node.g_varName,
},"span":node.span,"type":"BooleanVariableDeclarationExpr"}),
Self::g_TinyExpressionP4AST_2e_ObjectVariableDeclarationExpr(node)=>serde_json::json!({"fields":{
"desc":node.g_desc,
"onlyIfAbsent":node.g_onlyIfAbsent.as_ref().map(|v|v.canonical_value()),
"value":node.g_value.as_ref().map(|v|v.canonical_value()),
"varName":node.g_varName,
},"span":node.span,"type":"ObjectVariableDeclarationExpr"}),
Self::g_TinyExpressionP4AST_2e_OnlyIfAbsentExpr(node)=>serde_json::json!({"fields":{
},"span":node.span,"type":"OnlyIfAbsentExpr"}),
Self::g_TinyExpressionP4AST_2e_NumberMethodDeclarationExpr(node)=>serde_json::json!({"fields":{
"expression":node.g_expression.canonical_value(),
"methodName":node.g_methodName,
"parameters":node.g_parameters.as_ref().map(|v|v.canonical_value()),
},"span":node.span,"type":"NumberMethodDeclarationExpr"}),
Self::g_TinyExpressionP4AST_2e_StringMethodDeclarationExpr(node)=>serde_json::json!({"fields":{
"expression":node.g_expression.canonical_value(),
"methodName":node.g_methodName,
"parameters":node.g_parameters.as_ref().map(|v|v.canonical_value()),
},"span":node.span,"type":"StringMethodDeclarationExpr"}),
Self::g_TinyExpressionP4AST_2e_BooleanMethodDeclarationExpr(node)=>serde_json::json!({"fields":{
"expression":node.g_expression.canonical_value(),
"methodName":node.g_methodName,
"parameters":node.g_parameters.as_ref().map(|v|v.canonical_value()),
},"span":node.span,"type":"BooleanMethodDeclarationExpr"}),
Self::g_TinyExpressionP4AST_2e_ObjectMethodDeclarationExpr(node)=>serde_json::json!({"fields":{
"expression":node.g_expression.canonical_value(),
"methodName":node.g_methodName,
"parameters":node.g_parameters.as_ref().map(|v|v.canonical_value()),
},"span":node.span,"type":"ObjectMethodDeclarationExpr"}),
Self::g_TinyExpressionP4AST_2e_MethodParametersExpr(node)=>serde_json::json!({"fields":{
"values":node.g_values.iter().map(Ast::canonical_value).collect::<Vec<_>>(),
},"span":node.span,"type":"MethodParametersExpr"}),
Self::g_TinyExpressionP4AST_2e_MethodParameterExpr(node)=>serde_json::json!({"fields":{
"paramName":node.g_paramName,
"type":node.g_type,
},"span":node.span,"type":"MethodParameterExpr"}),
Self::g_TinyExpressionP4AST_2e_ExternalBooleanInvocationExpr(node)=>serde_json::json!({"fields":{
"args":node.g_args.as_ref().map(|v|v.canonical_value()),
"className":node.g_className.as_ref().map(|v|v.canonical_value()),
"name":node.g_name,
},"span":node.span,"type":"ExternalBooleanInvocationExpr"}),
Self::g_TinyExpressionP4AST_2e_ExternalNumberInvocationExpr(node)=>serde_json::json!({"fields":{
"args":node.g_args.as_ref().map(|v|v.canonical_value()),
"className":node.g_className.as_ref().map(|v|v.canonical_value()),
"name":node.g_name,
},"span":node.span,"type":"ExternalNumberInvocationExpr"}),
Self::g_TinyExpressionP4AST_2e_ExternalStringInvocationExpr(node)=>serde_json::json!({"fields":{
"args":node.g_args.as_ref().map(|v|v.canonical_value()),
"className":node.g_className.as_ref().map(|v|v.canonical_value()),
"name":node.g_name,
},"span":node.span,"type":"ExternalStringInvocationExpr"}),
Self::g_TinyExpressionP4AST_2e_ExternalObjectInvocationExpr(node)=>serde_json::json!({"fields":{
"args":node.g_args.as_ref().map(|v|v.canonical_value()),
"className":node.g_className.as_ref().map(|v|v.canonical_value()),
"name":node.g_name,
},"span":node.span,"type":"ExternalObjectInvocationExpr"}),
Self::g_TinyExpressionP4AST_2e_MethodInvocationExpr(node)=>serde_json::json!({"fields":{
"args":node.g_args.as_ref().map(|v|v.canonical_value()),
"name":node.g_name,
},"span":node.span,"type":"MethodInvocationExpr"}),
Self::g_TinyExpressionP4AST_2e_TernaryExpr(node)=>serde_json::json!({"fields":{
"condition":node.g_condition.canonical_value(),
"elseExpr":node.g_elseExpr.canonical_value(),
"thenExpr":node.g_thenExpr.canonical_value(),
},"span":node.span,"type":"TernaryExpr"}),
Self::g_TinyExpressionP4AST_2e_ArgumentExpressionExpr(node)=>serde_json::json!({"fields":{
"value":node.g_value.canonical_value(),
},"span":node.span,"type":"ArgumentExpressionExpr"}),
Self::g_TinyExpressionP4AST_2e_ArgumentsExpr(node)=>serde_json::json!({"fields":{
"values":node.g_values.iter().map(Ast::canonical_value).collect::<Vec<_>>(),
},"span":node.span,"type":"ArgumentsExpr"}),
Self::g_TinyExpressionP4AST_2e_BinaryExpr(node)=>serde_json::json!({"fields":{
"left":node.g_left.as_ref().map(|v|v.canonical_value()),
"op":node.g_op,
"right":node.g_right.iter().map(Ast::canonical_value).collect::<Vec<_>>(),
},"span":node.span,"type":"BinaryExpr"}),
Self::g_TinyExpressionP4AST_2e_SinExpr(node)=>serde_json::json!({"fields":{
"arg":node.g_arg.canonical_value(),
},"span":node.span,"type":"SinExpr"}),
Self::g_TinyExpressionP4AST_2e_CosExpr(node)=>serde_json::json!({"fields":{
"arg":node.g_arg.canonical_value(),
},"span":node.span,"type":"CosExpr"}),
Self::g_TinyExpressionP4AST_2e_TanExpr(node)=>serde_json::json!({"fields":{
"arg":node.g_arg.canonical_value(),
},"span":node.span,"type":"TanExpr"}),
Self::g_TinyExpressionP4AST_2e_SqrtExpr(node)=>serde_json::json!({"fields":{
"arg":node.g_arg.canonical_value(),
},"span":node.span,"type":"SqrtExpr"}),
Self::g_TinyExpressionP4AST_2e_MinExpr(node)=>serde_json::json!({"fields":{
"first":node.g_first.canonical_value(),
"rest":node.g_rest.iter().map(Ast::canonical_value).collect::<Vec<_>>(),
},"span":node.span,"type":"MinExpr"}),
Self::g_TinyExpressionP4AST_2e_MaxExpr(node)=>serde_json::json!({"fields":{
"first":node.g_first.canonical_value(),
"rest":node.g_rest.iter().map(Ast::canonical_value).collect::<Vec<_>>(),
},"span":node.span,"type":"MaxExpr"}),
Self::g_TinyExpressionP4AST_2e_RandomExpr(node)=>serde_json::json!({"fields":{
},"span":node.span,"type":"RandomExpr"}),
Self::g_TinyExpressionP4AST_2e_AbsExpr(node)=>serde_json::json!({"fields":{
"arg":node.g_arg.canonical_value(),
},"span":node.span,"type":"AbsExpr"}),
Self::g_TinyExpressionP4AST_2e_RoundExpr(node)=>serde_json::json!({"fields":{
"arg":node.g_arg.canonical_value(),
},"span":node.span,"type":"RoundExpr"}),
Self::g_TinyExpressionP4AST_2e_CeilExpr(node)=>serde_json::json!({"fields":{
"arg":node.g_arg.canonical_value(),
},"span":node.span,"type":"CeilExpr"}),
Self::g_TinyExpressionP4AST_2e_FloorExpr(node)=>serde_json::json!({"fields":{
"arg":node.g_arg.canonical_value(),
},"span":node.span,"type":"FloorExpr"}),
Self::g_TinyExpressionP4AST_2e_PowExpr(node)=>serde_json::json!({"fields":{
"base":node.g_base.canonical_value(),
"exponent":node.g_exponent.canonical_value(),
},"span":node.span,"type":"PowExpr"}),
Self::g_TinyExpressionP4AST_2e_LogExpr(node)=>serde_json::json!({"fields":{
"arg":node.g_arg.canonical_value(),
},"span":node.span,"type":"LogExpr"}),
Self::g_TinyExpressionP4AST_2e_ExpExpr(node)=>serde_json::json!({"fields":{
"arg":node.g_arg.canonical_value(),
},"span":node.span,"type":"ExpExpr"}),
Self::g_TinyExpressionP4AST_2e_ToNumExpr(node)=>serde_json::json!({"fields":{
"defaultValue":node.g_defaultValue.canonical_value(),
"value":node.g_value.canonical_value(),
},"span":node.span,"type":"ToNumExpr"}),
Self::g_TinyExpressionP4AST_2e_ToUpperCaseExpr(node)=>serde_json::json!({"fields":{
"value":node.g_value.canonical_value(),
},"span":node.span,"type":"ToUpperCaseExpr"}),
Self::g_TinyExpressionP4AST_2e_ToLowerCaseExpr(node)=>serde_json::json!({"fields":{
"value":node.g_value.canonical_value(),
},"span":node.span,"type":"ToLowerCaseExpr"}),
Self::g_TinyExpressionP4AST_2e_TrimExpr(node)=>serde_json::json!({"fields":{
"value":node.g_value.canonical_value(),
},"span":node.span,"type":"TrimExpr"}),
Self::g_TinyExpressionP4AST_2e_LengthExpr(node)=>serde_json::json!({"fields":{
"value":node.g_value.canonical_value(),
},"span":node.span,"type":"LengthExpr"}),
Self::g_TinyExpressionP4AST_2e_ToUpperCaseDotExpr(node)=>serde_json::json!({"fields":{
"value":node.g_value.canonical_value(),
},"span":node.span,"type":"ToUpperCaseDotExpr"}),
Self::g_TinyExpressionP4AST_2e_ToLowerCaseDotExpr(node)=>serde_json::json!({"fields":{
"value":node.g_value.canonical_value(),
},"span":node.span,"type":"ToLowerCaseDotExpr"}),
Self::g_TinyExpressionP4AST_2e_TrimDotExpr(node)=>serde_json::json!({"fields":{
"value":node.g_value.canonical_value(),
},"span":node.span,"type":"TrimDotExpr"}),
Self::g_TinyExpressionP4AST_2e_LengthDotExpr(node)=>serde_json::json!({"fields":{
"value":node.g_value.canonical_value(),
},"span":node.span,"type":"LengthDotExpr"}),
Self::g_TinyExpressionP4AST_2e_StartsWithExpr(node)=>serde_json::json!({"fields":{
"patterns":node.g_patterns.iter().map(Ast::canonical_value).collect::<Vec<_>>(),
"value":node.g_value.canonical_value(),
},"span":node.span,"type":"StartsWithExpr"}),
Self::g_TinyExpressionP4AST_2e_EndsWithExpr(node)=>serde_json::json!({"fields":{
"patterns":node.g_patterns.iter().map(Ast::canonical_value).collect::<Vec<_>>(),
"value":node.g_value.canonical_value(),
},"span":node.span,"type":"EndsWithExpr"}),
Self::g_TinyExpressionP4AST_2e_ContainsExpr(node)=>serde_json::json!({"fields":{
"patterns":node.g_patterns.iter().map(Ast::canonical_value).collect::<Vec<_>>(),
"value":node.g_value.canonical_value(),
},"span":node.span,"type":"ContainsExpr"}),
Self::g_TinyExpressionP4AST_2e_InExpr(node)=>serde_json::json!({"fields":{
"candidates":node.g_candidates.iter().map(Ast::canonical_value).collect::<Vec<_>>(),
"value":node.g_value.canonical_value(),
},"span":node.span,"type":"InExpr"}),
Self::g_TinyExpressionP4AST_2e_StartsWithDotExpr(node)=>serde_json::json!({"fields":{
"patterns":node.g_patterns.iter().map(Ast::canonical_value).collect::<Vec<_>>(),
"value":node.g_value.canonical_value(),
},"span":node.span,"type":"StartsWithDotExpr"}),
Self::g_TinyExpressionP4AST_2e_EndsWithDotExpr(node)=>serde_json::json!({"fields":{
"patterns":node.g_patterns.iter().map(Ast::canonical_value).collect::<Vec<_>>(),
"value":node.g_value.canonical_value(),
},"span":node.span,"type":"EndsWithDotExpr"}),
Self::g_TinyExpressionP4AST_2e_ContainsDotExpr(node)=>serde_json::json!({"fields":{
"patterns":node.g_patterns.iter().map(Ast::canonical_value).collect::<Vec<_>>(),
"value":node.g_value.canonical_value(),
},"span":node.span,"type":"ContainsDotExpr"}),
Self::g_TinyExpressionP4AST_2e_IsPresentExpr(node)=>serde_json::json!({"fields":{
"value":node.g_value.canonical_value(),
},"span":node.span,"type":"IsPresentExpr"}),
Self::g_TinyExpressionP4AST_2e_InTimeRangeExpr(node)=>serde_json::json!({"fields":{
"endHour":node.g_endHour.canonical_value(),
"startHour":node.g_startHour.canonical_value(),
},"span":node.span,"type":"InTimeRangeExpr"}),
Self::g_TinyExpressionP4AST_2e_InDayTimeRangeExpr(node)=>serde_json::json!({"fields":{
"endDay":node.g_endDay,
"endHour":node.g_endHour.canonical_value(),
"startDay":node.g_startDay,
"startHour":node.g_startHour.canonical_value(),
},"span":node.span,"type":"InDayTimeRangeExpr"}),
Self::g_TinyExpressionP4AST_2e_SliceExpr(node)=>serde_json::json!({"fields":{
"end":node.g_end.as_ref().map(|v|v.canonical_value()),
"start":node.g_start.as_ref().map(|v|v.canonical_value()),
"step":node.g_step.as_ref().map(|v|v.canonical_value()),
"value":node.g_value.canonical_value(),
},"span":node.span,"type":"SliceExpr"}),
Self::g_TinyExpressionP4AST_2e_StringConcatExpr(node)=>serde_json::json!({"fields":{
"left":node.g_left.canonical_value(),
"op":node.g_op,
"right":node.g_right.iter().map(Ast::canonical_value).collect::<Vec<_>>(),
},"span":node.span,"type":"StringConcatExpr"}),
Self::g_TinyExpressionP4AST_2e_StringCastVariableRefExpr(node)=>serde_json::json!({"fields":{
"name":node.g_name,
},"span":node.span,"type":"StringCastVariableRefExpr"}),
Self::g_TinyExpressionP4AST_2e_StringTypedVariableRefExpr(node)=>serde_json::json!({"fields":{
"name":node.g_name,
},"span":node.span,"type":"StringTypedVariableRefExpr"}),
Self::g_TinyExpressionP4AST_2e_BooleanOrExpr(node)=>serde_json::json!({"fields":{
"left":node.g_left.canonical_value(),
"op":node.g_op,
"right":node.g_right.iter().map(Ast::canonical_value).collect::<Vec<_>>(),
},"span":node.span,"type":"BooleanOrExpr"}),
Self::g_TinyExpressionP4AST_2e_BooleanAndExpr(node)=>serde_json::json!({"fields":{
"left":node.g_left.canonical_value(),
"op":node.g_op,
"right":node.g_right.iter().map(Ast::canonical_value).collect::<Vec<_>>(),
},"span":node.span,"type":"BooleanAndExpr"}),
Self::g_TinyExpressionP4AST_2e_BooleanXorExpr(node)=>serde_json::json!({"fields":{
"left":node.g_left.canonical_value(),
"op":node.g_op,
"right":node.g_right.iter().map(Ast::canonical_value).collect::<Vec<_>>(),
},"span":node.span,"type":"BooleanXorExpr"}),
Self::g_TinyExpressionP4AST_2e_NotExpr(node)=>serde_json::json!({"fields":{
"value":node.g_value.canonical_value(),
},"span":node.span,"type":"NotExpr"}),
Self::g_TinyExpressionP4AST_2e_BooleanEqualityExpr(node)=>serde_json::json!({"fields":{
"left":node.g_left.canonical_value(),
"op":node.g_op,
"right":node.g_right.canonical_value(),
},"span":node.span,"type":"BooleanEqualityExpr"}),
Self::g_TinyExpressionP4AST_2e_BooleanFactorExpr(node)=>serde_json::json!({"fields":{
"value":node.g_value.canonical_value(),
},"span":node.span,"type":"BooleanFactorExpr"}),
Self::g_TinyExpressionP4AST_2e_StringComparisonExpr(node)=>serde_json::json!({"fields":{
"left":node.g_left.canonical_value(),
"op":node.g_op,
"right":node.g_right.canonical_value(),
},"span":node.span,"type":"StringComparisonExpr"}),
Self::g_TinyExpressionP4AST_2e_ComparisonExpr(node)=>serde_json::json!({"fields":{
"left":node.g_left.canonical_value(),
"op":node.g_op,
"right":node.g_right.canonical_value(),
},"span":node.span,"type":"ComparisonExpr"}),
Self::g_TinyExpressionP4AST_2e_ObjectExpr(node)=>serde_json::json!({"fields":{
"value":node.g_value.canonical_value(),
},"span":node.span,"type":"ObjectExpr"}),
Self::g_TinyExpressionP4AST_2e_IfExpr(node)=>serde_json::json!({"fields":{
"condition":node.g_condition.canonical_value(),
"elseExpr":node.g_elseExpr.canonical_value(),
"thenExpr":node.g_thenExpr.canonical_value(),
},"span":node.span,"type":"IfExpr"}),
Self::g_TinyExpressionP4AST_2e_BranchExpressionExpr(node)=>serde_json::json!({"fields":{
"value":node.g_value.canonical_value(),
},"span":node.span,"type":"BranchExpressionExpr"}),
Self::g_TinyExpressionP4AST_2e_NumberMatchExpr(node)=>serde_json::json!({"fields":{
"defaultCase":node.g_defaultCase.canonical_value(),
"firstCase":node.g_firstCase.canonical_value(),
"moreCases":node.g_moreCases.iter().map(Ast::canonical_value).collect::<Vec<_>>(),
},"span":node.span,"type":"NumberMatchExpr"}),
Self::g_TinyExpressionP4AST_2e_NumberCaseExpr(node)=>serde_json::json!({"fields":{
"condition":node.g_condition.canonical_value(),
"value":node.g_value.canonical_value(),
},"span":node.span,"type":"NumberCaseExpr"}),
Self::g_TinyExpressionP4AST_2e_NumberDefaultCaseExpr(node)=>serde_json::json!({"fields":{
"value":node.g_value.canonical_value(),
},"span":node.span,"type":"NumberDefaultCaseExpr"}),
Self::g_TinyExpressionP4AST_2e_NumberCaseValueExpr(node)=>serde_json::json!({"fields":{
"value":node.g_value.canonical_value(),
},"span":node.span,"type":"NumberCaseValueExpr"}),
Self::g_TinyExpressionP4AST_2e_StringMatchExpr(node)=>serde_json::json!({"fields":{
"defaultCase":node.g_defaultCase.canonical_value(),
"firstCase":node.g_firstCase.canonical_value(),
"moreCases":node.g_moreCases.iter().map(Ast::canonical_value).collect::<Vec<_>>(),
},"span":node.span,"type":"StringMatchExpr"}),
Self::g_TinyExpressionP4AST_2e_StringCaseExpr(node)=>serde_json::json!({"fields":{
"condition":node.g_condition.canonical_value(),
"value":node.g_value.canonical_value(),
},"span":node.span,"type":"StringCaseExpr"}),
Self::g_TinyExpressionP4AST_2e_StringDefaultCaseExpr(node)=>serde_json::json!({"fields":{
"value":node.g_value.canonical_value(),
},"span":node.span,"type":"StringDefaultCaseExpr"}),
Self::g_TinyExpressionP4AST_2e_StringCaseValueExpr(node)=>serde_json::json!({"fields":{
"value":node.g_value.canonical_value(),
},"span":node.span,"type":"StringCaseValueExpr"}),
Self::g_TinyExpressionP4AST_2e_BooleanMatchExpr(node)=>serde_json::json!({"fields":{
"defaultCase":node.g_defaultCase.canonical_value(),
"firstCase":node.g_firstCase.canonical_value(),
"moreCases":node.g_moreCases.iter().map(Ast::canonical_value).collect::<Vec<_>>(),
},"span":node.span,"type":"BooleanMatchExpr"}),
Self::g_TinyExpressionP4AST_2e_BooleanCaseExpr(node)=>serde_json::json!({"fields":{
"condition":node.g_condition.canonical_value(),
"value":node.g_value.canonical_value(),
},"span":node.span,"type":"BooleanCaseExpr"}),
Self::g_TinyExpressionP4AST_2e_BooleanDefaultCaseExpr(node)=>serde_json::json!({"fields":{
"value":node.g_value.canonical_value(),
},"span":node.span,"type":"BooleanDefaultCaseExpr"}),
Self::g_TinyExpressionP4AST_2e_BooleanCaseValueExpr(node)=>serde_json::json!({"fields":{
"value":node.g_value.canonical_value(),
},"span":node.span,"type":"BooleanCaseValueExpr"}),
Self::g_TinyExpressionP4AST_2e_VariableRefExpr(node)=>serde_json::json!({"fields":{
"name":node.g_name,
"type":node.g_type,
},"span":node.span,"type":"VariableRefExpr"}),
Self::g_TinyExpressionP4AST_2e_ExpressionExpr(node)=>serde_json::json!({"fields":{
"value":node.g_value.canonical_value(),
},"span":node.span,"type":"ExpressionExpr"}),
}}
#[cfg(feature="json")] pub fn canonical_json(&self)->String {serde_json::to_string(&self.canonical_value()).expect("finite canonical AST")}
}
#[cfg(feature="json")] impl serde::Serialize for Ast {fn serialize<S:serde::Serializer>(&self,serializer:S)->Result<S::Ok,S::Error> {self.canonical_value().serialize(serializer)}}
/// D-077: arena 形の AST。所有 `Ast` はこの木から写す。
pub mod tree {
#![allow(non_camel_case_types, non_snake_case, non_upper_case_globals, dead_code, clippy::all)]
// D-077: 採用結果の AST を arena（SoA）で持つ形。所有 `Ast` は常にこの木から写す。
//
// - 節点は `nodes: Vec<TreeNode>`（24 byte）に作った順で並ぶ。子は `slots: Vec<u32>` の添字で指す。
// - 文字列は持たない。Text 節点は入力の byte 範囲 `[a, b)` で、値は常に入力の部分文字列
//   （semantic text は trim 済み範囲か token の content 範囲で、どちらも入力の中にある）。
// - record の field は宣言順に slot を持つ: scalar / optional は 1 slot（空は `NONE`）、
//   list は 2 slot（`slots` 内の開始位置と個数。要素は record より先に `slots` へ積む）。
// - `#[doc(hidden)] pub` の構築用メソッドは生成 runtime 専用（driver が runtime を別 crate へ
//   `#[path]` で取り込むため crate 内可視にできない）。利用者は呼ばない。
// - 作ったが採用されなかった節点（値の包み直しで捨てた Text 等）も `nodes` に残る。
//   根から辿れないだけで観測には出ない（所有 `Ast` と同じ）。
use super::super::api::{NodeSpan, Span, ValueSpan};
/// 空の slot（optional の無値）と、根の無い木。
pub const NONE: u32 = u32::MAX;
pub const KIND_TEXT: u16 = 0;
pub const KIND_NULL: u16 = 1;
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) struct TreeNode {
    kind: u16,
    /// 節点を作った規則（`RULE_IDS` の添字）。Text / Null は 0。
    rule: u16,
    /// 型付き節点の作成順（`NodeSpan::node_id`）。Text / Null は 0。
    node_id: u32,
    /// code point の範囲。Text は値の出自（extent）。
    span: [u32; 2],
    /// Text: byte 範囲。record: 最初の slot。enum: variant の番号。
    a: u32,
    b: u32,
}
/// 1 回の parse の AST を arena に持つ木（`ParseOptions::ast_tree`）。
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct AstTree {
    source: String,
    nodes: Vec<TreeNode>,
    slots: Vec<u32>,
    typed: u32,
    root: u32,
}
impl Default for AstTree {
    fn default() -> Self {
        Self {
            source: String::new(),
            nodes: Vec::new(),
            slots: Vec::new(),
            typed: 0,
            root: NONE,
        }
    }
}
fn to_u32(value: usize) -> u32 {
    u32::try_from(value).expect("AST tree offsets fit in u32 (input_bytes <= u32::MAX)")
}
fn span_of(node: &TreeNode) -> Span {
    [node.span[0] as usize, node.span[1] as usize]
}
impl AstTree {
    /// 解析した入力（Text 節点の値はこの部分文字列）。
    pub fn source(&self) -> &str {
        &self.source
    }
    pub fn root(&self) -> Option<NodeRef<'_>> {
        (self.root != NONE).then_some(NodeRef {
            tree: self,
            id: self.root,
        })
    }
    /// 任意の節点（`NodeRef::id` の値）。範囲外は `None`。
    pub fn node(&self, id: u32) -> Option<NodeRef<'_>> {
        ((id as usize) < self.nodes.len()).then_some(NodeRef { tree: self, id })
    }
    /// arena の節点数（Text と、根から辿れない節点を含む）。
    pub fn node_count(&self) -> usize {
        self.nodes.len()
    }
    /// 子の参照に使っている slot（u32）の数。
    pub fn slot_count(&self) -> usize {
        self.slots.len()
    }
    /// 型付き節点の数（`Statistics::ast_nodes` と同じ）。
    pub fn typed_node_count(&self) -> usize {
        self.typed as usize
    }
    /// 所有 `Ast` へ写す（`ParseOptions::build_ast` が作るものと同じ値）。
    pub fn to_ast(&self) -> Option<super::Ast> {
        self.root().map(|root| root.to_ast())
    }
    /// `ParseResult::node_spans` と同じ表（作成順）。
    pub fn node_spans(&self) -> Vec<NodeSpan> {
        let mut out = Vec::with_capacity(self.typed as usize);
        self.push_node_spans(&mut out);
        out
    }
    /// `ParseOptions::value_spans` と同じ表（canonical AST の JSON pointer 順）。
    pub fn value_spans(&self) -> Vec<ValueSpan> {
        let mut out = Vec::new();
        if self.root != NONE {
            let mut path = String::with_capacity(128);
            self.collect_value_spans(&self.source, self.root, &mut path, &mut out);
        }
        out
    }
    /// この木が確保している byte 数（容量ベース）。
    pub fn heap_bytes(&self) -> usize {
        self.source.capacity()
            + self.nodes.capacity() * std::mem::size_of::<TreeNode>()
            + self.slots.capacity() * std::mem::size_of::<u32>()
    }
    #[doc(hidden)]
    pub fn push_node_spans(&self, out: &mut Vec<NodeSpan>) {
        for node in &self.nodes {
            if node.kind > KIND_NULL {
                out.push(NodeSpan {
                    node_id: node.node_id as usize,
                    rule_id: RULE_IDS[node.rule as usize],
                    node_type: TYPE_NAMES[node.kind as usize],
                    span: span_of(node),
                });
            }
        }
    }
    #[doc(hidden)]
    pub fn clear(&mut self) {
        self.source.clear();
        self.nodes.clear();
        self.slots.clear();
        self.typed = 0;
        self.root = NONE;
    }
    #[doc(hidden)]
    pub fn reserve(&mut self, nodes: usize, slots: usize) {
        self.nodes.reserve(nodes);
        self.slots.reserve(slots);
    }
    #[doc(hidden)]
    pub fn set_root(&mut self, root: u32) {
        self.root = root;
    }
    #[doc(hidden)]
    pub fn set_source(&mut self, source: &str) {
        self.source.clear();
        self.source.push_str(source);
    }
    #[inline]
    fn push(&mut self, node: TreeNode) -> u32 {
        let id = to_u32(self.nodes.len());
        self.nodes.push(node);
        id
    }
    /// 入力 `[bytes[0], bytes[1])` を値とする Text 節点。`extent` は値の出自（code point）。
    #[inline]
    #[doc(hidden)]
    pub fn text(&mut self, extent: Span, bytes: [usize; 2]) -> u32 {
        self.push(TreeNode {
            kind: KIND_TEXT,
            rule: 0,
            node_id: 0,
            span: [to_u32(extent[0]), to_u32(extent[1])],
            a: to_u32(bytes[0]),
            b: to_u32(bytes[1]),
        })
    }
    /// Text の出自を付け直す（leaf 型へ昇格した Text は capture の範囲を出自にする）。
    #[doc(hidden)]
    pub fn set_extent(&mut self, id: u32, extent: Span) {
        self.nodes[id as usize].span = [to_u32(extent[0]), to_u32(extent[1])];
    }
    /// list field の要素を積み、record の 2 slot（開始, 個数）を返す。
    #[inline]
    #[doc(hidden)]
    pub fn list(&mut self, items: &[u32]) -> [u32; 2] {
        let start = to_u32(self.slots.len());
        self.slots.extend_from_slice(items);
        [start, to_u32(items.len())]
    }
    #[inline]
    #[doc(hidden)]
    pub fn record(&mut self, kind: u16, rule: u16, span: Span, fields: &[u32]) -> u32 {
        let first = to_u32(self.slots.len());
        self.slots.extend_from_slice(fields);
        let node_id = self.typed;
        self.typed += 1;
        self.push(TreeNode {
            kind,
            rule,
            node_id,
            span: [to_u32(span[0]), to_u32(span[1])],
            a: first,
            b: to_u32(fields.len()),
        })
    }
    #[inline]
    #[doc(hidden)]
    pub fn enumeration(&mut self, kind: u16, rule: u16, span: Span, value: u32) -> u32 {
        let node_id = self.typed;
        self.typed += 1;
        self.push(TreeNode {
            kind,
            rule,
            node_id,
            span: [to_u32(span[0]), to_u32(span[1])],
            a: value,
            b: 0,
        })
    }
    #[inline]
    #[doc(hidden)]
    pub fn kind(&self, id: u32) -> u16 {
        self.nodes[id as usize].kind
    }
    #[doc(hidden)]
    pub fn type_id_of(&self, id: u32) -> &'static str {
        TYPE_IDS[self.kind(id) as usize]
    }
    #[doc(hidden)]
    pub fn type_name_of(&self, id: u32) -> &'static str {
        TYPE_NAMES[self.kind(id) as usize]
    }
    #[inline]
    fn slot(&self, id: u32, index: usize) -> u32 {
        self.slots[self.nodes[id as usize].a as usize + index]
    }
    #[inline]
    fn items(&self, id: u32, index: usize) -> &[u32] {
        let first = self.nodes[id as usize].a as usize + index;
        let (start, len) = (self.slots[first] as usize, self.slots[first + 1] as usize);
        &self.slots[start..start + len]
    }
    /// list field の要素（`slots` の開始位置と個数）。
    #[inline]
    fn list_at(&self, start: u32, len: u32) -> &[u32] {
        &self.slots[start as usize..start as usize + len as usize]
    }
    #[inline]
    fn text_in<'s>(&self, source: &'s str, id: u32) -> &'s str {
        let node = &self.nodes[id as usize];
        &source[node.a as usize..node.b as usize]
    }
    fn text_value_span(&self, id: u32, path: &str, out: &mut Vec<ValueSpan>) {
        out.push(ValueSpan {
            path: path.to_owned(),
            span: span_of(&self.nodes[id as usize]),
            text: None,
        });
    }
}
/// 木の 1 節点（Text / Null / 型付き節点）。
#[derive(Clone, Copy)]
pub struct NodeRef<'t> {
    tree: &'t AstTree,
    id: u32,
}
impl PartialEq for NodeRef<'_> {
    fn eq(&self, other: &Self) -> bool {
        std::ptr::eq(self.tree, other.tree) && self.id == other.id
    }
}
impl Eq for NodeRef<'_> {}
impl std::fmt::Debug for NodeRef<'_> {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("NodeRef")
            .field("id", &self.id)
            .field("type", &self.type_name())
            .finish()
    }
}
impl<'t> NodeRef<'t> {
    pub fn id(&self) -> u32 {
        self.id
    }
    pub fn tree(&self) -> &'t AstTree {
        self.tree
    }
    /// 型付き節点の範囲（code point）。Text / Null は `None`（`Ast::span` と同じ）。
    pub fn span(&self) -> Option<Span> {
        let node = &self.tree.nodes[self.id as usize];
        (node.kind > KIND_NULL).then(|| span_of(node))
    }
    pub fn type_name(&self) -> &'static str {
        self.tree.type_name_of(self.id)
    }
    pub fn type_id(&self) -> &'static str {
        self.tree.type_id_of(self.id)
    }
    /// Text 節点の値（入力の部分文字列、複製しない）。
    pub fn text(&self) -> Option<&'t str> {
        (self.tree.kind(self.id) == KIND_TEXT)
            .then(|| self.tree.text_in(&self.tree.source, self.id))
    }
    pub fn is_null(&self) -> bool {
        self.tree.kind(self.id) == KIND_NULL
    }
    pub fn to_ast(&self) -> super::Ast {
        self.tree.project(&self.tree.source, self.id)
    }
}
/// list field（要素は節点）。
#[derive(Clone, Copy)]
pub struct NodeList<'t> {
    tree: &'t AstTree,
    ids: &'t [u32],
}
impl std::fmt::Debug for NodeList<'_> {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_list().entries(self.iter()).finish()
    }
}
impl<'t> NodeList<'t> {
    pub fn len(&self) -> usize {
        self.ids.len()
    }
    pub fn is_empty(&self) -> bool {
        self.ids.is_empty()
    }
    pub fn get(&self, index: usize) -> Option<NodeRef<'t>> {
        let tree = self.tree;
        self.ids.get(index).map(|&id| NodeRef { tree, id })
    }
    pub fn iter(&self) -> impl ExactSizeIterator<Item = NodeRef<'t>> + 't {
        let tree = self.tree;
        self.ids.iter().map(move |&id| NodeRef { tree, id })
    }
}
/// list field（要素は文字列。入力の部分文字列を返す）。
#[derive(Clone, Copy)]
pub struct TextList<'t> {
    tree: &'t AstTree,
    ids: &'t [u32],
}
impl std::fmt::Debug for TextList<'_> {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_list().entries(self.iter()).finish()
    }
}
impl<'t> TextList<'t> {
    pub fn len(&self) -> usize {
        self.ids.len()
    }
    pub fn is_empty(&self) -> bool {
        self.ids.is_empty()
    }
    pub fn get(&self, index: usize) -> Option<&'t str> {
        let tree = self.tree;
        self.ids
            .get(index)
            .map(|&id| tree.text_in(&tree.source, id))
    }
    pub fn iter(&self) -> impl ExactSizeIterator<Item = &'t str> + 't {
        let tree = self.tree;
        self.ids
            .iter()
            .map(move |&id| tree.text_in(&tree.source, id))
    }
}
pub const K_g_TinyExpressionP4AST_2e_FormulaExpr: u16 = 2;
pub const K_g_TinyExpressionP4AST_2e_CodeBlockExpr: u16 = 3;
pub const K_g_TinyExpressionP4AST_2e_ImportDeclarationExpr: u16 = 4;
pub const K_g_TinyExpressionP4AST_2e_QualifiedNameExpr: u16 = 5;
pub const K_g_TinyExpressionP4AST_2e_NumberVariableDeclarationExpr: u16 = 6;
pub const K_g_TinyExpressionP4AST_2e_StringVariableDeclarationExpr: u16 = 7;
pub const K_g_TinyExpressionP4AST_2e_BooleanVariableDeclarationExpr: u16 = 8;
pub const K_g_TinyExpressionP4AST_2e_ObjectVariableDeclarationExpr: u16 = 9;
pub const K_g_TinyExpressionP4AST_2e_OnlyIfAbsentExpr: u16 = 10;
pub const K_g_TinyExpressionP4AST_2e_NumberMethodDeclarationExpr: u16 = 11;
pub const K_g_TinyExpressionP4AST_2e_StringMethodDeclarationExpr: u16 = 12;
pub const K_g_TinyExpressionP4AST_2e_BooleanMethodDeclarationExpr: u16 = 13;
pub const K_g_TinyExpressionP4AST_2e_ObjectMethodDeclarationExpr: u16 = 14;
pub const K_g_TinyExpressionP4AST_2e_MethodParametersExpr: u16 = 15;
pub const K_g_TinyExpressionP4AST_2e_MethodParameterExpr: u16 = 16;
pub const K_g_TinyExpressionP4AST_2e_ExternalBooleanInvocationExpr: u16 = 17;
pub const K_g_TinyExpressionP4AST_2e_ExternalNumberInvocationExpr: u16 = 18;
pub const K_g_TinyExpressionP4AST_2e_ExternalStringInvocationExpr: u16 = 19;
pub const K_g_TinyExpressionP4AST_2e_ExternalObjectInvocationExpr: u16 = 20;
pub const K_g_TinyExpressionP4AST_2e_MethodInvocationExpr: u16 = 21;
pub const K_g_TinyExpressionP4AST_2e_TernaryExpr: u16 = 22;
pub const K_g_TinyExpressionP4AST_2e_ArgumentExpressionExpr: u16 = 23;
pub const K_g_TinyExpressionP4AST_2e_ArgumentsExpr: u16 = 24;
pub const K_g_TinyExpressionP4AST_2e_BinaryExpr: u16 = 25;
pub const K_g_TinyExpressionP4AST_2e_SinExpr: u16 = 26;
pub const K_g_TinyExpressionP4AST_2e_CosExpr: u16 = 27;
pub const K_g_TinyExpressionP4AST_2e_TanExpr: u16 = 28;
pub const K_g_TinyExpressionP4AST_2e_SqrtExpr: u16 = 29;
pub const K_g_TinyExpressionP4AST_2e_MinExpr: u16 = 30;
pub const K_g_TinyExpressionP4AST_2e_MaxExpr: u16 = 31;
pub const K_g_TinyExpressionP4AST_2e_RandomExpr: u16 = 32;
pub const K_g_TinyExpressionP4AST_2e_AbsExpr: u16 = 33;
pub const K_g_TinyExpressionP4AST_2e_RoundExpr: u16 = 34;
pub const K_g_TinyExpressionP4AST_2e_CeilExpr: u16 = 35;
pub const K_g_TinyExpressionP4AST_2e_FloorExpr: u16 = 36;
pub const K_g_TinyExpressionP4AST_2e_PowExpr: u16 = 37;
pub const K_g_TinyExpressionP4AST_2e_LogExpr: u16 = 38;
pub const K_g_TinyExpressionP4AST_2e_ExpExpr: u16 = 39;
pub const K_g_TinyExpressionP4AST_2e_ToNumExpr: u16 = 40;
pub const K_g_TinyExpressionP4AST_2e_ToUpperCaseExpr: u16 = 41;
pub const K_g_TinyExpressionP4AST_2e_ToLowerCaseExpr: u16 = 42;
pub const K_g_TinyExpressionP4AST_2e_TrimExpr: u16 = 43;
pub const K_g_TinyExpressionP4AST_2e_LengthExpr: u16 = 44;
pub const K_g_TinyExpressionP4AST_2e_ToUpperCaseDotExpr: u16 = 45;
pub const K_g_TinyExpressionP4AST_2e_ToLowerCaseDotExpr: u16 = 46;
pub const K_g_TinyExpressionP4AST_2e_TrimDotExpr: u16 = 47;
pub const K_g_TinyExpressionP4AST_2e_LengthDotExpr: u16 = 48;
pub const K_g_TinyExpressionP4AST_2e_StartsWithExpr: u16 = 49;
pub const K_g_TinyExpressionP4AST_2e_EndsWithExpr: u16 = 50;
pub const K_g_TinyExpressionP4AST_2e_ContainsExpr: u16 = 51;
pub const K_g_TinyExpressionP4AST_2e_InExpr: u16 = 52;
pub const K_g_TinyExpressionP4AST_2e_StartsWithDotExpr: u16 = 53;
pub const K_g_TinyExpressionP4AST_2e_EndsWithDotExpr: u16 = 54;
pub const K_g_TinyExpressionP4AST_2e_ContainsDotExpr: u16 = 55;
pub const K_g_TinyExpressionP4AST_2e_IsPresentExpr: u16 = 56;
pub const K_g_TinyExpressionP4AST_2e_InTimeRangeExpr: u16 = 57;
pub const K_g_TinyExpressionP4AST_2e_InDayTimeRangeExpr: u16 = 58;
pub const K_g_TinyExpressionP4AST_2e_SliceExpr: u16 = 59;
pub const K_g_TinyExpressionP4AST_2e_StringConcatExpr: u16 = 60;
pub const K_g_TinyExpressionP4AST_2e_StringCastVariableRefExpr: u16 = 61;
pub const K_g_TinyExpressionP4AST_2e_StringTypedVariableRefExpr: u16 = 62;
pub const K_g_TinyExpressionP4AST_2e_BooleanOrExpr: u16 = 63;
pub const K_g_TinyExpressionP4AST_2e_BooleanAndExpr: u16 = 64;
pub const K_g_TinyExpressionP4AST_2e_BooleanXorExpr: u16 = 65;
pub const K_g_TinyExpressionP4AST_2e_NotExpr: u16 = 66;
pub const K_g_TinyExpressionP4AST_2e_BooleanEqualityExpr: u16 = 67;
pub const K_g_TinyExpressionP4AST_2e_BooleanFactorExpr: u16 = 68;
pub const K_g_TinyExpressionP4AST_2e_StringComparisonExpr: u16 = 69;
pub const K_g_TinyExpressionP4AST_2e_ComparisonExpr: u16 = 70;
pub const K_g_TinyExpressionP4AST_2e_ObjectExpr: u16 = 71;
pub const K_g_TinyExpressionP4AST_2e_IfExpr: u16 = 72;
pub const K_g_TinyExpressionP4AST_2e_BranchExpressionExpr: u16 = 73;
pub const K_g_TinyExpressionP4AST_2e_NumberMatchExpr: u16 = 74;
pub const K_g_TinyExpressionP4AST_2e_NumberCaseExpr: u16 = 75;
pub const K_g_TinyExpressionP4AST_2e_NumberDefaultCaseExpr: u16 = 76;
pub const K_g_TinyExpressionP4AST_2e_NumberCaseValueExpr: u16 = 77;
pub const K_g_TinyExpressionP4AST_2e_StringMatchExpr: u16 = 78;
pub const K_g_TinyExpressionP4AST_2e_StringCaseExpr: u16 = 79;
pub const K_g_TinyExpressionP4AST_2e_StringDefaultCaseExpr: u16 = 80;
pub const K_g_TinyExpressionP4AST_2e_StringCaseValueExpr: u16 = 81;
pub const K_g_TinyExpressionP4AST_2e_BooleanMatchExpr: u16 = 82;
pub const K_g_TinyExpressionP4AST_2e_BooleanCaseExpr: u16 = 83;
pub const K_g_TinyExpressionP4AST_2e_BooleanDefaultCaseExpr: u16 = 84;
pub const K_g_TinyExpressionP4AST_2e_BooleanCaseValueExpr: u16 = 85;
pub const K_g_TinyExpressionP4AST_2e_VariableRefExpr: u16 = 86;
pub const K_g_TinyExpressionP4AST_2e_ExpressionExpr: u16 = 87;
pub(crate) const TYPE_IDS: &[&str] = &["text","null","TinyExpressionP4AST.FormulaExpr","TinyExpressionP4AST.CodeBlockExpr","TinyExpressionP4AST.ImportDeclarationExpr","TinyExpressionP4AST.QualifiedNameExpr","TinyExpressionP4AST.NumberVariableDeclarationExpr","TinyExpressionP4AST.StringVariableDeclarationExpr","TinyExpressionP4AST.BooleanVariableDeclarationExpr","TinyExpressionP4AST.ObjectVariableDeclarationExpr","TinyExpressionP4AST.OnlyIfAbsentExpr","TinyExpressionP4AST.NumberMethodDeclarationExpr","TinyExpressionP4AST.StringMethodDeclarationExpr","TinyExpressionP4AST.BooleanMethodDeclarationExpr","TinyExpressionP4AST.ObjectMethodDeclarationExpr","TinyExpressionP4AST.MethodParametersExpr","TinyExpressionP4AST.MethodParameterExpr","TinyExpressionP4AST.ExternalBooleanInvocationExpr","TinyExpressionP4AST.ExternalNumberInvocationExpr","TinyExpressionP4AST.ExternalStringInvocationExpr","TinyExpressionP4AST.ExternalObjectInvocationExpr","TinyExpressionP4AST.MethodInvocationExpr","TinyExpressionP4AST.TernaryExpr","TinyExpressionP4AST.ArgumentExpressionExpr","TinyExpressionP4AST.ArgumentsExpr","TinyExpressionP4AST.BinaryExpr","TinyExpressionP4AST.SinExpr","TinyExpressionP4AST.CosExpr","TinyExpressionP4AST.TanExpr","TinyExpressionP4AST.SqrtExpr","TinyExpressionP4AST.MinExpr","TinyExpressionP4AST.MaxExpr","TinyExpressionP4AST.RandomExpr","TinyExpressionP4AST.AbsExpr","TinyExpressionP4AST.RoundExpr","TinyExpressionP4AST.CeilExpr","TinyExpressionP4AST.FloorExpr","TinyExpressionP4AST.PowExpr","TinyExpressionP4AST.LogExpr","TinyExpressionP4AST.ExpExpr","TinyExpressionP4AST.ToNumExpr","TinyExpressionP4AST.ToUpperCaseExpr","TinyExpressionP4AST.ToLowerCaseExpr","TinyExpressionP4AST.TrimExpr","TinyExpressionP4AST.LengthExpr","TinyExpressionP4AST.ToUpperCaseDotExpr","TinyExpressionP4AST.ToLowerCaseDotExpr","TinyExpressionP4AST.TrimDotExpr","TinyExpressionP4AST.LengthDotExpr","TinyExpressionP4AST.StartsWithExpr","TinyExpressionP4AST.EndsWithExpr","TinyExpressionP4AST.ContainsExpr","TinyExpressionP4AST.InExpr","TinyExpressionP4AST.StartsWithDotExpr","TinyExpressionP4AST.EndsWithDotExpr","TinyExpressionP4AST.ContainsDotExpr","TinyExpressionP4AST.IsPresentExpr","TinyExpressionP4AST.InTimeRangeExpr","TinyExpressionP4AST.InDayTimeRangeExpr","TinyExpressionP4AST.SliceExpr","TinyExpressionP4AST.StringConcatExpr","TinyExpressionP4AST.StringCastVariableRefExpr","TinyExpressionP4AST.StringTypedVariableRefExpr","TinyExpressionP4AST.BooleanOrExpr","TinyExpressionP4AST.BooleanAndExpr","TinyExpressionP4AST.BooleanXorExpr","TinyExpressionP4AST.NotExpr","TinyExpressionP4AST.BooleanEqualityExpr","TinyExpressionP4AST.BooleanFactorExpr","TinyExpressionP4AST.StringComparisonExpr","TinyExpressionP4AST.ComparisonExpr","TinyExpressionP4AST.ObjectExpr","TinyExpressionP4AST.IfExpr","TinyExpressionP4AST.BranchExpressionExpr","TinyExpressionP4AST.NumberMatchExpr","TinyExpressionP4AST.NumberCaseExpr","TinyExpressionP4AST.NumberDefaultCaseExpr","TinyExpressionP4AST.NumberCaseValueExpr","TinyExpressionP4AST.StringMatchExpr","TinyExpressionP4AST.StringCaseExpr","TinyExpressionP4AST.StringDefaultCaseExpr","TinyExpressionP4AST.StringCaseValueExpr","TinyExpressionP4AST.BooleanMatchExpr","TinyExpressionP4AST.BooleanCaseExpr","TinyExpressionP4AST.BooleanDefaultCaseExpr","TinyExpressionP4AST.BooleanCaseValueExpr","TinyExpressionP4AST.VariableRefExpr","TinyExpressionP4AST.ExpressionExpr"];
pub(crate) const TYPE_NAMES: &[&str] = &["text","null","FormulaExpr","CodeBlockExpr","ImportDeclarationExpr","QualifiedNameExpr","NumberVariableDeclarationExpr","StringVariableDeclarationExpr","BooleanVariableDeclarationExpr","ObjectVariableDeclarationExpr","OnlyIfAbsentExpr","NumberMethodDeclarationExpr","StringMethodDeclarationExpr","BooleanMethodDeclarationExpr","ObjectMethodDeclarationExpr","MethodParametersExpr","MethodParameterExpr","ExternalBooleanInvocationExpr","ExternalNumberInvocationExpr","ExternalStringInvocationExpr","ExternalObjectInvocationExpr","MethodInvocationExpr","TernaryExpr","ArgumentExpressionExpr","ArgumentsExpr","BinaryExpr","SinExpr","CosExpr","TanExpr","SqrtExpr","MinExpr","MaxExpr","RandomExpr","AbsExpr","RoundExpr","CeilExpr","FloorExpr","PowExpr","LogExpr","ExpExpr","ToNumExpr","ToUpperCaseExpr","ToLowerCaseExpr","TrimExpr","LengthExpr","ToUpperCaseDotExpr","ToLowerCaseDotExpr","TrimDotExpr","LengthDotExpr","StartsWithExpr","EndsWithExpr","ContainsExpr","InExpr","StartsWithDotExpr","EndsWithDotExpr","ContainsDotExpr","IsPresentExpr","InTimeRangeExpr","InDayTimeRangeExpr","SliceExpr","StringConcatExpr","StringCastVariableRefExpr","StringTypedVariableRefExpr","BooleanOrExpr","BooleanAndExpr","BooleanXorExpr","NotExpr","BooleanEqualityExpr","BooleanFactorExpr","StringComparisonExpr","ComparisonExpr","ObjectExpr","IfExpr","BranchExpressionExpr","NumberMatchExpr","NumberCaseExpr","NumberDefaultCaseExpr","NumberCaseValueExpr","StringMatchExpr","StringCaseExpr","StringDefaultCaseExpr","StringCaseValueExpr","BooleanMatchExpr","BooleanCaseExpr","BooleanDefaultCaseExpr","BooleanCaseValueExpr","VariableRefExpr","ExpressionExpr"];
pub(crate) const RULE_IDS: &[&str] = &["TinyExpressionP4::Formula", "TinyExpressionP4::CodeBlock", "TinyExpressionP4::ImportDeclaration", "TinyExpressionP4::ClassName", "TinyExpressionP4::VariableDeclaration", "TinyExpressionP4::NumberVariableDeclaration", "TinyExpressionP4::StringVariableDeclaration", "TinyExpressionP4::BooleanVariableDeclaration", "TinyExpressionP4::ObjectVariableDeclaration", "TinyExpressionP4::TypeHint", "TinyExpressionP4::NumberTypeHint", "TinyExpressionP4::StringTypeHint", "TinyExpressionP4::BooleanTypeHint", "TinyExpressionP4::ObjectTypeHint", "TinyExpressionP4::OnlyIfAbsent", "TinyExpressionP4::Description", "TinyExpressionP4::Annotation", "TinyExpressionP4::AnnotationParameters", "TinyExpressionP4::AnnotationParameter", "TinyExpressionP4::MethodDeclaration", "TinyExpressionP4::NumberMethodDeclaration", "TinyExpressionP4::StringMethodDeclaration", "TinyExpressionP4::BooleanMethodDeclaration", "TinyExpressionP4::ObjectMethodDeclaration", "TinyExpressionP4::MethodParameters", "TinyExpressionP4::MethodParameter", "TinyExpressionP4::NumberReturnType", "TinyExpressionP4::StringReturnType", "TinyExpressionP4::BooleanReturnType", "TinyExpressionP4::ObjectReturnType", "TinyExpressionP4::ReturnType", "TinyExpressionP4::ExternalBooleanInvocation", "TinyExpressionP4::ExternalNumberInvocation", "TinyExpressionP4::ExternalStringInvocation", "TinyExpressionP4::ExternalObjectInvocation", "TinyExpressionP4::MethodInvocationHeader", "TinyExpressionP4::MethodInvocation", "TinyExpressionP4::ArgumentTernary", "TinyExpressionP4::ArgumentExpression", "TinyExpressionP4::Arguments", "TinyExpressionP4::NumberExpression", "TinyExpressionP4::NumberTerm", "TinyExpressionP4::AddOp", "TinyExpressionP4::MulOp", "TinyExpressionP4::MathFunction", "TinyExpressionP4::SinFunction", "TinyExpressionP4::CosFunction", "TinyExpressionP4::TanFunction", "TinyExpressionP4::SqrtFunction", "TinyExpressionP4::MinFunction", "TinyExpressionP4::MaxFunction", "TinyExpressionP4::RandomFunction", "TinyExpressionP4::AbsFunction", "TinyExpressionP4::RoundFunction", "TinyExpressionP4::CeilFunction", "TinyExpressionP4::FloorFunction", "TinyExpressionP4::PowFunction", "TinyExpressionP4::LogFunction", "TinyExpressionP4::ExpFunction", "TinyExpressionP4::ToNumFunction", "TinyExpressionP4::NumberFactor", "TinyExpressionP4::ToUpperCaseFunction", "TinyExpressionP4::ToLowerCaseFunction", "TinyExpressionP4::TrimFunction", "TinyExpressionP4::LengthFunction", "TinyExpressionP4::LenFunction", "TinyExpressionP4::ToUpperCaseDotMethod", "TinyExpressionP4::ToLowerCaseDotMethod", "TinyExpressionP4::TrimDotMethod", "TinyExpressionP4::LengthDotMethod", "TinyExpressionP4::StartsWithFunction", "TinyExpressionP4::EndsWithFunction", "TinyExpressionP4::ContainsFunction", "TinyExpressionP4::InMethod", "TinyExpressionP4::StartsWithDotMethod", "TinyExpressionP4::EndsWithDotMethod", "TinyExpressionP4::ContainsDotMethod", "TinyExpressionP4::StringPredicateReceiver", "TinyExpressionP4::IsPresentFunction", "TinyExpressionP4::InTimeRangeFunction", "TinyExpressionP4::InDayTimeRangeFunction", "TinyExpressionP4::DayOfWeek", "TinyExpressionP4::SliceBaseReceiver", "TinyExpressionP4::SliceStartIndex", "TinyExpressionP4::SliceEndIndex", "TinyExpressionP4::SliceStepIndex", "TinyExpressionP4::SliceBaseExpression", "TinyExpressionP4::SliceNestedExpression", "TinyExpressionP4::SliceExpression", "TinyExpressionP4::StringExpression", "TinyExpressionP4::ParenthesizedStringExpression", "TinyExpressionP4::StringTerm", "TinyExpressionP4::StringCastVariable", "TinyExpressionP4::StringTypedVariable", "TinyExpressionP4::BooleanExpression", "TinyExpressionP4::BooleanAndExpression", "TinyExpressionP4::BooleanXorExpression", "TinyExpressionP4::NotExpression", "TinyExpressionP4::BooleanComparable", "TinyExpressionP4::BooleanEqualityExpression", "TinyExpressionP4::BooleanFactor", "TinyExpressionP4::StringComparisonExpression", "TinyExpressionP4::EqualityOp", "TinyExpressionP4::ComparisonExpression", "TinyExpressionP4::CompareOp", "TinyExpressionP4::ObjectExpression", "TinyExpressionP4::IfExpression", "TinyExpressionP4::BranchExpression", "TinyExpressionP4::TernaryExpression", "TinyExpressionP4::NumberMatchExpression", "TinyExpressionP4::NumberCase", "TinyExpressionP4::NumberDefaultCase", "TinyExpressionP4::NumberCaseValue", "TinyExpressionP4::StringMatchExpression", "TinyExpressionP4::StringCase", "TinyExpressionP4::StringDefaultCase", "TinyExpressionP4::StringCaseValue", "TinyExpressionP4::BooleanMatchExpression", "TinyExpressionP4::BooleanCase", "TinyExpressionP4::BooleanDefaultCase", "TinyExpressionP4::BooleanCaseValue", "TinyExpressionP4::VariableRef", "TinyExpressionP4::TypeKeyword", "TinyExpressionP4::Expression"];
/// `super::Ast` と同じ形の、木への参照。
#[derive(Clone, Copy, Debug)]
pub enum Node<'t> { Text(&'t str), Null,
g_TinyExpressionP4AST_2e_FormulaExpr(g_TinyExpressionP4AST_2e_FormulaExpr<'t>),
g_TinyExpressionP4AST_2e_CodeBlockExpr(g_TinyExpressionP4AST_2e_CodeBlockExpr<'t>),
g_TinyExpressionP4AST_2e_ImportDeclarationExpr(g_TinyExpressionP4AST_2e_ImportDeclarationExpr<'t>),
g_TinyExpressionP4AST_2e_QualifiedNameExpr(g_TinyExpressionP4AST_2e_QualifiedNameExpr<'t>),
g_TinyExpressionP4AST_2e_NumberVariableDeclarationExpr(g_TinyExpressionP4AST_2e_NumberVariableDeclarationExpr<'t>),
g_TinyExpressionP4AST_2e_StringVariableDeclarationExpr(g_TinyExpressionP4AST_2e_StringVariableDeclarationExpr<'t>),
g_TinyExpressionP4AST_2e_BooleanVariableDeclarationExpr(g_TinyExpressionP4AST_2e_BooleanVariableDeclarationExpr<'t>),
g_TinyExpressionP4AST_2e_ObjectVariableDeclarationExpr(g_TinyExpressionP4AST_2e_ObjectVariableDeclarationExpr<'t>),
g_TinyExpressionP4AST_2e_OnlyIfAbsentExpr(g_TinyExpressionP4AST_2e_OnlyIfAbsentExpr<'t>),
g_TinyExpressionP4AST_2e_NumberMethodDeclarationExpr(g_TinyExpressionP4AST_2e_NumberMethodDeclarationExpr<'t>),
g_TinyExpressionP4AST_2e_StringMethodDeclarationExpr(g_TinyExpressionP4AST_2e_StringMethodDeclarationExpr<'t>),
g_TinyExpressionP4AST_2e_BooleanMethodDeclarationExpr(g_TinyExpressionP4AST_2e_BooleanMethodDeclarationExpr<'t>),
g_TinyExpressionP4AST_2e_ObjectMethodDeclarationExpr(g_TinyExpressionP4AST_2e_ObjectMethodDeclarationExpr<'t>),
g_TinyExpressionP4AST_2e_MethodParametersExpr(g_TinyExpressionP4AST_2e_MethodParametersExpr<'t>),
g_TinyExpressionP4AST_2e_MethodParameterExpr(g_TinyExpressionP4AST_2e_MethodParameterExpr<'t>),
g_TinyExpressionP4AST_2e_ExternalBooleanInvocationExpr(g_TinyExpressionP4AST_2e_ExternalBooleanInvocationExpr<'t>),
g_TinyExpressionP4AST_2e_ExternalNumberInvocationExpr(g_TinyExpressionP4AST_2e_ExternalNumberInvocationExpr<'t>),
g_TinyExpressionP4AST_2e_ExternalStringInvocationExpr(g_TinyExpressionP4AST_2e_ExternalStringInvocationExpr<'t>),
g_TinyExpressionP4AST_2e_ExternalObjectInvocationExpr(g_TinyExpressionP4AST_2e_ExternalObjectInvocationExpr<'t>),
g_TinyExpressionP4AST_2e_MethodInvocationExpr(g_TinyExpressionP4AST_2e_MethodInvocationExpr<'t>),
g_TinyExpressionP4AST_2e_TernaryExpr(g_TinyExpressionP4AST_2e_TernaryExpr<'t>),
g_TinyExpressionP4AST_2e_ArgumentExpressionExpr(g_TinyExpressionP4AST_2e_ArgumentExpressionExpr<'t>),
g_TinyExpressionP4AST_2e_ArgumentsExpr(g_TinyExpressionP4AST_2e_ArgumentsExpr<'t>),
g_TinyExpressionP4AST_2e_BinaryExpr(g_TinyExpressionP4AST_2e_BinaryExpr<'t>),
g_TinyExpressionP4AST_2e_SinExpr(g_TinyExpressionP4AST_2e_SinExpr<'t>),
g_TinyExpressionP4AST_2e_CosExpr(g_TinyExpressionP4AST_2e_CosExpr<'t>),
g_TinyExpressionP4AST_2e_TanExpr(g_TinyExpressionP4AST_2e_TanExpr<'t>),
g_TinyExpressionP4AST_2e_SqrtExpr(g_TinyExpressionP4AST_2e_SqrtExpr<'t>),
g_TinyExpressionP4AST_2e_MinExpr(g_TinyExpressionP4AST_2e_MinExpr<'t>),
g_TinyExpressionP4AST_2e_MaxExpr(g_TinyExpressionP4AST_2e_MaxExpr<'t>),
g_TinyExpressionP4AST_2e_RandomExpr(g_TinyExpressionP4AST_2e_RandomExpr<'t>),
g_TinyExpressionP4AST_2e_AbsExpr(g_TinyExpressionP4AST_2e_AbsExpr<'t>),
g_TinyExpressionP4AST_2e_RoundExpr(g_TinyExpressionP4AST_2e_RoundExpr<'t>),
g_TinyExpressionP4AST_2e_CeilExpr(g_TinyExpressionP4AST_2e_CeilExpr<'t>),
g_TinyExpressionP4AST_2e_FloorExpr(g_TinyExpressionP4AST_2e_FloorExpr<'t>),
g_TinyExpressionP4AST_2e_PowExpr(g_TinyExpressionP4AST_2e_PowExpr<'t>),
g_TinyExpressionP4AST_2e_LogExpr(g_TinyExpressionP4AST_2e_LogExpr<'t>),
g_TinyExpressionP4AST_2e_ExpExpr(g_TinyExpressionP4AST_2e_ExpExpr<'t>),
g_TinyExpressionP4AST_2e_ToNumExpr(g_TinyExpressionP4AST_2e_ToNumExpr<'t>),
g_TinyExpressionP4AST_2e_ToUpperCaseExpr(g_TinyExpressionP4AST_2e_ToUpperCaseExpr<'t>),
g_TinyExpressionP4AST_2e_ToLowerCaseExpr(g_TinyExpressionP4AST_2e_ToLowerCaseExpr<'t>),
g_TinyExpressionP4AST_2e_TrimExpr(g_TinyExpressionP4AST_2e_TrimExpr<'t>),
g_TinyExpressionP4AST_2e_LengthExpr(g_TinyExpressionP4AST_2e_LengthExpr<'t>),
g_TinyExpressionP4AST_2e_ToUpperCaseDotExpr(g_TinyExpressionP4AST_2e_ToUpperCaseDotExpr<'t>),
g_TinyExpressionP4AST_2e_ToLowerCaseDotExpr(g_TinyExpressionP4AST_2e_ToLowerCaseDotExpr<'t>),
g_TinyExpressionP4AST_2e_TrimDotExpr(g_TinyExpressionP4AST_2e_TrimDotExpr<'t>),
g_TinyExpressionP4AST_2e_LengthDotExpr(g_TinyExpressionP4AST_2e_LengthDotExpr<'t>),
g_TinyExpressionP4AST_2e_StartsWithExpr(g_TinyExpressionP4AST_2e_StartsWithExpr<'t>),
g_TinyExpressionP4AST_2e_EndsWithExpr(g_TinyExpressionP4AST_2e_EndsWithExpr<'t>),
g_TinyExpressionP4AST_2e_ContainsExpr(g_TinyExpressionP4AST_2e_ContainsExpr<'t>),
g_TinyExpressionP4AST_2e_InExpr(g_TinyExpressionP4AST_2e_InExpr<'t>),
g_TinyExpressionP4AST_2e_StartsWithDotExpr(g_TinyExpressionP4AST_2e_StartsWithDotExpr<'t>),
g_TinyExpressionP4AST_2e_EndsWithDotExpr(g_TinyExpressionP4AST_2e_EndsWithDotExpr<'t>),
g_TinyExpressionP4AST_2e_ContainsDotExpr(g_TinyExpressionP4AST_2e_ContainsDotExpr<'t>),
g_TinyExpressionP4AST_2e_IsPresentExpr(g_TinyExpressionP4AST_2e_IsPresentExpr<'t>),
g_TinyExpressionP4AST_2e_InTimeRangeExpr(g_TinyExpressionP4AST_2e_InTimeRangeExpr<'t>),
g_TinyExpressionP4AST_2e_InDayTimeRangeExpr(g_TinyExpressionP4AST_2e_InDayTimeRangeExpr<'t>),
g_TinyExpressionP4AST_2e_SliceExpr(g_TinyExpressionP4AST_2e_SliceExpr<'t>),
g_TinyExpressionP4AST_2e_StringConcatExpr(g_TinyExpressionP4AST_2e_StringConcatExpr<'t>),
g_TinyExpressionP4AST_2e_StringCastVariableRefExpr(g_TinyExpressionP4AST_2e_StringCastVariableRefExpr<'t>),
g_TinyExpressionP4AST_2e_StringTypedVariableRefExpr(g_TinyExpressionP4AST_2e_StringTypedVariableRefExpr<'t>),
g_TinyExpressionP4AST_2e_BooleanOrExpr(g_TinyExpressionP4AST_2e_BooleanOrExpr<'t>),
g_TinyExpressionP4AST_2e_BooleanAndExpr(g_TinyExpressionP4AST_2e_BooleanAndExpr<'t>),
g_TinyExpressionP4AST_2e_BooleanXorExpr(g_TinyExpressionP4AST_2e_BooleanXorExpr<'t>),
g_TinyExpressionP4AST_2e_NotExpr(g_TinyExpressionP4AST_2e_NotExpr<'t>),
g_TinyExpressionP4AST_2e_BooleanEqualityExpr(g_TinyExpressionP4AST_2e_BooleanEqualityExpr<'t>),
g_TinyExpressionP4AST_2e_BooleanFactorExpr(g_TinyExpressionP4AST_2e_BooleanFactorExpr<'t>),
g_TinyExpressionP4AST_2e_StringComparisonExpr(g_TinyExpressionP4AST_2e_StringComparisonExpr<'t>),
g_TinyExpressionP4AST_2e_ComparisonExpr(g_TinyExpressionP4AST_2e_ComparisonExpr<'t>),
g_TinyExpressionP4AST_2e_ObjectExpr(g_TinyExpressionP4AST_2e_ObjectExpr<'t>),
g_TinyExpressionP4AST_2e_IfExpr(g_TinyExpressionP4AST_2e_IfExpr<'t>),
g_TinyExpressionP4AST_2e_BranchExpressionExpr(g_TinyExpressionP4AST_2e_BranchExpressionExpr<'t>),
g_TinyExpressionP4AST_2e_NumberMatchExpr(g_TinyExpressionP4AST_2e_NumberMatchExpr<'t>),
g_TinyExpressionP4AST_2e_NumberCaseExpr(g_TinyExpressionP4AST_2e_NumberCaseExpr<'t>),
g_TinyExpressionP4AST_2e_NumberDefaultCaseExpr(g_TinyExpressionP4AST_2e_NumberDefaultCaseExpr<'t>),
g_TinyExpressionP4AST_2e_NumberCaseValueExpr(g_TinyExpressionP4AST_2e_NumberCaseValueExpr<'t>),
g_TinyExpressionP4AST_2e_StringMatchExpr(g_TinyExpressionP4AST_2e_StringMatchExpr<'t>),
g_TinyExpressionP4AST_2e_StringCaseExpr(g_TinyExpressionP4AST_2e_StringCaseExpr<'t>),
g_TinyExpressionP4AST_2e_StringDefaultCaseExpr(g_TinyExpressionP4AST_2e_StringDefaultCaseExpr<'t>),
g_TinyExpressionP4AST_2e_StringCaseValueExpr(g_TinyExpressionP4AST_2e_StringCaseValueExpr<'t>),
g_TinyExpressionP4AST_2e_BooleanMatchExpr(g_TinyExpressionP4AST_2e_BooleanMatchExpr<'t>),
g_TinyExpressionP4AST_2e_BooleanCaseExpr(g_TinyExpressionP4AST_2e_BooleanCaseExpr<'t>),
g_TinyExpressionP4AST_2e_BooleanDefaultCaseExpr(g_TinyExpressionP4AST_2e_BooleanDefaultCaseExpr<'t>),
g_TinyExpressionP4AST_2e_BooleanCaseValueExpr(g_TinyExpressionP4AST_2e_BooleanCaseValueExpr<'t>),
g_TinyExpressionP4AST_2e_VariableRefExpr(g_TinyExpressionP4AST_2e_VariableRefExpr<'t>),
g_TinyExpressionP4AST_2e_ExpressionExpr(g_TinyExpressionP4AST_2e_ExpressionExpr<'t>),
}
impl<'t> NodeRef<'t> {
/// 型ごとの参照へ分ける。
pub fn get(&self) -> Node<'t> { let (tree, id) = (self.tree, self.id); match tree.kind(id) { KIND_TEXT => Node::Text(tree.text_in(&tree.source, id)), KIND_NULL => Node::Null,
K_g_TinyExpressionP4AST_2e_FormulaExpr => Node::g_TinyExpressionP4AST_2e_FormulaExpr(g_TinyExpressionP4AST_2e_FormulaExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_CodeBlockExpr => Node::g_TinyExpressionP4AST_2e_CodeBlockExpr(g_TinyExpressionP4AST_2e_CodeBlockExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_ImportDeclarationExpr => Node::g_TinyExpressionP4AST_2e_ImportDeclarationExpr(g_TinyExpressionP4AST_2e_ImportDeclarationExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_QualifiedNameExpr => Node::g_TinyExpressionP4AST_2e_QualifiedNameExpr(g_TinyExpressionP4AST_2e_QualifiedNameExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_NumberVariableDeclarationExpr => Node::g_TinyExpressionP4AST_2e_NumberVariableDeclarationExpr(g_TinyExpressionP4AST_2e_NumberVariableDeclarationExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_StringVariableDeclarationExpr => Node::g_TinyExpressionP4AST_2e_StringVariableDeclarationExpr(g_TinyExpressionP4AST_2e_StringVariableDeclarationExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_BooleanVariableDeclarationExpr => Node::g_TinyExpressionP4AST_2e_BooleanVariableDeclarationExpr(g_TinyExpressionP4AST_2e_BooleanVariableDeclarationExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_ObjectVariableDeclarationExpr => Node::g_TinyExpressionP4AST_2e_ObjectVariableDeclarationExpr(g_TinyExpressionP4AST_2e_ObjectVariableDeclarationExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_OnlyIfAbsentExpr => Node::g_TinyExpressionP4AST_2e_OnlyIfAbsentExpr(g_TinyExpressionP4AST_2e_OnlyIfAbsentExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_NumberMethodDeclarationExpr => Node::g_TinyExpressionP4AST_2e_NumberMethodDeclarationExpr(g_TinyExpressionP4AST_2e_NumberMethodDeclarationExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_StringMethodDeclarationExpr => Node::g_TinyExpressionP4AST_2e_StringMethodDeclarationExpr(g_TinyExpressionP4AST_2e_StringMethodDeclarationExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_BooleanMethodDeclarationExpr => Node::g_TinyExpressionP4AST_2e_BooleanMethodDeclarationExpr(g_TinyExpressionP4AST_2e_BooleanMethodDeclarationExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_ObjectMethodDeclarationExpr => Node::g_TinyExpressionP4AST_2e_ObjectMethodDeclarationExpr(g_TinyExpressionP4AST_2e_ObjectMethodDeclarationExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_MethodParametersExpr => Node::g_TinyExpressionP4AST_2e_MethodParametersExpr(g_TinyExpressionP4AST_2e_MethodParametersExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_MethodParameterExpr => Node::g_TinyExpressionP4AST_2e_MethodParameterExpr(g_TinyExpressionP4AST_2e_MethodParameterExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_ExternalBooleanInvocationExpr => Node::g_TinyExpressionP4AST_2e_ExternalBooleanInvocationExpr(g_TinyExpressionP4AST_2e_ExternalBooleanInvocationExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_ExternalNumberInvocationExpr => Node::g_TinyExpressionP4AST_2e_ExternalNumberInvocationExpr(g_TinyExpressionP4AST_2e_ExternalNumberInvocationExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_ExternalStringInvocationExpr => Node::g_TinyExpressionP4AST_2e_ExternalStringInvocationExpr(g_TinyExpressionP4AST_2e_ExternalStringInvocationExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_ExternalObjectInvocationExpr => Node::g_TinyExpressionP4AST_2e_ExternalObjectInvocationExpr(g_TinyExpressionP4AST_2e_ExternalObjectInvocationExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_MethodInvocationExpr => Node::g_TinyExpressionP4AST_2e_MethodInvocationExpr(g_TinyExpressionP4AST_2e_MethodInvocationExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_TernaryExpr => Node::g_TinyExpressionP4AST_2e_TernaryExpr(g_TinyExpressionP4AST_2e_TernaryExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_ArgumentExpressionExpr => Node::g_TinyExpressionP4AST_2e_ArgumentExpressionExpr(g_TinyExpressionP4AST_2e_ArgumentExpressionExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_ArgumentsExpr => Node::g_TinyExpressionP4AST_2e_ArgumentsExpr(g_TinyExpressionP4AST_2e_ArgumentsExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_BinaryExpr => Node::g_TinyExpressionP4AST_2e_BinaryExpr(g_TinyExpressionP4AST_2e_BinaryExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_SinExpr => Node::g_TinyExpressionP4AST_2e_SinExpr(g_TinyExpressionP4AST_2e_SinExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_CosExpr => Node::g_TinyExpressionP4AST_2e_CosExpr(g_TinyExpressionP4AST_2e_CosExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_TanExpr => Node::g_TinyExpressionP4AST_2e_TanExpr(g_TinyExpressionP4AST_2e_TanExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_SqrtExpr => Node::g_TinyExpressionP4AST_2e_SqrtExpr(g_TinyExpressionP4AST_2e_SqrtExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_MinExpr => Node::g_TinyExpressionP4AST_2e_MinExpr(g_TinyExpressionP4AST_2e_MinExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_MaxExpr => Node::g_TinyExpressionP4AST_2e_MaxExpr(g_TinyExpressionP4AST_2e_MaxExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_RandomExpr => Node::g_TinyExpressionP4AST_2e_RandomExpr(g_TinyExpressionP4AST_2e_RandomExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_AbsExpr => Node::g_TinyExpressionP4AST_2e_AbsExpr(g_TinyExpressionP4AST_2e_AbsExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_RoundExpr => Node::g_TinyExpressionP4AST_2e_RoundExpr(g_TinyExpressionP4AST_2e_RoundExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_CeilExpr => Node::g_TinyExpressionP4AST_2e_CeilExpr(g_TinyExpressionP4AST_2e_CeilExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_FloorExpr => Node::g_TinyExpressionP4AST_2e_FloorExpr(g_TinyExpressionP4AST_2e_FloorExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_PowExpr => Node::g_TinyExpressionP4AST_2e_PowExpr(g_TinyExpressionP4AST_2e_PowExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_LogExpr => Node::g_TinyExpressionP4AST_2e_LogExpr(g_TinyExpressionP4AST_2e_LogExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_ExpExpr => Node::g_TinyExpressionP4AST_2e_ExpExpr(g_TinyExpressionP4AST_2e_ExpExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_ToNumExpr => Node::g_TinyExpressionP4AST_2e_ToNumExpr(g_TinyExpressionP4AST_2e_ToNumExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_ToUpperCaseExpr => Node::g_TinyExpressionP4AST_2e_ToUpperCaseExpr(g_TinyExpressionP4AST_2e_ToUpperCaseExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_ToLowerCaseExpr => Node::g_TinyExpressionP4AST_2e_ToLowerCaseExpr(g_TinyExpressionP4AST_2e_ToLowerCaseExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_TrimExpr => Node::g_TinyExpressionP4AST_2e_TrimExpr(g_TinyExpressionP4AST_2e_TrimExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_LengthExpr => Node::g_TinyExpressionP4AST_2e_LengthExpr(g_TinyExpressionP4AST_2e_LengthExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_ToUpperCaseDotExpr => Node::g_TinyExpressionP4AST_2e_ToUpperCaseDotExpr(g_TinyExpressionP4AST_2e_ToUpperCaseDotExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_ToLowerCaseDotExpr => Node::g_TinyExpressionP4AST_2e_ToLowerCaseDotExpr(g_TinyExpressionP4AST_2e_ToLowerCaseDotExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_TrimDotExpr => Node::g_TinyExpressionP4AST_2e_TrimDotExpr(g_TinyExpressionP4AST_2e_TrimDotExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_LengthDotExpr => Node::g_TinyExpressionP4AST_2e_LengthDotExpr(g_TinyExpressionP4AST_2e_LengthDotExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_StartsWithExpr => Node::g_TinyExpressionP4AST_2e_StartsWithExpr(g_TinyExpressionP4AST_2e_StartsWithExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_EndsWithExpr => Node::g_TinyExpressionP4AST_2e_EndsWithExpr(g_TinyExpressionP4AST_2e_EndsWithExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_ContainsExpr => Node::g_TinyExpressionP4AST_2e_ContainsExpr(g_TinyExpressionP4AST_2e_ContainsExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_InExpr => Node::g_TinyExpressionP4AST_2e_InExpr(g_TinyExpressionP4AST_2e_InExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_StartsWithDotExpr => Node::g_TinyExpressionP4AST_2e_StartsWithDotExpr(g_TinyExpressionP4AST_2e_StartsWithDotExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_EndsWithDotExpr => Node::g_TinyExpressionP4AST_2e_EndsWithDotExpr(g_TinyExpressionP4AST_2e_EndsWithDotExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_ContainsDotExpr => Node::g_TinyExpressionP4AST_2e_ContainsDotExpr(g_TinyExpressionP4AST_2e_ContainsDotExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_IsPresentExpr => Node::g_TinyExpressionP4AST_2e_IsPresentExpr(g_TinyExpressionP4AST_2e_IsPresentExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_InTimeRangeExpr => Node::g_TinyExpressionP4AST_2e_InTimeRangeExpr(g_TinyExpressionP4AST_2e_InTimeRangeExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_InDayTimeRangeExpr => Node::g_TinyExpressionP4AST_2e_InDayTimeRangeExpr(g_TinyExpressionP4AST_2e_InDayTimeRangeExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_SliceExpr => Node::g_TinyExpressionP4AST_2e_SliceExpr(g_TinyExpressionP4AST_2e_SliceExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_StringConcatExpr => Node::g_TinyExpressionP4AST_2e_StringConcatExpr(g_TinyExpressionP4AST_2e_StringConcatExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_StringCastVariableRefExpr => Node::g_TinyExpressionP4AST_2e_StringCastVariableRefExpr(g_TinyExpressionP4AST_2e_StringCastVariableRefExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_StringTypedVariableRefExpr => Node::g_TinyExpressionP4AST_2e_StringTypedVariableRefExpr(g_TinyExpressionP4AST_2e_StringTypedVariableRefExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_BooleanOrExpr => Node::g_TinyExpressionP4AST_2e_BooleanOrExpr(g_TinyExpressionP4AST_2e_BooleanOrExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_BooleanAndExpr => Node::g_TinyExpressionP4AST_2e_BooleanAndExpr(g_TinyExpressionP4AST_2e_BooleanAndExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_BooleanXorExpr => Node::g_TinyExpressionP4AST_2e_BooleanXorExpr(g_TinyExpressionP4AST_2e_BooleanXorExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_NotExpr => Node::g_TinyExpressionP4AST_2e_NotExpr(g_TinyExpressionP4AST_2e_NotExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_BooleanEqualityExpr => Node::g_TinyExpressionP4AST_2e_BooleanEqualityExpr(g_TinyExpressionP4AST_2e_BooleanEqualityExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_BooleanFactorExpr => Node::g_TinyExpressionP4AST_2e_BooleanFactorExpr(g_TinyExpressionP4AST_2e_BooleanFactorExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_StringComparisonExpr => Node::g_TinyExpressionP4AST_2e_StringComparisonExpr(g_TinyExpressionP4AST_2e_StringComparisonExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_ComparisonExpr => Node::g_TinyExpressionP4AST_2e_ComparisonExpr(g_TinyExpressionP4AST_2e_ComparisonExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_ObjectExpr => Node::g_TinyExpressionP4AST_2e_ObjectExpr(g_TinyExpressionP4AST_2e_ObjectExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_IfExpr => Node::g_TinyExpressionP4AST_2e_IfExpr(g_TinyExpressionP4AST_2e_IfExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_BranchExpressionExpr => Node::g_TinyExpressionP4AST_2e_BranchExpressionExpr(g_TinyExpressionP4AST_2e_BranchExpressionExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_NumberMatchExpr => Node::g_TinyExpressionP4AST_2e_NumberMatchExpr(g_TinyExpressionP4AST_2e_NumberMatchExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_NumberCaseExpr => Node::g_TinyExpressionP4AST_2e_NumberCaseExpr(g_TinyExpressionP4AST_2e_NumberCaseExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_NumberDefaultCaseExpr => Node::g_TinyExpressionP4AST_2e_NumberDefaultCaseExpr(g_TinyExpressionP4AST_2e_NumberDefaultCaseExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_NumberCaseValueExpr => Node::g_TinyExpressionP4AST_2e_NumberCaseValueExpr(g_TinyExpressionP4AST_2e_NumberCaseValueExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_StringMatchExpr => Node::g_TinyExpressionP4AST_2e_StringMatchExpr(g_TinyExpressionP4AST_2e_StringMatchExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_StringCaseExpr => Node::g_TinyExpressionP4AST_2e_StringCaseExpr(g_TinyExpressionP4AST_2e_StringCaseExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_StringDefaultCaseExpr => Node::g_TinyExpressionP4AST_2e_StringDefaultCaseExpr(g_TinyExpressionP4AST_2e_StringDefaultCaseExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_StringCaseValueExpr => Node::g_TinyExpressionP4AST_2e_StringCaseValueExpr(g_TinyExpressionP4AST_2e_StringCaseValueExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_BooleanMatchExpr => Node::g_TinyExpressionP4AST_2e_BooleanMatchExpr(g_TinyExpressionP4AST_2e_BooleanMatchExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_BooleanCaseExpr => Node::g_TinyExpressionP4AST_2e_BooleanCaseExpr(g_TinyExpressionP4AST_2e_BooleanCaseExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_BooleanDefaultCaseExpr => Node::g_TinyExpressionP4AST_2e_BooleanDefaultCaseExpr(g_TinyExpressionP4AST_2e_BooleanDefaultCaseExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_BooleanCaseValueExpr => Node::g_TinyExpressionP4AST_2e_BooleanCaseValueExpr(g_TinyExpressionP4AST_2e_BooleanCaseValueExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_VariableRefExpr => Node::g_TinyExpressionP4AST_2e_VariableRefExpr(g_TinyExpressionP4AST_2e_VariableRefExpr { tree, id }),
K_g_TinyExpressionP4AST_2e_ExpressionExpr => Node::g_TinyExpressionP4AST_2e_ExpressionExpr(g_TinyExpressionP4AST_2e_ExpressionExpr { tree, id }),
_ => unreachable!("unknown AST node kind"), } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_FormulaExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_FormulaExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_FormulaExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_FormulaExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_FormulaExpr { self.tree.project_g_TinyExpressionP4AST_2e_FormulaExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_imports(&self) -> NodeList<'t> { NodeList { tree: self.tree, ids: self.tree.items(self.id, 0) } }
pub fn g_declarations(&self) -> NodeList<'t> { NodeList { tree: self.tree, ids: self.tree.items(self.id, 2) } }
pub fn g_expression(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 4) } }
pub fn g_methods(&self) -> NodeList<'t> { NodeList { tree: self.tree, ids: self.tree.items(self.id, 5) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_CodeBlockExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_CodeBlockExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_CodeBlockExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_CodeBlockExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_CodeBlockExpr { self.tree.project_g_TinyExpressionP4AST_2e_CodeBlockExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_ImportDeclarationExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_ImportDeclarationExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_ImportDeclarationExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_ImportDeclarationExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_ImportDeclarationExpr { self.tree.project_g_TinyExpressionP4AST_2e_ImportDeclarationExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_className(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
pub fn g_method(&self) -> Option<&'t str> { let tree = self.tree; let v = tree.slot(self.id, 1); (v != NONE).then(|| tree.text_in(&tree.source, v)) }
pub fn g_alias(&self) -> &'t str { let tree = self.tree; tree.text_in(&tree.source, tree.slot(self.id, 2)) }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_QualifiedNameExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_QualifiedNameExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_QualifiedNameExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_QualifiedNameExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_QualifiedNameExpr { self.tree.project_g_TinyExpressionP4AST_2e_QualifiedNameExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_head(&self) -> &'t str { let tree = self.tree; tree.text_in(&tree.source, tree.slot(self.id, 0)) }
pub fn g_tail(&self) -> TextList<'t> { TextList { tree: self.tree, ids: self.tree.items(self.id, 1) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_NumberVariableDeclarationExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_NumberVariableDeclarationExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_NumberVariableDeclarationExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_NumberVariableDeclarationExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_NumberVariableDeclarationExpr { self.tree.project_g_TinyExpressionP4AST_2e_NumberVariableDeclarationExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_varName(&self) -> &'t str { let tree = self.tree; tree.text_in(&tree.source, tree.slot(self.id, 0)) }
pub fn g_onlyIfAbsent(&self) -> Option<NodeRef<'t>> { let v = self.tree.slot(self.id, 1); (v != NONE).then_some(NodeRef { tree: self.tree, id: v }) }
pub fn g_value(&self) -> Option<NodeRef<'t>> { let v = self.tree.slot(self.id, 2); (v != NONE).then_some(NodeRef { tree: self.tree, id: v }) }
pub fn g_desc(&self) -> Option<&'t str> { let tree = self.tree; let v = tree.slot(self.id, 3); (v != NONE).then(|| tree.text_in(&tree.source, v)) }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_StringVariableDeclarationExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_StringVariableDeclarationExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_StringVariableDeclarationExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_StringVariableDeclarationExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_StringVariableDeclarationExpr { self.tree.project_g_TinyExpressionP4AST_2e_StringVariableDeclarationExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_varName(&self) -> &'t str { let tree = self.tree; tree.text_in(&tree.source, tree.slot(self.id, 0)) }
pub fn g_onlyIfAbsent(&self) -> Option<NodeRef<'t>> { let v = self.tree.slot(self.id, 1); (v != NONE).then_some(NodeRef { tree: self.tree, id: v }) }
pub fn g_value(&self) -> Option<NodeRef<'t>> { let v = self.tree.slot(self.id, 2); (v != NONE).then_some(NodeRef { tree: self.tree, id: v }) }
pub fn g_desc(&self) -> Option<&'t str> { let tree = self.tree; let v = tree.slot(self.id, 3); (v != NONE).then(|| tree.text_in(&tree.source, v)) }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_BooleanVariableDeclarationExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_BooleanVariableDeclarationExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_BooleanVariableDeclarationExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_BooleanVariableDeclarationExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_BooleanVariableDeclarationExpr { self.tree.project_g_TinyExpressionP4AST_2e_BooleanVariableDeclarationExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_varName(&self) -> &'t str { let tree = self.tree; tree.text_in(&tree.source, tree.slot(self.id, 0)) }
pub fn g_onlyIfAbsent(&self) -> Option<NodeRef<'t>> { let v = self.tree.slot(self.id, 1); (v != NONE).then_some(NodeRef { tree: self.tree, id: v }) }
pub fn g_value(&self) -> Option<NodeRef<'t>> { let v = self.tree.slot(self.id, 2); (v != NONE).then_some(NodeRef { tree: self.tree, id: v }) }
pub fn g_desc(&self) -> Option<&'t str> { let tree = self.tree; let v = tree.slot(self.id, 3); (v != NONE).then(|| tree.text_in(&tree.source, v)) }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_ObjectVariableDeclarationExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_ObjectVariableDeclarationExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_ObjectVariableDeclarationExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_ObjectVariableDeclarationExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_ObjectVariableDeclarationExpr { self.tree.project_g_TinyExpressionP4AST_2e_ObjectVariableDeclarationExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_varName(&self) -> &'t str { let tree = self.tree; tree.text_in(&tree.source, tree.slot(self.id, 0)) }
pub fn g_onlyIfAbsent(&self) -> Option<NodeRef<'t>> { let v = self.tree.slot(self.id, 1); (v != NONE).then_some(NodeRef { tree: self.tree, id: v }) }
pub fn g_value(&self) -> Option<NodeRef<'t>> { let v = self.tree.slot(self.id, 2); (v != NONE).then_some(NodeRef { tree: self.tree, id: v }) }
pub fn g_desc(&self) -> Option<&'t str> { let tree = self.tree; let v = tree.slot(self.id, 3); (v != NONE).then(|| tree.text_in(&tree.source, v)) }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_OnlyIfAbsentExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_OnlyIfAbsentExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_OnlyIfAbsentExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_OnlyIfAbsentExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_OnlyIfAbsentExpr { self.tree.project_g_TinyExpressionP4AST_2e_OnlyIfAbsentExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_NumberMethodDeclarationExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_NumberMethodDeclarationExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_NumberMethodDeclarationExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_NumberMethodDeclarationExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_NumberMethodDeclarationExpr { self.tree.project_g_TinyExpressionP4AST_2e_NumberMethodDeclarationExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_methodName(&self) -> &'t str { let tree = self.tree; tree.text_in(&tree.source, tree.slot(self.id, 0)) }
pub fn g_parameters(&self) -> Option<NodeRef<'t>> { let v = self.tree.slot(self.id, 1); (v != NONE).then_some(NodeRef { tree: self.tree, id: v }) }
pub fn g_expression(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 2) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_StringMethodDeclarationExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_StringMethodDeclarationExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_StringMethodDeclarationExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_StringMethodDeclarationExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_StringMethodDeclarationExpr { self.tree.project_g_TinyExpressionP4AST_2e_StringMethodDeclarationExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_methodName(&self) -> &'t str { let tree = self.tree; tree.text_in(&tree.source, tree.slot(self.id, 0)) }
pub fn g_parameters(&self) -> Option<NodeRef<'t>> { let v = self.tree.slot(self.id, 1); (v != NONE).then_some(NodeRef { tree: self.tree, id: v }) }
pub fn g_expression(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 2) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_BooleanMethodDeclarationExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_BooleanMethodDeclarationExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_BooleanMethodDeclarationExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_BooleanMethodDeclarationExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_BooleanMethodDeclarationExpr { self.tree.project_g_TinyExpressionP4AST_2e_BooleanMethodDeclarationExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_methodName(&self) -> &'t str { let tree = self.tree; tree.text_in(&tree.source, tree.slot(self.id, 0)) }
pub fn g_parameters(&self) -> Option<NodeRef<'t>> { let v = self.tree.slot(self.id, 1); (v != NONE).then_some(NodeRef { tree: self.tree, id: v }) }
pub fn g_expression(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 2) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_ObjectMethodDeclarationExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_ObjectMethodDeclarationExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_ObjectMethodDeclarationExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_ObjectMethodDeclarationExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_ObjectMethodDeclarationExpr { self.tree.project_g_TinyExpressionP4AST_2e_ObjectMethodDeclarationExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_methodName(&self) -> &'t str { let tree = self.tree; tree.text_in(&tree.source, tree.slot(self.id, 0)) }
pub fn g_parameters(&self) -> Option<NodeRef<'t>> { let v = self.tree.slot(self.id, 1); (v != NONE).then_some(NodeRef { tree: self.tree, id: v }) }
pub fn g_expression(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 2) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_MethodParametersExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_MethodParametersExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_MethodParametersExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_MethodParametersExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_MethodParametersExpr { self.tree.project_g_TinyExpressionP4AST_2e_MethodParametersExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_values(&self) -> NodeList<'t> { NodeList { tree: self.tree, ids: self.tree.items(self.id, 0) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_MethodParameterExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_MethodParameterExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_MethodParameterExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_MethodParameterExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_MethodParameterExpr { self.tree.project_g_TinyExpressionP4AST_2e_MethodParameterExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_paramName(&self) -> &'t str { let tree = self.tree; tree.text_in(&tree.source, tree.slot(self.id, 0)) }
pub fn g_type(&self) -> Option<&'t str> { let tree = self.tree; let v = tree.slot(self.id, 1); (v != NONE).then(|| tree.text_in(&tree.source, v)) }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_ExternalBooleanInvocationExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_ExternalBooleanInvocationExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_ExternalBooleanInvocationExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_ExternalBooleanInvocationExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_ExternalBooleanInvocationExpr { self.tree.project_g_TinyExpressionP4AST_2e_ExternalBooleanInvocationExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_className(&self) -> Option<NodeRef<'t>> { let v = self.tree.slot(self.id, 0); (v != NONE).then_some(NodeRef { tree: self.tree, id: v }) }
pub fn g_name(&self) -> &'t str { let tree = self.tree; tree.text_in(&tree.source, tree.slot(self.id, 1)) }
pub fn g_args(&self) -> Option<NodeRef<'t>> { let v = self.tree.slot(self.id, 2); (v != NONE).then_some(NodeRef { tree: self.tree, id: v }) }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_ExternalNumberInvocationExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_ExternalNumberInvocationExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_ExternalNumberInvocationExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_ExternalNumberInvocationExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_ExternalNumberInvocationExpr { self.tree.project_g_TinyExpressionP4AST_2e_ExternalNumberInvocationExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_className(&self) -> Option<NodeRef<'t>> { let v = self.tree.slot(self.id, 0); (v != NONE).then_some(NodeRef { tree: self.tree, id: v }) }
pub fn g_name(&self) -> &'t str { let tree = self.tree; tree.text_in(&tree.source, tree.slot(self.id, 1)) }
pub fn g_args(&self) -> Option<NodeRef<'t>> { let v = self.tree.slot(self.id, 2); (v != NONE).then_some(NodeRef { tree: self.tree, id: v }) }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_ExternalStringInvocationExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_ExternalStringInvocationExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_ExternalStringInvocationExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_ExternalStringInvocationExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_ExternalStringInvocationExpr { self.tree.project_g_TinyExpressionP4AST_2e_ExternalStringInvocationExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_className(&self) -> Option<NodeRef<'t>> { let v = self.tree.slot(self.id, 0); (v != NONE).then_some(NodeRef { tree: self.tree, id: v }) }
pub fn g_name(&self) -> &'t str { let tree = self.tree; tree.text_in(&tree.source, tree.slot(self.id, 1)) }
pub fn g_args(&self) -> Option<NodeRef<'t>> { let v = self.tree.slot(self.id, 2); (v != NONE).then_some(NodeRef { tree: self.tree, id: v }) }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_ExternalObjectInvocationExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_ExternalObjectInvocationExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_ExternalObjectInvocationExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_ExternalObjectInvocationExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_ExternalObjectInvocationExpr { self.tree.project_g_TinyExpressionP4AST_2e_ExternalObjectInvocationExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_className(&self) -> Option<NodeRef<'t>> { let v = self.tree.slot(self.id, 0); (v != NONE).then_some(NodeRef { tree: self.tree, id: v }) }
pub fn g_name(&self) -> &'t str { let tree = self.tree; tree.text_in(&tree.source, tree.slot(self.id, 1)) }
pub fn g_args(&self) -> Option<NodeRef<'t>> { let v = self.tree.slot(self.id, 2); (v != NONE).then_some(NodeRef { tree: self.tree, id: v }) }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_MethodInvocationExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_MethodInvocationExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_MethodInvocationExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_MethodInvocationExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_MethodInvocationExpr { self.tree.project_g_TinyExpressionP4AST_2e_MethodInvocationExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_name(&self) -> &'t str { let tree = self.tree; tree.text_in(&tree.source, tree.slot(self.id, 0)) }
pub fn g_args(&self) -> Option<NodeRef<'t>> { let v = self.tree.slot(self.id, 1); (v != NONE).then_some(NodeRef { tree: self.tree, id: v }) }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_TernaryExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_TernaryExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_TernaryExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_TernaryExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_TernaryExpr { self.tree.project_g_TinyExpressionP4AST_2e_TernaryExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_condition(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
pub fn g_thenExpr(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 1) } }
pub fn g_elseExpr(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 2) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_ArgumentExpressionExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_ArgumentExpressionExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_ArgumentExpressionExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_ArgumentExpressionExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_ArgumentExpressionExpr { self.tree.project_g_TinyExpressionP4AST_2e_ArgumentExpressionExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_value(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_ArgumentsExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_ArgumentsExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_ArgumentsExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_ArgumentsExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_ArgumentsExpr { self.tree.project_g_TinyExpressionP4AST_2e_ArgumentsExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_values(&self) -> NodeList<'t> { NodeList { tree: self.tree, ids: self.tree.items(self.id, 0) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_BinaryExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_BinaryExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_BinaryExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_BinaryExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_BinaryExpr { self.tree.project_g_TinyExpressionP4AST_2e_BinaryExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_left(&self) -> Option<NodeRef<'t>> { let v = self.tree.slot(self.id, 0); (v != NONE).then_some(NodeRef { tree: self.tree, id: v }) }
pub fn g_op(&self) -> TextList<'t> { TextList { tree: self.tree, ids: self.tree.items(self.id, 1) } }
pub fn g_right(&self) -> NodeList<'t> { NodeList { tree: self.tree, ids: self.tree.items(self.id, 3) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_SinExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_SinExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_SinExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_SinExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_SinExpr { self.tree.project_g_TinyExpressionP4AST_2e_SinExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_arg(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_CosExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_CosExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_CosExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_CosExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_CosExpr { self.tree.project_g_TinyExpressionP4AST_2e_CosExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_arg(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_TanExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_TanExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_TanExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_TanExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_TanExpr { self.tree.project_g_TinyExpressionP4AST_2e_TanExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_arg(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_SqrtExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_SqrtExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_SqrtExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_SqrtExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_SqrtExpr { self.tree.project_g_TinyExpressionP4AST_2e_SqrtExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_arg(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_MinExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_MinExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_MinExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_MinExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_MinExpr { self.tree.project_g_TinyExpressionP4AST_2e_MinExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_first(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
pub fn g_rest(&self) -> NodeList<'t> { NodeList { tree: self.tree, ids: self.tree.items(self.id, 1) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_MaxExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_MaxExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_MaxExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_MaxExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_MaxExpr { self.tree.project_g_TinyExpressionP4AST_2e_MaxExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_first(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
pub fn g_rest(&self) -> NodeList<'t> { NodeList { tree: self.tree, ids: self.tree.items(self.id, 1) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_RandomExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_RandomExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_RandomExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_RandomExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_RandomExpr { self.tree.project_g_TinyExpressionP4AST_2e_RandomExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_AbsExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_AbsExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_AbsExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_AbsExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_AbsExpr { self.tree.project_g_TinyExpressionP4AST_2e_AbsExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_arg(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_RoundExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_RoundExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_RoundExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_RoundExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_RoundExpr { self.tree.project_g_TinyExpressionP4AST_2e_RoundExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_arg(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_CeilExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_CeilExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_CeilExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_CeilExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_CeilExpr { self.tree.project_g_TinyExpressionP4AST_2e_CeilExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_arg(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_FloorExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_FloorExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_FloorExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_FloorExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_FloorExpr { self.tree.project_g_TinyExpressionP4AST_2e_FloorExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_arg(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_PowExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_PowExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_PowExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_PowExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_PowExpr { self.tree.project_g_TinyExpressionP4AST_2e_PowExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_base(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
pub fn g_exponent(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 1) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_LogExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_LogExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_LogExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_LogExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_LogExpr { self.tree.project_g_TinyExpressionP4AST_2e_LogExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_arg(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_ExpExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_ExpExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_ExpExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_ExpExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_ExpExpr { self.tree.project_g_TinyExpressionP4AST_2e_ExpExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_arg(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_ToNumExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_ToNumExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_ToNumExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_ToNumExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_ToNumExpr { self.tree.project_g_TinyExpressionP4AST_2e_ToNumExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_value(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
pub fn g_defaultValue(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 1) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_ToUpperCaseExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_ToUpperCaseExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_ToUpperCaseExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_ToUpperCaseExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_ToUpperCaseExpr { self.tree.project_g_TinyExpressionP4AST_2e_ToUpperCaseExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_value(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_ToLowerCaseExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_ToLowerCaseExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_ToLowerCaseExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_ToLowerCaseExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_ToLowerCaseExpr { self.tree.project_g_TinyExpressionP4AST_2e_ToLowerCaseExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_value(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_TrimExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_TrimExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_TrimExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_TrimExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_TrimExpr { self.tree.project_g_TinyExpressionP4AST_2e_TrimExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_value(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_LengthExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_LengthExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_LengthExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_LengthExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_LengthExpr { self.tree.project_g_TinyExpressionP4AST_2e_LengthExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_value(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_ToUpperCaseDotExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_ToUpperCaseDotExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_ToUpperCaseDotExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_ToUpperCaseDotExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_ToUpperCaseDotExpr { self.tree.project_g_TinyExpressionP4AST_2e_ToUpperCaseDotExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_value(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_ToLowerCaseDotExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_ToLowerCaseDotExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_ToLowerCaseDotExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_ToLowerCaseDotExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_ToLowerCaseDotExpr { self.tree.project_g_TinyExpressionP4AST_2e_ToLowerCaseDotExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_value(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_TrimDotExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_TrimDotExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_TrimDotExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_TrimDotExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_TrimDotExpr { self.tree.project_g_TinyExpressionP4AST_2e_TrimDotExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_value(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_LengthDotExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_LengthDotExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_LengthDotExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_LengthDotExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_LengthDotExpr { self.tree.project_g_TinyExpressionP4AST_2e_LengthDotExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_value(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_StartsWithExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_StartsWithExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_StartsWithExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_StartsWithExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_StartsWithExpr { self.tree.project_g_TinyExpressionP4AST_2e_StartsWithExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_value(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
pub fn g_patterns(&self) -> NodeList<'t> { NodeList { tree: self.tree, ids: self.tree.items(self.id, 1) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_EndsWithExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_EndsWithExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_EndsWithExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_EndsWithExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_EndsWithExpr { self.tree.project_g_TinyExpressionP4AST_2e_EndsWithExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_value(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
pub fn g_patterns(&self) -> NodeList<'t> { NodeList { tree: self.tree, ids: self.tree.items(self.id, 1) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_ContainsExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_ContainsExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_ContainsExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_ContainsExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_ContainsExpr { self.tree.project_g_TinyExpressionP4AST_2e_ContainsExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_value(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
pub fn g_patterns(&self) -> NodeList<'t> { NodeList { tree: self.tree, ids: self.tree.items(self.id, 1) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_InExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_InExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_InExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_InExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_InExpr { self.tree.project_g_TinyExpressionP4AST_2e_InExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_value(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
pub fn g_candidates(&self) -> NodeList<'t> { NodeList { tree: self.tree, ids: self.tree.items(self.id, 1) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_StartsWithDotExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_StartsWithDotExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_StartsWithDotExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_StartsWithDotExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_StartsWithDotExpr { self.tree.project_g_TinyExpressionP4AST_2e_StartsWithDotExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_value(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
pub fn g_patterns(&self) -> NodeList<'t> { NodeList { tree: self.tree, ids: self.tree.items(self.id, 1) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_EndsWithDotExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_EndsWithDotExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_EndsWithDotExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_EndsWithDotExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_EndsWithDotExpr { self.tree.project_g_TinyExpressionP4AST_2e_EndsWithDotExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_value(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
pub fn g_patterns(&self) -> NodeList<'t> { NodeList { tree: self.tree, ids: self.tree.items(self.id, 1) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_ContainsDotExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_ContainsDotExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_ContainsDotExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_ContainsDotExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_ContainsDotExpr { self.tree.project_g_TinyExpressionP4AST_2e_ContainsDotExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_value(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
pub fn g_patterns(&self) -> NodeList<'t> { NodeList { tree: self.tree, ids: self.tree.items(self.id, 1) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_IsPresentExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_IsPresentExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_IsPresentExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_IsPresentExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_IsPresentExpr { self.tree.project_g_TinyExpressionP4AST_2e_IsPresentExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_value(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_InTimeRangeExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_InTimeRangeExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_InTimeRangeExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_InTimeRangeExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_InTimeRangeExpr { self.tree.project_g_TinyExpressionP4AST_2e_InTimeRangeExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_startHour(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
pub fn g_endHour(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 1) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_InDayTimeRangeExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_InDayTimeRangeExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_InDayTimeRangeExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_InDayTimeRangeExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_InDayTimeRangeExpr { self.tree.project_g_TinyExpressionP4AST_2e_InDayTimeRangeExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_startDay(&self) -> &'t str { let tree = self.tree; tree.text_in(&tree.source, tree.slot(self.id, 0)) }
pub fn g_startHour(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 1) } }
pub fn g_endDay(&self) -> &'t str { let tree = self.tree; tree.text_in(&tree.source, tree.slot(self.id, 2)) }
pub fn g_endHour(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 3) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_SliceExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_SliceExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_SliceExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_SliceExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_SliceExpr { self.tree.project_g_TinyExpressionP4AST_2e_SliceExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_value(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
pub fn g_start(&self) -> Option<NodeRef<'t>> { let v = self.tree.slot(self.id, 1); (v != NONE).then_some(NodeRef { tree: self.tree, id: v }) }
pub fn g_end(&self) -> Option<NodeRef<'t>> { let v = self.tree.slot(self.id, 2); (v != NONE).then_some(NodeRef { tree: self.tree, id: v }) }
pub fn g_step(&self) -> Option<NodeRef<'t>> { let v = self.tree.slot(self.id, 3); (v != NONE).then_some(NodeRef { tree: self.tree, id: v }) }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_StringConcatExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_StringConcatExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_StringConcatExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_StringConcatExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_StringConcatExpr { self.tree.project_g_TinyExpressionP4AST_2e_StringConcatExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_left(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
pub fn g_op(&self) -> TextList<'t> { TextList { tree: self.tree, ids: self.tree.items(self.id, 1) } }
pub fn g_right(&self) -> NodeList<'t> { NodeList { tree: self.tree, ids: self.tree.items(self.id, 3) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_StringCastVariableRefExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_StringCastVariableRefExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_StringCastVariableRefExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_StringCastVariableRefExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_StringCastVariableRefExpr { self.tree.project_g_TinyExpressionP4AST_2e_StringCastVariableRefExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_name(&self) -> &'t str { let tree = self.tree; tree.text_in(&tree.source, tree.slot(self.id, 0)) }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_StringTypedVariableRefExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_StringTypedVariableRefExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_StringTypedVariableRefExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_StringTypedVariableRefExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_StringTypedVariableRefExpr { self.tree.project_g_TinyExpressionP4AST_2e_StringTypedVariableRefExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_name(&self) -> &'t str { let tree = self.tree; tree.text_in(&tree.source, tree.slot(self.id, 0)) }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_BooleanOrExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_BooleanOrExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_BooleanOrExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_BooleanOrExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_BooleanOrExpr { self.tree.project_g_TinyExpressionP4AST_2e_BooleanOrExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_left(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
pub fn g_op(&self) -> TextList<'t> { TextList { tree: self.tree, ids: self.tree.items(self.id, 1) } }
pub fn g_right(&self) -> NodeList<'t> { NodeList { tree: self.tree, ids: self.tree.items(self.id, 3) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_BooleanAndExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_BooleanAndExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_BooleanAndExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_BooleanAndExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_BooleanAndExpr { self.tree.project_g_TinyExpressionP4AST_2e_BooleanAndExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_left(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
pub fn g_op(&self) -> TextList<'t> { TextList { tree: self.tree, ids: self.tree.items(self.id, 1) } }
pub fn g_right(&self) -> NodeList<'t> { NodeList { tree: self.tree, ids: self.tree.items(self.id, 3) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_BooleanXorExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_BooleanXorExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_BooleanXorExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_BooleanXorExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_BooleanXorExpr { self.tree.project_g_TinyExpressionP4AST_2e_BooleanXorExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_left(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
pub fn g_op(&self) -> TextList<'t> { TextList { tree: self.tree, ids: self.tree.items(self.id, 1) } }
pub fn g_right(&self) -> NodeList<'t> { NodeList { tree: self.tree, ids: self.tree.items(self.id, 3) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_NotExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_NotExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_NotExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_NotExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_NotExpr { self.tree.project_g_TinyExpressionP4AST_2e_NotExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_value(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_BooleanEqualityExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_BooleanEqualityExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_BooleanEqualityExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_BooleanEqualityExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_BooleanEqualityExpr { self.tree.project_g_TinyExpressionP4AST_2e_BooleanEqualityExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_left(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
pub fn g_op(&self) -> &'t str { let tree = self.tree; tree.text_in(&tree.source, tree.slot(self.id, 1)) }
pub fn g_right(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 2) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_BooleanFactorExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_BooleanFactorExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_BooleanFactorExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_BooleanFactorExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_BooleanFactorExpr { self.tree.project_g_TinyExpressionP4AST_2e_BooleanFactorExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_value(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_StringComparisonExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_StringComparisonExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_StringComparisonExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_StringComparisonExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_StringComparisonExpr { self.tree.project_g_TinyExpressionP4AST_2e_StringComparisonExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_left(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
pub fn g_op(&self) -> &'t str { let tree = self.tree; tree.text_in(&tree.source, tree.slot(self.id, 1)) }
pub fn g_right(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 2) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_ComparisonExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_ComparisonExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_ComparisonExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_ComparisonExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_ComparisonExpr { self.tree.project_g_TinyExpressionP4AST_2e_ComparisonExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_left(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
pub fn g_op(&self) -> &'t str { let tree = self.tree; tree.text_in(&tree.source, tree.slot(self.id, 1)) }
pub fn g_right(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 2) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_ObjectExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_ObjectExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_ObjectExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_ObjectExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_ObjectExpr { self.tree.project_g_TinyExpressionP4AST_2e_ObjectExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_value(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_IfExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_IfExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_IfExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_IfExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_IfExpr { self.tree.project_g_TinyExpressionP4AST_2e_IfExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_condition(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
pub fn g_thenExpr(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 1) } }
pub fn g_elseExpr(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 2) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_BranchExpressionExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_BranchExpressionExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_BranchExpressionExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_BranchExpressionExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_BranchExpressionExpr { self.tree.project_g_TinyExpressionP4AST_2e_BranchExpressionExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_value(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_NumberMatchExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_NumberMatchExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_NumberMatchExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_NumberMatchExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_NumberMatchExpr { self.tree.project_g_TinyExpressionP4AST_2e_NumberMatchExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_firstCase(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
pub fn g_moreCases(&self) -> NodeList<'t> { NodeList { tree: self.tree, ids: self.tree.items(self.id, 1) } }
pub fn g_defaultCase(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 3) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_NumberCaseExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_NumberCaseExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_NumberCaseExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_NumberCaseExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_NumberCaseExpr { self.tree.project_g_TinyExpressionP4AST_2e_NumberCaseExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_condition(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
pub fn g_value(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 1) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_NumberDefaultCaseExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_NumberDefaultCaseExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_NumberDefaultCaseExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_NumberDefaultCaseExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_NumberDefaultCaseExpr { self.tree.project_g_TinyExpressionP4AST_2e_NumberDefaultCaseExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_value(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_NumberCaseValueExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_NumberCaseValueExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_NumberCaseValueExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_NumberCaseValueExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_NumberCaseValueExpr { self.tree.project_g_TinyExpressionP4AST_2e_NumberCaseValueExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_value(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_StringMatchExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_StringMatchExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_StringMatchExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_StringMatchExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_StringMatchExpr { self.tree.project_g_TinyExpressionP4AST_2e_StringMatchExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_firstCase(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
pub fn g_moreCases(&self) -> NodeList<'t> { NodeList { tree: self.tree, ids: self.tree.items(self.id, 1) } }
pub fn g_defaultCase(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 3) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_StringCaseExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_StringCaseExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_StringCaseExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_StringCaseExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_StringCaseExpr { self.tree.project_g_TinyExpressionP4AST_2e_StringCaseExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_condition(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
pub fn g_value(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 1) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_StringDefaultCaseExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_StringDefaultCaseExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_StringDefaultCaseExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_StringDefaultCaseExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_StringDefaultCaseExpr { self.tree.project_g_TinyExpressionP4AST_2e_StringDefaultCaseExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_value(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_StringCaseValueExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_StringCaseValueExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_StringCaseValueExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_StringCaseValueExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_StringCaseValueExpr { self.tree.project_g_TinyExpressionP4AST_2e_StringCaseValueExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_value(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_BooleanMatchExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_BooleanMatchExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_BooleanMatchExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_BooleanMatchExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_BooleanMatchExpr { self.tree.project_g_TinyExpressionP4AST_2e_BooleanMatchExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_firstCase(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
pub fn g_moreCases(&self) -> NodeList<'t> { NodeList { tree: self.tree, ids: self.tree.items(self.id, 1) } }
pub fn g_defaultCase(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 3) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_BooleanCaseExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_BooleanCaseExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_BooleanCaseExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_BooleanCaseExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_BooleanCaseExpr { self.tree.project_g_TinyExpressionP4AST_2e_BooleanCaseExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_condition(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
pub fn g_value(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 1) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_BooleanDefaultCaseExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_BooleanDefaultCaseExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_BooleanDefaultCaseExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_BooleanDefaultCaseExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_BooleanDefaultCaseExpr { self.tree.project_g_TinyExpressionP4AST_2e_BooleanDefaultCaseExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_value(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_BooleanCaseValueExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_BooleanCaseValueExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_BooleanCaseValueExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_BooleanCaseValueExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_BooleanCaseValueExpr { self.tree.project_g_TinyExpressionP4AST_2e_BooleanCaseValueExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_value(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_VariableRefExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_VariableRefExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_VariableRefExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_VariableRefExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_VariableRefExpr { self.tree.project_g_TinyExpressionP4AST_2e_VariableRefExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_name(&self) -> &'t str { let tree = self.tree; tree.text_in(&tree.source, tree.slot(self.id, 0)) }
pub fn g_type(&self) -> Option<&'t str> { let tree = self.tree; let v = tree.slot(self.id, 1); (v != NONE).then(|| tree.text_in(&tree.source, v)) }
}
#[derive(Clone, Copy)] pub struct g_TinyExpressionP4AST_2e_ExpressionExpr<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_TinyExpressionP4AST_2e_ExpressionExpr<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_TinyExpressionP4AST_2e_ExpressionExpr").field("id", &self.id).finish() } }
impl<'t> g_TinyExpressionP4AST_2e_ExpressionExpr<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_TinyExpressionP4AST_2e_ExpressionExpr { self.tree.project_g_TinyExpressionP4AST_2e_ExpressionExpr(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_value(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 0) } }
}
impl AstTree {
/// 節点を所有 `Ast` へ写す。`source` は Text の byte 範囲を読む入力。
#[doc(hidden)] pub fn project(&self, source: &str, id: u32) -> super::Ast { let node = self.nodes[id as usize]; match node.kind { KIND_TEXT => super::Ast::Text(source[node.a as usize..node.b as usize].to_owned()), KIND_NULL => super::Ast::Null,
K_g_TinyExpressionP4AST_2e_FormulaExpr => super::Ast::g_TinyExpressionP4AST_2e_FormulaExpr(self.project_g_TinyExpressionP4AST_2e_FormulaExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_CodeBlockExpr => super::Ast::g_TinyExpressionP4AST_2e_CodeBlockExpr(self.project_g_TinyExpressionP4AST_2e_CodeBlockExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_ImportDeclarationExpr => super::Ast::g_TinyExpressionP4AST_2e_ImportDeclarationExpr(self.project_g_TinyExpressionP4AST_2e_ImportDeclarationExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_QualifiedNameExpr => super::Ast::g_TinyExpressionP4AST_2e_QualifiedNameExpr(self.project_g_TinyExpressionP4AST_2e_QualifiedNameExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_NumberVariableDeclarationExpr => super::Ast::g_TinyExpressionP4AST_2e_NumberVariableDeclarationExpr(self.project_g_TinyExpressionP4AST_2e_NumberVariableDeclarationExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_StringVariableDeclarationExpr => super::Ast::g_TinyExpressionP4AST_2e_StringVariableDeclarationExpr(self.project_g_TinyExpressionP4AST_2e_StringVariableDeclarationExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_BooleanVariableDeclarationExpr => super::Ast::g_TinyExpressionP4AST_2e_BooleanVariableDeclarationExpr(self.project_g_TinyExpressionP4AST_2e_BooleanVariableDeclarationExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_ObjectVariableDeclarationExpr => super::Ast::g_TinyExpressionP4AST_2e_ObjectVariableDeclarationExpr(self.project_g_TinyExpressionP4AST_2e_ObjectVariableDeclarationExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_OnlyIfAbsentExpr => super::Ast::g_TinyExpressionP4AST_2e_OnlyIfAbsentExpr(self.project_g_TinyExpressionP4AST_2e_OnlyIfAbsentExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_NumberMethodDeclarationExpr => super::Ast::g_TinyExpressionP4AST_2e_NumberMethodDeclarationExpr(self.project_g_TinyExpressionP4AST_2e_NumberMethodDeclarationExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_StringMethodDeclarationExpr => super::Ast::g_TinyExpressionP4AST_2e_StringMethodDeclarationExpr(self.project_g_TinyExpressionP4AST_2e_StringMethodDeclarationExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_BooleanMethodDeclarationExpr => super::Ast::g_TinyExpressionP4AST_2e_BooleanMethodDeclarationExpr(self.project_g_TinyExpressionP4AST_2e_BooleanMethodDeclarationExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_ObjectMethodDeclarationExpr => super::Ast::g_TinyExpressionP4AST_2e_ObjectMethodDeclarationExpr(self.project_g_TinyExpressionP4AST_2e_ObjectMethodDeclarationExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_MethodParametersExpr => super::Ast::g_TinyExpressionP4AST_2e_MethodParametersExpr(self.project_g_TinyExpressionP4AST_2e_MethodParametersExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_MethodParameterExpr => super::Ast::g_TinyExpressionP4AST_2e_MethodParameterExpr(self.project_g_TinyExpressionP4AST_2e_MethodParameterExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_ExternalBooleanInvocationExpr => super::Ast::g_TinyExpressionP4AST_2e_ExternalBooleanInvocationExpr(self.project_g_TinyExpressionP4AST_2e_ExternalBooleanInvocationExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_ExternalNumberInvocationExpr => super::Ast::g_TinyExpressionP4AST_2e_ExternalNumberInvocationExpr(self.project_g_TinyExpressionP4AST_2e_ExternalNumberInvocationExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_ExternalStringInvocationExpr => super::Ast::g_TinyExpressionP4AST_2e_ExternalStringInvocationExpr(self.project_g_TinyExpressionP4AST_2e_ExternalStringInvocationExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_ExternalObjectInvocationExpr => super::Ast::g_TinyExpressionP4AST_2e_ExternalObjectInvocationExpr(self.project_g_TinyExpressionP4AST_2e_ExternalObjectInvocationExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_MethodInvocationExpr => super::Ast::g_TinyExpressionP4AST_2e_MethodInvocationExpr(self.project_g_TinyExpressionP4AST_2e_MethodInvocationExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_TernaryExpr => super::Ast::g_TinyExpressionP4AST_2e_TernaryExpr(self.project_g_TinyExpressionP4AST_2e_TernaryExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_ArgumentExpressionExpr => super::Ast::g_TinyExpressionP4AST_2e_ArgumentExpressionExpr(self.project_g_TinyExpressionP4AST_2e_ArgumentExpressionExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_ArgumentsExpr => super::Ast::g_TinyExpressionP4AST_2e_ArgumentsExpr(self.project_g_TinyExpressionP4AST_2e_ArgumentsExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_BinaryExpr => super::Ast::g_TinyExpressionP4AST_2e_BinaryExpr(self.project_g_TinyExpressionP4AST_2e_BinaryExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_SinExpr => super::Ast::g_TinyExpressionP4AST_2e_SinExpr(self.project_g_TinyExpressionP4AST_2e_SinExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_CosExpr => super::Ast::g_TinyExpressionP4AST_2e_CosExpr(self.project_g_TinyExpressionP4AST_2e_CosExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_TanExpr => super::Ast::g_TinyExpressionP4AST_2e_TanExpr(self.project_g_TinyExpressionP4AST_2e_TanExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_SqrtExpr => super::Ast::g_TinyExpressionP4AST_2e_SqrtExpr(self.project_g_TinyExpressionP4AST_2e_SqrtExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_MinExpr => super::Ast::g_TinyExpressionP4AST_2e_MinExpr(self.project_g_TinyExpressionP4AST_2e_MinExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_MaxExpr => super::Ast::g_TinyExpressionP4AST_2e_MaxExpr(self.project_g_TinyExpressionP4AST_2e_MaxExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_RandomExpr => super::Ast::g_TinyExpressionP4AST_2e_RandomExpr(self.project_g_TinyExpressionP4AST_2e_RandomExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_AbsExpr => super::Ast::g_TinyExpressionP4AST_2e_AbsExpr(self.project_g_TinyExpressionP4AST_2e_AbsExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_RoundExpr => super::Ast::g_TinyExpressionP4AST_2e_RoundExpr(self.project_g_TinyExpressionP4AST_2e_RoundExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_CeilExpr => super::Ast::g_TinyExpressionP4AST_2e_CeilExpr(self.project_g_TinyExpressionP4AST_2e_CeilExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_FloorExpr => super::Ast::g_TinyExpressionP4AST_2e_FloorExpr(self.project_g_TinyExpressionP4AST_2e_FloorExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_PowExpr => super::Ast::g_TinyExpressionP4AST_2e_PowExpr(self.project_g_TinyExpressionP4AST_2e_PowExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_LogExpr => super::Ast::g_TinyExpressionP4AST_2e_LogExpr(self.project_g_TinyExpressionP4AST_2e_LogExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_ExpExpr => super::Ast::g_TinyExpressionP4AST_2e_ExpExpr(self.project_g_TinyExpressionP4AST_2e_ExpExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_ToNumExpr => super::Ast::g_TinyExpressionP4AST_2e_ToNumExpr(self.project_g_TinyExpressionP4AST_2e_ToNumExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_ToUpperCaseExpr => super::Ast::g_TinyExpressionP4AST_2e_ToUpperCaseExpr(self.project_g_TinyExpressionP4AST_2e_ToUpperCaseExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_ToLowerCaseExpr => super::Ast::g_TinyExpressionP4AST_2e_ToLowerCaseExpr(self.project_g_TinyExpressionP4AST_2e_ToLowerCaseExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_TrimExpr => super::Ast::g_TinyExpressionP4AST_2e_TrimExpr(self.project_g_TinyExpressionP4AST_2e_TrimExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_LengthExpr => super::Ast::g_TinyExpressionP4AST_2e_LengthExpr(self.project_g_TinyExpressionP4AST_2e_LengthExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_ToUpperCaseDotExpr => super::Ast::g_TinyExpressionP4AST_2e_ToUpperCaseDotExpr(self.project_g_TinyExpressionP4AST_2e_ToUpperCaseDotExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_ToLowerCaseDotExpr => super::Ast::g_TinyExpressionP4AST_2e_ToLowerCaseDotExpr(self.project_g_TinyExpressionP4AST_2e_ToLowerCaseDotExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_TrimDotExpr => super::Ast::g_TinyExpressionP4AST_2e_TrimDotExpr(self.project_g_TinyExpressionP4AST_2e_TrimDotExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_LengthDotExpr => super::Ast::g_TinyExpressionP4AST_2e_LengthDotExpr(self.project_g_TinyExpressionP4AST_2e_LengthDotExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_StartsWithExpr => super::Ast::g_TinyExpressionP4AST_2e_StartsWithExpr(self.project_g_TinyExpressionP4AST_2e_StartsWithExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_EndsWithExpr => super::Ast::g_TinyExpressionP4AST_2e_EndsWithExpr(self.project_g_TinyExpressionP4AST_2e_EndsWithExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_ContainsExpr => super::Ast::g_TinyExpressionP4AST_2e_ContainsExpr(self.project_g_TinyExpressionP4AST_2e_ContainsExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_InExpr => super::Ast::g_TinyExpressionP4AST_2e_InExpr(self.project_g_TinyExpressionP4AST_2e_InExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_StartsWithDotExpr => super::Ast::g_TinyExpressionP4AST_2e_StartsWithDotExpr(self.project_g_TinyExpressionP4AST_2e_StartsWithDotExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_EndsWithDotExpr => super::Ast::g_TinyExpressionP4AST_2e_EndsWithDotExpr(self.project_g_TinyExpressionP4AST_2e_EndsWithDotExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_ContainsDotExpr => super::Ast::g_TinyExpressionP4AST_2e_ContainsDotExpr(self.project_g_TinyExpressionP4AST_2e_ContainsDotExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_IsPresentExpr => super::Ast::g_TinyExpressionP4AST_2e_IsPresentExpr(self.project_g_TinyExpressionP4AST_2e_IsPresentExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_InTimeRangeExpr => super::Ast::g_TinyExpressionP4AST_2e_InTimeRangeExpr(self.project_g_TinyExpressionP4AST_2e_InTimeRangeExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_InDayTimeRangeExpr => super::Ast::g_TinyExpressionP4AST_2e_InDayTimeRangeExpr(self.project_g_TinyExpressionP4AST_2e_InDayTimeRangeExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_SliceExpr => super::Ast::g_TinyExpressionP4AST_2e_SliceExpr(self.project_g_TinyExpressionP4AST_2e_SliceExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_StringConcatExpr => super::Ast::g_TinyExpressionP4AST_2e_StringConcatExpr(self.project_g_TinyExpressionP4AST_2e_StringConcatExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_StringCastVariableRefExpr => super::Ast::g_TinyExpressionP4AST_2e_StringCastVariableRefExpr(self.project_g_TinyExpressionP4AST_2e_StringCastVariableRefExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_StringTypedVariableRefExpr => super::Ast::g_TinyExpressionP4AST_2e_StringTypedVariableRefExpr(self.project_g_TinyExpressionP4AST_2e_StringTypedVariableRefExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_BooleanOrExpr => super::Ast::g_TinyExpressionP4AST_2e_BooleanOrExpr(self.project_g_TinyExpressionP4AST_2e_BooleanOrExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_BooleanAndExpr => super::Ast::g_TinyExpressionP4AST_2e_BooleanAndExpr(self.project_g_TinyExpressionP4AST_2e_BooleanAndExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_BooleanXorExpr => super::Ast::g_TinyExpressionP4AST_2e_BooleanXorExpr(self.project_g_TinyExpressionP4AST_2e_BooleanXorExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_NotExpr => super::Ast::g_TinyExpressionP4AST_2e_NotExpr(self.project_g_TinyExpressionP4AST_2e_NotExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_BooleanEqualityExpr => super::Ast::g_TinyExpressionP4AST_2e_BooleanEqualityExpr(self.project_g_TinyExpressionP4AST_2e_BooleanEqualityExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_BooleanFactorExpr => super::Ast::g_TinyExpressionP4AST_2e_BooleanFactorExpr(self.project_g_TinyExpressionP4AST_2e_BooleanFactorExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_StringComparisonExpr => super::Ast::g_TinyExpressionP4AST_2e_StringComparisonExpr(self.project_g_TinyExpressionP4AST_2e_StringComparisonExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_ComparisonExpr => super::Ast::g_TinyExpressionP4AST_2e_ComparisonExpr(self.project_g_TinyExpressionP4AST_2e_ComparisonExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_ObjectExpr => super::Ast::g_TinyExpressionP4AST_2e_ObjectExpr(self.project_g_TinyExpressionP4AST_2e_ObjectExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_IfExpr => super::Ast::g_TinyExpressionP4AST_2e_IfExpr(self.project_g_TinyExpressionP4AST_2e_IfExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_BranchExpressionExpr => super::Ast::g_TinyExpressionP4AST_2e_BranchExpressionExpr(self.project_g_TinyExpressionP4AST_2e_BranchExpressionExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_NumberMatchExpr => super::Ast::g_TinyExpressionP4AST_2e_NumberMatchExpr(self.project_g_TinyExpressionP4AST_2e_NumberMatchExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_NumberCaseExpr => super::Ast::g_TinyExpressionP4AST_2e_NumberCaseExpr(self.project_g_TinyExpressionP4AST_2e_NumberCaseExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_NumberDefaultCaseExpr => super::Ast::g_TinyExpressionP4AST_2e_NumberDefaultCaseExpr(self.project_g_TinyExpressionP4AST_2e_NumberDefaultCaseExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_NumberCaseValueExpr => super::Ast::g_TinyExpressionP4AST_2e_NumberCaseValueExpr(self.project_g_TinyExpressionP4AST_2e_NumberCaseValueExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_StringMatchExpr => super::Ast::g_TinyExpressionP4AST_2e_StringMatchExpr(self.project_g_TinyExpressionP4AST_2e_StringMatchExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_StringCaseExpr => super::Ast::g_TinyExpressionP4AST_2e_StringCaseExpr(self.project_g_TinyExpressionP4AST_2e_StringCaseExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_StringDefaultCaseExpr => super::Ast::g_TinyExpressionP4AST_2e_StringDefaultCaseExpr(self.project_g_TinyExpressionP4AST_2e_StringDefaultCaseExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_StringCaseValueExpr => super::Ast::g_TinyExpressionP4AST_2e_StringCaseValueExpr(self.project_g_TinyExpressionP4AST_2e_StringCaseValueExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_BooleanMatchExpr => super::Ast::g_TinyExpressionP4AST_2e_BooleanMatchExpr(self.project_g_TinyExpressionP4AST_2e_BooleanMatchExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_BooleanCaseExpr => super::Ast::g_TinyExpressionP4AST_2e_BooleanCaseExpr(self.project_g_TinyExpressionP4AST_2e_BooleanCaseExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_BooleanDefaultCaseExpr => super::Ast::g_TinyExpressionP4AST_2e_BooleanDefaultCaseExpr(self.project_g_TinyExpressionP4AST_2e_BooleanDefaultCaseExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_BooleanCaseValueExpr => super::Ast::g_TinyExpressionP4AST_2e_BooleanCaseValueExpr(self.project_g_TinyExpressionP4AST_2e_BooleanCaseValueExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_VariableRefExpr => super::Ast::g_TinyExpressionP4AST_2e_VariableRefExpr(self.project_g_TinyExpressionP4AST_2e_VariableRefExpr(source, &node)),
K_g_TinyExpressionP4AST_2e_ExpressionExpr => super::Ast::g_TinyExpressionP4AST_2e_ExpressionExpr(self.project_g_TinyExpressionP4AST_2e_ExpressionExpr(source, &node)),
_ => unreachable!("unknown AST node kind"), } }
fn project_g_TinyExpressionP4AST_2e_FormulaExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_FormulaExpr { let f = &self.slots[node.a as usize..node.a as usize + 7]; super::g_TinyExpressionP4AST_2e_FormulaExpr { span: span_of(node), node_id: node.node_id as usize,
g_imports: self.list_at(f[0], f[1]).iter().map(|&v| self.project(source, v)).collect(),
g_declarations: self.list_at(f[2], f[3]).iter().map(|&v| self.project(source, v)).collect(),
g_expression: Box::new(self.project(source, f[4])),
g_methods: self.list_at(f[5], f[6]).iter().map(|&v| self.project(source, v)).collect(),
} }
fn project_g_TinyExpressionP4AST_2e_CodeBlockExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_CodeBlockExpr { let _ = source; super::g_TinyExpressionP4AST_2e_CodeBlockExpr { span: span_of(node), node_id: node.node_id as usize,
} }
fn project_g_TinyExpressionP4AST_2e_ImportDeclarationExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_ImportDeclarationExpr { let f = &self.slots[node.a as usize..node.a as usize + 3]; super::g_TinyExpressionP4AST_2e_ImportDeclarationExpr { span: span_of(node), node_id: node.node_id as usize,
g_className: Box::new(self.project(source, f[0])),
g_method: { let v = f[1]; (v != NONE).then(|| self.text_in(source, v).to_owned()) },
g_alias: self.text_in(source, f[2]).to_owned(),
} }
fn project_g_TinyExpressionP4AST_2e_QualifiedNameExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_QualifiedNameExpr { let f = &self.slots[node.a as usize..node.a as usize + 3]; super::g_TinyExpressionP4AST_2e_QualifiedNameExpr { span: span_of(node), node_id: node.node_id as usize,
g_head: self.text_in(source, f[0]).to_owned(),
g_tail: self.list_at(f[1], f[2]).iter().map(|&v| self.text_in(source, v).to_owned()).collect(),
} }
fn project_g_TinyExpressionP4AST_2e_NumberVariableDeclarationExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_NumberVariableDeclarationExpr { let f = &self.slots[node.a as usize..node.a as usize + 4]; super::g_TinyExpressionP4AST_2e_NumberVariableDeclarationExpr { span: span_of(node), node_id: node.node_id as usize,
g_varName: self.text_in(source, f[0]).to_owned(),
g_onlyIfAbsent: { let v = f[1]; (v != NONE).then(|| Box::new(self.project(source, v))) },
g_value: { let v = f[2]; (v != NONE).then(|| Box::new(self.project(source, v))) },
g_desc: { let v = f[3]; (v != NONE).then(|| self.text_in(source, v).to_owned()) },
} }
fn project_g_TinyExpressionP4AST_2e_StringVariableDeclarationExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_StringVariableDeclarationExpr { let f = &self.slots[node.a as usize..node.a as usize + 4]; super::g_TinyExpressionP4AST_2e_StringVariableDeclarationExpr { span: span_of(node), node_id: node.node_id as usize,
g_varName: self.text_in(source, f[0]).to_owned(),
g_onlyIfAbsent: { let v = f[1]; (v != NONE).then(|| Box::new(self.project(source, v))) },
g_value: { let v = f[2]; (v != NONE).then(|| Box::new(self.project(source, v))) },
g_desc: { let v = f[3]; (v != NONE).then(|| self.text_in(source, v).to_owned()) },
} }
fn project_g_TinyExpressionP4AST_2e_BooleanVariableDeclarationExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_BooleanVariableDeclarationExpr { let f = &self.slots[node.a as usize..node.a as usize + 4]; super::g_TinyExpressionP4AST_2e_BooleanVariableDeclarationExpr { span: span_of(node), node_id: node.node_id as usize,
g_varName: self.text_in(source, f[0]).to_owned(),
g_onlyIfAbsent: { let v = f[1]; (v != NONE).then(|| Box::new(self.project(source, v))) },
g_value: { let v = f[2]; (v != NONE).then(|| Box::new(self.project(source, v))) },
g_desc: { let v = f[3]; (v != NONE).then(|| self.text_in(source, v).to_owned()) },
} }
fn project_g_TinyExpressionP4AST_2e_ObjectVariableDeclarationExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_ObjectVariableDeclarationExpr { let f = &self.slots[node.a as usize..node.a as usize + 4]; super::g_TinyExpressionP4AST_2e_ObjectVariableDeclarationExpr { span: span_of(node), node_id: node.node_id as usize,
g_varName: self.text_in(source, f[0]).to_owned(),
g_onlyIfAbsent: { let v = f[1]; (v != NONE).then(|| Box::new(self.project(source, v))) },
g_value: { let v = f[2]; (v != NONE).then(|| Box::new(self.project(source, v))) },
g_desc: { let v = f[3]; (v != NONE).then(|| self.text_in(source, v).to_owned()) },
} }
fn project_g_TinyExpressionP4AST_2e_OnlyIfAbsentExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_OnlyIfAbsentExpr { let _ = source; super::g_TinyExpressionP4AST_2e_OnlyIfAbsentExpr { span: span_of(node), node_id: node.node_id as usize,
} }
fn project_g_TinyExpressionP4AST_2e_NumberMethodDeclarationExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_NumberMethodDeclarationExpr { let f = &self.slots[node.a as usize..node.a as usize + 3]; super::g_TinyExpressionP4AST_2e_NumberMethodDeclarationExpr { span: span_of(node), node_id: node.node_id as usize,
g_methodName: self.text_in(source, f[0]).to_owned(),
g_parameters: { let v = f[1]; (v != NONE).then(|| Box::new(self.project(source, v))) },
g_expression: Box::new(self.project(source, f[2])),
} }
fn project_g_TinyExpressionP4AST_2e_StringMethodDeclarationExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_StringMethodDeclarationExpr { let f = &self.slots[node.a as usize..node.a as usize + 3]; super::g_TinyExpressionP4AST_2e_StringMethodDeclarationExpr { span: span_of(node), node_id: node.node_id as usize,
g_methodName: self.text_in(source, f[0]).to_owned(),
g_parameters: { let v = f[1]; (v != NONE).then(|| Box::new(self.project(source, v))) },
g_expression: Box::new(self.project(source, f[2])),
} }
fn project_g_TinyExpressionP4AST_2e_BooleanMethodDeclarationExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_BooleanMethodDeclarationExpr { let f = &self.slots[node.a as usize..node.a as usize + 3]; super::g_TinyExpressionP4AST_2e_BooleanMethodDeclarationExpr { span: span_of(node), node_id: node.node_id as usize,
g_methodName: self.text_in(source, f[0]).to_owned(),
g_parameters: { let v = f[1]; (v != NONE).then(|| Box::new(self.project(source, v))) },
g_expression: Box::new(self.project(source, f[2])),
} }
fn project_g_TinyExpressionP4AST_2e_ObjectMethodDeclarationExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_ObjectMethodDeclarationExpr { let f = &self.slots[node.a as usize..node.a as usize + 3]; super::g_TinyExpressionP4AST_2e_ObjectMethodDeclarationExpr { span: span_of(node), node_id: node.node_id as usize,
g_methodName: self.text_in(source, f[0]).to_owned(),
g_parameters: { let v = f[1]; (v != NONE).then(|| Box::new(self.project(source, v))) },
g_expression: Box::new(self.project(source, f[2])),
} }
fn project_g_TinyExpressionP4AST_2e_MethodParametersExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_MethodParametersExpr { let f = &self.slots[node.a as usize..node.a as usize + 2]; super::g_TinyExpressionP4AST_2e_MethodParametersExpr { span: span_of(node), node_id: node.node_id as usize,
g_values: self.list_at(f[0], f[1]).iter().map(|&v| self.project(source, v)).collect(),
} }
fn project_g_TinyExpressionP4AST_2e_MethodParameterExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_MethodParameterExpr { let f = &self.slots[node.a as usize..node.a as usize + 2]; super::g_TinyExpressionP4AST_2e_MethodParameterExpr { span: span_of(node), node_id: node.node_id as usize,
g_paramName: self.text_in(source, f[0]).to_owned(),
g_type: { let v = f[1]; (v != NONE).then(|| self.text_in(source, v).to_owned()) },
} }
fn project_g_TinyExpressionP4AST_2e_ExternalBooleanInvocationExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_ExternalBooleanInvocationExpr { let f = &self.slots[node.a as usize..node.a as usize + 3]; super::g_TinyExpressionP4AST_2e_ExternalBooleanInvocationExpr { span: span_of(node), node_id: node.node_id as usize,
g_className: { let v = f[0]; (v != NONE).then(|| Box::new(self.project(source, v))) },
g_name: self.text_in(source, f[1]).to_owned(),
g_args: { let v = f[2]; (v != NONE).then(|| Box::new(self.project(source, v))) },
} }
fn project_g_TinyExpressionP4AST_2e_ExternalNumberInvocationExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_ExternalNumberInvocationExpr { let f = &self.slots[node.a as usize..node.a as usize + 3]; super::g_TinyExpressionP4AST_2e_ExternalNumberInvocationExpr { span: span_of(node), node_id: node.node_id as usize,
g_className: { let v = f[0]; (v != NONE).then(|| Box::new(self.project(source, v))) },
g_name: self.text_in(source, f[1]).to_owned(),
g_args: { let v = f[2]; (v != NONE).then(|| Box::new(self.project(source, v))) },
} }
fn project_g_TinyExpressionP4AST_2e_ExternalStringInvocationExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_ExternalStringInvocationExpr { let f = &self.slots[node.a as usize..node.a as usize + 3]; super::g_TinyExpressionP4AST_2e_ExternalStringInvocationExpr { span: span_of(node), node_id: node.node_id as usize,
g_className: { let v = f[0]; (v != NONE).then(|| Box::new(self.project(source, v))) },
g_name: self.text_in(source, f[1]).to_owned(),
g_args: { let v = f[2]; (v != NONE).then(|| Box::new(self.project(source, v))) },
} }
fn project_g_TinyExpressionP4AST_2e_ExternalObjectInvocationExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_ExternalObjectInvocationExpr { let f = &self.slots[node.a as usize..node.a as usize + 3]; super::g_TinyExpressionP4AST_2e_ExternalObjectInvocationExpr { span: span_of(node), node_id: node.node_id as usize,
g_className: { let v = f[0]; (v != NONE).then(|| Box::new(self.project(source, v))) },
g_name: self.text_in(source, f[1]).to_owned(),
g_args: { let v = f[2]; (v != NONE).then(|| Box::new(self.project(source, v))) },
} }
fn project_g_TinyExpressionP4AST_2e_MethodInvocationExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_MethodInvocationExpr { let f = &self.slots[node.a as usize..node.a as usize + 2]; super::g_TinyExpressionP4AST_2e_MethodInvocationExpr { span: span_of(node), node_id: node.node_id as usize,
g_name: self.text_in(source, f[0]).to_owned(),
g_args: { let v = f[1]; (v != NONE).then(|| Box::new(self.project(source, v))) },
} }
fn project_g_TinyExpressionP4AST_2e_TernaryExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_TernaryExpr { let f = &self.slots[node.a as usize..node.a as usize + 3]; super::g_TinyExpressionP4AST_2e_TernaryExpr { span: span_of(node), node_id: node.node_id as usize,
g_condition: Box::new(self.project(source, f[0])),
g_thenExpr: Box::new(self.project(source, f[1])),
g_elseExpr: Box::new(self.project(source, f[2])),
} }
fn project_g_TinyExpressionP4AST_2e_ArgumentExpressionExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_ArgumentExpressionExpr { let f = &self.slots[node.a as usize..node.a as usize + 1]; super::g_TinyExpressionP4AST_2e_ArgumentExpressionExpr { span: span_of(node), node_id: node.node_id as usize,
g_value: Box::new(self.project(source, f[0])),
} }
fn project_g_TinyExpressionP4AST_2e_ArgumentsExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_ArgumentsExpr { let f = &self.slots[node.a as usize..node.a as usize + 2]; super::g_TinyExpressionP4AST_2e_ArgumentsExpr { span: span_of(node), node_id: node.node_id as usize,
g_values: self.list_at(f[0], f[1]).iter().map(|&v| self.project(source, v)).collect(),
} }
fn project_g_TinyExpressionP4AST_2e_BinaryExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_BinaryExpr { let f = &self.slots[node.a as usize..node.a as usize + 5]; super::g_TinyExpressionP4AST_2e_BinaryExpr { span: span_of(node), node_id: node.node_id as usize,
g_left: { let v = f[0]; (v != NONE).then(|| Box::new(self.project(source, v))) },
g_op: self.list_at(f[1], f[2]).iter().map(|&v| self.text_in(source, v).to_owned()).collect(),
g_right: self.list_at(f[3], f[4]).iter().map(|&v| self.project(source, v)).collect(),
} }
fn project_g_TinyExpressionP4AST_2e_SinExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_SinExpr { let f = &self.slots[node.a as usize..node.a as usize + 1]; super::g_TinyExpressionP4AST_2e_SinExpr { span: span_of(node), node_id: node.node_id as usize,
g_arg: Box::new(self.project(source, f[0])),
} }
fn project_g_TinyExpressionP4AST_2e_CosExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_CosExpr { let f = &self.slots[node.a as usize..node.a as usize + 1]; super::g_TinyExpressionP4AST_2e_CosExpr { span: span_of(node), node_id: node.node_id as usize,
g_arg: Box::new(self.project(source, f[0])),
} }
fn project_g_TinyExpressionP4AST_2e_TanExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_TanExpr { let f = &self.slots[node.a as usize..node.a as usize + 1]; super::g_TinyExpressionP4AST_2e_TanExpr { span: span_of(node), node_id: node.node_id as usize,
g_arg: Box::new(self.project(source, f[0])),
} }
fn project_g_TinyExpressionP4AST_2e_SqrtExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_SqrtExpr { let f = &self.slots[node.a as usize..node.a as usize + 1]; super::g_TinyExpressionP4AST_2e_SqrtExpr { span: span_of(node), node_id: node.node_id as usize,
g_arg: Box::new(self.project(source, f[0])),
} }
fn project_g_TinyExpressionP4AST_2e_MinExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_MinExpr { let f = &self.slots[node.a as usize..node.a as usize + 3]; super::g_TinyExpressionP4AST_2e_MinExpr { span: span_of(node), node_id: node.node_id as usize,
g_first: Box::new(self.project(source, f[0])),
g_rest: self.list_at(f[1], f[2]).iter().map(|&v| self.project(source, v)).collect(),
} }
fn project_g_TinyExpressionP4AST_2e_MaxExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_MaxExpr { let f = &self.slots[node.a as usize..node.a as usize + 3]; super::g_TinyExpressionP4AST_2e_MaxExpr { span: span_of(node), node_id: node.node_id as usize,
g_first: Box::new(self.project(source, f[0])),
g_rest: self.list_at(f[1], f[2]).iter().map(|&v| self.project(source, v)).collect(),
} }
fn project_g_TinyExpressionP4AST_2e_RandomExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_RandomExpr { let _ = source; super::g_TinyExpressionP4AST_2e_RandomExpr { span: span_of(node), node_id: node.node_id as usize,
} }
fn project_g_TinyExpressionP4AST_2e_AbsExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_AbsExpr { let f = &self.slots[node.a as usize..node.a as usize + 1]; super::g_TinyExpressionP4AST_2e_AbsExpr { span: span_of(node), node_id: node.node_id as usize,
g_arg: Box::new(self.project(source, f[0])),
} }
fn project_g_TinyExpressionP4AST_2e_RoundExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_RoundExpr { let f = &self.slots[node.a as usize..node.a as usize + 1]; super::g_TinyExpressionP4AST_2e_RoundExpr { span: span_of(node), node_id: node.node_id as usize,
g_arg: Box::new(self.project(source, f[0])),
} }
fn project_g_TinyExpressionP4AST_2e_CeilExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_CeilExpr { let f = &self.slots[node.a as usize..node.a as usize + 1]; super::g_TinyExpressionP4AST_2e_CeilExpr { span: span_of(node), node_id: node.node_id as usize,
g_arg: Box::new(self.project(source, f[0])),
} }
fn project_g_TinyExpressionP4AST_2e_FloorExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_FloorExpr { let f = &self.slots[node.a as usize..node.a as usize + 1]; super::g_TinyExpressionP4AST_2e_FloorExpr { span: span_of(node), node_id: node.node_id as usize,
g_arg: Box::new(self.project(source, f[0])),
} }
fn project_g_TinyExpressionP4AST_2e_PowExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_PowExpr { let f = &self.slots[node.a as usize..node.a as usize + 2]; super::g_TinyExpressionP4AST_2e_PowExpr { span: span_of(node), node_id: node.node_id as usize,
g_base: Box::new(self.project(source, f[0])),
g_exponent: Box::new(self.project(source, f[1])),
} }
fn project_g_TinyExpressionP4AST_2e_LogExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_LogExpr { let f = &self.slots[node.a as usize..node.a as usize + 1]; super::g_TinyExpressionP4AST_2e_LogExpr { span: span_of(node), node_id: node.node_id as usize,
g_arg: Box::new(self.project(source, f[0])),
} }
fn project_g_TinyExpressionP4AST_2e_ExpExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_ExpExpr { let f = &self.slots[node.a as usize..node.a as usize + 1]; super::g_TinyExpressionP4AST_2e_ExpExpr { span: span_of(node), node_id: node.node_id as usize,
g_arg: Box::new(self.project(source, f[0])),
} }
fn project_g_TinyExpressionP4AST_2e_ToNumExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_ToNumExpr { let f = &self.slots[node.a as usize..node.a as usize + 2]; super::g_TinyExpressionP4AST_2e_ToNumExpr { span: span_of(node), node_id: node.node_id as usize,
g_value: Box::new(self.project(source, f[0])),
g_defaultValue: Box::new(self.project(source, f[1])),
} }
fn project_g_TinyExpressionP4AST_2e_ToUpperCaseExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_ToUpperCaseExpr { let f = &self.slots[node.a as usize..node.a as usize + 1]; super::g_TinyExpressionP4AST_2e_ToUpperCaseExpr { span: span_of(node), node_id: node.node_id as usize,
g_value: Box::new(self.project(source, f[0])),
} }
fn project_g_TinyExpressionP4AST_2e_ToLowerCaseExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_ToLowerCaseExpr { let f = &self.slots[node.a as usize..node.a as usize + 1]; super::g_TinyExpressionP4AST_2e_ToLowerCaseExpr { span: span_of(node), node_id: node.node_id as usize,
g_value: Box::new(self.project(source, f[0])),
} }
fn project_g_TinyExpressionP4AST_2e_TrimExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_TrimExpr { let f = &self.slots[node.a as usize..node.a as usize + 1]; super::g_TinyExpressionP4AST_2e_TrimExpr { span: span_of(node), node_id: node.node_id as usize,
g_value: Box::new(self.project(source, f[0])),
} }
fn project_g_TinyExpressionP4AST_2e_LengthExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_LengthExpr { let f = &self.slots[node.a as usize..node.a as usize + 1]; super::g_TinyExpressionP4AST_2e_LengthExpr { span: span_of(node), node_id: node.node_id as usize,
g_value: Box::new(self.project(source, f[0])),
} }
fn project_g_TinyExpressionP4AST_2e_ToUpperCaseDotExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_ToUpperCaseDotExpr { let f = &self.slots[node.a as usize..node.a as usize + 1]; super::g_TinyExpressionP4AST_2e_ToUpperCaseDotExpr { span: span_of(node), node_id: node.node_id as usize,
g_value: Box::new(self.project(source, f[0])),
} }
fn project_g_TinyExpressionP4AST_2e_ToLowerCaseDotExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_ToLowerCaseDotExpr { let f = &self.slots[node.a as usize..node.a as usize + 1]; super::g_TinyExpressionP4AST_2e_ToLowerCaseDotExpr { span: span_of(node), node_id: node.node_id as usize,
g_value: Box::new(self.project(source, f[0])),
} }
fn project_g_TinyExpressionP4AST_2e_TrimDotExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_TrimDotExpr { let f = &self.slots[node.a as usize..node.a as usize + 1]; super::g_TinyExpressionP4AST_2e_TrimDotExpr { span: span_of(node), node_id: node.node_id as usize,
g_value: Box::new(self.project(source, f[0])),
} }
fn project_g_TinyExpressionP4AST_2e_LengthDotExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_LengthDotExpr { let f = &self.slots[node.a as usize..node.a as usize + 1]; super::g_TinyExpressionP4AST_2e_LengthDotExpr { span: span_of(node), node_id: node.node_id as usize,
g_value: Box::new(self.project(source, f[0])),
} }
fn project_g_TinyExpressionP4AST_2e_StartsWithExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_StartsWithExpr { let f = &self.slots[node.a as usize..node.a as usize + 3]; super::g_TinyExpressionP4AST_2e_StartsWithExpr { span: span_of(node), node_id: node.node_id as usize,
g_value: Box::new(self.project(source, f[0])),
g_patterns: self.list_at(f[1], f[2]).iter().map(|&v| self.project(source, v)).collect(),
} }
fn project_g_TinyExpressionP4AST_2e_EndsWithExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_EndsWithExpr { let f = &self.slots[node.a as usize..node.a as usize + 3]; super::g_TinyExpressionP4AST_2e_EndsWithExpr { span: span_of(node), node_id: node.node_id as usize,
g_value: Box::new(self.project(source, f[0])),
g_patterns: self.list_at(f[1], f[2]).iter().map(|&v| self.project(source, v)).collect(),
} }
fn project_g_TinyExpressionP4AST_2e_ContainsExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_ContainsExpr { let f = &self.slots[node.a as usize..node.a as usize + 3]; super::g_TinyExpressionP4AST_2e_ContainsExpr { span: span_of(node), node_id: node.node_id as usize,
g_value: Box::new(self.project(source, f[0])),
g_patterns: self.list_at(f[1], f[2]).iter().map(|&v| self.project(source, v)).collect(),
} }
fn project_g_TinyExpressionP4AST_2e_InExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_InExpr { let f = &self.slots[node.a as usize..node.a as usize + 3]; super::g_TinyExpressionP4AST_2e_InExpr { span: span_of(node), node_id: node.node_id as usize,
g_value: Box::new(self.project(source, f[0])),
g_candidates: self.list_at(f[1], f[2]).iter().map(|&v| self.project(source, v)).collect(),
} }
fn project_g_TinyExpressionP4AST_2e_StartsWithDotExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_StartsWithDotExpr { let f = &self.slots[node.a as usize..node.a as usize + 3]; super::g_TinyExpressionP4AST_2e_StartsWithDotExpr { span: span_of(node), node_id: node.node_id as usize,
g_value: Box::new(self.project(source, f[0])),
g_patterns: self.list_at(f[1], f[2]).iter().map(|&v| self.project(source, v)).collect(),
} }
fn project_g_TinyExpressionP4AST_2e_EndsWithDotExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_EndsWithDotExpr { let f = &self.slots[node.a as usize..node.a as usize + 3]; super::g_TinyExpressionP4AST_2e_EndsWithDotExpr { span: span_of(node), node_id: node.node_id as usize,
g_value: Box::new(self.project(source, f[0])),
g_patterns: self.list_at(f[1], f[2]).iter().map(|&v| self.project(source, v)).collect(),
} }
fn project_g_TinyExpressionP4AST_2e_ContainsDotExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_ContainsDotExpr { let f = &self.slots[node.a as usize..node.a as usize + 3]; super::g_TinyExpressionP4AST_2e_ContainsDotExpr { span: span_of(node), node_id: node.node_id as usize,
g_value: Box::new(self.project(source, f[0])),
g_patterns: self.list_at(f[1], f[2]).iter().map(|&v| self.project(source, v)).collect(),
} }
fn project_g_TinyExpressionP4AST_2e_IsPresentExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_IsPresentExpr { let f = &self.slots[node.a as usize..node.a as usize + 1]; super::g_TinyExpressionP4AST_2e_IsPresentExpr { span: span_of(node), node_id: node.node_id as usize,
g_value: Box::new(self.project(source, f[0])),
} }
fn project_g_TinyExpressionP4AST_2e_InTimeRangeExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_InTimeRangeExpr { let f = &self.slots[node.a as usize..node.a as usize + 2]; super::g_TinyExpressionP4AST_2e_InTimeRangeExpr { span: span_of(node), node_id: node.node_id as usize,
g_startHour: Box::new(self.project(source, f[0])),
g_endHour: Box::new(self.project(source, f[1])),
} }
fn project_g_TinyExpressionP4AST_2e_InDayTimeRangeExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_InDayTimeRangeExpr { let f = &self.slots[node.a as usize..node.a as usize + 4]; super::g_TinyExpressionP4AST_2e_InDayTimeRangeExpr { span: span_of(node), node_id: node.node_id as usize,
g_startDay: self.text_in(source, f[0]).to_owned(),
g_startHour: Box::new(self.project(source, f[1])),
g_endDay: self.text_in(source, f[2]).to_owned(),
g_endHour: Box::new(self.project(source, f[3])),
} }
fn project_g_TinyExpressionP4AST_2e_SliceExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_SliceExpr { let f = &self.slots[node.a as usize..node.a as usize + 4]; super::g_TinyExpressionP4AST_2e_SliceExpr { span: span_of(node), node_id: node.node_id as usize,
g_value: Box::new(self.project(source, f[0])),
g_start: { let v = f[1]; (v != NONE).then(|| Box::new(self.project(source, v))) },
g_end: { let v = f[2]; (v != NONE).then(|| Box::new(self.project(source, v))) },
g_step: { let v = f[3]; (v != NONE).then(|| Box::new(self.project(source, v))) },
} }
fn project_g_TinyExpressionP4AST_2e_StringConcatExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_StringConcatExpr { let f = &self.slots[node.a as usize..node.a as usize + 5]; super::g_TinyExpressionP4AST_2e_StringConcatExpr { span: span_of(node), node_id: node.node_id as usize,
g_left: Box::new(self.project(source, f[0])),
g_op: self.list_at(f[1], f[2]).iter().map(|&v| self.text_in(source, v).to_owned()).collect(),
g_right: self.list_at(f[3], f[4]).iter().map(|&v| self.project(source, v)).collect(),
} }
fn project_g_TinyExpressionP4AST_2e_StringCastVariableRefExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_StringCastVariableRefExpr { let f = &self.slots[node.a as usize..node.a as usize + 1]; super::g_TinyExpressionP4AST_2e_StringCastVariableRefExpr { span: span_of(node), node_id: node.node_id as usize,
g_name: self.text_in(source, f[0]).to_owned(),
} }
fn project_g_TinyExpressionP4AST_2e_StringTypedVariableRefExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_StringTypedVariableRefExpr { let f = &self.slots[node.a as usize..node.a as usize + 1]; super::g_TinyExpressionP4AST_2e_StringTypedVariableRefExpr { span: span_of(node), node_id: node.node_id as usize,
g_name: self.text_in(source, f[0]).to_owned(),
} }
fn project_g_TinyExpressionP4AST_2e_BooleanOrExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_BooleanOrExpr { let f = &self.slots[node.a as usize..node.a as usize + 5]; super::g_TinyExpressionP4AST_2e_BooleanOrExpr { span: span_of(node), node_id: node.node_id as usize,
g_left: Box::new(self.project(source, f[0])),
g_op: self.list_at(f[1], f[2]).iter().map(|&v| self.text_in(source, v).to_owned()).collect(),
g_right: self.list_at(f[3], f[4]).iter().map(|&v| self.project(source, v)).collect(),
} }
fn project_g_TinyExpressionP4AST_2e_BooleanAndExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_BooleanAndExpr { let f = &self.slots[node.a as usize..node.a as usize + 5]; super::g_TinyExpressionP4AST_2e_BooleanAndExpr { span: span_of(node), node_id: node.node_id as usize,
g_left: Box::new(self.project(source, f[0])),
g_op: self.list_at(f[1], f[2]).iter().map(|&v| self.text_in(source, v).to_owned()).collect(),
g_right: self.list_at(f[3], f[4]).iter().map(|&v| self.project(source, v)).collect(),
} }
fn project_g_TinyExpressionP4AST_2e_BooleanXorExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_BooleanXorExpr { let f = &self.slots[node.a as usize..node.a as usize + 5]; super::g_TinyExpressionP4AST_2e_BooleanXorExpr { span: span_of(node), node_id: node.node_id as usize,
g_left: Box::new(self.project(source, f[0])),
g_op: self.list_at(f[1], f[2]).iter().map(|&v| self.text_in(source, v).to_owned()).collect(),
g_right: self.list_at(f[3], f[4]).iter().map(|&v| self.project(source, v)).collect(),
} }
fn project_g_TinyExpressionP4AST_2e_NotExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_NotExpr { let f = &self.slots[node.a as usize..node.a as usize + 1]; super::g_TinyExpressionP4AST_2e_NotExpr { span: span_of(node), node_id: node.node_id as usize,
g_value: Box::new(self.project(source, f[0])),
} }
fn project_g_TinyExpressionP4AST_2e_BooleanEqualityExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_BooleanEqualityExpr { let f = &self.slots[node.a as usize..node.a as usize + 3]; super::g_TinyExpressionP4AST_2e_BooleanEqualityExpr { span: span_of(node), node_id: node.node_id as usize,
g_left: Box::new(self.project(source, f[0])),
g_op: self.text_in(source, f[1]).to_owned(),
g_right: Box::new(self.project(source, f[2])),
} }
fn project_g_TinyExpressionP4AST_2e_BooleanFactorExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_BooleanFactorExpr { let f = &self.slots[node.a as usize..node.a as usize + 1]; super::g_TinyExpressionP4AST_2e_BooleanFactorExpr { span: span_of(node), node_id: node.node_id as usize,
g_value: Box::new(self.project(source, f[0])),
} }
fn project_g_TinyExpressionP4AST_2e_StringComparisonExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_StringComparisonExpr { let f = &self.slots[node.a as usize..node.a as usize + 3]; super::g_TinyExpressionP4AST_2e_StringComparisonExpr { span: span_of(node), node_id: node.node_id as usize,
g_left: Box::new(self.project(source, f[0])),
g_op: self.text_in(source, f[1]).to_owned(),
g_right: Box::new(self.project(source, f[2])),
} }
fn project_g_TinyExpressionP4AST_2e_ComparisonExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_ComparisonExpr { let f = &self.slots[node.a as usize..node.a as usize + 3]; super::g_TinyExpressionP4AST_2e_ComparisonExpr { span: span_of(node), node_id: node.node_id as usize,
g_left: Box::new(self.project(source, f[0])),
g_op: self.text_in(source, f[1]).to_owned(),
g_right: Box::new(self.project(source, f[2])),
} }
fn project_g_TinyExpressionP4AST_2e_ObjectExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_ObjectExpr { let f = &self.slots[node.a as usize..node.a as usize + 1]; super::g_TinyExpressionP4AST_2e_ObjectExpr { span: span_of(node), node_id: node.node_id as usize,
g_value: Box::new(self.project(source, f[0])),
} }
fn project_g_TinyExpressionP4AST_2e_IfExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_IfExpr { let f = &self.slots[node.a as usize..node.a as usize + 3]; super::g_TinyExpressionP4AST_2e_IfExpr { span: span_of(node), node_id: node.node_id as usize,
g_condition: Box::new(self.project(source, f[0])),
g_thenExpr: Box::new(self.project(source, f[1])),
g_elseExpr: Box::new(self.project(source, f[2])),
} }
fn project_g_TinyExpressionP4AST_2e_BranchExpressionExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_BranchExpressionExpr { let f = &self.slots[node.a as usize..node.a as usize + 1]; super::g_TinyExpressionP4AST_2e_BranchExpressionExpr { span: span_of(node), node_id: node.node_id as usize,
g_value: Box::new(self.project(source, f[0])),
} }
fn project_g_TinyExpressionP4AST_2e_NumberMatchExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_NumberMatchExpr { let f = &self.slots[node.a as usize..node.a as usize + 4]; super::g_TinyExpressionP4AST_2e_NumberMatchExpr { span: span_of(node), node_id: node.node_id as usize,
g_firstCase: Box::new(self.project(source, f[0])),
g_moreCases: self.list_at(f[1], f[2]).iter().map(|&v| self.project(source, v)).collect(),
g_defaultCase: Box::new(self.project(source, f[3])),
} }
fn project_g_TinyExpressionP4AST_2e_NumberCaseExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_NumberCaseExpr { let f = &self.slots[node.a as usize..node.a as usize + 2]; super::g_TinyExpressionP4AST_2e_NumberCaseExpr { span: span_of(node), node_id: node.node_id as usize,
g_condition: Box::new(self.project(source, f[0])),
g_value: Box::new(self.project(source, f[1])),
} }
fn project_g_TinyExpressionP4AST_2e_NumberDefaultCaseExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_NumberDefaultCaseExpr { let f = &self.slots[node.a as usize..node.a as usize + 1]; super::g_TinyExpressionP4AST_2e_NumberDefaultCaseExpr { span: span_of(node), node_id: node.node_id as usize,
g_value: Box::new(self.project(source, f[0])),
} }
fn project_g_TinyExpressionP4AST_2e_NumberCaseValueExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_NumberCaseValueExpr { let f = &self.slots[node.a as usize..node.a as usize + 1]; super::g_TinyExpressionP4AST_2e_NumberCaseValueExpr { span: span_of(node), node_id: node.node_id as usize,
g_value: Box::new(self.project(source, f[0])),
} }
fn project_g_TinyExpressionP4AST_2e_StringMatchExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_StringMatchExpr { let f = &self.slots[node.a as usize..node.a as usize + 4]; super::g_TinyExpressionP4AST_2e_StringMatchExpr { span: span_of(node), node_id: node.node_id as usize,
g_firstCase: Box::new(self.project(source, f[0])),
g_moreCases: self.list_at(f[1], f[2]).iter().map(|&v| self.project(source, v)).collect(),
g_defaultCase: Box::new(self.project(source, f[3])),
} }
fn project_g_TinyExpressionP4AST_2e_StringCaseExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_StringCaseExpr { let f = &self.slots[node.a as usize..node.a as usize + 2]; super::g_TinyExpressionP4AST_2e_StringCaseExpr { span: span_of(node), node_id: node.node_id as usize,
g_condition: Box::new(self.project(source, f[0])),
g_value: Box::new(self.project(source, f[1])),
} }
fn project_g_TinyExpressionP4AST_2e_StringDefaultCaseExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_StringDefaultCaseExpr { let f = &self.slots[node.a as usize..node.a as usize + 1]; super::g_TinyExpressionP4AST_2e_StringDefaultCaseExpr { span: span_of(node), node_id: node.node_id as usize,
g_value: Box::new(self.project(source, f[0])),
} }
fn project_g_TinyExpressionP4AST_2e_StringCaseValueExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_StringCaseValueExpr { let f = &self.slots[node.a as usize..node.a as usize + 1]; super::g_TinyExpressionP4AST_2e_StringCaseValueExpr { span: span_of(node), node_id: node.node_id as usize,
g_value: Box::new(self.project(source, f[0])),
} }
fn project_g_TinyExpressionP4AST_2e_BooleanMatchExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_BooleanMatchExpr { let f = &self.slots[node.a as usize..node.a as usize + 4]; super::g_TinyExpressionP4AST_2e_BooleanMatchExpr { span: span_of(node), node_id: node.node_id as usize,
g_firstCase: Box::new(self.project(source, f[0])),
g_moreCases: self.list_at(f[1], f[2]).iter().map(|&v| self.project(source, v)).collect(),
g_defaultCase: Box::new(self.project(source, f[3])),
} }
fn project_g_TinyExpressionP4AST_2e_BooleanCaseExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_BooleanCaseExpr { let f = &self.slots[node.a as usize..node.a as usize + 2]; super::g_TinyExpressionP4AST_2e_BooleanCaseExpr { span: span_of(node), node_id: node.node_id as usize,
g_condition: Box::new(self.project(source, f[0])),
g_value: Box::new(self.project(source, f[1])),
} }
fn project_g_TinyExpressionP4AST_2e_BooleanDefaultCaseExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_BooleanDefaultCaseExpr { let f = &self.slots[node.a as usize..node.a as usize + 1]; super::g_TinyExpressionP4AST_2e_BooleanDefaultCaseExpr { span: span_of(node), node_id: node.node_id as usize,
g_value: Box::new(self.project(source, f[0])),
} }
fn project_g_TinyExpressionP4AST_2e_BooleanCaseValueExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_BooleanCaseValueExpr { let f = &self.slots[node.a as usize..node.a as usize + 1]; super::g_TinyExpressionP4AST_2e_BooleanCaseValueExpr { span: span_of(node), node_id: node.node_id as usize,
g_value: Box::new(self.project(source, f[0])),
} }
fn project_g_TinyExpressionP4AST_2e_VariableRefExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_VariableRefExpr { let f = &self.slots[node.a as usize..node.a as usize + 2]; super::g_TinyExpressionP4AST_2e_VariableRefExpr { span: span_of(node), node_id: node.node_id as usize,
g_name: self.text_in(source, f[0]).to_owned(),
g_type: { let v = f[1]; (v != NONE).then(|| self.text_in(source, v).to_owned()) },
} }
fn project_g_TinyExpressionP4AST_2e_ExpressionExpr(&self, source: &str, node: &TreeNode) -> super::g_TinyExpressionP4AST_2e_ExpressionExpr { let f = &self.slots[node.a as usize..node.a as usize + 1]; super::g_TinyExpressionP4AST_2e_ExpressionExpr { span: span_of(node), node_id: node.node_id as usize,
g_value: Box::new(self.project(source, f[0])),
} }
}
impl AstTree {
#[doc(hidden)] pub fn collect_value_spans(&self, source: &str, id: u32, path: &mut String, out: &mut Vec<ValueSpan>) { let _ = &source; let node = self.nodes[id as usize]; match node.kind { KIND_TEXT => out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: Some(self.text_in(source, id).to_owned()) }), KIND_NULL => {},
K_g_TinyExpressionP4AST_2e_FormulaExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/imports"); for (i, &v) in self.items(id, 0).iter().enumerate() { let len = path.len(); let _ = std::fmt::Write::write_fmt(path, format_args!("/{i}")); self.collect_value_spans(source, v, path, out); path.truncate(len); } path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/declarations"); for (i, &v) in self.items(id, 2).iter().enumerate() { let len = path.len(); let _ = std::fmt::Write::write_fmt(path, format_args!("/{i}")); self.collect_value_spans(source, v, path, out); path.truncate(len); } path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/expression"); self.collect_value_spans(source, self.slot(id, 4), path, out); path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/methods"); for (i, &v) in self.items(id, 5).iter().enumerate() { let len = path.len(); let _ = std::fmt::Write::write_fmt(path, format_args!("/{i}")); self.collect_value_spans(source, v, path, out); path.truncate(len); } path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_CodeBlockExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
},
K_g_TinyExpressionP4AST_2e_ImportDeclarationExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/className"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/method"); let v = self.slot(id, 1); if v != NONE { self.text_value_span(v, path, out); } path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/alias"); self.text_value_span(self.slot(id, 2), path, out); path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_QualifiedNameExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/head"); self.text_value_span(self.slot(id, 0), path, out); path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/tail"); for (i, &v) in self.items(id, 1).iter().enumerate() { let len = path.len(); let _ = std::fmt::Write::write_fmt(path, format_args!("/{i}")); self.text_value_span(v, path, out); path.truncate(len); } path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_NumberVariableDeclarationExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/varName"); self.text_value_span(self.slot(id, 0), path, out); path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/onlyIfAbsent"); let v = self.slot(id, 1); if v != NONE { self.collect_value_spans(source, v, path, out); } path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/value"); let v = self.slot(id, 2); if v != NONE { self.collect_value_spans(source, v, path, out); } path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/desc"); let v = self.slot(id, 3); if v != NONE { self.text_value_span(v, path, out); } path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_StringVariableDeclarationExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/varName"); self.text_value_span(self.slot(id, 0), path, out); path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/onlyIfAbsent"); let v = self.slot(id, 1); if v != NONE { self.collect_value_spans(source, v, path, out); } path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/value"); let v = self.slot(id, 2); if v != NONE { self.collect_value_spans(source, v, path, out); } path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/desc"); let v = self.slot(id, 3); if v != NONE { self.text_value_span(v, path, out); } path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_BooleanVariableDeclarationExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/varName"); self.text_value_span(self.slot(id, 0), path, out); path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/onlyIfAbsent"); let v = self.slot(id, 1); if v != NONE { self.collect_value_spans(source, v, path, out); } path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/value"); let v = self.slot(id, 2); if v != NONE { self.collect_value_spans(source, v, path, out); } path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/desc"); let v = self.slot(id, 3); if v != NONE { self.text_value_span(v, path, out); } path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_ObjectVariableDeclarationExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/varName"); self.text_value_span(self.slot(id, 0), path, out); path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/onlyIfAbsent"); let v = self.slot(id, 1); if v != NONE { self.collect_value_spans(source, v, path, out); } path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/value"); let v = self.slot(id, 2); if v != NONE { self.collect_value_spans(source, v, path, out); } path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/desc"); let v = self.slot(id, 3); if v != NONE { self.text_value_span(v, path, out); } path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_OnlyIfAbsentExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
},
K_g_TinyExpressionP4AST_2e_NumberMethodDeclarationExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/methodName"); self.text_value_span(self.slot(id, 0), path, out); path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/parameters"); let v = self.slot(id, 1); if v != NONE { self.collect_value_spans(source, v, path, out); } path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/expression"); self.collect_value_spans(source, self.slot(id, 2), path, out); path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_StringMethodDeclarationExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/methodName"); self.text_value_span(self.slot(id, 0), path, out); path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/parameters"); let v = self.slot(id, 1); if v != NONE { self.collect_value_spans(source, v, path, out); } path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/expression"); self.collect_value_spans(source, self.slot(id, 2), path, out); path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_BooleanMethodDeclarationExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/methodName"); self.text_value_span(self.slot(id, 0), path, out); path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/parameters"); let v = self.slot(id, 1); if v != NONE { self.collect_value_spans(source, v, path, out); } path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/expression"); self.collect_value_spans(source, self.slot(id, 2), path, out); path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_ObjectMethodDeclarationExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/methodName"); self.text_value_span(self.slot(id, 0), path, out); path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/parameters"); let v = self.slot(id, 1); if v != NONE { self.collect_value_spans(source, v, path, out); } path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/expression"); self.collect_value_spans(source, self.slot(id, 2), path, out); path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_MethodParametersExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/values"); for (i, &v) in self.items(id, 0).iter().enumerate() { let len = path.len(); let _ = std::fmt::Write::write_fmt(path, format_args!("/{i}")); self.collect_value_spans(source, v, path, out); path.truncate(len); } path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_MethodParameterExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/paramName"); self.text_value_span(self.slot(id, 0), path, out); path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/type"); let v = self.slot(id, 1); if v != NONE { self.text_value_span(v, path, out); } path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_ExternalBooleanInvocationExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/className"); let v = self.slot(id, 0); if v != NONE { self.collect_value_spans(source, v, path, out); } path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/name"); self.text_value_span(self.slot(id, 1), path, out); path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/args"); let v = self.slot(id, 2); if v != NONE { self.collect_value_spans(source, v, path, out); } path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_ExternalNumberInvocationExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/className"); let v = self.slot(id, 0); if v != NONE { self.collect_value_spans(source, v, path, out); } path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/name"); self.text_value_span(self.slot(id, 1), path, out); path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/args"); let v = self.slot(id, 2); if v != NONE { self.collect_value_spans(source, v, path, out); } path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_ExternalStringInvocationExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/className"); let v = self.slot(id, 0); if v != NONE { self.collect_value_spans(source, v, path, out); } path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/name"); self.text_value_span(self.slot(id, 1), path, out); path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/args"); let v = self.slot(id, 2); if v != NONE { self.collect_value_spans(source, v, path, out); } path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_ExternalObjectInvocationExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/className"); let v = self.slot(id, 0); if v != NONE { self.collect_value_spans(source, v, path, out); } path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/name"); self.text_value_span(self.slot(id, 1), path, out); path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/args"); let v = self.slot(id, 2); if v != NONE { self.collect_value_spans(source, v, path, out); } path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_MethodInvocationExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/name"); self.text_value_span(self.slot(id, 0), path, out); path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/args"); let v = self.slot(id, 1); if v != NONE { self.collect_value_spans(source, v, path, out); } path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_TernaryExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/condition"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/thenExpr"); self.collect_value_spans(source, self.slot(id, 1), path, out); path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/elseExpr"); self.collect_value_spans(source, self.slot(id, 2), path, out); path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_ArgumentExpressionExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/value"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_ArgumentsExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/values"); for (i, &v) in self.items(id, 0).iter().enumerate() { let len = path.len(); let _ = std::fmt::Write::write_fmt(path, format_args!("/{i}")); self.collect_value_spans(source, v, path, out); path.truncate(len); } path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_BinaryExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/left"); let v = self.slot(id, 0); if v != NONE { self.collect_value_spans(source, v, path, out); } path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/op"); for (i, &v) in self.items(id, 1).iter().enumerate() { let len = path.len(); let _ = std::fmt::Write::write_fmt(path, format_args!("/{i}")); self.text_value_span(v, path, out); path.truncate(len); } path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/right"); for (i, &v) in self.items(id, 3).iter().enumerate() { let len = path.len(); let _ = std::fmt::Write::write_fmt(path, format_args!("/{i}")); self.collect_value_spans(source, v, path, out); path.truncate(len); } path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_SinExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/arg"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_CosExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/arg"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_TanExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/arg"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_SqrtExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/arg"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_MinExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/first"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/rest"); for (i, &v) in self.items(id, 1).iter().enumerate() { let len = path.len(); let _ = std::fmt::Write::write_fmt(path, format_args!("/{i}")); self.collect_value_spans(source, v, path, out); path.truncate(len); } path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_MaxExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/first"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/rest"); for (i, &v) in self.items(id, 1).iter().enumerate() { let len = path.len(); let _ = std::fmt::Write::write_fmt(path, format_args!("/{i}")); self.collect_value_spans(source, v, path, out); path.truncate(len); } path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_RandomExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
},
K_g_TinyExpressionP4AST_2e_AbsExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/arg"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_RoundExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/arg"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_CeilExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/arg"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_FloorExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/arg"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_PowExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/base"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/exponent"); self.collect_value_spans(source, self.slot(id, 1), path, out); path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_LogExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/arg"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_ExpExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/arg"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_ToNumExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/value"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/defaultValue"); self.collect_value_spans(source, self.slot(id, 1), path, out); path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_ToUpperCaseExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/value"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_ToLowerCaseExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/value"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_TrimExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/value"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_LengthExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/value"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_ToUpperCaseDotExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/value"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_ToLowerCaseDotExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/value"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_TrimDotExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/value"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_LengthDotExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/value"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_StartsWithExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/value"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/patterns"); for (i, &v) in self.items(id, 1).iter().enumerate() { let len = path.len(); let _ = std::fmt::Write::write_fmt(path, format_args!("/{i}")); self.collect_value_spans(source, v, path, out); path.truncate(len); } path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_EndsWithExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/value"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/patterns"); for (i, &v) in self.items(id, 1).iter().enumerate() { let len = path.len(); let _ = std::fmt::Write::write_fmt(path, format_args!("/{i}")); self.collect_value_spans(source, v, path, out); path.truncate(len); } path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_ContainsExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/value"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/patterns"); for (i, &v) in self.items(id, 1).iter().enumerate() { let len = path.len(); let _ = std::fmt::Write::write_fmt(path, format_args!("/{i}")); self.collect_value_spans(source, v, path, out); path.truncate(len); } path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_InExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/value"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/candidates"); for (i, &v) in self.items(id, 1).iter().enumerate() { let len = path.len(); let _ = std::fmt::Write::write_fmt(path, format_args!("/{i}")); self.collect_value_spans(source, v, path, out); path.truncate(len); } path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_StartsWithDotExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/value"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/patterns"); for (i, &v) in self.items(id, 1).iter().enumerate() { let len = path.len(); let _ = std::fmt::Write::write_fmt(path, format_args!("/{i}")); self.collect_value_spans(source, v, path, out); path.truncate(len); } path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_EndsWithDotExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/value"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/patterns"); for (i, &v) in self.items(id, 1).iter().enumerate() { let len = path.len(); let _ = std::fmt::Write::write_fmt(path, format_args!("/{i}")); self.collect_value_spans(source, v, path, out); path.truncate(len); } path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_ContainsDotExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/value"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/patterns"); for (i, &v) in self.items(id, 1).iter().enumerate() { let len = path.len(); let _ = std::fmt::Write::write_fmt(path, format_args!("/{i}")); self.collect_value_spans(source, v, path, out); path.truncate(len); } path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_IsPresentExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/value"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_InTimeRangeExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/startHour"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/endHour"); self.collect_value_spans(source, self.slot(id, 1), path, out); path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_InDayTimeRangeExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/startDay"); self.text_value_span(self.slot(id, 0), path, out); path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/startHour"); self.collect_value_spans(source, self.slot(id, 1), path, out); path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/endDay"); self.text_value_span(self.slot(id, 2), path, out); path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/endHour"); self.collect_value_spans(source, self.slot(id, 3), path, out); path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_SliceExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/value"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/start"); let v = self.slot(id, 1); if v != NONE { self.collect_value_spans(source, v, path, out); } path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/end"); let v = self.slot(id, 2); if v != NONE { self.collect_value_spans(source, v, path, out); } path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/step"); let v = self.slot(id, 3); if v != NONE { self.collect_value_spans(source, v, path, out); } path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_StringConcatExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/left"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/op"); for (i, &v) in self.items(id, 1).iter().enumerate() { let len = path.len(); let _ = std::fmt::Write::write_fmt(path, format_args!("/{i}")); self.text_value_span(v, path, out); path.truncate(len); } path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/right"); for (i, &v) in self.items(id, 3).iter().enumerate() { let len = path.len(); let _ = std::fmt::Write::write_fmt(path, format_args!("/{i}")); self.collect_value_spans(source, v, path, out); path.truncate(len); } path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_StringCastVariableRefExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/name"); self.text_value_span(self.slot(id, 0), path, out); path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_StringTypedVariableRefExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/name"); self.text_value_span(self.slot(id, 0), path, out); path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_BooleanOrExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/left"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/op"); for (i, &v) in self.items(id, 1).iter().enumerate() { let len = path.len(); let _ = std::fmt::Write::write_fmt(path, format_args!("/{i}")); self.text_value_span(v, path, out); path.truncate(len); } path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/right"); for (i, &v) in self.items(id, 3).iter().enumerate() { let len = path.len(); let _ = std::fmt::Write::write_fmt(path, format_args!("/{i}")); self.collect_value_spans(source, v, path, out); path.truncate(len); } path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_BooleanAndExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/left"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/op"); for (i, &v) in self.items(id, 1).iter().enumerate() { let len = path.len(); let _ = std::fmt::Write::write_fmt(path, format_args!("/{i}")); self.text_value_span(v, path, out); path.truncate(len); } path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/right"); for (i, &v) in self.items(id, 3).iter().enumerate() { let len = path.len(); let _ = std::fmt::Write::write_fmt(path, format_args!("/{i}")); self.collect_value_spans(source, v, path, out); path.truncate(len); } path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_BooleanXorExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/left"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/op"); for (i, &v) in self.items(id, 1).iter().enumerate() { let len = path.len(); let _ = std::fmt::Write::write_fmt(path, format_args!("/{i}")); self.text_value_span(v, path, out); path.truncate(len); } path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/right"); for (i, &v) in self.items(id, 3).iter().enumerate() { let len = path.len(); let _ = std::fmt::Write::write_fmt(path, format_args!("/{i}")); self.collect_value_spans(source, v, path, out); path.truncate(len); } path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_NotExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/value"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_BooleanEqualityExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/left"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/op"); self.text_value_span(self.slot(id, 1), path, out); path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/right"); self.collect_value_spans(source, self.slot(id, 2), path, out); path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_BooleanFactorExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/value"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_StringComparisonExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/left"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/op"); self.text_value_span(self.slot(id, 1), path, out); path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/right"); self.collect_value_spans(source, self.slot(id, 2), path, out); path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_ComparisonExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/left"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/op"); self.text_value_span(self.slot(id, 1), path, out); path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/right"); self.collect_value_spans(source, self.slot(id, 2), path, out); path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_ObjectExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/value"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_IfExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/condition"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/thenExpr"); self.collect_value_spans(source, self.slot(id, 1), path, out); path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/elseExpr"); self.collect_value_spans(source, self.slot(id, 2), path, out); path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_BranchExpressionExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/value"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_NumberMatchExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/firstCase"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/moreCases"); for (i, &v) in self.items(id, 1).iter().enumerate() { let len = path.len(); let _ = std::fmt::Write::write_fmt(path, format_args!("/{i}")); self.collect_value_spans(source, v, path, out); path.truncate(len); } path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/defaultCase"); self.collect_value_spans(source, self.slot(id, 3), path, out); path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_NumberCaseExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/condition"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/value"); self.collect_value_spans(source, self.slot(id, 1), path, out); path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_NumberDefaultCaseExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/value"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_NumberCaseValueExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/value"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_StringMatchExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/firstCase"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/moreCases"); for (i, &v) in self.items(id, 1).iter().enumerate() { let len = path.len(); let _ = std::fmt::Write::write_fmt(path, format_args!("/{i}")); self.collect_value_spans(source, v, path, out); path.truncate(len); } path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/defaultCase"); self.collect_value_spans(source, self.slot(id, 3), path, out); path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_StringCaseExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/condition"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/value"); self.collect_value_spans(source, self.slot(id, 1), path, out); path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_StringDefaultCaseExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/value"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_StringCaseValueExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/value"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_BooleanMatchExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/firstCase"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/moreCases"); for (i, &v) in self.items(id, 1).iter().enumerate() { let len = path.len(); let _ = std::fmt::Write::write_fmt(path, format_args!("/{i}")); self.collect_value_spans(source, v, path, out); path.truncate(len); } path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/defaultCase"); self.collect_value_spans(source, self.slot(id, 3), path, out); path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_BooleanCaseExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/condition"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/value"); self.collect_value_spans(source, self.slot(id, 1), path, out); path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_BooleanDefaultCaseExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/value"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_BooleanCaseValueExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/value"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_VariableRefExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/name"); self.text_value_span(self.slot(id, 0), path, out); path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/type"); let v = self.slot(id, 1); if v != NONE { self.text_value_span(v, path, out); } path.truncate(len); }
},
K_g_TinyExpressionP4AST_2e_ExpressionExpr => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/value"); self.collect_value_spans(source, self.slot(id, 0), path, out); path.truncate(len); }
},
_ => unreachable!("unknown AST node kind"), } }
}
#[cfg(feature="json")] impl AstTree {
/// 根の canonical JSON（所有 `Ast::canonical_json` と byte 一致）。
pub fn canonical_json(&self) -> Option<String> { self.root().map(|root| serde_json::to_string(&root.canonical_value()).expect("finite canonical AST")) }
}
#[cfg(feature="json")] impl<'t> NodeRef<'t> {
pub fn canonical_value(&self) -> serde_json::Value { let (tree, id) = (self.tree, self.id); let node = tree.nodes[id as usize]; let text = |v: u32| tree.text_in(&tree.source, v); let value = |v: u32| NodeRef { tree, id: v }.canonical_value(); let _ = (&text, &value); match node.kind { KIND_TEXT => serde_json::Value::String(text(id).to_owned()), KIND_NULL => serde_json::Value::Null,
K_g_TinyExpressionP4AST_2e_FormulaExpr => serde_json::json!({"fields":{
"declarations":(tree.items(id, 2).iter().map(|&v| value(v)).collect::<Vec<_>>()),
"expression":(value(tree.slot(id, 4))),
"imports":(tree.items(id, 0).iter().map(|&v| value(v)).collect::<Vec<_>>()),
"methods":(tree.items(id, 5).iter().map(|&v| value(v)).collect::<Vec<_>>()),
},"span":span_of(&node),"type":"FormulaExpr"}),
K_g_TinyExpressionP4AST_2e_CodeBlockExpr => serde_json::json!({"fields":{
},"span":span_of(&node),"type":"CodeBlockExpr"}),
K_g_TinyExpressionP4AST_2e_ImportDeclarationExpr => serde_json::json!({"fields":{
"alias":(text(tree.slot(id, 2))),
"className":(value(tree.slot(id, 0))),
"method":({ let v = tree.slot(id, 1); (v != NONE).then(|| text(v)) }),
},"span":span_of(&node),"type":"ImportDeclarationExpr"}),
K_g_TinyExpressionP4AST_2e_QualifiedNameExpr => serde_json::json!({"fields":{
"head":(text(tree.slot(id, 0))),
"tail":(tree.items(id, 1).iter().map(|&v| text(v)).collect::<Vec<_>>()),
},"span":span_of(&node),"type":"QualifiedNameExpr"}),
K_g_TinyExpressionP4AST_2e_NumberVariableDeclarationExpr => serde_json::json!({"fields":{
"desc":({ let v = tree.slot(id, 3); (v != NONE).then(|| text(v)) }),
"onlyIfAbsent":({ let v = tree.slot(id, 1); (v != NONE).then(|| value(v)) }),
"value":({ let v = tree.slot(id, 2); (v != NONE).then(|| value(v)) }),
"varName":(text(tree.slot(id, 0))),
},"span":span_of(&node),"type":"NumberVariableDeclarationExpr"}),
K_g_TinyExpressionP4AST_2e_StringVariableDeclarationExpr => serde_json::json!({"fields":{
"desc":({ let v = tree.slot(id, 3); (v != NONE).then(|| text(v)) }),
"onlyIfAbsent":({ let v = tree.slot(id, 1); (v != NONE).then(|| value(v)) }),
"value":({ let v = tree.slot(id, 2); (v != NONE).then(|| value(v)) }),
"varName":(text(tree.slot(id, 0))),
},"span":span_of(&node),"type":"StringVariableDeclarationExpr"}),
K_g_TinyExpressionP4AST_2e_BooleanVariableDeclarationExpr => serde_json::json!({"fields":{
"desc":({ let v = tree.slot(id, 3); (v != NONE).then(|| text(v)) }),
"onlyIfAbsent":({ let v = tree.slot(id, 1); (v != NONE).then(|| value(v)) }),
"value":({ let v = tree.slot(id, 2); (v != NONE).then(|| value(v)) }),
"varName":(text(tree.slot(id, 0))),
},"span":span_of(&node),"type":"BooleanVariableDeclarationExpr"}),
K_g_TinyExpressionP4AST_2e_ObjectVariableDeclarationExpr => serde_json::json!({"fields":{
"desc":({ let v = tree.slot(id, 3); (v != NONE).then(|| text(v)) }),
"onlyIfAbsent":({ let v = tree.slot(id, 1); (v != NONE).then(|| value(v)) }),
"value":({ let v = tree.slot(id, 2); (v != NONE).then(|| value(v)) }),
"varName":(text(tree.slot(id, 0))),
},"span":span_of(&node),"type":"ObjectVariableDeclarationExpr"}),
K_g_TinyExpressionP4AST_2e_OnlyIfAbsentExpr => serde_json::json!({"fields":{
},"span":span_of(&node),"type":"OnlyIfAbsentExpr"}),
K_g_TinyExpressionP4AST_2e_NumberMethodDeclarationExpr => serde_json::json!({"fields":{
"expression":(value(tree.slot(id, 2))),
"methodName":(text(tree.slot(id, 0))),
"parameters":({ let v = tree.slot(id, 1); (v != NONE).then(|| value(v)) }),
},"span":span_of(&node),"type":"NumberMethodDeclarationExpr"}),
K_g_TinyExpressionP4AST_2e_StringMethodDeclarationExpr => serde_json::json!({"fields":{
"expression":(value(tree.slot(id, 2))),
"methodName":(text(tree.slot(id, 0))),
"parameters":({ let v = tree.slot(id, 1); (v != NONE).then(|| value(v)) }),
},"span":span_of(&node),"type":"StringMethodDeclarationExpr"}),
K_g_TinyExpressionP4AST_2e_BooleanMethodDeclarationExpr => serde_json::json!({"fields":{
"expression":(value(tree.slot(id, 2))),
"methodName":(text(tree.slot(id, 0))),
"parameters":({ let v = tree.slot(id, 1); (v != NONE).then(|| value(v)) }),
},"span":span_of(&node),"type":"BooleanMethodDeclarationExpr"}),
K_g_TinyExpressionP4AST_2e_ObjectMethodDeclarationExpr => serde_json::json!({"fields":{
"expression":(value(tree.slot(id, 2))),
"methodName":(text(tree.slot(id, 0))),
"parameters":({ let v = tree.slot(id, 1); (v != NONE).then(|| value(v)) }),
},"span":span_of(&node),"type":"ObjectMethodDeclarationExpr"}),
K_g_TinyExpressionP4AST_2e_MethodParametersExpr => serde_json::json!({"fields":{
"values":(tree.items(id, 0).iter().map(|&v| value(v)).collect::<Vec<_>>()),
},"span":span_of(&node),"type":"MethodParametersExpr"}),
K_g_TinyExpressionP4AST_2e_MethodParameterExpr => serde_json::json!({"fields":{
"paramName":(text(tree.slot(id, 0))),
"type":({ let v = tree.slot(id, 1); (v != NONE).then(|| text(v)) }),
},"span":span_of(&node),"type":"MethodParameterExpr"}),
K_g_TinyExpressionP4AST_2e_ExternalBooleanInvocationExpr => serde_json::json!({"fields":{
"args":({ let v = tree.slot(id, 2); (v != NONE).then(|| value(v)) }),
"className":({ let v = tree.slot(id, 0); (v != NONE).then(|| value(v)) }),
"name":(text(tree.slot(id, 1))),
},"span":span_of(&node),"type":"ExternalBooleanInvocationExpr"}),
K_g_TinyExpressionP4AST_2e_ExternalNumberInvocationExpr => serde_json::json!({"fields":{
"args":({ let v = tree.slot(id, 2); (v != NONE).then(|| value(v)) }),
"className":({ let v = tree.slot(id, 0); (v != NONE).then(|| value(v)) }),
"name":(text(tree.slot(id, 1))),
},"span":span_of(&node),"type":"ExternalNumberInvocationExpr"}),
K_g_TinyExpressionP4AST_2e_ExternalStringInvocationExpr => serde_json::json!({"fields":{
"args":({ let v = tree.slot(id, 2); (v != NONE).then(|| value(v)) }),
"className":({ let v = tree.slot(id, 0); (v != NONE).then(|| value(v)) }),
"name":(text(tree.slot(id, 1))),
},"span":span_of(&node),"type":"ExternalStringInvocationExpr"}),
K_g_TinyExpressionP4AST_2e_ExternalObjectInvocationExpr => serde_json::json!({"fields":{
"args":({ let v = tree.slot(id, 2); (v != NONE).then(|| value(v)) }),
"className":({ let v = tree.slot(id, 0); (v != NONE).then(|| value(v)) }),
"name":(text(tree.slot(id, 1))),
},"span":span_of(&node),"type":"ExternalObjectInvocationExpr"}),
K_g_TinyExpressionP4AST_2e_MethodInvocationExpr => serde_json::json!({"fields":{
"args":({ let v = tree.slot(id, 1); (v != NONE).then(|| value(v)) }),
"name":(text(tree.slot(id, 0))),
},"span":span_of(&node),"type":"MethodInvocationExpr"}),
K_g_TinyExpressionP4AST_2e_TernaryExpr => serde_json::json!({"fields":{
"condition":(value(tree.slot(id, 0))),
"elseExpr":(value(tree.slot(id, 2))),
"thenExpr":(value(tree.slot(id, 1))),
},"span":span_of(&node),"type":"TernaryExpr"}),
K_g_TinyExpressionP4AST_2e_ArgumentExpressionExpr => serde_json::json!({"fields":{
"value":(value(tree.slot(id, 0))),
},"span":span_of(&node),"type":"ArgumentExpressionExpr"}),
K_g_TinyExpressionP4AST_2e_ArgumentsExpr => serde_json::json!({"fields":{
"values":(tree.items(id, 0).iter().map(|&v| value(v)).collect::<Vec<_>>()),
},"span":span_of(&node),"type":"ArgumentsExpr"}),
K_g_TinyExpressionP4AST_2e_BinaryExpr => serde_json::json!({"fields":{
"left":({ let v = tree.slot(id, 0); (v != NONE).then(|| value(v)) }),
"op":(tree.items(id, 1).iter().map(|&v| text(v)).collect::<Vec<_>>()),
"right":(tree.items(id, 3).iter().map(|&v| value(v)).collect::<Vec<_>>()),
},"span":span_of(&node),"type":"BinaryExpr"}),
K_g_TinyExpressionP4AST_2e_SinExpr => serde_json::json!({"fields":{
"arg":(value(tree.slot(id, 0))),
},"span":span_of(&node),"type":"SinExpr"}),
K_g_TinyExpressionP4AST_2e_CosExpr => serde_json::json!({"fields":{
"arg":(value(tree.slot(id, 0))),
},"span":span_of(&node),"type":"CosExpr"}),
K_g_TinyExpressionP4AST_2e_TanExpr => serde_json::json!({"fields":{
"arg":(value(tree.slot(id, 0))),
},"span":span_of(&node),"type":"TanExpr"}),
K_g_TinyExpressionP4AST_2e_SqrtExpr => serde_json::json!({"fields":{
"arg":(value(tree.slot(id, 0))),
},"span":span_of(&node),"type":"SqrtExpr"}),
K_g_TinyExpressionP4AST_2e_MinExpr => serde_json::json!({"fields":{
"first":(value(tree.slot(id, 0))),
"rest":(tree.items(id, 1).iter().map(|&v| value(v)).collect::<Vec<_>>()),
},"span":span_of(&node),"type":"MinExpr"}),
K_g_TinyExpressionP4AST_2e_MaxExpr => serde_json::json!({"fields":{
"first":(value(tree.slot(id, 0))),
"rest":(tree.items(id, 1).iter().map(|&v| value(v)).collect::<Vec<_>>()),
},"span":span_of(&node),"type":"MaxExpr"}),
K_g_TinyExpressionP4AST_2e_RandomExpr => serde_json::json!({"fields":{
},"span":span_of(&node),"type":"RandomExpr"}),
K_g_TinyExpressionP4AST_2e_AbsExpr => serde_json::json!({"fields":{
"arg":(value(tree.slot(id, 0))),
},"span":span_of(&node),"type":"AbsExpr"}),
K_g_TinyExpressionP4AST_2e_RoundExpr => serde_json::json!({"fields":{
"arg":(value(tree.slot(id, 0))),
},"span":span_of(&node),"type":"RoundExpr"}),
K_g_TinyExpressionP4AST_2e_CeilExpr => serde_json::json!({"fields":{
"arg":(value(tree.slot(id, 0))),
},"span":span_of(&node),"type":"CeilExpr"}),
K_g_TinyExpressionP4AST_2e_FloorExpr => serde_json::json!({"fields":{
"arg":(value(tree.slot(id, 0))),
},"span":span_of(&node),"type":"FloorExpr"}),
K_g_TinyExpressionP4AST_2e_PowExpr => serde_json::json!({"fields":{
"base":(value(tree.slot(id, 0))),
"exponent":(value(tree.slot(id, 1))),
},"span":span_of(&node),"type":"PowExpr"}),
K_g_TinyExpressionP4AST_2e_LogExpr => serde_json::json!({"fields":{
"arg":(value(tree.slot(id, 0))),
},"span":span_of(&node),"type":"LogExpr"}),
K_g_TinyExpressionP4AST_2e_ExpExpr => serde_json::json!({"fields":{
"arg":(value(tree.slot(id, 0))),
},"span":span_of(&node),"type":"ExpExpr"}),
K_g_TinyExpressionP4AST_2e_ToNumExpr => serde_json::json!({"fields":{
"defaultValue":(value(tree.slot(id, 1))),
"value":(value(tree.slot(id, 0))),
},"span":span_of(&node),"type":"ToNumExpr"}),
K_g_TinyExpressionP4AST_2e_ToUpperCaseExpr => serde_json::json!({"fields":{
"value":(value(tree.slot(id, 0))),
},"span":span_of(&node),"type":"ToUpperCaseExpr"}),
K_g_TinyExpressionP4AST_2e_ToLowerCaseExpr => serde_json::json!({"fields":{
"value":(value(tree.slot(id, 0))),
},"span":span_of(&node),"type":"ToLowerCaseExpr"}),
K_g_TinyExpressionP4AST_2e_TrimExpr => serde_json::json!({"fields":{
"value":(value(tree.slot(id, 0))),
},"span":span_of(&node),"type":"TrimExpr"}),
K_g_TinyExpressionP4AST_2e_LengthExpr => serde_json::json!({"fields":{
"value":(value(tree.slot(id, 0))),
},"span":span_of(&node),"type":"LengthExpr"}),
K_g_TinyExpressionP4AST_2e_ToUpperCaseDotExpr => serde_json::json!({"fields":{
"value":(value(tree.slot(id, 0))),
},"span":span_of(&node),"type":"ToUpperCaseDotExpr"}),
K_g_TinyExpressionP4AST_2e_ToLowerCaseDotExpr => serde_json::json!({"fields":{
"value":(value(tree.slot(id, 0))),
},"span":span_of(&node),"type":"ToLowerCaseDotExpr"}),
K_g_TinyExpressionP4AST_2e_TrimDotExpr => serde_json::json!({"fields":{
"value":(value(tree.slot(id, 0))),
},"span":span_of(&node),"type":"TrimDotExpr"}),
K_g_TinyExpressionP4AST_2e_LengthDotExpr => serde_json::json!({"fields":{
"value":(value(tree.slot(id, 0))),
},"span":span_of(&node),"type":"LengthDotExpr"}),
K_g_TinyExpressionP4AST_2e_StartsWithExpr => serde_json::json!({"fields":{
"patterns":(tree.items(id, 1).iter().map(|&v| value(v)).collect::<Vec<_>>()),
"value":(value(tree.slot(id, 0))),
},"span":span_of(&node),"type":"StartsWithExpr"}),
K_g_TinyExpressionP4AST_2e_EndsWithExpr => serde_json::json!({"fields":{
"patterns":(tree.items(id, 1).iter().map(|&v| value(v)).collect::<Vec<_>>()),
"value":(value(tree.slot(id, 0))),
},"span":span_of(&node),"type":"EndsWithExpr"}),
K_g_TinyExpressionP4AST_2e_ContainsExpr => serde_json::json!({"fields":{
"patterns":(tree.items(id, 1).iter().map(|&v| value(v)).collect::<Vec<_>>()),
"value":(value(tree.slot(id, 0))),
},"span":span_of(&node),"type":"ContainsExpr"}),
K_g_TinyExpressionP4AST_2e_InExpr => serde_json::json!({"fields":{
"candidates":(tree.items(id, 1).iter().map(|&v| value(v)).collect::<Vec<_>>()),
"value":(value(tree.slot(id, 0))),
},"span":span_of(&node),"type":"InExpr"}),
K_g_TinyExpressionP4AST_2e_StartsWithDotExpr => serde_json::json!({"fields":{
"patterns":(tree.items(id, 1).iter().map(|&v| value(v)).collect::<Vec<_>>()),
"value":(value(tree.slot(id, 0))),
},"span":span_of(&node),"type":"StartsWithDotExpr"}),
K_g_TinyExpressionP4AST_2e_EndsWithDotExpr => serde_json::json!({"fields":{
"patterns":(tree.items(id, 1).iter().map(|&v| value(v)).collect::<Vec<_>>()),
"value":(value(tree.slot(id, 0))),
},"span":span_of(&node),"type":"EndsWithDotExpr"}),
K_g_TinyExpressionP4AST_2e_ContainsDotExpr => serde_json::json!({"fields":{
"patterns":(tree.items(id, 1).iter().map(|&v| value(v)).collect::<Vec<_>>()),
"value":(value(tree.slot(id, 0))),
},"span":span_of(&node),"type":"ContainsDotExpr"}),
K_g_TinyExpressionP4AST_2e_IsPresentExpr => serde_json::json!({"fields":{
"value":(value(tree.slot(id, 0))),
},"span":span_of(&node),"type":"IsPresentExpr"}),
K_g_TinyExpressionP4AST_2e_InTimeRangeExpr => serde_json::json!({"fields":{
"endHour":(value(tree.slot(id, 1))),
"startHour":(value(tree.slot(id, 0))),
},"span":span_of(&node),"type":"InTimeRangeExpr"}),
K_g_TinyExpressionP4AST_2e_InDayTimeRangeExpr => serde_json::json!({"fields":{
"endDay":(text(tree.slot(id, 2))),
"endHour":(value(tree.slot(id, 3))),
"startDay":(text(tree.slot(id, 0))),
"startHour":(value(tree.slot(id, 1))),
},"span":span_of(&node),"type":"InDayTimeRangeExpr"}),
K_g_TinyExpressionP4AST_2e_SliceExpr => serde_json::json!({"fields":{
"end":({ let v = tree.slot(id, 2); (v != NONE).then(|| value(v)) }),
"start":({ let v = tree.slot(id, 1); (v != NONE).then(|| value(v)) }),
"step":({ let v = tree.slot(id, 3); (v != NONE).then(|| value(v)) }),
"value":(value(tree.slot(id, 0))),
},"span":span_of(&node),"type":"SliceExpr"}),
K_g_TinyExpressionP4AST_2e_StringConcatExpr => serde_json::json!({"fields":{
"left":(value(tree.slot(id, 0))),
"op":(tree.items(id, 1).iter().map(|&v| text(v)).collect::<Vec<_>>()),
"right":(tree.items(id, 3).iter().map(|&v| value(v)).collect::<Vec<_>>()),
},"span":span_of(&node),"type":"StringConcatExpr"}),
K_g_TinyExpressionP4AST_2e_StringCastVariableRefExpr => serde_json::json!({"fields":{
"name":(text(tree.slot(id, 0))),
},"span":span_of(&node),"type":"StringCastVariableRefExpr"}),
K_g_TinyExpressionP4AST_2e_StringTypedVariableRefExpr => serde_json::json!({"fields":{
"name":(text(tree.slot(id, 0))),
},"span":span_of(&node),"type":"StringTypedVariableRefExpr"}),
K_g_TinyExpressionP4AST_2e_BooleanOrExpr => serde_json::json!({"fields":{
"left":(value(tree.slot(id, 0))),
"op":(tree.items(id, 1).iter().map(|&v| text(v)).collect::<Vec<_>>()),
"right":(tree.items(id, 3).iter().map(|&v| value(v)).collect::<Vec<_>>()),
},"span":span_of(&node),"type":"BooleanOrExpr"}),
K_g_TinyExpressionP4AST_2e_BooleanAndExpr => serde_json::json!({"fields":{
"left":(value(tree.slot(id, 0))),
"op":(tree.items(id, 1).iter().map(|&v| text(v)).collect::<Vec<_>>()),
"right":(tree.items(id, 3).iter().map(|&v| value(v)).collect::<Vec<_>>()),
},"span":span_of(&node),"type":"BooleanAndExpr"}),
K_g_TinyExpressionP4AST_2e_BooleanXorExpr => serde_json::json!({"fields":{
"left":(value(tree.slot(id, 0))),
"op":(tree.items(id, 1).iter().map(|&v| text(v)).collect::<Vec<_>>()),
"right":(tree.items(id, 3).iter().map(|&v| value(v)).collect::<Vec<_>>()),
},"span":span_of(&node),"type":"BooleanXorExpr"}),
K_g_TinyExpressionP4AST_2e_NotExpr => serde_json::json!({"fields":{
"value":(value(tree.slot(id, 0))),
},"span":span_of(&node),"type":"NotExpr"}),
K_g_TinyExpressionP4AST_2e_BooleanEqualityExpr => serde_json::json!({"fields":{
"left":(value(tree.slot(id, 0))),
"op":(text(tree.slot(id, 1))),
"right":(value(tree.slot(id, 2))),
},"span":span_of(&node),"type":"BooleanEqualityExpr"}),
K_g_TinyExpressionP4AST_2e_BooleanFactorExpr => serde_json::json!({"fields":{
"value":(value(tree.slot(id, 0))),
},"span":span_of(&node),"type":"BooleanFactorExpr"}),
K_g_TinyExpressionP4AST_2e_StringComparisonExpr => serde_json::json!({"fields":{
"left":(value(tree.slot(id, 0))),
"op":(text(tree.slot(id, 1))),
"right":(value(tree.slot(id, 2))),
},"span":span_of(&node),"type":"StringComparisonExpr"}),
K_g_TinyExpressionP4AST_2e_ComparisonExpr => serde_json::json!({"fields":{
"left":(value(tree.slot(id, 0))),
"op":(text(tree.slot(id, 1))),
"right":(value(tree.slot(id, 2))),
},"span":span_of(&node),"type":"ComparisonExpr"}),
K_g_TinyExpressionP4AST_2e_ObjectExpr => serde_json::json!({"fields":{
"value":(value(tree.slot(id, 0))),
},"span":span_of(&node),"type":"ObjectExpr"}),
K_g_TinyExpressionP4AST_2e_IfExpr => serde_json::json!({"fields":{
"condition":(value(tree.slot(id, 0))),
"elseExpr":(value(tree.slot(id, 2))),
"thenExpr":(value(tree.slot(id, 1))),
},"span":span_of(&node),"type":"IfExpr"}),
K_g_TinyExpressionP4AST_2e_BranchExpressionExpr => serde_json::json!({"fields":{
"value":(value(tree.slot(id, 0))),
},"span":span_of(&node),"type":"BranchExpressionExpr"}),
K_g_TinyExpressionP4AST_2e_NumberMatchExpr => serde_json::json!({"fields":{
"defaultCase":(value(tree.slot(id, 3))),
"firstCase":(value(tree.slot(id, 0))),
"moreCases":(tree.items(id, 1).iter().map(|&v| value(v)).collect::<Vec<_>>()),
},"span":span_of(&node),"type":"NumberMatchExpr"}),
K_g_TinyExpressionP4AST_2e_NumberCaseExpr => serde_json::json!({"fields":{
"condition":(value(tree.slot(id, 0))),
"value":(value(tree.slot(id, 1))),
},"span":span_of(&node),"type":"NumberCaseExpr"}),
K_g_TinyExpressionP4AST_2e_NumberDefaultCaseExpr => serde_json::json!({"fields":{
"value":(value(tree.slot(id, 0))),
},"span":span_of(&node),"type":"NumberDefaultCaseExpr"}),
K_g_TinyExpressionP4AST_2e_NumberCaseValueExpr => serde_json::json!({"fields":{
"value":(value(tree.slot(id, 0))),
},"span":span_of(&node),"type":"NumberCaseValueExpr"}),
K_g_TinyExpressionP4AST_2e_StringMatchExpr => serde_json::json!({"fields":{
"defaultCase":(value(tree.slot(id, 3))),
"firstCase":(value(tree.slot(id, 0))),
"moreCases":(tree.items(id, 1).iter().map(|&v| value(v)).collect::<Vec<_>>()),
},"span":span_of(&node),"type":"StringMatchExpr"}),
K_g_TinyExpressionP4AST_2e_StringCaseExpr => serde_json::json!({"fields":{
"condition":(value(tree.slot(id, 0))),
"value":(value(tree.slot(id, 1))),
},"span":span_of(&node),"type":"StringCaseExpr"}),
K_g_TinyExpressionP4AST_2e_StringDefaultCaseExpr => serde_json::json!({"fields":{
"value":(value(tree.slot(id, 0))),
},"span":span_of(&node),"type":"StringDefaultCaseExpr"}),
K_g_TinyExpressionP4AST_2e_StringCaseValueExpr => serde_json::json!({"fields":{
"value":(value(tree.slot(id, 0))),
},"span":span_of(&node),"type":"StringCaseValueExpr"}),
K_g_TinyExpressionP4AST_2e_BooleanMatchExpr => serde_json::json!({"fields":{
"defaultCase":(value(tree.slot(id, 3))),
"firstCase":(value(tree.slot(id, 0))),
"moreCases":(tree.items(id, 1).iter().map(|&v| value(v)).collect::<Vec<_>>()),
},"span":span_of(&node),"type":"BooleanMatchExpr"}),
K_g_TinyExpressionP4AST_2e_BooleanCaseExpr => serde_json::json!({"fields":{
"condition":(value(tree.slot(id, 0))),
"value":(value(tree.slot(id, 1))),
},"span":span_of(&node),"type":"BooleanCaseExpr"}),
K_g_TinyExpressionP4AST_2e_BooleanDefaultCaseExpr => serde_json::json!({"fields":{
"value":(value(tree.slot(id, 0))),
},"span":span_of(&node),"type":"BooleanDefaultCaseExpr"}),
K_g_TinyExpressionP4AST_2e_BooleanCaseValueExpr => serde_json::json!({"fields":{
"value":(value(tree.slot(id, 0))),
},"span":span_of(&node),"type":"BooleanCaseValueExpr"}),
K_g_TinyExpressionP4AST_2e_VariableRefExpr => serde_json::json!({"fields":{
"name":(text(tree.slot(id, 0))),
"type":({ let v = tree.slot(id, 1); (v != NONE).then(|| text(v)) }),
},"span":span_of(&node),"type":"VariableRefExpr"}),
K_g_TinyExpressionP4AST_2e_ExpressionExpr => serde_json::json!({"fields":{
"value":(value(tree.slot(id, 0))),
},"span":span_of(&node),"type":"ExpressionExpr"}),
_ => unreachable!("unknown AST node kind"), } }
}
}
