//! The contextual runtime's public API and host traits (issue #179). Semantics are gated by
//! `java_differential.rs`; these tests pin the host contract a native embedder relies on.

use std::collections::HashMap;

use tinyexpression_rs::runtime::{
    calculate, calculator_result, Clock, Context, ContextClock, ErrorKind, ExternalCall,
    ExternalError, ExternalHost, Host, NoExternals, NumberType, Options, Program, RandomSource,
    ResultType, Variables, XorShiftRandom,
};
use tinyexpression_rs::Value;

fn run(
    program: &Program,
    context: &mut Context,
    external: &mut dyn ExternalHost,
    clock: &dyn Clock,
) -> [Result<Value, tinyexpression_rs::runtime::EvalError>; 2] {
    let mut random = XorShiftRandom::new(3);
    let tree = {
        let mut host = Host {
            external,
            clock,
            random: &mut random,
        };
        calculator_result(program.eval_tree(&mut context.clone(), &mut host))
    };
    let compiled = program.compile();
    let mut host = Host {
        external,
        clock,
        random: &mut random,
    };
    [tree, calculator_result(compiled.eval(context, &mut host))]
}

#[test]
fn variables_follow_the_four_java_maps() {
    let mut context = Context::new();
    context.set_float("price", 10.0);
    context.set_string("country", "jp");
    let value = calculate(
        "if($country=='jp'){$price*2}else{0}",
        Options::new(ResultType::Float),
        &mut context,
    );
    assert_eq!(value, Ok(Value::Number(20.0)));
    // Absent variables read as the numberType zero in arithmetic.
    let value = calculate(
        "$missing+1",
        Options::new(ResultType::Float),
        &mut Context::new(),
    );
    assert_eq!(value, Ok(Value::Number(1.0)));
}

#[test]
fn declarations_are_calculation_local() {
    let mut context = Context::new();
    let value = calculate(
        "var $x as number set 5;\n$x*2",
        Options::new(ResultType::Float),
        &mut context,
    );
    assert_eq!(value, Ok(Value::Number(10.0)));
    assert!(
        !context.exists("x"),
        "a declaration must not leak into the host context"
    );
    // `set if not exists` keeps a host value.
    context.set_float("x", 7.0);
    let value = calculate(
        "var $x as number set if not exists 5;\n$x*2",
        Options::new(ResultType::Float),
        &mut context,
    );
    assert_eq!(value, Ok(Value::Number(14.0)));
}

#[test]
fn number_type_selects_the_arithmetic() {
    let options = Options::new(ResultType::Float).with_number_type(NumberType::Int);
    let program = Program::new("7/2", options).unwrap();
    for result in run(
        &program,
        &mut Context::new(),
        &mut NoExternals,
        &ContextClock,
    ) {
        assert_eq!(result, Ok(Value::Int(3)));
    }
    let program = Program::new("7/0", options).unwrap();
    for result in run(
        &program,
        &mut Context::new(),
        &mut NoExternals,
        &ContextClock,
    ) {
        assert_eq!(result.unwrap_err().kind, ErrorKind::Arithmetic);
    }
}

#[test]
fn default_host_reports_an_unregistered_external_like_java() {
    let program = Program::new(
        "import org.example.Fee#calculate as fee;\nexternal returning as number fee(1)",
        Options::new(ResultType::Float),
    )
    .unwrap();
    for result in run(
        &program,
        &mut Context::new(),
        &mut NoExternals,
        &ContextClock,
    ) {
        assert_eq!(result.unwrap_err().kind, ErrorKind::Calculation);
    }
}

struct Doubler {
    calls: HashMap<String, usize>,
}

impl ExternalHost for Doubler {
    fn class_exists(&self, class_name: &str) -> bool {
        class_name == "org.example.Doubler"
    }

    fn invoke(
        &mut self,
        call: &ExternalCall<'_>,
        variables: &dyn Variables,
    ) -> Result<Value, ExternalError> {
        *self.calls.entry(call.method_name.to_owned()).or_default() += 1;
        match (call.method_name, call.args) {
            ("twice", [Value::Number(v)]) => Ok(Value::Number(v * 2.0)),
            ("bonus", []) => Ok(variables.number("bonus").unwrap_or(Value::Number(0.0))),
            _ => Err(ExternalError::MethodNotFound),
        }
    }
}

