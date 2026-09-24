//! Semantics shared by the tree walker and the closure compiler: Java boxing and coercions,
//! `numberType` arithmetic, variable scopes, and the small helpers of `P4TypedAstEvaluator`.

use std::cmp::Ordering;
use std::collections::HashMap;

use super::java;
use super::{
    Clock, Context, DayOfWeek, ErrorKind, EvalError, ExternalError, NumberType, ResultType,
    Variables,
};
use crate::Value;

pub(crate) type R = Result<Value, EvalError>;

pub(crate) fn err(kind: ErrorKind, message: impl Into<String>) -> EvalError {
    EvalError::new(kind, message)
}

pub(crate) fn is_number(value: &Value) -> bool {
    matches!(
        value,
        Value::Number(_)
            | Value::Double(_)
            | Value::Int(_)
            | Value::Long(_)
            | Value::Short(_)
            | Value::Byte(_)
    )
}

/// `Number.floatValue()`.
pub(crate) fn float_value(value: &Value) -> f32 {
    match value {
        Value::Number(v) => *v,
        Value::Double(v) => *v as f32,
        Value::Int(v) => *v as f32,
        Value::Long(v) => *v as f32,
        Value::Short(v) => f32::from(*v),
        Value::Byte(v) => f32::from(*v),
        _ => 0.0,
    }
}

/// `Number.doubleValue()`.
pub(crate) fn double_value(value: &Value) -> f64 {
    match value {
        Value::Number(v) => f64::from(*v),
        Value::Double(v) => *v,
        Value::Int(v) => f64::from(*v),
        Value::Long(v) => *v as f64,
        Value::Short(v) => f64::from(*v),
        Value::Byte(v) => f64::from(*v),
        _ => 0.0,
    }
}

/// `Number.intValue()`.
fn int_value(value: &Value) -> i32 {
    match value {
        Value::Number(v) => *v as i32,
        Value::Double(v) => *v as i32,
        Value::Int(v) => *v,
        Value::Long(v) => *v as i32,
        Value::Short(v) => i32::from(*v),
        Value::Byte(v) => i32::from(*v),
        _ => 0,
    }
}

/// `Number.longValue()`.
fn long_value(value: &Value) -> i64 {
    match value {
        Value::Number(v) => *v as i64,
        Value::Double(v) => *v as i64,
        Value::Int(v) => i64::from(*v),
        Value::Long(v) => *v,
        Value::Short(v) => i64::from(*v),
        Value::Byte(v) => i64::from(*v),
        _ => 0,
    }
}

/// `Number.shortValue()` widened to `int` (Java computes short arithmetic in `int`).
fn short_value(value: &Value) -> i32 {
    match value {
        Value::Short(v) => i32::from(*v),
        Value::Byte(v) => i32::from(*v),
        Value::Long(v) => i32::from(*v as i16),
        other => i32::from(int_value(other) as i16),
    }
}

/// `Number.byteValue()` widened to `int`.
fn byte_value(value: &Value) -> i32 {
    match value {
        Value::Byte(v) => i32::from(*v),
        Value::Long(v) => i32::from(*v as i8),
        other => i32::from(int_value(other) as i8),
    }
}

/// `P4TypedAstEvaluator.toBoolean`.
pub(crate) fn to_boolean(value: &Value) -> bool {
    match value {
        Value::Boolean(b) => *b,
        Value::Null => false,
        other => java::strip(&super::java_string(other)).to_lowercase() == "true",
    }
}

/// `"true".equalsIgnoreCase(String.valueOf(value))`.
pub(crate) fn equals_true_ignore_case(value: &Value) -> bool {
    super::java_string(value).eq_ignore_ascii_case("true")
}

fn number_format(text: &str) -> EvalError {
    err(
        ErrorKind::NumberFormat,
        format!("For input string: \"{text}\""),
    )
}

/// `numberType.parseNumber(text)`.
pub(crate) fn parse_number(number_type: NumberType, text: &str) -> R {
    let parsed = match number_type {
        NumberType::Float => java::parse_float(text).map(Value::Number),
        NumberType::Double => java::parse_double(text).map(Value::Double),
        NumberType::Int => java::parse_integer(text, i32::MIN.into(), i32::MAX.into())
            .map(|v| Value::Int(v as i32)),
        NumberType::Long => java::parse_integer(text, i64::MIN, i64::MAX).map(Value::Long),
        NumberType::Short => java::parse_integer(text, i16::MIN.into(), i16::MAX.into())
            .map(|v| Value::Short(v as i16)),
        NumberType::Byte => {
            java::parse_integer(text, i8::MIN.into(), i8::MAX.into()).map(|v| Value::Byte(v as i8))
        }
    };
    parsed.ok_or_else(|| number_format(text))
}

