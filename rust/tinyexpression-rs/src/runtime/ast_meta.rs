//! Node names and child enumeration for the public typed AST (derived from `generated/ast.rs`;
//! regenerate by hand if the grammar adds nodes — `node_name` is exhaustive, so the compiler
//! flags a missing variant).

use crate::generated::ast::{Ast, AstValue};

/// The Java record simple name of a node (`BinaryExpr`, `IfExpr`, ...).
pub(crate) fn node_name(ast: &Ast) -> &'static str {
    match ast {
        Ast::FormulaExpr { .. } => "FormulaExpr",
        Ast::CodeBlockExpr { .. } => "CodeBlockExpr",
        Ast::ImportDeclarationExpr { .. } => "ImportDeclarationExpr",
        Ast::QualifiedNameExpr { .. } => "QualifiedNameExpr",
        Ast::NumberVariableDeclarationExpr { .. } => "NumberVariableDeclarationExpr",
        Ast::StringVariableDeclarationExpr { .. } => "StringVariableDeclarationExpr",
        Ast::BooleanVariableDeclarationExpr { .. } => "BooleanVariableDeclarationExpr",
        Ast::ObjectVariableDeclarationExpr { .. } => "ObjectVariableDeclarationExpr",
        Ast::OnlyIfAbsentExpr { .. } => "OnlyIfAbsentExpr",
        Ast::NumberMethodDeclarationExpr { .. } => "NumberMethodDeclarationExpr",
        Ast::StringMethodDeclarationExpr { .. } => "StringMethodDeclarationExpr",
        Ast::BooleanMethodDeclarationExpr { .. } => "BooleanMethodDeclarationExpr",
        Ast::ObjectMethodDeclarationExpr { .. } => "ObjectMethodDeclarationExpr",
        Ast::MethodParametersExpr { .. } => "MethodParametersExpr",
        Ast::MethodParameterExpr { .. } => "MethodParameterExpr",
        Ast::ExternalBooleanInvocationExpr { .. } => "ExternalBooleanInvocationExpr",
        Ast::ExternalNumberInvocationExpr { .. } => "ExternalNumberInvocationExpr",
        Ast::ExternalStringInvocationExpr { .. } => "ExternalStringInvocationExpr",
        Ast::ExternalObjectInvocationExpr { .. } => "ExternalObjectInvocationExpr",
        Ast::MethodInvocationExpr { .. } => "MethodInvocationExpr",
        Ast::TernaryExpr { .. } => "TernaryExpr",
        Ast::ArgumentExpressionExpr { .. } => "ArgumentExpressionExpr",
        Ast::ArgumentsExpr { .. } => "ArgumentsExpr",
        Ast::BinaryExpr { .. } => "BinaryExpr",
        Ast::SinExpr { .. } => "SinExpr",
        Ast::CosExpr { .. } => "CosExpr",
        Ast::TanExpr { .. } => "TanExpr",
        Ast::SqrtExpr { .. } => "SqrtExpr",
        Ast::MinExpr { .. } => "MinExpr",
        Ast::MaxExpr { .. } => "MaxExpr",
        Ast::RandomExpr { .. } => "RandomExpr",
        Ast::AbsExpr { .. } => "AbsExpr",
        Ast::RoundExpr { .. } => "RoundExpr",
        Ast::CeilExpr { .. } => "CeilExpr",
        Ast::FloorExpr { .. } => "FloorExpr",
        Ast::PowExpr { .. } => "PowExpr",
        Ast::LogExpr { .. } => "LogExpr",
        Ast::ExpExpr { .. } => "ExpExpr",
        Ast::ToNumExpr { .. } => "ToNumExpr",
        Ast::ToUpperCaseExpr { .. } => "ToUpperCaseExpr",
        Ast::ToLowerCaseExpr { .. } => "ToLowerCaseExpr",
        Ast::TrimExpr { .. } => "TrimExpr",
        Ast::LengthExpr { .. } => "LengthExpr",
        Ast::ToUpperCaseDotExpr { .. } => "ToUpperCaseDotExpr",
        Ast::ToLowerCaseDotExpr { .. } => "ToLowerCaseDotExpr",
        Ast::TrimDotExpr { .. } => "TrimDotExpr",
        Ast::LengthDotExpr { .. } => "LengthDotExpr",
        Ast::StartsWithExpr { .. } => "StartsWithExpr",
        Ast::EndsWithExpr { .. } => "EndsWithExpr",
        Ast::ContainsExpr { .. } => "ContainsExpr",
        Ast::InExpr { .. } => "InExpr",
        Ast::StartsWithDotExpr { .. } => "StartsWithDotExpr",
        Ast::EndsWithDotExpr { .. } => "EndsWithDotExpr",
        Ast::ContainsDotExpr { .. } => "ContainsDotExpr",
        Ast::IsPresentExpr { .. } => "IsPresentExpr",
        Ast::InTimeRangeExpr { .. } => "InTimeRangeExpr",
        Ast::InDayTimeRangeExpr { .. } => "InDayTimeRangeExpr",
        Ast::SliceExpr { .. } => "SliceExpr",
        Ast::StringConcatExpr { .. } => "StringConcatExpr",
        Ast::StringCastVariableRefExpr { .. } => "StringCastVariableRefExpr",
        Ast::StringTypedVariableRefExpr { .. } => "StringTypedVariableRefExpr",
        Ast::BooleanOrExpr { .. } => "BooleanOrExpr",
        Ast::BooleanAndExpr { .. } => "BooleanAndExpr",
        Ast::BooleanXorExpr { .. } => "BooleanXorExpr",
        Ast::NotExpr { .. } => "NotExpr",
        Ast::BooleanEqualityExpr { .. } => "BooleanEqualityExpr",
        Ast::BooleanFactorExpr { .. } => "BooleanFactorExpr",
        Ast::StringComparisonExpr { .. } => "StringComparisonExpr",
        Ast::ComparisonExpr { .. } => "ComparisonExpr",
        Ast::ObjectExpr { .. } => "ObjectExpr",
        Ast::IfExpr { .. } => "IfExpr",
        Ast::BranchExpressionExpr { .. } => "BranchExpressionExpr",
        Ast::NumberMatchExpr { .. } => "NumberMatchExpr",
        Ast::NumberCaseExpr { .. } => "NumberCaseExpr",
        Ast::NumberDefaultCaseExpr { .. } => "NumberDefaultCaseExpr",
        Ast::NumberCaseValueExpr { .. } => "NumberCaseValueExpr",
        Ast::StringMatchExpr { .. } => "StringMatchExpr",
        Ast::StringCaseExpr { .. } => "StringCaseExpr",
        Ast::StringDefaultCaseExpr { .. } => "StringDefaultCaseExpr",
        Ast::StringCaseValueExpr { .. } => "StringCaseValueExpr",
        Ast::BooleanMatchExpr { .. } => "BooleanMatchExpr",
        Ast::BooleanCaseExpr { .. } => "BooleanCaseExpr",
        Ast::BooleanDefaultCaseExpr { .. } => "BooleanDefaultCaseExpr",
        Ast::BooleanCaseValueExpr { .. } => "BooleanCaseValueExpr",
        Ast::VariableRefExpr { .. } => "VariableRefExpr",
        Ast::ExpressionExpr { .. } => "ExpressionExpr",
    }
}

