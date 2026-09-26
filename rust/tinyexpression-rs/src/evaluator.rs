use std::error::Error;
use std::fmt::{self, Display, Formatter};

use crate::{ParseDiagnostic, Span};

use crate::generated::ast::{Ast, AstValue};
use crate::generated::evaluator::{self as generated_evaluator, Semantics};
use crate::{parse, FrontendError};

/// A value produced by the evaluators.
///
/// The context-free [`evaluate`] only produces `Number`, `Boolean` and `String`. The contextual
/// runtime ([`crate::runtime`]) mirrors the Java boxed types a `CalculationContext` or a
/// `numberType` can carry: `Number` is `java.lang.Float`, the other numeric variants are
/// `Double`/`Integer`/`Long`/`Short`/`Byte`, `Null` is Java `null`, and `Object` is an opaque
/// host value.
#[derive(Debug, Clone, PartialEq)]
pub enum Value {
    Number(f32),
    Boolean(bool),
    String(String),
    Double(f64),
    Int(i32),
    Long(i64),
    Short(i16),
    Byte(i8),
    Null,
    Object(crate::runtime::HostObject),
}

impl Value {
    pub fn number(&self) -> Option<f32> {
        match self {
            Self::Number(value) => Some(*value),
            _ => None,
        }
    }

    pub fn boolean(&self) -> Option<bool> {
        match self {
            Self::Boolean(value) => Some(*value),
            _ => None,
        }
    }

    pub fn string(&self) -> Option<&str> {
        match self {
            Self::String(value) => Some(value),
            _ => None,
        }
    }

    /// Returns the contained number.
    ///
    /// This compatibility accessor predates the scalar variants. New callers that may receive
    /// more than one value kind should use [`Value::number`] instead.
    pub fn as_f32(&self) -> f32 {
        self.number().expect("Value is not a number")
    }

    pub fn number_bits(&self) -> Option<u32> {
        self.number().map(f32::to_bits)
    }

    /// Returns the raw bits of the contained number.
    ///
    /// Like [`Value::as_f32`], this compatibility accessor panics for non-number variants. Use
    /// [`Value::number_bits`] when the value kind is not already known.
    pub fn f32_bits(&self) -> u32 {
        self.number_bits().expect("Value is not a number")
    }

