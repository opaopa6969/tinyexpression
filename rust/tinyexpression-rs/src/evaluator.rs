use std::error::Error;
use std::fmt::{self, Display, Formatter};

use unlaxer_runtime::{ParseDiagnostic, Span};

use crate::generated::ast::{Ast, AstValue};
use crate::generated::evaluator::{self as generated_evaluator, Semantics};
use crate::{parse, FrontendError};

/// A value produced by the context-free native evaluator.
#[derive(Debug, Clone, Copy, PartialEq)]
pub enum Value {
    Number(f32),
}

impl Value {
    pub fn as_f32(self) -> f32 {
        match self {
            Self::Number(value) => value,
        }
    }

    pub fn f32_bits(self) -> u32 {
        self.as_f32().to_bits()
    }

    pub fn canonical_json(self) -> String {
        format!(
            "{{\"kind\":\"number\",\"value\":{},\"f32Bits\":\"0x{:08x}\"}}",
            unlaxer_runtime::json_string(&self.as_f32().to_string()),
            self.f32_bits()
        )
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
        }
    }

    pub fn span(&self) -> Option<Span> {
        match self {
            Self::Parse(_) | Self::Mapping(_) => None,
            Self::ContextRequired { span, .. }
            | Self::UnsupportedNode { span, .. }
            | Self::InvalidNumber { span, .. }
            | Self::InvalidBinaryShape { span, .. }
            | Self::UnsupportedOperator { span, .. } => Some(*span),
        }
    }

    pub fn canonical_json(&self) -> String {
        let span = self
            .span()
            .map(|span| format!("[{},{}]", span.start, span.end))
            .unwrap_or_else(|| "null".to_owned());
        let details = match self {
            Self::ContextRequired { feature, .. } => {
                format!(",\"feature\":{}", unlaxer_runtime::json_string(feature))
            }
            Self::UnsupportedNode { node, .. } => {
                format!(",\"node\":{}", unlaxer_runtime::json_string(node))
            }
            Self::InvalidNumber { literal, .. } => {
                format!(",\"literal\":{}", unlaxer_runtime::json_string(literal))
            }
            Self::InvalidBinaryShape {
                operators,
                operands,
                ..
            } => format!(",\"operators\":{operators},\"rightOperands\":{operands}"),
            Self::UnsupportedOperator { operator, .. } => {
                format!(",\"operator\":{}", unlaxer_runtime::json_string(operator))
            }
            Self::Parse(_) | Self::Mapping(_) => String::new(),
        };
        format!(
            "{{\"kind\":{},\"span\":{}{},\"message\":{}}}",
            unlaxer_runtime::json_string(self.kind()),
            span,
            details,
            unlaxer_runtime::json_string(&self.to_string())
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
        }
    }
}

impl Error for EvaluationError {}

impl From<FrontendError> for EvaluationError {
    fn from(error: FrontendError) -> Self {
        match error {
            FrontendError::Parse(error) => Self::Parse(error),
            FrontendError::Mapping(error) => Self::Mapping(error),
        }
    }
}

/// Parses and evaluates the context-free f32 arithmetic subset.
///
/// This function has no Java or alternate evaluator fallback. Unsupported typed AST nodes
/// return [`EvaluationError::UnsupportedNode`] instead of being interpreted by another path.
pub fn evaluate(source: &str) -> Result<Value, EvaluationError> {
    let ast = parse(source)?;
    evaluate_ast(&ast)
}

/// Evaluates an already-owned generated AST with the context-free f32 semantics.
pub fn evaluate_ast(ast: &Ast) -> Result<Value, EvaluationError> {
    generated_evaluator::evaluate(ast, &mut NumericSemantics)
}

struct NumericSemantics;

impl NumericSemantics {
    fn evaluate_value(&mut self, value: &AstValue) -> Result<Value, EvaluationError> {
        match value {
            AstValue::Text { text, span } => {
                text.trim().parse::<f32>().map(Value::Number).map_err(|_| {
                    EvaluationError::InvalidNumber {
                        literal: text.clone(),
                        span: *span,
                    }
                })
            }
            AstValue::Node(node) => generated_evaluator::evaluate(node, self),
        }
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
impl Semantics for NumericSemantics {
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

        let mut result = self.evaluate_value(left)?.as_f32();
        for (operator, operand) in op.iter().zip(right) {
            let operand = self.evaluate_value(operand)?.as_f32();
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

    unsupported_semantics! {
        fn eval_code_block_expr();
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
        fn eval_ternary_expr(condition: &Ast, thenExpr: &Ast, elseExpr: &Ast);
        fn eval_argument_expression_expr(value: &Ast);
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
        fn eval_string_concat_expr(left: &AstValue, op: &[String], right: &[AstValue]);
        fn eval_string_cast_variable_ref_expr(name: &str);
        fn eval_string_typed_variable_ref_expr(name: &str);
        fn eval_boolean_or_expr(left: &Ast, op: &[String], right: &[Ast]);
        fn eval_boolean_and_expr(left: &Ast, op: &[String], right: &[Ast]);
        fn eval_boolean_xor_expr(left: &Ast, op: &[String], right: &[Ast]);
        fn eval_not_expr(value: &Ast);
        fn eval_boolean_equality_expr(left: &AstValue, op: &str, right: &AstValue);
        fn eval_boolean_factor_expr(value: &AstValue);
        fn eval_string_comparison_expr(left: &Ast, op: &str, right: &Ast);
        fn eval_comparison_expr(left: &Ast, op: &str, right: &Ast);
        fn eval_object_expr(value: &Ast);
        fn eval_if_expr(condition: &Ast, thenExpr: &Ast, elseExpr: &Ast);
        fn eval_branch_expression_expr(value: &Ast);
        fn eval_number_match_expr(firstCase: &Ast, moreCases: &[Ast], defaultCase: &Ast);
        fn eval_number_case_expr(condition: &Ast, value: &Ast);
        fn eval_number_default_case_expr(value: &Ast);
        fn eval_number_case_value_expr(value: &Ast);
        fn eval_string_match_expr(firstCase: &Ast, moreCases: &[Ast], defaultCase: &Ast);
        fn eval_string_case_expr(condition: &Ast, value: &Ast);
        fn eval_string_default_case_expr(value: &Ast);
        fn eval_string_case_value_expr(value: &Ast);
        fn eval_boolean_match_expr(firstCase: &Ast, moreCases: &[Ast], defaultCase: &Ast);
        fn eval_boolean_case_expr(condition: &Ast, value: &Ast);
        fn eval_boolean_default_case_expr(value: &Ast);
        fn eval_boolean_case_value_expr(value: &Ast);
        fn eval_variable_ref_expr(name: &str, r#type: Option<&str>);
    }
}
