use tinyexpression_rs::runtime::bindings::{BindingError, ClassBindings};
use tinyexpression_rs::runtime::{ExternalError, HostObject};
use tinyexpression_rs::Value;

pub fn register(class: &mut ClassBindings) -> Result<(), BindingError> {
    class.bind::<(f32, f32), f32, _>("add", |_, (a, b)| Ok(a + b))?;
    class.bind::<(String,), String, _>("greet", |vars, (s,)| {
        Ok(format!("{}{}", vars.string("prefix").unwrap_or_default(), s))
    })?;
    class.bind::<(String,), bool, _>("digits", |_, (s,)| {
        Ok(!s.is_empty() && s.chars().all(|c| c.is_ascii_digit()))
    })?;
    class.bind::<(), f32, _>("scoped", |vars, ()| {
        match vars.number("x") { Some(Value::Number(n)) => Ok(n), _ => Ok(-1.0) }
    })?;
    let mut counter = 0;
    class.bind::<(), f32, _>("next", move |_, ()| { counter += 1; Ok(counter as f32) })?;
    class.bind::<(), f32, _>("fail", |_, ()| Err(ExternalError::Failed("deliberate".into())))?;
    class.bind::<(f32,), f32, _>("floatArg", |_, (v,)| Ok(v))?;
    class.bind::<(f64,), f64, _>("doubleArg", |_, (v,)| Ok(v))?;
    class.bind::<(i32,), i32, _>("intArg", |_, (v,)| Ok(v))?;
    class.bind::<(i64,), i64, _>("longArg", |_, (v,)| Ok(v))?;
    class.bind::<(i16,), i16, _>("shortArg", |_, (v,)| Ok(v))?;
    class.bind::<(i8,), i8, _>("byteArg", |_, (v,)| Ok(v))?;
    class.bind::<(bool,), bool, _>("boolArg", |_, (v,)| Ok(v))?;
    class.bind::<(Option<String>,), Option<String>, _>("nullable", |_, (v,)| Ok(v))?;
    class.bind::<(Option<String>,), bool, _>("isNull", |_, (v,)| Ok(v.is_none()))?;
    class.bind::<(Value,), Value, _>("identity", |_, (v,)| Ok(v))?;
    class.bind::<(), HostObject, _>("object", |_, ()| Ok(HostObject::new("NativeObject", "opaque")))?;
    class.bind::<(Value,), String, _>("objectText", |_, (v,)| Ok(tinyexpression_rs::runtime::java_string(&v)))?;
    Ok(())
}
