//! Typed, linked native methods for trusted hosts. Registration never compiles source.
use super::{
    java, java_string, ops, EvalError, ExternalCall, ExternalError, ExternalHost, HostObject,
    Options, Program, Variables,
};
use crate::{code_blocks, Span, Value};
use std::collections::BTreeMap;

pub const BINDING_ABI: &str = "tinyexpression-native-bindings-v1";

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct BindingError {
    pub code: &'static str,
    pub message: String,
    pub span: Option<Span>,
}
impl BindingError {
    pub fn canonical_json(&self) -> String {
        let span = self
            .span
            .map_or_else(|| "null".into(), |s| format!("[{},{}]", s.start, s.end));
        format!("{{\"ok\":false,\"stage\":\"link\",\"diagnostics\":[{{\"code\":{},\"origin\":\"binding\",\"message\":{},\"span\":{span}}}]}}",
            crate::json_string(self.code), crate::json_string(&self.message))
    }
    fn new(code: &'static str, message: impl Into<String>) -> Self {
        Self {
            code,
            message: message.into(),
            span: None,
        }
    }
}
impl std::fmt::Display for BindingError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}: {}", self.code, self.message)
    }
}
impl std::error::Error for BindingError {}

/// Conversion at the Java-compatible external-call boundary. `Option<T>` carries
/// nullable boxed/reference values; a non-optional Rust argument rejects null.
pub trait HostValue: Sized + 'static {
    fn read(value: &Value) -> Result<Self, ExternalError>;
    /// Non-null Java boxed/reference conversion (primitive widening may differ).
    fn read_boxed(value: &Value) -> Result<Self, ExternalError> {
        Self::read(value)
    }
    fn into_value(self) -> Value;
}
fn mismatch() -> ExternalError {
    ExternalError::Failed("native argument type mismatch".into())
}
macro_rules! number {
    ($t:ty, $variant:ident, $number:ident, $parse:expr) => {
        impl HostValue for $t {
            fn read(v: &Value) -> Result<Self, ExternalError> {
                if *v == Value::Null {
                    return Err(mismatch());
                }
                Ok(if ops::is_number(v) {
                    ops::$number(v) as $t
                } else {
                    ($parse)(&java_string(v)).unwrap_or_default()
                })
            }
            fn into_value(self) -> Value {
                Value::$variant(self)
            }
        }
    };
}
number!(f32, Number, float_value, java::parse_float);
number!(f64, Double, double_value, java::parse_double);
number!(i32, Int, int_value, |s: &str| java::parse_integer(
    s,
    i32::MIN as i64,
    i32::MAX as i64
)
.map(|v| v as i32));
number!(i64, Long, long_value, |s: &str| java::parse_integer(
    s,
    i64::MIN,
    i64::MAX
));
impl HostValue for i16 {
    fn read_boxed(v: &Value) -> Result<Self, ExternalError> {
        match v {
            Value::Short(v) => Ok(*v),
            _ => Err(mismatch()),
        }
    }
    fn read(v: &Value) -> Result<Self, ExternalError> {
        match v {
            Value::Short(v) => Ok(*v),
            Value::Byte(v) => Ok(*v as i16),
            _ => Err(mismatch()),
        }
    }
    fn into_value(self) -> Value {
        Value::Short(self)
    }
}
impl HostValue for i8 {
    fn read(v: &Value) -> Result<Self, ExternalError> {
        match v {
            Value::Byte(v) => Ok(*v),
            _ => Err(mismatch()),
        }
    }
    fn into_value(self) -> Value {
        Value::Byte(self)
    }
}
impl HostValue for bool {
    fn read(v: &Value) -> Result<Self, ExternalError> {
        if *v == Value::Null {
            Err(mismatch())
        } else {
            Ok(java_string(v).eq_ignore_ascii_case("true"))
        }
    }
    fn into_value(self) -> Value {
        Value::Boolean(self)
    }
}
impl HostValue for String {
    fn read(v: &Value) -> Result<Self, ExternalError> {
        if *v == Value::Null {
            Err(mismatch())
        } else {
            Ok(java_string(v))
        }
    }
    fn into_value(self) -> Value {
        Value::String(self)
    }
}
impl HostValue for HostObject {
    fn read(v: &Value) -> Result<Self, ExternalError> {
        match v {
            Value::Object(o) => Ok(o.clone()),
            _ => Err(mismatch()),
        }
    }
    fn into_value(self) -> Value {
        Value::Object(self)
    }
}
impl HostValue for Value {
    fn read(v: &Value) -> Result<Self, ExternalError> {
        Ok(v.clone())
    }
    fn into_value(self) -> Value {
        self
    }
}
impl<T: HostValue> HostValue for Option<T> {
    fn read(v: &Value) -> Result<Self, ExternalError> {
        if *v == Value::Null {
            Ok(None)
        } else {
            T::read_boxed(v).map(Some)
        }
    }
    fn into_value(self) -> Value {
        self.map_or(Value::Null, HostValue::into_value)
    }
}
impl HostValue for () {
    fn read(v: &Value) -> Result<Self, ExternalError> {
        if *v == Value::Null {
            Ok(())
        } else {
            Err(mismatch())
        }
    }
    fn into_value(self) -> Value {
        Value::Null
    }
}