/// `numberType.parseNumber("0")`.
pub(crate) fn zero(number_type: NumberType) -> Value {
    match number_type {
        NumberType::Float => Value::Number(0.0),
        NumberType::Double => Value::Double(0.0),
        NumberType::Int => Value::Int(0),
        NumberType::Long => Value::Long(0),
        NumberType::Short => Value::Short(0),
        NumberType::Byte => Value::Byte(0),
    }
}

/// `castToNumberType(double)`.
pub(crate) fn cast_double(number_type: NumberType, value: f64) -> Value {
    match number_type {
        NumberType::Float => Value::Number(value as f32),
        NumberType::Double => Value::Double(value),
        NumberType::Int => Value::Int(value as i32),
        NumberType::Long => Value::Long(value as i64),
        NumberType::Short => Value::Short((value as i32) as i16),
        NumberType::Byte => Value::Byte((value as i32) as i8),
    }
}

/// Arithmetic operators after parsing (`+ - * /`).
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) enum Op {
    Add,
    Sub,
    Mul,
    Div,
}

impl Op {
    pub(crate) fn parse(op: &str) -> Result<Self, EvalError> {
        Ok(match op {
            "+" => Self::Add,
            "-" => Self::Sub,
            "*" => Self::Mul,
            "/" => Self::Div,
            other => {
                return Err(err(
                    ErrorKind::IllegalArgument,
                    format!("Unsupported operator: {other}"),
                ))
            }
        })
    }
}

fn divide_by_zero() -> EvalError {
    err(ErrorKind::Arithmetic, "/ by zero")
}

/// `applyBinary(operator, left, right)` for the configured `numberType`.
pub(crate) fn apply_binary(number_type: NumberType, op: Op, left: &Value, right: &Value) -> R {
    Ok(match number_type {
        NumberType::Float => {
            let (l, r) = (float_value(left), float_value(right));
            Value::Number(match op {
                Op::Add => l + r,
                Op::Sub => l - r,
                Op::Mul => l * r,
                Op::Div => l / r,
            })
        }
        NumberType::Double => {
            let (l, r) = (double_value(left), double_value(right));
            Value::Double(match op {
                Op::Add => l + r,
                Op::Sub => l - r,
                Op::Mul => l * r,
                Op::Div => l / r,
            })
        }
        NumberType::Int => {
            let (l, r) = (int_value(left), int_value(right));
            Value::Int(match op {
                Op::Add => l.wrapping_add(r),
                Op::Sub => l.wrapping_sub(r),
                Op::Mul => l.wrapping_mul(r),
                Op::Div if r == 0 => return Err(divide_by_zero()),
                Op::Div => l.wrapping_div(r),
            })
        }
        NumberType::Long => {
            let (l, r) = (long_value(left), long_value(right));
            Value::Long(match op {
                Op::Add => l.wrapping_add(r),
                Op::Sub => l.wrapping_sub(r),
                Op::Mul => l.wrapping_mul(r),
                Op::Div if r == 0 => return Err(divide_by_zero()),
                Op::Div => l.wrapping_div(r),
            })
        }
        NumberType::Short | NumberType::Byte => {
            let (l, r) = if number_type == NumberType::Short {
                (short_value(left), short_value(right))
            } else {
                (byte_value(left), byte_value(right))
            };
            let result = match op {
                Op::Add => l.wrapping_add(r),
                Op::Sub => l.wrapping_sub(r),
                Op::Mul => l.wrapping_mul(r),
                Op::Div if r == 0 => return Err(divide_by_zero()),
                Op::Div => l.wrapping_div(r),
            };
            if number_type == NumberType::Short {
                Value::Short(result as i16)
            } else {
                Value::Byte(result as i8)
            }
        }
    })
}

