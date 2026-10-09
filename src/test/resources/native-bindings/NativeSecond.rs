use tinyexpression_rs::runtime::bindings::{BindingError, ClassBindings};
pub fn register(class: &mut ClassBindings) -> Result<(), BindingError> {
    class.bind::<(f32,), f32, _>("twice", |_, (v,)| Ok(v * 2.0))
}
