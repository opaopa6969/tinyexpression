// The public typed AST of this crate. Originally emitted by the unlaxer Rust backend;
// since the parser moved to the vendored ubnfc backend (issue #178) it is maintained here
// and `src/generated/compat.rs` converts the ubnfc AST into it. The node set is still
// derived from the P4 grammar, so changing the grammar means updating this file too.
use crate::{json_string, Span};

#[derive(Debug, Clone, PartialEq)]
pub enum AstValue {
    Text { text: String, span: Span },
    Node(Box<Ast>),
}

impl AstValue {
    pub fn span(&self) -> Span {
        match self {
            Self::Text { span, .. } => *span,
            Self::Node(node) => node.span(),
        }
    }

    pub fn canonical_json(&self) -> String {
        match self {
            Self::Text { text, .. } => json_string(text),
            Self::Node(node) => node.canonical_json(),
        }
    }
}

#[allow(non_snake_case, non_camel_case_types)]
#[derive(Debug, Clone, PartialEq)]
pub enum Ast {
    r#FormulaExpr { span: Span, r#imports: Vec<Ast>, r#declarations: Vec<Ast>, r#expression: Box<Ast>, r#methods: Vec<Ast> },
    r#CodeBlockExpr { span: Span },
    r#ImportDeclarationExpr { span: Span, r#className: Box<Ast>, r#method: Option<String>, r#alias: String },
    r#QualifiedNameExpr { span: Span, r#head: String, r#tail: Vec<String> },
    r#NumberVariableDeclarationExpr { span: Span, r#varName: String, r#onlyIfAbsent: Option<Box<Ast>>, r#value: Option<Box<Ast>>, r#desc: Option<String> },
    r#StringVariableDeclarationExpr { span: Span, r#varName: String, r#onlyIfAbsent: Option<Box<Ast>>, r#value: Option<Box<Ast>>, r#desc: Option<String> },
    r#BooleanVariableDeclarationExpr { span: Span, r#varName: String, r#onlyIfAbsent: Option<Box<Ast>>, r#value: Option<Box<Ast>>, r#desc: Option<String> },
    r#ObjectVariableDeclarationExpr { span: Span, r#varName: String, r#onlyIfAbsent: Option<Box<Ast>>, r#value: Option<Box<Ast>>, r#desc: Option<String> },
    r#OnlyIfAbsentExpr { span: Span },
    r#NumberMethodDeclarationExpr { span: Span, r#methodName: String, r#parameters: Option<Box<Ast>>, r#expression: Box<Ast> },
    r#StringMethodDeclarationExpr { span: Span, r#methodName: String, r#parameters: Option<Box<Ast>>, r#expression: Box<Ast> },
    r#BooleanMethodDeclarationExpr { span: Span, r#methodName: String, r#parameters: Option<Box<Ast>>, r#expression: Box<Ast> },
    r#ObjectMethodDeclarationExpr { span: Span, r#methodName: String, r#parameters: Option<Box<Ast>>, r#expression: Box<Ast> },
    r#MethodParametersExpr { span: Span, r#values: Vec<Ast> },
    r#MethodParameterExpr { span: Span, r#paramName: String, r#type: Option<String> },
    r#ExternalBooleanInvocationExpr { span: Span, r#className: Option<Box<Ast>>, r#name: String, r#args: Option<Box<Ast>> },
    r#ExternalNumberInvocationExpr { span: Span, r#className: Option<Box<Ast>>, r#name: String, r#args: Option<Box<Ast>> },
    r#ExternalStringInvocationExpr { span: Span, r#className: Option<Box<Ast>>, r#name: String, r#args: Option<Box<Ast>> },
    r#ExternalObjectInvocationExpr { span: Span, r#className: Option<Box<Ast>>, r#name: String, r#args: Option<Box<Ast>> },
    r#MethodInvocationExpr { span: Span, r#name: String, r#args: Option<Box<Ast>> },
    r#TernaryExpr { span: Span, r#condition: Box<Ast>, r#thenExpr: Box<Ast>, r#elseExpr: Box<Ast> },
    r#ArgumentExpressionExpr { span: Span, r#value: Box<Ast> },
    r#ArgumentsExpr { span: Span, r#values: Vec<Ast> },
    r#BinaryExpr { span: Span, r#left: AstValue, r#op: Vec<String>, r#right: Vec<AstValue> },
    r#SinExpr { span: Span, r#arg: Box<Ast> },
    r#CosExpr { span: Span, r#arg: Box<Ast> },
    r#TanExpr { span: Span, r#arg: Box<Ast> },
    r#SqrtExpr { span: Span, r#arg: Box<Ast> },
    r#MinExpr { span: Span, r#first: Box<Ast>, r#rest: Vec<Ast> },
    r#MaxExpr { span: Span, r#first: Box<Ast>, r#rest: Vec<Ast> },
    r#RandomExpr { span: Span },
    r#AbsExpr { span: Span, r#arg: Box<Ast> },
    r#RoundExpr { span: Span, r#arg: Box<Ast> },
    r#CeilExpr { span: Span, r#arg: Box<Ast> },
    r#FloorExpr { span: Span, r#arg: Box<Ast> },
    r#PowExpr { span: Span, r#base: Box<Ast>, r#exponent: Box<Ast> },
    r#LogExpr { span: Span, r#arg: Box<Ast> },
    r#ExpExpr { span: Span, r#arg: Box<Ast> },
    r#ToNumExpr { span: Span, r#value: Box<Ast>, r#defaultValue: Box<Ast> },
    r#ToUpperCaseExpr { span: Span, r#value: Box<Ast> },
    r#ToLowerCaseExpr { span: Span, r#value: Box<Ast> },
    r#TrimExpr { span: Span, r#value: Box<Ast> },
    r#LengthExpr { span: Span, r#value: Box<Ast> },
    r#ToUpperCaseDotExpr { span: Span, r#value: Box<Ast> },
    r#ToLowerCaseDotExpr { span: Span, r#value: Box<Ast> },
    r#TrimDotExpr { span: Span, r#value: Box<Ast> },
    r#LengthDotExpr { span: Span, r#value: Box<Ast> },
    r#StartsWithExpr { span: Span, r#value: Box<Ast>, r#patterns: Vec<Ast> },
    r#EndsWithExpr { span: Span, r#value: Box<Ast>, r#patterns: Vec<Ast> },
    r#ContainsExpr { span: Span, r#value: Box<Ast>, r#patterns: Vec<Ast> },
    r#InExpr { span: Span, r#value: Box<Ast>, r#candidates: Vec<Ast> },
    r#StartsWithDotExpr { span: Span, r#value: Box<Ast>, r#patterns: Vec<Ast> },
    r#EndsWithDotExpr { span: Span, r#value: Box<Ast>, r#patterns: Vec<Ast> },
    r#ContainsDotExpr { span: Span, r#value: Box<Ast>, r#patterns: Vec<Ast> },
    r#IsPresentExpr { span: Span, r#value: Box<Ast> },
    r#InTimeRangeExpr { span: Span, r#startHour: Box<Ast>, r#endHour: Box<Ast> },
    r#InDayTimeRangeExpr { span: Span, r#startDay: String, r#startHour: Box<Ast>, r#endDay: String, r#endHour: Box<Ast> },
    r#SliceExpr { span: Span, r#value: AstValue, r#start: Option<Box<Ast>>, r#end: Option<Box<Ast>>, r#step: Option<Box<Ast>> },
    r#StringConcatExpr { span: Span, r#left: AstValue, r#op: Vec<String>, r#right: Vec<AstValue> },
    r#StringCastVariableRefExpr { span: Span, r#name: String },
    r#StringTypedVariableRefExpr { span: Span, r#name: String },
    r#BooleanOrExpr { span: Span, r#left: Box<Ast>, r#op: Vec<String>, r#right: Vec<Ast> },
    r#BooleanAndExpr { span: Span, r#left: Box<Ast>, r#op: Vec<String>, r#right: Vec<Ast> },
    r#BooleanXorExpr { span: Span, r#left: Box<Ast>, r#op: Vec<String>, r#right: Vec<Ast> },
    r#NotExpr { span: Span, r#value: Box<Ast> },
    r#BooleanEqualityExpr { span: Span, r#left: AstValue, r#op: String, r#right: AstValue },
    r#BooleanFactorExpr { span: Span, r#value: AstValue },
    r#StringComparisonExpr { span: Span, r#left: Box<Ast>, r#op: String, r#right: Box<Ast> },
    r#ComparisonExpr { span: Span, r#left: Box<Ast>, r#op: String, r#right: Box<Ast> },
    r#ObjectExpr { span: Span, r#value: Box<Ast> },
    r#IfExpr { span: Span, r#condition: Box<Ast>, r#thenExpr: Box<Ast>, r#elseExpr: Box<Ast> },
    r#BranchExpressionExpr { span: Span, r#value: Box<Ast> },
    r#NumberMatchExpr { span: Span, r#firstCase: Box<Ast>, r#moreCases: Vec<Ast>, r#defaultCase: Box<Ast> },
    r#NumberCaseExpr { span: Span, r#condition: Box<Ast>, r#value: Box<Ast> },
    r#NumberDefaultCaseExpr { span: Span, r#value: Box<Ast> },
    r#NumberCaseValueExpr { span: Span, r#value: Box<Ast> },
    r#StringMatchExpr { span: Span, r#firstCase: Box<Ast>, r#moreCases: Vec<Ast>, r#defaultCase: Box<Ast> },
    r#StringCaseExpr { span: Span, r#condition: Box<Ast>, r#value: Box<Ast> },
    r#StringDefaultCaseExpr { span: Span, r#value: Box<Ast> },
    r#StringCaseValueExpr { span: Span, r#value: Box<Ast> },
    r#BooleanMatchExpr { span: Span, r#firstCase: Box<Ast>, r#moreCases: Vec<Ast>, r#defaultCase: Box<Ast> },
    r#BooleanCaseExpr { span: Span, r#condition: Box<Ast>, r#value: Box<Ast> },
    r#BooleanDefaultCaseExpr { span: Span, r#value: Box<Ast> },
    r#BooleanCaseValueExpr { span: Span, r#value: Box<Ast> },
    r#VariableRefExpr { span: Span, r#name: String, r#type: Option<String> },
    r#ExpressionExpr { span: Span, r#value: Box<Ast> },
}