/// `compareNumbers`: same-typed fast paths, otherwise `Double.compare` of the widened values.
pub(crate) fn compare_numbers(left: &Value, right: &Value) -> Ordering {
    match (left, right) {
        (Value::Number(l), Value::Number(r)) => java::float_compare(*l, *r),
        (Value::Double(l), Value::Double(r)) => java::double_compare(*l, *r),
        (Value::Int(l), Value::Int(r)) => l.cmp(r),
        (Value::Long(l), Value::Long(r)) => l.cmp(r),
        (Value::Short(l), Value::Short(r)) => l.cmp(r),
        (Value::Byte(l), Value::Byte(r)) => l.cmp(r),
        (l, r) => java::double_compare(double_value(l), double_value(r)),
    }
}

/// Comparison operators after `strip()`; unknown operators compare as `false`.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) enum Cmp {
    Eq,
    Ne,
    Lt,
    Le,
    Gt,
    Ge,
    Never,
}

impl Cmp {
    pub(crate) fn parse(op: &str) -> Self {
        match java::strip(op) {
            "==" => Self::Eq,
            "!=" => Self::Ne,
            "<" => Self::Lt,
            "<=" => Self::Le,
            ">" => Self::Gt,
            ">=" => Self::Ge,
            _ => Self::Never,
        }
    }

    pub(crate) fn test(self, ordering: Ordering) -> bool {
        match self {
            Self::Eq => ordering.is_eq(),
            Self::Ne => !ordering.is_eq(),
            Self::Lt => ordering.is_lt(),
            Self::Le => ordering.is_le(),
            Self::Gt => ordering.is_gt(),
            Self::Ge => ordering.is_ge(),
            Self::Never => false,
        }
    }
}

/// Equality operators of `BooleanEqualityExpr`/`StringComparisonExpr` (`==` / `!=`; other text
/// is `false`).
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) enum Equality {
    Eq,
    Ne,
    Never,
}

impl Equality {
    pub(crate) fn parse(op: &str) -> Self {
        match java::strip(op) {
            "==" => Self::Eq,
            "!=" => Self::Ne,
            _ => Self::Never,
        }
    }

    pub(crate) fn test<T: PartialEq>(self, left: T, right: T) -> bool {
        match self {
            Self::Eq => left == right,
            Self::Ne => left != right,
            Self::Never => false,
        }
    }
}

/// `((Number) value).doubleValue()` for math functions.
pub(crate) fn number_arg(value: &Value) -> Result<f64, EvalError> {
    match value {
        Value::Null => Err(err(
            ErrorKind::NullPointer,
            "Cannot invoke \"java.lang.Number.doubleValue()\" because the value is null",
        )),
        v if is_number(v) => Ok(double_value(v)),
        other => Err(err(
            ErrorKind::ClassCast,
            format!("{} cannot be cast to java.lang.Number", java_class(other)),
        )),
    }
}

pub(crate) fn java_class(value: &Value) -> &str {
    match value {
        Value::Number(_) => "java.lang.Float",
        Value::Double(_) => "java.lang.Double",
        Value::Int(_) => "java.lang.Integer",
        Value::Long(_) => "java.lang.Long",
        Value::Short(_) => "java.lang.Short",
        Value::Byte(_) => "java.lang.Byte",
        Value::Boolean(_) => "java.lang.Boolean",
        Value::String(_) => "java.lang.String",
        Value::Null => "null",
        Value::Object(object) => object.class_name(),
    }
}

/// `parseExpressionType` restricted to what coercion distinguishes.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) enum Declared {
    Number,
    String,
    Boolean,
    Object,
}

pub(crate) fn parse_declared(token: &str) -> Option<Declared> {
    Some(match java::strip(token).to_lowercase().as_str() {
        "number" | "float" => Declared::Number,
        "string" => Declared::String,
        "boolean" => Declared::Boolean,
        "object" => Declared::Object,
        _ => return None,
    })
}

/// `coerceToType(value, targetType)`.
pub(crate) fn coerce(value: Value, target: Declared, number_type: NumberType) -> Value {
    match (target, value) {
        (_, Value::Null) => Value::Null,
        (Declared::String, v) => Value::String(super::java_string(&v)),
        (Declared::Boolean, Value::Boolean(b)) => Value::Boolean(b),
        (Declared::Boolean, v) => Value::Boolean(equals_true_ignore_case(&v)),
        (Declared::Number, v) if is_number(&v) => cast_double(number_type, double_value(&v)),
        (Declared::Number, Value::String(text)) => {
            parse_number(number_type, java::strip(&text)).unwrap_or(Value::String(text))
        }
        (_, v) => v,
    }
}

