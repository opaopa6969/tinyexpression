//! Tree-walking evaluator: `P4TypedAstEvaluator` method by method.

#![allow(non_snake_case)]

use std::collections::HashMap;

use super::java;
use super::ops::{self, err, Cmp, Declared, Equality, Math1, Op, Pred, Scope, StrFn, R};
use super::trace::{TraceHook, TraceSite};
use super::{Context, ErrorKind, EvalError, ExternalCall, Host, Program, ResultType};
use crate::generated::ast::{Ast, AstValue};
use crate::Value;

pub(crate) struct Walker<'a, 'c, 'h, 'hh> {
    program: &'a Program,
    scope: Scope<'c>,
    host: &'h mut Host<'hh>,
    imports: HashMap<String, (String, Option<String>)>,
    methods: HashMap<String, &'a Ast>,
    declared: HashMap<String, Declared>,
    depth: usize,
    /// Observer of every step (issue #201 stage 3); `None` on the untraced path.
    trace: Option<&'h mut dyn TraceHook>,
}

enum Unwrapped<'a> {
    Node(&'a Ast),
    Text(&'a str),
}

/// `unwrapTransparentBinary`: descends `BinaryExpr{left, op=[], right=[]}` wrappers.
fn unwrap_transparent(node: &Ast) -> Unwrapped<'_> {
    let mut current = node;
    loop {
        match current {
            Ast::BinaryExpr {
                left, op, right, ..
            } if op.is_empty() && right.is_empty() => match left {
                AstValue::Node(inner) => current = inner,
                AstValue::Text { text, .. } => return Unwrapped::Text(text),
            },
            _ => return Unwrapped::Node(current),
        }
    }
}

/// `extractExactVariableReference` on a `BinaryExpr` chain of wrappers.
pub(crate) fn exact_variable_of_binary(node: &Ast) -> Option<String> {
    match node {
        Ast::BinaryExpr {
            left, op, right, ..
        } if op.is_empty() && right.is_empty() => match left {
            AstValue::Text { text, .. } => {
                ops::exact_variable(java::strip(text)).map(str::to_owned)
            }
            AstValue::Node(inner) => match inner.as_ref() {
                Ast::BinaryExpr { .. } => exact_variable_of_binary(inner),
                Ast::VariableRefExpr { name, .. } => ops::variable_ref_name(name),
                _ => None,
            },
        },
        _ => None,
    }
}

impl<'a, 'c, 'h, 'hh> Walker<'a, 'c, 'h, 'hh> {
    pub(crate) fn new(
        program: &'a Program,
        context: &'c mut Context,
        host: &'h mut Host<'hh>,
    ) -> Self {
        Self {
            program,
            scope: Scope {
                base: context,
                frames: Vec::new(),
            },
            host,
            imports: HashMap::new(),
            methods: HashMap::new(),
            declared: HashMap::new(),
            depth: 0,
            trace: None,
        }
    }

    pub(crate) fn with_trace(mut self, hook: &'h mut dyn TraceHook) -> Self {
        self.trace = Some(hook);
        self
    }

    pub(crate) fn run(mut self, root: &'a Ast) -> R {
        self.eval(root)
    }

    fn number_type(&self) -> super::NumberType {
        self.program.options.number_type
    }

    fn result_type(&self) -> ResultType {
        self.program.options.result_type
    }

    fn string_of(&mut self, node: &'a Ast) -> Result<String, EvalError> {
        Ok(super::java_string(&self.eval(node)?))
    }

    /// Evaluates a node; with a trace hook, reports the step around it.
    fn eval(&mut self, node: &'a Ast) -> R {
        if self.trace.is_none() {
            return self.eval_node(node);
        }
        self.traced(TraceSite::Node(node), |walker| walker.eval_node(node))
    }

    /// Runs `step` between the hook's `on_enter(site)` and `on_exit(result)`.
    fn traced(&mut self, site: TraceSite<'_>, step: impl FnOnce(&mut Self) -> R) -> R {
        match self.trace.as_deref_mut() {
            None => step(self),
            Some(hook) => {
                hook.on_enter(site);
                let result = step(self);
                if let Some(hook) = self.trace.as_deref_mut() {
                    hook.on_exit(result.as_ref());
                }
                result
            }
        }
    }