#[allow(non_snake_case)]
impl Ast {
    pub fn span(&self) -> Span {
        match self {
            Self::r#FormulaExpr { span, .. } => *span,
            Self::r#CodeBlockExpr { span, .. } => *span,
            Self::r#ImportDeclarationExpr { span, .. } => *span,
            Self::r#QualifiedNameExpr { span, .. } => *span,
            Self::r#NumberVariableDeclarationExpr { span, .. } => *span,
            Self::r#StringVariableDeclarationExpr { span, .. } => *span,
            Self::r#BooleanVariableDeclarationExpr { span, .. } => *span,
            Self::r#ObjectVariableDeclarationExpr { span, .. } => *span,
            Self::r#OnlyIfAbsentExpr { span, .. } => *span,
            Self::r#NumberMethodDeclarationExpr { span, .. } => *span,
            Self::r#StringMethodDeclarationExpr { span, .. } => *span,
            Self::r#BooleanMethodDeclarationExpr { span, .. } => *span,
            Self::r#ObjectMethodDeclarationExpr { span, .. } => *span,
            Self::r#MethodParametersExpr { span, .. } => *span,
            Self::r#MethodParameterExpr { span, .. } => *span,
            Self::r#ExternalBooleanInvocationExpr { span, .. } => *span,
            Self::r#ExternalNumberInvocationExpr { span, .. } => *span,
            Self::r#ExternalStringInvocationExpr { span, .. } => *span,
            Self::r#ExternalObjectInvocationExpr { span, .. } => *span,
            Self::r#MethodInvocationExpr { span, .. } => *span,
            Self::r#TernaryExpr { span, .. } => *span,
            Self::r#ArgumentExpressionExpr { span, .. } => *span,
            Self::r#ArgumentsExpr { span, .. } => *span,
            Self::r#BinaryExpr { span, .. } => *span,
            Self::r#SinExpr { span, .. } => *span,
            Self::r#CosExpr { span, .. } => *span,
            Self::r#TanExpr { span, .. } => *span,
            Self::r#SqrtExpr { span, .. } => *span,
            Self::r#MinExpr { span, .. } => *span,
            Self::r#MaxExpr { span, .. } => *span,
            Self::r#RandomExpr { span, .. } => *span,
            Self::r#AbsExpr { span, .. } => *span,
            Self::r#RoundExpr { span, .. } => *span,
            Self::r#CeilExpr { span, .. } => *span,
            Self::r#FloorExpr { span, .. } => *span,
            Self::r#PowExpr { span, .. } => *span,
            Self::r#LogExpr { span, .. } => *span,
            Self::r#ExpExpr { span, .. } => *span,
            Self::r#ToNumExpr { span, .. } => *span,
            Self::r#ToUpperCaseExpr { span, .. } => *span,
            Self::r#ToLowerCaseExpr { span, .. } => *span,
            Self::r#TrimExpr { span, .. } => *span,
            Self::r#LengthExpr { span, .. } => *span,
            Self::r#ToUpperCaseDotExpr { span, .. } => *span,
            Self::r#ToLowerCaseDotExpr { span, .. } => *span,
            Self::r#TrimDotExpr { span, .. } => *span,
            Self::r#LengthDotExpr { span, .. } => *span,
            Self::r#StartsWithExpr { span, .. } => *span,
            Self::r#EndsWithExpr { span, .. } => *span,
            Self::r#ContainsExpr { span, .. } => *span,
            Self::r#InExpr { span, .. } => *span,
            Self::r#StartsWithDotExpr { span, .. } => *span,
            Self::r#EndsWithDotExpr { span, .. } => *span,
            Self::r#ContainsDotExpr { span, .. } => *span,
            Self::r#IsPresentExpr { span, .. } => *span,
            Self::r#InTimeRangeExpr { span, .. } => *span,
            Self::r#InDayTimeRangeExpr { span, .. } => *span,
            Self::r#SliceExpr { span, .. } => *span,
            Self::r#StringConcatExpr { span, .. } => *span,
            Self::r#StringCastVariableRefExpr { span, .. } => *span,
            Self::r#StringTypedVariableRefExpr { span, .. } => *span,
            Self::r#BooleanOrExpr { span, .. } => *span,
            Self::r#BooleanAndExpr { span, .. } => *span,
            Self::r#BooleanXorExpr { span, .. } => *span,
            Self::r#NotExpr { span, .. } => *span,
            Self::r#BooleanEqualityExpr { span, .. } => *span,
            Self::r#BooleanFactorExpr { span, .. } => *span,
            Self::r#StringComparisonExpr { span, .. } => *span,
            Self::r#ComparisonExpr { span, .. } => *span,
            Self::r#ObjectExpr { span, .. } => *span,
            Self::r#IfExpr { span, .. } => *span,
            Self::r#BranchExpressionExpr { span, .. } => *span,
            Self::r#NumberMatchExpr { span, .. } => *span,
            Self::r#NumberCaseExpr { span, .. } => *span,
            Self::r#NumberDefaultCaseExpr { span, .. } => *span,
            Self::r#NumberCaseValueExpr { span, .. } => *span,
            Self::r#StringMatchExpr { span, .. } => *span,
            Self::r#StringCaseExpr { span, .. } => *span,
            Self::r#StringDefaultCaseExpr { span, .. } => *span,
            Self::r#StringCaseValueExpr { span, .. } => *span,
            Self::r#BooleanMatchExpr { span, .. } => *span,
            Self::r#BooleanCaseExpr { span, .. } => *span,
            Self::r#BooleanDefaultCaseExpr { span, .. } => *span,
            Self::r#BooleanCaseValueExpr { span, .. } => *span,
            Self::r#VariableRefExpr { span, .. } => *span,
            Self::r#ExpressionExpr { span, .. } => *span,
        }
    }