pub trait Arguments: Sized + 'static {
    const LEN: usize;
    fn read(values: &[Value]) -> Result<Self, ExternalError>;
}
impl Arguments for () {
    const LEN: usize = 0;
    fn read(v: &[Value]) -> Result<Self, ExternalError> {
        if v.is_empty() {
            Ok(())
        } else {
            Err(ExternalError::MethodNotFound)
        }
    }
}
/// Arbitrary arity, without a generated tuple-implementation limit.
pub struct Cons<H, T>(pub H, pub T);
impl<H: HostValue, T: Arguments> Arguments for Cons<H, T> {
    const LEN: usize = 1 + T::LEN;
    fn read(v: &[Value]) -> Result<Self, ExternalError> {
        let (head, tail) = v.split_first().ok_or(ExternalError::MethodNotFound)?;
        Ok(Self(H::read(head)?, T::read(tail)?))
    }
}
macro_rules! tuple {
    ($($t:ident:$i:tt),+) => {
        impl<$($t: HostValue),+> Arguments for ($($t,)+) {
            const LEN: usize = [$(stringify!($t)),+].len();
            fn read(v: &[Value]) -> Result<Self, ExternalError> {
                if v.len() != Self::LEN { return Err(ExternalError::MethodNotFound); }
                Ok(($($t::read(&v[$i])?,)+))
            }
        }
    };
}
tuple!(A:0);
tuple!(A:0,B:1);
tuple!(A:0,B:1,C:2);
tuple!(A:0,B:1,C:2,D:3);
tuple!(A:0,B:1,C:2,D:3,E:4);
tuple!(A:0,B:1,C:2,D:3,E:4,F:5);

type NativeMethod = Box<dyn FnMut(&dyn Variables, &[Value]) -> Result<Value, ExternalError>>;
/// One compiled block's namespace. Methods resolve by name and argument count,
/// like the Java evaluator. Same-name/same-arity overloads are explicitly rejected.
pub struct ClassBindings {
    registered: bool,
    methods: BTreeMap<(String, usize), NativeMethod>,
}
impl Default for ClassBindings {
    fn default() -> Self {
        Self {
            registered: true,
            methods: BTreeMap::new(),
        }
    }
}
impl ClassBindings {
    pub fn set_registered(&mut self, registered: bool) {
        self.registered = registered;
    }
    pub fn bind<A, R, F>(&mut self, name: &str, mut function: F) -> Result<(), BindingError>
    where
        A: Arguments,
        R: HostValue,
        F: FnMut(&dyn Variables, A) -> Result<R, ExternalError> + 'static,
    {
        let key = (name.to_owned(), A::LEN);
        if name.is_empty() || self.methods.contains_key(&key) {
            return Err(BindingError::new(
                "CB006",
                format!("empty or duplicate native method {name}/{}", A::LEN),
            ));
        }
        self.methods.insert(
            key,
            Box::new(move |vars, args| function(vars, A::read(args)?).map(HostValue::into_value)),
        );
        Ok(())
    }
}