    pub fn canonical_json(&self) -> String {
        match self {
            Self::Number(value) => format!(
                "{{\"kind\":\"number\",\"value\":{},\"f32Bits\":\"0x{:08x}\"}}",
                crate::json_string(&value.to_string()),
                value.to_bits()
            ),
            Self::Boolean(value) => {
                format!("{{\"kind\":\"boolean\",\"value\":{value}}}")
            }
            Self::String(value) => format!(
                "{{\"kind\":\"string\",\"value\":{}}}",
                crate::json_string(value)
            ),
            Self::Double(value) => format!(
                "{{\"kind\":\"double\",\"value\":{},\"f64Bits\":\"0x{:016x}\"}}",
                crate::json_string(&crate::runtime::java::double_to_string(*value)),
                value.to_bits()
            ),
            Self::Int(value) => format!("{{\"kind\":\"int\",\"value\":{value}}}"),
            Self::Long(value) => format!("{{\"kind\":\"long\",\"value\":{value}}}"),
            Self::Short(value) => format!("{{\"kind\":\"short\",\"value\":{value}}}"),
            Self::Byte(value) => format!("{{\"kind\":\"byte\",\"value\":{value}}}"),
            Self::Null => "{\"kind\":\"null\"}".to_owned(),
            Self::Object(object) => format!(
                "{{\"kind\":\"object\",\"class\":{}}}",
                crate::json_string(object.class_name())
            ),
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum EvaluationError {
    Parse(ParseDiagnostic),
    Mapping(String),
    ContextRequired {
        feature: &'static str,
        span: Span,
    },
    UnsupportedNode {
        node: String,
        span: Span,
    },
    InvalidNumber {
        literal: String,
        span: Span,
    },
    InvalidBinaryShape {
        operators: usize,
        operands: usize,
        span: Span,
    },
    UnsupportedOperator {
        operator: String,
        span: Span,
    },
    TypeMismatch {
        expected: &'static str,
        actual: &'static str,
        span: Span,
    },
}

impl EvaluationError {
    pub fn kind(&self) -> &'static str {
        match self {
            Self::Parse(_) => "parse",
            Self::Mapping(_) => "mapping",
            Self::ContextRequired { .. } => "context_required",
            Self::UnsupportedNode { .. } => "unsupported_node",
            Self::InvalidNumber { .. } => "invalid_number",
            Self::InvalidBinaryShape { .. } => "invalid_binary_shape",
            Self::UnsupportedOperator { .. } => "unsupported_operator",
            Self::TypeMismatch { .. } => "type_mismatch",
        }
    }

    pub fn span(&self) -> Option<Span> {
        match self {
            Self::Parse(_) | Self::Mapping(_) => None,
            Self::ContextRequired { span, .. }
            | Self::UnsupportedNode { span, .. }
            | Self::InvalidNumber { span, .. }
            | Self::InvalidBinaryShape { span, .. }
            | Self::UnsupportedOperator { span, .. }
            | Self::TypeMismatch { span, .. } => Some(*span),
        }
    }

    pub fn canonical_json(&self) -> String {
        let span = self
            .span()
            .map(|span| format!("[{},{}]", span.start, span.end))
            .unwrap_or_else(|| "null".to_owned());
        let details = match self {
            Self::ContextRequired { feature, .. } => {
                format!(",\"feature\":{}", crate::json_string(feature))
            }
            Self::UnsupportedNode { node, .. } => {
                format!(",\"node\":{}", crate::json_string(node))
            }
            Self::InvalidNumber { literal, .. } => {
                format!(",\"literal\":{}", crate::json_string(literal))
            }
            Self::InvalidBinaryShape {
                operators,
                operands,
                ..
            } => format!(",\"operators\":{operators},\"rightOperands\":{operands}"),
            Self::UnsupportedOperator { operator, .. } => {
                format!(",\"operator\":{}", crate::json_string(operator))
            }
            Self::TypeMismatch {
                expected, actual, ..
            } => format!(
                ",\"expected\":{},\"actual\":{}",
                crate::json_string(expected),
                crate::json_string(actual)
            ),
            Self::Parse(_) | Self::Mapping(_) => String::new(),
        };
        format!(
            "{{\"kind\":{},\"span\":{}{},\"message\":{}}}",
            crate::json_string(self.kind()),
            span,
            details,
            crate::json_string(&self.to_string())
        )
    }
}

impl Display for EvaluationError {
    fn fmt(&self, formatter: &mut Formatter<'_>) -> fmt::Result {
        match self {
            Self::Parse(error) => Display::fmt(error, formatter),
            Self::Mapping(error) => write!(formatter, "AST mapping failed: {error}"),
            Self::ContextRequired { feature, span } => write!(
                formatter,
                "context-free evaluator does not support {feature} at {}..{}",
                span.start, span.end
            ),
            Self::UnsupportedNode { node, span } => write!(
                formatter,
                "context-free evaluator does not support {node} at {}..{}",
                span.start, span.end
            ),
            Self::InvalidNumber { literal, span } => write!(
                formatter,
                "invalid f32 literal {literal:?} at {}..{}",
                span.start, span.end
            ),
            Self::InvalidBinaryShape {
                operators,
                operands,
                span,
            } => write!(
                formatter,
                "binary expression has {operators} operators and {operands} right operands at {}..{}",
                span.start, span.end
            ),
            Self::UnsupportedOperator { operator, span } => write!(
                formatter,
                "unsupported numeric operator {operator:?} at {}..{}",
                span.start, span.end
            ),
            Self::TypeMismatch {
                expected,
                actual,
                span,
            } => write!(
                formatter,
                "expected {expected}, found {actual} at {}..{}",
                span.start, span.end
            ),
        }
    }
}

impl Error for EvaluationError {}

impl From<FrontendError> for EvaluationError {
    fn from(error: FrontendError) -> Self {
        match error {
            FrontendError::Parse(error) => Self::Parse(error),
            FrontendError::Mapping(error) => Self::Mapping(error),
            FrontendError::TypeMismatch { span, .. } => Self::TypeMismatch {
                expected: "one match result family",
                actual: "mixed explicit type hints",
                span,
            },
        }
    }
}

/// Parses and evaluates the context-free scalar subset.
///
/// This function has no Java or alternate evaluator fallback. Unsupported typed AST nodes
/// return [`EvaluationError::UnsupportedNode`] instead of being interpreted by another path.
pub fn evaluate(source: &str) -> Result<Value, EvaluationError> {
    let ast = parse(source)?;
    evaluate_ast(&ast)
}

/// Evaluates an already-owned generated AST with the context-free scalar semantics.
pub fn evaluate_ast(ast: &Ast) -> Result<Value, EvaluationError> {
    generated_evaluator::evaluate(ast, &mut ScalarSemantics)
}

struct ScalarSemantics;

impl ScalarSemantics {
    fn evaluate_value(&mut self, value: &AstValue) -> Result<Value, EvaluationError> {
        match value {
            AstValue::Text { text, span } => {
                let trimmed = text.trim();
                if trimmed.eq_ignore_ascii_case("true") {
                    return Ok(Value::Boolean(true));
                }
                if trimmed.eq_ignore_ascii_case("false") {
                    return Ok(Value::Boolean(false));
                }
                if let Some(value) = Self::unquote_string_literal(trimmed) {
                    return Ok(Value::String(value.to_owned()));
                }
                trimmed.parse::<f32>().map(Value::Number).map_err(|_| {
                    EvaluationError::InvalidNumber {
                        literal: text.clone(),
                        span: *span,
                    }
                })
            }
            AstValue::Node(node) => generated_evaluator::evaluate(node, self),
        }
    }

    fn evaluate_string_value(&mut self, value: &AstValue) -> Result<String, EvaluationError> {
        match value {
            // STRING captures are normalized by the mapper and no longer include delimiters.
            AstValue::Text { text, .. } => Ok(text.clone()),
            AstValue::Node(node) => {
                let value = generated_evaluator::evaluate(node, self)?;
                Ok(Self::java_string(value))
            }
        }
    }

    fn kind(value: &Value) -> &'static str {
        match value {
            Value::Number(_) => "number",
            Value::Boolean(_) => "boolean",
            Value::String(_) => "string",
            // The context-free evaluator never produces the contextual runtime's values.
            _ => "object",
        }
    }

    fn require_number(value: Value, span: Span) -> Result<f32, EvaluationError> {
        value.number().ok_or_else(|| EvaluationError::TypeMismatch {
            expected: "number",
            actual: Self::kind(&value),
            span,
        })
    }

    fn to_boolean(value: &Value) -> bool {
        match value {
            Value::Boolean(value) => *value,
            Value::Number(value) => value.to_string().trim().eq_ignore_ascii_case("true"),
            Value::String(value) => value.trim().eq_ignore_ascii_case("true"),
            _ => false,
        }
    }

    fn java_string(value: Value) -> String {
        match value {
            Value::Number(value) => Self::java_float_string(value),
            Value::Boolean(value) => value.to_string(),
            Value::String(value) => value,
            other => crate::runtime::java_string(&other),
        }
    }

    /// Formats an f32 with the spelling contract of Java 21 `Float.toString`.
    fn java_float_string(value: f32) -> String {
        if value.is_nan() {
            return "NaN".to_owned();
        }
        if value == f32::INFINITY {
            return "Infinity".to_owned();
        }
        if value == f32::NEG_INFINITY {
            return "-Infinity".to_owned();
        }

        let absolute = value.abs();
        if absolute == 0.0 {
            return if value.is_sign_negative() {
                "-0.0".to_owned()
            } else {
                "0.0".to_owned()
            };
        }

        // Java requires at least two significant digits and chooses the closest shortest decimal
        // that round-trips to the same float. Rust intentionally permits a shorter spelling for
        // values such as Float.MIN_VALUE (`1e-45`), while Java emits `1.4E-45`.
        let (digits, exponent) = (2..=9)
            .find_map(|significant_digits| {
                let scientific = format!("{:.*e}", significant_digits - 1, f64::from(absolute));
                let parsed = scientific.parse::<f32>().ok()?;
                if parsed.to_bits() != absolute.to_bits() {
                    return None;
                }
                let (mantissa, exponent) = scientific.split_once('e')?;
                Some((mantissa.replace('.', ""), exponent.parse::<i32>().ok()?))
            })
            .expect("every finite f32 has a round-tripping decimal with at most 9 digits");
        let sign = if value.is_sign_negative() { "-" } else { "" };

        if !(1.0e-3..1.0e7).contains(&absolute) {
            let (first, rest) = digits.split_at(1);
            return format!("{sign}{first}.{rest}E{exponent}");
        }

        let decimal_position = exponent + 1;
        let mut plain = if decimal_position <= 0 {
            format!("0.{}{}", "0".repeat((-decimal_position) as usize), digits)
        } else if decimal_position as usize >= digits.len() {
            format!(
                "{}{}.0",
                digits,
                "0".repeat(decimal_position as usize - digits.len())
            )
        } else {
            let (integer, fraction) = digits.split_at(decimal_position as usize);
            format!("{integer}.{fraction}")
        };
        if plain.contains('.') {
            while plain.ends_with('0') {
                plain.pop();
            }
            if plain.ends_with('.') {
                plain.push('0');
            }
        }
        format!("{sign}{plain}")
    }

    fn unquote_string_literal(raw: &str) -> Option<&str> {
        if raw.len() < 2 {
            return None;
        }
        let quote = raw.as_bytes()[0];
        if !matches!(quote, b'\'' | b'"') || raw.as_bytes()[raw.len() - 1] != quote {
            return None;
        }
        let inner = &raw[1..raw.len() - 1];
        let mut escaped = false;
        for byte in inner.bytes() {
            if byte == quote && !escaped {
                return None;
            }
            escaped = byte == b'\\' && !escaped;
            if byte != b'\\' {
                escaped = false;
            }
        }
        Some(inner)
    }

    fn validate_chain(op: &[String], right_len: usize, span: Span) -> Result<(), EvaluationError> {
        if op.len() == right_len {
            Ok(())
        } else {
            Err(EvaluationError::InvalidBinaryShape {
                operators: op.len(),
                operands: right_len,
                span,
            })
        }
    }

    /// Java `Float.compare` ordering, including canonical NaN and signed zero handling.
    fn compare_f32(left: f32, right: f32) -> std::cmp::Ordering {
        if left < right {
            return std::cmp::Ordering::Less;
        }
        if left > right {
            return std::cmp::Ordering::Greater;
        }
        let left_bits = if left.is_nan() {
            0x7fc0_0000
        } else {
            left.to_bits()
        } as i32;
        let right_bits = if right.is_nan() {
            0x7fc0_0000
        } else {
            right.to_bits()
        } as i32;
        left_bits.cmp(&right_bits)
    }

    fn evaluate_match(
        &mut self,
        first_case: &Ast,
        more_cases: &[Ast],
        default_case: &Ast,
    ) -> Result<Value, EvaluationError> {
        for case in std::iter::once(first_case).chain(more_cases) {
            let (condition, value) = match case {
                Ast::r#NumberCaseExpr {
                    r#condition,
                    r#value,
                    ..
                }
                | Ast::r#StringCaseExpr {
                    r#condition,
                    r#value,
                    ..
                }
                | Ast::r#BooleanCaseExpr {
                    r#condition,
                    r#value,
                    ..
                } => (condition.as_ref(), value.as_ref()),
                _ => return Self::unsupported("match_case", case.span()),
            };
            let condition = generated_evaluator::evaluate(condition, self)?;
            if Self::to_boolean(&condition) {
                return generated_evaluator::evaluate(value, self);
            }
        }
        generated_evaluator::evaluate(default_case, self)
    }

    fn unsupported(method: &'static str, span: Span) -> Result<Value, EvaluationError> {
        let semantic_name = method
            .strip_prefix("eval_")
            .and_then(|name| name.strip_suffix("_expr"))
            .unwrap_or(method);
        let mut node = String::new();
        for word in semantic_name.split('_') {
            let mut characters = word.chars();
            if let Some(first) = characters.next() {
                node.extend(first.to_uppercase());
                node.extend(characters);
            }
        }
        node.push_str("Expr");
        Err(EvaluationError::UnsupportedNode { node, span })
    }
}