pub(crate) fn external_error(error: ExternalError, class: &str, method: &str) -> EvalError {
    match error {
        ExternalError::NotRegistered => err(
            ErrorKind::Calculation,
            format!("class not found in CalculationContext. please set :{class}"),
        ),
        ExternalError::MethodNotFound => err(
            ErrorKind::UnsupportedOperation,
            format!("Method not found: {class}#{method}"),
        ),
        ExternalError::ClassNotFound | ExternalError::Failed(_) => err(
            ErrorKind::UnsupportedOperation,
            format!("External invocation failed: {class}#{method}"),
        ),
    }
}

/// `extractVariableName`: `$` followed by letters, digits or `_`.
pub(crate) fn extract_variable_name(raw: &str) -> Option<&str> {
    let rest = raw.strip_prefix('$')?;
    let end = rest
        .char_indices()
        .find(|(_, c)| !(c.is_alphanumeric() || *c == '_'))
        .map_or(rest.len(), |(i, _)| i);
    (end > 0).then(|| &rest[..end])
}

/// `isExactVariableReference`.
pub(crate) fn exact_variable(raw: &str) -> Option<&str> {
    let name = extract_variable_name(raw)?;
    (java::strip(raw).len() == name.len() + 1 && java::strip(raw).starts_with('$')).then_some(name)
}

/// `resolveVariableRefName`.
pub(crate) fn variable_ref_name(raw: &str) -> Option<String> {
    if let Some(name) = extract_variable_name(raw) {
        return Some(name.to_owned());
    }
    let stripped = java::strip(raw);
    if stripped.is_empty() {
        return None;
    }
    let name = if stripped.starts_with('$') {
        extract_variable_name(stripped)?
    } else {
        stripped
    };
    (!name.is_empty()).then(|| name.to_owned())
}

/// `unquoteStringLiteral`: strips one pair of matching quotes unless an unescaped quote of the
/// same kind occurs inside.
pub(crate) fn unquote(raw: &str) -> Option<&str> {
    let units: Vec<u16> = raw.encode_utf16().collect();
    if units.len() < 2 {
        return None;
    }
    let (first, last) = (units[0], units[units.len() - 1]);
    let quote = match (first, last) {
        (0x27, 0x27) | (0x22, 0x22) => first,
        _ => return None,
    };
    for i in 1..units.len() - 1 {
        if units[i] == quote && units[i - 1] != u16::from(b'\\') {
            return None;
        }
    }
    Some(&raw[1..raw.len() - 1])
}

/// `ImportDeclarationExpr`/`QualifiedNameExpr` rendering.
pub(crate) fn qualified_name(head: &str, tail: &[String]) -> String {
    if tail.is_empty() {
        head.to_owned()
    } else {
        format!("{head}.{}", tail.join("."))
    }
}

/// The alias an import registers under.
pub(crate) fn import_alias(class_name: &str, method: Option<&str>, alias: &str) -> String {
    if !java::strip(alias).is_empty() {
        return alias.to_owned();
    }
    match method {
        Some(method) => method.to_owned(),
        None => class_name
            .rsplit_once('.')
            .map_or(class_name, |(_, simple)| simple)
            .to_owned(),
    }
}

/// `Integer.valueOf(text.strip())` for a slice index; `None` for an absent/blank index.
pub(crate) fn slice_index(text: Option<String>) -> Result<Option<i32>, EvalError> {
    let Some(text) = text else { return Ok(None) };
    let stripped = java::strip(&text);
    if stripped.is_empty() {
        return Ok(None);
    }
    java::parse_integer(stripped, i32::MIN.into(), i32::MAX.into())
        .map(|v| Some(v as i32))
        .ok_or_else(|| number_format(stripped))
}

