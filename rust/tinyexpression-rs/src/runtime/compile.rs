//! Closure compilation: the selected AST is lowered once into nested `Box<dyn Fn>` closures.
//!
//! Every closure reproduces one `P4TypedAstEvaluator` method exactly like the tree walker
//! (`walk.rs`); what compilation removes is the per-evaluation dispatch work that does not
//! depend on the context: node-kind matching, operator parsing, numeric literal parsing for the
//! configured `numberType`, slice-index parsing, import resolution, declared-type lookups and
//! method-name lookups. Errors that Java raises at evaluation time (a literal that does not
//! parse as the `numberType`, a bad slice index, an unknown method) are still raised when the
//! closure runs, so both forms fail on the same rows with the same exception kind.

#![allow(non_snake_case)]

use std::collections::HashMap;

use super::java;
use super::ops::{self, err, Cmp, Declared, Equality, Math1, Op, Pred, Scope, StrFn, R};
use super::walk::exact_variable_of_binary;
use super::{
    Context, ErrorKind, EvalError, ExternalCall, ExternalError, Host, NumberType, Program,
    ResultType,
};
use crate::generated::ast::{Ast, AstValue};
use crate::Value;

/// Evaluation-time state.
pub(crate) struct Exec<'c, 'h, 'hh> {
    scope: Scope<'c>,
    host: &'h mut Host<'hh>,
    depth: usize,
}

struct Method {
    params: Vec<(String, Declared)>,
    body: Code,
}

struct Env {
    methods: Vec<Method>,
    max_call_depth: usize,
}

