//! Contextual evaluator: the Java `P4TypedAstEvaluator` / `AstEvaluatorCalculator` semantics.
//!
//! [`Program::new`] parses a formula and selects the evaluation root the way the Java
//! `P4_AST_EVALUATOR` backend does for a result type (`P4PreferredAstMapper` candidate order).
//! A program then runs in one of two forms that must agree on every input:
//!
//! - [`Program::eval_tree`]: a tree walker that follows the Java evaluator method by method;
//! - [`Program::compile`] → [`Compiled::eval`]: the AST lowered once into nested closures.
//!
//! Variables live in a [`Context`] (the four maps of Java's `AbstractCalculationContext`).
//! Everything the formula cannot compute by itself comes from host traits: [`ExternalHost`]
//! (`external ...` invocations), [`Clock`] (`inTimeRange`/`inDayTimeRange`) and
//! [`RandomSource`] (`random()`). The defaults behave like a Java host with nothing registered.

mod ast_meta;
pub(crate) mod compile;
pub mod java;
mod ops;
mod select;
pub mod trace;
mod walk;

use std::any::Any;
use std::collections::HashMap;
use std::fmt::{self, Display, Formatter};
use std::sync::Arc;

use crate::generated::ast::Ast;
use crate::{FrontendError, ParseDiagnostic, Value};

pub use compile::Compiled;
pub use trace::{TraceHook, TraceNode, TraceRecorder, TraceSite};

/// `ExpressionType` values a formula can be evaluated as (the result type of the calculator).
#[derive(Clone, Copy, Debug, Eq, PartialEq, Hash)]
pub enum ResultType {
    Float,
    Double,
    Int,
    Long,
    Short,
    Byte,
    Boolean,
    String,
    Object,
}

impl ResultType {
    pub fn is_number(self) -> bool {
        matches!(
            self,
            Self::Float | Self::Double | Self::Int | Self::Long | Self::Short | Self::Byte
        )
    }

    /// Parses the spellings FormulaInfo and the CLI accept (`float`, `java.lang.Float`, ...).
    pub fn parse(name: &str) -> Option<Self> {
        Some(match name.trim().to_ascii_lowercase().as_str() {
            "float" | "number" | "java.lang.float" => Self::Float,
            "double" | "java.lang.double" => Self::Double,
            "int" | "integer" | "java.lang.integer" => Self::Int,
            "long" | "java.lang.long" => Self::Long,
            "short" | "java.lang.short" => Self::Short,
            "byte" | "java.lang.byte" => Self::Byte,
            "boolean" | "java.lang.boolean" => Self::Boolean,
            "string" | "java.lang.string" => Self::String,
            "object" | "java.lang.object" => Self::Object,
            _ => return None,
        })
    }
}

/// The `numberType` of a calculator: the representation of every arithmetic result.
///
/// `BigDecimal`/`BigInteger` are out of scope (they would need an arbitrary-precision
/// dependency); see the crate README.
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq, Hash)]
pub enum NumberType {
    #[default]
    Float,
    Double,
    Int,
    Long,
    Short,
    Byte,
}

impl NumberType {
    pub fn parse(name: &str) -> Option<Self> {
        Some(match ResultType::parse(name)? {
            ResultType::Float => Self::Float,
            ResultType::Double => Self::Double,
            ResultType::Int => Self::Int,
            ResultType::Long => Self::Long,
            ResultType::Short => Self::Short,
            ResultType::Byte => Self::Byte,
            _ => return None,
        })
    }
}

/// `CalculationContext.Angle`: how `sin`/`cos`/`tan` read their argument.
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub enum Angle {
    Radian,
    #[default]
    Degree,
}

/// Calculator settings (`SpecifiedExpressionTypes` plus evaluator limits).
#[derive(Clone, Copy, Debug)]
pub struct Options {
    pub result_type: ResultType,
    pub number_type: NumberType,
    /// Nested `call` depth before the evaluator reports `StackOverflowError`. Java's limit is the
    /// thread stack; Rust needs an explicit bound to stay within its own stack.
    pub max_call_depth: usize,
}

/// Default nested `call` depth. On wasm32 the evaluator runs on the host engine's call stack
/// (about 1 MB in V8), which 256 levels of a recursive method exhaust before the depth check,
/// trapping the whole instance; 48 keeps `StackOverflowError` a normal evaluation error there.
#[cfg(not(target_arch = "wasm32"))]
const DEFAULT_MAX_CALL_DEPTH: usize = 256;
#[cfg(target_arch = "wasm32")]
const DEFAULT_MAX_CALL_DEPTH: usize = 48;