macro_rules! unsupported_semantics {
    ($(fn $name:ident($($argument:ident: $argument_type:ty),*);)*) => {
        $(
            fn $name(
                &mut self,
                $($argument: $argument_type,)*
                span: Span,
            ) -> Self::Output {
                $(let _ = $argument;)*
                Self::unsupported(stringify!($name), span)
            }
        )*
    };
}

#[allow(non_snake_case)]
impl Semantics for ScalarSemantics {
    type Output = Result<Value, EvaluationError>;

    fn eval_formula_expr(
        &mut self,
        imports: &[Ast],
        declarations: &[Ast],
        expression: &Ast,
        methods: &[Ast],
        span: Span,
    ) -> Self::Output {
        let feature = if !imports.is_empty() {
            Some("imports")
        } else if !declarations.is_empty() {
            Some("declarations")
        } else if !methods.is_empty() {
            Some("methods")
        } else {
            None
        };
        if let Some(feature) = feature {
            return Err(EvaluationError::ContextRequired { feature, span });
        }
        generated_evaluator::evaluate(expression, self)
    }

    fn eval_binary_expr(
        &mut self,
        left: &AstValue,
        op: &[String],
        right: &[AstValue],
        span: Span,
    ) -> Self::Output {
        if op.len() != right.len() {
            return Err(EvaluationError::InvalidBinaryShape {
                operators: op.len(),
                operands: right.len(),
                span,
            });
        }

        // The P4 root grammar may wrap a control-flow expression in the numeric precedence
        // ladder before its selected scalar kind is known. Preserve a lone leaf here; numeric
        // parents and comparisons still enforce number at their own boundary.
        if op.is_empty() {
            return self.evaluate_value(left);
        }

        let mut result = Self::require_number(self.evaluate_value(left)?, left.span())?;
        for (operator, operand) in op.iter().zip(right) {
            let operand = Self::require_number(self.evaluate_value(operand)?, operand.span())?;
            result = match operator.as_str() {
                "+" => result + operand,
                "-" => result - operand,
                "*" => result * operand,
                "/" => result / operand,
                _ => {
                    return Err(EvaluationError::UnsupportedOperator {
                        operator: operator.clone(),
                        span,
                    });
                }
            };
        }
        Ok(Value::Number(result))
    }