type Code = Box<dyn Fn(&mut Exec<'_, '_, '_>, &Env) -> R>;
type StrCode = Box<dyn Fn(&mut Exec<'_, '_, '_>, &Env) -> Result<String, EvalError>>;
type BoolCode = Box<dyn Fn(&mut Exec<'_, '_, '_>, &Env) -> Result<bool, EvalError>>;

/// A program lowered into closures. Evaluate it any number of times with [`Compiled::eval`].
pub struct Compiled {
    root: Option<Code>,
    env: Env,
}

impl Compiled {
    /// Same contract as [`Program::eval_tree`].
    pub fn eval(&self, context: &mut Context, host: &mut Host<'_>) -> Result<Value, EvalError> {
        let Some(root) = &self.root else {
            return Ok(Value::Null);
        };
        let mut exec = Exec {
            scope: Scope {
                base: context,
                frames: Vec::new(),
            },
            host,
            depth: 0,
        };
        root(&mut exec, &self.env)
    }
}

impl std::fmt::Debug for Compiled {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("Compiled")
            .field("methods", &self.env.methods.len())
            .finish()
    }
}

pub(crate) fn compile_program(program: &Program) -> Compiled {
    let mut compiler = Compiler {
        program,
        number_type: program.options.number_type,
        result_type: program.options.result_type,
        imports: HashMap::new(),
        declared: HashMap::new(),
        method_index: None,
        methods: Vec::new(),
    };
    let root = program.root.as_ref().map(|root| compiler.node(root));
    Compiled {
        root,
        env: Env {
            methods: compiler.methods,
            max_call_depth: program.options.max_call_depth,
        },
    }
}

struct Compiler<'p> {
    program: &'p Program,
    number_type: NumberType,
    result_type: ResultType,
    imports: HashMap<String, (String, Option<String>)>,
    declared: HashMap<String, Declared>,
    /// `None` while compiling code that runs before the formula's methods are registered.
    method_index: Option<HashMap<String, usize>>,
    methods: Vec<Method>,
}

fn constant(value: R) -> Code {
    Box::new(move |_, _| value.clone())
}

fn java_string_or_empty(value: &Value) -> String {
    if *value == Value::Null {
        String::new()
    } else {
        super::java_string(value)
    }
}

impl Compiler<'_> {
    fn node(&mut self, node: &Ast) -> Code {
        let nt = self.number_type;
        match node {
            Ast::FormulaExpr {
                imports,
                declarations,
                expression,
                methods,
                ..
            } => self.formula(imports, declarations, methods, expression),
            Ast::CodeBlockExpr { .. }
            | Ast::MethodParametersExpr { .. }
            | Ast::MethodParameterExpr { .. }
            | Ast::ArgumentsExpr { .. } => constant(Ok(Value::Null)),
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
                constant(Ok(Value::Null))
            }
            Ast::QualifiedNameExpr { head, tail, .. } => {
                constant(Ok(Value::String(ops::qualified_name(head, tail))))
            }
            Ast::NumberVariableDeclarationExpr {
                varName,
                onlyIfAbsent,
                value,
                ..
            }
            | Ast::StringVariableDeclarationExpr {
                varName,
                onlyIfAbsent,
                value,
                ..
            }
            | Ast::BooleanVariableDeclarationExpr {
                varName,
                onlyIfAbsent,
                value,
                ..
            }
            | Ast::ObjectVariableDeclarationExpr {
                varName,
                onlyIfAbsent,
                value,
                ..
            } => {
                let kind = match node {
                    Ast::NumberVariableDeclarationExpr { .. } => Declared::Number,
                    Ast::StringVariableDeclarationExpr { .. } => Declared::String,
                    Ast::BooleanVariableDeclarationExpr { .. } => Declared::Boolean,
                    _ => Declared::Object,
                };
                self.declared.insert(varName.clone(), kind);
                let Some(value) = value else {
                    return constant(Ok(Value::Null));
                };
                let value = self.node(value);
                let name = varName.clone();
                let only_if_absent = onlyIfAbsent.is_some();
                Box::new(move |x, env| {
                    if only_if_absent && x.scope.exists(&name) {
                        return Ok(Value::Null);
                    }
                    let value = value(x, env)?;
                    let stored = match kind {
                        Declared::Number => {
                            if value != Value::Null && !ops::is_number(&value) {
                                return Err(err(
                                    ErrorKind::ClassCast,
                                    format!(
                                        "{} cannot be cast to java.lang.Number",
                                        ops::java_class(&value)
                                    ),
                                ));
                            }
                            value
                        }
                        Declared::String => Value::String(super::java_string(&value)),
                        Declared::Boolean => Value::Boolean(value == Value::Boolean(true)),
                        Declared::Object => value,
                    };
                    x.scope.set(&name, stored);
                    Ok(Value::Null)
                })
            }
            Ast::OnlyIfAbsentExpr { .. } => constant(Ok(Value::Boolean(true))),
            Ast::NumberMethodDeclarationExpr { .. }
            | Ast::StringMethodDeclarationExpr { .. }
            | Ast::BooleanMethodDeclarationExpr { .. }
            | Ast::ObjectMethodDeclarationExpr { .. } => constant(Ok(Value::Null)),
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
                let condition = self.node(condition);
                let then_code = self.node(thenExpr);
                let else_code = self.node(elseExpr);
                Box::new(move |x, env| {
                    if ops::to_boolean(&condition(x, env)?) {
                        then_code(x, env)
                    } else {
                        else_code(x, env)
                    }
                })
            }
            Ast::ArgumentExpressionExpr { value, .. }
            | Ast::ExpressionExpr { value, .. }
            | Ast::ObjectExpr { value, .. } => {
                let inner = self.node(value);
                match exact_variable_of_binary(value) {
                    Some(name) => Box::new(move |x, env| {
                        let resolved = x.scope.any(&name);
                        if resolved != Value::Null {
                            return Ok(resolved);
                        }
                        inner(x, env)
                    }),
                    None => inner,
                }
            }
            Ast::BranchExpressionExpr { value, .. } => self.node(value),
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
                let first = self.node(first);
                let rest: Vec<Code> = rest.iter().map(|r| self.node(r)).collect();
                Box::new(move |x, env| {
                    let mut acc = ops::number_arg(&first(x, env)?)?;
                    for next in &rest {
                        let value = ops::number_arg(&next(x, env)?)?;
                        acc = if is_min {
                            java::math_min(acc, value)
                        } else {
                            java::math_max(acc, value)
                        };
                    }
                    Ok(ops::cast_double(nt, acc))
                })
            }
            Ast::RandomExpr { .. } => Box::new(move |x, _| {
                let value = x.host.random.next_double();
                Ok(ops::cast_double(nt, value))
            }),
            Ast::PowExpr { base, exponent, .. } => {
                let base = self.node(base);
                let exponent = self.node(exponent);
                Box::new(move |x, env| {
                    let b = ops::number_arg(&base(x, env)?)?;
                    let e = ops::number_arg(&exponent(x, env)?)?;
                    Ok(ops::cast_double(nt, java::math_pow(b, e)))
                })
            }
            Ast::ToNumExpr {
                value,
                defaultValue,
                ..
            } => {
                let text = self.string_of(value);
                let fallback = self.node(defaultValue);
                Box::new(move |x, env| {
                    let text = text(x, env)?;
                    match java::parse_double(&text) {
                        Some(parsed) => Ok(ops::cast_double(nt, parsed)),
                        None => {
                            let value = fallback(x, env)?;
                            let value = if ops::is_number(&value) {
                                ops::double_value(&value)
                            } else {
                                0.0
                            };
                            Ok(ops::cast_double(nt, value))
                        }
                    }
                })
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
                match name {
                    Some(name) => Box::new(move |x, _| Ok(Value::Boolean(x.scope.exists(&name)))),
                    None => constant(Ok(Value::Boolean(false))),
                }
            }
            Ast::InTimeRangeExpr {
                startHour, endHour, ..
            } => {
                let from = self.number_of(startHour);
                let to = self.number_of(endHour);
                Box::new(move |x, env| {
                    let from = ops::float_value(&from(x, env)?);
                    let to = ops::float_value(&to(x, env)?);
                    let now = x.host.clock.now_hour(&x.scope);
                    Ok(Value::Boolean(ops::in_time_range(now, from, to)))
                })
            }
            Ast::InDayTimeRangeExpr {
                startDay,
                startHour,
                endDay,
                endHour,
                ..
            } => {
                let from_hour = self.number_of(startHour);
                let to_hour = self.number_of(endHour);
                let (start_day, end_day) = (startDay.clone(), endDay.clone());
                Box::new(move |x, env| {
                    let from_hour = ops::float_value(&from_hour(x, env)?);
                    let to_hour = ops::float_value(&to_hour(x, env)?);
                    let from_day = ops::day_of_week(&start_day)?;
                    let to_day = ops::day_of_week(&end_day)?;
                    Ok(Value::Boolean(ops::in_day_time_range(
                        x.host.clock,
                        x.scope.base,
                        from_day,
                        from_hour,
                        to_day,
                        to_hour,
                    )))
                })
            }
            Ast::SliceExpr {
                value,
                start,
                end,
                step,
                ..
            } => {
                let text = self.string_leaf(value);
                let index = |node: &Option<Box<Ast>>| {
                    ops::slice_index(node.as_ref().map(|n| self.program.source_text(n.span())))
                };
                let indexes: Result<_, EvalError> =
                    (|| Ok((index(start)?, index(end)?, index(step)?)))();
                Box::new(move |x, env| {
                    let text = text(x, env)?;
                    let (start, end, step) = indexes.clone()?;
                    ops::slice(&text, start, end, step)
                })
            }
            Ast::StringConcatExpr {
                left, op, right, ..
            } => {
                let left = self.string_leaf(left);
                if op.is_empty() {
                    return Box::new(move |x, env| Ok(Value::String(left(x, env)?)));
                }
                let rest: Vec<StrCode> = right
                    .iter()
                    .take(op.len())
                    .map(|r| self.string_leaf(r))
                    .collect();
                Box::new(move |x, env| {
                    let mut text = left(x, env)?;
                    for part in &rest {
                        text.push_str(&part(x, env)?);
                    }
                    Ok(Value::String(text))
                })
            }
            Ast::StringCastVariableRefExpr { name, .. }
            | Ast::StringTypedVariableRefExpr { name, .. } => {
                let name = name.clone();
                Box::new(move |x, _| Ok(Value::String(x.scope.string(&name).unwrap_or_default())))
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
                let left = self.node(left);
                if op.is_empty() {
                    return left;
                }
                let rest: Vec<Code> = right.iter().take(op.len()).map(|r| self.node(r)).collect();
                let kind = match node {
                    Ast::BooleanOrExpr { .. } => 0,
                    Ast::BooleanAndExpr { .. } => 1,
                    _ => 2,
                };
                Box::new(move |x, env| {
                    let mut acc = ops::to_boolean(&left(x, env)?);
                    for next in &rest {
                        let value = ops::to_boolean(&next(x, env)?);
                        acc = match kind {
                            0 => acc | value,
                            1 => acc & value,
                            _ => acc ^ value,
                        };
                    }
                    Ok(Value::Boolean(acc))
                })
            }
            Ast::NotExpr { value, .. } => {
                let value = self.node(value);
                Box::new(move |x, env| Ok(Value::Boolean(!ops::to_boolean(&value(x, env)?))))
            }
            Ast::BooleanEqualityExpr {
                left, op, right, ..
            } => {
                let equality = Equality::parse(op);
                if self.declared_string(left) || self.declared_string(right) {
                    let l = self.equality_string(left);
                    let r = self.equality_string(right);
                    return Box::new(move |x, env| {
                        let l = l(x, env)?;
                        let r = r(x, env)?;
                        Ok(Value::Boolean(equality.test(l, r)))
                    });
                }
                let l = self.boolean_operand(left);
                let r = self.boolean_operand(right);
                Box::new(move |x, env| {
                    let l = l(x, env)?;
                    let r = r(x, env)?;
                    Ok(Value::Boolean(equality.test(l, r)))
                })
            }
            Ast::BooleanFactorExpr { value, .. } => match value {
                AstValue::Node(inner) => self.node(inner),
                AstValue::Text { text, .. } => {
                    let stripped = java::strip(text);
                    match ops::exact_variable(stripped) {
                        Some(name) => {
                            let name = name.to_owned();
                            Box::new(move |x, _| {
                                Ok(Value::Boolean(ops::to_boolean(&x.scope.any(&name))))
                            })
                        }
                        None => constant(Ok(Value::Boolean(ops::to_boolean(&Value::String(
                            text.clone(),
                        ))))),
                    }
                }
            },
            Ast::StringComparisonExpr {
                left, op, right, ..
            } => {
                let equality = Equality::parse(op);
                let l = self.string_of(left);
                let r = self.string_of(right);
                Box::new(move |x, env| {
                    let l = l(x, env)?;
                    let r = r(x, env)?;
                    Ok(Value::Boolean(equality.test(l, r)))
                })
            }
            Ast::ComparisonExpr {
                left, op, right, ..
            } => {
                let cmp = Cmp::parse(op);
                let l = self.number_of(left);
                let r = self.number_of(right);
                Box::new(move |x, env| {
                    let l = l(x, env)?;
                    let r = r(x, env)?;
                    Ok(Value::Boolean(cmp.test(ops::compare_numbers(&l, &r))))
                })
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
                let mut cases: Vec<(Code, Code)> = Vec::new();
                for case in std::iter::once(firstCase.as_ref()).chain(moreCases) {
                    if let Ast::NumberCaseExpr {
                        condition, value, ..
                    }
                    | Ast::StringCaseExpr {
                        condition, value, ..
                    }
                    | Ast::BooleanCaseExpr {
                        condition, value, ..
                    } = case
                    {
                        cases.push((self.node(condition), self.node(value)));
                    }
                }
                let default = self.node(defaultCase);
                Box::new(move |x, env| {
                    for (condition, value) in &cases {
                        if ops::to_boolean(&condition(x, env)?) {
                            let result = value(x, env)?;
                            if result != Value::Null {
                                return Ok(result);
                            }
                        }
                    }
                    default(x, env)
                })
            }
            Ast::NumberCaseExpr { value, .. }
            | Ast::NumberDefaultCaseExpr { value, .. }
            | Ast::StringCaseExpr { value, .. }
            | Ast::StringDefaultCaseExpr { value, .. }
            | Ast::BooleanCaseExpr { value, .. }
            | Ast::BooleanDefaultCaseExpr { value, .. }
            | Ast::BooleanCaseValueExpr { value, .. }
            | Ast::StringCaseValueExpr { value, .. } => self.node(value),
            Ast::NumberCaseValueExpr { value, .. } => self.number_of(value),
            Ast::VariableRefExpr { name, r#type, .. } => {
                let Some(name) = ops::variable_ref_name(name) else {
                    return constant(Ok(Value::Null));
                };
                match r#type.as_deref().and_then(ops::parse_declared) {
                    Some(declared) => Box::new(move |x, _| Ok(x.scope.typed(&name, declared))),
                    None => {
                        let rt = self.result_type;
                        Box::new(move |x, _| Ok(x.scope.for_result(&name, rt)))
                    }
                }
            }
        }
    }

    fn formula(
        &mut self,
        imports: &[Ast],
        declarations: &[Ast],
        methods: &[Ast],
        expression: &Ast,
    ) -> Code {
        // Java evaluates imports, declarations, method registrations and then the expression in
        // a fresh calculation-local scope. Imports and method registration have no runtime
        // effect beyond the tables compiled here.
        for import in imports {
            let _ = self.node(import);
        }
        let saved_methods = self.method_index.take();
        let declarations: Vec<Code> = declarations.iter().map(|d| self.node(d)).collect();
        let mut index = HashMap::new();
        let mut bodies = Vec::new();
        for method in methods {
            if let Ast::NumberMethodDeclarationExpr {
                methodName,
                parameters,
                expression,
                ..
            }
            | Ast::StringMethodDeclarationExpr {
                methodName,
                parameters,
                expression,
                ..
            }
            | Ast::BooleanMethodDeclarationExpr {
                methodName,
                parameters,
                expression,
                ..
            }
            | Ast::ObjectMethodDeclarationExpr {
                methodName,
                parameters,
                expression,
                ..
            } = method
            {
                let slot = self.methods.len() + bodies.len();
                index.insert(methodName.clone(), slot);
                bodies.push((parameters, expression));
            }
        }
        self.method_index = Some(index);
        let base = self.methods.len();
        // Reserve slots so recursive bodies can refer to each other by index.
        for (parameters, _) in &bodies {
            let params = match parameters.as_deref() {
                Some(Ast::MethodParametersExpr { values, .. }) => values
                    .iter()
                    .filter_map(|p| match p {
                        Ast::MethodParameterExpr {
                            paramName, r#type, ..
                        } => Some((
                            paramName.clone(),
                            r#type
                                .as_deref()
                                .and_then(ops::parse_declared)
                                .unwrap_or(Declared::Object),
                        )),
                        _ => None,
                    })
                    .collect(),
                _ => Vec::new(),
            };
            self.methods.push(Method {
                params,
                body: constant(Ok(Value::Null)),
            });
        }
        for (offset, (_, expression)) in bodies.iter().enumerate() {
            let body = self.node(expression);
            self.methods[base + offset].body = body;
        }
        let expression = self.node(expression);
        self.method_index = saved_methods;
        Box::new(move |x, env| {
            x.scope.frames.push(HashMap::new());
            let result = (|| {
                for declaration in &declarations {
                    declaration(x, env)?;
                }
                expression(x, env)
            })();
            x.scope.frames.pop();
            result
        })
    }

    fn binary_expr(&mut self, node: &Ast) -> Code {
        let variable = exact_variable_of_binary(node);
        let body: Code = if !self.result_type.is_number() {
            let mut current = node;
            let mut text = None;
            while let Ast::BinaryExpr {
                left, op, right, ..
            } = current
            {
                if !(op.is_empty() && right.is_empty()) {
                    break;
                }
                match left {
                    AstValue::Node(inner) => current = inner,
                    AstValue::Text { text: t, .. } => {
                        text = Some(t.clone());
                        break;
                    }
                }
            }
            match text {
                Some(text) => self.leaf(&text),
                None if !std::ptr::eq(current, node) => self.node(current),
                None => self.number_of(node),
            }
        } else {
            self.number_of(node)
        };
        match variable {
            Some(name) => Box::new(move |x, env| {
                let resolved = x.scope.any(&name);
                if resolved != Value::Null {
                    return Ok(resolved);
                }
                body(x, env)
            }),
            None => body,
        }
    }

    fn leaf(&mut self, text: &str) -> Code {
        let literal = self.leaf_literal(text);
        match ops::exact_variable(java::strip(text)) {
            Some(name) => {
                let name = name.to_owned();
                Box::new(move |x, env| {
                    let resolved = x.scope.any(&name);
                    if resolved != Value::Null {
                        return Ok(resolved);
                    }
                    literal(x, env)
                })
            }
            None => literal,
        }
    }

    fn leaf_literal(&mut self, raw: &str) -> Code {
        let nt = self.number_type;
        let literal = java::strip(raw);
        match ops::exact_variable(literal) {
            Some(name) => {
                let name = name.to_owned();
                Box::new(move |x, _| Ok(x.scope.number(&name).unwrap_or_else(|| ops::zero(nt))))
            }
            None => constant(ops::parse_number(nt, literal)),
        }
    }

    fn number_of(&mut self, node: &Ast) -> Code {
        let nt = self.number_type;
        match node {
            Ast::BinaryExpr {
                left, op, right, ..
            } => {
                let first = self.operand(left);
                if op.is_empty() && right.is_empty() {
                    return first;
                }
                let mut steps: Vec<(Result<Op, EvalError>, Code)> = Vec::new();
                for (operator, operand) in op.iter().zip(right) {
                    steps.push((Op::parse(operator), self.operand(operand)));
                }
                // Common case: one float operation between two operands.
                Box::new(move |x, env| {
                    let mut current = first(x, env)?;
                    for (operator, operand) in &steps {
                        let value = operand(x, env)?;
                        current = ops::apply_binary(nt, operator.clone()?, &current, &value)?;
                    }
                    Ok(current)
                })
            }
            other => {
                let inner = self.node(other);
                Box::new(move |x, env| {
                    let value = inner(x, env)?;
                    if ops::is_number(&value) {
                        return Ok(value);
                    }
                    if value == Value::Null {
                        return Ok(ops::zero(nt));
                    }
                    ops::parse_number(nt, &super::java_string(&value))
                })
            }
        }
    }

    fn operand(&mut self, value: &AstValue) -> Code {
        match value {
            AstValue::Text { text, .. } => self.leaf_literal(text),
            AstValue::Node(node) => self.number_of(node),
        }
    }

    fn string_of(&mut self, node: &Ast) -> StrCode {
        let inner = self.node(node);
        Box::new(move |x, env| Ok(super::java_string(&inner(x, env)?)))
    }

    fn string_leaf(&mut self, value: &AstValue) -> StrCode {
        match value {
            AstValue::Text { text, .. } => {
                let stripped = java::strip(text);
                match ops::exact_variable(stripped) {
                    Some(name) => {
                        let name = name.to_owned();
                        Box::new(move |x, _| Ok(java_string_or_empty(&x.scope.any(&name))))
                    }
                    None => {
                        let text = ops::unquote(stripped).unwrap_or(text).to_owned();
                        Box::new(move |_, _| Ok(text.clone()))
                    }
                }
            }
            AstValue::Node(node) => {
                let inner = self.node(node);
                match exact_variable_of_binary(node) {
                    Some(name) => Box::new(move |x, env| {
                        let resolved = x.scope.any(&name);
                        if resolved != Value::Null {
                            return Ok(super::java_string(&resolved));
                        }
                        Ok(java_string_or_empty(&inner(x, env)?))
                    }),
                    None => Box::new(move |x, env| Ok(java_string_or_empty(&inner(x, env)?))),
                }
            }
        }
    }

    fn math1(&mut self, function: Math1, arg: &Ast) -> Code {
        let nt = self.number_type;
        let arg = self.node(arg);
        Box::new(move |x, env| {
            let value = ops::number_arg(&arg(x, env)?)?;
            Ok(ops::cast_double(
                nt,
                ops::math1(function, x.scope.base, value),
            ))
        })
    }

    fn str_fn(&mut self, function: StrFn, value: &Ast) -> Code {
        let nt = self.number_type;
        let value = self.node(value);
        Box::new(move |x, env| Ok(ops::str_fn(function, &value(x, env)?, nt)))
    }

    fn pred(&mut self, function: Pred, value: &Ast, patterns: &[Ast]) -> Code {
        let value = self.string_of(value);
        let patterns: Vec<StrCode> = patterns.iter().map(|p| self.string_of(p)).collect();
        Box::new(move |x, env| {
            let value = value(x, env)?;
            for pattern in &patterns {
                if ops::pred(function, &value, &pattern(x, env)?) {
                    return Ok(Value::Boolean(true));
                }
            }
            Ok(Value::Boolean(false))
        })
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
        let name = match node.as_ref() {
            Ast::VariableRefExpr { name, .. } => ops::variable_ref_name(name),
            Ast::BinaryExpr { .. } => exact_variable_of_binary(node),
            _ => None,
        };
        name.and_then(|name| self.declared.get(&name).copied()) == Some(Declared::String)
    }

    fn equality_string(&mut self, operand: &AstValue) -> StrCode {
        match operand {
            AstValue::Text { text, .. } => {
                let text = text.clone();
                Box::new(move |_, _| Ok(text.clone()))
            }
            AstValue::Node(node) => match node.as_ref() {
                Ast::VariableRefExpr { name, .. } => match ops::variable_ref_name(name) {
                    Some(name) => {
                        Box::new(move |x, _| Ok(java_string_or_empty(&x.scope.any(&name))))
                    }
                    None => Box::new(|_, _| Ok(String::new())),
                },
                other => {
                    let inner = self.node(other);
                    Box::new(move |x, env| Ok(java_string_or_empty(&inner(x, env)?)))
                }
            },
        }
    }

    fn boolean_operand(&mut self, operand: &AstValue) -> BoolCode {
        match operand {
            AstValue::Node(node) => {
                let inner = self.node(node);
                Box::new(move |x, env| Ok(ops::to_boolean(&inner(x, env)?)))
            }
            AstValue::Text { text, .. } => {
                let stripped = java::strip(text);
                if stripped.is_empty() {
                    return Box::new(|_, _| Ok(false));
                }
                match ops::exact_variable(stripped) {
                    Some(name) => {
                        let name = name.to_owned();
                        Box::new(move |x, _| Ok(ops::to_boolean(&x.scope.any(&name))))
                    }
                    None => {
                        let value = ops::to_boolean(&Value::String(stripped.to_owned()));
                        Box::new(move |_, _| Ok(value))
                    }
                }
            }
        }
    }

    fn arguments(&mut self, args: &Option<Box<Ast>>) -> Vec<Code> {
        match args.as_deref() {
            Some(Ast::ArgumentsExpr { values, .. }) => {
                values.iter().map(|v| self.node(v)).collect()
            }
            _ => Vec::new(),
        }
    }

    fn invoke(&mut self, name: &str, args: &Option<Box<Ast>>) -> Code {
        let nt = self.number_type;
        let name = java::strip(name).to_owned();
        let slot = self
            .method_index
            .as_ref()
            .and_then(|index| index.get(&name).copied());
        let Some(slot) = slot else {
            return constant(Err(err(
                ErrorKind::UnsupportedOperation,
                format!("Generated AST method not found: {name}"),
            )));
        };
        let arguments = self.arguments(args);
        Box::new(move |x, env| {
            let method = &env.methods[slot];
            if method.params.len() != arguments.len() {
                return Err(err(
                    ErrorKind::UnsupportedOperation,
                    format!(
                        "Argument count mismatch for method {name}: expected {} but got {}",
                        method.params.len(),
                        arguments.len()
                    ),
                ));
            }
            let mut bindings = HashMap::new();
            for ((param, declared), argument) in method.params.iter().zip(&arguments) {
                let value = argument(x, env)?;
                bindings.insert(param.clone(), ops::coerce(value, *declared, nt));
            }
            if x.depth >= env.max_call_depth {
                return Err(err(ErrorKind::StackOverflow, "method call depth exceeded"));
            }
            x.depth += 1;
            let pushed = !bindings.is_empty();
            if pushed {
                x.scope.frames.push(bindings);
            }
            let result = (method.body)(x, env);
            if pushed {
                x.scope.frames.pop();
            }
            x.depth -= 1;
            result
        })
    }

    fn external(
        &mut self,
        qualifier: &Option<Box<Ast>>,
        target: &str,
        args: &Option<Box<Ast>>,
        expected: Declared,
    ) -> Code {
        let nt = self.number_type;
        let name = java::strip(target).to_owned();
        let mut class_name = match qualifier.as_deref() {
            Some(Ast::QualifiedNameExpr { head, tail, .. }) => ops::qualified_name(head, tail),
            _ => String::new(),
        };
        let mut method_name = name.clone();
        if class_name.is_empty() {
            if let Some((class, method)) = self.imports.get(&name) {
                class_name = class.clone();
                if let Some(method) = method {
                    method_name = method.clone();
                }
            }
        } else if let Some((class, _)) = self.imports.get(&class_name) {
            class_name = class.clone();
        }
        if class_name.is_empty() {
            return constant(Err(err(
                ErrorKind::UnsupportedOperation,
                format!("External target is not imported: {name}"),
            )));
        }
        let arguments = self.arguments(args);
        let code_block = self.program.declares_code_block(&class_name);
        Box::new(move |x, env| {
            if !x.host.external.class_exists(&class_name) {
                return Err(super::code_block::external_error(
                    ExternalError::ClassNotFound,
                    &class_name,
                    &method_name,
                    code_block,
                ));
            }
            let mut values = Vec::with_capacity(arguments.len());
            for argument in &arguments {
                values.push(argument(x, env)?);
            }
            let call = ExternalCall {
                class_name: &class_name,
                method_name: &method_name,
                args: &values,
            };
            match x.host.external.invoke(&call, &x.scope) {
                Ok(Value::Null) => Ok(Value::Null),
                Ok(value) => Ok(ops::coerce(value, expected, nt)),
                Err(error) => Err(super::code_block::external_error(
                    error,
                    &class_name,
                    &method_name,
                    code_block,
                )),
            }
        })
    }
}