impl Default for Options {
    fn default() -> Self {
        Self {
            result_type: ResultType::Float,
            number_type: NumberType::Float,
            max_call_depth: DEFAULT_MAX_CALL_DEPTH,
        }
    }
}

impl Options {
    pub fn new(result_type: ResultType) -> Self {
        Self {
            result_type,
            ..Self::default()
        }
    }

    pub fn with_number_type(mut self, number_type: NumberType) -> Self {
        self.number_type = number_type;
        self
    }
}

/// An opaque value a host stores in the object map or returns from an external call.
#[derive(Clone)]
pub struct HostObject {
    class_name: String,
    display: String,
    payload: Option<Arc<dyn Any + Send + Sync>>,
}

impl HostObject {
    /// `class_name` is the Java-style class name, `display` is what `String.valueOf` prints.
    pub fn new(class_name: impl Into<String>, display: impl Into<String>) -> Self {
        Self {
            class_name: class_name.into(),
            display: display.into(),
            payload: None,
        }
    }

    pub fn with_payload(mut self, payload: Arc<dyn Any + Send + Sync>) -> Self {
        self.payload = Some(payload);
        self
    }

    pub fn class_name(&self) -> &str {
        &self.class_name
    }

    pub fn display(&self) -> &str {
        &self.display
    }

    pub fn payload(&self) -> Option<&Arc<dyn Any + Send + Sync>> {
        self.payload.as_ref()
    }
}

impl fmt::Debug for HostObject {
    fn fmt(&self, f: &mut Formatter<'_>) -> fmt::Result {
        f.debug_struct("HostObject")
            .field("class_name", &self.class_name)
            .field("display", &self.display)
            .finish()
    }
}

impl PartialEq for HostObject {
    fn eq(&self, other: &Self) -> bool {
        self.class_name == other.class_name
            && self.display == other.display
            && match (&self.payload, &other.payload) {
                (Some(a), Some(b)) => Arc::ptr_eq(a, b),
                (None, None) => true,
                _ => false,
            }
    }
}

/// `String.valueOf(value)` for every runtime value.
pub fn java_string(value: &Value) -> String {
    match value {
        Value::Number(v) => java::float_to_string(*v),
        Value::Double(v) => java::double_to_string(*v),
        Value::Int(v) => v.to_string(),
        Value::Long(v) => v.to_string(),
        Value::Short(v) => v.to_string(),
        Value::Byte(v) => v.to_string(),
        Value::Boolean(v) => v.to_string(),
        Value::String(v) => v.clone(),
        Value::Null => "null".to_owned(),
        Value::Object(object) => object.display.clone(),
    }
}

/// The four variable maps of Java's `AbstractCalculationContext`.
///
/// A name can be present in several maps at once, exactly as in Java; typed lookups read one map
/// and untyped lookups try number, string, boolean, object in that order.
#[derive(Clone, Debug, Default)]
pub struct Context {
    numbers: HashMap<String, Value>,
    strings: HashMap<String, String>,
    booleans: HashMap<String, bool>,
    objects: HashMap<String, Value>,
    angle: Angle,
}

impl Context {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn with_angle(angle: Angle) -> Self {
        Self {
            angle,
            ..Self::default()
        }
    }

    pub fn angle(&self) -> Angle {
        self.angle
    }

    /// `set(name, float)`.
    pub fn set_float(&mut self, name: &str, value: f32) {
        self.numbers.insert(name.to_owned(), Value::Number(value));
    }

    /// `set(name, Number)`: `value` must be one of the numeric variants (others are ignored).
    pub fn set_number(&mut self, name: &str, value: Value) {
        if ops::is_number(&value) {
            self.numbers.insert(name.to_owned(), value);
        }
    }

    pub fn set_string(&mut self, name: &str, value: impl Into<String>) {
        self.strings.insert(name.to_owned(), value.into());
    }

    pub fn set_boolean(&mut self, name: &str, value: bool) {
        self.booleans.insert(name.to_owned(), value);
    }

    /// `setObject(name, value)`: any value, including boxed numbers, strings and booleans.
    pub fn set_object(&mut self, name: &str, value: Value) {
        if value == Value::Null {
            self.objects.remove(name);
        } else {
            self.objects.insert(name.to_owned(), value);
        }
    }

    pub fn number(&self, name: &str) -> Option<&Value> {
        self.numbers.get(name)
    }