    fn eval_expression_expr(&mut self, value: &Ast, _span: Span) -> Self::Output {
        generated_evaluator::evaluate(value, self)
    }

    fn eval_argument_expression_expr(&mut self, value: &Ast, _span: Span) -> Self::Output {
        generated_evaluator::evaluate(value, self)
    }

    fn eval_ternary_expr(
        &mut self,
        condition: &Ast,
        thenExpr: &Ast,
        elseExpr: &Ast,
        _span: Span,
    ) -> Self::Output {
        let condition = generated_evaluator::evaluate(condition, self)?;
        generated_evaluator::evaluate(
            if Self::to_boolean(&condition) {
                thenExpr
            } else {
                elseExpr
            },
            self,
        )
    }

    fn eval_string_concat_expr(
        &mut self,
        left: &AstValue,
        op: &[String],
        right: &[AstValue],
        span: Span,
    ) -> Self::Output {
        Self::validate_chain(op, right.len(), span)?;
        let mut result = self.evaluate_string_value(left)?;
        for (operator, operand) in op.iter().zip(right) {
            if operator != "+" {
                return Err(EvaluationError::UnsupportedOperator {
                    operator: operator.clone(),
                    span,
                });
            }
            result.push_str(&self.evaluate_string_value(operand)?);
        }
        Ok(Value::String(result))
    }

