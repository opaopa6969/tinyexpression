use std::sync::{
    atomic::{AtomicUsize, Ordering},
    Arc,
};
use tinyexpression_rs::{
    runtime::{
        bindings::*, Context, ContextClock, ExternalCall, ExternalError, ExternalHost, Host,
        HostObject, Options, Program, Variables, XorShiftRandom,
    },
    Value,
};

struct Empty;
impl Variables for Empty {
    fn number(&self, _: &str) -> Option<Value> {
        None
    }
    fn string(&self, _: &str) -> Option<String> {
        None
    }
    fn boolean(&self, _: &str) -> Option<bool> {
        None
    }
    fn object(&self, _: &str) -> Option<Value> {
        None
    }
    fn exists(&self, _: &str) -> bool {
        false
    }
}

#[test]
fn typed_values_and_java_conversions_are_explicit() {
    assert_eq!(f32::read(&Value::String("1.25".into())).unwrap(), 1.25);
    assert_eq!(i32::read(&Value::String("bad".into())).unwrap(), 0);
    assert_eq!(i32::read(&Value::Long(4_294_967_297)).unwrap(), 1);
    assert_eq!(
        i64::read(&Value::Long(9_007_199_254_740_993)).unwrap(),
        9_007_199_254_740_993
    );
    assert_eq!(i16::read(&Value::Byte(-2)).unwrap(), -2);
    assert!(Option::<i16>::read(&Value::Byte(-2)).is_err());
    assert_eq!(Option::<i16>::read(&Value::Short(2)).unwrap(), Some(2));
    assert!(i8::read(&Value::Int(2)).is_err());
    assert!(f32::read(&Value::Null).is_err());
    assert_eq!(Option::<String>::read(&Value::Null).unwrap(), None);
    assert_eq!(Option::<String>::None.into_value(), Value::Null);
    assert!(bool::read(&Value::String("TrUe".into())).unwrap());
    assert_eq!(String::read(&Value::Long(12)).unwrap(), "12");
    assert_eq!(Value::read(&Value::Null).unwrap(), Value::Null);
    let object = HostObject::new("Example", "opaque").with_payload(Arc::new(123_i64));
    let copied = HostObject::read(&Value::Object(object.clone())).unwrap();
    assert_eq!(object, copied);
    assert_eq!(
        *copied.payload().unwrap().downcast_ref::<i64>().unwrap(),
        123
    );
    for v in [
        1f32.into_value(),
        2f64.into_value(),
        3i32.into_value(),
        4i64.into_value(),
        5i16.into_value(),
        6i8.into_value(),
        true.into_value(),
        "s".to_owned().into_value(),
        ().into_value(),
        object.into_value(),
    ] {
        assert_eq!(Value::read(&v).unwrap(), v);
    }
}

#[test]
fn registration_and_call_failures_are_not_silent() {
    let mut class = ClassBindings::default();
    class
        .bind::<(i32,), i32, _>("echo", |_, (n,)| Ok(n))
        .unwrap();
    assert_eq!(
        class
            .bind::<(f32,), f32, _>("echo", |_, (n,)| Ok(n))
            .unwrap_err()
            .code,
        "CB006"
    );
    class
        .bind::<(), (), _>("panic", |_, ()| panic!("deliberate callback panic"))
        .unwrap();
    class
        .bind::<(Option<String>,), bool, _>("isNull", |_, (v,)| Ok(v.is_none()))
        .unwrap();
    let mut registry = Registry::default();
    registry.add_class("Demo", class).unwrap();
    assert_eq!(
        registry
            .add_class("Demo", ClassBindings::default())
            .unwrap_err()
            .code,
        "CB006"
    );
    let call = |class_name, method_name, args| ExternalCall {
        class_name,
        method_name,
        args,
    };
    assert_eq!(
        registry
            .invoke(&call("Demo", "echo", &[Value::Int(7)]), &Empty)
            .unwrap(),
        Value::Int(7)
    );
    assert_eq!(
        registry.invoke(&call("Demo", "isNull", &[Value::Null]), &Empty),
        Ok(Value::Boolean(true))
    );
    assert!(registry
        .invoke(&call("Demo", "echo", &[Value::Null]), &Empty)
        .is_err());
    assert_eq!(
        registry.invoke(&call("Missing", "echo", &[]), &Empty),
        Err(ExternalError::ClassNotFound)
    );
    assert_eq!(
        registry.invoke(&call("Demo", "echo", &[]), &Empty),
        Err(ExternalError::MethodNotFound)
    );
    assert!(matches!(
        registry.invoke(&call("Demo", "panic", &[]), &Empty),
        Err(ExternalError::Failed(_))
    ));
    let mut denied = ClassBindings::default();
    denied
        .bind::<(), (), _>("go", |_, ()| panic!("must not execute"))
        .unwrap();
    denied.set_registered(false);
    registry.add_class("Denied", denied).unwrap();
    assert_eq!(
        registry.invoke(&call("Denied", "go", &[]), &Empty),
        Err(ExternalError::NotRegistered)
    );
    assert_eq!(
        registry.invoke(&call("Denied", "absent", &[]), &Empty),
        Err(ExternalError::MethodNotFound)
    );
    type Seven = Cons<i32, Cons<i32, Cons<i32, Cons<i32, Cons<i32, Cons<i32, Cons<i32, ()>>>>>>>;
    assert_eq!(Seven::LEN, 7);
    let seven = Seven::read(&[
        Value::Int(1),
        Value::Int(2),
        Value::Int(3),
        Value::Int(4),
        Value::Int(5),
        Value::Int(6),
        Value::Int(7),
    ])
    .unwrap();
    assert_eq!(seven.1 .1 .1 .1 .1 .1 .0, 7);
}