    /// `getValue(name)`: the number map viewed as `Float`.
    pub fn float_value(&self, name: &str) -> Option<f32> {
        self.numbers.get(name).map(ops::float_value)
    }

    pub fn string(&self, name: &str) -> Option<&str> {
        self.strings.get(name).map(String::as_str)
    }

    pub fn boolean(&self, name: &str) -> Option<bool> {
        self.booleans.get(name).copied()
    }

    pub fn object(&self, name: &str) -> Option<&Value> {
        self.objects.get(name)
    }

    /// `isExists(name)`.
    pub fn exists(&self, name: &str) -> bool {
        self.numbers.contains_key(name)
            || self.booleans.contains_key(name)
            || self.strings.contains_key(name)
            || self.objects.contains_key(name)
    }
}

/// Read access to the variables visible at the point of an external call (the scoped view the
/// Java evaluator hands to an external method as its `CalculationContext` parameter).
pub trait Variables {
    fn number(&self, name: &str) -> Option<Value>;
    fn string(&self, name: &str) -> Option<String>;
    fn boolean(&self, name: &str) -> Option<bool>;
    fn object(&self, name: &str) -> Option<Value>;
    fn exists(&self, name: &str) -> bool;
}

/// Why an external invocation did not produce a value. Each maps to the Java exception the
/// reflection-based `evaluateExternalInvocation` raises in the same situation.
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum ExternalError {
    /// `Class.forName` failed → `UnsupportedOperationException("External invocation failed")`.
    ClassNotFound,
    /// No public method with that name and argument count → `UnsupportedOperationException`.
    MethodNotFound,
    /// The class exists but no instance was registered in the context →
    /// `CalculationException("class not found in CalculationContext ...")`.
    NotRegistered,
    /// The method threw → `UnsupportedOperationException` (wrapped `InvocationTargetException`).
    Failed(String),
}

/// One `external ...` call after import resolution, with evaluated arguments.
#[derive(Debug)]
pub struct ExternalCall<'a> {
    /// Fully qualified class name after import/alias resolution.
    pub class_name: &'a str,
    pub method_name: &'a str,
    pub args: &'a [Value],
}

/// Supplies the objects `external` invocations call. Java resolves these by reflection on
/// `context.getObject(className)`; a native host registers them here instead.
pub trait ExternalHost {
    /// Mirrors `Class.forName`: returning `false` fails before the arguments are evaluated.
    fn class_exists(&self, class_name: &str) -> bool {
        let _ = class_name;
        true
    }

    /// Invokes the method. `Ok(Value::Null)` is a Java `null` return.
    fn invoke(
        &mut self,
        call: &ExternalCall<'_>,
        variables: &dyn Variables,
    ) -> Result<Value, ExternalError>;
}

/// The default host: every class resolves but nothing is registered, so an external call fails
/// with the error Java raises when the context holds no instance (`CalculationException`).
#[derive(Clone, Copy, Debug, Default)]
pub struct NoExternals;

impl ExternalHost for NoExternals {
    fn invoke(&mut self, _: &ExternalCall<'_>, _: &dyn Variables) -> Result<Value, ExternalError> {
        Err(ExternalError::NotRegistered)
    }
}

/// Day of week as `java.time.DayOfWeek` (MONDAY = 1 ... SUNDAY = 7).
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum DayOfWeek {
    Monday = 1,
    Tuesday,
    Wednesday,
    Thursday,
    Friday,
    Saturday,
    Sunday,
}

impl DayOfWeek {
    /// `DayOfWeek.valueOf` (exact upper-case constant names).
    pub fn value_of(name: &str) -> Option<Self> {
        Some(match name {
            "MONDAY" => Self::Monday,
            "TUESDAY" => Self::Tuesday,
            "WEDNESDAY" => Self::Wednesday,
            "THURSDAY" => Self::Thursday,
            "FRIDAY" => Self::Friday,
            "SATURDAY" => Self::Saturday,
            "SUNDAY" => Self::Sunday,
            _ => return None,
        })
    }

    pub fn value(self) -> i32 {
        self as i32
    }
}

/// Time source for `inTimeRange` / `inDayTimeRange`.
///
/// Java reads the host clock through the context variables `nowHour` and `nowDayOfWeek`
/// (`EmbeddedFunction.inTimeRange`, `AbstractCalculationContext.inDayTimeRange`);
/// [`ContextClock`] does the same. A host with a real clock overrides these two methods.
pub trait Clock {
    /// Current hour of day for `inTimeRange` (Java: scoped `getValue("nowHour")`).
    fn now_hour(&self, variables: &dyn Variables) -> Option<f32> {
        variables.number("nowHour").map(|v| ops::float_value(&v))
    }