/// Child nodes in field order (text captures are not nodes).
pub(crate) fn children(ast: &Ast) -> Vec<&Ast> {
    let mut out: Vec<&Ast> = Vec::new();
    match ast {
        Ast::FormulaExpr {
            r#imports,
            r#declarations,
            r#expression,
            r#methods,
            ..
        } => {
            out.extend(r#imports.iter());
            out.extend(r#declarations.iter());
            out.push(r#expression);
            out.extend(r#methods.iter());
        }
        Ast::CodeBlockExpr { .. } => {}
        Ast::ImportDeclarationExpr { r#className, .. } => {
            out.push(r#className);
        }
        Ast::QualifiedNameExpr { .. } => {}
        Ast::NumberVariableDeclarationExpr {
            r#onlyIfAbsent,
            r#value,
            ..
        } => {
            if let Some(node) = r#onlyIfAbsent {
                out.push(node);
            }
            if let Some(node) = r#value {
                out.push(node);
            }
        }
        Ast::StringVariableDeclarationExpr {
            r#onlyIfAbsent,
            r#value,
            ..
        } => {
            if let Some(node) = r#onlyIfAbsent {
                out.push(node);
            }
            if let Some(node) = r#value {
                out.push(node);
            }
        }
        Ast::BooleanVariableDeclarationExpr {
            r#onlyIfAbsent,
            r#value,
            ..
        } => {
            if let Some(node) = r#onlyIfAbsent {
                out.push(node);
            }
            if let Some(node) = r#value {
                out.push(node);
            }
        }
        Ast::ObjectVariableDeclarationExpr {
            r#onlyIfAbsent,
            r#value,
            ..
        } => {
            if let Some(node) = r#onlyIfAbsent {
                out.push(node);
            }
            if let Some(node) = r#value {
                out.push(node);
            }
        }
        Ast::OnlyIfAbsentExpr { .. } => {}
        Ast::NumberMethodDeclarationExpr {
            r#parameters,
            r#expression,
            ..
        } => {
            if let Some(node) = r#parameters {
                out.push(node);
            }
            out.push(r#expression);
        }
        Ast::StringMethodDeclarationExpr {
            r#parameters,
            r#expression,
            ..
        } => {
            if let Some(node) = r#parameters {
                out.push(node);
            }
            out.push(r#expression);
        }
        Ast::BooleanMethodDeclarationExpr {
            r#parameters,
            r#expression,
            ..
        } => {
            if let Some(node) = r#parameters {
                out.push(node);
            }
            out.push(r#expression);
        }
        Ast::ObjectMethodDeclarationExpr {
            r#parameters,
            r#expression,
            ..
        } => {
            if let Some(node) = r#parameters {
                out.push(node);
            }
            out.push(r#expression);
        }
        Ast::MethodParametersExpr { r#values, .. } => {
            out.extend(r#values.iter());
        }
        Ast::MethodParameterExpr { .. } => {}
        Ast::ExternalBooleanInvocationExpr {
            r#className,
            r#args,
            ..
        } => {
            if let Some(node) = r#className {
                out.push(node);
            }
            if let Some(node) = r#args {
                out.push(node);
            }
        }
        Ast::ExternalNumberInvocationExpr {
            r#className,
            r#args,
            ..
        } => {
            if let Some(node) = r#className {
                out.push(node);
            }
            if let Some(node) = r#args {
                out.push(node);
            }
        }
        Ast::ExternalStringInvocationExpr {
            r#className,
            r#args,
            ..
        } => {
            if let Some(node) = r#className {
                out.push(node);
            }
            if let Some(node) = r#args {
                out.push(node);
            }
        }
        Ast::ExternalObjectInvocationExpr {
            r#className,
            r#args,
            ..
        } => {
            if let Some(node) = r#className {
                out.push(node);
            }
            if let Some(node) = r#args {
                out.push(node);
            }
        }
        Ast::MethodInvocationExpr { r#args, .. } => {
            if let Some(node) = r#args {
                out.push(node);
            }
        }
        Ast::TernaryExpr {
            r#condition,
            r#thenExpr,
            r#elseExpr,
            ..
        } => {
            out.push(r#condition);
            out.push(r#thenExpr);
            out.push(r#elseExpr);
        }
        Ast::ArgumentExpressionExpr { r#value, .. } => {
            out.push(r#value);
        }
        Ast::ArgumentsExpr { r#values, .. } => {
            out.extend(r#values.iter());
        }
        Ast::BinaryExpr {
            r#left, r#right, ..
        } => {
            if let AstValue::Node(node) = r#left {
                out.push(node);
            }
            for value in r#right {
                if let AstValue::Node(node) = value {
                    out.push(node);
                }
            }
        }
        Ast::SinExpr { r#arg, .. } => {
            out.push(r#arg);
        }
        Ast::CosExpr { r#arg, .. } => {
            out.push(r#arg);
        }
        Ast::TanExpr { r#arg, .. } => {
            out.push(r#arg);
        }
        Ast::SqrtExpr { r#arg, .. } => {
            out.push(r#arg);
        }
        Ast::MinExpr {
            r#first, r#rest, ..
        } => {
            out.push(r#first);
            out.extend(r#rest.iter());
        }
        Ast::MaxExpr {
            r#first, r#rest, ..
        } => {
            out.push(r#first);
            out.extend(r#rest.iter());
        }
        Ast::RandomExpr { .. } => {}
        Ast::AbsExpr { r#arg, .. } => {
            out.push(r#arg);
        }
        Ast::RoundExpr { r#arg, .. } => {
            out.push(r#arg);
        }
        Ast::CeilExpr { r#arg, .. } => {
            out.push(r#arg);
        }
        Ast::FloorExpr { r#arg, .. } => {
            out.push(r#arg);
        }
        Ast::PowExpr {
            r#base, r#exponent, ..
        } => {
            out.push(r#base);
            out.push(r#exponent);
        }
        Ast::LogExpr { r#arg, .. } => {
            out.push(r#arg);
        }
        Ast::ExpExpr { r#arg, .. } => {
            out.push(r#arg);
        }
        Ast::ToNumExpr {
            r#value,
            r#defaultValue,
            ..
        } => {
            out.push(r#value);
            out.push(r#defaultValue);
        }
        Ast::ToUpperCaseExpr { r#value, .. } => {
            out.push(r#value);
        }
        Ast::ToLowerCaseExpr { r#value, .. } => {
            out.push(r#value);
        }
        Ast::TrimExpr { r#value, .. } => {
            out.push(r#value);
        }
        Ast::LengthExpr { r#value, .. } => {
            out.push(r#value);
        }
        Ast::ToUpperCaseDotExpr { r#value, .. } => {
            out.push(r#value);
        }
        Ast::ToLowerCaseDotExpr { r#value, .. } => {
            out.push(r#value);
        }
        Ast::TrimDotExpr { r#value, .. } => {
            out.push(r#value);
        }
        Ast::LengthDotExpr { r#value, .. } => {
            out.push(r#value);
        }
        Ast::StartsWithExpr {
            r#value,
            r#patterns,
            ..
        } => {
            out.push(r#value);
            out.extend(r#patterns.iter());
        }
        Ast::EndsWithExpr {
            r#value,
            r#patterns,
            ..
        } => {
            out.push(r#value);
            out.extend(r#patterns.iter());
        }
        Ast::ContainsExpr {
            r#value,
            r#patterns,
            ..
        } => {
            out.push(r#value);
            out.extend(r#patterns.iter());
        }
        Ast::InExpr {
            r#value,
            r#candidates,
            ..
        } => {
            out.push(r#value);
            out.extend(r#candidates.iter());
        }
        Ast::StartsWithDotExpr {
            r#value,
            r#patterns,
            ..
        } => {
            out.push(r#value);
            out.extend(r#patterns.iter());
        }
        Ast::EndsWithDotExpr {
            r#value,
            r#patterns,
            ..
        } => {
            out.push(r#value);
            out.extend(r#patterns.iter());
        }
        Ast::ContainsDotExpr {
            r#value,
            r#patterns,
            ..
        } => {
            out.push(r#value);
            out.extend(r#patterns.iter());
        }
        Ast::IsPresentExpr { r#value, .. } => {
            out.push(r#value);
        }
        Ast::InTimeRangeExpr {
            r#startHour,
            r#endHour,
            ..
        } => {
            out.push(r#startHour);
            out.push(r#endHour);
        }
        Ast::InDayTimeRangeExpr {
            r#startHour,
            r#endHour,
            ..
        } => {
            out.push(r#startHour);
            out.push(r#endHour);
        }
        Ast::SliceExpr {
            r#value,
            r#start,
            r#end,
            r#step,
            ..
        } => {
            if let AstValue::Node(node) = r#value {
                out.push(node);
            }
            if let Some(node) = r#start {
                out.push(node);
            }
            if let Some(node) = r#end {
                out.push(node);
            }
            if let Some(node) = r#step {
                out.push(node);
            }
        }
        Ast::StringConcatExpr {
            r#left, r#right, ..
        } => {
            if let AstValue::Node(node) = r#left {
                out.push(node);
            }
            for value in r#right {
                if let AstValue::Node(node) = value {
                    out.push(node);
                }
            }
        }
        Ast::StringCastVariableRefExpr { .. } => {}
        Ast::StringTypedVariableRefExpr { .. } => {}
        Ast::BooleanOrExpr {
            r#left, r#right, ..
        } => {
            out.push(r#left);
            out.extend(r#right.iter());
        }
        Ast::BooleanAndExpr {
            r#left, r#right, ..
        } => {
            out.push(r#left);
            out.extend(r#right.iter());
        }
        Ast::BooleanXorExpr {
            r#left, r#right, ..
        } => {
            out.push(r#left);
            out.extend(r#right.iter());
        }
        Ast::NotExpr { r#value, .. } => {
            out.push(r#value);
        }
        Ast::BooleanEqualityExpr {
            r#left, r#right, ..
        } => {
            if let AstValue::Node(node) = r#left {
                out.push(node);
            }
            if let AstValue::Node(node) = r#right {
                out.push(node);
            }
        }
        Ast::BooleanFactorExpr { r#value, .. } => {
            if let AstValue::Node(node) = r#value {
                out.push(node);
            }
        }
        Ast::StringComparisonExpr {
            r#left, r#right, ..
        } => {
            out.push(r#left);
            out.push(r#right);
        }
        Ast::ComparisonExpr {
            r#left, r#right, ..
        } => {
            out.push(r#left);
            out.push(r#right);
        }
        Ast::ObjectExpr { r#value, .. } => {
            out.push(r#value);
        }
        Ast::IfExpr {
            r#condition,
            r#thenExpr,
            r#elseExpr,
            ..
        } => {
            out.push(r#condition);
            out.push(r#thenExpr);
            out.push(r#elseExpr);
        }
        Ast::BranchExpressionExpr { r#value, .. } => {
            out.push(r#value);
        }
        Ast::NumberMatchExpr {
            r#firstCase,
            r#moreCases,
            r#defaultCase,
            ..
        } => {
            out.push(r#firstCase);
            out.extend(r#moreCases.iter());
            out.push(r#defaultCase);
        }
        Ast::NumberCaseExpr {
            r#condition,
            r#value,
            ..
        } => {
            out.push(r#condition);
            out.push(r#value);
        }
        Ast::NumberDefaultCaseExpr { r#value, .. } => {
            out.push(r#value);
        }
        Ast::NumberCaseValueExpr { r#value, .. } => {
            out.push(r#value);
        }
        Ast::StringMatchExpr {
            r#firstCase,
            r#moreCases,
            r#defaultCase,
            ..
        } => {
            out.push(r#firstCase);
            out.extend(r#moreCases.iter());
            out.push(r#defaultCase);
        }
        Ast::StringCaseExpr {
            r#condition,
            r#value,
            ..
        } => {
            out.push(r#condition);
            out.push(r#value);
        }
        Ast::StringDefaultCaseExpr { r#value, .. } => {
            out.push(r#value);
        }
        Ast::StringCaseValueExpr { r#value, .. } => {
            out.push(r#value);
        }
        Ast::BooleanMatchExpr {
            r#firstCase,
            r#moreCases,
            r#defaultCase,
            ..
        } => {
            out.push(r#firstCase);
            out.extend(r#moreCases.iter());
            out.push(r#defaultCase);
        }
        Ast::BooleanCaseExpr {
            r#condition,
            r#value,
            ..
        } => {
            out.push(r#condition);
            out.push(r#value);
        }
        Ast::BooleanDefaultCaseExpr { r#value, .. } => {
            out.push(r#value);
        }
        Ast::BooleanCaseValueExpr { r#value, .. } => {
            out.push(r#value);
        }
        Ast::VariableRefExpr { .. } => {}
        Ast::ExpressionExpr { r#value, .. } => {
            out.push(r#value);
        }
    }
    out
}

/// Child nodes the Java record reflection of `P4StrictMatchTypingValidator` visits: every
/// field except the ones Java declares as `Optional` (the validator does not look inside those).
pub(crate) fn record_children(ast: &Ast) -> Vec<&Ast> {
    let mut out: Vec<&Ast> = Vec::new();
    match ast {
        Ast::FormulaExpr {
            r#imports,
            r#declarations,
            r#expression,
            r#methods,
            ..
        } => {
            out.extend(r#imports.iter());
            out.extend(r#declarations.iter());
            out.push(r#expression);
            out.extend(r#methods.iter());
        }
        Ast::CodeBlockExpr { .. } => {}
        Ast::ImportDeclarationExpr { r#className, .. } => {
            out.push(r#className);
        }
        Ast::QualifiedNameExpr { .. } => {}
        Ast::NumberVariableDeclarationExpr { .. } => {}
        Ast::StringVariableDeclarationExpr { .. } => {}
        Ast::BooleanVariableDeclarationExpr { .. } => {}
        Ast::ObjectVariableDeclarationExpr { .. } => {}
        Ast::OnlyIfAbsentExpr { .. } => {}
        Ast::NumberMethodDeclarationExpr { r#expression, .. } => {
            out.push(r#expression);
        }
        Ast::StringMethodDeclarationExpr { r#expression, .. } => {
            out.push(r#expression);
        }
        Ast::BooleanMethodDeclarationExpr { r#expression, .. } => {
            out.push(r#expression);
        }
        Ast::ObjectMethodDeclarationExpr { r#expression, .. } => {
            out.push(r#expression);
        }
        Ast::MethodParametersExpr { r#values, .. } => {
            out.extend(r#values.iter());
        }
        Ast::MethodParameterExpr { .. } => {}
        Ast::ExternalBooleanInvocationExpr { .. } => {}
        Ast::ExternalNumberInvocationExpr { .. } => {}
        Ast::ExternalStringInvocationExpr { .. } => {}
        Ast::ExternalObjectInvocationExpr { .. } => {}
        Ast::MethodInvocationExpr { .. } => {}
        Ast::TernaryExpr {
            r#condition,
            r#thenExpr,
            r#elseExpr,
            ..
        } => {
            out.push(r#condition);
            out.push(r#thenExpr);
            out.push(r#elseExpr);
        }
        Ast::ArgumentExpressionExpr { r#value, .. } => {
            out.push(r#value);
        }
        Ast::ArgumentsExpr { r#values, .. } => {
            out.extend(r#values.iter());
        }
        Ast::BinaryExpr {
            r#left, r#right, ..
        } => {
            if let AstValue::Node(node) = r#left {
                out.push(node);
            }
            for value in r#right {
                if let AstValue::Node(node) = value {
                    out.push(node);
                }
            }
        }
        Ast::SinExpr { r#arg, .. } => {
            out.push(r#arg);
        }
        Ast::CosExpr { r#arg, .. } => {
            out.push(r#arg);
        }
        Ast::TanExpr { r#arg, .. } => {
            out.push(r#arg);
        }
        Ast::SqrtExpr { r#arg, .. } => {
            out.push(r#arg);
        }
        Ast::MinExpr {
            r#first, r#rest, ..
        } => {
            out.push(r#first);
            out.extend(r#rest.iter());
        }
        Ast::MaxExpr {
            r#first, r#rest, ..
        } => {
            out.push(r#first);
            out.extend(r#rest.iter());
        }
        Ast::RandomExpr { .. } => {}
        Ast::AbsExpr { r#arg, .. } => {
            out.push(r#arg);
        }
        Ast::RoundExpr { r#arg, .. } => {
            out.push(r#arg);
        }
        Ast::CeilExpr { r#arg, .. } => {
            out.push(r#arg);
        }
        Ast::FloorExpr { r#arg, .. } => {
            out.push(r#arg);
        }
        Ast::PowExpr {
            r#base, r#exponent, ..
        } => {
            out.push(r#base);
            out.push(r#exponent);
        }
        Ast::LogExpr { r#arg, .. } => {
            out.push(r#arg);
        }
        Ast::ExpExpr { r#arg, .. } => {
            out.push(r#arg);
        }
        Ast::ToNumExpr {
            r#value,
            r#defaultValue,
            ..
        } => {
            out.push(r#value);
            out.push(r#defaultValue);
        }
        Ast::ToUpperCaseExpr { r#value, .. } => {
            out.push(r#value);
        }
        Ast::ToLowerCaseExpr { r#value, .. } => {
            out.push(r#value);
        }
        Ast::TrimExpr { r#value, .. } => {
            out.push(r#value);
        }
        Ast::LengthExpr { r#value, .. } => {
            out.push(r#value);
        }
        Ast::ToUpperCaseDotExpr { r#value, .. } => {
            out.push(r#value);
        }
        Ast::ToLowerCaseDotExpr { r#value, .. } => {
            out.push(r#value);
        }
        Ast::TrimDotExpr { r#value, .. } => {
            out.push(r#value);
        }
        Ast::LengthDotExpr { r#value, .. } => {
            out.push(r#value);
        }
        Ast::StartsWithExpr {
            r#value,
            r#patterns,
            ..
        } => {
            out.push(r#value);
            out.extend(r#patterns.iter());
        }
        Ast::EndsWithExpr {
            r#value,
            r#patterns,
            ..
        } => {
            out.push(r#value);
            out.extend(r#patterns.iter());
        }
        Ast::ContainsExpr {
            r#value,
            r#patterns,
            ..
        } => {
            out.push(r#value);
            out.extend(r#patterns.iter());
        }
        Ast::InExpr {
            r#value,
            r#candidates,
            ..
        } => {
            out.push(r#value);
            out.extend(r#candidates.iter());
        }
        Ast::StartsWithDotExpr {
            r#value,
            r#patterns,
            ..
        } => {
            out.push(r#value);
            out.extend(r#patterns.iter());
        }
        Ast::EndsWithDotExpr {
            r#value,
            r#patterns,
            ..
        } => {
            out.push(r#value);
            out.extend(r#patterns.iter());
        }
        Ast::ContainsDotExpr {
            r#value,
            r#patterns,
            ..
        } => {
            out.push(r#value);
            out.extend(r#patterns.iter());
        }
        Ast::IsPresentExpr { r#value, .. } => {
            out.push(r#value);
        }
        Ast::InTimeRangeExpr {
            r#startHour,
            r#endHour,
            ..
        } => {
            out.push(r#startHour);
            out.push(r#endHour);
        }
        Ast::InDayTimeRangeExpr {
            r#startHour,
            r#endHour,
            ..
        } => {
            out.push(r#startHour);
            out.push(r#endHour);
        }
        Ast::SliceExpr { r#value, .. } => {
            if let AstValue::Node(node) = r#value {
                out.push(node);
            }
        }
        Ast::StringConcatExpr {
            r#left, r#right, ..
        } => {
            if let AstValue::Node(node) = r#left {
                out.push(node);
            }
            for value in r#right {
                if let AstValue::Node(node) = value {
                    out.push(node);
                }
            }
        }
        Ast::StringCastVariableRefExpr { .. } => {}
        Ast::StringTypedVariableRefExpr { .. } => {}
        Ast::BooleanOrExpr {
            r#left, r#right, ..
        } => {
            out.push(r#left);
            out.extend(r#right.iter());
        }
        Ast::BooleanAndExpr {
            r#left, r#right, ..
        } => {
            out.push(r#left);
            out.extend(r#right.iter());
        }
        Ast::BooleanXorExpr {
            r#left, r#right, ..
        } => {
            out.push(r#left);
            out.extend(r#right.iter());
        }
        Ast::NotExpr { r#value, .. } => {
            out.push(r#value);
        }
        Ast::BooleanEqualityExpr {
            r#left, r#right, ..
        } => {
            if let AstValue::Node(node) = r#left {
                out.push(node);
            }
            if let AstValue::Node(node) = r#right {
                out.push(node);
            }
        }
        Ast::BooleanFactorExpr { r#value, .. } => {
            if let AstValue::Node(node) = r#value {
                out.push(node);
            }
        }
        Ast::StringComparisonExpr {
            r#left, r#right, ..
        } => {
            out.push(r#left);
            out.push(r#right);
        }
        Ast::ComparisonExpr {
            r#left, r#right, ..
        } => {
            out.push(r#left);
            out.push(r#right);
        }
        Ast::ObjectExpr { r#value, .. } => {
            out.push(r#value);
        }
        Ast::IfExpr {
            r#condition,
            r#thenExpr,
            r#elseExpr,
            ..
        } => {
            out.push(r#condition);
            out.push(r#thenExpr);
            out.push(r#elseExpr);
        }
        Ast::BranchExpressionExpr { r#value, .. } => {
            out.push(r#value);
        }
        Ast::NumberMatchExpr {
            r#firstCase,
            r#moreCases,
            r#defaultCase,
            ..
        } => {
            out.push(r#firstCase);
            out.extend(r#moreCases.iter());
            out.push(r#defaultCase);
        }
        Ast::NumberCaseExpr {
            r#condition,
            r#value,
            ..
        } => {
            out.push(r#condition);
            out.push(r#value);
        }
        Ast::NumberDefaultCaseExpr { r#value, .. } => {
            out.push(r#value);
        }
        Ast::NumberCaseValueExpr { r#value, .. } => {
            out.push(r#value);
        }
        Ast::StringMatchExpr {
            r#firstCase,
            r#moreCases,
            r#defaultCase,
            ..
        } => {
            out.push(r#firstCase);
            out.extend(r#moreCases.iter());
            out.push(r#defaultCase);
        }
        Ast::StringCaseExpr {
            r#condition,
            r#value,
            ..
        } => {
            out.push(r#condition);
            out.push(r#value);
        }
        Ast::StringDefaultCaseExpr { r#value, .. } => {
            out.push(r#value);
        }
        Ast::StringCaseValueExpr { r#value, .. } => {
            out.push(r#value);
        }
        Ast::BooleanMatchExpr {
            r#firstCase,
            r#moreCases,
            r#defaultCase,
            ..
        } => {
            out.push(r#firstCase);
            out.extend(r#moreCases.iter());
            out.push(r#defaultCase);
        }
        Ast::BooleanCaseExpr {
            r#condition,
            r#value,
            ..
        } => {
            out.push(r#condition);
            out.push(r#value);
        }
        Ast::BooleanDefaultCaseExpr { r#value, .. } => {
            out.push(r#value);
        }
        Ast::BooleanCaseValueExpr { r#value, .. } => {
            out.push(r#value);
        }
        Ast::VariableRefExpr { .. } => {}
        Ast::ExpressionExpr { r#value, .. } => {
            out.push(r#value);
        }
    }
    out
}