    fn eval_boolean_or_expr(
        &mut self,
        left: &Ast,
        op: &[String],
        right: &[Ast],
        span: Span,
    ) -> Self::Output {
        Self::validate_chain(op, right.len(), span)?;
        let mut result = Self::to_boolean(&generated_evaluator::evaluate(left, self)?);
        for (operator, operand) in op.iter().zip(right) {
            let operand = Self::to_boolean(&generated_evaluator::evaluate(operand, self)?);
            match operator.as_str() {
                "|" => result |= operand,
                _ => {
                    return Err(EvaluationError::UnsupportedOperator {
                        operator: operator.clone(),
                        span,
                    });
                }
            }
        }
        Ok(Value::Boolean(result))
    }

    fn eval_boolean_and_expr(
        &mut self,
        left: &Ast,
        op: &[String],
        right: &[Ast],
        span: Span,
    ) -> Self::Output {
        Self::validate_chain(op, right.len(), span)?;
        let mut result = Self::to_boolean(&generated_evaluator::evaluate(left, self)?);
        for (operator, operand) in op.iter().zip(right) {
            let operand = Self::to_boolean(&generated_evaluator::evaluate(operand, self)?);
            match operator.as_str() {
                "&" => result &= operand,
                _ => {
                    return Err(EvaluationError::UnsupportedOperator {
                        operator: operator.clone(),
                        span,
                    });
                }
            }
        }
        Ok(Value::Boolean(result))
    }