    fn eval_node(&mut self, node: &'a Ast) -> R {
        match node {
            Ast::FormulaExpr {
                imports,
                declarations,
                expression,
                methods,
                ..
            } => {
                self.scope.frames.push(HashMap::new());
                let result = self.formula(imports, declarations, methods, expression);
                self.scope.frames.pop();
                result
            }
            Ast::CodeBlockExpr { .. } => Ok(Value::Null),
            Ast::ImportDeclarationExpr {
                className,
                method,
                alias,
                ..
            } => {
                let class_name = match className.as_ref() {
                    Ast::QualifiedNameExpr { head, tail, .. } => ops::qualified_name(head, tail),
                    _ => String::new(),
                };
                let alias = ops::import_alias(&class_name, method.as_deref(), alias);
                self.imports.insert(alias, (class_name, method.clone()));
                Ok(Value::Null)
            }
            Ast::QualifiedNameExpr { head, tail, .. } => {
                Ok(Value::String(ops::qualified_name(head, tail)))
            }
            Ast::NumberVariableDeclarationExpr {
                varName,
                onlyIfAbsent,
                value,
                ..
            } => {
                self.declared.insert(varName.clone(), Declared::Number);
                if let Some(value) = self.declaration_value(varName, onlyIfAbsent, value)? {
                    if value != Value::Null && !ops::is_number(&value) {
                        return Err(err(
                            ErrorKind::ClassCast,
                            format!(
                                "{} cannot be cast to java.lang.Number",
                                ops::java_class(&value)
                            ),
                        ));
                    }
                    self.scope.set(varName, value);
                }
                Ok(Value::Null)
            }
            Ast::StringVariableDeclarationExpr {
                varName,
                onlyIfAbsent,
                value,
                ..
            } => {
                self.declared.insert(varName.clone(), Declared::String);
                if let Some(value) = self.declaration_value(varName, onlyIfAbsent, value)? {
                    self.scope
                        .set(varName, Value::String(super::java_string(&value)));
                }
                Ok(Value::Null)
            }
            Ast::BooleanVariableDeclarationExpr {
                varName,
                onlyIfAbsent,
                value,
                ..
            } => {
                self.declared.insert(varName.clone(), Declared::Boolean);
                if let Some(value) = self.declaration_value(varName, onlyIfAbsent, value)? {
                    self.scope
                        .set(varName, Value::Boolean(value == Value::Boolean(true)));
                }
                Ok(Value::Null)
            }
            Ast::ObjectVariableDeclarationExpr {
                varName,
                onlyIfAbsent,
                value,
                ..
            } => {
                self.declared.insert(varName.clone(), Declared::Object);
                if let Some(value) = self.declaration_value(varName, onlyIfAbsent, value)? {
                    self.scope.set(varName, value);
                }
                Ok(Value::Null)
            }
            Ast::OnlyIfAbsentExpr { .. } => Ok(Value::Boolean(true)),
            Ast::NumberMethodDeclarationExpr { methodName, .. }
            | Ast::StringMethodDeclarationExpr { methodName, .. }
            | Ast::BooleanMethodDeclarationExpr { methodName, .. }
            | Ast::ObjectMethodDeclarationExpr { methodName, .. } => {
                self.methods.insert(methodName.clone(), node);
                Ok(Value::Null)
            }
            Ast::MethodParametersExpr { .. }
            | Ast::MethodParameterExpr { .. }
            | Ast::ArgumentsExpr { .. } => Ok(Value::Null),
            Ast::ExternalBooleanInvocationExpr {
                className,
                name,
                args,
                ..
            } => self.external(className, name, args, Declared::Boolean),
            Ast::ExternalNumberInvocationExpr {
                className,
                name,
                args,
                ..
            } => self.external(className, name, args, Declared::Number),
            Ast::ExternalStringInvocationExpr {
                className,
                name,
                args,
                ..
            } => self.external(className, name, args, Declared::String),
            Ast::ExternalObjectInvocationExpr {
                className,
                name,
                args,
                ..
            } => self.external(className, name, args, Declared::Object),
            Ast::MethodInvocationExpr { name, args, .. } => self.invoke(name, args),
            Ast::TernaryExpr {
                condition,
                thenExpr,
                elseExpr,
                ..
            }
            | Ast::IfExpr {
                condition,
                thenExpr,
                elseExpr,
                ..
            } => {
                let condition = self.eval(condition)?;
                self.eval(if ops::to_boolean(&condition) {
                    thenExpr
                } else {
                    elseExpr
                })
            }
            Ast::ArgumentExpressionExpr { value, .. }
            | Ast::ExpressionExpr { value, .. }
            | Ast::ObjectExpr { value, .. } => {
                if let Some(name) = exact_variable_of_binary(value) {
                    let resolved = self.scope.any(&name);
                    if resolved != Value::Null {
                        return Ok(resolved);
                    }
                }
                self.eval(value)
            }
            Ast::BranchExpressionExpr { value, .. } => self.eval(value),
            Ast::BinaryExpr { .. } => self.binary_expr(node),
            Ast::SinExpr { arg, .. } => self.math1(Math1::Sin, arg),
            Ast::CosExpr { arg, .. } => self.math1(Math1::Cos, arg),
            Ast::TanExpr { arg, .. } => self.math1(Math1::Tan, arg),
            Ast::SqrtExpr { arg, .. } => self.math1(Math1::Sqrt, arg),
            Ast::AbsExpr { arg, .. } => self.math1(Math1::Abs, arg),
            Ast::RoundExpr { arg, .. } => self.math1(Math1::Round, arg),
            Ast::CeilExpr { arg, .. } => self.math1(Math1::Ceil, arg),
            Ast::FloorExpr { arg, .. } => self.math1(Math1::Floor, arg),
            Ast::LogExpr { arg, .. } => self.math1(Math1::Log, arg),
            Ast::ExpExpr { arg, .. } => self.math1(Math1::Exp, arg),
            Ast::MinExpr { first, rest, .. } | Ast::MaxExpr { first, rest, .. } => {
                let is_min = matches!(node, Ast::MinExpr { .. });
                let mut acc = ops::number_arg(&self.eval(first)?)?;
                for next in rest {
                    let value = ops::number_arg(&self.eval(next)?)?;
                    acc = if is_min {
                        java::math_min(acc, value)
                    } else {
                        java::math_max(acc, value)
                    };
                }
                Ok(ops::cast_double(self.number_type(), acc))
            }
            Ast::RandomExpr { .. } => {
                let value = self.host.random.next_double();
                Ok(ops::cast_double(self.number_type(), value))
            }
            Ast::PowExpr { base, exponent, .. } => {
                let base = ops::number_arg(&self.eval(base)?)?;
                let exponent = ops::number_arg(&self.eval(exponent)?)?;
                Ok(ops::cast_double(
                    self.number_type(),
                    java::math_pow(base, exponent),
                ))
            }
            Ast::ToNumExpr {
                value,
                defaultValue,
                ..
            } => {
                let text = self.string_of(value)?;
                match java::parse_double(&text) {
                    Some(parsed) => Ok(ops::cast_double(self.number_type(), parsed)),
                    None => {
                        let fallback = self.eval(defaultValue)?;
                        let fallback = if ops::is_number(&fallback) {
                            ops::double_value(&fallback)
                        } else {
                            0.0
                        };
                        Ok(ops::cast_double(self.number_type(), fallback))
                    }
                }
            }
            Ast::ToUpperCaseExpr { value, .. } | Ast::ToUpperCaseDotExpr { value, .. } => {
                self.str_fn(StrFn::Upper, value)
            }
            Ast::ToLowerCaseExpr { value, .. } | Ast::ToLowerCaseDotExpr { value, .. } => {
                self.str_fn(StrFn::Lower, value)
            }
            Ast::TrimExpr { value, .. } | Ast::TrimDotExpr { value, .. } => {
                self.str_fn(StrFn::Trim, value)
            }
            Ast::LengthExpr { value, .. } | Ast::LengthDotExpr { value, .. } => {
                self.str_fn(StrFn::Length, value)
            }
            Ast::StartsWithExpr {
                value, patterns, ..
            }
            | Ast::StartsWithDotExpr {
                value, patterns, ..
            } => self.pred(Pred::StartsWith, value, patterns),
            Ast::EndsWithExpr {
                value, patterns, ..
            }
            | Ast::EndsWithDotExpr {
                value, patterns, ..
            } => self.pred(Pred::EndsWith, value, patterns),
            Ast::ContainsExpr {
                value, patterns, ..
            }
            | Ast::ContainsDotExpr {
                value, patterns, ..
            } => self.pred(Pred::Contains, value, patterns),
            Ast::InExpr {
                value, candidates, ..
            } => self.pred(Pred::In, value, candidates),
            Ast::IsPresentExpr { value, .. } => {
                let name = match value.as_ref() {
                    Ast::VariableRefExpr { name, .. } => ops::variable_ref_name(name),
                    _ => None,
                };
                Ok(Value::Boolean(
                    name.is_some_and(|name| self.scope.exists(&name)),
                ))
            }
            Ast::InTimeRangeExpr {
                startHour, endHour, ..
            } => {
                let from = ops::float_value(&self.number_of(startHour)?);
                let to = ops::float_value(&self.number_of(endHour)?);
                let now = self.host.clock.now_hour(&self.scope);
                Ok(Value::Boolean(ops::in_time_range(now, from, to)))
            }
            Ast::InDayTimeRangeExpr {
                startDay,
                startHour,
                endDay,
                endHour,
                ..
            } => {
                let from_hour = ops::float_value(&self.number_of(startHour)?);
                let to_hour = ops::float_value(&self.number_of(endHour)?);
                let from_day = ops::day_of_week(startDay)?;
                let to_day = ops::day_of_week(endDay)?;
                Ok(Value::Boolean(ops::in_day_time_range(
                    self.host.clock,
                    self.scope.base,
                    from_day,
                    from_hour,
                    to_day,
                    to_hour,
                )))
            }
            Ast::SliceExpr {
                value,
                start,
                end,
                step,
                ..
            } => {
                let text = self.string_leaf(value)?;
                let index = |node: &Option<Box<Ast>>| {
                    ops::slice_index(node.as_ref().map(|n| self.program.source_text(n.span())))
                };
                let (start, end, step) = (index(start)?, index(end)?, index(step)?);
                ops::slice(&text, start, end, step)
            }
            Ast::StringConcatExpr {
                left, op, right, ..
            } => {
                let mut text = self.string_leaf(left)?;
                if op.is_empty() {
                    return Ok(Value::String(text));
                }
                for operand in right.iter().take(op.len()) {
                    text.push_str(&self.string_leaf(operand)?);
                }
                Ok(Value::String(text))
            }
            Ast::StringCastVariableRefExpr { name, .. }
            | Ast::StringTypedVariableRefExpr { name, .. } => {
                Ok(Value::String(self.scope.string(name).unwrap_or_default()))
            }
            Ast::BooleanOrExpr {
                left, op, right, ..
            }
            | Ast::BooleanAndExpr {
                left, op, right, ..
            }
            | Ast::BooleanXorExpr {
                left, op, right, ..
            } => {
                if op.is_empty() {
                    return self.eval(left);
                }
                let mut acc = ops::to_boolean(&self.eval(left)?);
                for operand in right.iter().take(op.len()) {
                    let value = ops::to_boolean(&self.eval(operand)?);
                    acc = match node {
                        Ast::BooleanOrExpr { .. } => acc | value,
                        Ast::BooleanAndExpr { .. } => acc & value,
                        _ => acc ^ value,
                    };
                }
                Ok(Value::Boolean(acc))
            }
            Ast::NotExpr { value, .. } => Ok(Value::Boolean(!ops::to_boolean(&self.eval(value)?))),
            Ast::BooleanEqualityExpr {
                left, op, right, ..
            } => {
                let equality = Equality::parse(op);
                if self.declared_string(left) || self.declared_string(right) {
                    let l = self.equality_string(left)?;
                    let r = self.equality_string(right)?;
                    return Ok(Value::Boolean(equality.test(l, r)));
                }
                let l = self.boolean_operand(left)?;
                let r = self.boolean_operand(right)?;
                Ok(Value::Boolean(equality.test(l, r)))
            }
            Ast::BooleanFactorExpr { value, .. } => match value {
                AstValue::Node(inner) => self.eval(inner),
                AstValue::Text { text, .. } => {
                    let stripped = java::strip(text);
                    if let Some(name) = ops::exact_variable(stripped) {
                        return Ok(Value::Boolean(ops::to_boolean(&self.scope.any(name))));
                    }
                    Ok(Value::Boolean(ops::to_boolean(&Value::String(
                        text.clone(),
                    ))))
                }
            },
            Ast::StringComparisonExpr {
                left, op, right, ..
            } => {
                let equality = Equality::parse(op);
                let l = self.string_of(left)?;
                let r = self.string_of(right)?;
                Ok(Value::Boolean(equality.test(l, r)))
            }
            Ast::ComparisonExpr {
                left, op, right, ..
            } => {
                let l = self.number_of(left)?;
                let r = self.number_of(right)?;
                Ok(Value::Boolean(
                    Cmp::parse(op).test(ops::compare_numbers(&l, &r)),
                ))
            }
            Ast::NumberMatchExpr {
                firstCase,
                moreCases,
                defaultCase,
                ..
            }
            | Ast::StringMatchExpr {
                firstCase,
                moreCases,
                defaultCase,
                ..
            }
            | Ast::BooleanMatchExpr {
                firstCase,
                moreCases,
                defaultCase,
                ..
            } => {
                for case in std::iter::once(firstCase.as_ref()).chain(moreCases) {
                    let (condition, value) = match case {
                        Ast::NumberCaseExpr {
                            condition, value, ..
                        }
                        | Ast::StringCaseExpr {
                            condition, value, ..
                        }
                        | Ast::BooleanCaseExpr {
                            condition, value, ..
                        } => (condition, value),
                        _ => continue,
                    };
                    if ops::to_boolean(&self.eval(condition)?) {
                        let result = self.eval(value)?;
                        if result != Value::Null {
                            return Ok(result);
                        }
                    }
                }
                self.eval(defaultCase)
            }
            Ast::NumberCaseExpr { value, .. }
            | Ast::NumberDefaultCaseExpr { value, .. }
            | Ast::StringCaseExpr { value, .. }
            | Ast::StringDefaultCaseExpr { value, .. }
            | Ast::BooleanCaseExpr { value, .. }
            | Ast::BooleanDefaultCaseExpr { value, .. }
            | Ast::BooleanCaseValueExpr { value, .. }
            | Ast::StringCaseValueExpr { value, .. } => self.eval(value),
            Ast::NumberCaseValueExpr { value, .. } => self.number_of(value),
            Ast::VariableRefExpr { name, r#type, .. } => {
                let Some(name) = ops::variable_ref_name(name) else {
                    return Ok(Value::Null);
                };
                match r#type.as_deref().and_then(ops::parse_declared) {
                    Some(declared) => Ok(self.scope.typed(&name, declared)),
                    None => Ok(self.scope.for_result(&name, self.result_type())),
                }
            }
        }
    }

    fn formula(
        &mut self,
        imports: &'a [Ast],
        declarations: &'a [Ast],
        methods: &'a [Ast],
        expression: &'a Ast,
    ) -> R {
        for node in imports.iter().chain(declarations).chain(methods) {
            self.eval(node)?;
        }
        self.eval(expression)
    }

    fn declaration_value(
        &mut self,
        name: &str,
        only_if_absent: &Option<Box<Ast>>,
        value: &'a Option<Box<Ast>>,
    ) -> Result<Option<Value>, EvalError> {
        let apply = only_if_absent.is_none() || !self.scope.exists(name);
        match value {
            Some(value) if apply => Ok(Some(self.eval(value)?)),
            _ => Ok(None),
        }
    }

    /// `evalBinaryExpr`.
    fn binary_expr(&mut self, node: &'a Ast) -> R {
        if let Some(name) = exact_variable_of_binary(node) {
            let resolved = self.scope.any(&name);
            if resolved != Value::Null {
                return Ok(resolved);
            }
        }
        if !self.result_type().is_number() {
            match unwrap_transparent(node) {
                Unwrapped::Node(inner) if !std::ptr::eq(inner, node) => return self.eval(inner),
                Unwrapped::Text(text) => return self.leaf(text),
                Unwrapped::Node(_) => {}
            }
        }
        self.number_of(node)
    }

    /// `evalBinaryExpr` on a Java leaf `BinaryExpr(null, [text], [])`.
    fn leaf(&mut self, text: &str) -> R {
        if let Some(name) = ops::exact_variable(java::strip(text)) {
            let resolved = self.scope.any(name);
            if resolved != Value::Null {
                return Ok(resolved);
            }
        }
        self.leaf_literal(text)
    }

    /// `resolveLeafLiteral`.
    fn leaf_literal(&mut self, raw: &str) -> R {
        let literal = java::strip(raw);
        if let Some(name) = ops::exact_variable(literal) {
            return Ok(self
                .scope
                .number(name)
                .unwrap_or_else(|| ops::zero(self.number_type())));
        }
        ops::parse_number(self.number_type(), literal)
    }

    /// `evalBinaryAsNumber` (for a non-`BinaryExpr`, `evalOperandAsNumber`).
    fn number_of(&mut self, node: &'a Ast) -> R {
        match node {
            Ast::BinaryExpr {
                left, op, right, ..
            } => {
                if op.is_empty() && right.is_empty() {
                    return self.operand(left);
                }
                let mut current = self.operand(left)?;
                for (operator, operand) in op.iter().zip(right) {
                    let value = self.operand(operand)?;
                    current = ops::apply_binary(
                        self.number_type(),
                        Op::parse(operator)?,
                        &current,
                        &value,
                    )?;
                }
                Ok(current)
            }
            other => self.coerce_number(other),
        }
    }

    /// `evalOperandAsNumber`.
    fn operand(&mut self, value: &'a AstValue) -> R {
        if self.trace.is_some() {
            // Text operands and nested `BinaryExpr` operands are not `eval`uated as nodes, so the
            // trace records them here; other nodes are recorded by `eval` (`coerce_number`).
            return match value {
                AstValue::Text { text, span } => self
                    .traced(TraceSite::Leaf { text, span: *span }, |walker| {
                        walker.leaf_literal(text)
                    }),
                AstValue::Node(node) if matches!(node.as_ref(), Ast::BinaryExpr { .. }) => {
                    self.traced(TraceSite::Node(node), |walker| walker.number_of(node))
                }
                AstValue::Node(node) => self.number_of(node),
            };
        }
        match value {
            AstValue::Text { text, .. } => self.leaf_literal(text),
            AstValue::Node(node) => self.number_of(node),
        }
    }

    fn coerce_number(&mut self, node: &'a Ast) -> R {
        let value = self.eval(node)?;
        if ops::is_number(&value) {
            return Ok(value);
        }
        if value == Value::Null {
            return Ok(ops::zero(self.number_type()));
        }
        ops::parse_number(self.number_type(), &super::java_string(&value))
    }

    /// `resolveStringLeaf`.
    fn string_leaf(&mut self, value: &'a AstValue) -> Result<String, EvalError> {
        if self.trace.is_some() {
            if let AstValue::Text { text, span } = value {
                let traced = self.traced(TraceSite::Leaf { text, span: *span }, |walker| {
                    walker.string_text_leaf(text).map(Value::String)
                });
                return traced.map(|value| match value {
                    Value::String(text) => text,
                    _ => unreachable!("string leaves are strings"),
                });
            }
        }
        match value {
            AstValue::Text { text, .. } => self.string_text_leaf(text),
            AstValue::Node(node) => {
                if let Some(name) = exact_variable_of_binary(node) {
                    let resolved = self.scope.any(&name);
                    if resolved != Value::Null {
                        return Ok(super::java_string(&resolved));
                    }
                }
                let value = self.eval(node)?;
                Ok(if value == Value::Null {
                    String::new()
                } else {
                    super::java_string(&value)
                })
            }
        }
    }

    /// `resolveStringLeaf` on a text operand.
    fn string_text_leaf(&mut self, text: &'a str) -> Result<String, EvalError> {
        let stripped = java::strip(text);
        if let Some(name) = ops::exact_variable(stripped) {
            let resolved = self.scope.any(name);
            return Ok(if resolved == Value::Null {
                String::new()
            } else {
                super::java_string(&resolved)
            });
        }
        Ok(ops::unquote(stripped).unwrap_or(text).to_owned())
    }

    fn math1(&mut self, function: Math1, arg: &'a Ast) -> R {
        let x = ops::number_arg(&self.eval(arg)?)?;
        Ok(ops::cast_double(
            self.number_type(),
            ops::math1(function, self.scope.base, x),
        ))
    }

    fn str_fn(&mut self, function: StrFn, value: &'a Ast) -> R {
        let value = self.eval(value)?;
        Ok(ops::str_fn(function, &value, self.number_type()))
    }

    fn pred(&mut self, function: Pred, value: &'a Ast, patterns: &'a [Ast]) -> R {
        let value = self.string_of(value)?;
        for pattern in patterns {
            let pattern = self.string_of(pattern)?;
            if ops::pred(function, &value, &pattern) {
                return Ok(Value::Boolean(true));
            }
        }
        Ok(Value::Boolean(false))
    }

    fn declared_string(&self, operand: &AstValue) -> bool {
        let AstValue::Node(node) = operand else {
            return false;
        };
        match node.as_ref() {
            Ast::StringCastVariableRefExpr { .. } | Ast::StringTypedVariableRefExpr { .. } => {
                return true
            }
            Ast::VariableRefExpr {
                r#type: Some(kind), ..
            } if ops::parse_declared(kind) == Some(Declared::String) => return true,
            _ => {}
        }
        if self.declared.is_empty() {
            return false;
        }
        let name = match node.as_ref() {
            Ast::VariableRefExpr { name, .. } => ops::variable_ref_name(name),
            Ast::BinaryExpr { .. } => exact_variable_of_binary(node),
            _ => None,
        };
        name.and_then(|name| self.declared.get(&name).copied()) == Some(Declared::String)
    }

    /// `stringValueOfEqualityOperand`.
    fn equality_string(&mut self, operand: &'a AstValue) -> Result<String, EvalError> {
        match operand {
            AstValue::Text { text, .. } => Ok(text.clone()),
            AstValue::Node(node) => {
                let value = match node.as_ref() {
                    Ast::VariableRefExpr { name, .. } => match ops::variable_ref_name(name) {
                        Some(name) => self.scope.any(&name),
                        None => Value::Null,
                    },
                    _ => self.eval(node)?,
                };
                Ok(if value == Value::Null {
                    String::new()
                } else {
                    super::java_string(&value)
                })
            }
        }
    }

    /// `resolveBooleanSourceOperand`.
    fn boolean_operand(&mut self, operand: &'a AstValue) -> Result<bool, EvalError> {
        match operand {
            AstValue::Node(node) => Ok(ops::to_boolean(&self.eval(node)?)),
            AstValue::Text { text, .. } => {
                let stripped = java::strip(text);
                if stripped.is_empty() {
                    return Ok(false);
                }
                if let Some(name) = ops::exact_variable(stripped) {
                    return Ok(ops::to_boolean(&self.scope.any(name)));
                }
                Ok(ops::to_boolean(&Value::String(stripped.to_owned())))
            }
        }
    }

    fn arguments(args: &'a Option<Box<Ast>>) -> &'a [Ast] {
        match args.as_deref() {
            Some(Ast::ArgumentsExpr { values, .. }) => values,
            _ => &[],
        }
    }

    /// `evalMethodInvocationExpr`.
    fn invoke(&mut self, name: &str, args: &'a Option<Box<Ast>>) -> R {
        let name = java::strip(name);
        let Some(method) = self.methods.get(name).copied() else {
            return Err(err(
                ErrorKind::UnsupportedOperation,
                format!("Generated AST method not found: {name}"),
            ));
        };
        let (parameters, body) = match method {
            Ast::NumberMethodDeclarationExpr {
                parameters,
                expression,
                ..
            }
            | Ast::StringMethodDeclarationExpr {
                parameters,
                expression,
                ..
            }
            | Ast::BooleanMethodDeclarationExpr {
                parameters,
                expression,
                ..
            }
            | Ast::ObjectMethodDeclarationExpr {
                parameters,
                expression,
                ..
            } => (parameters, expression),
            _ => unreachable!("only method declarations are registered"),
        };
        let parameters: &[Ast] = match parameters.as_deref() {
            Some(Ast::MethodParametersExpr { values, .. }) => values,
            _ => &[],
        };
        let arguments = Self::arguments(args);
        if parameters.len() != arguments.len() {
            return Err(err(
                ErrorKind::UnsupportedOperation,
                format!(
                    "Argument count mismatch for method {name}: expected {} but got {}",
                    parameters.len(),
                    arguments.len()
                ),
            ));
        }
        let mut bindings = HashMap::new();
        for (parameter, argument) in parameters.iter().zip(arguments) {
            let value = self.eval(argument)?;
            if let Ast::MethodParameterExpr {
                paramName, r#type, ..
            } = parameter
            {
                let declared = r#type
                    .as_deref()
                    .and_then(ops::parse_declared)
                    .unwrap_or(Declared::Object);
                bindings.insert(
                    paramName.clone(),
                    ops::coerce(value, declared, self.number_type()),
                );
            }
        }
        if self.depth >= self.program.options.max_call_depth {
            return Err(err(ErrorKind::StackOverflow, "method call depth exceeded"));
        }
        self.depth += 1;
        let pushed = !bindings.is_empty();
        if pushed {
            self.scope.frames.push(bindings);
        }
        let result = self.eval(body);
        if pushed {
            self.scope.frames.pop();
        }
        self.depth -= 1;
        result
    }

    /// `evaluateExternalInvocation`.
    fn external(
        &mut self,
        qualifier: &Option<Box<Ast>>,
        target: &str,
        args: &'a Option<Box<Ast>>,
        expected: Declared,
    ) -> R {
        let name = java::strip(target);
        let mut class_name = match qualifier.as_deref() {
            Some(Ast::QualifiedNameExpr { head, tail, .. }) => ops::qualified_name(head, tail),
            _ => String::new(),
        };
        let mut method_name = name.to_owned();
        if class_name.is_empty() {
            if let Some((class, method)) = self.imports.get(name) {
                class_name = class.clone();
                if let Some(method) = method {
                    method_name = method.clone();
                }
            }
        } else if let Some((class, _)) = self.imports.get(&class_name) {
            class_name = class.clone();
        }
        if class_name.is_empty() {
            return Err(err(
                ErrorKind::UnsupportedOperation,
                format!("External target is not imported: {name}"),
            ));
        }
        if !self.host.external.class_exists(&class_name) {
            return Err(ops::external_error(
                super::ExternalError::ClassNotFound,
                &class_name,
                &method_name,
            ));
        }
        let mut values = Vec::new();
        for argument in Self::arguments(args) {
            values.push(self.eval(argument)?);
        }
        let call = ExternalCall {
            class_name: &class_name,
            method_name: &method_name,
            args: &values,
        };
        match self.host.external.invoke(&call, &self.scope) {
            Ok(Value::Null) => Ok(Value::Null),
            Ok(value) => Ok(ops::coerce(value, expected, self.number_type())),
            Err(error) => Err(ops::external_error(error, &class_name, &method_name)),
        }
    }
}