    /// Current hour and ISO day of week for `inDayTimeRange` (Java: the base context's
    /// `getValue("nowHour")` / `getValue("nowDayOfWeek")`, not method-local scopes).
    fn now_day_and_hour(&self, base: &Context) -> Option<(f32, f32)> {
        Some((
            base.float_value("nowDayOfWeek")?,
            base.float_value("nowHour")?,
        ))
    }
}

/// Reads the time from the context variables, as Java does.
#[derive(Clone, Copy, Debug, Default)]
pub struct ContextClock;

impl Clock for ContextClock {}

/// Source of `random()` (Java: `Math.random()`), a double in `[0, 1)`.
pub trait RandomSource {
    fn next_double(&mut self) -> f64;
}

/// A small xorshift generator; deterministic for a given seed.
#[derive(Clone, Debug)]
pub struct XorShiftRandom(u64);

impl XorShiftRandom {
    pub fn new(seed: u64) -> Self {
        Self(seed | 1)
    }
}

impl Default for XorShiftRandom {
    /// Seeded from the system clock; on `wasm32-unknown-unknown`, which has no clock (reading
    /// it panics), from a fixed seed — pass a seed with [`XorShiftRandom::new`] there.
    #[cfg(target_arch = "wasm32")]
    fn default() -> Self {
        Self::new(0x9e37_79b9_7f4a_7c15)
    }

    #[cfg(not(target_arch = "wasm32"))]
    fn default() -> Self {
        let seed = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .map(|d| d.as_nanos() as u64)
            .unwrap_or(0x9e37_79b9_7f4a_7c15);
        Self::new(seed)
    }
}

impl RandomSource for XorShiftRandom {
    fn next_double(&mut self) -> f64 {
        let mut x = self.0;
        x ^= x << 13;
        x ^= x >> 7;
        x ^= x << 17;
        self.0 = x;
        (x >> 11) as f64 / (1u64 << 53) as f64
    }
}

/// The host services one evaluation uses.
pub struct Host<'a> {
    pub external: &'a mut dyn ExternalHost,
    pub clock: &'a dyn Clock,
    pub random: &'a mut dyn RandomSource,
}

/// Java exception classes the evaluator can raise, by simple name.
#[derive(Clone, Copy, Debug, Eq, PartialEq, Hash)]
pub enum ErrorKind {
    /// The formula was rejected while building the calculator (`ParseException`).
    Parse,
    UnsupportedOperation,
    IllegalArgument,
    NumberFormat,
    Arithmetic,
    ClassCast,
    NullPointer,
    StringIndexOutOfBounds,
    Calculation,
    StackOverflow,
}

impl ErrorKind {
    /// The Java exception's simple class name.
    pub fn java_name(self) -> &'static str {
        match self {
            Self::Parse => "ParseException",
            Self::UnsupportedOperation => "UnsupportedOperationException",
            Self::IllegalArgument => "IllegalArgumentException",
            Self::NumberFormat => "NumberFormatException",
            Self::Arithmetic => "ArithmeticException",
            Self::ClassCast => "ClassCastException",
            Self::NullPointer => "NullPointerException",
            Self::StringIndexOutOfBounds => "StringIndexOutOfBoundsException",
            Self::Calculation => "CalculationException",
            Self::StackOverflow => "StackOverflowError",
        }
    }

    /// What `AstEvaluatorCalculator.apply` lets escape: it rewraps `UnsupportedOperationException`
    /// and `IllegalArgumentException` (including `NumberFormatException`).
    pub fn at_calculator(self) -> Self {
        match self {
            Self::IllegalArgument | Self::NumberFormat | Self::UnsupportedOperation => {
                Self::UnsupportedOperation
            }
            other => other,
        }
    }
}

/// An evaluation failure: the Java exception kind plus a message.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct EvalError {
    pub kind: ErrorKind,
    pub message: String,
    /// The parser diagnostic when `kind` is [`ErrorKind::Parse`] and the parser rejected the
    /// source (as opposed to root selection finding no whole-source candidate).
    pub diagnostic: Option<ParseDiagnostic>,
}

impl EvalError {
    pub(crate) fn new(kind: ErrorKind, message: impl Into<String>) -> Self {
        Self {
            kind,
            message: message.into(),
            diagnostic: None,
        }
    }