/// `evalSliceExpr` over UTF-16 code units; lone surrogates in the result become U+FFFD
/// (Rust strings cannot hold them — documented deviation, both sides compare the replaced form).
pub(crate) fn slice(value: &str, start: Option<i32>, end: Option<i32>, step: Option<i32>) -> R {
    let units: Vec<u16> = value.encode_utf16().collect();
    let len = units.len() as i64;
    let step = i64::from(step.unwrap_or(1));
    if step == 0 {
        return Err(err(ErrorKind::IllegalArgument, "slice step cannot be zero"));
    }
    let normalize = |index: i32| -> i64 {
        let mut index = i64::from(index);
        if index < 0 {
            index += len;
        }
        index.clamp(0, len)
    };
    let (start, end) = if step > 0 {
        (start.map_or(0, normalize), end.map_or(len, normalize))
    } else {
        (start.map_or(len - 1, normalize), end.map_or(-1, normalize))
    };
    let mut out = Vec::new();
    let mut index = start;
    let out_of_bounds = |index: i64| {
        err(
            ErrorKind::StringIndexOutOfBounds,
            format!("Index {index} out of bounds for length {len}"),
        )
    };
    if step > 0 {
        while index < end {
            out.push(
                *units
                    .get(index as usize)
                    .ok_or_else(|| out_of_bounds(index))?,
            );
            index += step;
        }
    } else {
        while index > end {
            if index < 0 {
                return Err(out_of_bounds(index));
            }
            out.push(
                *units
                    .get(index as usize)
                    .ok_or_else(|| out_of_bounds(index))?,
            );
            index += step;
        }
    }
    Ok(Value::String(String::from_utf16_lossy(&out)))
}

/// `EmbeddedFunction.inTimeRange`.
pub(crate) fn in_time_range(now_hour: Option<f32>, from: f32, to: f32) -> bool {
    let Some(now) = now_hour else { return false };
    if from > to {
        now >= from || now < to
    } else {
        now >= from && now < to
    }
}

/// `AbstractCalculationContext.inDayTimeRange`.
pub(crate) fn in_day_time_range(
    clock: &dyn Clock,
    base: &Context,
    from_day: DayOfWeek,
    from_hour: f32,
    to_day: DayOfWeek,
    to_hour: f32,
) -> bool {
    let Some((now_day, now_hour)) = clock.now_day_and_hour(base) else {
        return false;
    };
    let (from, to) = (from_day.value() as f32, to_day.value() as f32);
    let within_day = if from_day.value() > to_day.value() {
        now_day >= from || now_day <= to
    } else {
        now_day >= from && now_day <= to
    };
    if !within_day {
        return false;
    }
    if from_day == to_day {
        if from_hour > to_hour {
            now_hour >= from_hour || now_hour < to_hour
        } else {
            now_hour >= from_hour && now_hour < to_hour
        }
    } else if from == now_day {
        now_hour >= from_hour
    } else if to == now_day {
        now_hour < to_hour
    } else {
        true
    }
}

pub(crate) fn day_of_week(name: &str) -> Result<DayOfWeek, EvalError> {
    let name = java::strip(name);
    DayOfWeek::value_of(name).ok_or_else(|| {
        err(
            ErrorKind::IllegalArgument,
            format!("No enum constant java.time.DayOfWeek.{name}"),
        )
    })
}

/// The base context plus the calculation-local frames of `ScopedCalculationContext`.
///
/// A frame value of `Null` behaves as absent, as a Java `null` entry does.
pub(crate) struct Scope<'c> {
    pub(crate) base: &'c mut Context,
    pub(crate) frames: Vec<HashMap<String, Value>>,
}