    pub fn canonical_json(&self) -> String {
        match self {
            Self::r#FormulaExpr { span, r#imports, r#declarations, r#expression, r#methods } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("imports"), format!("[{}]", r#imports.iter().map(|value| value.canonical_json()).collect::<Vec<_>>().join(","))),
                    format!("{}:{}", json_string("declarations"), format!("[{}]", r#declarations.iter().map(|value| value.canonical_json()).collect::<Vec<_>>().join(","))),
                    format!("{}:{}", json_string("expression"), r#expression.canonical_json()),
                    format!("{}:{}", json_string("methods"), format!("[{}]", r#methods.iter().map(|value| value.canonical_json()).collect::<Vec<_>>().join(","))),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("FormulaExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#CodeBlockExpr { span } => {
                let fields: Vec<String> = vec![
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("CodeBlockExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#ImportDeclarationExpr { span, r#className, r#method, r#alias } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("className"), r#className.canonical_json()),
                    format!("{}:{}", json_string("method"), r#method.as_ref().map_or_else(|| "null".to_owned(), |value| json_string(value))),
                    format!("{}:{}", json_string("alias"), json_string(r#alias)),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("ImportDeclarationExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#QualifiedNameExpr { span, r#head, r#tail } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("head"), json_string(r#head)),
                    format!("{}:{}", json_string("tail"), format!("[{}]", r#tail.iter().map(|value| json_string(value)).collect::<Vec<_>>().join(","))),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("QualifiedNameExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#NumberVariableDeclarationExpr { span, r#varName, r#onlyIfAbsent, r#value, r#desc } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("varName"), json_string(r#varName)),
                    format!("{}:{}", json_string("onlyIfAbsent"), r#onlyIfAbsent.as_ref().map_or_else(|| "null".to_owned(), |value| value.canonical_json())),
                    format!("{}:{}", json_string("value"), r#value.as_ref().map_or_else(|| "null".to_owned(), |value| value.canonical_json())),
                    format!("{}:{}", json_string("desc"), r#desc.as_ref().map_or_else(|| "null".to_owned(), |value| json_string(value))),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("NumberVariableDeclarationExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#StringVariableDeclarationExpr { span, r#varName, r#onlyIfAbsent, r#value, r#desc } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("varName"), json_string(r#varName)),
                    format!("{}:{}", json_string("onlyIfAbsent"), r#onlyIfAbsent.as_ref().map_or_else(|| "null".to_owned(), |value| value.canonical_json())),
                    format!("{}:{}", json_string("value"), r#value.as_ref().map_or_else(|| "null".to_owned(), |value| value.canonical_json())),
                    format!("{}:{}", json_string("desc"), r#desc.as_ref().map_or_else(|| "null".to_owned(), |value| json_string(value))),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("StringVariableDeclarationExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#BooleanVariableDeclarationExpr { span, r#varName, r#onlyIfAbsent, r#value, r#desc } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("varName"), json_string(r#varName)),
                    format!("{}:{}", json_string("onlyIfAbsent"), r#onlyIfAbsent.as_ref().map_or_else(|| "null".to_owned(), |value| value.canonical_json())),
                    format!("{}:{}", json_string("value"), r#value.as_ref().map_or_else(|| "null".to_owned(), |value| value.canonical_json())),
                    format!("{}:{}", json_string("desc"), r#desc.as_ref().map_or_else(|| "null".to_owned(), |value| json_string(value))),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("BooleanVariableDeclarationExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#ObjectVariableDeclarationExpr { span, r#varName, r#onlyIfAbsent, r#value, r#desc } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("varName"), json_string(r#varName)),
                    format!("{}:{}", json_string("onlyIfAbsent"), r#onlyIfAbsent.as_ref().map_or_else(|| "null".to_owned(), |value| value.canonical_json())),
                    format!("{}:{}", json_string("value"), r#value.as_ref().map_or_else(|| "null".to_owned(), |value| value.canonical_json())),
                    format!("{}:{}", json_string("desc"), r#desc.as_ref().map_or_else(|| "null".to_owned(), |value| json_string(value))),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("ObjectVariableDeclarationExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#OnlyIfAbsentExpr { span } => {
                let fields: Vec<String> = vec![
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("OnlyIfAbsentExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#NumberMethodDeclarationExpr { span, r#methodName, r#parameters, r#expression } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("methodName"), json_string(r#methodName)),
                    format!("{}:{}", json_string("parameters"), r#parameters.as_ref().map_or_else(|| "null".to_owned(), |value| value.canonical_json())),
                    format!("{}:{}", json_string("expression"), r#expression.canonical_json()),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("NumberMethodDeclarationExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#StringMethodDeclarationExpr { span, r#methodName, r#parameters, r#expression } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("methodName"), json_string(r#methodName)),
                    format!("{}:{}", json_string("parameters"), r#parameters.as_ref().map_or_else(|| "null".to_owned(), |value| value.canonical_json())),
                    format!("{}:{}", json_string("expression"), r#expression.canonical_json()),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("StringMethodDeclarationExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#BooleanMethodDeclarationExpr { span, r#methodName, r#parameters, r#expression } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("methodName"), json_string(r#methodName)),
                    format!("{}:{}", json_string("parameters"), r#parameters.as_ref().map_or_else(|| "null".to_owned(), |value| value.canonical_json())),
                    format!("{}:{}", json_string("expression"), r#expression.canonical_json()),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("BooleanMethodDeclarationExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#ObjectMethodDeclarationExpr { span, r#methodName, r#parameters, r#expression } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("methodName"), json_string(r#methodName)),
                    format!("{}:{}", json_string("parameters"), r#parameters.as_ref().map_or_else(|| "null".to_owned(), |value| value.canonical_json())),
                    format!("{}:{}", json_string("expression"), r#expression.canonical_json()),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("ObjectMethodDeclarationExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#MethodParametersExpr { span, r#values } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("values"), format!("[{}]", r#values.iter().map(|value| value.canonical_json()).collect::<Vec<_>>().join(","))),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("MethodParametersExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#MethodParameterExpr { span, r#paramName, r#type } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("paramName"), json_string(r#paramName)),
                    format!("{}:{}", json_string("type"), r#type.as_ref().map_or_else(|| "null".to_owned(), |value| json_string(value))),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("MethodParameterExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#ExternalBooleanInvocationExpr { span, r#className, r#name, r#args } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("className"), r#className.as_ref().map_or_else(|| "null".to_owned(), |value| value.canonical_json())),
                    format!("{}:{}", json_string("name"), json_string(r#name)),
                    format!("{}:{}", json_string("args"), r#args.as_ref().map_or_else(|| "null".to_owned(), |value| value.canonical_json())),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("ExternalBooleanInvocationExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#ExternalNumberInvocationExpr { span, r#className, r#name, r#args } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("className"), r#className.as_ref().map_or_else(|| "null".to_owned(), |value| value.canonical_json())),
                    format!("{}:{}", json_string("name"), json_string(r#name)),
                    format!("{}:{}", json_string("args"), r#args.as_ref().map_or_else(|| "null".to_owned(), |value| value.canonical_json())),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("ExternalNumberInvocationExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#ExternalStringInvocationExpr { span, r#className, r#name, r#args } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("className"), r#className.as_ref().map_or_else(|| "null".to_owned(), |value| value.canonical_json())),
                    format!("{}:{}", json_string("name"), json_string(r#name)),
                    format!("{}:{}", json_string("args"), r#args.as_ref().map_or_else(|| "null".to_owned(), |value| value.canonical_json())),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("ExternalStringInvocationExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#ExternalObjectInvocationExpr { span, r#className, r#name, r#args } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("className"), r#className.as_ref().map_or_else(|| "null".to_owned(), |value| value.canonical_json())),
                    format!("{}:{}", json_string("name"), json_string(r#name)),
                    format!("{}:{}", json_string("args"), r#args.as_ref().map_or_else(|| "null".to_owned(), |value| value.canonical_json())),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("ExternalObjectInvocationExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#MethodInvocationExpr { span, r#name, r#args } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("name"), json_string(r#name)),
                    format!("{}:{}", json_string("args"), r#args.as_ref().map_or_else(|| "null".to_owned(), |value| value.canonical_json())),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("MethodInvocationExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#TernaryExpr { span, r#condition, r#thenExpr, r#elseExpr } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("condition"), r#condition.canonical_json()),
                    format!("{}:{}", json_string("thenExpr"), r#thenExpr.canonical_json()),
                    format!("{}:{}", json_string("elseExpr"), r#elseExpr.canonical_json()),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("TernaryExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#ArgumentExpressionExpr { span, r#value } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("value"), r#value.canonical_json()),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("ArgumentExpressionExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#ArgumentsExpr { span, r#values } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("values"), format!("[{}]", r#values.iter().map(|value| value.canonical_json()).collect::<Vec<_>>().join(","))),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("ArgumentsExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#BinaryExpr { span, r#left, r#op, r#right } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("left"), r#left.canonical_json()),
                    format!("{}:{}", json_string("op"), format!("[{}]", r#op.iter().map(|value| json_string(value)).collect::<Vec<_>>().join(","))),
                    format!("{}:{}", json_string("right"), format!("[{}]", r#right.iter().map(|value| value.canonical_json()).collect::<Vec<_>>().join(","))),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("BinaryExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#SinExpr { span, r#arg } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("arg"), r#arg.canonical_json()),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("SinExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#CosExpr { span, r#arg } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("arg"), r#arg.canonical_json()),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("CosExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#TanExpr { span, r#arg } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("arg"), r#arg.canonical_json()),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("TanExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#SqrtExpr { span, r#arg } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("arg"), r#arg.canonical_json()),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("SqrtExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#MinExpr { span, r#first, r#rest } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("first"), r#first.canonical_json()),
                    format!("{}:{}", json_string("rest"), format!("[{}]", r#rest.iter().map(|value| value.canonical_json()).collect::<Vec<_>>().join(","))),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("MinExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#MaxExpr { span, r#first, r#rest } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("first"), r#first.canonical_json()),
                    format!("{}:{}", json_string("rest"), format!("[{}]", r#rest.iter().map(|value| value.canonical_json()).collect::<Vec<_>>().join(","))),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("MaxExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#RandomExpr { span } => {
                let fields: Vec<String> = vec![
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("RandomExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#AbsExpr { span, r#arg } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("arg"), r#arg.canonical_json()),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("AbsExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#RoundExpr { span, r#arg } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("arg"), r#arg.canonical_json()),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("RoundExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#CeilExpr { span, r#arg } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("arg"), r#arg.canonical_json()),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("CeilExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#FloorExpr { span, r#arg } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("arg"), r#arg.canonical_json()),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("FloorExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#PowExpr { span, r#base, r#exponent } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("base"), r#base.canonical_json()),
                    format!("{}:{}", json_string("exponent"), r#exponent.canonical_json()),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("PowExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#LogExpr { span, r#arg } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("arg"), r#arg.canonical_json()),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("LogExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#ExpExpr { span, r#arg } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("arg"), r#arg.canonical_json()),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("ExpExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#ToNumExpr { span, r#value, r#defaultValue } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("value"), r#value.canonical_json()),
                    format!("{}:{}", json_string("defaultValue"), r#defaultValue.canonical_json()),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("ToNumExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#ToUpperCaseExpr { span, r#value } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("value"), r#value.canonical_json()),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("ToUpperCaseExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#ToLowerCaseExpr { span, r#value } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("value"), r#value.canonical_json()),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("ToLowerCaseExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#TrimExpr { span, r#value } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("value"), r#value.canonical_json()),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("TrimExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#LengthExpr { span, r#value } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("value"), r#value.canonical_json()),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("LengthExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#ToUpperCaseDotExpr { span, r#value } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("value"), r#value.canonical_json()),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("ToUpperCaseDotExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#ToLowerCaseDotExpr { span, r#value } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("value"), r#value.canonical_json()),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("ToLowerCaseDotExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#TrimDotExpr { span, r#value } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("value"), r#value.canonical_json()),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("TrimDotExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#LengthDotExpr { span, r#value } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("value"), r#value.canonical_json()),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("LengthDotExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#StartsWithExpr { span, r#value, r#patterns } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("value"), r#value.canonical_json()),
                    format!("{}:{}", json_string("patterns"), format!("[{}]", r#patterns.iter().map(|value| value.canonical_json()).collect::<Vec<_>>().join(","))),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("StartsWithExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#EndsWithExpr { span, r#value, r#patterns } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("value"), r#value.canonical_json()),
                    format!("{}:{}", json_string("patterns"), format!("[{}]", r#patterns.iter().map(|value| value.canonical_json()).collect::<Vec<_>>().join(","))),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("EndsWithExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#ContainsExpr { span, r#value, r#patterns } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("value"), r#value.canonical_json()),
                    format!("{}:{}", json_string("patterns"), format!("[{}]", r#patterns.iter().map(|value| value.canonical_json()).collect::<Vec<_>>().join(","))),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("ContainsExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#InExpr { span, r#value, r#candidates } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("value"), r#value.canonical_json()),
                    format!("{}:{}", json_string("candidates"), format!("[{}]", r#candidates.iter().map(|value| value.canonical_json()).collect::<Vec<_>>().join(","))),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("InExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#StartsWithDotExpr { span, r#value, r#patterns } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("value"), r#value.canonical_json()),
                    format!("{}:{}", json_string("patterns"), format!("[{}]", r#patterns.iter().map(|value| value.canonical_json()).collect::<Vec<_>>().join(","))),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("StartsWithDotExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#EndsWithDotExpr { span, r#value, r#patterns } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("value"), r#value.canonical_json()),
                    format!("{}:{}", json_string("patterns"), format!("[{}]", r#patterns.iter().map(|value| value.canonical_json()).collect::<Vec<_>>().join(","))),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("EndsWithDotExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#ContainsDotExpr { span, r#value, r#patterns } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("value"), r#value.canonical_json()),
                    format!("{}:{}", json_string("patterns"), format!("[{}]", r#patterns.iter().map(|value| value.canonical_json()).collect::<Vec<_>>().join(","))),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("ContainsDotExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#IsPresentExpr { span, r#value } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("value"), r#value.canonical_json()),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("IsPresentExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#InTimeRangeExpr { span, r#startHour, r#endHour } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("startHour"), r#startHour.canonical_json()),
                    format!("{}:{}", json_string("endHour"), r#endHour.canonical_json()),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("InTimeRangeExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#InDayTimeRangeExpr { span, r#startDay, r#startHour, r#endDay, r#endHour } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("startDay"), json_string(r#startDay)),
                    format!("{}:{}", json_string("startHour"), r#startHour.canonical_json()),
                    format!("{}:{}", json_string("endDay"), json_string(r#endDay)),
                    format!("{}:{}", json_string("endHour"), r#endHour.canonical_json()),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("InDayTimeRangeExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#SliceExpr { span, r#value, r#start, r#end, r#step } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("value"), r#value.canonical_json()),
                    format!("{}:{}", json_string("start"), r#start.as_ref().map_or_else(|| "null".to_owned(), |value| value.canonical_json())),
                    format!("{}:{}", json_string("end"), r#end.as_ref().map_or_else(|| "null".to_owned(), |value| value.canonical_json())),
                    format!("{}:{}", json_string("step"), r#step.as_ref().map_or_else(|| "null".to_owned(), |value| value.canonical_json())),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("SliceExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#StringConcatExpr { span, r#left, r#op, r#right } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("left"), r#left.canonical_json()),
                    format!("{}:{}", json_string("op"), format!("[{}]", r#op.iter().map(|value| json_string(value)).collect::<Vec<_>>().join(","))),
                    format!("{}:{}", json_string("right"), format!("[{}]", r#right.iter().map(|value| value.canonical_json()).collect::<Vec<_>>().join(","))),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("StringConcatExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#StringCastVariableRefExpr { span, r#name } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("name"), json_string(r#name)),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("StringCastVariableRefExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#StringTypedVariableRefExpr { span, r#name } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("name"), json_string(r#name)),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("StringTypedVariableRefExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#BooleanOrExpr { span, r#left, r#op, r#right } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("left"), r#left.canonical_json()),
                    format!("{}:{}", json_string("op"), format!("[{}]", r#op.iter().map(|value| json_string(value)).collect::<Vec<_>>().join(","))),
                    format!("{}:{}", json_string("right"), format!("[{}]", r#right.iter().map(|value| value.canonical_json()).collect::<Vec<_>>().join(","))),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("BooleanOrExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#BooleanAndExpr { span, r#left, r#op, r#right } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("left"), r#left.canonical_json()),
                    format!("{}:{}", json_string("op"), format!("[{}]", r#op.iter().map(|value| json_string(value)).collect::<Vec<_>>().join(","))),
                    format!("{}:{}", json_string("right"), format!("[{}]", r#right.iter().map(|value| value.canonical_json()).collect::<Vec<_>>().join(","))),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("BooleanAndExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#BooleanXorExpr { span, r#left, r#op, r#right } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("left"), r#left.canonical_json()),
                    format!("{}:{}", json_string("op"), format!("[{}]", r#op.iter().map(|value| json_string(value)).collect::<Vec<_>>().join(","))),
                    format!("{}:{}", json_string("right"), format!("[{}]", r#right.iter().map(|value| value.canonical_json()).collect::<Vec<_>>().join(","))),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("BooleanXorExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#NotExpr { span, r#value } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("value"), r#value.canonical_json()),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("NotExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#BooleanEqualityExpr { span, r#left, r#op, r#right } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("left"), r#left.canonical_json()),
                    format!("{}:{}", json_string("op"), json_string(r#op)),
                    format!("{}:{}", json_string("right"), r#right.canonical_json()),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("BooleanEqualityExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#BooleanFactorExpr { span, r#value } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("value"), r#value.canonical_json()),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("BooleanFactorExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#StringComparisonExpr { span, r#left, r#op, r#right } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("left"), r#left.canonical_json()),
                    format!("{}:{}", json_string("op"), json_string(r#op)),
                    format!("{}:{}", json_string("right"), r#right.canonical_json()),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("StringComparisonExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#ComparisonExpr { span, r#left, r#op, r#right } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("left"), r#left.canonical_json()),
                    format!("{}:{}", json_string("op"), json_string(r#op)),
                    format!("{}:{}", json_string("right"), r#right.canonical_json()),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("ComparisonExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#ObjectExpr { span, r#value } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("value"), r#value.canonical_json()),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("ObjectExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#IfExpr { span, r#condition, r#thenExpr, r#elseExpr } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("condition"), r#condition.canonical_json()),
                    format!("{}:{}", json_string("thenExpr"), r#thenExpr.canonical_json()),
                    format!("{}:{}", json_string("elseExpr"), r#elseExpr.canonical_json()),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("IfExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#BranchExpressionExpr { span, r#value } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("value"), r#value.canonical_json()),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("BranchExpressionExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#NumberMatchExpr { span, r#firstCase, r#moreCases, r#defaultCase } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("firstCase"), r#firstCase.canonical_json()),
                    format!("{}:{}", json_string("moreCases"), format!("[{}]", r#moreCases.iter().map(|value| value.canonical_json()).collect::<Vec<_>>().join(","))),
                    format!("{}:{}", json_string("defaultCase"), r#defaultCase.canonical_json()),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("NumberMatchExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#NumberCaseExpr { span, r#condition, r#value } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("condition"), r#condition.canonical_json()),
                    format!("{}:{}", json_string("value"), r#value.canonical_json()),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("NumberCaseExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#NumberDefaultCaseExpr { span, r#value } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("value"), r#value.canonical_json()),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("NumberDefaultCaseExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#NumberCaseValueExpr { span, r#value } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("value"), r#value.canonical_json()),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("NumberCaseValueExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#StringMatchExpr { span, r#firstCase, r#moreCases, r#defaultCase } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("firstCase"), r#firstCase.canonical_json()),
                    format!("{}:{}", json_string("moreCases"), format!("[{}]", r#moreCases.iter().map(|value| value.canonical_json()).collect::<Vec<_>>().join(","))),
                    format!("{}:{}", json_string("defaultCase"), r#defaultCase.canonical_json()),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("StringMatchExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#StringCaseExpr { span, r#condition, r#value } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("condition"), r#condition.canonical_json()),
                    format!("{}:{}", json_string("value"), r#value.canonical_json()),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("StringCaseExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#StringDefaultCaseExpr { span, r#value } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("value"), r#value.canonical_json()),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("StringDefaultCaseExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#StringCaseValueExpr { span, r#value } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("value"), r#value.canonical_json()),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("StringCaseValueExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#BooleanMatchExpr { span, r#firstCase, r#moreCases, r#defaultCase } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("firstCase"), r#firstCase.canonical_json()),
                    format!("{}:{}", json_string("moreCases"), format!("[{}]", r#moreCases.iter().map(|value| value.canonical_json()).collect::<Vec<_>>().join(","))),
                    format!("{}:{}", json_string("defaultCase"), r#defaultCase.canonical_json()),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("BooleanMatchExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#BooleanCaseExpr { span, r#condition, r#value } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("condition"), r#condition.canonical_json()),
                    format!("{}:{}", json_string("value"), r#value.canonical_json()),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("BooleanCaseExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#BooleanDefaultCaseExpr { span, r#value } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("value"), r#value.canonical_json()),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("BooleanDefaultCaseExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#BooleanCaseValueExpr { span, r#value } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("value"), r#value.canonical_json()),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("BooleanCaseValueExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#VariableRefExpr { span, r#name, r#type } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("name"), json_string(r#name)),
                    format!("{}:{}", json_string("type"), r#type.as_ref().map_or_else(|| "null".to_owned(), |value| json_string(value))),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("VariableRefExpr"), span.start, span.end, fields.join(","))
            },
            Self::r#ExpressionExpr { span, r#value } => {
                let fields: Vec<String> = vec![
                    format!("{}:{}", json_string("value"), r#value.canonical_json()),
                ];
                format!("{{\"type\":{},\"span\":[{},{}],\"fields\":{{{}}}}}", json_string("ExpressionExpr"), span.start, span.end, fields.join(","))
            },
        }
    }
}