    pub fn canonical_json(&self) -> String {
        format!(
            "{{\"kind\":{},\"message\":{}}}",
            crate::json_string(self.kind.java_name()),
            crate::json_string(&self.message)
        )
    }
}

impl Display for EvalError {
    fn fmt(&self, f: &mut Formatter<'_>) -> fmt::Result {
        write!(f, "{}: {}", self.kind.java_name(), self.message)
    }
}

impl std::error::Error for EvalError {}

impl From<FrontendError> for EvalError {
    fn from(error: FrontendError) -> Self {
        let message = error.to_string();
        Self {
            kind: ErrorKind::Parse,
            message,
            diagnostic: match error {
                FrontendError::Parse(diagnostic) => Some(diagnostic),
                _ => None,
            },
        }
    }
}

/// A parsed formula with its evaluation root selected for [`Options::result_type`].
#[derive(Clone, Debug)]
pub struct Program {
    /// Evaluation root (the Java `ParsedAst.ast()`); `None` for a blank formula.
    root: Option<Ast>,
    /// The comment-blanked source (`stripJavaStyleCommentsPreservingLayout`) as code points,
    /// used for slice index text exactly as Java's `P4SourceText` reads it.
    source: Vec<char>,
    options: Options,
}

impl Program {
    /// Parses `source` and selects the evaluation root (Java: the `AstEvaluatorCalculator`
    /// constructor). A rejection is an [`ErrorKind::Parse`] error.
    pub fn new(source: &str, options: Options) -> Result<Self, EvalError> {
        let stripped = select::strip_comments(source);
        if source.chars().all(java::is_whitespace) {
            return Ok(Self {
                root: None,
                source: stripped,
                options,
            });
        }
        let root = select::select_root(source, &stripped, options.result_type)?;
        Ok(Self {
            root: Some(root),
            source: stripped,
            options,
        })
    }

    pub fn options(&self) -> &Options {
        &self.options
    }

    /// The selected evaluation root.
    pub fn root(&self) -> Option<&Ast> {
        self.root.as_ref()
    }

    /// Evaluates with the tree walker. Returns the evaluator-level result: `Ok(Value::Null)` is
    /// a Java `null` result, errors carry the exception the Java evaluator throws.
    pub fn eval_tree(
        &self,
        context: &mut Context,
        host: &mut Host<'_>,
    ) -> Result<Value, EvalError> {
        match &self.root {
            Some(root) => walk::Walker::new(self, context, host).run(root),
            None => Ok(Value::Null),
        }
    }

    /// [`Program::eval_tree`] with `hook` observing every step of the walker (issue #201): the
    /// result is the same as without a hook. See [`trace`].
    pub fn eval_tree_traced(
        &self,
        context: &mut Context,
        host: &mut Host<'_>,
        hook: &mut dyn TraceHook,
    ) -> Result<Value, EvalError> {
        match &self.root {
            Some(root) => walk::Walker::new(self, context, host)
                .with_trace(hook)
                .run(root),
            None => Ok(Value::Null),
        }
    }

    /// Lowers the selected root into closures once; [`Compiled::eval`] then runs it.
    pub fn compile(&self) -> Compiled {
        compile::compile_program(self)
    }

    pub(crate) fn source_text(&self, span: crate::Span) -> String {
        let end = span.end.min(self.source.len());
        let start = span.start.min(end);
        self.source[start..end].iter().collect()
    }
}

/// `AstEvaluatorCalculator.apply` on top of an evaluator result: a `null` result and
/// `IllegalArgumentException`/`UnsupportedOperationException` become
/// `UnsupportedOperationException`; everything else passes through.
pub fn calculator_result(result: Result<Value, EvalError>) -> Result<Value, EvalError> {
    match result {
        Ok(Value::Null) => Err(EvalError::new(
            ErrorKind::UnsupportedOperation,
            "Generated AST backend cannot evaluate formula (result was null)",
        )),
        Ok(value) => Ok(value),
        Err(error) => Err(EvalError {
            kind: error.kind.at_calculator(),
            ..error
        }),
    }
}

/// Parses, selects the root and evaluates once with the default host (tree walker).
pub fn calculate(
    source: &str,
    options: Options,
    context: &mut Context,
) -> Result<Value, EvalError> {
    let program = Program::new(source, options)?;
    let mut external = NoExternals;
    let mut random = XorShiftRandom::default();
    let mut host = Host {
        external: &mut external,
        clock: &ContextClock,
        random: &mut random,
    };
    calculator_result(program.eval_tree(context, &mut host))
}