impl Scope<'_> {
    pub(crate) fn number(&self, name: &str) -> Option<Value> {
        for frame in self.frames.iter().rev() {
            if let Some(value) = frame.get(name).filter(|v| is_number(v)) {
                return Some(value.clone());
            }
        }
        self.base.number(name).cloned()
    }

    pub(crate) fn string(&self, name: &str) -> Option<String> {
        for frame in self.frames.iter().rev() {
            if let Some(Value::String(value)) = frame.get(name) {
                return Some(value.clone());
            }
        }
        self.base.string(name).map(str::to_owned)
    }

    pub(crate) fn boolean(&self, name: &str) -> Option<bool> {
        for frame in self.frames.iter().rev() {
            if let Some(Value::Boolean(value)) = frame.get(name) {
                return Some(*value);
            }
        }
        self.base.boolean(name)
    }

    pub(crate) fn object(&self, name: &str) -> Option<Value> {
        for frame in self.frames.iter().rev() {
            if let Some(value) = frame.get(name).filter(|v| **v != Value::Null) {
                return Some(value.clone());
            }
        }
        self.base.object(name).cloned()
    }

    pub(crate) fn exists(&self, name: &str) -> bool {
        self.frames
            .iter()
            .any(|frame| frame.get(name).is_some_and(|v| *v != Value::Null))
            || self.base.exists(name)
    }

    /// `resolveVariableAny`: number, string, boolean, object.
    pub(crate) fn any(&self, name: &str) -> Value {
        if let Some(v) = self.number(name) {
            return v;
        }
        if let Some(v) = self.string(name) {
            return Value::String(v);
        }
        if let Some(v) = self.boolean(name) {
            return Value::Boolean(v);
        }
        self.object(name).unwrap_or(Value::Null)
    }

    /// `context.set(...)` from a declaration: into the innermost frame.
    pub(crate) fn set(&mut self, name: &str, value: Value) {
        match self.frames.last_mut() {
            Some(frame) => {
                frame.insert(name.to_owned(), value);
            }
            None => match value {
                Value::Null => {}
                Value::String(v) => self.base.set_string(name, v),
                Value::Boolean(v) => self.base.set_boolean(name, v),
                v if is_number(&v) => self.base.set_number(name, v),
                v => self.base.set_object(name, v),
            },
        }
    }

    /// Typed lookup for `VariableRefExpr` with an explicit type token.
    pub(crate) fn typed(&self, name: &str, declared: Declared) -> Value {
        match declared {
            Declared::Number => self.number(name),
            Declared::Boolean => self.boolean(name).map(Value::Boolean),
            Declared::String => self.string(name).map(Value::String),
            Declared::Object => self.object(name),
        }
        .unwrap_or(Value::Null)
    }

    /// Untyped `VariableRefExpr` lookup: prefer the result type's map, then any map.
    pub(crate) fn for_result(&self, name: &str, result_type: ResultType) -> Value {
        let preferred = if result_type.is_number() {
            self.number(name)
        } else if result_type == ResultType::Boolean {
            self.boolean(name).map(Value::Boolean)
        } else if result_type == ResultType::String {
            self.string(name).map(Value::String)
        } else {
            None
        };
        preferred.unwrap_or_else(|| self.any(name))
    }
}

impl Variables for Scope<'_> {
    fn number(&self, name: &str) -> Option<Value> {
        Scope::number(self, name)
    }
    fn string(&self, name: &str) -> Option<String> {
        Scope::string(self, name)
    }
    fn boolean(&self, name: &str) -> Option<bool> {
        Scope::boolean(self, name)
    }
    fn object(&self, name: &str) -> Option<Value> {
        Scope::object(self, name)
    }
    fn exists(&self, name: &str) -> bool {
        Scope::exists(self, name)
    }
}

/// `radianAngle(x)` of the base context.
pub(crate) fn radians(base: &Context, x: f64) -> f64 {
    match base.angle() {
        super::Angle::Radian => x,
        super::Angle::Degree => java::to_radians(x),
    }
}

/// `java.lang.Math` functions with one argument.
#[derive(Clone, Copy, Debug)]
pub(crate) enum Math1 {
    Sin,
    Cos,
    Tan,
    Sqrt,
    Abs,
    Round,
    Ceil,
    Floor,
    Log,
    Exp,
}

pub(crate) fn math1(function: Math1, base: &Context, x: f64) -> f64 {
    match function {
        Math1::Sin => radians(base, x).sin(),
        Math1::Cos => radians(base, x).cos(),
        Math1::Tan => radians(base, x).tan(),
        Math1::Sqrt => x.sqrt(),
        Math1::Abs => x.abs(),
        Math1::Round => java::math_round(x) as f64,
        Math1::Ceil => x.ceil(),
        Math1::Floor => x.floor(),
        Math1::Log => x.ln(),
        Math1::Exp => x.exp(),
    }
}

/// String functions of the function and dot forms.
#[derive(Clone, Copy, Debug)]
pub(crate) enum StrFn {
    Upper,
    Lower,
    Trim,
    Length,
}

pub(crate) fn str_fn(function: StrFn, value: &Value, number_type: NumberType) -> Value {
    let text = super::java_string(value);
    match function {
        StrFn::Upper => Value::String(java::to_upper(&text)),
        StrFn::Lower => Value::String(java::to_lower(&text)),
        StrFn::Trim => Value::String(java::trim(&text).to_owned()),
        StrFn::Length => cast_double(number_type, java::utf16_len(&text) as f64),
    }
}

/// String predicates.
#[derive(Clone, Copy, Debug)]
pub(crate) enum Pred {
    StartsWith,
    EndsWith,
    Contains,
    In,
}

pub(crate) fn pred(function: Pred, value: &str, pattern: &str) -> bool {
    match function {
        Pred::StartsWith => value.starts_with(pattern),
        Pred::EndsWith => value.ends_with(pattern),
        Pred::Contains => value.contains(pattern),
        Pred::In => value == pattern,
    }
}