#[test]
fn all_source_bindings_are_verified_before_any_hook_and_normal_eval_stays_denied() {
    static CALLED: AtomicUsize = AtomicUsize::new(0);
    fn register(c: &mut ClassBindings) -> Result<(), BindingError> {
        CALLED.fetch_add(1, Ordering::SeqCst);
        c.bind::<(), f32, _>("answer", |_, ()| Ok(42.0))
    }
    let source = "````rust:Demo\n// first ```\n````\n`````rust:Second\n// second\n`````\nimport Demo#answer as answer; external returning as number answer()";
    let mut blocks = [
        CompiledBlock {
            identifier: "Demo",
            body: "// first ```\n",
            register,
        },
        CompiledBlock {
            identifier: "Second",
            body: "// wrong\n",
            register,
        },
    ];
    assert!(matches!(
        LinkedCode::new(source, &blocks),
        Err(BindingError { code: "CB007", .. })
    ));
    assert_eq!(CALLED.load(Ordering::SeqCst), 0);
    assert!(Program::new(source, Options::default())
        .unwrap_err()
        .message
        .contains("CB005"));
    blocks[1].body = "// second\n";
    let mut linked = LinkedCode::new(source, &blocks).unwrap();
    assert_eq!(CALLED.load(Ordering::SeqCst), 2);
    assert_eq!(linked.source(), source);
    let program = linked.program(Options::default()).unwrap();
    assert_eq!(program.code_blocks().len(), 2);
    let mut random = XorShiftRandom::new(1);
    let mut host = Host {
        external: linked.externals(),
        clock: &ContextClock,
        random: &mut random,
    };
    assert_eq!(
        program.eval_tree(&mut Context::new(), &mut host),
        Ok(Value::Number(42.0))
    );
    assert_eq!(
        program.compile().eval(&mut Context::new(), &mut host),
        Ok(Value::Number(42.0))
    );
    let mut trace = tinyexpression_rs::runtime::TraceRecorder::default();
    assert_eq!(
        program.eval_tree_traced(&mut Context::new(), &mut host, &mut trace),
        Ok(Value::Number(42.0))
    );
    let trace_json = trace.to_json();
    assert!(trace_json.contains("ExternalNumberInvocationExpr"));
    assert!(trace_json.contains(&format!(
        "\"span\":[{},{}]",
        source.find("external returning").unwrap(),
        source.len()
    )));
    assert_eq!(linked.evaluate_request("{}").exit_code, 0);
    assert!(linked
        .evaluate_request(r#"{"formula":"1"}"#)
        .json
        .contains("CB007"));
    blocks[1].register = |_| panic!("deliberate registration failure");
    assert!(matches!(
        LinkedCode::new(source, &blocks),
        Err(BindingError {
            code: "CB006",
            span: Some(_),
            ..
        })
    ));
}