    fn eval_boolean_xor_expr(
        &mut self,
        left: &Ast,
        op: &[String],
        right: &[Ast],
        span: Span,
    ) -> Self::Output {
        Self::validate_chain(op, right.len(), span)?;
        let mut result = Self::to_boolean(&generated_evaluator::evaluate(left, self)?);
        for (operator, operand) in op.iter().zip(right) {
            let operand = Self::to_boolean(&generated_evaluator::evaluate(operand, self)?);
            match operator.as_str() {
                "^" => result ^= operand,
                _ => {
                    return Err(EvaluationError::UnsupportedOperator {
                        operator: operator.clone(),
                        span,
                    });
                }
            }
        }
        Ok(Value::Boolean(result))
    }

    fn eval_not_expr(&mut self, value: &Ast, _span: Span) -> Self::Output {
        Ok(Value::Boolean(!Self::to_boolean(
            &generated_evaluator::evaluate(value, self)?,
        )))
    }

    fn eval_boolean_equality_expr(
        &mut self,
        left: &AstValue,
        op: &str,
        right: &AstValue,
        span: Span,
    ) -> Self::Output {
        let left = Self::to_boolean(&self.evaluate_value(left)?);
        let right = Self::to_boolean(&self.evaluate_value(right)?);
        let result = match op.trim() {
            "==" => left == right,
            "!=" => left != right,
            _ => {
                return Err(EvaluationError::UnsupportedOperator {
                    operator: op.to_owned(),
                    span,
                });
            }
        };
        Ok(Value::Boolean(result))
    }

    fn eval_boolean_factor_expr(&mut self, value: &AstValue, _span: Span) -> Self::Output {
        let value = self.evaluate_value(value)?;
        Ok(Value::Boolean(Self::to_boolean(&value)))
    }

    fn eval_string_comparison_expr(
        &mut self,
        left: &Ast,
        op: &str,
        right: &Ast,
        span: Span,
    ) -> Self::Output {
        let left = Self::java_string(generated_evaluator::evaluate(left, self)?);
        let right = Self::java_string(generated_evaluator::evaluate(right, self)?);
        let result = match op.trim() {
            "==" => left == right,
            "!=" => left != right,
            _ => {
                return Err(EvaluationError::UnsupportedOperator {
                    operator: op.to_owned(),
                    span,
                });
            }
        };
        Ok(Value::Boolean(result))
    }

    fn eval_comparison_expr(
        &mut self,
        left: &Ast,
        op: &str,
        right: &Ast,
        span: Span,
    ) -> Self::Output {
        let left_value = generated_evaluator::evaluate(left, self)?;
        let left = Self::require_number(left_value, left.span())?;
        let right_value = generated_evaluator::evaluate(right, self)?;
        let right = Self::require_number(right_value, right.span())?;
        let ordering = Self::compare_f32(left, right);
        let result = match op.trim() {
            "==" => ordering.is_eq(),
            "!=" => !ordering.is_eq(),
            "<" => ordering.is_lt(),
            "<=" => ordering.is_le(),
            ">" => ordering.is_gt(),
            ">=" => ordering.is_ge(),
            _ => {
                return Err(EvaluationError::UnsupportedOperator {
                    operator: op.to_owned(),
                    span,
                });
            }
        };
        Ok(Value::Boolean(result))
    }