#[derive(Default)]
pub struct Registry {
    classes: BTreeMap<String, ClassBindings>,
}
impl Registry {
    pub fn add_class(&mut self, label: &str, bindings: ClassBindings) -> Result<(), BindingError> {
        if label.is_empty() || self.classes.contains_key(label) {
            return Err(BindingError::new(
                "CB006",
                format!("empty or duplicate native class {label}"),
            ));
        }
        self.classes.insert(label.into(), bindings);
        Ok(())
    }
}
impl ExternalHost for Registry {
    fn class_exists(&self, class: &str) -> bool {
        self.classes.contains_key(class)
    }
    fn invoke(
        &mut self,
        call: &ExternalCall<'_>,
        vars: &dyn Variables,
    ) -> Result<Value, ExternalError> {
        let class = self
            .classes
            .get_mut(call.class_name)
            .ok_or(ExternalError::ClassNotFound)?;
        let method = class
            .methods
            .get_mut(&(call.method_name.into(), call.args.len()))
            .ok_or(ExternalError::MethodNotFound)?;
        if !class.registered {
            return Err(ExternalError::NotRegistered);
        }
        std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| method(vars, call.args)))
            .unwrap_or_else(|_| Err(ExternalError::Failed("native method panicked".into())))
    }
}

/// Emitted by the AOT builder, linking an exact source body to compiled registration
/// code. Trusted embedding hosts may also supply this explicitly; it is not a sandbox.
pub struct CompiledBlock {
    pub identifier: &'static str,
    pub body: &'static str,
    pub register: fn(&mut ClassBindings) -> Result<(), BindingError>,
}

/// Owns the exact source and its linked functions. Normal `Program::new` continues
/// to reject Rust blocks; only this verified, explicit linked-code path accepts them.
pub struct LinkedCode {
    source: String,
    pub(crate) registry: Registry,
}
impl LinkedCode {
    pub fn new(source: &str, compiled: &[CompiledBlock]) -> Result<Self, BindingError> {
        let blocks =
            code_blocks::parse(source).map_err(|e| BindingError::new("CB007", e.to_string()))?;
        if let Some(error) =
            code_blocks::preflight(&blocks, code_blocks::Target::Rust, true).first()
        {
            return Err(BindingError {
                code: error.code,
                message: error.to_string(),
                span: Some(error.span),
            });
        }
        if blocks.len() != compiled.len() {
            return Err(BindingError::new(
                "CB007",
                "compiled block count does not match source",
            ));
        }
        // Validate every binding before invoking any registration function.
        for (block, native) in blocks.iter().zip(compiled) {
            if block.identifier != native.identifier || block.body != native.body {
                return Err(BindingError {
                    code: "CB007",
                    message: "compiled block does not match source".into(),
                    span: Some(block.name_span),
                });
            }
        }
        let mut registry = Registry::default();
        for (block, native) in blocks.iter().zip(compiled) {
            let mut class = ClassBindings::default();
            std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
                (native.register)(&mut class)
            }))
            .unwrap_or_else(|_| Err(BindingError::new("CB006", "native registration panicked")))
            .map_err(|mut e| {
                e.span = Some(block.name_span);
                e
            })?;
            registry.add_class(&block.identifier, class)?;
        }
        Ok(Self {
            source: source.into(),
            registry,
        })
    }
    pub fn source(&self) -> &str {
        &self.source
    }
    /// Use with `program()` and the ordinary Host API (including tracing/closures).
    pub fn externals(&mut self) -> &mut Registry {
        &mut self.registry
    }
    pub fn program(&self, options: Options) -> Result<Program, EvalError> {
        Program::new_linked(&self.source, options)
    }
    /// Evaluate the embedded formula using the usual JSON context contract. A
    /// supplied `formula` must match exactly; compiled classes take priority over stubs.
    pub fn evaluate_request(&mut self, request: &str) -> crate::api::Response {
        crate::api::eval_linked_context_json(request, self)
    }
}