#[test]
fn external_host_receives_resolved_calls_and_the_scoped_variables() {
    let program = Program::new(
        "import org.example.Doubler#twice as twice;\nimport org.example.Doubler as D;\nexternal returning as number twice(21) + external returning as number D#bonus()",
        Options::new(ResultType::Float),
    )
    .unwrap();
    let mut context = Context::new();
    context.set_float("bonus", 0.5);
    let mut host = Doubler {
        calls: HashMap::new(),
    };
    for result in run(&program, &mut context, &mut host, &ContextClock) {
        assert_eq!(result, Ok(Value::Number(42.5)));
    }
    assert_eq!(host.calls.get("twice"), Some(&2));
    let unknown = Program::new(
        "import org.other.Thing#m as m;\nexternal returning as number m()",
        Options::new(ResultType::Float),
    )
    .unwrap();
    for result in run(&unknown, &mut Context::new(), &mut host, &ContextClock) {
        assert_eq!(result.unwrap_err().kind, ErrorKind::UnsupportedOperation);
    }
}

struct FixedClock {
    day: f32,
    hour: f32,
}

impl Clock for FixedClock {
    fn now_hour(&self, _: &dyn Variables) -> Option<f32> {
        Some(self.hour)
    }
    fn now_day_and_hour(&self, _: &Context) -> Option<(f32, f32)> {
        Some((self.day, self.hour))
    }
}

#[test]
fn clock_is_injected_and_defaults_to_the_context_variables() {
    let program = Program::new(
        "inDayTimeRange(MONDAY,9,FRIDAY,17)",
        Options::new(ResultType::Boolean),
    )
    .unwrap();
    let tuesday_noon = FixedClock {
        day: 2.0,
        hour: 12.0,
    };
    for result in run(
        &program,
        &mut Context::new(),
        &mut NoExternals,
        &tuesday_noon,
    ) {
        assert_eq!(result, Ok(Value::Boolean(true)));
    }
    let sunday = FixedClock {
        day: 7.0,
        hour: 12.0,
    };
    for result in run(&program, &mut Context::new(), &mut NoExternals, &sunday) {
        assert_eq!(result, Ok(Value::Boolean(false)));
    }
    // ContextClock: Java reads nowHour / nowDayOfWeek; absent means "not in range".
    for result in run(
        &program,
        &mut Context::new(),
        &mut NoExternals,
        &ContextClock,
    ) {
        assert_eq!(result, Ok(Value::Boolean(false)));
    }
    let mut context = Context::new();
    context.set_float("nowDayOfWeek", 3.0);
    context.set_float("nowHour", 10.0);
    for result in run(&program, &mut context, &mut NoExternals, &ContextClock) {
        assert_eq!(result, Ok(Value::Boolean(true)));
    }
}

struct Constant(f64);

impl RandomSource for Constant {
    fn next_double(&mut self) -> f64 {
        self.0
    }
}

#[test]
fn random_comes_from_the_host() {
    let program = Program::new("random()", Options::new(ResultType::Float)).unwrap();
    let mut random = Constant(0.25);
    let mut host = Host {
        external: &mut NoExternals,
        clock: &ContextClock,
        random: &mut random,
    };
    assert_eq!(
        program.eval_tree(&mut Context::new(), &mut host),
        Ok(Value::Number(0.25))
    );
    assert_eq!(
        program.compile().eval(&mut Context::new(), &mut host),
        Ok(Value::Number(0.25))
    );
}

#[test]
fn parse_rejections_are_create_stage_errors() {
    let error = Program::new("1 +", Options::new(ResultType::Float)).unwrap_err();
    assert_eq!(error.kind, ErrorKind::Parse);
    assert!(error.diagnostic.is_some());
    // A formula whose only whole-source root is boolean has no float calculator (Java rejects
    // `1<2` as a float result the same way).
    let error = Program::new("1<2", Options::new(ResultType::Float)).unwrap_err();
    assert_eq!(error.kind, ErrorKind::Parse);
    assert_eq!(
        calculate(
            "1<2",
            Options::new(ResultType::Boolean),
            &mut Context::new()
        ),
        Ok(Value::Boolean(true))
    );
}

#[test]
fn recursion_is_bounded() {
    let program = Program::new(
        "call loop(0)\nfloat loop($n as number){call loop($n+1)}",
        Options::new(ResultType::Float),
    )
    .unwrap();
    for result in run(
        &program,
        &mut Context::new(),
        &mut NoExternals,
        &ContextClock,
    ) {
        assert_eq!(result.unwrap_err().kind, ErrorKind::StackOverflow);
    }
}