    fn eval_if_expr(
        &mut self,
        condition: &Ast,
        thenExpr: &Ast,
        elseExpr: &Ast,
        _span: Span,
    ) -> Self::Output {
        let condition = generated_evaluator::evaluate(condition, self)?;
        generated_evaluator::evaluate(
            if Self::to_boolean(&condition) {
                thenExpr
            } else {
                elseExpr
            },
            self,
        )
    }

    fn eval_branch_expression_expr(&mut self, value: &Ast, _span: Span) -> Self::Output {
        generated_evaluator::evaluate(value, self)
    }

    fn eval_number_match_expr(
        &mut self,
        firstCase: &Ast,
        moreCases: &[Ast],
        defaultCase: &Ast,
        _span: Span,
    ) -> Self::Output {
        self.evaluate_match(firstCase, moreCases, defaultCase)
    }

    fn eval_string_match_expr(
        &mut self,
        firstCase: &Ast,
        moreCases: &[Ast],
        defaultCase: &Ast,
        _span: Span,
    ) -> Self::Output {
        self.evaluate_match(firstCase, moreCases, defaultCase)
    }

    fn eval_boolean_match_expr(
        &mut self,
        firstCase: &Ast,
        moreCases: &[Ast],
        defaultCase: &Ast,
        _span: Span,
    ) -> Self::Output {
        self.evaluate_match(firstCase, moreCases, defaultCase)
    }

    fn eval_number_case_expr(
        &mut self,
        _condition: &Ast,
        value: &Ast,
        _span: Span,
    ) -> Self::Output {
        generated_evaluator::evaluate(value, self)
    }

    fn eval_number_default_case_expr(&mut self, value: &Ast, _span: Span) -> Self::Output {
        generated_evaluator::evaluate(value, self)
    }

    fn eval_number_case_value_expr(&mut self, value: &Ast, span: Span) -> Self::Output {
        let value = generated_evaluator::evaluate(value, self)?;
        Ok(Value::Number(Self::require_number(value, span)?))
    }

    fn eval_string_case_expr(
        &mut self,
        _condition: &Ast,
        value: &Ast,
        _span: Span,
    ) -> Self::Output {
        generated_evaluator::evaluate(value, self)
    }

    fn eval_string_default_case_expr(&mut self, value: &Ast, _span: Span) -> Self::Output {
        generated_evaluator::evaluate(value, self)
    }

    fn eval_string_case_value_expr(&mut self, value: &Ast, _span: Span) -> Self::Output {
        let value = generated_evaluator::evaluate(value, self)?;
        Ok(Value::String(Self::java_string(value)))
    }

    fn eval_boolean_case_expr(
        &mut self,
        _condition: &Ast,
        value: &Ast,
        _span: Span,
    ) -> Self::Output {
        generated_evaluator::evaluate(value, self)
    }

    fn eval_boolean_default_case_expr(&mut self, value: &Ast, _span: Span) -> Self::Output {
        generated_evaluator::evaluate(value, self)
    }

    fn eval_boolean_case_value_expr(&mut self, value: &Ast, _span: Span) -> Self::Output {
        let value = generated_evaluator::evaluate(value, self)?;
        Ok(Value::Boolean(Self::to_boolean(&value)))
    }

    /// A ```` ```java:Class ```` block only declares a class (issue #216): nothing to evaluate,
    /// and it is never compiled or run here. (The parser keeps blocks out of `FormulaExpr`, so
    /// this is reached only by a hand-built AST.)
    fn eval_code_block_expr(&mut self, _span: Span) -> Self::Output {
        Ok(Value::Null)
    }

    unsupported_semantics! {
        fn eval_import_declaration_expr(className: &Ast, method: Option<&str>, alias: &str);
        fn eval_qualified_name_expr(head: &str, tail: &[String]);
        fn eval_number_variable_declaration_expr(varName: &str, onlyIfAbsent: Option<&Ast>, value: Option<&Ast>, desc: Option<&str>);
        fn eval_string_variable_declaration_expr(varName: &str, onlyIfAbsent: Option<&Ast>, value: Option<&Ast>, desc: Option<&str>);
        fn eval_boolean_variable_declaration_expr(varName: &str, onlyIfAbsent: Option<&Ast>, value: Option<&Ast>, desc: Option<&str>);
        fn eval_object_variable_declaration_expr(varName: &str, onlyIfAbsent: Option<&Ast>, value: Option<&Ast>, desc: Option<&str>);
        fn eval_only_if_absent_expr();
        fn eval_number_method_declaration_expr(methodName: &str, parameters: Option<&Ast>, expression: &Ast);
        fn eval_string_method_declaration_expr(methodName: &str, parameters: Option<&Ast>, expression: &Ast);
        fn eval_boolean_method_declaration_expr(methodName: &str, parameters: Option<&Ast>, expression: &Ast);
        fn eval_object_method_declaration_expr(methodName: &str, parameters: Option<&Ast>, expression: &Ast);
        fn eval_method_parameters_expr(values: &[Ast]);
        fn eval_method_parameter_expr(paramName: &str, r#type: Option<&str>);
        fn eval_external_boolean_invocation_expr(className: Option<&Ast>, name: &str, args: Option<&Ast>);
        fn eval_external_number_invocation_expr(className: Option<&Ast>, name: &str, args: Option<&Ast>);
        fn eval_external_string_invocation_expr(className: Option<&Ast>, name: &str, args: Option<&Ast>);
        fn eval_external_object_invocation_expr(className: Option<&Ast>, name: &str, args: Option<&Ast>);
        fn eval_method_invocation_expr(name: &str, args: Option<&Ast>);
        fn eval_arguments_expr(values: &[Ast]);
        fn eval_sin_expr(arg: &Ast);
        fn eval_cos_expr(arg: &Ast);
        fn eval_tan_expr(arg: &Ast);
        fn eval_sqrt_expr(arg: &Ast);
        fn eval_min_expr(first: &Ast, rest: &[Ast]);
        fn eval_max_expr(first: &Ast, rest: &[Ast]);
        fn eval_random_expr();
        fn eval_abs_expr(arg: &Ast);
        fn eval_round_expr(arg: &Ast);
        fn eval_ceil_expr(arg: &Ast);
        fn eval_floor_expr(arg: &Ast);
        fn eval_pow_expr(base: &Ast, exponent: &Ast);
        fn eval_log_expr(arg: &Ast);
        fn eval_exp_expr(arg: &Ast);
        fn eval_to_num_expr(value: &Ast, defaultValue: &Ast);
        fn eval_to_upper_case_expr(value: &Ast);
        fn eval_to_lower_case_expr(value: &Ast);
        fn eval_trim_expr(value: &Ast);
        fn eval_length_expr(value: &Ast);
        fn eval_to_upper_case_dot_expr(value: &Ast);
        fn eval_to_lower_case_dot_expr(value: &Ast);
        fn eval_trim_dot_expr(value: &Ast);
        fn eval_length_dot_expr(value: &Ast);
        fn eval_starts_with_expr(value: &Ast, patterns: &[Ast]);
        fn eval_ends_with_expr(value: &Ast, patterns: &[Ast]);
        fn eval_contains_expr(value: &Ast, patterns: &[Ast]);
        fn eval_in_expr(value: &Ast, candidates: &[Ast]);
        fn eval_starts_with_dot_expr(value: &Ast, patterns: &[Ast]);
        fn eval_ends_with_dot_expr(value: &Ast, patterns: &[Ast]);
        fn eval_contains_dot_expr(value: &Ast, patterns: &[Ast]);
        fn eval_is_present_expr(value: &Ast);
        fn eval_in_time_range_expr(startHour: &Ast, endHour: &Ast);
        fn eval_in_day_time_range_expr(startDay: &str, startHour: &Ast, endDay: &str, endHour: &Ast);
        fn eval_slice_expr(value: &AstValue, start: Option<&Ast>, end: Option<&Ast>, step: Option<&Ast>);
        fn eval_string_cast_variable_ref_expr(name: &str);
        fn eval_string_typed_variable_ref_expr(name: &str);
        fn eval_object_expr(value: &Ast);
        fn eval_variable_ref_expr(name: &str, r#type: Option<&str>);
    }
}
