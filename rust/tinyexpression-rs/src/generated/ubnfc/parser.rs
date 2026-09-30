//! IR の expression/rule ごとの静的関数。c=consumed, m=matchOnly。
#![allow(dead_code, unused_mut)]
use super::api::*;
use super::runtime::*;
#[allow(unused_imports)]
use super::rt::{diag::Diag,memo::Key};
pub fn parse(text:&str)->ParseResult {parse_with_options(text,ParseOptions::default())}
// issue #35 / D-070: 既定呼出しは呼出し元 thread の上で保守的な native stack 番兵
// （NATIVE_STACK_SENTINEL_BYTES、呼出し元が明示的に小さい stack で呼んでも安全な値）の
// まま解析する。番兵に max_depth より先に当たった（＝呼出し元の stack が小さいだけで、
// 論理上限にはまだ余裕がある）ときだけ、max_depth から見積もった十分な stack を持つ
// 専用 thread で 1 度だけ解析し直す。外部 TokenScanner を受け取る parse_with_scanner は
// 任意の状態を持つ scanner を thread 境界へ渡せないためこの対象外（既知の制約）。
pub fn parse_with_options(text:&str,options:ParseOptions)->ParseResult {
let (result,stack_exhausted)=parse_with_options_budget(text,options,NATIVE_STACK_SENTINEL_BYTES);
if result.ok || !stack_exhausted {return result;}
parse_with_options_escalated(text,options)
}
// issue #65 / D-070 wasm: wasm32 には OS スレッドの stack size 制御が無く
// （`wasm32-unknown-unknown` は既定で `std::thread` 自体を欠く）、この escalation は
// コンパイルできない。生成物は依存ゼロ・std のみのままにするため wasm32 では
// escalation を丸ごとコンパイル対象から外し、番兵budget（NATIVE_STACK_SENTINEL_BYTES）
// の結果、つまり既に "maximum parse depth exceeded" が入った診断へそのまま fallback する
// （crash しない。D-070 の契約どおり）。
#[cfg(not(target_arch = "wasm32"))]
fn parse_with_options_escalated(text:&str,options:ParseOptions)->ParseResult {
let budget=escalated_stack_budget(options.max_depth);
let stack_bytes=escalated_thread_stack_bytes(options.max_depth);
std::thread::scope(|scope| {
std::thread::Builder::new().stack_size(stack_bytes)
.spawn_scoped(scope, move || parse_with_options_budget(text,options,budget).0)
.expect("spawn escalated parser thread")
.join()
.unwrap_or_else(|payload| std::panic::resume_unwind(payload))
})
}
#[cfg(target_arch = "wasm32")]
fn parse_with_options_escalated(text:&str,options:ParseOptions)->ParseResult {
parse_with_options_budget(text,options,NATIVE_STACK_SENTINEL_BYTES).0
}
fn parse_with_options_budget(text:&str,options:ParseOptions,budget:usize)->(ParseResult,bool) {
parse_with_scanner_budget(text,options,&mut RejectExtern,budget)
}
pub fn parse_with_scanner(text:&str,options:ParseOptions,scanner:&mut dyn TokenScanner)->ParseResult {
parse_with_scanner_budget(text,options,scanner,NATIVE_STACK_SENTINEL_BYTES).0
}
fn parse_with_scanner_budget(text:&str,options:ParseOptions,scanner:&mut dyn TokenScanner,budget:usize)->(ParseResult,bool) {
// 診断は const DIAG で単相化する（D-036）。要求されていれば診断 session で 1 度だけ走り、
// そうでなければ fast session で走って、診断が要る結果のときだけ診断 session で解析し直す（D-023）。
if options.diagnostics {
let mut parser=Session::<true>::new(text,options,scanner,budget);
let step=parser.r0_c(State::default());
let stack_exhausted=parser.stack_sentinel_tripped();
return (parser.finish(step),stack_exhausted);
}
let mut parser=Session::<false>::new(text,options,&mut *scanner,budget);
let step=parser.r0_c(State::default());
let stack_exhausted=parser.stack_sentinel_tripped();
let (result,escalate)=parser.finish_checked(step);
if !escalate {return (result,stack_exhausted);}
let diag_options=ParseOptions{diagnostics:true,..options};
let mut parser=Session::<true>::new(text,diag_options,scanner,budget);
let step=parser.r0_c(State::default());
let stack_exhausted=stack_exhausted || parser.stack_sentinel_tripped();
(parser.finish(step),stack_exhausted)
}
fn entry_step<const DIAG: bool>(parser:&mut Session<'_,DIAG>,grammar:&str,entry:Option<&str>)->Result<Step,&'static str> {
Ok(match (grammar,entry) {
("TinyExpressionP4",None)=>parser.r0_c(State::default()),
("TinyExpressionP4",Some("Formula"))=>parser.r0_c(State::default()),
("TinyExpressionP4",Some("CodeBlock"))=>parser.r1_c(State::default()),
("TinyExpressionP4",Some("ImportDeclaration"))=>parser.r2_c(State::default()),
("TinyExpressionP4",Some("ClassName"))=>parser.r3_c(State::default()),
("TinyExpressionP4",Some("VariableDeclaration"))=>parser.r4_c(State::default()),
("TinyExpressionP4",Some("NumberVariableDeclaration"))=>parser.r5_c(State::default()),
("TinyExpressionP4",Some("StringVariableDeclaration"))=>parser.r6_c(State::default()),
("TinyExpressionP4",Some("BooleanVariableDeclaration"))=>parser.r7_c(State::default()),
("TinyExpressionP4",Some("ObjectVariableDeclaration"))=>parser.r8_c(State::default()),
("TinyExpressionP4",Some("TypeHint"))=>parser.r9_c(State::default()),
("TinyExpressionP4",Some("NumberTypeHint"))=>parser.r10_c(State::default()),
("TinyExpressionP4",Some("StringTypeHint"))=>parser.r11_c(State::default()),
("TinyExpressionP4",Some("BooleanTypeHint"))=>parser.r12_c(State::default()),
("TinyExpressionP4",Some("ObjectTypeHint"))=>parser.r13_c(State::default()),
("TinyExpressionP4",Some("OnlyIfAbsent"))=>parser.r14_c(State::default()),
("TinyExpressionP4",Some("Description"))=>parser.r15_c(State::default()),
("TinyExpressionP4",Some("Annotation"))=>parser.r16_c(State::default()),
("TinyExpressionP4",Some("AnnotationParameters"))=>parser.r17_c(State::default()),
("TinyExpressionP4",Some("AnnotationParameter"))=>parser.r18_c(State::default()),
("TinyExpressionP4",Some("MethodDeclaration"))=>parser.r19_c(State::default()),
("TinyExpressionP4",Some("NumberMethodDeclaration"))=>parser.r20_c(State::default()),
("TinyExpressionP4",Some("StringMethodDeclaration"))=>parser.r21_c(State::default()),
("TinyExpressionP4",Some("BooleanMethodDeclaration"))=>parser.r22_c(State::default()),
("TinyExpressionP4",Some("ObjectMethodDeclaration"))=>parser.r23_c(State::default()),
("TinyExpressionP4",Some("MethodParameters"))=>parser.r24_c(State::default()),
("TinyExpressionP4",Some("MethodParameter"))=>parser.r25_c(State::default()),
("TinyExpressionP4",Some("NumberReturnType"))=>parser.r26_c(State::default()),
("TinyExpressionP4",Some("StringReturnType"))=>parser.r27_c(State::default()),
("TinyExpressionP4",Some("BooleanReturnType"))=>parser.r28_c(State::default()),
("TinyExpressionP4",Some("ObjectReturnType"))=>parser.r29_c(State::default()),
("TinyExpressionP4",Some("ReturnType"))=>parser.r30_c(State::default()),
("TinyExpressionP4",Some("ExternalBooleanInvocation"))=>parser.r31_c(State::default()),
("TinyExpressionP4",Some("ExternalNumberInvocation"))=>parser.r32_c(State::default()),
("TinyExpressionP4",Some("ExternalStringInvocation"))=>parser.r33_c(State::default()),
("TinyExpressionP4",Some("ExternalObjectInvocation"))=>parser.r34_c(State::default()),
("TinyExpressionP4",Some("MethodInvocationHeader"))=>parser.r35_c(State::default()),
("TinyExpressionP4",Some("MethodInvocation"))=>parser.r36_c(State::default()),
("TinyExpressionP4",Some("ArgumentTernary"))=>parser.r37_c(State::default()),
("TinyExpressionP4",Some("ArgumentExpression"))=>parser.r38_c(State::default()),
("TinyExpressionP4",Some("Arguments"))=>parser.r39_c(State::default()),
("TinyExpressionP4",Some("NumberExpression"))=>parser.r40_c(State::default()),
("TinyExpressionP4",Some("NumberTerm"))=>parser.r41_c(State::default()),
("TinyExpressionP4",Some("AddOp"))=>parser.r42_c(State::default()),
("TinyExpressionP4",Some("MulOp"))=>parser.r43_c(State::default()),
("TinyExpressionP4",Some("MathFunction"))=>parser.r44_c(State::default()),
("TinyExpressionP4",Some("SinFunction"))=>parser.r45_c(State::default()),
("TinyExpressionP4",Some("CosFunction"))=>parser.r46_c(State::default()),
("TinyExpressionP4",Some("TanFunction"))=>parser.r47_c(State::default()),
("TinyExpressionP4",Some("SqrtFunction"))=>parser.r48_c(State::default()),
("TinyExpressionP4",Some("MinFunction"))=>parser.r49_c(State::default()),
("TinyExpressionP4",Some("MaxFunction"))=>parser.r50_c(State::default()),
("TinyExpressionP4",Some("RandomFunction"))=>parser.r51_c(State::default()),
("TinyExpressionP4",Some("AbsFunction"))=>parser.r52_c(State::default()),
("TinyExpressionP4",Some("RoundFunction"))=>parser.r53_c(State::default()),
("TinyExpressionP4",Some("CeilFunction"))=>parser.r54_c(State::default()),
("TinyExpressionP4",Some("FloorFunction"))=>parser.r55_c(State::default()),
("TinyExpressionP4",Some("PowFunction"))=>parser.r56_c(State::default()),
("TinyExpressionP4",Some("LogFunction"))=>parser.r57_c(State::default()),
("TinyExpressionP4",Some("ExpFunction"))=>parser.r58_c(State::default()),
("TinyExpressionP4",Some("ToNumFunction"))=>parser.r59_c(State::default()),
("TinyExpressionP4",Some("NumberFactor"))=>parser.r60_c(State::default()),
("TinyExpressionP4",Some("ToUpperCaseFunction"))=>parser.r61_c(State::default()),
("TinyExpressionP4",Some("ToLowerCaseFunction"))=>parser.r62_c(State::default()),
("TinyExpressionP4",Some("TrimFunction"))=>parser.r63_c(State::default()),
("TinyExpressionP4",Some("LengthFunction"))=>parser.r64_c(State::default()),
("TinyExpressionP4",Some("LenFunction"))=>parser.r65_c(State::default()),
("TinyExpressionP4",Some("ToUpperCaseDotMethod"))=>parser.r66_c(State::default()),
("TinyExpressionP4",Some("ToLowerCaseDotMethod"))=>parser.r67_c(State::default()),
("TinyExpressionP4",Some("TrimDotMethod"))=>parser.r68_c(State::default()),
("TinyExpressionP4",Some("LengthDotMethod"))=>parser.r69_c(State::default()),
("TinyExpressionP4",Some("StartsWithFunction"))=>parser.r70_c(State::default()),
("TinyExpressionP4",Some("EndsWithFunction"))=>parser.r71_c(State::default()),
("TinyExpressionP4",Some("ContainsFunction"))=>parser.r72_c(State::default()),
("TinyExpressionP4",Some("InMethod"))=>parser.r73_c(State::default()),
("TinyExpressionP4",Some("StartsWithDotMethod"))=>parser.r74_c(State::default()),
("TinyExpressionP4",Some("EndsWithDotMethod"))=>parser.r75_c(State::default()),
("TinyExpressionP4",Some("ContainsDotMethod"))=>parser.r76_c(State::default()),
("TinyExpressionP4",Some("StringPredicateReceiver"))=>parser.r77_c(State::default()),
("TinyExpressionP4",Some("IsPresentFunction"))=>parser.r78_c(State::default()),
("TinyExpressionP4",Some("InTimeRangeFunction"))=>parser.r79_c(State::default()),
("TinyExpressionP4",Some("InDayTimeRangeFunction"))=>parser.r80_c(State::default()),
("TinyExpressionP4",Some("DayOfWeek"))=>parser.r81_c(State::default()),
("TinyExpressionP4",Some("SliceBaseReceiver"))=>parser.r82_c(State::default()),
("TinyExpressionP4",Some("SliceStartIndex"))=>parser.r83_c(State::default()),
("TinyExpressionP4",Some("SliceEndIndex"))=>parser.r84_c(State::default()),
("TinyExpressionP4",Some("SliceStepIndex"))=>parser.r85_c(State::default()),
("TinyExpressionP4",Some("SliceBaseExpression"))=>parser.r86_c(State::default()),
("TinyExpressionP4",Some("SliceNestedExpression"))=>parser.r87_c(State::default()),
("TinyExpressionP4",Some("SliceExpression"))=>parser.r88_c(State::default()),
("TinyExpressionP4",Some("StringExpression"))=>parser.r89_c(State::default()),
("TinyExpressionP4",Some("ParenthesizedStringExpression"))=>parser.r90_c(State::default()),
("TinyExpressionP4",Some("StringTerm"))=>parser.r91_c(State::default()),
("TinyExpressionP4",Some("StringCastVariable"))=>parser.r92_c(State::default()),
("TinyExpressionP4",Some("StringTypedVariable"))=>parser.r93_c(State::default()),
("TinyExpressionP4",Some("BooleanExpression"))=>parser.r94_c(State::default()),
("TinyExpressionP4",Some("BooleanAndExpression"))=>parser.r95_c(State::default()),
("TinyExpressionP4",Some("BooleanXorExpression"))=>parser.r96_c(State::default()),
("TinyExpressionP4",Some("NotExpression"))=>parser.r97_c(State::default()),
("TinyExpressionP4",Some("BooleanComparable"))=>parser.r98_c(State::default()),
("TinyExpressionP4",Some("BooleanEqualityExpression"))=>parser.r99_c(State::default()),
("TinyExpressionP4",Some("BooleanFactor"))=>parser.r100_c(State::default()),
("TinyExpressionP4",Some("StringComparisonExpression"))=>parser.r101_c(State::default()),
("TinyExpressionP4",Some("EqualityOp"))=>parser.r102_c(State::default()),
("TinyExpressionP4",Some("ComparisonExpression"))=>parser.r103_c(State::default()),
("TinyExpressionP4",Some("CompareOp"))=>parser.r104_c(State::default()),
("TinyExpressionP4",Some("ObjectExpression"))=>parser.r105_c(State::default()),
("TinyExpressionP4",Some("IfExpression"))=>parser.r106_c(State::default()),
("TinyExpressionP4",Some("BranchExpression"))=>parser.r107_c(State::default()),
("TinyExpressionP4",Some("TernaryExpression"))=>parser.r108_c(State::default()),
("TinyExpressionP4",Some("NumberMatchExpression"))=>parser.r109_c(State::default()),
("TinyExpressionP4",Some("NumberCase"))=>parser.r110_c(State::default()),
("TinyExpressionP4",Some("NumberDefaultCase"))=>parser.r111_c(State::default()),
("TinyExpressionP4",Some("NumberCaseValue"))=>parser.r112_c(State::default()),
("TinyExpressionP4",Some("StringMatchExpression"))=>parser.r113_c(State::default()),
("TinyExpressionP4",Some("StringCase"))=>parser.r114_c(State::default()),
("TinyExpressionP4",Some("StringDefaultCase"))=>parser.r115_c(State::default()),
("TinyExpressionP4",Some("StringCaseValue"))=>parser.r116_c(State::default()),
("TinyExpressionP4",Some("BooleanMatchExpression"))=>parser.r117_c(State::default()),
("TinyExpressionP4",Some("BooleanCase"))=>parser.r118_c(State::default()),
("TinyExpressionP4",Some("BooleanDefaultCase"))=>parser.r119_c(State::default()),
("TinyExpressionP4",Some("BooleanCaseValue"))=>parser.r120_c(State::default()),
("TinyExpressionP4",Some("VariableRef"))=>parser.r121_c(State::default()),
("TinyExpressionP4",Some("TypeKeyword"))=>parser.r122_c(State::default()),
("TinyExpressionP4",Some("Expression"))=>parser.r123_c(State::default()),
_=>return Err("unsupported grammar or entry"),})}
// D-070: `parse_entry_budget` / `parse_entry_escalated` は `parse_entry_with_options` より
// 前に置く。driver.rs の scanner_hook / examples/p4-rust の build.rs はこの関数を
// テキストごと切り出して `parse_entry_with_scanner` へ改名するので、その範囲に
// この 2 つを含めない（含めると全文を丸ごと差し込む方の写しと二重定義になる）。
fn parse_entry_budget(grammar:&str,entry:Option<&str>,text:&str,options:ParseOptions,scanner:&mut dyn TokenScanner,budget:usize)->Result<(ParseResult,bool),&'static str> {
if options.diagnostics {
let mut parser=Session::<true>::new(text,options,scanner,budget);
let step=entry_step(&mut parser,grammar,entry)?;
let stack_exhausted=parser.stack_sentinel_tripped();
return Ok((parser.finish(step),stack_exhausted));
}
let mut parser=Session::<false>::new(text,options,scanner,budget);
let step=entry_step(&mut parser,grammar,entry)?;
let stack_exhausted=parser.stack_sentinel_tripped();
let (result,escalate)=parser.finish_checked(step);
if !escalate {return Ok((result,stack_exhausted));}
let diag_options=ParseOptions{diagnostics:true,..options};
let mut parser=Session::<true>::new(text,diag_options,scanner,budget);
let step=entry_step(&mut parser,grammar,entry)?;
let stack_exhausted=stack_exhausted || parser.stack_sentinel_tripped();
Ok((parser.finish(step),stack_exhausted))
}
// issue #35 / D-070: native stack 番兵に max_depth より先へ当たった（＝呼出し元 thread の
// stack が小さいだけ）ときだけ、max_depth から見積もった十分な stack を持つ専用 thread で
// 1 度だけ解析し直す。RejectExtern は状態を持たないので thread をまたいで再構築してよい。
#[cfg(not(target_arch = "wasm32"))]
fn parse_entry_escalated(grammar:&str,entry:Option<&str>,text:&str,options:ParseOptions)->Result<ParseResult,&'static str> {
let budget=escalated_stack_budget(options.max_depth);
let stack_bytes=escalated_thread_stack_bytes(options.max_depth);
std::thread::scope(|scope| {
std::thread::Builder::new().stack_size(stack_bytes)
.spawn_scoped(scope, move || {
let mut scanner=RejectExtern;
parse_entry_budget(grammar,entry,text,options,&mut scanner,budget)
})
.expect("spawn escalated parser thread")
.join()
.unwrap_or_else(|payload| std::panic::resume_unwind(payload))
}).map(|(result,_)| result)
}
// issue #65 / D-070 wasm: wasm32 では escalation を丸ごとコンパイル対象から外し、番兵
// budget（NATIVE_STACK_SENTINEL_BYTES）での再解析結果、つまり既に "maximum parse depth
// exceeded" が入った診断へそのまま fallback する（crash しない。D-070 の契約どおり）。
#[cfg(target_arch = "wasm32")]
fn parse_entry_escalated(grammar:&str,entry:Option<&str>,text:&str,options:ParseOptions)->Result<ParseResult,&'static str> {
let mut scanner=RejectExtern;
parse_entry_budget(grammar,entry,text,options,&mut scanner,NATIVE_STACK_SENTINEL_BYTES).map(|(result,_)| result)
}
pub fn parse_entry_with_options(grammar:&str,entry:Option<&str>,text:&str,options:ParseOptions)->Result<ParseResult,&'static str> {
let mut scanner=RejectExtern;
let (result,stack_exhausted)=parse_entry_budget(grammar,entry,text,options,&mut scanner,NATIVE_STACK_SENTINEL_BYTES)?;
if result.ok || !stack_exhausted {return Ok(result);}
/*STACK_ESCALATION*/parse_entry_escalated(grammar,entry,text,options)/*STACK_ESCALATION*/
}
impl<const DIAG: bool> Session<'_, DIAG> {
fn r0_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
self.effect_scope_at(ScanEffect::Enter,"lexical",self.cp(state.consumed),0,"Formula");
let mut out=self.e0_c(state);
if out.ok {
self.effect_scope_at(ScanEffect::Leave,"lexical",self.cp(out.state.consumed),0,"Formula");
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:0,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["FormulaParser"]);}
self.depth-=1;out.diag=self.diag_rule(0,out.diag);out
}
fn r1_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e13_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:1,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["CodeBlockParser"]);}
self.depth-=1;out.diag=self.diag_rule(1,out.diag);out
}
fn r2_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e19_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:2,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'import'", "'as'", "';'"]);}
self.depth-=1;out.diag=self.diag_rule(2,out.diag);out
}
fn r3_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e29_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:3,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["ClassNameParser"]);}
self.depth-=1;out.diag=self.diag_rule(3,out.diag);out
}
fn r4_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e35_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:4,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'$'", "';'"]);}
self.depth-=1;out.diag=self.diag_rule(4,out.diag);out
}
fn r5_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e40_c(state);
if out.ok {
let mut scope_sites=self.take_scope();self.scope_captures(out.events,&[11],&mut scope_sites);for &(site,span) in &scope_sites { let name=self.text(span);if name.is_empty() {continue;}let offset_cp=self.cp(span[0]);let len_cp=self.cp(span[1])-offset_cp;
if site==11 {
self.effect_at(ScanEffect::Declare{name,offset_cp},"lexical",offset_cp,len_cp);
}
let _=(offset_cp,len_cp); }
self.give_scope(scope_sites);
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:5,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'variable'", "'var'", "'$'", "';'"]);}
self.depth-=1;out.diag=self.diag_rule(5,out.diag);out
}
fn r6_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e58_c(state);
if out.ok {
let mut scope_sites=self.take_scope();self.scope_captures(out.events,&[15],&mut scope_sites);for &(site,span) in &scope_sites { let name=self.text(span);if name.is_empty() {continue;}let offset_cp=self.cp(span[0]);let len_cp=self.cp(span[1])-offset_cp;
if site==15 {
self.effect_at(ScanEffect::Declare{name,offset_cp},"lexical",offset_cp,len_cp);
}
let _=(offset_cp,len_cp); }
self.give_scope(scope_sites);
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:6,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'variable'", "'var'", "'$'", "';'"]);}
self.depth-=1;out.diag=self.diag_rule(6,out.diag);out
}
fn r7_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e76_c(state);
if out.ok {
let mut scope_sites=self.take_scope();self.scope_captures(out.events,&[19],&mut scope_sites);for &(site,span) in &scope_sites { let name=self.text(span);if name.is_empty() {continue;}let offset_cp=self.cp(span[0]);let len_cp=self.cp(span[1])-offset_cp;
if site==19 {
self.effect_at(ScanEffect::Declare{name,offset_cp},"lexical",offset_cp,len_cp);
}
let _=(offset_cp,len_cp); }
self.give_scope(scope_sites);
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:7,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'variable'", "'var'", "'$'", "';'"]);}
self.depth-=1;out.diag=self.diag_rule(7,out.diag);out
}
fn r8_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e94_c(state);
if out.ok {
let mut scope_sites=self.take_scope();self.scope_captures(out.events,&[23],&mut scope_sites);for &(site,span) in &scope_sites { let name=self.text(span);if name.is_empty() {continue;}let offset_cp=self.cp(span[0]);let len_cp=self.cp(span[1])-offset_cp;
if site==23 {
self.effect_at(ScanEffect::Declare{name,offset_cp},"lexical",offset_cp,len_cp);
}
let _=(offset_cp,len_cp); }
self.give_scope(scope_sites);
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:8,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'variable'", "'var'", "'$'", "';'"]);}
self.depth-=1;out.diag=self.diag_rule(8,out.diag);out
}
fn r9_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e112_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:9,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'as'", "'number'", "'Number'", "'string'", "'String'", "'boolean'", "'Boolean'", "'object'", "'Object'"]);}
self.depth-=1;out.diag=self.diag_rule(9,out.diag);out
}
fn r10_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e125_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:10,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'as'", "'number'", "'Number'", "'float'", "'Float'"]);}
self.depth-=1;out.diag=self.diag_rule(10,out.diag);out
}
fn r11_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e134_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:11,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'as'", "'string'", "'String'"]);}
self.depth-=1;out.diag=self.diag_rule(11,out.diag);out
}
fn r12_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e141_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:12,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'as'", "'boolean'", "'Boolean'"]);}
self.depth-=1;out.diag=self.diag_rule(12,out.diag);out
}
fn r13_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e148_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:13,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'as'", "'object'", "'Object'"]);}
self.depth-=1;out.diag=self.diag_rule(13,out.diag);out
}
fn r14_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e155_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:14,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'if'", "'not'", "'exists'"]);}
self.depth-=1;out.diag=self.diag_rule(14,out.diag);out
}
fn r15_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e159_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:15,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'description'", "'='"]);}
self.depth-=1;out.diag=self.diag_rule(15,out.diag);out
}
fn r16_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e163_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:16,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'@'", "'('", "')'"]);}
self.depth-=1;out.diag=self.diag_rule(16,out.diag);out
}
fn r17_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e170_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:17,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'='"]);}
self.depth-=1;out.diag=self.diag_rule(17,out.diag);out
}
fn r18_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e176_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:18,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'='"]);}
self.depth-=1;out.diag=self.diag_rule(18,out.diag);out
}
fn r19_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e180_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:19,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'('", "')'", "'{'", "'}'"]);}
self.depth-=1;out.diag=self.diag_rule(19,out.diag);out
}
fn r20_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
self.effect_scope_at(ScanEffect::Enter,"lexical",self.cp(state.consumed),0,"NumberMethodDeclaration");
let mut out=self.e185_c(state);
if out.ok {
self.effect_scope_at(ScanEffect::Leave,"lexical",self.cp(out.state.consumed),0,"NumberMethodDeclaration");
let mut scope_sites=self.take_scope();self.scope_captures(out.events,&[27],&mut scope_sites);for &(site,span) in &scope_sites { let name=self.text(span);if name.is_empty() {continue;}let offset_cp=self.cp(span[0]);let len_cp=self.cp(span[1])-offset_cp;
if site==27 {
self.effect_at(ScanEffect::Declare{name,offset_cp},"lexical",offset_cp,len_cp);
}
let _=(offset_cp,len_cp); }
self.give_scope(scope_sites);
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:20,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'number'", "'float'", "'('", "')'", "'{'", "'}'"]);}
self.depth-=1;out.diag=self.diag_rule(20,out.diag);out
}
fn r21_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
self.effect_scope_at(ScanEffect::Enter,"lexical",self.cp(state.consumed),0,"StringMethodDeclaration");
let mut out=self.e195_c(state);
if out.ok {
self.effect_scope_at(ScanEffect::Leave,"lexical",self.cp(out.state.consumed),0,"StringMethodDeclaration");
let mut scope_sites=self.take_scope();self.scope_captures(out.events,&[30],&mut scope_sites);for &(site,span) in &scope_sites { let name=self.text(span);if name.is_empty() {continue;}let offset_cp=self.cp(span[0]);let len_cp=self.cp(span[1])-offset_cp;
if site==30 {
self.effect_at(ScanEffect::Declare{name,offset_cp},"lexical",offset_cp,len_cp);
}
let _=(offset_cp,len_cp); }
self.give_scope(scope_sites);
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:21,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'string'", "'('", "')'", "'{'", "'}'"]);}
self.depth-=1;out.diag=self.diag_rule(21,out.diag);out
}
fn r22_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
self.effect_scope_at(ScanEffect::Enter,"lexical",self.cp(state.consumed),0,"BooleanMethodDeclaration");
let mut out=self.e205_c(state);
if out.ok {
self.effect_scope_at(ScanEffect::Leave,"lexical",self.cp(out.state.consumed),0,"BooleanMethodDeclaration");
let mut scope_sites=self.take_scope();self.scope_captures(out.events,&[33],&mut scope_sites);for &(site,span) in &scope_sites { let name=self.text(span);if name.is_empty() {continue;}let offset_cp=self.cp(span[0]);let len_cp=self.cp(span[1])-offset_cp;
if site==33 {
self.effect_at(ScanEffect::Declare{name,offset_cp},"lexical",offset_cp,len_cp);
}
let _=(offset_cp,len_cp); }
self.give_scope(scope_sites);
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:22,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'boolean'", "'('", "')'", "'{'", "'}'"]);}
self.depth-=1;out.diag=self.diag_rule(22,out.diag);out
}
fn r23_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
self.effect_scope_at(ScanEffect::Enter,"lexical",self.cp(state.consumed),0,"ObjectMethodDeclaration");
let mut out=self.e215_c(state);
if out.ok {
self.effect_scope_at(ScanEffect::Leave,"lexical",self.cp(out.state.consumed),0,"ObjectMethodDeclaration");
let mut scope_sites=self.take_scope();self.scope_captures(out.events,&[36],&mut scope_sites);for &(site,span) in &scope_sites { let name=self.text(span);if name.is_empty() {continue;}let offset_cp=self.cp(span[0]);let len_cp=self.cp(span[1])-offset_cp;
if site==36 {
self.effect_at(ScanEffect::Declare{name,offset_cp},"lexical",offset_cp,len_cp);
}
let _=(offset_cp,len_cp); }
self.give_scope(scope_sites);
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:23,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'object'", "'('", "')'", "'{'", "'}'"]);}
self.depth-=1;out.diag=self.diag_rule(23,out.diag);out
}
fn r24_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e225_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:24,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["MethodParametersParser"]);}
self.depth-=1;out.diag=self.diag_rule(24,out.diag);out
}
fn r25_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e231_c(state);
if out.ok {
let mut scope_sites=self.take_scope();self.scope_captures(out.events,&[41],&mut scope_sites);for &(site,span) in &scope_sites { let name=self.text(span);if name.is_empty() {continue;}let offset_cp=self.cp(span[0]);let len_cp=self.cp(span[1])-offset_cp;
if site==41 {
self.effect_at(ScanEffect::Declare{name,offset_cp},"lexical",offset_cp,len_cp);
}
let _=(offset_cp,len_cp); }
self.give_scope(scope_sites);
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:25,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'$'"]);}
self.depth-=1;out.diag=self.diag_rule(25,out.diag);out
}
fn r26_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e238_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:26,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'number'", "'float'"]);}
self.depth-=1;out.diag=self.diag_rule(26,out.diag);out
}
fn r27_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e241_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:27,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'string'"]);}
self.depth-=1;out.diag=self.diag_rule(27,out.diag);out
}
fn r28_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e243_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:28,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'boolean'"]);}
self.depth-=1;out.diag=self.diag_rule(28,out.diag);out
}
fn r29_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e245_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:29,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'object'"]);}
self.depth-=1;out.diag=self.diag_rule(29,out.diag);out
}
fn r30_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e247_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:30,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'number'", "'float'", "'string'", "'boolean'", "'object'"]);}
self.depth-=1;out.diag=self.diag_rule(30,out.diag);out
}
fn r31_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e252_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:31,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'external'", "'boolean'", "':'", "'('", "')'"]);}
self.depth-=1;out.diag=self.diag_rule(31,out.diag);out
}
fn r32_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e273_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:32,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'external'", "'('", "')'"]);}
self.depth-=1;out.diag=self.diag_rule(32,out.diag);out
}
fn r33_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e299_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:33,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'external'", "'string'", "':'", "'('", "')'"]);}
self.depth-=1;out.diag=self.diag_rule(33,out.diag);out
}
fn r34_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e320_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:34,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'external'", "'object'", "':'", "'('", "')'"]);}
self.depth-=1;out.diag=self.diag_rule(34,out.diag);out
}
fn r35_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e341_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:35,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'call'", "'internal'"]);}
self.depth-=1;out.diag=self.diag_rule(35,out.diag);out
}
fn r36_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e347_c(state);
if out.ok {
let mut scope_sites=self.take_scope();self.scope_captures(out.events,&[59],&mut scope_sites);for &(site,span) in &scope_sites { let name=self.text(span);if name.is_empty() {continue;}let offset_cp=self.cp(span[0]);let len_cp=self.cp(span[1])-offset_cp;
if site==59 {
self.effect_at(ScanEffect::Use{name,offset_cp,len_cp},"lexical",offset_cp,len_cp);
}
let _=(offset_cp,len_cp); }
self.give_scope(scope_sites);
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:36,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'internal'", "'('", "')'"]);}
self.depth-=1;out.diag=self.diag_rule(36,out.diag);out
}
fn r37_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e354_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:37,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'?'", "':'"]);}
self.depth-=1;out.diag=self.diag_rule(37,out.diag);out
}
fn r38_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e360_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:38,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["ArgumentExpressionParser"]);}
self.depth-=1;out.diag=self.diag_rule(38,out.diag);out
}
fn r39_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e365_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:39,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["ArgumentsParser"]);}
self.depth-=1;out.diag=self.diag_rule(39,out.diag);out
}
fn r40_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e371_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:40,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["NumberExpressionParser"]);}
self.depth-=1;out.diag=self.diag_rule(40,out.diag);out
}
fn r41_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e377_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:41,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["NumberTermParser"]);}
self.depth-=1;out.diag=self.diag_rule(41,out.diag);out
}
fn r42_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e383_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:42,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'+'", "'-'"]);}
self.depth-=1;out.diag=self.diag_rule(42,out.diag);out
}
fn r43_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e386_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:43,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'*'", "'/'"]);}
self.depth-=1;out.diag=self.diag_rule(43,out.diag);out
}
fn r44_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e389_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:44,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'sin'", "'('", "')'", "'cos'", "'tan'", "'sqrt'", "'min'", "'max'", "'random'", "'abs'", "'round'", "'ceil'", "'floor'", "'pow'", "','", "'log'", "'exp'"]);}
self.depth-=1;out.diag=self.diag_rule(44,out.diag);out
}
fn r45_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e404_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:45,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'sin'", "'('", "')'"]);}
self.depth-=1;out.diag=self.diag_rule(45,out.diag);out
}
fn r46_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e409_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:46,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'cos'", "'('", "')'"]);}
self.depth-=1;out.diag=self.diag_rule(46,out.diag);out
}
fn r47_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e414_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:47,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'tan'", "'('", "')'"]);}
self.depth-=1;out.diag=self.diag_rule(47,out.diag);out
}
fn r48_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e419_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:48,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'sqrt'", "'('", "')'"]);}
self.depth-=1;out.diag=self.diag_rule(48,out.diag);out
}
fn r49_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e424_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:49,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'min'", "'('", "')'"]);}
self.depth-=1;out.diag=self.diag_rule(49,out.diag);out
}
fn r50_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e433_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:50,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'max'", "'('", "')'"]);}
self.depth-=1;out.diag=self.diag_rule(50,out.diag);out
}
fn r51_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e442_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:51,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'random'", "'('", "')'"]);}
self.depth-=1;out.diag=self.diag_rule(51,out.diag);out
}
fn r52_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e446_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:52,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'abs'", "'('", "')'"]);}
self.depth-=1;out.diag=self.diag_rule(52,out.diag);out
}
fn r53_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e451_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:53,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'round'", "'('", "')'"]);}
self.depth-=1;out.diag=self.diag_rule(53,out.diag);out
}
fn r54_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e456_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:54,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'ceil'", "'('", "')'"]);}
self.depth-=1;out.diag=self.diag_rule(54,out.diag);out
}
fn r55_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e461_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:55,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'floor'", "'('", "')'"]);}
self.depth-=1;out.diag=self.diag_rule(55,out.diag);out
}
fn r56_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e466_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:56,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'pow'", "'('", "','", "')'"]);}
self.depth-=1;out.diag=self.diag_rule(56,out.diag);out
}
fn r57_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e473_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:57,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'log'", "'('", "')'"]);}
self.depth-=1;out.diag=self.diag_rule(57,out.diag);out
}
fn r58_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e478_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:58,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'exp'", "'('", "')'"]);}
self.depth-=1;out.diag=self.diag_rule(58,out.diag);out
}
fn r59_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e483_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:59,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'toNum'", "'('", "','", "')'"]);}
self.depth-=1;out.diag=self.diag_rule(59,out.diag);out
}
fn r60_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e490_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:60,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'('", "'?'", "':'", "')'", "'match'", "'{'", "','", "'}'", "'if'", "'else'", "'toNum'", "'.length'", "'len'", "'length'", "'external'", "'$'"]);}
self.depth-=1;out.diag=self.diag_rule(60,out.diag);out
}
fn r61_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e507_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:61,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'toUpperCase'", "'('", "')'"]);}
self.depth-=1;out.diag=self.diag_rule(61,out.diag);out
}
fn r62_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e512_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:62,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'toLowerCase'", "'('", "')'"]);}
self.depth-=1;out.diag=self.diag_rule(62,out.diag);out
}
fn r63_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e517_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:63,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'trim'", "'('", "')'"]);}
self.depth-=1;out.diag=self.diag_rule(63,out.diag);out
}
fn r64_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e522_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:64,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'length'", "'('", "')'"]);}
self.depth-=1;out.diag=self.diag_rule(64,out.diag);out
}
fn r65_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e527_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:65,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'len'", "'('", "')'"]);}
self.depth-=1;out.diag=self.diag_rule(65,out.diag);out
}
fn r66_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e532_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:66,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'.toUpperCase'", "'('", "')'"]);}
self.depth-=1;out.diag=self.diag_rule(66,out.diag);out
}
fn r67_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e537_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:67,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'.toLowerCase'", "'('", "')'"]);}
self.depth-=1;out.diag=self.diag_rule(67,out.diag);out
}
fn r68_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e542_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:68,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'.trim'", "'('", "')'"]);}
self.depth-=1;out.diag=self.diag_rule(68,out.diag);out
}
fn r69_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e547_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:69,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'.length'", "'('", "')'"]);}
self.depth-=1;out.diag=self.diag_rule(69,out.diag);out
}
fn r70_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e552_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:70,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'startsWith'", "'('", "','", "')'"]);}
self.depth-=1;out.diag=self.diag_rule(70,out.diag);out
}
fn r71_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e563_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:71,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'endsWith'", "'('", "','", "')'"]);}
self.depth-=1;out.diag=self.diag_rule(71,out.diag);out
}
fn r72_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e574_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:72,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'contains'", "'('", "','", "')'"]);}
self.depth-=1;out.diag=self.diag_rule(72,out.diag);out
}
fn r73_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e585_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:73,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'.in'", "'('", "')'"]);}
self.depth-=1;out.diag=self.diag_rule(73,out.diag);out
}
fn r74_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e595_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:74,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'.startsWith'", "'('", "')'"]);}
self.depth-=1;out.diag=self.diag_rule(74,out.diag);out
}
fn r75_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e605_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:75,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'.endsWith'", "'('", "')'"]);}
self.depth-=1;out.diag=self.diag_rule(75,out.diag);out
}
fn r76_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e615_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:76,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'.contains'", "'('", "')'"]);}
self.depth-=1;out.diag=self.diag_rule(76,out.diag);out
}
fn r77_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e625_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:77,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'toLowerCase'", "'('", "')'", "'toUpperCase'", "'trim'", "'$'"]);}
self.depth-=1;out.diag=self.diag_rule(77,out.diag);out
}
fn r78_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e630_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:78,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'isPresent'", "'('", "')'"]);}
self.depth-=1;out.diag=self.diag_rule(78,out.diag);out
}
fn r79_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e635_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:79,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'inTimeRange'", "'('", "','", "')'"]);}
self.depth-=1;out.diag=self.diag_rule(79,out.diag);out
}
fn r80_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e642_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:80,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'inDayTimeRange'", "'('", "','", "')'"]);}
self.depth-=1;out.diag=self.diag_rule(80,out.diag);out
}
fn r81_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e653_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:81,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'MONDAY'", "'TUESDAY'", "'WEDNESDAY'", "'THURSDAY'", "'FRIDAY'", "'SATURDAY'", "'SUNDAY'"]);}
self.depth-=1;out.diag=self.diag_rule(81,out.diag);out
}
fn r82_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e661_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:82,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'external'", "'('", "')'", "'toUpperCase'", "'toLowerCase'", "'trim'", "'.toUpperCase'", "'.toLowerCase'", "'.trim'", "'$'"]);}
self.depth-=1;out.diag=self.diag_rule(82,out.diag);out
}
fn r83_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e677_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:83,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["SliceStartIndexParser"]);}
self.depth-=1;out.diag=self.diag_rule(83,out.diag);out
}
fn r84_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e679_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:84,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["SliceEndIndexParser"]);}
self.depth-=1;out.diag=self.diag_rule(84,out.diag);out
}
fn r85_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e681_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:85,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["SliceStepIndexParser"]);}
self.depth-=1;out.diag=self.diag_rule(85,out.diag);out
}
fn r86_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e683_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:86,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'['", "':'", "']'"]);}
self.depth-=1;out.diag=self.diag_rule(86,out.diag);out
}
fn r87_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e740_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:87,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'['", "':'", "']'"]);}
self.depth-=1;out.diag=self.diag_rule(87,out.diag);out
}
fn r88_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e797_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:88,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["SliceExpressionParser"]);}
self.depth-=1;out.diag=self.diag_rule(88,out.diag);out
}
fn r89_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e800_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:89,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["StringExpressionParser"]);}
self.depth-=1;out.diag=self.diag_rule(89,out.diag);out
}
fn r90_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e806_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:90,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'('", "')'"]);}
self.depth-=1;out.diag=self.diag_rule(90,out.diag);out
}
fn r91_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e810_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:91,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'match'", "'{'", "','", "'}'", "'if'", "'('", "')'", "'else'", "'$'", "'as'", "'external'", "'toUpperCase'", "'toLowerCase'", "'trim'", "'.toUpperCase'", "'.toLowerCase'", "'.trim'"]);}
self.depth-=1;out.diag=self.diag_rule(91,out.diag);out
}
fn r92_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e827_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:92,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'('", "'string'", "'String'", "')'", "'$'"]);}
self.depth-=1;out.diag=self.diag_rule(92,out.diag);out
}
fn r93_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e836_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:93,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'$'", "'as'", "'string'", "'String'"]);}
self.depth-=1;out.diag=self.diag_rule(93,out.diag);out
}
fn r94_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e844_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:94,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["BooleanExpressionParser"]);}
self.depth-=1;out.diag=self.diag_rule(94,out.diag);out
}
fn r95_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e850_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:95,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["BooleanAndExpressionParser"]);}
self.depth-=1;out.diag=self.diag_rule(95,out.diag);out
}
fn r96_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e856_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:96,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["BooleanXorExpressionParser"]);}
self.depth-=1;out.diag=self.diag_rule(96,out.diag);out
}
fn r97_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e862_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:97,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'not'", "'('", "')'"]);}
self.depth-=1;out.diag=self.diag_rule(97,out.diag);out
}
fn r98_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e867_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:98,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'not'", "'('", "')'", "'if'", "'{'", "'}'", "'else'", "'match'", "','", "'external'", "'.in'", "'.startsWith'", "'.endsWith'", "'.contains'", "'startsWith'", "'endsWith'", "'contains'", "'isPresent'", "'inTimeRange'", "'inDayTimeRange'", "'true'", "'false'", "'$'"]);}
self.depth-=1;out.diag=self.diag_rule(98,out.diag);out
}
fn r99_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e890_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:99,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["BooleanEqualityExpressionParser"]);}
self.depth-=1;out.diag=self.diag_rule(99,out.diag);out
}
fn r100_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e894_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:100,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["BooleanFactorParser"]);}
self.depth-=1;out.diag=self.diag_rule(100,out.diag);out
}
fn r101_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e899_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:101,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["StringComparisonExpressionParser"]);}
self.depth-=1;out.diag=self.diag_rule(101,out.diag);out
}
fn r102_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e903_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:102,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'=='", "'!='"]);}
self.depth-=1;out.diag=self.diag_rule(102,out.diag);out
}
fn r103_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e906_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:103,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["ComparisonExpressionParser"]);}
self.depth-=1;out.diag=self.diag_rule(103,out.diag);out
}
fn r104_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e910_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:104,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'=='", "'!='", "'<='", "'>='", "'<'", "'>'"]);}
self.depth-=1;out.diag=self.diag_rule(104,out.diag);out
}
fn r105_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e917_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:105,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["ObjectExpressionParser"]);}
self.depth-=1;out.diag=self.diag_rule(105,out.diag);out
}
fn r106_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e924_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:106,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'if'", "'('", "')'", "'{'", "'}'", "'else'"]);}
self.depth-=1;out.diag=self.diag_rule(106,out.diag);out
}
fn r107_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e936_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:107,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["BranchExpressionParser"]);}
self.depth-=1;out.diag=self.diag_rule(107,out.diag);out
}
fn r108_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e945_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:108,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'('", "'?'", "':'", "')'"]);}
self.depth-=1;out.diag=self.diag_rule(108,out.diag);out
}
fn r109_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e953_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:109,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'match'", "'{'", "','", "'}'"]);}
self.depth-=1;out.diag=self.diag_rule(109,out.diag);out
}
fn r110_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e964_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:110,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'->'"]);}
self.depth-=1;out.diag=self.diag_rule(110,out.diag);out
}
fn r111_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e968_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:111,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'default'", "'->'"]);}
self.depth-=1;out.diag=self.diag_rule(111,out.diag);out
}
fn r112_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e972_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:112,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["NumberCaseValueParser"]);}
self.depth-=1;out.diag=self.diag_rule(112,out.diag);out
}
fn r113_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e974_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:113,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'match'", "'{'", "','", "'}'"]);}
self.depth-=1;out.diag=self.diag_rule(113,out.diag);out
}
fn r114_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e985_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:114,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'->'"]);}
self.depth-=1;out.diag=self.diag_rule(114,out.diag);out
}
fn r115_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e989_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:115,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'default'", "'->'"]);}
self.depth-=1;out.diag=self.diag_rule(115,out.diag);out
}
fn r116_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e993_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:116,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["StringCaseValueParser"]);}
self.depth-=1;out.diag=self.diag_rule(116,out.diag);out
}
fn r117_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e995_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:117,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'match'", "'{'", "','", "'}'"]);}
self.depth-=1;out.diag=self.diag_rule(117,out.diag);out
}
fn r118_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e1006_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:118,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'->'"]);}
self.depth-=1;out.diag=self.diag_rule(118,out.diag);out
}
fn r119_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e1010_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:119,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'default'", "'->'"]);}
self.depth-=1;out.diag=self.diag_rule(119,out.diag);out
}
fn r120_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e1014_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:120,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["BooleanCaseValueParser"]);}
self.depth-=1;out.diag=self.diag_rule(120,out.diag);out
}
fn r121_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e1016_c(state);
if out.ok {
let mut scope_sites=self.take_scope();self.scope_captures(out.events,&[240],&mut scope_sites);for &(site,span) in &scope_sites { let name=self.text(span);if name.is_empty() {continue;}let offset_cp=self.cp(span[0]);let len_cp=self.cp(span[1])-offset_cp;
if site==240 {
self.effect_at(ScanEffect::Use{name,offset_cp,len_cp},"lexical",offset_cp,len_cp);
}
let _=(offset_cp,len_cp); }
self.give_scope(scope_sites);
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:121,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'$'"]);}
self.depth-=1;out.diag=self.diag_rule(121,out.diag);out
}
fn r122_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e1024_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:122,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'number'", "'Number'", "'float'", "'Float'", "'string'", "'String'", "'boolean'", "'Boolean'", "'object'", "'Object'"]);}
self.depth-=1;out.diag=self.diag_rule(122,out.diag);out
}
fn r123_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e1035_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:123,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'('", "')'"]);}
self.depth-=1;out.diag=self.diag_rule(123,out.diag);out
}
fn e0_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:0,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b0_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b0_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_0::<false>(out.state);out=self.combine(out,trivia);
let child=self.e1_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_0::<false>(out.state);out=self.combine(out,trivia);
let child=self.e3_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_0::<false>(out.state);out=self.combine(out,trivia);
let child=self.e5_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_0::<false>(out.state);out=self.combine(out,trivia);
let child=self.e7_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_0::<false>(out.state);out=self.combine(out,trivia);
let child=self.e9_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_0::<false>(out.state);out=self.combine(out,trivia);
let child=self.e10_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_0::<false>(out.state);out=self.combine(out,trivia);
let child=self.e12_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_0::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e1_c(&mut self,state:State)->Step {
let mut out=self.b1_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.display_failures(state.consumed.max(state.matched),&["Repeat"]);}
out
}
fn b1_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e2_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
}
let _=count;
if false {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e2_c(&mut self,state:State)->Step {
let mut out=self.b2_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:0,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b2_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r1_c(out.state);
out
}
fn e3_c(&mut self,state:State)->Step {
let mut out=self.b3_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.display_failures(state.consumed.max(state.matched),&["Repeat"]);}
out
}
fn b3_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e4_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
}
let _=count;
if false {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e4_c(&mut self,state:State)->Step {
let mut out=self.b4_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:1,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["';'", "'as'", "'import'"]);}
out
}
fn b4_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r2_c(out.state);
out
}
fn e5_c(&mut self,state:State)->Step {
let mut out=self.b5_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.display_failures(state.consumed.max(state.matched),&["Repeat"]);}
out
}
fn b5_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e6_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
}
let _=count;
if false {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e6_c(&mut self,state:State)->Step {
let mut out=self.b6_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:2,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b6_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r4_c(out.state);
out
}
fn e7_c(&mut self,state:State)->Step {
let mut out=self.b7_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.display_failures(state.consumed.max(state.matched),&["'('", "')'", "'@'"]);}
out
}
fn b7_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e8_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
}
let _=count;
if false {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e8_c(&mut self,state:State)->Step {
let mut out=self.b8_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b8_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r16_c(out.state);
out
}
fn e9_c(&mut self,state:State)->Step {
let mut out=self.b9_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:3,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b9_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r123_c(out.state);
out
}
fn e10_c(&mut self,state:State)->Step {
let mut out=self.b10_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.display_failures(state.consumed.max(state.matched),&["Repeat"]);}
out
}
fn b10_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e11_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
}
let _=count;
if false {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e11_c(&mut self,state:State)->Step {
let mut out=self.b11_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:4,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b11_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r19_c(out.state);
out
}
fn e12_c(&mut self,state:State)->Step {
let mut out=self.b12_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,12,0);
} else {self.display_failures(state.consumed.max(state.matched),&["EndOfSourceParser"]);}
out
}
fn b12_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.token_6::<false>(out.state,"EOF","EndOfSourceParser");
out
}
fn e13_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:13,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b13_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b13_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e14_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e14_c(&mut self,state:State)->Step {
let mut out=self.b14_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:5,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["CodeBlockGroup0Parser", "__CaptureSite"]);}
out
}
fn b14_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.e15_c(out.state);
out
}
fn e15_c(&mut self,state:State)->Step {
let mut out=self.b15_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["CodeBlockGroup0Parser"]);}
out
}
fn b15_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e16_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e17_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e18_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e16_c(&mut self,state:State)->Step {
let mut out=self.b16_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,16,1);
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b16_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.token_3::<false>(out.state,"CODE_START","CodeStartParser");
out
}
fn e17_c(&mut self,state:State)->Step {
let mut out=self.b17_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,17,1);
} else {self.display_failures(state.consumed.max(state.matched),&["WildCardStringTerminatorParser"]);}
out
}
fn b17_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.token_4::<false>(out.state,"CODE_BODY","WildCardStringTerminatorParser");
out
}
fn e18_c(&mut self,state:State)->Step {
let mut out=self.b18_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,18,1);
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b18_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.token_5::<false>(out.state,"CODE_END","CodeEndParser");
out
}
fn e19_c(&mut self,state:State)->Step {
let mut out=self.b19_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b19_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_0::<false>(out.state);out=self.combine(out,trivia);
let child=self.e20_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_0::<false>(out.state);out=self.combine(out,trivia);
let child=self.e21_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_0::<false>(out.state);out=self.combine(out,trivia);
let child=self.e22_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_0::<false>(out.state);out=self.combine(out,trivia);
let child=self.e26_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_0::<false>(out.state);out=self.combine(out,trivia);
let child=self.e27_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_0::<false>(out.state);out=self.combine(out,trivia);
let child=self.e28_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_0::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e20_c(&mut self,state:State)->Step {
let mut out=self.b20_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,20,2);
} else {self.display_failures(state.consumed.max(state.matched),&["'import'"]);}
out
}
fn b20_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"import",true,64,false,"import");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'import'");}
out
}
fn e21_c(&mut self,state:State)->Step {
let mut out=self.b21_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:6,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b21_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r3_c(out.state);
out
}
fn e22_c(&mut self,state:State)->Step {
let mut out=self.b22_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'#'"]);}
out
}
fn b22_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e23_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
if count>=1 {break;}
}
let _=count;
if count>1 {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e23_c(&mut self,state:State)->Step {
let mut out=self.b23_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'#'"]);}
out
}
fn b23_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_0::<false>(out.state);out=self.combine(out,trivia);
let child=self.e24_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_0::<false>(out.state);out=self.combine(out,trivia);
let child=self.e25_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_0::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e24_c(&mut self,state:State)->Step {
let mut out=self.b24_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,24,2);
} else {self.display_failures(state.consumed.max(state.matched),&["'#'"]);}
out
}
fn b24_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"#",true,1,false,"#");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'#'");}
out
}
fn e25_c(&mut self,state:State)->Step {
let mut out=self.b25_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,25,2);
out.events=self.event(Event::Capture {site:7,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["IdentifierParser", "__CaptureSite"]);}
out
}
fn b25_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.token_1::<false>(out.state,"IDENTIFIER","IdentifierParser");
out
}
fn e26_c(&mut self,state:State)->Step {
let mut out=self.b26_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,26,2);
} else {self.display_failures(state.consumed.max(state.matched),&["'as'"]);}
out
}
fn b26_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"as",true,47,false,"as");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'as'");}
out
}
fn e27_c(&mut self,state:State)->Step {
let mut out=self.b27_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,27,2);
out.events=self.event(Event::Capture {site:8,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["IdentifierParser", "__CaptureSite"]);}
out
}
fn b27_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.token_1::<false>(out.state,"IDENTIFIER","IdentifierParser");
out
}
fn e28_c(&mut self,state:State)->Step {
let mut out=self.b28_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,28,2);
} else {self.display_failures(state.consumed.max(state.matched),&["';'"]);}
out
}
fn b28_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,";",true,22,false,";");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"';'");}
out
}
fn e29_c(&mut self,state:State)->Step {
let mut out=self.b29_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b29_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e30_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e31_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e30_c(&mut self,state:State)->Step {
let mut out=self.b30_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,30,3);
out.events=self.event(Event::Capture {site:9,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["IdentifierParser", "__CaptureSite"]);}
out
}
fn b30_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.token_1::<false>(out.state,"IDENTIFIER","IdentifierParser");
out
}
fn e31_c(&mut self,state:State)->Step {
let mut out=self.b31_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'.'"]);}
out
}
fn b31_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e32_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
}
let _=count;
if false {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e32_c(&mut self,state:State)->Step {
let mut out=self.b32_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'.'"]);}
out
}
fn b32_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e33_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e34_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e33_c(&mut self,state:State)->Step {
let mut out=self.b33_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,33,3);
} else {self.display_failures(state.consumed.max(state.matched),&["'.'"]);}
out
}
fn b33_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,".",true,11,false,".");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'.'");}
out
}
fn e34_c(&mut self,state:State)->Step {
let mut out=self.b34_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,34,3);
out.events=self.event(Event::Capture {site:10,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["IdentifierParser", "__CaptureSite"]);}
out
}
fn b34_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.token_1::<false>(out.state,"IDENTIFIER","IdentifierParser");
out
}
fn e35_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:35,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b35_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b35_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out='choice: {let mark=self.mark();let mut diagnostic=Diag::NONE;let mut best:Option<(Step,[usize;2])>=None;
let ct=self.input.cp_at(self.skip_0::<false>(out.state.begin()).position::<false>()).map(|(c,_)|c);
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,118))) {self.guard_e36_c(out.state.begin())} else {self.e36_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,118))) {self.guard_e37_c(out.state.begin())} else {self.e37_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,118))) {self.guard_e38_c(out.state.begin())} else {self.e38_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,118))) {self.guard_e39_c(out.state.begin())} else {self.e39_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);break 'choice if let Some((mut child,effects))=best {self.replay_effects(effects);child.diag=diagnostic;child} else {out.ok=false;out.diag=diagnostic;out};
};
out
}
fn e36_c(&mut self,state:State)->Step {
let mut out=self.b36_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b36_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r5_c(out.state);
out
}
fn e37_c(&mut self,state:State)->Step {
let mut out=self.b37_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b37_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r6_c(out.state);
out
}
fn e38_c(&mut self,state:State)->Step {
let mut out=self.b38_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b38_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r7_c(out.state);
out
}
fn e39_c(&mut self,state:State)->Step {
let mut out=self.b39_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b39_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r8_c(out.state);
out
}
fn e40_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:40,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b40_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b40_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e41_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e45_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e46_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e47_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e49_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e55_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e57_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e41_c(&mut self,state:State)->Step {
let mut out=self.b41_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'var'", "'variable'"]);}
out
}
fn b41_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.e42_c(out.state);
out
}
fn e42_c(&mut self,state:State)->Step {
let mut out=self.b42_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'var'", "'variable'"]);}
out
}
fn b42_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out='choice: {let mark=self.mark();let mut diagnostic=Diag::NONE;let mut best:Option<(Step,[usize;2])>=None;
self.restore(mark);let mut child=self.e43_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e44_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);break 'choice if let Some((mut child,effects))=best {self.replay_effects(effects);child.diag=diagnostic;child} else {out.ok=false;out.diag=diagnostic;out};
};
out
}
fn e43_c(&mut self,state:State)->Step {
let mut out=self.b43_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,43,5);
} else {self.display_failures(state.consumed.max(state.matched),&["'variable'"]);}
out
}
fn b43_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"variable",true,94,false,"variable");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'variable'");}
out
}
fn e44_c(&mut self,state:State)->Step {
let mut out=self.b44_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,44,5);
} else {self.display_failures(state.consumed.max(state.matched),&["'var'"]);}
out
}
fn b44_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"var",true,93,false,"var");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'var'");}
out
}
fn e45_c(&mut self,state:State)->Step {
let mut out=self.b45_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,45,5);
} else {self.display_failures(state.consumed.max(state.matched),&["'$'"]);}
out
}
fn b45_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"$",true,2,false,"$");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'$'");}
out
}
fn e46_c(&mut self,state:State)->Step {
let mut out=self.b46_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,46,5);
out.events=self.event(Event::Capture {site:11,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["IdentifierParser", "__CaptureSite"]);}
out
}
fn b46_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.token_1::<false>(out.state,"IDENTIFIER","IdentifierParser");
out
}
fn e47_c(&mut self,state:State)->Step {
let mut out=self.b47_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["Optional"]);}
out
}
fn b47_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e48_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
if count>=1 {break;}
}
let _=count;
if count>1 {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e48_c(&mut self,state:State)->Step {
let mut out=self.b48_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b48_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r10_c(out.state);
out
}
fn e49_c(&mut self,state:State)->Step {
let mut out=self.b49_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.display_failures(state.consumed.max(state.matched),&["'set'"]);}
out
}
fn b49_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e50_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
if count>=1 {break;}
}
let _=count;
if count>1 {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e50_c(&mut self,state:State)->Step {
let mut out=self.b50_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.display_failures(state.consumed.max(state.matched),&["'set'"]);}
out
}
fn b50_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e51_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e52_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e54_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e51_c(&mut self,state:State)->Step {
let mut out=self.b51_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,51,5);
} else {self.display_failures(state.consumed.max(state.matched),&["'set'"]);}
out
}
fn b51_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"set",true,82,false,"set");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'set'");}
out
}
fn e52_c(&mut self,state:State)->Step {
let mut out=self.b52_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.display_failures(state.consumed.max(state.matched),&["Optional"]);}
out
}
fn b52_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e53_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
if count>=1 {break;}
}
let _=count;
if count>1 {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e53_c(&mut self,state:State)->Step {
let mut out=self.b53_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:12,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["'exists'", "'if'", "'not'"]);}
out
}
fn b53_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r14_c(out.state);
out
}
fn e54_c(&mut self,state:State)->Step {
let mut out=self.b54_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:13,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b54_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r40_c(out.state);
out
}
fn e55_c(&mut self,state:State)->Step {
let mut out=self.b55_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["Optional"]);}
out
}
fn b55_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e56_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
if count>=1 {break;}
}
let _=count;
if count>1 {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e56_c(&mut self,state:State)->Step {
let mut out=self.b56_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:14,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["'='", "'description'"]);}
out
}
fn b56_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r15_c(out.state);
out
}
fn e57_c(&mut self,state:State)->Step {
let mut out=self.b57_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,57,5);
} else {self.display_failures(state.consumed.max(state.matched),&["';'"]);}
out
}
fn b57_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,";",true,22,false,";");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"';'");}
out
}
fn e58_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:58,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b58_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b58_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e59_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e63_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e64_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e65_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e67_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e73_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e75_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e59_c(&mut self,state:State)->Step {
let mut out=self.b59_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'var'", "'variable'"]);}
out
}
fn b59_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.e60_c(out.state);
out
}
fn e60_c(&mut self,state:State)->Step {
let mut out=self.b60_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'var'", "'variable'"]);}
out
}
fn b60_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out='choice: {let mark=self.mark();let mut diagnostic=Diag::NONE;let mut best:Option<(Step,[usize;2])>=None;
self.restore(mark);let mut child=self.e61_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e62_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);break 'choice if let Some((mut child,effects))=best {self.replay_effects(effects);child.diag=diagnostic;child} else {out.ok=false;out.diag=diagnostic;out};
};
out
}
fn e61_c(&mut self,state:State)->Step {
let mut out=self.b61_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,61,6);
} else {self.display_failures(state.consumed.max(state.matched),&["'variable'"]);}
out
}
fn b61_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"variable",true,94,false,"variable");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'variable'");}
out
}
fn e62_c(&mut self,state:State)->Step {
let mut out=self.b62_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,62,6);
} else {self.display_failures(state.consumed.max(state.matched),&["'var'"]);}
out
}
fn b62_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"var",true,93,false,"var");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'var'");}
out
}
fn e63_c(&mut self,state:State)->Step {
let mut out=self.b63_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,63,6);
} else {self.display_failures(state.consumed.max(state.matched),&["'$'"]);}
out
}
fn b63_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"$",true,2,false,"$");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'$'");}
out
}
fn e64_c(&mut self,state:State)->Step {
let mut out=self.b64_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,64,6);
out.events=self.event(Event::Capture {site:15,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["IdentifierParser", "__CaptureSite"]);}
out
}
fn b64_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.token_1::<false>(out.state,"IDENTIFIER","IdentifierParser");
out
}
fn e65_c(&mut self,state:State)->Step {
let mut out=self.b65_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["Optional"]);}
out
}
fn b65_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e66_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
if count>=1 {break;}
}
let _=count;
if count>1 {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e66_c(&mut self,state:State)->Step {
let mut out=self.b66_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b66_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r11_c(out.state);
out
}
fn e67_c(&mut self,state:State)->Step {
let mut out=self.b67_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.display_failures(state.consumed.max(state.matched),&["'set'"]);}
out
}
fn b67_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e68_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
if count>=1 {break;}
}
let _=count;
if count>1 {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e68_c(&mut self,state:State)->Step {
let mut out=self.b68_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.display_failures(state.consumed.max(state.matched),&["'set'"]);}
out
}
fn b68_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e69_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e70_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e72_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e69_c(&mut self,state:State)->Step {
let mut out=self.b69_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,69,6);
} else {self.display_failures(state.consumed.max(state.matched),&["'set'"]);}
out
}
fn b69_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"set",true,82,false,"set");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'set'");}
out
}
fn e70_c(&mut self,state:State)->Step {
let mut out=self.b70_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.display_failures(state.consumed.max(state.matched),&["Optional"]);}
out
}
fn b70_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e71_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
if count>=1 {break;}
}
let _=count;
if count>1 {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e71_c(&mut self,state:State)->Step {
let mut out=self.b71_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:16,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["'exists'", "'if'", "'not'"]);}
out
}
fn b71_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r14_c(out.state);
out
}
fn e72_c(&mut self,state:State)->Step {
let mut out=self.b72_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:17,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b72_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r89_c(out.state);
out
}
fn e73_c(&mut self,state:State)->Step {
let mut out=self.b73_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["Optional"]);}
out
}
fn b73_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e74_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
if count>=1 {break;}
}
let _=count;
if count>1 {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e74_c(&mut self,state:State)->Step {
let mut out=self.b74_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:18,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["'='", "'description'"]);}
out
}
fn b74_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r15_c(out.state);
out
}
fn e75_c(&mut self,state:State)->Step {
let mut out=self.b75_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,75,6);
} else {self.display_failures(state.consumed.max(state.matched),&["';'"]);}
out
}
fn b75_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,";",true,22,false,";");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"';'");}
out
}
fn e76_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:76,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b76_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b76_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e77_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e81_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e82_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e83_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e85_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e91_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e93_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e77_c(&mut self,state:State)->Step {
let mut out=self.b77_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'var'", "'variable'"]);}
out
}
fn b77_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.e78_c(out.state);
out
}
fn e78_c(&mut self,state:State)->Step {
let mut out=self.b78_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'var'", "'variable'"]);}
out
}
fn b78_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out='choice: {let mark=self.mark();let mut diagnostic=Diag::NONE;let mut best:Option<(Step,[usize;2])>=None;
self.restore(mark);let mut child=self.e79_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e80_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);break 'choice if let Some((mut child,effects))=best {self.replay_effects(effects);child.diag=diagnostic;child} else {out.ok=false;out.diag=diagnostic;out};
};
out
}
fn e79_c(&mut self,state:State)->Step {
let mut out=self.b79_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,79,7);
} else {self.display_failures(state.consumed.max(state.matched),&["'variable'"]);}
out
}
fn b79_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"variable",true,94,false,"variable");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'variable'");}
out
}
fn e80_c(&mut self,state:State)->Step {
let mut out=self.b80_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,80,7);
} else {self.display_failures(state.consumed.max(state.matched),&["'var'"]);}
out
}
fn b80_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"var",true,93,false,"var");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'var'");}
out
}
fn e81_c(&mut self,state:State)->Step {
let mut out=self.b81_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,81,7);
} else {self.display_failures(state.consumed.max(state.matched),&["'$'"]);}
out
}
fn b81_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"$",true,2,false,"$");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'$'");}
out
}
fn e82_c(&mut self,state:State)->Step {
let mut out=self.b82_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,82,7);
out.events=self.event(Event::Capture {site:19,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["IdentifierParser", "__CaptureSite"]);}
out
}
fn b82_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.token_1::<false>(out.state,"IDENTIFIER","IdentifierParser");
out
}
fn e83_c(&mut self,state:State)->Step {
let mut out=self.b83_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["Optional"]);}
out
}
fn b83_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e84_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
if count>=1 {break;}
}
let _=count;
if count>1 {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e84_c(&mut self,state:State)->Step {
let mut out=self.b84_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b84_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r12_c(out.state);
out
}
fn e85_c(&mut self,state:State)->Step {
let mut out=self.b85_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.display_failures(state.consumed.max(state.matched),&["'set'"]);}
out
}
fn b85_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e86_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
if count>=1 {break;}
}
let _=count;
if count>1 {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e86_c(&mut self,state:State)->Step {
let mut out=self.b86_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.display_failures(state.consumed.max(state.matched),&["'set'"]);}
out
}
fn b86_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e87_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e88_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e90_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e87_c(&mut self,state:State)->Step {
let mut out=self.b87_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,87,7);
} else {self.display_failures(state.consumed.max(state.matched),&["'set'"]);}
out
}
fn b87_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"set",true,82,false,"set");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'set'");}
out
}
fn e88_c(&mut self,state:State)->Step {
let mut out=self.b88_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.display_failures(state.consumed.max(state.matched),&["Optional"]);}
out
}
fn b88_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e89_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
if count>=1 {break;}
}
let _=count;
if count>1 {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e89_c(&mut self,state:State)->Step {
let mut out=self.b89_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:20,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["'exists'", "'if'", "'not'"]);}
out
}
fn b89_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r14_c(out.state);
out
}
fn e90_c(&mut self,state:State)->Step {
let mut out=self.b90_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:21,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b90_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r94_c(out.state);
out
}
fn e91_c(&mut self,state:State)->Step {
let mut out=self.b91_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["Optional"]);}
out
}
fn b91_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e92_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
if count>=1 {break;}
}
let _=count;
if count>1 {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e92_c(&mut self,state:State)->Step {
let mut out=self.b92_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:22,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["'='", "'description'"]);}
out
}
fn b92_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r15_c(out.state);
out
}
fn e93_c(&mut self,state:State)->Step {
let mut out=self.b93_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,93,7);
} else {self.display_failures(state.consumed.max(state.matched),&["';'"]);}
out
}
fn b93_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,";",true,22,false,";");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"';'");}
out
}
fn e94_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:94,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b94_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b94_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e95_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e99_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e100_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e101_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e103_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e109_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e111_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e95_c(&mut self,state:State)->Step {
let mut out=self.b95_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'var'", "'variable'"]);}
out
}
fn b95_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.e96_c(out.state);
out
}
fn e96_c(&mut self,state:State)->Step {
let mut out=self.b96_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'var'", "'variable'"]);}
out
}
fn b96_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out='choice: {let mark=self.mark();let mut diagnostic=Diag::NONE;let mut best:Option<(Step,[usize;2])>=None;
self.restore(mark);let mut child=self.e97_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e98_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);break 'choice if let Some((mut child,effects))=best {self.replay_effects(effects);child.diag=diagnostic;child} else {out.ok=false;out.diag=diagnostic;out};
};
out
}
fn e97_c(&mut self,state:State)->Step {
let mut out=self.b97_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,97,8);
} else {self.display_failures(state.consumed.max(state.matched),&["'variable'"]);}
out
}
fn b97_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"variable",true,94,false,"variable");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'variable'");}
out
}
fn e98_c(&mut self,state:State)->Step {
let mut out=self.b98_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,98,8);
} else {self.display_failures(state.consumed.max(state.matched),&["'var'"]);}
out
}
fn b98_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"var",true,93,false,"var");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'var'");}
out
}
fn e99_c(&mut self,state:State)->Step {
let mut out=self.b99_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,99,8);
} else {self.display_failures(state.consumed.max(state.matched),&["'$'"]);}
out
}
fn b99_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"$",true,2,false,"$");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'$'");}
out
}
fn e100_c(&mut self,state:State)->Step {
let mut out=self.b100_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,100,8);
out.events=self.event(Event::Capture {site:23,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["IdentifierParser", "__CaptureSite"]);}
out
}
fn b100_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.token_1::<false>(out.state,"IDENTIFIER","IdentifierParser");
out
}
fn e101_c(&mut self,state:State)->Step {
let mut out=self.b101_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["Optional"]);}
out
}
fn b101_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e102_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
if count>=1 {break;}
}
let _=count;
if count>1 {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e102_c(&mut self,state:State)->Step {
let mut out=self.b102_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b102_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r13_c(out.state);
out
}
fn e103_c(&mut self,state:State)->Step {
let mut out=self.b103_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.display_failures(state.consumed.max(state.matched),&["'set'"]);}
out
}
fn b103_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e104_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
if count>=1 {break;}
}
let _=count;
if count>1 {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e104_c(&mut self,state:State)->Step {
let mut out=self.b104_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.display_failures(state.consumed.max(state.matched),&["'set'"]);}
out
}
fn b104_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e105_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e106_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e108_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e105_c(&mut self,state:State)->Step {
let mut out=self.b105_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,105,8);
} else {self.display_failures(state.consumed.max(state.matched),&["'set'"]);}
out
}
fn b105_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"set",true,82,false,"set");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'set'");}
out
}
fn e106_c(&mut self,state:State)->Step {
let mut out=self.b106_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.display_failures(state.consumed.max(state.matched),&["Optional"]);}
out
}
fn b106_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e107_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
if count>=1 {break;}
}
let _=count;
if count>1 {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e107_c(&mut self,state:State)->Step {
let mut out=self.b107_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:24,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["'exists'", "'if'", "'not'"]);}
out
}
fn b107_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r14_c(out.state);
out
}
fn e108_c(&mut self,state:State)->Step {
let mut out=self.b108_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:25,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b108_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r105_c(out.state);
out
}
fn e109_c(&mut self,state:State)->Step {
let mut out=self.b109_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["Optional"]);}
out
}
fn b109_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e110_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
if count>=1 {break;}
}
let _=count;
if count>1 {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e110_c(&mut self,state:State)->Step {
let mut out=self.b110_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:26,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["'='", "'description'"]);}
out
}
fn b110_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r15_c(out.state);
out
}
fn e111_c(&mut self,state:State)->Step {
let mut out=self.b111_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,111,8);
} else {self.display_failures(state.consumed.max(state.matched),&["';'"]);}
out
}
fn b111_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,";",true,22,false,";");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"';'");}
out
}
fn e112_c(&mut self,state:State)->Step {
let mut out=self.b112_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b112_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e113_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e115_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e113_c(&mut self,state:State)->Step {
let mut out=self.b113_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'as'"]);}
out
}
fn b113_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e114_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
if count>=1 {break;}
}
let _=count;
if count>1 {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e114_c(&mut self,state:State)->Step {
let mut out=self.b114_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,114,9);
} else {self.display_failures(state.consumed.max(state.matched),&["'as'"]);}
out
}
fn b114_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"as",true,47,false,"as");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'as'");}
out
}
fn e115_c(&mut self,state:State)->Step {
let mut out=self.b115_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'Boolean'", "'Number'", "'Object'", "'String'", "'boolean'", "'number'", "'object'", "'string'"]);}
out
}
fn b115_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.e116_c(out.state);
out
}
fn e116_c(&mut self,state:State)->Step {
let mut out=self.b116_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'Boolean'", "'Number'", "'Object'", "'String'", "'boolean'", "'number'", "'object'", "'string'"]);}
out
}
fn b116_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out='choice: {let mark=self.mark();let mut diagnostic=Diag::NONE;let mut best:Option<(Step,[usize;2])>=None;
self.restore(mark);let mut child=self.e117_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e118_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e119_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e120_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e121_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e122_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e123_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e124_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);break 'choice if let Some((mut child,effects))=best {self.replay_effects(effects);child.diag=diagnostic;child} else {out.ok=false;out.diag=diagnostic;out};
};
out
}
fn e117_c(&mut self,state:State)->Step {
let mut out=self.b117_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,117,9);
} else {self.display_failures(state.consumed.max(state.matched),&["'number'"]);}
out
}
fn b117_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"number",true,76,false,"number");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'number'");}
out
}
fn e118_c(&mut self,state:State)->Step {
let mut out=self.b118_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,118,9);
} else {self.display_failures(state.consumed.max(state.matched),&["'Number'"]);}
out
}
fn b118_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"Number",true,35,false,"Number");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'Number'");}
out
}
fn e119_c(&mut self,state:State)->Step {
let mut out=self.b119_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,119,9);
} else {self.display_failures(state.consumed.max(state.matched),&["'string'"]);}
out
}
fn b119_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"string",true,86,false,"string");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'string'");}
out
}
fn e120_c(&mut self,state:State)->Step {
let mut out=self.b120_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,120,9);
} else {self.display_failures(state.consumed.max(state.matched),&["'String'"]);}
out
}
fn b120_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"String",true,39,false,"String");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'String'");}
out
}
fn e121_c(&mut self,state:State)->Step {
let mut out=self.b121_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,121,9);
} else {self.display_failures(state.consumed.max(state.matched),&["'boolean'"]);}
out
}
fn b121_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"boolean",true,48,false,"boolean");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'boolean'");}
out
}
fn e122_c(&mut self,state:State)->Step {
let mut out=self.b122_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,122,9);
} else {self.display_failures(state.consumed.max(state.matched),&["'Boolean'"]);}
out
}
fn b122_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"Boolean",true,31,false,"Boolean");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'Boolean'");}
out
}
fn e123_c(&mut self,state:State)->Step {
let mut out=self.b123_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,123,9);
} else {self.display_failures(state.consumed.max(state.matched),&["'object'"]);}
out
}
fn b123_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"object",true,77,false,"object");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'object'");}
out
}
fn e124_c(&mut self,state:State)->Step {
let mut out=self.b124_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,124,9);
} else {self.display_failures(state.consumed.max(state.matched),&["'Object'"]);}
out
}
fn b124_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"Object",true,36,false,"Object");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'Object'");}
out
}
fn e125_c(&mut self,state:State)->Step {
let mut out=self.b125_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b125_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e126_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e128_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e126_c(&mut self,state:State)->Step {
let mut out=self.b126_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'as'"]);}
out
}
fn b126_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e127_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
if count>=1 {break;}
}
let _=count;
if count>1 {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e127_c(&mut self,state:State)->Step {
let mut out=self.b127_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,127,10);
} else {self.display_failures(state.consumed.max(state.matched),&["'as'"]);}
out
}
fn b127_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"as",true,47,false,"as");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'as'");}
out
}
fn e128_c(&mut self,state:State)->Step {
let mut out=self.b128_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'Float'", "'Number'", "'float'", "'number'"]);}
out
}
fn b128_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.e129_c(out.state);
out
}
fn e129_c(&mut self,state:State)->Step {
let mut out=self.b129_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'Float'", "'Number'", "'float'", "'number'"]);}
out
}
fn b129_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out='choice: {let mark=self.mark();let mut diagnostic=Diag::NONE;let mut best:Option<(Step,[usize;2])>=None;
self.restore(mark);let mut child=self.e130_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e131_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e132_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e133_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);break 'choice if let Some((mut child,effects))=best {self.replay_effects(effects);child.diag=diagnostic;child} else {out.ok=false;out.diag=diagnostic;out};
};
out
}
fn e130_c(&mut self,state:State)->Step {
let mut out=self.b130_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,130,10);
} else {self.display_failures(state.consumed.max(state.matched),&["'number'"]);}
out
}
fn b130_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"number",true,76,false,"number");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'number'");}
out
}
fn e131_c(&mut self,state:State)->Step {
let mut out=self.b131_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,131,10);
} else {self.display_failures(state.consumed.max(state.matched),&["'Number'"]);}
out
}
fn b131_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"Number",true,35,false,"Number");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'Number'");}
out
}
fn e132_c(&mut self,state:State)->Step {
let mut out=self.b132_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,132,10);
} else {self.display_failures(state.consumed.max(state.matched),&["'float'"]);}
out
}
fn b132_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"float",true,61,false,"float");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'float'");}
out
}
fn e133_c(&mut self,state:State)->Step {
let mut out=self.b133_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,133,10);
} else {self.display_failures(state.consumed.max(state.matched),&["'Float'"]);}
out
}
fn b133_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"Float",true,33,false,"Float");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'Float'");}
out
}
fn e134_c(&mut self,state:State)->Step {
let mut out=self.b134_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b134_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e135_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e137_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e135_c(&mut self,state:State)->Step {
let mut out=self.b135_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'as'"]);}
out
}
fn b135_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e136_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
if count>=1 {break;}
}
let _=count;
if count>1 {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e136_c(&mut self,state:State)->Step {
let mut out=self.b136_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,136,11);
} else {self.display_failures(state.consumed.max(state.matched),&["'as'"]);}
out
}
fn b136_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"as",true,47,false,"as");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'as'");}
out
}
fn e137_c(&mut self,state:State)->Step {
let mut out=self.b137_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'String'", "'string'"]);}
out
}
fn b137_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.e138_c(out.state);
out
}
fn e138_c(&mut self,state:State)->Step {
let mut out=self.b138_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'String'", "'string'"]);}
out
}
fn b138_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out='choice: {let mark=self.mark();let mut diagnostic=Diag::NONE;let mut best:Option<(Step,[usize;2])>=None;
self.restore(mark);let mut child=self.e139_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e140_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);break 'choice if let Some((mut child,effects))=best {self.replay_effects(effects);child.diag=diagnostic;child} else {out.ok=false;out.diag=diagnostic;out};
};
out
}
fn e139_c(&mut self,state:State)->Step {
let mut out=self.b139_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,139,11);
} else {self.display_failures(state.consumed.max(state.matched),&["'string'"]);}
out
}
fn b139_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"string",true,86,false,"string");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'string'");}
out
}
fn e140_c(&mut self,state:State)->Step {
let mut out=self.b140_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,140,11);
} else {self.display_failures(state.consumed.max(state.matched),&["'String'"]);}
out
}
fn b140_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"String",true,39,false,"String");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'String'");}
out
}
fn e141_c(&mut self,state:State)->Step {
let mut out=self.b141_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b141_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e142_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e144_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e142_c(&mut self,state:State)->Step {
let mut out=self.b142_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'as'"]);}
out
}
fn b142_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e143_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
if count>=1 {break;}
}
let _=count;
if count>1 {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e143_c(&mut self,state:State)->Step {
let mut out=self.b143_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,143,12);
} else {self.display_failures(state.consumed.max(state.matched),&["'as'"]);}
out
}
fn b143_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"as",true,47,false,"as");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'as'");}
out
}
fn e144_c(&mut self,state:State)->Step {
let mut out=self.b144_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'Boolean'", "'boolean'"]);}
out
}
fn b144_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.e145_c(out.state);
out
}
fn e145_c(&mut self,state:State)->Step {
let mut out=self.b145_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'Boolean'", "'boolean'"]);}
out
}
fn b145_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out='choice: {let mark=self.mark();let mut diagnostic=Diag::NONE;let mut best:Option<(Step,[usize;2])>=None;
self.restore(mark);let mut child=self.e146_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e147_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);break 'choice if let Some((mut child,effects))=best {self.replay_effects(effects);child.diag=diagnostic;child} else {out.ok=false;out.diag=diagnostic;out};
};
out
}
fn e146_c(&mut self,state:State)->Step {
let mut out=self.b146_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,146,12);
} else {self.display_failures(state.consumed.max(state.matched),&["'boolean'"]);}
out
}
fn b146_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"boolean",true,48,false,"boolean");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'boolean'");}
out
}
fn e147_c(&mut self,state:State)->Step {
let mut out=self.b147_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,147,12);
} else {self.display_failures(state.consumed.max(state.matched),&["'Boolean'"]);}
out
}
fn b147_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"Boolean",true,31,false,"Boolean");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'Boolean'");}
out
}
fn e148_c(&mut self,state:State)->Step {
let mut out=self.b148_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b148_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e149_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e151_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e149_c(&mut self,state:State)->Step {
let mut out=self.b149_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'as'"]);}
out
}
fn b149_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e150_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
if count>=1 {break;}
}
let _=count;
if count>1 {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e150_c(&mut self,state:State)->Step {
let mut out=self.b150_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,150,13);
} else {self.display_failures(state.consumed.max(state.matched),&["'as'"]);}
out
}
fn b150_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"as",true,47,false,"as");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'as'");}
out
}
fn e151_c(&mut self,state:State)->Step {
let mut out=self.b151_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'Object'", "'object'"]);}
out
}
fn b151_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.e152_c(out.state);
out
}
fn e152_c(&mut self,state:State)->Step {
let mut out=self.b152_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'Object'", "'object'"]);}
out
}
fn b152_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out='choice: {let mark=self.mark();let mut diagnostic=Diag::NONE;let mut best:Option<(Step,[usize;2])>=None;
self.restore(mark);let mut child=self.e153_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e154_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);break 'choice if let Some((mut child,effects))=best {self.replay_effects(effects);child.diag=diagnostic;child} else {out.ok=false;out.diag=diagnostic;out};
};
out
}
fn e153_c(&mut self,state:State)->Step {
let mut out=self.b153_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,153,13);
} else {self.display_failures(state.consumed.max(state.matched),&["'object'"]);}
out
}
fn b153_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"object",true,77,false,"object");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'object'");}
out
}
fn e154_c(&mut self,state:State)->Step {
let mut out=self.b154_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,154,13);
} else {self.display_failures(state.consumed.max(state.matched),&["'Object'"]);}
out
}
fn b154_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"Object",true,36,false,"Object");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'Object'");}
out
}
fn e155_c(&mut self,state:State)->Step {
let mut out=self.b155_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b155_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e156_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e157_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e158_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e156_c(&mut self,state:State)->Step {
let mut out=self.b156_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,156,14);
} else {self.display_failures(state.consumed.max(state.matched),&["'if'"]);}
out
}
fn b156_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"if",true,63,false,"if");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'if'");}
out
}
fn e157_c(&mut self,state:State)->Step {
let mut out=self.b157_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,157,14);
} else {self.display_failures(state.consumed.max(state.matched),&["'not'"]);}
out
}
fn b157_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"not",true,75,false,"not");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'not'");}
out
}
fn e158_c(&mut self,state:State)->Step {
let mut out=self.b158_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,158,14);
} else {self.display_failures(state.consumed.max(state.matched),&["'exists'"]);}
out
}
fn b158_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"exists",true,57,false,"exists");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'exists'");}
out
}
fn e159_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:159,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b159_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b159_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e160_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e161_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e162_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e160_c(&mut self,state:State)->Step {
let mut out=self.b160_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,160,15);
} else {self.display_failures(state.consumed.max(state.matched),&["'description'"]);}
out
}
fn b160_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"description",true,54,false,"description");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'description'");}
out
}
fn e161_c(&mut self,state:State)->Step {
let mut out=self.b161_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,161,15);
} else {self.display_failures(state.consumed.max(state.matched),&["'='"]);}
out
}
fn b161_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"=",true,25,false,"=");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'='");}
out
}
fn e162_c(&mut self,state:State)->Step {
let mut out=self.b162_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,162,15);
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b162_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.token_2::<false>(out.state,"STRING","StringLiteralParser");
out
}
fn e163_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:163,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b163_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b163_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e164_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e165_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e166_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e167_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e169_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e164_c(&mut self,state:State)->Step {
let mut out=self.b164_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,164,16);
} else {self.display_failures(state.consumed.max(state.matched),&["'@'"]);}
out
}
fn b164_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"@",true,30,false,"@");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'@'");}
out
}
fn e165_c(&mut self,state:State)->Step {
let mut out=self.b165_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,165,16);
} else {self.display_failures(state.consumed.max(state.matched),&["IdentifierParser"]);}
out
}
fn b165_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.token_1::<false>(out.state,"IDENTIFIER","IdentifierParser");
out
}
fn e166_c(&mut self,state:State)->Step {
let mut out=self.b166_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,166,16);
} else {self.display_failures(state.consumed.max(state.matched),&["'('"]);}
out
}
fn b166_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"(",true,4,false,"(");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'('");}
out
}
fn e167_c(&mut self,state:State)->Step {
let mut out=self.b167_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.display_failures(state.consumed.max(state.matched),&["Optional"]);}
out
}
fn b167_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e168_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
if count>=1 {break;}
}
let _=count;
if count>1 {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e168_c(&mut self,state:State)->Step {
let mut out=self.b168_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b168_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r17_c(out.state);
out
}
fn e169_c(&mut self,state:State)->Step {
let mut out=self.b169_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,169,16);
} else {self.display_failures(state.consumed.max(state.matched),&["')'"]);}
out
}
fn b169_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,")",true,5,false,")");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"')'");}
out
}
fn e170_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:170,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b170_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b170_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e171_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e172_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e171_c(&mut self,state:State)->Step {
let mut out=self.b171_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b171_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r18_c(out.state);
out
}
fn e172_c(&mut self,state:State)->Step {
let mut out=self.b172_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.display_failures(state.consumed.max(state.matched),&["','"]);}
out
}
fn b172_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e173_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
}
let _=count;
if false {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e173_c(&mut self,state:State)->Step {
let mut out=self.b173_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.display_failures(state.consumed.max(state.matched),&["','", "'='"]);}
out
}
fn b173_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e174_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e175_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e174_c(&mut self,state:State)->Step {
let mut out=self.b174_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,174,17);
} else {self.display_failures(state.consumed.max(state.matched),&["','"]);}
out
}
fn b174_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,",",true,8,false,",");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"','");}
out
}
fn e175_c(&mut self,state:State)->Step {
let mut out=self.b175_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b175_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r18_c(out.state);
out
}
fn e176_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:176,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b176_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b176_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e177_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e178_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e179_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e177_c(&mut self,state:State)->Step {
let mut out=self.b177_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,177,18);
} else {self.display_failures(state.consumed.max(state.matched),&["IdentifierParser"]);}
out
}
fn b177_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.token_1::<false>(out.state,"IDENTIFIER","IdentifierParser");
out
}
fn e178_c(&mut self,state:State)->Step {
let mut out=self.b178_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,178,18);
} else {self.display_failures(state.consumed.max(state.matched),&["'='"]);}
out
}
fn b178_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"=",true,25,false,"=");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'='");}
out
}
fn e179_c(&mut self,state:State)->Step {
let mut out=self.b179_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b179_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r123_c(out.state);
out
}
fn e180_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:180,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b180_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b180_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out='choice: {let mark=self.mark();let mut diagnostic=Diag::NONE;let mut best:Option<(Step,[usize;2])>=None;
self.restore(mark);let mut child=self.e181_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e182_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e183_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e184_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);break 'choice if let Some((mut child,effects))=best {self.replay_effects(effects);child.diag=diagnostic;child} else {out.ok=false;out.diag=diagnostic;out};
};
out
}
fn e181_c(&mut self,state:State)->Step {
let mut out=self.b181_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b181_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r20_c(out.state);
out
}
fn e182_c(&mut self,state:State)->Step {
let mut out=self.b182_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b182_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r21_c(out.state);
out
}
fn e183_c(&mut self,state:State)->Step {
let mut out=self.b183_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b183_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r22_c(out.state);
out
}
fn e184_c(&mut self,state:State)->Step {
let mut out=self.b184_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b184_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r23_c(out.state);
out
}
fn e185_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:185,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b185_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b185_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e186_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e187_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e188_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e189_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e191_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e192_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e193_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e194_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e186_c(&mut self,state:State)->Step {
let mut out=self.b186_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b186_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r26_c(out.state);
out
}
fn e187_c(&mut self,state:State)->Step {
let mut out=self.b187_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,187,20);
out.events=self.event(Event::Capture {site:27,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["IdentifierParser", "__CaptureSite"]);}
out
}
fn b187_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.token_1::<false>(out.state,"IDENTIFIER","IdentifierParser");
out
}
fn e188_c(&mut self,state:State)->Step {
let mut out=self.b188_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,188,20);
} else {self.display_failures(state.consumed.max(state.matched),&["'('"]);}
out
}
fn b188_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"(",true,4,false,"(");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'('");}
out
}
fn e189_c(&mut self,state:State)->Step {
let mut out=self.b189_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.display_failures(state.consumed.max(state.matched),&["Optional"]);}
out
}
fn b189_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e190_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
if count>=1 {break;}
}
let _=count;
if count>1 {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e190_c(&mut self,state:State)->Step {
let mut out=self.b190_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:28,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b190_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r24_c(out.state);
out
}
fn e191_c(&mut self,state:State)->Step {
let mut out=self.b191_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,191,20);
} else {self.display_failures(state.consumed.max(state.matched),&["')'"]);}
out
}
fn b191_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,")",true,5,false,")");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"')'");}
out
}
fn e192_c(&mut self,state:State)->Step {
let mut out=self.b192_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,192,20);
} else {self.display_failures(state.consumed.max(state.matched),&["'{'"]);}
out
}
fn b192_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"{",true,95,false,"{");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'{'");}
out
}
fn e193_c(&mut self,state:State)->Step {
let mut out=self.b193_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:29,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b193_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r40_c(out.state);
out
}
fn e194_c(&mut self,state:State)->Step {
let mut out=self.b194_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,194,20);
} else {self.display_failures(state.consumed.max(state.matched),&["'}'"]);}
out
}
fn b194_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"}",true,97,false,"}");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'}'");}
out
}
fn e195_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:195,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b195_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b195_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e196_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e197_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e198_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e199_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e201_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e202_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e203_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e204_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e196_c(&mut self,state:State)->Step {
let mut out=self.b196_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b196_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r27_c(out.state);
out
}
fn e197_c(&mut self,state:State)->Step {
let mut out=self.b197_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,197,21);
out.events=self.event(Event::Capture {site:30,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["IdentifierParser", "__CaptureSite"]);}
out
}
fn b197_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.token_1::<false>(out.state,"IDENTIFIER","IdentifierParser");
out
}
fn e198_c(&mut self,state:State)->Step {
let mut out=self.b198_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,198,21);
} else {self.display_failures(state.consumed.max(state.matched),&["'('"]);}
out
}
fn b198_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"(",true,4,false,"(");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'('");}
out
}
fn e199_c(&mut self,state:State)->Step {
let mut out=self.b199_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.display_failures(state.consumed.max(state.matched),&["Optional"]);}
out
}
fn b199_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e200_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
if count>=1 {break;}
}
let _=count;
if count>1 {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e200_c(&mut self,state:State)->Step {
let mut out=self.b200_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:31,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b200_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r24_c(out.state);
out
}
fn e201_c(&mut self,state:State)->Step {
let mut out=self.b201_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,201,21);
} else {self.display_failures(state.consumed.max(state.matched),&["')'"]);}
out
}
fn b201_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,")",true,5,false,")");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"')'");}
out
}
fn e202_c(&mut self,state:State)->Step {
let mut out=self.b202_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,202,21);
} else {self.display_failures(state.consumed.max(state.matched),&["'{'"]);}
out
}
fn b202_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"{",true,95,false,"{");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'{'");}
out
}
fn e203_c(&mut self,state:State)->Step {
let mut out=self.b203_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:32,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b203_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r89_c(out.state);
out
}
fn e204_c(&mut self,state:State)->Step {
let mut out=self.b204_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,204,21);
} else {self.display_failures(state.consumed.max(state.matched),&["'}'"]);}
out
}
fn b204_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"}",true,97,false,"}");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'}'");}
out
}
fn e205_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:205,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b205_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b205_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e206_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e207_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e208_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e209_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e211_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e212_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e213_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e214_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e206_c(&mut self,state:State)->Step {
let mut out=self.b206_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b206_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r28_c(out.state);
out
}
fn e207_c(&mut self,state:State)->Step {
let mut out=self.b207_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,207,22);
out.events=self.event(Event::Capture {site:33,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["IdentifierParser", "__CaptureSite"]);}
out
}
fn b207_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.token_1::<false>(out.state,"IDENTIFIER","IdentifierParser");
out
}
fn e208_c(&mut self,state:State)->Step {
let mut out=self.b208_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,208,22);
} else {self.display_failures(state.consumed.max(state.matched),&["'('"]);}
out
}
fn b208_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"(",true,4,false,"(");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'('");}
out
}
fn e209_c(&mut self,state:State)->Step {
let mut out=self.b209_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.display_failures(state.consumed.max(state.matched),&["Optional"]);}
out
}
fn b209_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e210_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
if count>=1 {break;}
}
let _=count;
if count>1 {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e210_c(&mut self,state:State)->Step {
let mut out=self.b210_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:34,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b210_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r24_c(out.state);
out
}
fn e211_c(&mut self,state:State)->Step {
let mut out=self.b211_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,211,22);
} else {self.display_failures(state.consumed.max(state.matched),&["')'"]);}
out
}
fn b211_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,")",true,5,false,")");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"')'");}
out
}
fn e212_c(&mut self,state:State)->Step {
let mut out=self.b212_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,212,22);
} else {self.display_failures(state.consumed.max(state.matched),&["'{'"]);}
out
}
fn b212_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"{",true,95,false,"{");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'{'");}
out
}
fn e213_c(&mut self,state:State)->Step {
let mut out=self.b213_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:35,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b213_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r94_c(out.state);
out
}
fn e214_c(&mut self,state:State)->Step {
let mut out=self.b214_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,214,22);
} else {self.display_failures(state.consumed.max(state.matched),&["'}'"]);}
out
}
fn b214_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"}",true,97,false,"}");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'}'");}
out
}
fn e215_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:215,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b215_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b215_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e216_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e217_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e218_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e219_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e221_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e222_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e223_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e224_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e216_c(&mut self,state:State)->Step {
let mut out=self.b216_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b216_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r29_c(out.state);
out
}
fn e217_c(&mut self,state:State)->Step {
let mut out=self.b217_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,217,23);
out.events=self.event(Event::Capture {site:36,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["IdentifierParser", "__CaptureSite"]);}
out
}
fn b217_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.token_1::<false>(out.state,"IDENTIFIER","IdentifierParser");
out
}
fn e218_c(&mut self,state:State)->Step {
let mut out=self.b218_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,218,23);
} else {self.display_failures(state.consumed.max(state.matched),&["'('"]);}
out
}
fn b218_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"(",true,4,false,"(");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'('");}
out
}
fn e219_c(&mut self,state:State)->Step {
let mut out=self.b219_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.display_failures(state.consumed.max(state.matched),&["Optional"]);}
out
}
fn b219_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e220_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
if count>=1 {break;}
}
let _=count;
if count>1 {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e220_c(&mut self,state:State)->Step {
let mut out=self.b220_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:37,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b220_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r24_c(out.state);
out
}
fn e221_c(&mut self,state:State)->Step {
let mut out=self.b221_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,221,23);
} else {self.display_failures(state.consumed.max(state.matched),&["')'"]);}
out
}
fn b221_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,")",true,5,false,")");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"')'");}
out
}
fn e222_c(&mut self,state:State)->Step {
let mut out=self.b222_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,222,23);
} else {self.display_failures(state.consumed.max(state.matched),&["'{'"]);}
out
}
fn b222_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"{",true,95,false,"{");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'{'");}
out
}
fn e223_c(&mut self,state:State)->Step {
let mut out=self.b223_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:38,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b223_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r105_c(out.state);
out
}
fn e224_c(&mut self,state:State)->Step {
let mut out=self.b224_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,224,23);
} else {self.display_failures(state.consumed.max(state.matched),&["'}'"]);}
out
}
fn b224_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"}",true,97,false,"}");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'}'");}
out
}
fn e225_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:225,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b225_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b225_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e226_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e227_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e226_c(&mut self,state:State)->Step {
let mut out=self.b226_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:39,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["'$'"]);}
out
}
fn b226_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r25_c(out.state);
out
}
fn e227_c(&mut self,state:State)->Step {
let mut out=self.b227_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.display_failures(state.consumed.max(state.matched),&["','"]);}
out
}
fn b227_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e228_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
}
let _=count;
if false {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e228_c(&mut self,state:State)->Step {
let mut out=self.b228_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.display_failures(state.consumed.max(state.matched),&["','"]);}
out
}
fn b228_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e229_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e230_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e229_c(&mut self,state:State)->Step {
let mut out=self.b229_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,229,24);
} else {self.display_failures(state.consumed.max(state.matched),&["','"]);}
out
}
fn b229_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,",",true,8,false,",");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"','");}
out
}
fn e230_c(&mut self,state:State)->Step {
let mut out=self.b230_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:40,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["'$'"]);}
out
}
fn b230_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r25_c(out.state);
out
}
fn e231_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:231,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b231_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b231_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e232_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e233_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e234_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e232_c(&mut self,state:State)->Step {
let mut out=self.b232_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,232,25);
} else {self.display_failures(state.consumed.max(state.matched),&["'$'"]);}
out
}
fn b232_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"$",true,2,false,"$");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'$'");}
out
}
fn e233_c(&mut self,state:State)->Step {
let mut out=self.b233_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,233,25);
out.events=self.event(Event::Capture {site:41,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["IdentifierParser", "__CaptureSite"]);}
out
}
fn b233_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.token_1::<false>(out.state,"IDENTIFIER","IdentifierParser");
out
}
fn e234_c(&mut self,state:State)->Step {
let mut out=self.b234_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'as'"]);}
out
}
fn b234_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e235_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
if count>=1 {break;}
}
let _=count;
if count>1 {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e235_c(&mut self,state:State)->Step {
let mut out=self.b235_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'as'"]);}
out
}
fn b235_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e236_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e237_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e236_c(&mut self,state:State)->Step {
let mut out=self.b236_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,236,25);
} else {self.display_failures(state.consumed.max(state.matched),&["'as'"]);}
out
}
fn b236_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"as",true,47,false,"as");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'as'");}
out
}
fn e237_c(&mut self,state:State)->Step {
let mut out=self.b237_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:42,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b237_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r30_c(out.state);
out
}
fn e238_c(&mut self,state:State)->Step {
let mut out=self.b238_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b238_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out='choice: {let mark=self.mark();let mut diagnostic=Diag::NONE;let mut best:Option<(Step,[usize;2])>=None;
self.restore(mark);let mut child=self.e239_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e240_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);break 'choice if let Some((mut child,effects))=best {self.replay_effects(effects);child.diag=diagnostic;child} else {out.ok=false;out.diag=diagnostic;out};
};
out
}
fn e239_c(&mut self,state:State)->Step {
let mut out=self.b239_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,239,26);
} else {self.display_failures(state.consumed.max(state.matched),&["'number'"]);}
out
}
fn b239_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"number",true,76,false,"number");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'number'");}
out
}
fn e240_c(&mut self,state:State)->Step {
let mut out=self.b240_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,240,26);
} else {self.display_failures(state.consumed.max(state.matched),&["'float'"]);}
out
}
fn b240_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"float",true,61,false,"float");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'float'");}
out
}
fn e241_c(&mut self,state:State)->Step {
let mut out=self.b241_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b241_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e242_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e242_c(&mut self,state:State)->Step {
let mut out=self.b242_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,242,27);
} else {self.display_failures(state.consumed.max(state.matched),&["'string'"]);}
out
}
fn b242_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"string",true,86,false,"string");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'string'");}
out
}
fn e243_c(&mut self,state:State)->Step {
let mut out=self.b243_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b243_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e244_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e244_c(&mut self,state:State)->Step {
let mut out=self.b244_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,244,28);
} else {self.display_failures(state.consumed.max(state.matched),&["'boolean'"]);}
out
}
fn b244_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"boolean",true,48,false,"boolean");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'boolean'");}
out
}
fn e245_c(&mut self,state:State)->Step {
let mut out=self.b245_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b245_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e246_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e246_c(&mut self,state:State)->Step {
let mut out=self.b246_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,246,29);
} else {self.display_failures(state.consumed.max(state.matched),&["'object'"]);}
out
}
fn b246_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"object",true,77,false,"object");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'object'");}
out
}
fn e247_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:247,state,matched_mode:false,version:0};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b247_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b247_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out='choice: {let mark=self.mark();let mut diagnostic=Diag::NONE;let mut best:Option<(Step,[usize;2])>=None;
let c0=self.input.cp_at(out.state.begin().position::<false>()).map(|(c,_)|c);
let ct=self.input.cp_at(self.skip_0::<false>(out.state.begin()).position::<false>()).map(|(c,_)|c);
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(c0.is_some_and(|c| matches!(c as u32,102|110))) {self.guard_e248_c(out.state.begin())} else {self.e248_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,115))) {self.guard_e249_c(out.state.begin())} else {self.e249_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,98))) {self.guard_e250_c(out.state.begin())} else {self.e250_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,111))) {self.guard_e251_c(out.state.begin())} else {self.e251_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);break 'choice if let Some((mut child,effects))=best {self.replay_effects(effects);child.diag=diagnostic;child} else {out.ok=false;out.diag=diagnostic;out};
};
out
}
fn e248_c(&mut self,state:State)->Step {
let mut out=self.b248_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b248_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r26_c(out.state);
out
}
fn e249_c(&mut self,state:State)->Step {
let mut out=self.b249_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b249_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r27_c(out.state);
out
}
fn e250_c(&mut self,state:State)->Step {
let mut out=self.b250_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b250_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r28_c(out.state);
out
}
fn e251_c(&mut self,state:State)->Step {
let mut out=self.b251_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b251_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r29_c(out.state);
out
}
fn e252_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:252,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b252_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b252_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e253_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e254_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e259_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e260_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e262_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e269_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e270_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e272_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e253_c(&mut self,state:State)->Step {
let mut out=self.b253_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,253,31);
} else {self.display_failures(state.consumed.max(state.matched),&["'external'"]);}
out
}
fn b253_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"external",true,59,false,"external");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'external'");}
out
}
fn e254_c(&mut self,state:State)->Step {
let mut out=self.b254_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'returning'"]);}
out
}
fn b254_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e255_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
if count>=1 {break;}
}
let _=count;
if count>1 {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e255_c(&mut self,state:State)->Step {
let mut out=self.b255_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'as'", "'returning'"]);}
out
}
fn b255_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e256_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e257_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e256_c(&mut self,state:State)->Step {
let mut out=self.b256_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,256,31);
} else {self.display_failures(state.consumed.max(state.matched),&["'returning'"]);}
out
}
fn b256_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"returning",true,80,false,"returning");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'returning'");}
out
}
fn e257_c(&mut self,state:State)->Step {
let mut out=self.b257_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'as'"]);}
out
}
fn b257_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e258_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
if count>=1 {break;}
}
let _=count;
if count>1 {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e258_c(&mut self,state:State)->Step {
let mut out=self.b258_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,258,31);
} else {self.display_failures(state.consumed.max(state.matched),&["'as'"]);}
out
}
fn b258_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"as",true,47,false,"as");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'as'");}
out
}
fn e259_c(&mut self,state:State)->Step {
let mut out=self.b259_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b259_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r28_c(out.state);
out
}
fn e260_c(&mut self,state:State)->Step {
let mut out=self.b260_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["':'"]);}
out
}
fn b260_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e261_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
if count>=1 {break;}
}
let _=count;
if count>1 {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e261_c(&mut self,state:State)->Step {
let mut out=self.b261_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,261,31);
} else {self.display_failures(state.consumed.max(state.matched),&["':'"]);}
out
}
fn b261_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,":",true,21,false,":");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"':'");}
out
}
fn e262_c(&mut self,state:State)->Step {
let mut out=self.b262_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.display_failures(state.consumed.max(state.matched),&["'#'"]);}
out
}
fn b262_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.e263_c(out.state);
out
}
fn e263_c(&mut self,state:State)->Step {
let mut out=self.b263_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'#'"]);}
out
}
fn b263_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out='choice: {let mark=self.mark();let mut diagnostic=Diag::NONE;let mut best:Option<(Step,[usize;2])>=None;
let ct=self.input.cp_at(self.skip_0::<false>(out.state.begin()).position::<false>()).map(|(c,_)|c);
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,65..=90|95|97..=122))) {self.guard_e264_c(out.state.begin())} else {self.e264_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e268_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);break 'choice if let Some((mut child,effects))=best {self.replay_effects(effects);child.diag=diagnostic;child} else {out.ok=false;out.diag=diagnostic;out};
};
out
}
fn e264_c(&mut self,state:State)->Step {
let mut out=self.b264_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.display_failures(state.consumed.max(state.matched),&["'#'"]);}
out
}
fn b264_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e265_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e266_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e267_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e265_c(&mut self,state:State)->Step {
let mut out=self.b265_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:43,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b265_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r3_c(out.state);
out
}
fn e266_c(&mut self,state:State)->Step {
let mut out=self.b266_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,266,31);
} else {self.display_failures(state.consumed.max(state.matched),&["'#'"]);}
out
}
fn b266_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"#",true,1,false,"#");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'#'");}
out
}
fn e267_c(&mut self,state:State)->Step {
let mut out=self.b267_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,267,31);
out.events=self.event(Event::Capture {site:44,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["IdentifierParser", "__CaptureSite"]);}
out
}
fn b267_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.token_1::<false>(out.state,"IDENTIFIER","IdentifierParser");
out
}
fn e268_c(&mut self,state:State)->Step {
let mut out=self.b268_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,268,31);
out.events=self.event(Event::Capture {site:45,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["IdentifierParser", "__CaptureSite"]);}
out
}
fn b268_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.token_1::<false>(out.state,"IDENTIFIER","IdentifierParser");
out
}
fn e269_c(&mut self,state:State)->Step {
let mut out=self.b269_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,269,31);
} else {self.display_failures(state.consumed.max(state.matched),&["'('"]);}
out
}
fn b269_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"(",true,4,false,"(");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'('");}
out
}
fn e270_c(&mut self,state:State)->Step {
let mut out=self.b270_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.display_failures(state.consumed.max(state.matched),&["Optional"]);}
out
}
fn b270_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e271_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
if count>=1 {break;}
}
let _=count;
if count>1 {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e271_c(&mut self,state:State)->Step {
let mut out=self.b271_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:46,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b271_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r39_c(out.state);
out
}
fn e272_c(&mut self,state:State)->Step {
let mut out=self.b272_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,272,31);
} else {self.display_failures(state.consumed.max(state.matched),&["')'"]);}
out
}
fn b272_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,")",true,5,false,")");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"')'");}
out
}
fn e273_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:273,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b273_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b273_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e274_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e275_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e288_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e295_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e296_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e298_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e274_c(&mut self,state:State)->Step {
let mut out=self.b274_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,274,32);
} else {self.display_failures(state.consumed.max(state.matched),&["'external'"]);}
out
}
fn b274_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"external",true,59,false,"external");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'external'");}
out
}
fn e275_c(&mut self,state:State)->Step {
let mut out=self.b275_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["':'"]);}
out
}
fn b275_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.e276_c(out.state);
out
}
fn e276_c(&mut self,state:State)->Step {
let mut out=self.b276_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["':'"]);}
out
}
fn b276_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out='choice: {let mark=self.mark();let mut diagnostic=Diag::NONE;let mut best:Option<(Step,[usize;2])>=None;
self.restore(mark);let mut child=self.e277_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e286_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);break 'choice if let Some((mut child,effects))=best {self.replay_effects(effects);child.diag=diagnostic;child} else {out.ok=false;out.diag=diagnostic;out};
};
out
}
fn e277_c(&mut self,state:State)->Step {
let mut out=self.b277_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["':'", "'float'", "'number'"]);}
out
}
fn b277_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e278_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e283_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e284_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e278_c(&mut self,state:State)->Step {
let mut out=self.b278_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'returning'"]);}
out
}
fn b278_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e279_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
if count>=1 {break;}
}
let _=count;
if count>1 {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e279_c(&mut self,state:State)->Step {
let mut out=self.b279_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'as'", "'returning'"]);}
out
}
fn b279_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e280_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e281_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e280_c(&mut self,state:State)->Step {
let mut out=self.b280_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,280,32);
} else {self.display_failures(state.consumed.max(state.matched),&["'returning'"]);}
out
}
fn b280_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"returning",true,80,false,"returning");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'returning'");}
out
}
fn e281_c(&mut self,state:State)->Step {
let mut out=self.b281_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'as'"]);}
out
}
fn b281_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e282_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
if count>=1 {break;}
}
let _=count;
if count>1 {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e282_c(&mut self,state:State)->Step {
let mut out=self.b282_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,282,32);
} else {self.display_failures(state.consumed.max(state.matched),&["'as'"]);}
out
}
fn b282_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"as",true,47,false,"as");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'as'");}
out
}
fn e283_c(&mut self,state:State)->Step {
let mut out=self.b283_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b283_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r26_c(out.state);
out
}
fn e284_c(&mut self,state:State)->Step {
let mut out=self.b284_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["':'"]);}
out
}
fn b284_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e285_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
if count>=1 {break;}
}
let _=count;
if count>1 {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e285_c(&mut self,state:State)->Step {
let mut out=self.b285_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,285,32);
} else {self.display_failures(state.consumed.max(state.matched),&["':'"]);}
out
}
fn b285_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,":",true,21,false,":");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"':'");}
out
}
fn e286_c(&mut self,state:State)->Step {
let mut out=self.b286_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["':'"]);}
out
}
fn b286_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e287_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
if count>=1 {break;}
}
let _=count;
if count>1 {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e287_c(&mut self,state:State)->Step {
let mut out=self.b287_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,287,32);
} else {self.display_failures(state.consumed.max(state.matched),&["':'"]);}
out
}
fn b287_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,":",true,21,false,":");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"':'");}
out
}
fn e288_c(&mut self,state:State)->Step {
let mut out=self.b288_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.display_failures(state.consumed.max(state.matched),&["'#'"]);}
out
}
fn b288_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.e289_c(out.state);
out
}
fn e289_c(&mut self,state:State)->Step {
let mut out=self.b289_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'#'"]);}
out
}
fn b289_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out='choice: {let mark=self.mark();let mut diagnostic=Diag::NONE;let mut best:Option<(Step,[usize;2])>=None;
let ct=self.input.cp_at(self.skip_0::<false>(out.state.begin()).position::<false>()).map(|(c,_)|c);
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,65..=90|95|97..=122))) {self.guard_e290_c(out.state.begin())} else {self.e290_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e294_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);break 'choice if let Some((mut child,effects))=best {self.replay_effects(effects);child.diag=diagnostic;child} else {out.ok=false;out.diag=diagnostic;out};
};
out
}
fn e290_c(&mut self,state:State)->Step {
let mut out=self.b290_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.display_failures(state.consumed.max(state.matched),&["'#'"]);}
out
}
fn b290_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e291_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e292_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e293_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e291_c(&mut self,state:State)->Step {
let mut out=self.b291_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:47,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b291_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r3_c(out.state);
out
}
fn e292_c(&mut self,state:State)->Step {
let mut out=self.b292_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,292,32);
} else {self.display_failures(state.consumed.max(state.matched),&["'#'"]);}
out
}
fn b292_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"#",true,1,false,"#");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'#'");}
out
}
fn e293_c(&mut self,state:State)->Step {
let mut out=self.b293_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,293,32);
out.events=self.event(Event::Capture {site:48,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["IdentifierParser", "__CaptureSite"]);}
out
}
fn b293_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.token_1::<false>(out.state,"IDENTIFIER","IdentifierParser");
out
}
fn e294_c(&mut self,state:State)->Step {
let mut out=self.b294_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,294,32);
out.events=self.event(Event::Capture {site:49,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["IdentifierParser", "__CaptureSite"]);}
out
}
fn b294_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.token_1::<false>(out.state,"IDENTIFIER","IdentifierParser");
out
}
fn e295_c(&mut self,state:State)->Step {
let mut out=self.b295_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,295,32);
} else {self.display_failures(state.consumed.max(state.matched),&["'('"]);}
out
}
fn b295_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"(",true,4,false,"(");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'('");}
out
}
fn e296_c(&mut self,state:State)->Step {
let mut out=self.b296_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.display_failures(state.consumed.max(state.matched),&["Optional"]);}
out
}
fn b296_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e297_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
if count>=1 {break;}
}
let _=count;
if count>1 {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e297_c(&mut self,state:State)->Step {
let mut out=self.b297_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:50,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b297_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r39_c(out.state);
out
}
fn e298_c(&mut self,state:State)->Step {
let mut out=self.b298_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,298,32);
} else {self.display_failures(state.consumed.max(state.matched),&["')'"]);}
out
}
fn b298_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,")",true,5,false,")");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"')'");}
out
}
fn e299_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:299,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b299_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b299_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e300_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e301_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e306_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e307_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e309_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e316_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e317_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e319_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e300_c(&mut self,state:State)->Step {
let mut out=self.b300_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,300,33);
} else {self.display_failures(state.consumed.max(state.matched),&["'external'"]);}
out
}
fn b300_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"external",true,59,false,"external");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'external'");}
out
}
fn e301_c(&mut self,state:State)->Step {
let mut out=self.b301_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'returning'"]);}
out
}
fn b301_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e302_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
if count>=1 {break;}
}
let _=count;
if count>1 {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e302_c(&mut self,state:State)->Step {
let mut out=self.b302_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'as'", "'returning'"]);}
out
}
fn b302_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e303_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e304_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e303_c(&mut self,state:State)->Step {
let mut out=self.b303_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,303,33);
} else {self.display_failures(state.consumed.max(state.matched),&["'returning'"]);}
out
}
fn b303_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"returning",true,80,false,"returning");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'returning'");}
out
}
fn e304_c(&mut self,state:State)->Step {
let mut out=self.b304_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'as'"]);}
out
}
fn b304_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e305_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
if count>=1 {break;}
}
let _=count;
if count>1 {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e305_c(&mut self,state:State)->Step {
let mut out=self.b305_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,305,33);
} else {self.display_failures(state.consumed.max(state.matched),&["'as'"]);}
out
}
fn b305_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"as",true,47,false,"as");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'as'");}
out
}
fn e306_c(&mut self,state:State)->Step {
let mut out=self.b306_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b306_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r27_c(out.state);
out
}
fn e307_c(&mut self,state:State)->Step {
let mut out=self.b307_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["':'"]);}
out
}
fn b307_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e308_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
if count>=1 {break;}
}
let _=count;
if count>1 {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e308_c(&mut self,state:State)->Step {
let mut out=self.b308_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,308,33);
} else {self.display_failures(state.consumed.max(state.matched),&["':'"]);}
out
}
fn b308_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,":",true,21,false,":");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"':'");}
out
}
fn e309_c(&mut self,state:State)->Step {
let mut out=self.b309_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.display_failures(state.consumed.max(state.matched),&["'#'"]);}
out
}
fn b309_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.e310_c(out.state);
out
}
fn e310_c(&mut self,state:State)->Step {
let mut out=self.b310_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'#'"]);}
out
}
fn b310_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out='choice: {let mark=self.mark();let mut diagnostic=Diag::NONE;let mut best:Option<(Step,[usize;2])>=None;
let ct=self.input.cp_at(self.skip_0::<false>(out.state.begin()).position::<false>()).map(|(c,_)|c);
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,65..=90|95|97..=122))) {self.guard_e311_c(out.state.begin())} else {self.e311_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e315_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);break 'choice if let Some((mut child,effects))=best {self.replay_effects(effects);child.diag=diagnostic;child} else {out.ok=false;out.diag=diagnostic;out};
};
out
}
fn e311_c(&mut self,state:State)->Step {
let mut out=self.b311_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.display_failures(state.consumed.max(state.matched),&["'#'"]);}
out
}
fn b311_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e312_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e313_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e314_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e312_c(&mut self,state:State)->Step {
let mut out=self.b312_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:51,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b312_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r3_c(out.state);
out
}
fn e313_c(&mut self,state:State)->Step {
let mut out=self.b313_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,313,33);
} else {self.display_failures(state.consumed.max(state.matched),&["'#'"]);}
out
}
fn b313_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"#",true,1,false,"#");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'#'");}
out
}
fn e314_c(&mut self,state:State)->Step {
let mut out=self.b314_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,314,33);
out.events=self.event(Event::Capture {site:52,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["IdentifierParser", "__CaptureSite"]);}
out
}
fn b314_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.token_1::<false>(out.state,"IDENTIFIER","IdentifierParser");
out
}
fn e315_c(&mut self,state:State)->Step {
let mut out=self.b315_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,315,33);
out.events=self.event(Event::Capture {site:53,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["IdentifierParser", "__CaptureSite"]);}
out
}
fn b315_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.token_1::<false>(out.state,"IDENTIFIER","IdentifierParser");
out
}
fn e316_c(&mut self,state:State)->Step {
let mut out=self.b316_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,316,33);
} else {self.display_failures(state.consumed.max(state.matched),&["'('"]);}
out
}
fn b316_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"(",true,4,false,"(");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'('");}
out
}
fn e317_c(&mut self,state:State)->Step {
let mut out=self.b317_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.display_failures(state.consumed.max(state.matched),&["Optional"]);}
out
}
fn b317_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e318_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
if count>=1 {break;}
}
let _=count;
if count>1 {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e318_c(&mut self,state:State)->Step {
let mut out=self.b318_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:54,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b318_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r39_c(out.state);
out
}
fn e319_c(&mut self,state:State)->Step {
let mut out=self.b319_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,319,33);
} else {self.display_failures(state.consumed.max(state.matched),&["')'"]);}
out
}
fn b319_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,")",true,5,false,")");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"')'");}
out
}
fn e320_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:320,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b320_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b320_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e321_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e322_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e327_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e328_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e330_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e337_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e338_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e340_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e321_c(&mut self,state:State)->Step {
let mut out=self.b321_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,321,34);
} else {self.display_failures(state.consumed.max(state.matched),&["'external'"]);}
out
}
fn b321_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"external",true,59,false,"external");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'external'");}
out
}
fn e322_c(&mut self,state:State)->Step {
let mut out=self.b322_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'returning'"]);}
out
}
fn b322_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e323_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
if count>=1 {break;}
}
let _=count;
if count>1 {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e323_c(&mut self,state:State)->Step {
let mut out=self.b323_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'as'", "'returning'"]);}
out
}
fn b323_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e324_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e325_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e324_c(&mut self,state:State)->Step {
let mut out=self.b324_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,324,34);
} else {self.display_failures(state.consumed.max(state.matched),&["'returning'"]);}
out
}
fn b324_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"returning",true,80,false,"returning");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'returning'");}
out
}
fn e325_c(&mut self,state:State)->Step {
let mut out=self.b325_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'as'"]);}
out
}
fn b325_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e326_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
if count>=1 {break;}
}
let _=count;
if count>1 {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e326_c(&mut self,state:State)->Step {
let mut out=self.b326_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,326,34);
} else {self.display_failures(state.consumed.max(state.matched),&["'as'"]);}
out
}
fn b326_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"as",true,47,false,"as");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'as'");}
out
}
fn e327_c(&mut self,state:State)->Step {
let mut out=self.b327_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b327_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r29_c(out.state);
out
}
fn e328_c(&mut self,state:State)->Step {
let mut out=self.b328_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["':'"]);}
out
}
fn b328_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e329_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
if count>=1 {break;}
}
let _=count;
if count>1 {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e329_c(&mut self,state:State)->Step {
let mut out=self.b329_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,329,34);
} else {self.display_failures(state.consumed.max(state.matched),&["':'"]);}
out
}
fn b329_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,":",true,21,false,":");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"':'");}
out
}
fn e330_c(&mut self,state:State)->Step {
let mut out=self.b330_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.display_failures(state.consumed.max(state.matched),&["'#'"]);}
out
}
fn b330_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.e331_c(out.state);
out
}
fn e331_c(&mut self,state:State)->Step {
let mut out=self.b331_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'#'"]);}
out
}
fn b331_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out='choice: {let mark=self.mark();let mut diagnostic=Diag::NONE;let mut best:Option<(Step,[usize;2])>=None;
let ct=self.input.cp_at(self.skip_0::<false>(out.state.begin()).position::<false>()).map(|(c,_)|c);
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,65..=90|95|97..=122))) {self.guard_e332_c(out.state.begin())} else {self.e332_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e336_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);break 'choice if let Some((mut child,effects))=best {self.replay_effects(effects);child.diag=diagnostic;child} else {out.ok=false;out.diag=diagnostic;out};
};
out
}
fn e332_c(&mut self,state:State)->Step {
let mut out=self.b332_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.display_failures(state.consumed.max(state.matched),&["'#'"]);}
out
}
fn b332_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e333_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e334_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e335_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e333_c(&mut self,state:State)->Step {
let mut out=self.b333_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:55,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b333_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r3_c(out.state);
out
}
fn e334_c(&mut self,state:State)->Step {
let mut out=self.b334_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,334,34);
} else {self.display_failures(state.consumed.max(state.matched),&["'#'"]);}
out
}
fn b334_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"#",true,1,false,"#");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'#'");}
out
}
fn e335_c(&mut self,state:State)->Step {
let mut out=self.b335_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,335,34);
out.events=self.event(Event::Capture {site:56,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["IdentifierParser", "__CaptureSite"]);}
out
}
fn b335_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.token_1::<false>(out.state,"IDENTIFIER","IdentifierParser");
out
}
fn e336_c(&mut self,state:State)->Step {
let mut out=self.b336_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,336,34);
out.events=self.event(Event::Capture {site:57,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["IdentifierParser", "__CaptureSite"]);}
out
}
fn b336_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.token_1::<false>(out.state,"IDENTIFIER","IdentifierParser");
out
}
fn e337_c(&mut self,state:State)->Step {
let mut out=self.b337_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,337,34);
} else {self.display_failures(state.consumed.max(state.matched),&["'('"]);}
out
}
fn b337_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"(",true,4,false,"(");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'('");}
out
}
fn e338_c(&mut self,state:State)->Step {
let mut out=self.b338_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.display_failures(state.consumed.max(state.matched),&["Optional"]);}
out
}
fn b338_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e339_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
if count>=1 {break;}
}
let _=count;
if count>1 {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e339_c(&mut self,state:State)->Step {
let mut out=self.b339_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:58,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b339_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r39_c(out.state);
out
}
fn e340_c(&mut self,state:State)->Step {
let mut out=self.b340_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,340,34);
} else {self.display_failures(state.consumed.max(state.matched),&["')'"]);}
out
}
fn b340_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,")",true,5,false,")");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"')'");}
out
}
fn e341_c(&mut self,state:State)->Step {
let mut out=self.b341_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b341_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out='choice: {let mark=self.mark();let mut diagnostic=Diag::NONE;let mut best:Option<(Step,[usize;2])>=None;
let ct=self.input.cp_at(self.skip_0::<false>(out.state.begin()).position::<false>()).map(|(c,_)|c);
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,99))) {self.guard_e342_c(out.state.begin())} else {self.e342_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e346_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);break 'choice if let Some((mut child,effects))=best {self.replay_effects(effects);child.diag=diagnostic;child} else {out.ok=false;out.diag=diagnostic;out};
};
out
}
fn e342_c(&mut self,state:State)->Step {
let mut out=self.b342_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'call'", "'internal'"]);}
out
}
fn b342_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e343_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e344_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e343_c(&mut self,state:State)->Step {
let mut out=self.b343_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,343,35);
} else {self.display_failures(state.consumed.max(state.matched),&["'call'"]);}
out
}
fn b343_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"call",true,49,false,"call");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'call'");}
out
}
fn e344_c(&mut self,state:State)->Step {
let mut out=self.b344_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'internal'"]);}
out
}
fn b344_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e345_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
if count>=1 {break;}
}
let _=count;
if count>1 {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e345_c(&mut self,state:State)->Step {
let mut out=self.b345_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,345,35);
} else {self.display_failures(state.consumed.max(state.matched),&["'internal'"]);}
out
}
fn b345_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"internal",true,67,false,"internal");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'internal'");}
out
}
fn e346_c(&mut self,state:State)->Step {
let mut out=self.b346_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,346,35);
} else {self.display_failures(state.consumed.max(state.matched),&["'internal'"]);}
out
}
fn b346_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"internal",true,67,false,"internal");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'internal'");}
out
}
fn e347_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:347,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b347_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b347_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e348_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e349_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e350_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e351_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e353_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e348_c(&mut self,state:State)->Step {
let mut out=self.b348_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b348_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r35_c(out.state);
out
}
fn e349_c(&mut self,state:State)->Step {
let mut out=self.b349_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,349,36);
out.events=self.event(Event::Capture {site:59,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["IdentifierParser", "__CaptureSite"]);}
out
}
fn b349_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.token_1::<false>(out.state,"IDENTIFIER","IdentifierParser");
out
}
fn e350_c(&mut self,state:State)->Step {
let mut out=self.b350_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,350,36);
} else {self.display_failures(state.consumed.max(state.matched),&["'('"]);}
out
}
fn b350_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"(",true,4,false,"(");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'('");}
out
}
fn e351_c(&mut self,state:State)->Step {
let mut out=self.b351_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.display_failures(state.consumed.max(state.matched),&["Optional"]);}
out
}
fn b351_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e352_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
if count>=1 {break;}
}
let _=count;
if count>1 {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e352_c(&mut self,state:State)->Step {
let mut out=self.b352_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:60,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b352_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r39_c(out.state);
out
}
fn e353_c(&mut self,state:State)->Step {
let mut out=self.b353_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,353,36);
} else {self.display_failures(state.consumed.max(state.matched),&["')'"]);}
out
}
fn b353_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,")",true,5,false,")");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"')'");}
out
}
fn e354_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:354,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b354_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b354_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e355_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e356_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e357_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e358_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e359_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e355_c(&mut self,state:State)->Step {
let mut out=self.b355_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:61,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b355_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r94_c(out.state);
out
}
fn e356_c(&mut self,state:State)->Step {
let mut out=self.b356_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,356,37);
} else {self.display_failures(state.consumed.max(state.matched),&["'?'"]);}
out
}
fn b356_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"?",true,29,false,"?");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'?'");}
out
}
fn e357_c(&mut self,state:State)->Step {
let mut out=self.b357_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:62,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b357_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r107_c(out.state);
out
}
fn e358_c(&mut self,state:State)->Step {
let mut out=self.b358_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,358,37);
} else {self.display_failures(state.consumed.max(state.matched),&["':'"]);}
out
}
fn b358_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,":",true,21,false,":");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"':'");}
out
}
fn e359_c(&mut self,state:State)->Step {
let mut out=self.b359_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:63,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b359_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r107_c(out.state);
out
}
fn e360_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:360,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b360_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b360_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out='choice: {let mark=self.mark();let mut diagnostic=Diag::NONE;let mut best:Option<(Step,[usize;2])>=None;
let ct=self.input.cp_at(self.skip_0::<false>(out.state.begin()).position::<false>()).map(|(c,_)|c);
self.restore(mark);let mut child=self.e361_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,36|40|43|45..=46|48..=57|97|99|101..=102|105|108..=109|112|114..=116))) {self.guard_e362_c(out.state.begin())} else {self.e362_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e363_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e364_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);break 'choice if let Some((mut child,effects))=best {self.replay_effects(effects);child.diag=diagnostic;child} else {out.ok=false;out.diag=diagnostic;out};
};
out
}
fn e361_c(&mut self,state:State)->Step {
let mut out=self.b361_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:64,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["':'", "'?'"]);}
out
}
fn b361_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r37_c(out.state);
out
}
fn e362_c(&mut self,state:State)->Step {
let mut out=self.b362_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:65,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b362_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r103_c(out.state);
out
}
fn e363_c(&mut self,state:State)->Step {
let mut out=self.b363_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:66,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b363_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r101_c(out.state);
out
}
fn e364_c(&mut self,state:State)->Step {
let mut out=self.b364_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:67,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b364_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r123_c(out.state);
out
}
fn e365_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:365,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b365_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b365_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e366_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e367_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e366_c(&mut self,state:State)->Step {
let mut out=self.b366_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:68,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b366_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r38_c(out.state);
out
}
fn e367_c(&mut self,state:State)->Step {
let mut out=self.b367_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.display_failures(state.consumed.max(state.matched),&["','"]);}
out
}
fn b367_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e368_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
}
let _=count;
if false {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e368_c(&mut self,state:State)->Step {
let mut out=self.b368_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.display_failures(state.consumed.max(state.matched),&["','"]);}
out
}
fn b368_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e369_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e370_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e369_c(&mut self,state:State)->Step {
let mut out=self.b369_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,369,39);
} else {self.display_failures(state.consumed.max(state.matched),&["','"]);}
out
}
fn b369_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,",",true,8,false,",");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"','");}
out
}
fn e370_c(&mut self,state:State)->Step {
let mut out=self.b370_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:69,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b370_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r38_c(out.state);
out
}
fn e371_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:371,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b371_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b371_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e372_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e373_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e372_c(&mut self,state:State)->Step {
let mut out=self.b372_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:70,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b372_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r41_c(out.state);
out
}
fn e373_c(&mut self,state:State)->Step {
let mut out=self.b373_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.display_failures(state.consumed.max(state.matched),&["Repeat"]);}
out
}
fn b373_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e374_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
}
let _=count;
if false {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e374_c(&mut self,state:State)->Step {
let mut out=self.b374_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.display_failures(state.consumed.max(state.matched),&["NumberExpressionRepeat0Parser"]);}
out
}
fn b374_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e375_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e376_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e375_c(&mut self,state:State)->Step {
let mut out=self.b375_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:71,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["'+'", "'-'"]);}
out
}
fn b375_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r42_c(out.state);
out
}
fn e376_c(&mut self,state:State)->Step {
let mut out=self.b376_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:72,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b376_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r41_c(out.state);
out
}
fn e377_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:377,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b377_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b377_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e378_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e379_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e378_c(&mut self,state:State)->Step {
let mut out=self.b378_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:73,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b378_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r60_c(out.state);
out
}
fn e379_c(&mut self,state:State)->Step {
let mut out=self.b379_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.display_failures(state.consumed.max(state.matched),&["Repeat"]);}
out
}
fn b379_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e380_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
}
let _=count;
if false {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e380_c(&mut self,state:State)->Step {
let mut out=self.b380_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.display_failures(state.consumed.max(state.matched),&["NumberTermRepeat0Parser"]);}
out
}
fn b380_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e381_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e382_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e381_c(&mut self,state:State)->Step {
let mut out=self.b381_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:74,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["'*'", "'/'"]);}
out
}
fn b381_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r43_c(out.state);
out
}
fn e382_c(&mut self,state:State)->Step {
let mut out=self.b382_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:75,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b382_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r60_c(out.state);
out
}
fn e383_c(&mut self,state:State)->Step {
let mut out=self.b383_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b383_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out='choice: {let mark=self.mark();let mut diagnostic=Diag::NONE;let mut best:Option<(Step,[usize;2])>=None;
if !out.state.invert {let predicted=match self.input.cp_at(out.state.begin().position::<false>()).map(|(c,_)|c) {
Some('+')=>0usize,
Some('-')=>1usize,
_=>usize::MAX,};let candidate=match predicted {
0=>Some(self.e384_c(out.state.begin())),
1=>Some(self.e385_c(out.state.begin())),
_=>None,};
if !DIAG && self.options.predict && predicted==usize::MAX {self.restore(mark);out.ok=false;out.diag=diagnostic;break 'choice out;}
if let Some(mut child)=candidate {if child.ok {
if predicted>0 {let d=self.diag_fail(out.state.begin().position::<false>(),"+");diagnostic=self.diag_join(diagnostic,d);}
if predicted>1 {let d=self.diag_fail(out.state.begin().position::<false>(),"-");diagnostic=self.diag_join(diagnostic,d);}
child.state=out.state.commit(child.state);child.diag=self.diag_join(diagnostic,child.diag);break 'choice child;}}self.restore(mark); }
self.restore(mark);let mut child=self.e384_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e385_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);break 'choice if let Some((mut child,effects))=best {self.replay_effects(effects);child.diag=diagnostic;child} else {out.ok=false;out.diag=diagnostic;out};
};
out
}
fn e384_c(&mut self,state:State)->Step {
let mut out=self.b384_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,384,42);
} else {self.display_failures(state.consumed.max(state.matched),&["'+'"]);}
out
}
fn b384_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"+",true,7,false,"+");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'+'");}
out
}
fn e385_c(&mut self,state:State)->Step {
let mut out=self.b385_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,385,42);
} else {self.display_failures(state.consumed.max(state.matched),&["'-'"]);}
out
}
fn b385_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"-",true,9,false,"-");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'-'");}
out
}
fn e386_c(&mut self,state:State)->Step {
let mut out=self.b386_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b386_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out='choice: {let mark=self.mark();let mut diagnostic=Diag::NONE;let mut best:Option<(Step,[usize;2])>=None;
if !out.state.invert {let predicted=match self.input.cp_at(out.state.begin().position::<false>()).map(|(c,_)|c) {
Some('*')=>0usize,
Some('/')=>1usize,
_=>usize::MAX,};let candidate=match predicted {
0=>Some(self.e387_c(out.state.begin())),
1=>Some(self.e388_c(out.state.begin())),
_=>None,};
if !DIAG && self.options.predict && predicted==usize::MAX {self.restore(mark);out.ok=false;out.diag=diagnostic;break 'choice out;}
if let Some(mut child)=candidate {if child.ok {
if predicted>0 {let d=self.diag_fail(out.state.begin().position::<false>(),"*");diagnostic=self.diag_join(diagnostic,d);}
if predicted>1 {let d=self.diag_fail(out.state.begin().position::<false>(),"/");diagnostic=self.diag_join(diagnostic,d);}
child.state=out.state.commit(child.state);child.diag=self.diag_join(diagnostic,child.diag);break 'choice child;}}self.restore(mark); }
self.restore(mark);let mut child=self.e387_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e388_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);break 'choice if let Some((mut child,effects))=best {self.replay_effects(effects);child.diag=diagnostic;child} else {out.ok=false;out.diag=diagnostic;out};
};
out
}
fn e387_c(&mut self,state:State)->Step {
let mut out=self.b387_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,387,43);
} else {self.display_failures(state.consumed.max(state.matched),&["'*'"]);}
out
}
fn b387_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"*",true,6,false,"*");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'*'");}
out
}
fn e388_c(&mut self,state:State)->Step {
let mut out=self.b388_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,388,43);
} else {self.display_failures(state.consumed.max(state.matched),&["'/'"]);}
out
}
fn b388_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"/",true,20,false,"/");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'/'");}
out
}
fn e389_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:389,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b389_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b389_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out='choice: {let mark=self.mark();let mut diagnostic=Diag::NONE;let mut best:Option<(Step,[usize;2])>=None;
let ct=self.input.cp_at(self.skip_0::<false>(out.state.begin()).position::<false>()).map(|(c,_)|c);
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,115))) {self.guard_e390_c(out.state.begin())} else {self.e390_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,99))) {self.guard_e391_c(out.state.begin())} else {self.e391_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,116))) {self.guard_e392_c(out.state.begin())} else {self.e392_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,115))) {self.guard_e393_c(out.state.begin())} else {self.e393_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,109))) {self.guard_e394_c(out.state.begin())} else {self.e394_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,109))) {self.guard_e395_c(out.state.begin())} else {self.e395_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,114))) {self.guard_e396_c(out.state.begin())} else {self.e396_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,97))) {self.guard_e397_c(out.state.begin())} else {self.e397_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,114))) {self.guard_e398_c(out.state.begin())} else {self.e398_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,99))) {self.guard_e399_c(out.state.begin())} else {self.e399_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,102))) {self.guard_e400_c(out.state.begin())} else {self.e400_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,112))) {self.guard_e401_c(out.state.begin())} else {self.e401_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,108))) {self.guard_e402_c(out.state.begin())} else {self.e402_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,101))) {self.guard_e403_c(out.state.begin())} else {self.e403_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);break 'choice if let Some((mut child,effects))=best {self.replay_effects(effects);child.diag=diagnostic;child} else {out.ok=false;out.diag=diagnostic;out};
};
out
}
fn e390_c(&mut self,state:State)->Step {
let mut out=self.b390_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b390_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r45_c(out.state);
out
}
fn e391_c(&mut self,state:State)->Step {
let mut out=self.b391_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b391_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r46_c(out.state);
out
}
fn e392_c(&mut self,state:State)->Step {
let mut out=self.b392_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b392_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r47_c(out.state);
out
}
fn e393_c(&mut self,state:State)->Step {
let mut out=self.b393_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b393_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r48_c(out.state);
out
}
fn e394_c(&mut self,state:State)->Step {
let mut out=self.b394_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b394_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r49_c(out.state);
out
}
fn e395_c(&mut self,state:State)->Step {
let mut out=self.b395_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b395_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r50_c(out.state);
out
}
fn e396_c(&mut self,state:State)->Step {
let mut out=self.b396_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b396_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r51_c(out.state);
out
}
fn e397_c(&mut self,state:State)->Step {
let mut out=self.b397_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b397_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r52_c(out.state);
out
}
fn e398_c(&mut self,state:State)->Step {
let mut out=self.b398_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b398_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r53_c(out.state);
out
}
fn e399_c(&mut self,state:State)->Step {
let mut out=self.b399_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b399_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r54_c(out.state);
out
}
fn e400_c(&mut self,state:State)->Step {
let mut out=self.b400_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b400_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r55_c(out.state);
out
}
fn e401_c(&mut self,state:State)->Step {
let mut out=self.b401_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b401_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r56_c(out.state);
out
}
fn e402_c(&mut self,state:State)->Step {
let mut out=self.b402_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b402_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r57_c(out.state);
out
}
fn e403_c(&mut self,state:State)->Step {
let mut out=self.b403_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b403_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r58_c(out.state);
out
}
fn e404_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:404,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b404_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b404_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e405_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e406_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e407_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e408_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e405_c(&mut self,state:State)->Step {
let mut out=self.b405_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,405,45);
} else {self.display_failures(state.consumed.max(state.matched),&["'sin'"]);}
out
}
fn b405_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"sin",true,83,false,"sin");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'sin'");}
out
}
fn e406_c(&mut self,state:State)->Step {
let mut out=self.b406_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,406,45);
} else {self.display_failures(state.consumed.max(state.matched),&["'('"]);}
out
}
fn b406_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"(",true,4,false,"(");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'('");}
out
}
fn e407_c(&mut self,state:State)->Step {
let mut out=self.b407_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:76,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b407_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r38_c(out.state);
out
}
fn e408_c(&mut self,state:State)->Step {
let mut out=self.b408_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,408,45);
} else {self.display_failures(state.consumed.max(state.matched),&["')'"]);}
out
}
fn b408_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,")",true,5,false,")");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"')'");}
out
}
fn e409_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:409,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b409_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b409_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e410_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e411_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e412_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e413_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e410_c(&mut self,state:State)->Step {
let mut out=self.b410_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,410,46);
} else {self.display_failures(state.consumed.max(state.matched),&["'cos'"]);}
out
}
fn b410_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"cos",true,52,false,"cos");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'cos'");}
out
}
fn e411_c(&mut self,state:State)->Step {
let mut out=self.b411_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,411,46);
} else {self.display_failures(state.consumed.max(state.matched),&["'('"]);}
out
}
fn b411_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"(",true,4,false,"(");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'('");}
out
}
fn e412_c(&mut self,state:State)->Step {
let mut out=self.b412_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:77,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b412_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r38_c(out.state);
out
}
fn e413_c(&mut self,state:State)->Step {
let mut out=self.b413_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,413,46);
} else {self.display_failures(state.consumed.max(state.matched),&["')'"]);}
out
}
fn b413_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,")",true,5,false,")");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"')'");}
out
}
fn e414_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:414,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b414_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b414_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e415_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e416_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e417_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e418_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e415_c(&mut self,state:State)->Step {
let mut out=self.b415_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,415,47);
} else {self.display_failures(state.consumed.max(state.matched),&["'tan'"]);}
out
}
fn b415_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"tan",true,87,false,"tan");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'tan'");}
out
}
fn e416_c(&mut self,state:State)->Step {
let mut out=self.b416_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,416,47);
} else {self.display_failures(state.consumed.max(state.matched),&["'('"]);}
out
}
fn b416_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"(",true,4,false,"(");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'('");}
out
}
fn e417_c(&mut self,state:State)->Step {
let mut out=self.b417_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:78,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b417_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r38_c(out.state);
out
}
fn e418_c(&mut self,state:State)->Step {
let mut out=self.b418_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,418,47);
} else {self.display_failures(state.consumed.max(state.matched),&["')'"]);}
out
}
fn b418_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,")",true,5,false,")");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"')'");}
out
}
fn e419_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:419,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b419_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b419_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e420_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e421_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e422_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e423_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e420_c(&mut self,state:State)->Step {
let mut out=self.b420_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,420,48);
} else {self.display_failures(state.consumed.max(state.matched),&["'sqrt'"]);}
out
}
fn b420_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"sqrt",true,84,false,"sqrt");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'sqrt'");}
out
}
fn e421_c(&mut self,state:State)->Step {
let mut out=self.b421_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,421,48);
} else {self.display_failures(state.consumed.max(state.matched),&["'('"]);}
out
}
fn b421_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"(",true,4,false,"(");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'('");}
out
}
fn e422_c(&mut self,state:State)->Step {
let mut out=self.b422_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:79,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b422_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r38_c(out.state);
out
}
fn e423_c(&mut self,state:State)->Step {
let mut out=self.b423_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,423,48);
} else {self.display_failures(state.consumed.max(state.matched),&["')'"]);}
out
}
fn b423_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,")",true,5,false,")");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"')'");}
out
}
fn e424_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:424,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b424_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b424_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e425_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e426_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e427_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e428_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e432_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e425_c(&mut self,state:State)->Step {
let mut out=self.b425_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,425,49);
} else {self.display_failures(state.consumed.max(state.matched),&["'min'"]);}
out
}
fn b425_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"min",true,74,false,"min");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'min'");}
out
}
fn e426_c(&mut self,state:State)->Step {
let mut out=self.b426_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,426,49);
} else {self.display_failures(state.consumed.max(state.matched),&["'('"]);}
out
}
fn b426_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"(",true,4,false,"(");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'('");}
out
}
fn e427_c(&mut self,state:State)->Step {
let mut out=self.b427_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:80,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b427_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r38_c(out.state);
out
}
fn e428_c(&mut self,state:State)->Step {
let mut out=self.b428_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.display_failures(state.consumed.max(state.matched),&["','"]);}
out
}
fn b428_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e429_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
}
let _=count;
if false {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e429_c(&mut self,state:State)->Step {
let mut out=self.b429_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.display_failures(state.consumed.max(state.matched),&["','"]);}
out
}
fn b429_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e430_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e431_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e430_c(&mut self,state:State)->Step {
let mut out=self.b430_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,430,49);
} else {self.display_failures(state.consumed.max(state.matched),&["','"]);}
out
}
fn b430_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,",",true,8,false,",");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"','");}
out
}
fn e431_c(&mut self,state:State)->Step {
let mut out=self.b431_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:81,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b431_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r38_c(out.state);
out
}
fn e432_c(&mut self,state:State)->Step {
let mut out=self.b432_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,432,49);
} else {self.display_failures(state.consumed.max(state.matched),&["')'"]);}
out
}
fn b432_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,")",true,5,false,")");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"')'");}
out
}
fn e433_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:433,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b433_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b433_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e434_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e435_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e436_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e437_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e441_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e434_c(&mut self,state:State)->Step {
let mut out=self.b434_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,434,50);
} else {self.display_failures(state.consumed.max(state.matched),&["'max'"]);}
out
}
fn b434_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"max",true,73,false,"max");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'max'");}
out
}
fn e435_c(&mut self,state:State)->Step {
let mut out=self.b435_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,435,50);
} else {self.display_failures(state.consumed.max(state.matched),&["'('"]);}
out
}
fn b435_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"(",true,4,false,"(");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'('");}
out
}
fn e436_c(&mut self,state:State)->Step {
let mut out=self.b436_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:82,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b436_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r38_c(out.state);
out
}
fn e437_c(&mut self,state:State)->Step {
let mut out=self.b437_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.display_failures(state.consumed.max(state.matched),&["','"]);}
out
}
fn b437_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e438_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
}
let _=count;
if false {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e438_c(&mut self,state:State)->Step {
let mut out=self.b438_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.display_failures(state.consumed.max(state.matched),&["','"]);}
out
}
fn b438_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e439_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e440_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e439_c(&mut self,state:State)->Step {
let mut out=self.b439_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,439,50);
} else {self.display_failures(state.consumed.max(state.matched),&["','"]);}
out
}
fn b439_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,",",true,8,false,",");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"','");}
out
}
fn e440_c(&mut self,state:State)->Step {
let mut out=self.b440_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:83,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b440_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r38_c(out.state);
out
}
fn e441_c(&mut self,state:State)->Step {
let mut out=self.b441_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,441,50);
} else {self.display_failures(state.consumed.max(state.matched),&["')'"]);}
out
}
fn b441_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,")",true,5,false,")");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"')'");}
out
}
fn e442_c(&mut self,state:State)->Step {
let mut out=self.b442_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b442_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e443_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e444_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e445_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e443_c(&mut self,state:State)->Step {
let mut out=self.b443_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,443,51);
} else {self.display_failures(state.consumed.max(state.matched),&["'random'"]);}
out
}
fn b443_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"random",true,79,false,"random");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'random'");}
out
}
fn e444_c(&mut self,state:State)->Step {
let mut out=self.b444_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,444,51);
} else {self.display_failures(state.consumed.max(state.matched),&["'('"]);}
out
}
fn b444_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"(",true,4,false,"(");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'('");}
out
}
fn e445_c(&mut self,state:State)->Step {
let mut out=self.b445_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,445,51);
} else {self.display_failures(state.consumed.max(state.matched),&["')'"]);}
out
}
fn b445_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,")",true,5,false,")");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"')'");}
out
}
fn e446_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:446,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b446_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b446_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e447_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e448_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e449_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e450_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e447_c(&mut self,state:State)->Step {
let mut out=self.b447_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,447,52);
} else {self.display_failures(state.consumed.max(state.matched),&["'abs'"]);}
out
}
fn b447_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"abs",true,46,false,"abs");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'abs'");}
out
}
fn e448_c(&mut self,state:State)->Step {
let mut out=self.b448_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,448,52);
} else {self.display_failures(state.consumed.max(state.matched),&["'('"]);}
out
}
fn b448_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"(",true,4,false,"(");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'('");}
out
}
fn e449_c(&mut self,state:State)->Step {
let mut out=self.b449_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:84,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b449_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r38_c(out.state);
out
}
fn e450_c(&mut self,state:State)->Step {
let mut out=self.b450_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,450,52);
} else {self.display_failures(state.consumed.max(state.matched),&["')'"]);}
out
}
fn b450_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,")",true,5,false,")");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"')'");}
out
}
fn e451_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:451,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b451_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b451_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e452_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e453_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e454_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e455_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e452_c(&mut self,state:State)->Step {
let mut out=self.b452_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,452,53);
} else {self.display_failures(state.consumed.max(state.matched),&["'round'"]);}
out
}
fn b452_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"round",true,81,false,"round");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'round'");}
out
}
fn e453_c(&mut self,state:State)->Step {
let mut out=self.b453_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,453,53);
} else {self.display_failures(state.consumed.max(state.matched),&["'('"]);}
out
}
fn b453_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"(",true,4,false,"(");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'('");}
out
}
fn e454_c(&mut self,state:State)->Step {
let mut out=self.b454_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:85,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b454_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r38_c(out.state);
out
}
fn e455_c(&mut self,state:State)->Step {
let mut out=self.b455_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,455,53);
} else {self.display_failures(state.consumed.max(state.matched),&["')'"]);}
out
}
fn b455_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,")",true,5,false,")");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"')'");}
out
}
fn e456_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:456,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b456_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b456_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e457_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e458_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e459_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e460_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e457_c(&mut self,state:State)->Step {
let mut out=self.b457_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,457,54);
} else {self.display_failures(state.consumed.max(state.matched),&["'ceil'"]);}
out
}
fn b457_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"ceil",true,50,false,"ceil");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'ceil'");}
out
}
fn e458_c(&mut self,state:State)->Step {
let mut out=self.b458_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,458,54);
} else {self.display_failures(state.consumed.max(state.matched),&["'('"]);}
out
}
fn b458_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"(",true,4,false,"(");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'('");}
out
}
fn e459_c(&mut self,state:State)->Step {
let mut out=self.b459_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:86,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b459_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r38_c(out.state);
out
}
fn e460_c(&mut self,state:State)->Step {
let mut out=self.b460_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,460,54);
} else {self.display_failures(state.consumed.max(state.matched),&["')'"]);}
out
}
fn b460_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,")",true,5,false,")");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"')'");}
out
}
fn e461_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:461,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b461_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b461_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e462_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e463_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e464_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e465_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e462_c(&mut self,state:State)->Step {
let mut out=self.b462_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,462,55);
} else {self.display_failures(state.consumed.max(state.matched),&["'floor'"]);}
out
}
fn b462_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"floor",true,62,false,"floor");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'floor'");}
out
}
fn e463_c(&mut self,state:State)->Step {
let mut out=self.b463_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,463,55);
} else {self.display_failures(state.consumed.max(state.matched),&["'('"]);}
out
}
fn b463_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"(",true,4,false,"(");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'('");}
out
}
fn e464_c(&mut self,state:State)->Step {
let mut out=self.b464_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:87,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b464_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r38_c(out.state);
out
}
fn e465_c(&mut self,state:State)->Step {
let mut out=self.b465_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,465,55);
} else {self.display_failures(state.consumed.max(state.matched),&["')'"]);}
out
}
fn b465_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,")",true,5,false,")");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"')'");}
out
}
fn e466_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:466,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b466_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b466_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e467_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e468_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e469_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e470_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e471_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e472_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e467_c(&mut self,state:State)->Step {
let mut out=self.b467_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,467,56);
} else {self.display_failures(state.consumed.max(state.matched),&["'pow'"]);}
out
}
fn b467_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"pow",true,78,false,"pow");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'pow'");}
out
}
fn e468_c(&mut self,state:State)->Step {
let mut out=self.b468_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,468,56);
} else {self.display_failures(state.consumed.max(state.matched),&["'('"]);}
out
}
fn b468_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"(",true,4,false,"(");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'('");}
out
}
fn e469_c(&mut self,state:State)->Step {
let mut out=self.b469_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:88,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b469_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r38_c(out.state);
out
}
fn e470_c(&mut self,state:State)->Step {
let mut out=self.b470_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,470,56);
} else {self.display_failures(state.consumed.max(state.matched),&["','"]);}
out
}
fn b470_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,",",true,8,false,",");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"','");}
out
}
fn e471_c(&mut self,state:State)->Step {
let mut out=self.b471_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:89,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b471_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r38_c(out.state);
out
}
fn e472_c(&mut self,state:State)->Step {
let mut out=self.b472_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,472,56);
} else {self.display_failures(state.consumed.max(state.matched),&["')'"]);}
out
}
fn b472_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,")",true,5,false,")");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"')'");}
out
}
fn e473_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:473,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b473_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b473_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e474_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e475_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e476_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e477_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e474_c(&mut self,state:State)->Step {
let mut out=self.b474_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,474,57);
} else {self.display_failures(state.consumed.max(state.matched),&["'log'"]);}
out
}
fn b474_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"log",true,71,false,"log");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'log'");}
out
}
fn e475_c(&mut self,state:State)->Step {
let mut out=self.b475_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,475,57);
} else {self.display_failures(state.consumed.max(state.matched),&["'('"]);}
out
}
fn b475_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"(",true,4,false,"(");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'('");}
out
}
fn e476_c(&mut self,state:State)->Step {
let mut out=self.b476_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:90,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b476_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r38_c(out.state);
out
}
fn e477_c(&mut self,state:State)->Step {
let mut out=self.b477_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,477,57);
} else {self.display_failures(state.consumed.max(state.matched),&["')'"]);}
out
}
fn b477_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,")",true,5,false,")");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"')'");}
out
}
fn e478_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:478,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b478_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b478_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e479_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e480_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e481_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e482_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e479_c(&mut self,state:State)->Step {
let mut out=self.b479_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,479,58);
} else {self.display_failures(state.consumed.max(state.matched),&["'exp'"]);}
out
}
fn b479_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"exp",true,58,false,"exp");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'exp'");}
out
}
fn e480_c(&mut self,state:State)->Step {
let mut out=self.b480_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,480,58);
} else {self.display_failures(state.consumed.max(state.matched),&["'('"]);}
out
}
fn b480_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"(",true,4,false,"(");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'('");}
out
}
fn e481_c(&mut self,state:State)->Step {
let mut out=self.b481_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:91,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b481_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r38_c(out.state);
out
}
fn e482_c(&mut self,state:State)->Step {
let mut out=self.b482_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,482,58);
} else {self.display_failures(state.consumed.max(state.matched),&["')'"]);}
out
}
fn b482_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,")",true,5,false,")");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"')'");}
out
}
fn e483_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:483,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b483_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b483_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e484_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e485_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e486_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e487_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e488_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e489_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e484_c(&mut self,state:State)->Step {
let mut out=self.b484_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,484,59);
} else {self.display_failures(state.consumed.max(state.matched),&["'toNum'"]);}
out
}
fn b484_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"toNum",true,89,false,"toNum");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'toNum'");}
out
}
fn e485_c(&mut self,state:State)->Step {
let mut out=self.b485_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,485,59);
} else {self.display_failures(state.consumed.max(state.matched),&["'('"]);}
out
}
fn b485_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"(",true,4,false,"(");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'('");}
out
}
fn e486_c(&mut self,state:State)->Step {
let mut out=self.b486_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:92,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b486_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r89_c(out.state);
out
}
fn e487_c(&mut self,state:State)->Step {
let mut out=self.b487_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,487,59);
} else {self.display_failures(state.consumed.max(state.matched),&["','"]);}
out
}
fn b487_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,",",true,8,false,",");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"','");}
out
}
fn e488_c(&mut self,state:State)->Step {
let mut out=self.b488_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:93,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b488_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r38_c(out.state);
out
}
fn e489_c(&mut self,state:State)->Step {
let mut out=self.b489_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,489,59);
} else {self.display_failures(state.consumed.max(state.matched),&["')'"]);}
out
}
fn b489_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,")",true,5,false,")");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"')'");}
out
}
fn e490_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:490,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b490_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b490_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out='choice: {let mark=self.mark();let mut diagnostic=Diag::NONE;let mut best:Option<(Step,[usize;2])>=None;
let ct=self.input.cp_at(self.skip_0::<false>(out.state.begin()).position::<false>()).map(|(c,_)|c);
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,40))) {self.guard_e491_c(out.state.begin())} else {self.e491_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,109))) {self.guard_e492_c(out.state.begin())} else {self.e492_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,105))) {self.guard_e493_c(out.state.begin())} else {self.e493_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,97|99|101..=102|108..=109|112|114..=116))) {self.guard_e494_c(out.state.begin())} else {self.e494_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,116))) {self.guard_e495_c(out.state.begin())} else {self.e495_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,36))) {self.guard_e496_c(out.state.begin())} else {self.e496_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,108))) {self.guard_e497_c(out.state.begin())} else {self.e497_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,108))) {self.guard_e498_c(out.state.begin())} else {self.e498_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,101))) {self.guard_e499_c(out.state.begin())} else {self.e499_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e500_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,36))) {self.guard_e501_c(out.state.begin())} else {self.e501_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,99|105))) {self.guard_e502_c(out.state.begin())} else {self.e502_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,40))) {self.guard_e503_c(out.state.begin())} else {self.e503_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);break 'choice if let Some((mut child,effects))=best {self.replay_effects(effects);child.diag=diagnostic;child} else {out.ok=false;out.diag=diagnostic;out};
};
out
}
fn e491_c(&mut self,state:State)->Step {
let mut out=self.b491_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b491_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r108_c(out.state);
out
}
fn e492_c(&mut self,state:State)->Step {
let mut out=self.b492_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b492_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r109_c(out.state);
out
}
fn e493_c(&mut self,state:State)->Step {
let mut out=self.b493_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b493_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r106_c(out.state);
out
}
fn e494_c(&mut self,state:State)->Step {
let mut out=self.b494_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b494_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r44_c(out.state);
out
}
fn e495_c(&mut self,state:State)->Step {
let mut out=self.b495_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b495_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r59_c(out.state);
out
}
fn e496_c(&mut self,state:State)->Step {
let mut out=self.b496_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b496_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r69_c(out.state);
out
}
fn e497_c(&mut self,state:State)->Step {
let mut out=self.b497_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b497_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r65_c(out.state);
out
}
fn e498_c(&mut self,state:State)->Step {
let mut out=self.b498_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b498_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r64_c(out.state);
out
}
fn e499_c(&mut self,state:State)->Step {
let mut out=self.b499_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b499_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r32_c(out.state);
out
}
fn e500_c(&mut self,state:State)->Step {
let mut out=self.b500_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,500,60);
} else {self.display_failures(state.consumed.max(state.matched),&["NumberParser"]);}
out
}
fn b500_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.token_0::<false>(out.state,"NUMBER","NumberParser");
out
}
fn e501_c(&mut self,state:State)->Step {
let mut out=self.b501_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b501_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r121_c(out.state);
out
}
fn e502_c(&mut self,state:State)->Step {
let mut out=self.b502_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b502_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r36_c(out.state);
out
}
fn e503_c(&mut self,state:State)->Step {
let mut out=self.b503_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.display_failures(state.consumed.max(state.matched),&["'('", "')'"]);}
out
}
fn b503_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e504_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e505_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e506_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e504_c(&mut self,state:State)->Step {
let mut out=self.b504_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,504,60);
} else {self.display_failures(state.consumed.max(state.matched),&["'('"]);}
out
}
fn b504_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"(",true,4,false,"(");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'('");}
out
}
fn e505_c(&mut self,state:State)->Step {
let mut out=self.b505_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b505_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r40_c(out.state);
out
}
fn e506_c(&mut self,state:State)->Step {
let mut out=self.b506_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,506,60);
} else {self.display_failures(state.consumed.max(state.matched),&["')'"]);}
out
}
fn b506_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,")",true,5,false,")");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"')'");}
out
}
fn e507_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:507,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b507_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b507_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e508_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e509_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e510_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e511_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e508_c(&mut self,state:State)->Step {
let mut out=self.b508_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,508,61);
} else {self.display_failures(state.consumed.max(state.matched),&["'toUpperCase'"]);}
out
}
fn b508_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"toUpperCase",true,90,false,"toUpperCase");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'toUpperCase'");}
out
}
fn e509_c(&mut self,state:State)->Step {
let mut out=self.b509_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,509,61);
} else {self.display_failures(state.consumed.max(state.matched),&["'('"]);}
out
}
fn b509_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"(",true,4,false,"(");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'('");}
out
}
fn e510_c(&mut self,state:State)->Step {
let mut out=self.b510_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:94,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b510_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r89_c(out.state);
out
}
fn e511_c(&mut self,state:State)->Step {
let mut out=self.b511_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,511,61);
} else {self.display_failures(state.consumed.max(state.matched),&["')'"]);}
out
}
fn b511_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,")",true,5,false,")");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"')'");}
out
}
fn e512_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:512,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b512_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b512_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e513_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e514_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e515_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e516_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e513_c(&mut self,state:State)->Step {
let mut out=self.b513_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,513,62);
} else {self.display_failures(state.consumed.max(state.matched),&["'toLowerCase'"]);}
out
}
fn b513_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"toLowerCase",true,88,false,"toLowerCase");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'toLowerCase'");}
out
}
fn e514_c(&mut self,state:State)->Step {
let mut out=self.b514_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,514,62);
} else {self.display_failures(state.consumed.max(state.matched),&["'('"]);}
out
}
fn b514_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"(",true,4,false,"(");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'('");}
out
}
fn e515_c(&mut self,state:State)->Step {
let mut out=self.b515_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:95,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b515_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r89_c(out.state);
out
}
fn e516_c(&mut self,state:State)->Step {
let mut out=self.b516_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,516,62);
} else {self.display_failures(state.consumed.max(state.matched),&["')'"]);}
out
}
fn b516_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,")",true,5,false,")");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"')'");}
out
}
fn e517_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:517,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b517_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b517_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e518_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e519_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e520_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e521_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e518_c(&mut self,state:State)->Step {
let mut out=self.b518_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,518,63);
} else {self.display_failures(state.consumed.max(state.matched),&["'trim'"]);}
out
}
fn b518_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"trim",true,91,false,"trim");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'trim'");}
out
}
fn e519_c(&mut self,state:State)->Step {
let mut out=self.b519_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,519,63);
} else {self.display_failures(state.consumed.max(state.matched),&["'('"]);}
out
}
fn b519_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"(",true,4,false,"(");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'('");}
out
}
fn e520_c(&mut self,state:State)->Step {
let mut out=self.b520_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:96,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b520_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r89_c(out.state);
out
}
fn e521_c(&mut self,state:State)->Step {
let mut out=self.b521_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,521,63);
} else {self.display_failures(state.consumed.max(state.matched),&["')'"]);}
out
}
fn b521_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,")",true,5,false,")");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"')'");}
out
}
fn e522_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:522,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b522_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b522_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e523_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e524_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e525_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e526_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e523_c(&mut self,state:State)->Step {
let mut out=self.b523_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,523,64);
} else {self.display_failures(state.consumed.max(state.matched),&["'length'"]);}
out
}
fn b523_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"length",true,70,false,"length");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'length'");}
out
}
fn e524_c(&mut self,state:State)->Step {
let mut out=self.b524_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,524,64);
} else {self.display_failures(state.consumed.max(state.matched),&["'('"]);}
out
}
fn b524_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"(",true,4,false,"(");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'('");}
out
}
fn e525_c(&mut self,state:State)->Step {
let mut out=self.b525_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:97,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b525_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r89_c(out.state);
out
}
fn e526_c(&mut self,state:State)->Step {
let mut out=self.b526_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,526,64);
} else {self.display_failures(state.consumed.max(state.matched),&["')'"]);}
out
}
fn b526_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,")",true,5,false,")");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"')'");}
out
}
fn e527_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:527,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b527_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b527_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e528_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e529_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e530_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e531_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e528_c(&mut self,state:State)->Step {
let mut out=self.b528_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,528,65);
} else {self.display_failures(state.consumed.max(state.matched),&["'len'"]);}
out
}
fn b528_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"len",true,69,false,"len");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'len'");}
out
}
fn e529_c(&mut self,state:State)->Step {
let mut out=self.b529_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,529,65);
} else {self.display_failures(state.consumed.max(state.matched),&["'('"]);}
out
}
fn b529_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"(",true,4,false,"(");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'('");}
out
}
fn e530_c(&mut self,state:State)->Step {
let mut out=self.b530_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:98,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b530_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r89_c(out.state);
out
}
fn e531_c(&mut self,state:State)->Step {
let mut out=self.b531_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,531,65);
} else {self.display_failures(state.consumed.max(state.matched),&["')'"]);}
out
}
fn b531_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,")",true,5,false,")");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"')'");}
out
}
fn e532_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:532,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b532_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b532_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e533_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e534_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e535_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e536_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e533_c(&mut self,state:State)->Step {
let mut out=self.b533_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:99,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["'$'"]);}
out
}
fn b533_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r121_c(out.state);
out
}
fn e534_c(&mut self,state:State)->Step {
let mut out=self.b534_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,534,66);
} else {self.display_failures(state.consumed.max(state.matched),&["'.toUpperCase'"]);}
out
}
fn b534_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,".toUpperCase",true,18,false,".toUpperCase");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'.toUpperCase'");}
out
}
fn e535_c(&mut self,state:State)->Step {
let mut out=self.b535_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,535,66);
} else {self.display_failures(state.consumed.max(state.matched),&["'('"]);}
out
}
fn b535_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"(",true,4,false,"(");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'('");}
out
}
fn e536_c(&mut self,state:State)->Step {
let mut out=self.b536_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,536,66);
} else {self.display_failures(state.consumed.max(state.matched),&["')'"]);}
out
}
fn b536_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,")",true,5,false,")");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"')'");}
out
}
fn e537_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:537,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b537_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b537_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e538_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e539_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e540_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e541_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e538_c(&mut self,state:State)->Step {
let mut out=self.b538_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:100,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["'$'"]);}
out
}
fn b538_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r121_c(out.state);
out
}
fn e539_c(&mut self,state:State)->Step {
let mut out=self.b539_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,539,67);
} else {self.display_failures(state.consumed.max(state.matched),&["'.toLowerCase'"]);}
out
}
fn b539_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,".toLowerCase",true,17,false,".toLowerCase");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'.toLowerCase'");}
out
}
fn e540_c(&mut self,state:State)->Step {
let mut out=self.b540_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,540,67);
} else {self.display_failures(state.consumed.max(state.matched),&["'('"]);}
out
}
fn b540_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"(",true,4,false,"(");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'('");}
out
}
fn e541_c(&mut self,state:State)->Step {
let mut out=self.b541_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,541,67);
} else {self.display_failures(state.consumed.max(state.matched),&["')'"]);}
out
}
fn b541_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,")",true,5,false,")");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"')'");}
out
}
fn e542_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:542,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b542_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b542_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e543_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e544_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e545_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e546_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e543_c(&mut self,state:State)->Step {
let mut out=self.b543_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:101,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["'$'"]);}
out
}
fn b543_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r121_c(out.state);
out
}
fn e544_c(&mut self,state:State)->Step {
let mut out=self.b544_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,544,68);
} else {self.display_failures(state.consumed.max(state.matched),&["'.trim'"]);}
out
}
fn b544_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,".trim",true,19,false,".trim");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'.trim'");}
out
}
fn e545_c(&mut self,state:State)->Step {
let mut out=self.b545_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,545,68);
} else {self.display_failures(state.consumed.max(state.matched),&["'('"]);}
out
}
fn b545_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"(",true,4,false,"(");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'('");}
out
}
fn e546_c(&mut self,state:State)->Step {
let mut out=self.b546_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,546,68);
} else {self.display_failures(state.consumed.max(state.matched),&["')'"]);}
out
}
fn b546_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,")",true,5,false,")");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"')'");}
out
}
fn e547_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:547,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b547_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b547_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e548_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e549_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e550_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e551_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e548_c(&mut self,state:State)->Step {
let mut out=self.b548_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:102,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["'$'"]);}
out
}
fn b548_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r121_c(out.state);
out
}
fn e549_c(&mut self,state:State)->Step {
let mut out=self.b549_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,549,69);
} else {self.display_failures(state.consumed.max(state.matched),&["'.length'"]);}
out
}
fn b549_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,".length",true,15,false,".length");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'.length'");}
out
}
fn e550_c(&mut self,state:State)->Step {
let mut out=self.b550_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,550,69);
} else {self.display_failures(state.consumed.max(state.matched),&["'('"]);}
out
}
fn b550_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"(",true,4,false,"(");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'('");}
out
}
fn e551_c(&mut self,state:State)->Step {
let mut out=self.b551_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,551,69);
} else {self.display_failures(state.consumed.max(state.matched),&["')'"]);}
out
}
fn b551_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,")",true,5,false,")");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"')'");}
out
}
fn e552_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:552,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b552_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b552_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e553_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e554_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e555_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e556_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e557_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e558_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e562_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e553_c(&mut self,state:State)->Step {
let mut out=self.b553_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,553,70);
} else {self.display_failures(state.consumed.max(state.matched),&["'startsWith'"]);}
out
}
fn b553_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"startsWith",true,85,false,"startsWith");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'startsWith'");}
out
}
fn e554_c(&mut self,state:State)->Step {
let mut out=self.b554_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,554,70);
} else {self.display_failures(state.consumed.max(state.matched),&["'('"]);}
out
}
fn b554_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"(",true,4,false,"(");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'('");}
out
}
fn e555_c(&mut self,state:State)->Step {
let mut out=self.b555_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:103,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b555_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r89_c(out.state);
out
}
fn e556_c(&mut self,state:State)->Step {
let mut out=self.b556_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,556,70);
} else {self.display_failures(state.consumed.max(state.matched),&["','"]);}
out
}
fn b556_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,",",true,8,false,",");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"','");}
out
}
fn e557_c(&mut self,state:State)->Step {
let mut out=self.b557_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:104,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b557_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r89_c(out.state);
out
}
fn e558_c(&mut self,state:State)->Step {
let mut out=self.b558_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.display_failures(state.consumed.max(state.matched),&["','"]);}
out
}
fn b558_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e559_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
}
let _=count;
if false {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e559_c(&mut self,state:State)->Step {
let mut out=self.b559_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.display_failures(state.consumed.max(state.matched),&["','"]);}
out
}
fn b559_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e560_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e561_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e560_c(&mut self,state:State)->Step {
let mut out=self.b560_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,560,70);
} else {self.display_failures(state.consumed.max(state.matched),&["','"]);}
out
}
fn b560_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,",",true,8,false,",");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"','");}
out
}
fn e561_c(&mut self,state:State)->Step {
let mut out=self.b561_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:105,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b561_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r89_c(out.state);
out
}
fn e562_c(&mut self,state:State)->Step {
let mut out=self.b562_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,562,70);
} else {self.display_failures(state.consumed.max(state.matched),&["')'"]);}
out
}
fn b562_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,")",true,5,false,")");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"')'");}
out
}
fn e563_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:563,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b563_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b563_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e564_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e565_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e566_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e567_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e568_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e569_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e573_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e564_c(&mut self,state:State)->Step {
let mut out=self.b564_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,564,71);
} else {self.display_failures(state.consumed.max(state.matched),&["'endsWith'"]);}
out
}
fn b564_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"endsWith",true,56,false,"endsWith");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'endsWith'");}
out
}
fn e565_c(&mut self,state:State)->Step {
let mut out=self.b565_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,565,71);
} else {self.display_failures(state.consumed.max(state.matched),&["'('"]);}
out
}
fn b565_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"(",true,4,false,"(");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'('");}
out
}
fn e566_c(&mut self,state:State)->Step {
let mut out=self.b566_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:106,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b566_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r89_c(out.state);
out
}
fn e567_c(&mut self,state:State)->Step {
let mut out=self.b567_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,567,71);
} else {self.display_failures(state.consumed.max(state.matched),&["','"]);}
out
}
fn b567_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,",",true,8,false,",");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"','");}
out
}
fn e568_c(&mut self,state:State)->Step {
let mut out=self.b568_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:107,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b568_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r89_c(out.state);
out
}
fn e569_c(&mut self,state:State)->Step {
let mut out=self.b569_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.display_failures(state.consumed.max(state.matched),&["','"]);}
out
}
fn b569_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e570_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
}
let _=count;
if false {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e570_c(&mut self,state:State)->Step {
let mut out=self.b570_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.display_failures(state.consumed.max(state.matched),&["','"]);}
out
}
fn b570_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e571_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e572_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e571_c(&mut self,state:State)->Step {
let mut out=self.b571_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,571,71);
} else {self.display_failures(state.consumed.max(state.matched),&["','"]);}
out
}
fn b571_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,",",true,8,false,",");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"','");}
out
}
fn e572_c(&mut self,state:State)->Step {
let mut out=self.b572_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:108,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b572_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r89_c(out.state);
out
}
fn e573_c(&mut self,state:State)->Step {
let mut out=self.b573_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,573,71);
} else {self.display_failures(state.consumed.max(state.matched),&["')'"]);}
out
}
fn b573_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,")",true,5,false,")");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"')'");}
out
}
fn e574_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:574,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b574_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b574_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e575_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e576_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e577_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e578_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e579_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e580_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e584_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e575_c(&mut self,state:State)->Step {
let mut out=self.b575_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,575,72);
} else {self.display_failures(state.consumed.max(state.matched),&["'contains'"]);}
out
}
fn b575_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"contains",true,51,false,"contains");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'contains'");}
out
}
fn e576_c(&mut self,state:State)->Step {
let mut out=self.b576_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,576,72);
} else {self.display_failures(state.consumed.max(state.matched),&["'('"]);}
out
}
fn b576_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"(",true,4,false,"(");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'('");}
out
}
fn e577_c(&mut self,state:State)->Step {
let mut out=self.b577_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:109,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b577_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r89_c(out.state);
out
}
fn e578_c(&mut self,state:State)->Step {
let mut out=self.b578_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,578,72);
} else {self.display_failures(state.consumed.max(state.matched),&["','"]);}
out
}
fn b578_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,",",true,8,false,",");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"','");}
out
}
fn e579_c(&mut self,state:State)->Step {
let mut out=self.b579_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:110,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b579_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r89_c(out.state);
out
}
fn e580_c(&mut self,state:State)->Step {
let mut out=self.b580_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.display_failures(state.consumed.max(state.matched),&["','"]);}
out
}
fn b580_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e581_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
}
let _=count;
if false {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e581_c(&mut self,state:State)->Step {
let mut out=self.b581_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.display_failures(state.consumed.max(state.matched),&["','"]);}
out
}
fn b581_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e582_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e583_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e582_c(&mut self,state:State)->Step {
let mut out=self.b582_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,582,72);
} else {self.display_failures(state.consumed.max(state.matched),&["','"]);}
out
}
fn b582_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,",",true,8,false,",");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"','");}
out
}
fn e583_c(&mut self,state:State)->Step {
let mut out=self.b583_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:111,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b583_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r89_c(out.state);
out
}
fn e584_c(&mut self,state:State)->Step {
let mut out=self.b584_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,584,72);
} else {self.display_failures(state.consumed.max(state.matched),&["')'"]);}
out
}
fn b584_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,")",true,5,false,")");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"')'");}
out
}
fn e585_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:585,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b585_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b585_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e586_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e587_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e588_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e589_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e590_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e594_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e586_c(&mut self,state:State)->Step {
let mut out=self.b586_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:112,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b586_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r89_c(out.state);
out
}
fn e587_c(&mut self,state:State)->Step {
let mut out=self.b587_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,587,73);
} else {self.display_failures(state.consumed.max(state.matched),&["'.in'"]);}
out
}
fn b587_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,".in",true,14,false,".in");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'.in'");}
out
}
fn e588_c(&mut self,state:State)->Step {
let mut out=self.b588_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,588,73);
} else {self.display_failures(state.consumed.max(state.matched),&["'('"]);}
out
}
fn b588_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"(",true,4,false,"(");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'('");}
out
}
fn e589_c(&mut self,state:State)->Step {
let mut out=self.b589_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:113,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b589_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r89_c(out.state);
out
}
fn e590_c(&mut self,state:State)->Step {
let mut out=self.b590_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.display_failures(state.consumed.max(state.matched),&["','"]);}
out
}
fn b590_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e591_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
}
let _=count;
if false {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e591_c(&mut self,state:State)->Step {
let mut out=self.b591_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.display_failures(state.consumed.max(state.matched),&["','"]);}
out
}
fn b591_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e592_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e593_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e592_c(&mut self,state:State)->Step {
let mut out=self.b592_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,592,73);
} else {self.display_failures(state.consumed.max(state.matched),&["','"]);}
out
}
fn b592_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,",",true,8,false,",");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"','");}
out
}
fn e593_c(&mut self,state:State)->Step {
let mut out=self.b593_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:114,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b593_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r89_c(out.state);
out
}
fn e594_c(&mut self,state:State)->Step {
let mut out=self.b594_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,594,73);
} else {self.display_failures(state.consumed.max(state.matched),&["')'"]);}
out
}
fn b594_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,")",true,5,false,")");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"')'");}
out
}
fn e595_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:595,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b595_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b595_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e596_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e597_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e598_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e599_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e600_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e604_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e596_c(&mut self,state:State)->Step {
let mut out=self.b596_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:115,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b596_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r77_c(out.state);
out
}
fn e597_c(&mut self,state:State)->Step {
let mut out=self.b597_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,597,74);
} else {self.display_failures(state.consumed.max(state.matched),&["'.startsWith'"]);}
out
}
fn b597_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,".startsWith",true,16,false,".startsWith");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'.startsWith'");}
out
}
fn e598_c(&mut self,state:State)->Step {
let mut out=self.b598_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,598,74);
} else {self.display_failures(state.consumed.max(state.matched),&["'('"]);}
out
}
fn b598_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"(",true,4,false,"(");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'('");}
out
}
fn e599_c(&mut self,state:State)->Step {
let mut out=self.b599_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:116,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b599_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r89_c(out.state);
out
}
fn e600_c(&mut self,state:State)->Step {
let mut out=self.b600_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.display_failures(state.consumed.max(state.matched),&["','"]);}
out
}
fn b600_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e601_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
}
let _=count;
if false {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e601_c(&mut self,state:State)->Step {
let mut out=self.b601_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.display_failures(state.consumed.max(state.matched),&["','"]);}
out
}
fn b601_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e602_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e603_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e602_c(&mut self,state:State)->Step {
let mut out=self.b602_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,602,74);
} else {self.display_failures(state.consumed.max(state.matched),&["','"]);}
out
}
fn b602_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,",",true,8,false,",");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"','");}
out
}
fn e603_c(&mut self,state:State)->Step {
let mut out=self.b603_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:117,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b603_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r89_c(out.state);
out
}
fn e604_c(&mut self,state:State)->Step {
let mut out=self.b604_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,604,74);
} else {self.display_failures(state.consumed.max(state.matched),&["')'"]);}
out
}
fn b604_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,")",true,5,false,")");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"')'");}
out
}
fn e605_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:605,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b605_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b605_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e606_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e607_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e608_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e609_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e610_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e614_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e606_c(&mut self,state:State)->Step {
let mut out=self.b606_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:118,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b606_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r77_c(out.state);
out
}
fn e607_c(&mut self,state:State)->Step {
let mut out=self.b607_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,607,75);
} else {self.display_failures(state.consumed.max(state.matched),&["'.endsWith'"]);}
out
}
fn b607_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,".endsWith",true,13,false,".endsWith");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'.endsWith'");}
out
}
fn e608_c(&mut self,state:State)->Step {
let mut out=self.b608_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,608,75);
} else {self.display_failures(state.consumed.max(state.matched),&["'('"]);}
out
}
fn b608_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"(",true,4,false,"(");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'('");}
out
}
fn e609_c(&mut self,state:State)->Step {
let mut out=self.b609_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:119,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b609_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r89_c(out.state);
out
}
fn e610_c(&mut self,state:State)->Step {
let mut out=self.b610_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.display_failures(state.consumed.max(state.matched),&["','"]);}
out
}
fn b610_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e611_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
}
let _=count;
if false {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e611_c(&mut self,state:State)->Step {
let mut out=self.b611_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.display_failures(state.consumed.max(state.matched),&["','"]);}
out
}
fn b611_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e612_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e613_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e612_c(&mut self,state:State)->Step {
let mut out=self.b612_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,612,75);
} else {self.display_failures(state.consumed.max(state.matched),&["','"]);}
out
}
fn b612_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,",",true,8,false,",");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"','");}
out
}
fn e613_c(&mut self,state:State)->Step {
let mut out=self.b613_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:120,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b613_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r89_c(out.state);
out
}
fn e614_c(&mut self,state:State)->Step {
let mut out=self.b614_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,614,75);
} else {self.display_failures(state.consumed.max(state.matched),&["')'"]);}
out
}
fn b614_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,")",true,5,false,")");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"')'");}
out
}
fn e615_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:615,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b615_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b615_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e616_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e617_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e618_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e619_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e620_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e624_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e616_c(&mut self,state:State)->Step {
let mut out=self.b616_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:121,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b616_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r77_c(out.state);
out
}
fn e617_c(&mut self,state:State)->Step {
let mut out=self.b617_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,617,76);
} else {self.display_failures(state.consumed.max(state.matched),&["'.contains'"]);}
out
}
fn b617_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,".contains",true,12,false,".contains");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'.contains'");}
out
}
fn e618_c(&mut self,state:State)->Step {
let mut out=self.b618_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,618,76);
} else {self.display_failures(state.consumed.max(state.matched),&["'('"]);}
out
}
fn b618_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"(",true,4,false,"(");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'('");}
out
}
fn e619_c(&mut self,state:State)->Step {
let mut out=self.b619_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:122,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b619_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r89_c(out.state);
out
}
fn e620_c(&mut self,state:State)->Step {
let mut out=self.b620_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.display_failures(state.consumed.max(state.matched),&["','"]);}
out
}
fn b620_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e621_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
}
let _=count;
if false {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e621_c(&mut self,state:State)->Step {
let mut out=self.b621_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.display_failures(state.consumed.max(state.matched),&["','"]);}
out
}
fn b621_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e622_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e623_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e622_c(&mut self,state:State)->Step {
let mut out=self.b622_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,622,76);
} else {self.display_failures(state.consumed.max(state.matched),&["','"]);}
out
}
fn b622_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,",",true,8,false,",");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"','");}
out
}
fn e623_c(&mut self,state:State)->Step {
let mut out=self.b623_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:123,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b623_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r89_c(out.state);
out
}
fn e624_c(&mut self,state:State)->Step {
let mut out=self.b624_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,624,76);
} else {self.display_failures(state.consumed.max(state.matched),&["')'"]);}
out
}
fn b624_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,")",true,5,false,")");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"')'");}
out
}
fn e625_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:625,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b625_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b625_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out='choice: {let mark=self.mark();let mut diagnostic=Diag::NONE;let mut best:Option<(Step,[usize;2])>=None;
let ct=self.input.cp_at(self.skip_0::<false>(out.state.begin()).position::<false>()).map(|(c,_)|c);
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,116))) {self.guard_e626_c(out.state.begin())} else {self.e626_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,116))) {self.guard_e627_c(out.state.begin())} else {self.e627_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,116))) {self.guard_e628_c(out.state.begin())} else {self.e628_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,36))) {self.guard_e629_c(out.state.begin())} else {self.e629_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);break 'choice if let Some((mut child,effects))=best {self.replay_effects(effects);child.diag=diagnostic;child} else {out.ok=false;out.diag=diagnostic;out};
};
out
}
fn e626_c(&mut self,state:State)->Step {
let mut out=self.b626_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b626_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r62_c(out.state);
out
}
fn e627_c(&mut self,state:State)->Step {
let mut out=self.b627_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b627_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r61_c(out.state);
out
}
fn e628_c(&mut self,state:State)->Step {
let mut out=self.b628_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b628_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r63_c(out.state);
out
}
fn e629_c(&mut self,state:State)->Step {
let mut out=self.b629_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b629_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r121_c(out.state);
out
}
fn e630_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:630,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b630_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b630_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e631_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e632_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e633_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e634_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e631_c(&mut self,state:State)->Step {
let mut out=self.b631_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,631,78);
} else {self.display_failures(state.consumed.max(state.matched),&["'isPresent'"]);}
out
}
fn b631_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"isPresent",true,68,false,"isPresent");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'isPresent'");}
out
}
fn e632_c(&mut self,state:State)->Step {
let mut out=self.b632_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,632,78);
} else {self.display_failures(state.consumed.max(state.matched),&["'('"]);}
out
}
fn b632_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"(",true,4,false,"(");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'('");}
out
}
fn e633_c(&mut self,state:State)->Step {
let mut out=self.b633_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:124,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["'$'"]);}
out
}
fn b633_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r121_c(out.state);
out
}
fn e634_c(&mut self,state:State)->Step {
let mut out=self.b634_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,634,78);
} else {self.display_failures(state.consumed.max(state.matched),&["')'"]);}
out
}
fn b634_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,")",true,5,false,")");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"')'");}
out
}
fn e635_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:635,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b635_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b635_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e636_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e637_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e638_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e639_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e640_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e641_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e636_c(&mut self,state:State)->Step {
let mut out=self.b636_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,636,79);
} else {self.display_failures(state.consumed.max(state.matched),&["'inTimeRange'"]);}
out
}
fn b636_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"inTimeRange",true,66,false,"inTimeRange");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'inTimeRange'");}
out
}
fn e637_c(&mut self,state:State)->Step {
let mut out=self.b637_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,637,79);
} else {self.display_failures(state.consumed.max(state.matched),&["'('"]);}
out
}
fn b637_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"(",true,4,false,"(");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'('");}
out
}
fn e638_c(&mut self,state:State)->Step {
let mut out=self.b638_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:125,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b638_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r40_c(out.state);
out
}
fn e639_c(&mut self,state:State)->Step {
let mut out=self.b639_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,639,79);
} else {self.display_failures(state.consumed.max(state.matched),&["','"]);}
out
}
fn b639_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,",",true,8,false,",");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"','");}
out
}
fn e640_c(&mut self,state:State)->Step {
let mut out=self.b640_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:126,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b640_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r40_c(out.state);
out
}
fn e641_c(&mut self,state:State)->Step {
let mut out=self.b641_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,641,79);
} else {self.display_failures(state.consumed.max(state.matched),&["')'"]);}
out
}
fn b641_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,")",true,5,false,")");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"')'");}
out
}
fn e642_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:642,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b642_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b642_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e643_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e644_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e645_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e646_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e647_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e648_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e649_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e650_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e651_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e652_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e643_c(&mut self,state:State)->Step {
let mut out=self.b643_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,643,80);
} else {self.display_failures(state.consumed.max(state.matched),&["'inDayTimeRange'"]);}
out
}
fn b643_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"inDayTimeRange",true,65,false,"inDayTimeRange");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'inDayTimeRange'");}
out
}
fn e644_c(&mut self,state:State)->Step {
let mut out=self.b644_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,644,80);
} else {self.display_failures(state.consumed.max(state.matched),&["'('"]);}
out
}
fn b644_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"(",true,4,false,"(");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'('");}
out
}
fn e645_c(&mut self,state:State)->Step {
let mut out=self.b645_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:127,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["'FRIDAY'", "'MONDAY'", "'SATURDAY'", "'SUNDAY'", "'THURSDAY'", "'TUESDAY'", "'WEDNESDAY'"]);}
out
}
fn b645_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r81_c(out.state);
out
}
fn e646_c(&mut self,state:State)->Step {
let mut out=self.b646_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,646,80);
} else {self.display_failures(state.consumed.max(state.matched),&["','"]);}
out
}
fn b646_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,",",true,8,false,",");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"','");}
out
}
fn e647_c(&mut self,state:State)->Step {
let mut out=self.b647_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:128,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b647_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r40_c(out.state);
out
}
fn e648_c(&mut self,state:State)->Step {
let mut out=self.b648_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,648,80);
} else {self.display_failures(state.consumed.max(state.matched),&["','"]);}
out
}
fn b648_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,",",true,8,false,",");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"','");}
out
}
fn e649_c(&mut self,state:State)->Step {
let mut out=self.b649_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:129,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["'FRIDAY'", "'MONDAY'", "'SATURDAY'", "'SUNDAY'", "'THURSDAY'", "'TUESDAY'", "'WEDNESDAY'"]);}
out
}
fn b649_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r81_c(out.state);
out
}
fn e650_c(&mut self,state:State)->Step {
let mut out=self.b650_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,650,80);
} else {self.display_failures(state.consumed.max(state.matched),&["','"]);}
out
}
fn b650_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,",",true,8,false,",");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"','");}
out
}
fn e651_c(&mut self,state:State)->Step {
let mut out=self.b651_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:130,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b651_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r40_c(out.state);
out
}
fn e652_c(&mut self,state:State)->Step {
let mut out=self.b652_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,652,80);
} else {self.display_failures(state.consumed.max(state.matched),&["')'"]);}
out
}
fn b652_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,")",true,5,false,")");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"')'");}
out
}
fn e653_c(&mut self,state:State)->Step {
let mut out=self.b653_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b653_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out='choice: {let mark=self.mark();let mut diagnostic=Diag::NONE;let mut best:Option<(Step,[usize;2])>=None;
self.restore(mark);let mut child=self.e654_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e655_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e656_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e657_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e658_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e659_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e660_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);break 'choice if let Some((mut child,effects))=best {self.replay_effects(effects);child.diag=diagnostic;child} else {out.ok=false;out.diag=diagnostic;out};
};
out
}
fn e654_c(&mut self,state:State)->Step {
let mut out=self.b654_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,654,81);
} else {self.display_failures(state.consumed.max(state.matched),&["'MONDAY'"]);}
out
}
fn b654_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"MONDAY",true,34,false,"MONDAY");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'MONDAY'");}
out
}
fn e655_c(&mut self,state:State)->Step {
let mut out=self.b655_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,655,81);
} else {self.display_failures(state.consumed.max(state.matched),&["'TUESDAY'"]);}
out
}
fn b655_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"TUESDAY",true,41,false,"TUESDAY");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'TUESDAY'");}
out
}
fn e656_c(&mut self,state:State)->Step {
let mut out=self.b656_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,656,81);
} else {self.display_failures(state.consumed.max(state.matched),&["'WEDNESDAY'"]);}
out
}
fn b656_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"WEDNESDAY",true,42,false,"WEDNESDAY");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'WEDNESDAY'");}
out
}
fn e657_c(&mut self,state:State)->Step {
let mut out=self.b657_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,657,81);
} else {self.display_failures(state.consumed.max(state.matched),&["'THURSDAY'"]);}
out
}
fn b657_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"THURSDAY",true,40,false,"THURSDAY");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'THURSDAY'");}
out
}
fn e658_c(&mut self,state:State)->Step {
let mut out=self.b658_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,658,81);
} else {self.display_failures(state.consumed.max(state.matched),&["'FRIDAY'"]);}
out
}
fn b658_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"FRIDAY",true,32,false,"FRIDAY");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'FRIDAY'");}
out
}
fn e659_c(&mut self,state:State)->Step {
let mut out=self.b659_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,659,81);
} else {self.display_failures(state.consumed.max(state.matched),&["'SATURDAY'"]);}
out
}
fn b659_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"SATURDAY",true,37,false,"SATURDAY");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'SATURDAY'");}
out
}
fn e660_c(&mut self,state:State)->Step {
let mut out=self.b660_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,660,81);
} else {self.display_failures(state.consumed.max(state.matched),&["'SUNDAY'"]);}
out
}
fn b660_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"SUNDAY",true,38,false,"SUNDAY");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'SUNDAY'");}
out
}
fn e661_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:661,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b661_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b661_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out='choice: {let mark=self.mark();let mut diagnostic=Diag::NONE;let mut best:Option<(Step,[usize;2])>=None;
let ct=self.input.cp_at(self.skip_0::<false>(out.state.begin()).position::<false>()).map(|(c,_)|c);
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,101))) {self.guard_e662_c(out.state.begin())} else {self.e662_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,40))) {self.guard_e663_c(out.state.begin())} else {self.e663_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,40))) {self.guard_e667_c(out.state.begin())} else {self.e667_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,116))) {self.guard_e668_c(out.state.begin())} else {self.e668_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,116))) {self.guard_e669_c(out.state.begin())} else {self.e669_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,116))) {self.guard_e670_c(out.state.begin())} else {self.e670_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,36))) {self.guard_e671_c(out.state.begin())} else {self.e671_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,36))) {self.guard_e672_c(out.state.begin())} else {self.e672_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,36))) {self.guard_e673_c(out.state.begin())} else {self.e673_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e674_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,36))) {self.guard_e675_c(out.state.begin())} else {self.e675_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,99|105))) {self.guard_e676_c(out.state.begin())} else {self.e676_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);break 'choice if let Some((mut child,effects))=best {self.replay_effects(effects);child.diag=diagnostic;child} else {out.ok=false;out.diag=diagnostic;out};
};
out
}
fn e662_c(&mut self,state:State)->Step {
let mut out=self.b662_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b662_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r33_c(out.state);
out
}
fn e663_c(&mut self,state:State)->Step {
let mut out=self.b663_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.display_failures(state.consumed.max(state.matched),&["'$'", "'('", "')'"]);}
out
}
fn b663_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e664_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e665_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e666_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e664_c(&mut self,state:State)->Step {
let mut out=self.b664_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,664,82);
} else {self.display_failures(state.consumed.max(state.matched),&["'('"]);}
out
}
fn b664_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"(",true,4,false,"(");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'('");}
out
}
fn e665_c(&mut self,state:State)->Step {
let mut out=self.b665_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b665_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r121_c(out.state);
out
}
fn e666_c(&mut self,state:State)->Step {
let mut out=self.b666_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,666,82);
} else {self.display_failures(state.consumed.max(state.matched),&["')'"]);}
out
}
fn b666_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,")",true,5,false,")");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"')'");}
out
}
fn e667_c(&mut self,state:State)->Step {
let mut out=self.b667_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b667_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r90_c(out.state);
out
}
fn e668_c(&mut self,state:State)->Step {
let mut out=self.b668_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b668_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r61_c(out.state);
out
}
fn e669_c(&mut self,state:State)->Step {
let mut out=self.b669_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b669_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r62_c(out.state);
out
}
fn e670_c(&mut self,state:State)->Step {
let mut out=self.b670_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b670_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r63_c(out.state);
out
}
fn e671_c(&mut self,state:State)->Step {
let mut out=self.b671_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b671_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r66_c(out.state);
out
}
fn e672_c(&mut self,state:State)->Step {
let mut out=self.b672_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b672_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r67_c(out.state);
out
}
fn e673_c(&mut self,state:State)->Step {
let mut out=self.b673_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b673_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r68_c(out.state);
out
}
fn e674_c(&mut self,state:State)->Step {
let mut out=self.b674_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,674,82);
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b674_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.token_2::<false>(out.state,"STRING","StringLiteralParser");
out
}
fn e675_c(&mut self,state:State)->Step {
let mut out=self.b675_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b675_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r121_c(out.state);
out
}
fn e676_c(&mut self,state:State)->Step {
let mut out=self.b676_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b676_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r36_c(out.state);
out
}
fn e677_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:677,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b677_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b677_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e678_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e678_c(&mut self,state:State)->Step {
let mut out=self.b678_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b678_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r40_c(out.state);
out
}
fn e679_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:679,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b679_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b679_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e680_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e680_c(&mut self,state:State)->Step {
let mut out=self.b680_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b680_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r40_c(out.state);
out
}
fn e681_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:681,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b681_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b681_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e682_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e682_c(&mut self,state:State)->Step {
let mut out=self.b682_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b682_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r40_c(out.state);
out
}
fn e683_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:683,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b683_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b683_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out='choice: {let mark=self.mark();let mut diagnostic=Diag::NONE;let mut best:Option<(Step,[usize;2])>=None;
let ct=self.input.cp_at(self.skip_0::<false>(out.state.begin()).position::<false>()).map(|(c,_)|c);
if self.options.factor && !out.state.invert {
let entry=out.state.begin();
let mut child='factored: {
self.restore(mark);
{let mut st=entry;
st.reset=false;
let mut cur=Step::yes(st);
cur.state=st.begin();
let trivia=self.trivia_1::<false>(cur.state);cur=self.combine(cur,trivia);
let p0=cur.state;let child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,34|36|39..=40|99|101|105|116))) {self.guard_e685_c(cur.state)} else {self.e685_c(cur.state)};let ev0=child.events;let _=(p0,ev0);cur=self.combine(cur,child);
if !cur.ok {
self.display_failures(entry.consumed.max(entry.matched),&["':'", "'['", "']'"]);
self.display_failures(p0.consumed.max(p0.matched),&["__CaptureSite"]);
diagnostic=self.diag_join(diagnostic,cur.diag);
} else {
let trivia=self.trivia_1::<false>(cur.state);cur=self.combine(cur,trivia);
let p4=cur.state;
let child=self.e686_c(cur.state);let ev3=child.events;let _=(p4,ev3);cur=self.combine(cur,child);
if !cur.ok {
self.display_failures(entry.consumed.max(entry.matched),&["':'", "'['", "']'"]);
self.display_failures(p4.consumed.max(p4.matched),&["'['"]);
diagnostic=self.diag_join(diagnostic,cur.diag);
} else {
let trivia=self.trivia_1::<false>(cur.state);cur=self.combine(cur,trivia);
let m5=self.mark();let b6=cur;
let p8=cur.state;
let child=self.e687_c(cur.state);let ev7=child.events;let _=(p8,ev7);cur=self.combine(cur,child);
if !cur.ok {
self.display_failures(entry.consumed.max(entry.matched),&["':'", "'['", "']'"]);
self.display_failures(p8.consumed.max(p8.matched),&["__CaptureSite"]);
diagnostic=self.diag_join(diagnostic,cur.diag);
} else {
let trivia=self.trivia_1::<false>(cur.state);cur=self.combine(cur,trivia);
let p12=cur.state;
let child=self.e688_c(cur.state);let ev11=child.events;let _=(p12,ev11);cur=self.combine(cur,child);
if !cur.ok {
self.display_failures(entry.consumed.max(entry.matched),&["':'", "'['", "']'"]);
self.display_failures(p12.consumed.max(p12.matched),&["':'"]);
diagnostic=self.diag_join(diagnostic,cur.diag);
} else {
let trivia=self.trivia_1::<false>(cur.state);cur=self.combine(cur,trivia);
let m13=self.mark();let b14=cur;
let p16=cur.state;
let child=self.e689_c(cur.state);let ev15=child.events;let _=(p16,ev15);cur=self.combine(cur,child);
if !cur.ok {
self.display_failures(entry.consumed.max(entry.matched),&["':'", "'['", "']'"]);
self.display_failures(p16.consumed.max(p16.matched),&["__CaptureSite"]);
diagnostic=self.diag_join(diagnostic,cur.diag);
} else {
let trivia=self.trivia_1::<false>(cur.state);cur=self.combine(cur,trivia);
let m17=self.mark();let b18=cur;
'f19: {
let child=self.e690_c(cur.state);cur=self.combine(cur,child);if !cur.ok {break 'f19;}
let trivia=self.trivia_1::<false>(cur.state);cur=self.combine(cur,trivia);
let child=self.e691_c(cur.state);cur=self.combine(cur,child);if !cur.ok {break 'f19;}
let trivia=self.trivia_1::<false>(cur.state);cur=self.combine(cur,trivia);
let child=self.e692_c(cur.state);cur=self.combine(cur,child);if !cur.ok {break 'f19;}
let trivia=self.trivia_1::<false>(cur.state);cur=self.combine(cur,trivia);
cur.state=st.commit(cur.state);
cur.events=self.event(Event::Values{span:[entry.consumed,cur.state.consumed],child:cur.events,wrap:false});
diagnostic=self.diag_join(diagnostic,cur.diag);break 'factored cur;
}
self.display_failures(entry.consumed.max(entry.matched),&["':'", "'['", "']'"]);
diagnostic=self.diag_join(diagnostic,cur.diag);
self.restore(m17);cur=b18;
'f20: {
let child=self.e699_c(cur.state);cur=self.combine(cur,child);if !cur.ok {break 'f20;}
let trivia=self.trivia_1::<false>(cur.state);cur=self.combine(cur,trivia);
cur.state=st.commit(cur.state);
cur.events=self.event(Event::Values{span:[entry.consumed,cur.state.consumed],child:cur.events,wrap:false});
self.retag(ev0,Some(135),None);
self.retag(ev3,None,Some(695));
self.retag(ev7,Some(136),None);
self.retag(ev11,None,Some(697));
self.retag(ev15,Some(137),None);
diagnostic=self.diag_join(diagnostic,cur.diag);break 'factored cur;
}
self.display_failures(entry.consumed.max(entry.matched),&["':'", "'['", "']'"]);
diagnostic=self.diag_join(diagnostic,cur.diag);
}
self.restore(m13);cur=b14;
'f21: {
let child=self.e705_c(cur.state);cur=self.combine(cur,child);if !cur.ok {break 'f21;}
let trivia=self.trivia_1::<false>(cur.state);cur=self.combine(cur,trivia);
let child=self.e706_c(cur.state);cur=self.combine(cur,child);if !cur.ok {break 'f21;}
let trivia=self.trivia_1::<false>(cur.state);cur=self.combine(cur,trivia);
let child=self.e707_c(cur.state);cur=self.combine(cur,child);if !cur.ok {break 'f21;}
let trivia=self.trivia_1::<false>(cur.state);cur=self.combine(cur,trivia);
cur.state=st.commit(cur.state);
cur.events=self.event(Event::Values{span:[entry.consumed,cur.state.consumed],child:cur.events,wrap:false});
self.retag(ev0,Some(138),None);
self.retag(ev3,None,Some(702));
self.retag(ev7,Some(139),None);
self.retag(ev11,None,Some(704));
diagnostic=self.diag_join(diagnostic,cur.diag);break 'factored cur;
}
self.display_failures(entry.consumed.max(entry.matched),&["':'", "'['", "']'"]);
diagnostic=self.diag_join(diagnostic,cur.diag);
self.restore(m13);cur=b14;
'f22: {
let child=self.e713_c(cur.state);cur=self.combine(cur,child);if !cur.ok {break 'f22;}
let trivia=self.trivia_1::<false>(cur.state);cur=self.combine(cur,trivia);
cur.state=st.commit(cur.state);
cur.events=self.event(Event::Values{span:[entry.consumed,cur.state.consumed],child:cur.events,wrap:false});
self.retag(ev0,Some(141),None);
self.retag(ev3,None,Some(710));
self.retag(ev7,Some(142),None);
self.retag(ev11,None,Some(712));
diagnostic=self.diag_join(diagnostic,cur.diag);break 'factored cur;
}
self.display_failures(entry.consumed.max(entry.matched),&["':'", "'['", "']'"]);
diagnostic=self.diag_join(diagnostic,cur.diag);
}
}
self.restore(m5);cur=b6;
let p24=cur.state;
let child=self.e717_c(cur.state);let ev23=child.events;let _=(p24,ev23);cur=self.combine(cur,child);
if !cur.ok {
self.display_failures(entry.consumed.max(entry.matched),&["':'", "'['", "']'"]);
self.display_failures(p24.consumed.max(p24.matched),&["':'"]);
diagnostic=self.diag_join(diagnostic,cur.diag);
} else {
let trivia=self.trivia_1::<false>(cur.state);cur=self.combine(cur,trivia);
let m25=self.mark();let b26=cur;
let p28=cur.state;
let child=self.e718_c(cur.state);let ev27=child.events;let _=(p28,ev27);cur=self.combine(cur,child);
if !cur.ok {
self.display_failures(entry.consumed.max(entry.matched),&["':'", "'['", "']'"]);
self.display_failures(p28.consumed.max(p28.matched),&["__CaptureSite"]);
diagnostic=self.diag_join(diagnostic,cur.diag);
} else {
let trivia=self.trivia_1::<false>(cur.state);cur=self.combine(cur,trivia);
let m29=self.mark();let b30=cur;
'f31: {
let child=self.e719_c(cur.state);cur=self.combine(cur,child);if !cur.ok {break 'f31;}
let trivia=self.trivia_1::<false>(cur.state);cur=self.combine(cur,trivia);
let child=self.e720_c(cur.state);cur=self.combine(cur,child);if !cur.ok {break 'f31;}
let trivia=self.trivia_1::<false>(cur.state);cur=self.combine(cur,trivia);
let child=self.e721_c(cur.state);cur=self.combine(cur,child);if !cur.ok {break 'f31;}
let trivia=self.trivia_1::<false>(cur.state);cur=self.combine(cur,trivia);
cur.state=st.commit(cur.state);
cur.events=self.event(Event::Values{span:[entry.consumed,cur.state.consumed],child:cur.events,wrap:false});
self.retag(ev0,Some(143),None);
self.retag(ev3,None,Some(716));
diagnostic=self.diag_join(diagnostic,cur.diag);break 'factored cur;
}
self.display_failures(entry.consumed.max(entry.matched),&["':'", "'['", "']'"]);
diagnostic=self.diag_join(diagnostic,cur.diag);
self.restore(m29);cur=b30;
'f32: {
let child=self.e727_c(cur.state);cur=self.combine(cur,child);if !cur.ok {break 'f32;}
let trivia=self.trivia_1::<false>(cur.state);cur=self.combine(cur,trivia);
cur.state=st.commit(cur.state);
cur.events=self.event(Event::Values{span:[entry.consumed,cur.state.consumed],child:cur.events,wrap:false});
self.retag(ev0,Some(146),None);
self.retag(ev3,None,Some(724));
self.retag(ev23,None,Some(725));
self.retag(ev27,Some(147),None);
diagnostic=self.diag_join(diagnostic,cur.diag);break 'factored cur;
}
self.display_failures(entry.consumed.max(entry.matched),&["':'", "'['", "']'"]);
diagnostic=self.diag_join(diagnostic,cur.diag);
}
self.restore(m25);cur=b26;
'f33: {
let child=self.e732_c(cur.state);cur=self.combine(cur,child);if !cur.ok {break 'f33;}
let trivia=self.trivia_1::<false>(cur.state);cur=self.combine(cur,trivia);
let child=self.e733_c(cur.state);cur=self.combine(cur,child);if !cur.ok {break 'f33;}
let trivia=self.trivia_1::<false>(cur.state);cur=self.combine(cur,trivia);
let child=self.e734_c(cur.state);cur=self.combine(cur,child);if !cur.ok {break 'f33;}
let trivia=self.trivia_1::<false>(cur.state);cur=self.combine(cur,trivia);
cur.state=st.commit(cur.state);
cur.events=self.event(Event::Values{span:[entry.consumed,cur.state.consumed],child:cur.events,wrap:false});
self.retag(ev0,Some(148),None);
self.retag(ev3,None,Some(730));
self.retag(ev23,None,Some(731));
diagnostic=self.diag_join(diagnostic,cur.diag);break 'factored cur;
}
self.display_failures(entry.consumed.max(entry.matched),&["':'", "'['", "']'"]);
diagnostic=self.diag_join(diagnostic,cur.diag);
self.restore(m25);cur=b26;
'f34: {
let child=self.e739_c(cur.state);cur=self.combine(cur,child);if !cur.ok {break 'f34;}
let trivia=self.trivia_1::<false>(cur.state);cur=self.combine(cur,trivia);
cur.state=st.commit(cur.state);
cur.events=self.event(Event::Values{span:[entry.consumed,cur.state.consumed],child:cur.events,wrap:true});
self.retag(ev0,Some(150),None);
self.retag(ev3,None,Some(737));
self.retag(ev23,None,Some(738));
diagnostic=self.diag_join(diagnostic,cur.diag);break 'factored cur;
}
self.display_failures(entry.consumed.max(entry.matched),&["':'", "'['", "']'"]);
diagnostic=self.diag_join(diagnostic,cur.diag);
}
}
}
}
let _=entry;self.restore(mark);out.ok=false;out.diag=diagnostic;out
};
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);break 'choice child;
}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,34|36|39..=40|99|101|105|116))) {self.guard_e684_c(out.state.begin())} else {self.e684_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,34|36|39..=40|99|101|105|116))) {self.guard_e693_c(out.state.begin())} else {self.e693_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,34|36|39..=40|99|101|105|116))) {self.guard_e700_c(out.state.begin())} else {self.e700_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,34|36|39..=40|99|101|105|116))) {self.guard_e708_c(out.state.begin())} else {self.e708_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,34|36|39..=40|99|101|105|116))) {self.guard_e714_c(out.state.begin())} else {self.e714_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,34|36|39..=40|99|101|105|116))) {self.guard_e722_c(out.state.begin())} else {self.e722_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,34|36|39..=40|99|101|105|116))) {self.guard_e728_c(out.state.begin())} else {self.e728_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,34|36|39..=40|99|101|105|116))) {self.guard_e735_c(out.state.begin())} else {self.e735_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);break 'choice if let Some((mut child,effects))=best {self.replay_effects(effects);child.diag=diagnostic;child} else {out.ok=false;out.diag=diagnostic;out};
};
out
}
fn e684_c(&mut self,state:State)->Step {
let mut out=self.b684_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.display_failures(state.consumed.max(state.matched),&["':'", "'['", "']'"]);}
out
}
fn b684_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e685_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e686_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e687_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e688_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e689_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e690_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e691_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e692_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e685_c(&mut self,state:State)->Step {
let mut out=self.b685_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:131,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b685_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r82_c(out.state);
out
}
fn e686_c(&mut self,state:State)->Step {
let mut out=self.b686_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,686,86);
} else {self.display_failures(state.consumed.max(state.matched),&["'['"]);}
out
}
fn b686_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"[",true,43,false,"[");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'['");}
out
}
fn e687_c(&mut self,state:State)->Step {
let mut out=self.b687_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:132,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b687_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r83_c(out.state);
out
}
fn e688_c(&mut self,state:State)->Step {
let mut out=self.b688_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,688,86);
} else {self.display_failures(state.consumed.max(state.matched),&["':'"]);}
out
}
fn b688_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,":",true,21,false,":");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"':'");}
out
}
fn e689_c(&mut self,state:State)->Step {
let mut out=self.b689_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:133,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b689_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r84_c(out.state);
out
}
fn e690_c(&mut self,state:State)->Step {
let mut out=self.b690_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,690,86);
} else {self.display_failures(state.consumed.max(state.matched),&["':'"]);}
out
}
fn b690_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,":",true,21,false,":");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"':'");}
out
}
fn e691_c(&mut self,state:State)->Step {
let mut out=self.b691_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:134,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b691_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r85_c(out.state);
out
}
fn e692_c(&mut self,state:State)->Step {
let mut out=self.b692_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,692,86);
} else {self.display_failures(state.consumed.max(state.matched),&["']'"]);}
out
}
fn b692_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"]",true,44,false,"]");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"']'");}
out
}
fn e693_c(&mut self,state:State)->Step {
let mut out=self.b693_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.display_failures(state.consumed.max(state.matched),&["':'", "'['", "']'"]);}
out
}
fn b693_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e694_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e695_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e696_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e697_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e698_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e699_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e694_c(&mut self,state:State)->Step {
let mut out=self.b694_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:135,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b694_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r82_c(out.state);
out
}
fn e695_c(&mut self,state:State)->Step {
let mut out=self.b695_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,695,86);
} else {self.display_failures(state.consumed.max(state.matched),&["'['"]);}
out
}
fn b695_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"[",true,43,false,"[");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'['");}
out
}
fn e696_c(&mut self,state:State)->Step {
let mut out=self.b696_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:136,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b696_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r83_c(out.state);
out
}
fn e697_c(&mut self,state:State)->Step {
let mut out=self.b697_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,697,86);
} else {self.display_failures(state.consumed.max(state.matched),&["':'"]);}
out
}
fn b697_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,":",true,21,false,":");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"':'");}
out
}
fn e698_c(&mut self,state:State)->Step {
let mut out=self.b698_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:137,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b698_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r84_c(out.state);
out
}
fn e699_c(&mut self,state:State)->Step {
let mut out=self.b699_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,699,86);
} else {self.display_failures(state.consumed.max(state.matched),&["']'"]);}
out
}
fn b699_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"]",true,44,false,"]");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"']'");}
out
}
fn e700_c(&mut self,state:State)->Step {
let mut out=self.b700_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.display_failures(state.consumed.max(state.matched),&["':'", "'['", "']'"]);}
out
}
fn b700_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e701_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e702_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e703_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e704_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e705_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e706_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e707_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e701_c(&mut self,state:State)->Step {
let mut out=self.b701_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:138,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b701_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r82_c(out.state);
out
}
fn e702_c(&mut self,state:State)->Step {
let mut out=self.b702_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,702,86);
} else {self.display_failures(state.consumed.max(state.matched),&["'['"]);}
out
}
fn b702_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"[",true,43,false,"[");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'['");}
out
}
fn e703_c(&mut self,state:State)->Step {
let mut out=self.b703_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:139,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b703_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r83_c(out.state);
out
}
fn e704_c(&mut self,state:State)->Step {
let mut out=self.b704_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,704,86);
} else {self.display_failures(state.consumed.max(state.matched),&["':'"]);}
out
}
fn b704_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,":",true,21,false,":");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"':'");}
out
}
fn e705_c(&mut self,state:State)->Step {
let mut out=self.b705_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,705,86);
} else {self.display_failures(state.consumed.max(state.matched),&["':'"]);}
out
}
fn b705_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,":",true,21,false,":");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"':'");}
out
}
fn e706_c(&mut self,state:State)->Step {
let mut out=self.b706_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:140,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b706_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r85_c(out.state);
out
}
fn e707_c(&mut self,state:State)->Step {
let mut out=self.b707_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,707,86);
} else {self.display_failures(state.consumed.max(state.matched),&["']'"]);}
out
}
fn b707_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"]",true,44,false,"]");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"']'");}
out
}
fn e708_c(&mut self,state:State)->Step {
let mut out=self.b708_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.display_failures(state.consumed.max(state.matched),&["':'", "'['", "']'"]);}
out
}
fn b708_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e709_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e710_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e711_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e712_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e713_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e709_c(&mut self,state:State)->Step {
let mut out=self.b709_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:141,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b709_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r82_c(out.state);
out
}
fn e710_c(&mut self,state:State)->Step {
let mut out=self.b710_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,710,86);
} else {self.display_failures(state.consumed.max(state.matched),&["'['"]);}
out
}
fn b710_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"[",true,43,false,"[");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'['");}
out
}
fn e711_c(&mut self,state:State)->Step {
let mut out=self.b711_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:142,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b711_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r83_c(out.state);
out
}
fn e712_c(&mut self,state:State)->Step {
let mut out=self.b712_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,712,86);
} else {self.display_failures(state.consumed.max(state.matched),&["':'"]);}
out
}
fn b712_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,":",true,21,false,":");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"':'");}
out
}
fn e713_c(&mut self,state:State)->Step {
let mut out=self.b713_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,713,86);
} else {self.display_failures(state.consumed.max(state.matched),&["']'"]);}
out
}
fn b713_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"]",true,44,false,"]");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"']'");}
out
}
fn e714_c(&mut self,state:State)->Step {
let mut out=self.b714_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.display_failures(state.consumed.max(state.matched),&["':'", "'['", "']'"]);}
out
}
fn b714_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e715_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e716_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e717_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e718_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e719_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e720_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e721_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e715_c(&mut self,state:State)->Step {
let mut out=self.b715_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:143,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b715_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r82_c(out.state);
out
}
fn e716_c(&mut self,state:State)->Step {
let mut out=self.b716_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,716,86);
} else {self.display_failures(state.consumed.max(state.matched),&["'['"]);}
out
}
fn b716_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"[",true,43,false,"[");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'['");}
out
}
fn e717_c(&mut self,state:State)->Step {
let mut out=self.b717_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,717,86);
} else {self.display_failures(state.consumed.max(state.matched),&["':'"]);}
out
}
fn b717_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,":",true,21,false,":");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"':'");}
out
}
fn e718_c(&mut self,state:State)->Step {
let mut out=self.b718_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:144,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b718_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r84_c(out.state);
out
}
fn e719_c(&mut self,state:State)->Step {
let mut out=self.b719_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,719,86);
} else {self.display_failures(state.consumed.max(state.matched),&["':'"]);}
out
}
fn b719_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,":",true,21,false,":");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"':'");}
out
}
fn e720_c(&mut self,state:State)->Step {
let mut out=self.b720_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:145,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b720_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r85_c(out.state);
out
}
fn e721_c(&mut self,state:State)->Step {
let mut out=self.b721_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,721,86);
} else {self.display_failures(state.consumed.max(state.matched),&["']'"]);}
out
}
fn b721_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"]",true,44,false,"]");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"']'");}
out
}
fn e722_c(&mut self,state:State)->Step {
let mut out=self.b722_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.display_failures(state.consumed.max(state.matched),&["':'", "'['", "']'"]);}
out
}
fn b722_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e723_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e724_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e725_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e726_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e727_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e723_c(&mut self,state:State)->Step {
let mut out=self.b723_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:146,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b723_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r82_c(out.state);
out
}
fn e724_c(&mut self,state:State)->Step {
let mut out=self.b724_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,724,86);
} else {self.display_failures(state.consumed.max(state.matched),&["'['"]);}
out
}
fn b724_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"[",true,43,false,"[");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'['");}
out
}
fn e725_c(&mut self,state:State)->Step {
let mut out=self.b725_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,725,86);
} else {self.display_failures(state.consumed.max(state.matched),&["':'"]);}
out
}
fn b725_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,":",true,21,false,":");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"':'");}
out
}
fn e726_c(&mut self,state:State)->Step {
let mut out=self.b726_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:147,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b726_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r84_c(out.state);
out
}
fn e727_c(&mut self,state:State)->Step {
let mut out=self.b727_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,727,86);
} else {self.display_failures(state.consumed.max(state.matched),&["']'"]);}
out
}
fn b727_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"]",true,44,false,"]");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"']'");}
out
}
fn e728_c(&mut self,state:State)->Step {
let mut out=self.b728_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.display_failures(state.consumed.max(state.matched),&["':'", "'['", "']'"]);}
out
}
fn b728_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e729_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e730_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e731_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e732_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e733_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e734_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e729_c(&mut self,state:State)->Step {
let mut out=self.b729_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:148,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b729_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r82_c(out.state);
out
}
fn e730_c(&mut self,state:State)->Step {
let mut out=self.b730_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,730,86);
} else {self.display_failures(state.consumed.max(state.matched),&["'['"]);}
out
}
fn b730_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"[",true,43,false,"[");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'['");}
out
}
fn e731_c(&mut self,state:State)->Step {
let mut out=self.b731_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,731,86);
} else {self.display_failures(state.consumed.max(state.matched),&["':'"]);}
out
}
fn b731_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,":",true,21,false,":");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"':'");}
out
}
fn e732_c(&mut self,state:State)->Step {
let mut out=self.b732_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,732,86);
} else {self.display_failures(state.consumed.max(state.matched),&["':'"]);}
out
}
fn b732_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,":",true,21,false,":");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"':'");}
out
}
fn e733_c(&mut self,state:State)->Step {
let mut out=self.b733_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:149,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b733_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r85_c(out.state);
out
}
fn e734_c(&mut self,state:State)->Step {
let mut out=self.b734_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,734,86);
} else {self.display_failures(state.consumed.max(state.matched),&["']'"]);}
out
}
fn b734_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"]",true,44,false,"]");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"']'");}
out
}
fn e735_c(&mut self,state:State)->Step {
let mut out=self.b735_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.display_failures(state.consumed.max(state.matched),&["':'", "'['", "']'"]);}
out
}
fn b735_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e736_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e737_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e738_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e739_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e736_c(&mut self,state:State)->Step {
let mut out=self.b736_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:150,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b736_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r82_c(out.state);
out
}
fn e737_c(&mut self,state:State)->Step {
let mut out=self.b737_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,737,86);
} else {self.display_failures(state.consumed.max(state.matched),&["'['"]);}
out
}
fn b737_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"[",true,43,false,"[");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'['");}
out
}
fn e738_c(&mut self,state:State)->Step {
let mut out=self.b738_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,738,86);
} else {self.display_failures(state.consumed.max(state.matched),&["':'"]);}
out
}
fn b738_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,":",true,21,false,":");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"':'");}
out
}
fn e739_c(&mut self,state:State)->Step {
let mut out=self.b739_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,739,86);
} else {self.display_failures(state.consumed.max(state.matched),&["']'"]);}
out
}
fn b739_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"]",true,44,false,"]");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"']'");}
out
}
fn e740_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:740,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b740_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b740_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out='choice: {let mark=self.mark();let mut diagnostic=Diag::NONE;let mut best:Option<(Step,[usize;2])>=None;
if self.options.factor && !out.state.invert {
let entry=out.state.begin();
let mut child='factored: {
self.restore(mark);
{let mut st=entry;
st.reset=false;
let mut cur=Step::yes(st);
cur.state=st.begin();
let trivia=self.trivia_1::<false>(cur.state);cur=self.combine(cur,trivia);
let p0=cur.state;let child=self.e742_c(cur.state);let ev0=child.events;let _=(p0,ev0);cur=self.combine(cur,child);
if !cur.ok {
self.display_failures(entry.consumed.max(entry.matched),&["':'", "'['", "']'"]);
self.display_failures(p0.consumed.max(p0.matched),&["__CaptureSite"]);
diagnostic=self.diag_join(diagnostic,cur.diag);
} else {
let trivia=self.trivia_1::<false>(cur.state);cur=self.combine(cur,trivia);
let p4=cur.state;
let child=self.e743_c(cur.state);let ev3=child.events;let _=(p4,ev3);cur=self.combine(cur,child);
if !cur.ok {
self.display_failures(entry.consumed.max(entry.matched),&["':'", "'['", "']'"]);
self.display_failures(p4.consumed.max(p4.matched),&["'['"]);
diagnostic=self.diag_join(diagnostic,cur.diag);
} else {
let trivia=self.trivia_1::<false>(cur.state);cur=self.combine(cur,trivia);
let m5=self.mark();let b6=cur;
let p8=cur.state;
let child=self.e744_c(cur.state);let ev7=child.events;let _=(p8,ev7);cur=self.combine(cur,child);
if !cur.ok {
self.display_failures(entry.consumed.max(entry.matched),&["':'", "'['", "']'"]);
self.display_failures(p8.consumed.max(p8.matched),&["__CaptureSite"]);
diagnostic=self.diag_join(diagnostic,cur.diag);
} else {
let trivia=self.trivia_1::<false>(cur.state);cur=self.combine(cur,trivia);
let p12=cur.state;
let child=self.e745_c(cur.state);let ev11=child.events;let _=(p12,ev11);cur=self.combine(cur,child);
if !cur.ok {
self.display_failures(entry.consumed.max(entry.matched),&["':'", "'['", "']'"]);
self.display_failures(p12.consumed.max(p12.matched),&["':'"]);
diagnostic=self.diag_join(diagnostic,cur.diag);
} else {
let trivia=self.trivia_1::<false>(cur.state);cur=self.combine(cur,trivia);
let m13=self.mark();let b14=cur;
let p16=cur.state;
let child=self.e746_c(cur.state);let ev15=child.events;let _=(p16,ev15);cur=self.combine(cur,child);
if !cur.ok {
self.display_failures(entry.consumed.max(entry.matched),&["':'", "'['", "']'"]);
self.display_failures(p16.consumed.max(p16.matched),&["__CaptureSite"]);
diagnostic=self.diag_join(diagnostic,cur.diag);
} else {
let trivia=self.trivia_1::<false>(cur.state);cur=self.combine(cur,trivia);
let m17=self.mark();let b18=cur;
'f19: {
let child=self.e747_c(cur.state);cur=self.combine(cur,child);if !cur.ok {break 'f19;}
let trivia=self.trivia_1::<false>(cur.state);cur=self.combine(cur,trivia);
let child=self.e748_c(cur.state);cur=self.combine(cur,child);if !cur.ok {break 'f19;}
let trivia=self.trivia_1::<false>(cur.state);cur=self.combine(cur,trivia);
let child=self.e749_c(cur.state);cur=self.combine(cur,child);if !cur.ok {break 'f19;}
let trivia=self.trivia_1::<false>(cur.state);cur=self.combine(cur,trivia);
cur.state=st.commit(cur.state);
cur.events=self.event(Event::Values{span:[entry.consumed,cur.state.consumed],child:cur.events,wrap:false});
diagnostic=self.diag_join(diagnostic,cur.diag);break 'factored cur;
}
self.display_failures(entry.consumed.max(entry.matched),&["':'", "'['", "']'"]);
diagnostic=self.diag_join(diagnostic,cur.diag);
self.restore(m17);cur=b18;
'f20: {
let child=self.e756_c(cur.state);cur=self.combine(cur,child);if !cur.ok {break 'f20;}
let trivia=self.trivia_1::<false>(cur.state);cur=self.combine(cur,trivia);
cur.state=st.commit(cur.state);
cur.events=self.event(Event::Values{span:[entry.consumed,cur.state.consumed],child:cur.events,wrap:false});
self.retag(ev0,Some(155),None);
self.retag(ev3,None,Some(752));
self.retag(ev7,Some(156),None);
self.retag(ev11,None,Some(754));
self.retag(ev15,Some(157),None);
diagnostic=self.diag_join(diagnostic,cur.diag);break 'factored cur;
}
self.display_failures(entry.consumed.max(entry.matched),&["':'", "'['", "']'"]);
diagnostic=self.diag_join(diagnostic,cur.diag);
}
self.restore(m13);cur=b14;
'f21: {
let child=self.e762_c(cur.state);cur=self.combine(cur,child);if !cur.ok {break 'f21;}
let trivia=self.trivia_1::<false>(cur.state);cur=self.combine(cur,trivia);
let child=self.e763_c(cur.state);cur=self.combine(cur,child);if !cur.ok {break 'f21;}
let trivia=self.trivia_1::<false>(cur.state);cur=self.combine(cur,trivia);
let child=self.e764_c(cur.state);cur=self.combine(cur,child);if !cur.ok {break 'f21;}
let trivia=self.trivia_1::<false>(cur.state);cur=self.combine(cur,trivia);
cur.state=st.commit(cur.state);
cur.events=self.event(Event::Values{span:[entry.consumed,cur.state.consumed],child:cur.events,wrap:false});
self.retag(ev0,Some(158),None);
self.retag(ev3,None,Some(759));
self.retag(ev7,Some(159),None);
self.retag(ev11,None,Some(761));
diagnostic=self.diag_join(diagnostic,cur.diag);break 'factored cur;
}
self.display_failures(entry.consumed.max(entry.matched),&["':'", "'['", "']'"]);
diagnostic=self.diag_join(diagnostic,cur.diag);
self.restore(m13);cur=b14;
'f22: {
let child=self.e770_c(cur.state);cur=self.combine(cur,child);if !cur.ok {break 'f22;}
let trivia=self.trivia_1::<false>(cur.state);cur=self.combine(cur,trivia);
cur.state=st.commit(cur.state);
cur.events=self.event(Event::Values{span:[entry.consumed,cur.state.consumed],child:cur.events,wrap:false});
self.retag(ev0,Some(161),None);
self.retag(ev3,None,Some(767));
self.retag(ev7,Some(162),None);
self.retag(ev11,None,Some(769));
diagnostic=self.diag_join(diagnostic,cur.diag);break 'factored cur;
}
self.display_failures(entry.consumed.max(entry.matched),&["':'", "'['", "']'"]);
diagnostic=self.diag_join(diagnostic,cur.diag);
}
}
self.restore(m5);cur=b6;
let p24=cur.state;
let child=self.e774_c(cur.state);let ev23=child.events;let _=(p24,ev23);cur=self.combine(cur,child);
if !cur.ok {
self.display_failures(entry.consumed.max(entry.matched),&["':'", "'['", "']'"]);
self.display_failures(p24.consumed.max(p24.matched),&["':'"]);
diagnostic=self.diag_join(diagnostic,cur.diag);
} else {
let trivia=self.trivia_1::<false>(cur.state);cur=self.combine(cur,trivia);
let m25=self.mark();let b26=cur;
let p28=cur.state;
let child=self.e775_c(cur.state);let ev27=child.events;let _=(p28,ev27);cur=self.combine(cur,child);
if !cur.ok {
self.display_failures(entry.consumed.max(entry.matched),&["':'", "'['", "']'"]);
self.display_failures(p28.consumed.max(p28.matched),&["__CaptureSite"]);
diagnostic=self.diag_join(diagnostic,cur.diag);
} else {
let trivia=self.trivia_1::<false>(cur.state);cur=self.combine(cur,trivia);
let m29=self.mark();let b30=cur;
'f31: {
let child=self.e776_c(cur.state);cur=self.combine(cur,child);if !cur.ok {break 'f31;}
let trivia=self.trivia_1::<false>(cur.state);cur=self.combine(cur,trivia);
let child=self.e777_c(cur.state);cur=self.combine(cur,child);if !cur.ok {break 'f31;}
let trivia=self.trivia_1::<false>(cur.state);cur=self.combine(cur,trivia);
let child=self.e778_c(cur.state);cur=self.combine(cur,child);if !cur.ok {break 'f31;}
let trivia=self.trivia_1::<false>(cur.state);cur=self.combine(cur,trivia);
cur.state=st.commit(cur.state);
cur.events=self.event(Event::Values{span:[entry.consumed,cur.state.consumed],child:cur.events,wrap:false});
self.retag(ev0,Some(163),None);
self.retag(ev3,None,Some(773));
diagnostic=self.diag_join(diagnostic,cur.diag);break 'factored cur;
}
self.display_failures(entry.consumed.max(entry.matched),&["':'", "'['", "']'"]);
diagnostic=self.diag_join(diagnostic,cur.diag);
self.restore(m29);cur=b30;
'f32: {
let child=self.e784_c(cur.state);cur=self.combine(cur,child);if !cur.ok {break 'f32;}
let trivia=self.trivia_1::<false>(cur.state);cur=self.combine(cur,trivia);
cur.state=st.commit(cur.state);
cur.events=self.event(Event::Values{span:[entry.consumed,cur.state.consumed],child:cur.events,wrap:false});
self.retag(ev0,Some(166),None);
self.retag(ev3,None,Some(781));
self.retag(ev23,None,Some(782));
self.retag(ev27,Some(167),None);
diagnostic=self.diag_join(diagnostic,cur.diag);break 'factored cur;
}
self.display_failures(entry.consumed.max(entry.matched),&["':'", "'['", "']'"]);
diagnostic=self.diag_join(diagnostic,cur.diag);
}
self.restore(m25);cur=b26;
'f33: {
let child=self.e789_c(cur.state);cur=self.combine(cur,child);if !cur.ok {break 'f33;}
let trivia=self.trivia_1::<false>(cur.state);cur=self.combine(cur,trivia);
let child=self.e790_c(cur.state);cur=self.combine(cur,child);if !cur.ok {break 'f33;}
let trivia=self.trivia_1::<false>(cur.state);cur=self.combine(cur,trivia);
let child=self.e791_c(cur.state);cur=self.combine(cur,child);if !cur.ok {break 'f33;}
let trivia=self.trivia_1::<false>(cur.state);cur=self.combine(cur,trivia);
cur.state=st.commit(cur.state);
cur.events=self.event(Event::Values{span:[entry.consumed,cur.state.consumed],child:cur.events,wrap:false});
self.retag(ev0,Some(168),None);
self.retag(ev3,None,Some(787));
self.retag(ev23,None,Some(788));
diagnostic=self.diag_join(diagnostic,cur.diag);break 'factored cur;
}
self.display_failures(entry.consumed.max(entry.matched),&["':'", "'['", "']'"]);
diagnostic=self.diag_join(diagnostic,cur.diag);
self.restore(m25);cur=b26;
'f34: {
let child=self.e796_c(cur.state);cur=self.combine(cur,child);if !cur.ok {break 'f34;}
let trivia=self.trivia_1::<false>(cur.state);cur=self.combine(cur,trivia);
cur.state=st.commit(cur.state);
cur.events=self.event(Event::Values{span:[entry.consumed,cur.state.consumed],child:cur.events,wrap:true});
self.retag(ev0,Some(170),None);
self.retag(ev3,None,Some(794));
self.retag(ev23,None,Some(795));
diagnostic=self.diag_join(diagnostic,cur.diag);break 'factored cur;
}
self.display_failures(entry.consumed.max(entry.matched),&["':'", "'['", "']'"]);
diagnostic=self.diag_join(diagnostic,cur.diag);
}
}
}
}
let _=entry;self.restore(mark);out.ok=false;out.diag=diagnostic;out
};
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);break 'choice child;
}
self.restore(mark);let mut child=self.e741_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e750_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e757_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e765_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e771_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e779_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e785_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e792_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);break 'choice if let Some((mut child,effects))=best {self.replay_effects(effects);child.diag=diagnostic;child} else {out.ok=false;out.diag=diagnostic;out};
};
out
}
fn e741_c(&mut self,state:State)->Step {
let mut out=self.b741_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.display_failures(state.consumed.max(state.matched),&["':'", "'['", "']'"]);}
out
}
fn b741_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e742_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e743_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e744_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e745_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e746_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e747_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e748_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e749_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e742_c(&mut self,state:State)->Step {
let mut out=self.b742_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:151,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b742_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r86_c(out.state);
out
}
fn e743_c(&mut self,state:State)->Step {
let mut out=self.b743_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,743,87);
} else {self.display_failures(state.consumed.max(state.matched),&["'['"]);}
out
}
fn b743_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"[",true,43,false,"[");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'['");}
out
}
fn e744_c(&mut self,state:State)->Step {
let mut out=self.b744_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:152,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b744_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r83_c(out.state);
out
}
fn e745_c(&mut self,state:State)->Step {
let mut out=self.b745_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,745,87);
} else {self.display_failures(state.consumed.max(state.matched),&["':'"]);}
out
}
fn b745_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,":",true,21,false,":");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"':'");}
out
}
fn e746_c(&mut self,state:State)->Step {
let mut out=self.b746_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:153,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b746_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r84_c(out.state);
out
}
fn e747_c(&mut self,state:State)->Step {
let mut out=self.b747_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,747,87);
} else {self.display_failures(state.consumed.max(state.matched),&["':'"]);}
out
}
fn b747_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,":",true,21,false,":");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"':'");}
out
}
fn e748_c(&mut self,state:State)->Step {
let mut out=self.b748_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:154,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b748_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r85_c(out.state);
out
}
fn e749_c(&mut self,state:State)->Step {
let mut out=self.b749_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,749,87);
} else {self.display_failures(state.consumed.max(state.matched),&["']'"]);}
out
}
fn b749_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"]",true,44,false,"]");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"']'");}
out
}
fn e750_c(&mut self,state:State)->Step {
let mut out=self.b750_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.display_failures(state.consumed.max(state.matched),&["':'", "'['", "']'"]);}
out
}
fn b750_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e751_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e752_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e753_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e754_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e755_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e756_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e751_c(&mut self,state:State)->Step {
let mut out=self.b751_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:155,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b751_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r86_c(out.state);
out
}
fn e752_c(&mut self,state:State)->Step {
let mut out=self.b752_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,752,87);
} else {self.display_failures(state.consumed.max(state.matched),&["'['"]);}
out
}
fn b752_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"[",true,43,false,"[");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'['");}
out
}
fn e753_c(&mut self,state:State)->Step {
let mut out=self.b753_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:156,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b753_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r83_c(out.state);
out
}
fn e754_c(&mut self,state:State)->Step {
let mut out=self.b754_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,754,87);
} else {self.display_failures(state.consumed.max(state.matched),&["':'"]);}
out
}
fn b754_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,":",true,21,false,":");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"':'");}
out
}
fn e755_c(&mut self,state:State)->Step {
let mut out=self.b755_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:157,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b755_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r84_c(out.state);
out
}
fn e756_c(&mut self,state:State)->Step {
let mut out=self.b756_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,756,87);
} else {self.display_failures(state.consumed.max(state.matched),&["']'"]);}
out
}
fn b756_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"]",true,44,false,"]");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"']'");}
out
}
fn e757_c(&mut self,state:State)->Step {
let mut out=self.b757_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.display_failures(state.consumed.max(state.matched),&["':'", "'['", "']'"]);}
out
}
fn b757_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e758_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e759_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e760_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e761_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e762_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e763_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e764_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e758_c(&mut self,state:State)->Step {
let mut out=self.b758_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:158,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b758_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r86_c(out.state);
out
}
fn e759_c(&mut self,state:State)->Step {
let mut out=self.b759_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,759,87);
} else {self.display_failures(state.consumed.max(state.matched),&["'['"]);}
out
}
fn b759_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"[",true,43,false,"[");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'['");}
out
}
fn e760_c(&mut self,state:State)->Step {
let mut out=self.b760_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:159,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b760_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r83_c(out.state);
out
}
fn e761_c(&mut self,state:State)->Step {
let mut out=self.b761_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,761,87);
} else {self.display_failures(state.consumed.max(state.matched),&["':'"]);}
out
}
fn b761_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,":",true,21,false,":");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"':'");}
out
}
fn e762_c(&mut self,state:State)->Step {
let mut out=self.b762_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,762,87);
} else {self.display_failures(state.consumed.max(state.matched),&["':'"]);}
out
}
fn b762_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,":",true,21,false,":");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"':'");}
out
}
fn e763_c(&mut self,state:State)->Step {
let mut out=self.b763_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:160,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b763_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r85_c(out.state);
out
}
fn e764_c(&mut self,state:State)->Step {
let mut out=self.b764_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,764,87);
} else {self.display_failures(state.consumed.max(state.matched),&["']'"]);}
out
}
fn b764_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"]",true,44,false,"]");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"']'");}
out
}
fn e765_c(&mut self,state:State)->Step {
let mut out=self.b765_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.display_failures(state.consumed.max(state.matched),&["':'", "'['", "']'"]);}
out
}
fn b765_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e766_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e767_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e768_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e769_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e770_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e766_c(&mut self,state:State)->Step {
let mut out=self.b766_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:161,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b766_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r86_c(out.state);
out
}
fn e767_c(&mut self,state:State)->Step {
let mut out=self.b767_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,767,87);
} else {self.display_failures(state.consumed.max(state.matched),&["'['"]);}
out
}
fn b767_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"[",true,43,false,"[");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'['");}
out
}
fn e768_c(&mut self,state:State)->Step {
let mut out=self.b768_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:162,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b768_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r83_c(out.state);
out
}
fn e769_c(&mut self,state:State)->Step {
let mut out=self.b769_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,769,87);
} else {self.display_failures(state.consumed.max(state.matched),&["':'"]);}
out
}
fn b769_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,":",true,21,false,":");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"':'");}
out
}
fn e770_c(&mut self,state:State)->Step {
let mut out=self.b770_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,770,87);
} else {self.display_failures(state.consumed.max(state.matched),&["']'"]);}
out
}
fn b770_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"]",true,44,false,"]");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"']'");}
out
}
fn e771_c(&mut self,state:State)->Step {
let mut out=self.b771_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.display_failures(state.consumed.max(state.matched),&["':'", "'['", "']'"]);}
out
}
fn b771_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e772_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e773_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e774_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e775_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e776_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e777_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e778_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e772_c(&mut self,state:State)->Step {
let mut out=self.b772_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:163,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b772_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r86_c(out.state);
out
}
fn e773_c(&mut self,state:State)->Step {
let mut out=self.b773_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,773,87);
} else {self.display_failures(state.consumed.max(state.matched),&["'['"]);}
out
}
fn b773_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"[",true,43,false,"[");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'['");}
out
}
fn e774_c(&mut self,state:State)->Step {
let mut out=self.b774_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,774,87);
} else {self.display_failures(state.consumed.max(state.matched),&["':'"]);}
out
}
fn b774_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,":",true,21,false,":");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"':'");}
out
}
fn e775_c(&mut self,state:State)->Step {
let mut out=self.b775_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:164,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b775_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r84_c(out.state);
out
}
fn e776_c(&mut self,state:State)->Step {
let mut out=self.b776_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,776,87);
} else {self.display_failures(state.consumed.max(state.matched),&["':'"]);}
out
}
fn b776_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,":",true,21,false,":");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"':'");}
out
}
fn e777_c(&mut self,state:State)->Step {
let mut out=self.b777_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:165,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b777_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r85_c(out.state);
out
}
fn e778_c(&mut self,state:State)->Step {
let mut out=self.b778_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,778,87);
} else {self.display_failures(state.consumed.max(state.matched),&["']'"]);}
out
}
fn b778_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"]",true,44,false,"]");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"']'");}
out
}
fn e779_c(&mut self,state:State)->Step {
let mut out=self.b779_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.display_failures(state.consumed.max(state.matched),&["':'", "'['", "']'"]);}
out
}
fn b779_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e780_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e781_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e782_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e783_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e784_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e780_c(&mut self,state:State)->Step {
let mut out=self.b780_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:166,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b780_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r86_c(out.state);
out
}
fn e781_c(&mut self,state:State)->Step {
let mut out=self.b781_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,781,87);
} else {self.display_failures(state.consumed.max(state.matched),&["'['"]);}
out
}
fn b781_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"[",true,43,false,"[");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'['");}
out
}
fn e782_c(&mut self,state:State)->Step {
let mut out=self.b782_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,782,87);
} else {self.display_failures(state.consumed.max(state.matched),&["':'"]);}
out
}
fn b782_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,":",true,21,false,":");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"':'");}
out
}
fn e783_c(&mut self,state:State)->Step {
let mut out=self.b783_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:167,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b783_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r84_c(out.state);
out
}
fn e784_c(&mut self,state:State)->Step {
let mut out=self.b784_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,784,87);
} else {self.display_failures(state.consumed.max(state.matched),&["']'"]);}
out
}
fn b784_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"]",true,44,false,"]");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"']'");}
out
}
fn e785_c(&mut self,state:State)->Step {
let mut out=self.b785_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.display_failures(state.consumed.max(state.matched),&["':'", "'['", "']'"]);}
out
}
fn b785_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e786_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e787_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e788_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e789_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e790_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e791_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e786_c(&mut self,state:State)->Step {
let mut out=self.b786_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:168,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b786_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r86_c(out.state);
out
}
fn e787_c(&mut self,state:State)->Step {
let mut out=self.b787_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,787,87);
} else {self.display_failures(state.consumed.max(state.matched),&["'['"]);}
out
}
fn b787_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"[",true,43,false,"[");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'['");}
out
}
fn e788_c(&mut self,state:State)->Step {
let mut out=self.b788_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,788,87);
} else {self.display_failures(state.consumed.max(state.matched),&["':'"]);}
out
}
fn b788_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,":",true,21,false,":");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"':'");}
out
}
fn e789_c(&mut self,state:State)->Step {
let mut out=self.b789_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,789,87);
} else {self.display_failures(state.consumed.max(state.matched),&["':'"]);}
out
}
fn b789_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,":",true,21,false,":");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"':'");}
out
}
fn e790_c(&mut self,state:State)->Step {
let mut out=self.b790_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:169,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b790_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r85_c(out.state);
out
}
fn e791_c(&mut self,state:State)->Step {
let mut out=self.b791_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,791,87);
} else {self.display_failures(state.consumed.max(state.matched),&["']'"]);}
out
}
fn b791_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"]",true,44,false,"]");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"']'");}
out
}
fn e792_c(&mut self,state:State)->Step {
let mut out=self.b792_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.display_failures(state.consumed.max(state.matched),&["':'", "'['", "']'"]);}
out
}
fn b792_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e793_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e794_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e795_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e796_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e793_c(&mut self,state:State)->Step {
let mut out=self.b793_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:170,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b793_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r86_c(out.state);
out
}
fn e794_c(&mut self,state:State)->Step {
let mut out=self.b794_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,794,87);
} else {self.display_failures(state.consumed.max(state.matched),&["'['"]);}
out
}
fn b794_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"[",true,43,false,"[");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'['");}
out
}
fn e795_c(&mut self,state:State)->Step {
let mut out=self.b795_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,795,87);
} else {self.display_failures(state.consumed.max(state.matched),&["':'"]);}
out
}
fn b795_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,":",true,21,false,":");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"':'");}
out
}
fn e796_c(&mut self,state:State)->Step {
let mut out=self.b796_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,796,87);
} else {self.display_failures(state.consumed.max(state.matched),&["']'"]);}
out
}
fn b796_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"]",true,44,false,"]");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"']'");}
out
}
fn e797_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:797,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b797_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b797_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out='choice: {let mark=self.mark();let mut diagnostic=Diag::NONE;let mut best:Option<(Step,[usize;2])>=None;
self.restore(mark);let mut child=self.e798_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e799_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);break 'choice if let Some((mut child,effects))=best {self.replay_effects(effects);child.diag=diagnostic;child} else {out.ok=false;out.diag=diagnostic;out};
};
out
}
fn e798_c(&mut self,state:State)->Step {
let mut out=self.b798_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b798_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r87_c(out.state);
out
}
fn e799_c(&mut self,state:State)->Step {
let mut out=self.b799_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b799_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r86_c(out.state);
out
}
fn e800_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:800,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b800_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b800_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e801_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e802_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e801_c(&mut self,state:State)->Step {
let mut out=self.b801_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:171,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b801_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r91_c(out.state);
out
}
fn e802_c(&mut self,state:State)->Step {
let mut out=self.b802_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.display_failures(state.consumed.max(state.matched),&["Repeat"]);}
out
}
fn b802_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e803_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
}
let _=count;
if false {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e803_c(&mut self,state:State)->Step {
let mut out=self.b803_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.display_failures(state.consumed.max(state.matched),&["'+'"]);}
out
}
fn b803_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e804_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e805_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e804_c(&mut self,state:State)->Step {
let mut out=self.b804_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,804,89);
out.events=self.event(Event::Capture {site:172,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["'+'"]);}
out
}
fn b804_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"+",true,7,false,"+");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'+'");}
out
}
fn e805_c(&mut self,state:State)->Step {
let mut out=self.b805_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:173,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b805_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r91_c(out.state);
out
}
fn e806_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:806,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b806_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b806_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e807_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e808_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e809_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e807_c(&mut self,state:State)->Step {
let mut out=self.b807_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,807,90);
} else {self.display_failures(state.consumed.max(state.matched),&["'('"]);}
out
}
fn b807_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"(",true,4,false,"(");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'('");}
out
}
fn e808_c(&mut self,state:State)->Step {
let mut out=self.b808_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b808_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r89_c(out.state);
out
}
fn e809_c(&mut self,state:State)->Step {
let mut out=self.b809_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,809,90);
} else {self.display_failures(state.consumed.max(state.matched),&["')'"]);}
out
}
fn b809_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,")",true,5,false,")");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"')'");}
out
}
fn e810_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:810,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b810_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b810_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out='choice: {let mark=self.mark();let mut diagnostic=Diag::NONE;let mut best:Option<(Step,[usize;2])>=None;
let ct=self.input.cp_at(self.skip_0::<false>(out.state.begin()).position::<false>()).map(|(c,_)|c);
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,109))) {self.guard_e811_c(out.state.begin())} else {self.e811_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,105))) {self.guard_e812_c(out.state.begin())} else {self.e812_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,40))) {self.guard_e813_c(out.state.begin())} else {self.e813_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,36))) {self.guard_e814_c(out.state.begin())} else {self.e814_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e815_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,40))) {self.guard_e816_c(out.state.begin())} else {self.e816_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,101))) {self.guard_e817_c(out.state.begin())} else {self.e817_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,116))) {self.guard_e818_c(out.state.begin())} else {self.e818_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,116))) {self.guard_e819_c(out.state.begin())} else {self.e819_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,116))) {self.guard_e820_c(out.state.begin())} else {self.e820_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,36))) {self.guard_e821_c(out.state.begin())} else {self.e821_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,36))) {self.guard_e822_c(out.state.begin())} else {self.e822_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,36))) {self.guard_e823_c(out.state.begin())} else {self.e823_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e824_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,36))) {self.guard_e825_c(out.state.begin())} else {self.e825_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,99|105))) {self.guard_e826_c(out.state.begin())} else {self.e826_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);break 'choice if let Some((mut child,effects))=best {self.replay_effects(effects);child.diag=diagnostic;child} else {out.ok=false;out.diag=diagnostic;out};
};
out
}
fn e811_c(&mut self,state:State)->Step {
let mut out=self.b811_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b811_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r113_c(out.state);
out
}
fn e812_c(&mut self,state:State)->Step {
let mut out=self.b812_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b812_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r106_c(out.state);
out
}
fn e813_c(&mut self,state:State)->Step {
let mut out=self.b813_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b813_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r92_c(out.state);
out
}
fn e814_c(&mut self,state:State)->Step {
let mut out=self.b814_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b814_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r93_c(out.state);
out
}
fn e815_c(&mut self,state:State)->Step {
let mut out=self.b815_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b815_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r88_c(out.state);
out
}
fn e816_c(&mut self,state:State)->Step {
let mut out=self.b816_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b816_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r90_c(out.state);
out
}
fn e817_c(&mut self,state:State)->Step {
let mut out=self.b817_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b817_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r33_c(out.state);
out
}
fn e818_c(&mut self,state:State)->Step {
let mut out=self.b818_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b818_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r61_c(out.state);
out
}
fn e819_c(&mut self,state:State)->Step {
let mut out=self.b819_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b819_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r62_c(out.state);
out
}
fn e820_c(&mut self,state:State)->Step {
let mut out=self.b820_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b820_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r63_c(out.state);
out
}
fn e821_c(&mut self,state:State)->Step {
let mut out=self.b821_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b821_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r66_c(out.state);
out
}
fn e822_c(&mut self,state:State)->Step {
let mut out=self.b822_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b822_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r67_c(out.state);
out
}
fn e823_c(&mut self,state:State)->Step {
let mut out=self.b823_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b823_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r68_c(out.state);
out
}
fn e824_c(&mut self,state:State)->Step {
let mut out=self.b824_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,824,91);
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b824_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.token_2::<false>(out.state,"STRING","StringLiteralParser");
out
}
fn e825_c(&mut self,state:State)->Step {
let mut out=self.b825_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b825_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r121_c(out.state);
out
}
fn e826_c(&mut self,state:State)->Step {
let mut out=self.b826_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b826_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r36_c(out.state);
out
}
fn e827_c(&mut self,state:State)->Step {
let mut out=self.b827_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b827_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e828_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e829_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e833_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e834_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e835_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e828_c(&mut self,state:State)->Step {
let mut out=self.b828_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,828,92);
} else {self.display_failures(state.consumed.max(state.matched),&["'('"]);}
out
}
fn b828_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"(",true,4,false,"(");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'('");}
out
}
fn e829_c(&mut self,state:State)->Step {
let mut out=self.b829_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'String'", "'string'"]);}
out
}
fn b829_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.e830_c(out.state);
out
}
fn e830_c(&mut self,state:State)->Step {
let mut out=self.b830_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'String'", "'string'"]);}
out
}
fn b830_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out='choice: {let mark=self.mark();let mut diagnostic=Diag::NONE;let mut best:Option<(Step,[usize;2])>=None;
self.restore(mark);let mut child=self.e831_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e832_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);break 'choice if let Some((mut child,effects))=best {self.replay_effects(effects);child.diag=diagnostic;child} else {out.ok=false;out.diag=diagnostic;out};
};
out
}
fn e831_c(&mut self,state:State)->Step {
let mut out=self.b831_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,831,92);
} else {self.display_failures(state.consumed.max(state.matched),&["'string'"]);}
out
}
fn b831_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"string",true,86,false,"string");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'string'");}
out
}
fn e832_c(&mut self,state:State)->Step {
let mut out=self.b832_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,832,92);
} else {self.display_failures(state.consumed.max(state.matched),&["'String'"]);}
out
}
fn b832_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"String",true,39,false,"String");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'String'");}
out
}
fn e833_c(&mut self,state:State)->Step {
let mut out=self.b833_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,833,92);
} else {self.display_failures(state.consumed.max(state.matched),&["')'"]);}
out
}
fn b833_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,")",true,5,false,")");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"')'");}
out
}
fn e834_c(&mut self,state:State)->Step {
let mut out=self.b834_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,834,92);
} else {self.display_failures(state.consumed.max(state.matched),&["'$'"]);}
out
}
fn b834_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"$",true,2,false,"$");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'$'");}
out
}
fn e835_c(&mut self,state:State)->Step {
let mut out=self.b835_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,835,92);
out.events=self.event(Event::Capture {site:174,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["IdentifierParser", "__CaptureSite"]);}
out
}
fn b835_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.token_1::<false>(out.state,"IDENTIFIER","IdentifierParser");
out
}
fn e836_c(&mut self,state:State)->Step {
let mut out=self.b836_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b836_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e837_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e838_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e839_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e840_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e837_c(&mut self,state:State)->Step {
let mut out=self.b837_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,837,93);
} else {self.display_failures(state.consumed.max(state.matched),&["'$'"]);}
out
}
fn b837_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"$",true,2,false,"$");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'$'");}
out
}
fn e838_c(&mut self,state:State)->Step {
let mut out=self.b838_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,838,93);
out.events=self.event(Event::Capture {site:175,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["IdentifierParser", "__CaptureSite"]);}
out
}
fn b838_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.token_1::<false>(out.state,"IDENTIFIER","IdentifierParser");
out
}
fn e839_c(&mut self,state:State)->Step {
let mut out=self.b839_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,839,93);
} else {self.display_failures(state.consumed.max(state.matched),&["'as'"]);}
out
}
fn b839_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"as",true,47,false,"as");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'as'");}
out
}
fn e840_c(&mut self,state:State)->Step {
let mut out=self.b840_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'String'", "'string'"]);}
out
}
fn b840_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.e841_c(out.state);
out
}
fn e841_c(&mut self,state:State)->Step {
let mut out=self.b841_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'String'", "'string'"]);}
out
}
fn b841_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out='choice: {let mark=self.mark();let mut diagnostic=Diag::NONE;let mut best:Option<(Step,[usize;2])>=None;
self.restore(mark);let mut child=self.e842_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e843_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);break 'choice if let Some((mut child,effects))=best {self.replay_effects(effects);child.diag=diagnostic;child} else {out.ok=false;out.diag=diagnostic;out};
};
out
}
fn e842_c(&mut self,state:State)->Step {
let mut out=self.b842_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,842,93);
} else {self.display_failures(state.consumed.max(state.matched),&["'string'"]);}
out
}
fn b842_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"string",true,86,false,"string");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'string'");}
out
}
fn e843_c(&mut self,state:State)->Step {
let mut out=self.b843_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,843,93);
} else {self.display_failures(state.consumed.max(state.matched),&["'String'"]);}
out
}
fn b843_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"String",true,39,false,"String");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'String'");}
out
}
fn e844_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:844,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b844_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b844_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e845_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e846_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e845_c(&mut self,state:State)->Step {
let mut out=self.b845_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:176,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b845_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r95_c(out.state);
out
}
fn e846_c(&mut self,state:State)->Step {
let mut out=self.b846_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.display_failures(state.consumed.max(state.matched),&["Repeat"]);}
out
}
fn b846_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e847_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
}
let _=count;
if false {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e847_c(&mut self,state:State)->Step {
let mut out=self.b847_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.display_failures(state.consumed.max(state.matched),&["'|'"]);}
out
}
fn b847_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e848_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e849_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e848_c(&mut self,state:State)->Step {
let mut out=self.b848_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,848,94);
out.events=self.event(Event::Capture {site:177,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["'|'"]);}
out
}
fn b848_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"|",true,96,false,"|");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'|'");}
out
}
fn e849_c(&mut self,state:State)->Step {
let mut out=self.b849_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:178,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b849_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r95_c(out.state);
out
}
fn e850_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:850,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b850_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b850_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e851_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e852_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e851_c(&mut self,state:State)->Step {
let mut out=self.b851_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:179,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b851_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r96_c(out.state);
out
}
fn e852_c(&mut self,state:State)->Step {
let mut out=self.b852_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.display_failures(state.consumed.max(state.matched),&["Repeat"]);}
out
}
fn b852_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e853_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
}
let _=count;
if false {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e853_c(&mut self,state:State)->Step {
let mut out=self.b853_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.display_failures(state.consumed.max(state.matched),&["'&'"]);}
out
}
fn b853_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e854_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e855_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e854_c(&mut self,state:State)->Step {
let mut out=self.b854_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,854,95);
out.events=self.event(Event::Capture {site:180,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["'&'"]);}
out
}
fn b854_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"&",true,3,false,"&");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'&'");}
out
}
fn e855_c(&mut self,state:State)->Step {
let mut out=self.b855_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:181,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b855_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r96_c(out.state);
out
}
fn e856_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:856,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b856_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b856_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e857_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e858_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e857_c(&mut self,state:State)->Step {
let mut out=self.b857_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:182,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b857_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r100_c(out.state);
out
}
fn e858_c(&mut self,state:State)->Step {
let mut out=self.b858_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.display_failures(state.consumed.max(state.matched),&["Repeat"]);}
out
}
fn b858_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e859_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
}
let _=count;
if false {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e859_c(&mut self,state:State)->Step {
let mut out=self.b859_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.display_failures(state.consumed.max(state.matched),&["'^'"]);}
out
}
fn b859_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e860_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e861_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e860_c(&mut self,state:State)->Step {
let mut out=self.b860_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,860,96);
out.events=self.event(Event::Capture {site:183,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["'^'"]);}
out
}
fn b860_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"^",true,45,false,"^");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'^'");}
out
}
fn e861_c(&mut self,state:State)->Step {
let mut out=self.b861_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:184,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b861_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r100_c(out.state);
out
}
fn e862_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:862,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b862_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b862_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e863_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e864_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e865_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e866_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e863_c(&mut self,state:State)->Step {
let mut out=self.b863_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,863,97);
} else {self.display_failures(state.consumed.max(state.matched),&["'not'"]);}
out
}
fn b863_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"not",true,75,false,"not");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'not'");}
out
}
fn e864_c(&mut self,state:State)->Step {
let mut out=self.b864_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,864,97);
} else {self.display_failures(state.consumed.max(state.matched),&["'('"]);}
out
}
fn b864_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"(",true,4,false,"(");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'('");}
out
}
fn e865_c(&mut self,state:State)->Step {
let mut out=self.b865_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:185,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b865_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r94_c(out.state);
out
}
fn e866_c(&mut self,state:State)->Step {
let mut out=self.b866_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,866,97);
} else {self.display_failures(state.consumed.max(state.matched),&["')'"]);}
out
}
fn b866_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,")",true,5,false,")");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"')'");}
out
}
fn e867_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:867,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b867_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b867_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out='choice: {let mark=self.mark();let mut diagnostic=Diag::NONE;let mut best:Option<(Step,[usize;2])>=None;
let ct=self.input.cp_at(self.skip_0::<false>(out.state.begin()).position::<false>()).map(|(c,_)|c);
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,110))) {self.guard_e868_c(out.state.begin())} else {self.e868_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,105))) {self.guard_e869_c(out.state.begin())} else {self.e869_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,109))) {self.guard_e870_c(out.state.begin())} else {self.e870_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,101))) {self.guard_e871_c(out.state.begin())} else {self.e871_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e872_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,36|116))) {self.guard_e873_c(out.state.begin())} else {self.e873_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,36|116))) {self.guard_e874_c(out.state.begin())} else {self.e874_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,36|116))) {self.guard_e875_c(out.state.begin())} else {self.e875_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,115))) {self.guard_e876_c(out.state.begin())} else {self.e876_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,101))) {self.guard_e877_c(out.state.begin())} else {self.e877_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,99))) {self.guard_e878_c(out.state.begin())} else {self.e878_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,105))) {self.guard_e879_c(out.state.begin())} else {self.e879_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,105))) {self.guard_e880_c(out.state.begin())} else {self.e880_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,105))) {self.guard_e881_c(out.state.begin())} else {self.e881_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e882_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e883_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,36))) {self.guard_e884_c(out.state.begin())} else {self.e884_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,99|105))) {self.guard_e885_c(out.state.begin())} else {self.e885_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,40))) {self.guard_e886_c(out.state.begin())} else {self.e886_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);break 'choice if let Some((mut child,effects))=best {self.replay_effects(effects);child.diag=diagnostic;child} else {out.ok=false;out.diag=diagnostic;out};
};
out
}
fn e868_c(&mut self,state:State)->Step {
let mut out=self.b868_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b868_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r97_c(out.state);
out
}
fn e869_c(&mut self,state:State)->Step {
let mut out=self.b869_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b869_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r106_c(out.state);
out
}
fn e870_c(&mut self,state:State)->Step {
let mut out=self.b870_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b870_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r117_c(out.state);
out
}
fn e871_c(&mut self,state:State)->Step {
let mut out=self.b871_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b871_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r31_c(out.state);
out
}
fn e872_c(&mut self,state:State)->Step {
let mut out=self.b872_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b872_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r73_c(out.state);
out
}
fn e873_c(&mut self,state:State)->Step {
let mut out=self.b873_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b873_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r74_c(out.state);
out
}
fn e874_c(&mut self,state:State)->Step {
let mut out=self.b874_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b874_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r75_c(out.state);
out
}
fn e875_c(&mut self,state:State)->Step {
let mut out=self.b875_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b875_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r76_c(out.state);
out
}
fn e876_c(&mut self,state:State)->Step {
let mut out=self.b876_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b876_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r70_c(out.state);
out
}
fn e877_c(&mut self,state:State)->Step {
let mut out=self.b877_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b877_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r71_c(out.state);
out
}
fn e878_c(&mut self,state:State)->Step {
let mut out=self.b878_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b878_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r72_c(out.state);
out
}
fn e879_c(&mut self,state:State)->Step {
let mut out=self.b879_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b879_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r78_c(out.state);
out
}
fn e880_c(&mut self,state:State)->Step {
let mut out=self.b880_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b880_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r79_c(out.state);
out
}
fn e881_c(&mut self,state:State)->Step {
let mut out=self.b881_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b881_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r80_c(out.state);
out
}
fn e882_c(&mut self,state:State)->Step {
let mut out=self.b882_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,882,98);
} else {self.display_failures(state.consumed.max(state.matched),&["'true'"]);}
out
}
fn b882_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"true",true,92,false,"true");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'true'");}
out
}
fn e883_c(&mut self,state:State)->Step {
let mut out=self.b883_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,883,98);
} else {self.display_failures(state.consumed.max(state.matched),&["'false'"]);}
out
}
fn b883_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"false",true,60,false,"false");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'false'");}
out
}
fn e884_c(&mut self,state:State)->Step {
let mut out=self.b884_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b884_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r121_c(out.state);
out
}
fn e885_c(&mut self,state:State)->Step {
let mut out=self.b885_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b885_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r36_c(out.state);
out
}
fn e886_c(&mut self,state:State)->Step {
let mut out=self.b886_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.display_failures(state.consumed.max(state.matched),&["'('", "')'"]);}
out
}
fn b886_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e887_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e888_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e889_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e887_c(&mut self,state:State)->Step {
let mut out=self.b887_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,887,98);
} else {self.display_failures(state.consumed.max(state.matched),&["'('"]);}
out
}
fn b887_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"(",true,4,false,"(");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'('");}
out
}
fn e888_c(&mut self,state:State)->Step {
let mut out=self.b888_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b888_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r94_c(out.state);
out
}
fn e889_c(&mut self,state:State)->Step {
let mut out=self.b889_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,889,98);
} else {self.display_failures(state.consumed.max(state.matched),&["')'"]);}
out
}
fn b889_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,")",true,5,false,")");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"')'");}
out
}
fn e890_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:890,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b890_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b890_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e891_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e892_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e893_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e891_c(&mut self,state:State)->Step {
let mut out=self.b891_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:186,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["'false'", "'true'"]);}
out
}
fn b891_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r98_c(out.state);
out
}
fn e892_c(&mut self,state:State)->Step {
let mut out=self.b892_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:187,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["'!='", "'=='"]);}
out
}
fn b892_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r102_c(out.state);
out
}
fn e893_c(&mut self,state:State)->Step {
let mut out=self.b893_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:188,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["'false'", "'true'"]);}
out
}
fn b893_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r98_c(out.state);
out
}
fn e894_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:894,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b894_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b894_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out='choice: {let mark=self.mark();let mut diagnostic=Diag::NONE;let mut best:Option<(Step,[usize;2])>=None;
let ct=self.input.cp_at(self.skip_0::<false>(out.state.begin()).position::<false>()).map(|(c,_)|c);
self.restore(mark);let mut child=self.e895_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,36|40|43|45..=46|48..=57|97|99|101..=102|105|108..=109|112|114..=116))) {self.guard_e896_c(out.state.begin())} else {self.e896_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e897_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e898_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);break 'choice if let Some((mut child,effects))=best {self.replay_effects(effects);child.diag=diagnostic;child} else {out.ok=false;out.diag=diagnostic;out};
};
out
}
fn e895_c(&mut self,state:State)->Step {
let mut out=self.b895_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:189,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b895_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r99_c(out.state);
out
}
fn e896_c(&mut self,state:State)->Step {
let mut out=self.b896_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:190,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b896_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r103_c(out.state);
out
}
fn e897_c(&mut self,state:State)->Step {
let mut out=self.b897_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:191,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b897_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r101_c(out.state);
out
}
fn e898_c(&mut self,state:State)->Step {
let mut out=self.b898_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:192,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["'false'", "'true'"]);}
out
}
fn b898_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r98_c(out.state);
out
}
fn e899_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:899,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b899_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b899_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e900_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e901_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e902_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e900_c(&mut self,state:State)->Step {
let mut out=self.b900_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:193,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b900_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r89_c(out.state);
out
}
fn e901_c(&mut self,state:State)->Step {
let mut out=self.b901_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:194,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["'!='", "'=='"]);}
out
}
fn b901_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r102_c(out.state);
out
}
fn e902_c(&mut self,state:State)->Step {
let mut out=self.b902_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:195,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b902_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r89_c(out.state);
out
}
fn e903_c(&mut self,state:State)->Step {
let mut out=self.b903_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b903_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out='choice: {let mark=self.mark();let mut diagnostic=Diag::NONE;let mut best:Option<(Step,[usize;2])>=None;
self.restore(mark);let mut child=self.e904_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e905_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);break 'choice if let Some((mut child,effects))=best {self.replay_effects(effects);child.diag=diagnostic;child} else {out.ok=false;out.diag=diagnostic;out};
};
out
}
fn e904_c(&mut self,state:State)->Step {
let mut out=self.b904_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,904,102);
} else {self.display_failures(state.consumed.max(state.matched),&["'=='"]);}
out
}
fn b904_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"==",true,26,false,"==");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'=='");}
out
}
fn e905_c(&mut self,state:State)->Step {
let mut out=self.b905_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,905,102);
} else {self.display_failures(state.consumed.max(state.matched),&["'!='"]);}
out
}
fn b905_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"!=",true,0,false,"!=");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'!='");}
out
}
fn e906_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:906,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b906_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b906_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e907_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e908_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e909_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e907_c(&mut self,state:State)->Step {
let mut out=self.b907_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:196,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b907_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r40_c(out.state);
out
}
fn e908_c(&mut self,state:State)->Step {
let mut out=self.b908_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:197,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["'!='", "'<'", "'<='", "'=='", "'>'", "'>='"]);}
out
}
fn b908_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r104_c(out.state);
out
}
fn e909_c(&mut self,state:State)->Step {
let mut out=self.b909_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:198,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b909_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r40_c(out.state);
out
}
fn e910_c(&mut self,state:State)->Step {
let mut out=self.b910_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b910_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out='choice: {let mark=self.mark();let mut diagnostic=Diag::NONE;let mut best:Option<(Step,[usize;2])>=None;
self.restore(mark);let mut child=self.e911_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e912_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e913_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e914_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e915_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e916_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);break 'choice if let Some((mut child,effects))=best {self.replay_effects(effects);child.diag=diagnostic;child} else {out.ok=false;out.diag=diagnostic;out};
};
out
}
fn e911_c(&mut self,state:State)->Step {
let mut out=self.b911_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,911,104);
} else {self.display_failures(state.consumed.max(state.matched),&["'=='"]);}
out
}
fn b911_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"==",true,26,false,"==");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'=='");}
out
}
fn e912_c(&mut self,state:State)->Step {
let mut out=self.b912_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,912,104);
} else {self.display_failures(state.consumed.max(state.matched),&["'!='"]);}
out
}
fn b912_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"!=",true,0,false,"!=");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'!='");}
out
}
fn e913_c(&mut self,state:State)->Step {
let mut out=self.b913_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,913,104);
} else {self.display_failures(state.consumed.max(state.matched),&["'<='"]);}
out
}
fn b913_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"<=",true,24,false,"<=");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'<='");}
out
}
fn e914_c(&mut self,state:State)->Step {
let mut out=self.b914_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,914,104);
} else {self.display_failures(state.consumed.max(state.matched),&["'>='"]);}
out
}
fn b914_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,">=",true,28,false,">=");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'>='");}
out
}
fn e915_c(&mut self,state:State)->Step {
let mut out=self.b915_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,915,104);
} else {self.display_failures(state.consumed.max(state.matched),&["'<'"]);}
out
}
fn b915_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"<",true,23,false,"<");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'<'");}
out
}
fn e916_c(&mut self,state:State)->Step {
let mut out=self.b916_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,916,104);
} else {self.display_failures(state.consumed.max(state.matched),&["'>'"]);}
out
}
fn b916_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,">",true,27,false,">");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'>'");}
out
}
fn e917_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:917,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b917_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b917_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out='choice: {let mark=self.mark();let mut diagnostic=Diag::NONE;let mut best:Option<(Step,[usize;2])>=None;
let ct=self.input.cp_at(self.skip_0::<false>(out.state.begin()).position::<false>()).map(|(c,_)|c);
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,36|40|43|45..=46|48..=57|97|99|101..=102|105|108..=109|112|114..=116))) {self.guard_e918_c(out.state.begin())} else {self.e918_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e919_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e920_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,101))) {self.guard_e921_c(out.state.begin())} else {self.e921_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,36))) {self.guard_e922_c(out.state.begin())} else {self.e922_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,99|105))) {self.guard_e923_c(out.state.begin())} else {self.e923_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);break 'choice if let Some((mut child,effects))=best {self.replay_effects(effects);child.diag=diagnostic;child} else {out.ok=false;out.diag=diagnostic;out};
};
out
}
fn e918_c(&mut self,state:State)->Step {
let mut out=self.b918_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:199,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b918_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r40_c(out.state);
out
}
fn e919_c(&mut self,state:State)->Step {
let mut out=self.b919_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:200,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b919_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r89_c(out.state);
out
}
fn e920_c(&mut self,state:State)->Step {
let mut out=self.b920_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:201,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b920_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r94_c(out.state);
out
}
fn e921_c(&mut self,state:State)->Step {
let mut out=self.b921_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:202,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["'('", "')'", "'external'"]);}
out
}
fn b921_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r34_c(out.state);
out
}
fn e922_c(&mut self,state:State)->Step {
let mut out=self.b922_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:203,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["'$'"]);}
out
}
fn b922_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r121_c(out.state);
out
}
fn e923_c(&mut self,state:State)->Step {
let mut out=self.b923_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:204,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["'('", "')'"]);}
out
}
fn b923_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r36_c(out.state);
out
}
fn e924_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:924,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b924_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b924_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e925_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e926_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e927_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e928_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e929_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e930_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e931_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e932_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e933_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e934_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e935_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e925_c(&mut self,state:State)->Step {
let mut out=self.b925_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,925,106);
} else {self.display_failures(state.consumed.max(state.matched),&["'if'"]);}
out
}
fn b925_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"if",true,63,false,"if");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'if'");}
out
}
fn e926_c(&mut self,state:State)->Step {
let mut out=self.b926_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,926,106);
} else {self.display_failures(state.consumed.max(state.matched),&["'('"]);}
out
}
fn b926_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"(",true,4,false,"(");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'('");}
out
}
fn e927_c(&mut self,state:State)->Step {
let mut out=self.b927_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:205,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b927_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r94_c(out.state);
out
}
fn e928_c(&mut self,state:State)->Step {
let mut out=self.b928_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,928,106);
} else {self.display_failures(state.consumed.max(state.matched),&["')'"]);}
out
}
fn b928_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,")",true,5,false,")");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"')'");}
out
}
fn e929_c(&mut self,state:State)->Step {
let mut out=self.b929_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,929,106);
} else {self.display_failures(state.consumed.max(state.matched),&["'{'"]);}
out
}
fn b929_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"{",true,95,false,"{");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'{'");}
out
}
fn e930_c(&mut self,state:State)->Step {
let mut out=self.b930_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:206,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b930_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r107_c(out.state);
out
}
fn e931_c(&mut self,state:State)->Step {
let mut out=self.b931_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,931,106);
} else {self.display_failures(state.consumed.max(state.matched),&["'}'"]);}
out
}
fn b931_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"}",true,97,false,"}");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'}'");}
out
}
fn e932_c(&mut self,state:State)->Step {
let mut out=self.b932_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,932,106);
} else {self.display_failures(state.consumed.max(state.matched),&["'else'"]);}
out
}
fn b932_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"else",true,55,false,"else");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'else'");}
out
}
fn e933_c(&mut self,state:State)->Step {
let mut out=self.b933_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,933,106);
} else {self.display_failures(state.consumed.max(state.matched),&["'{'"]);}
out
}
fn b933_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"{",true,95,false,"{");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'{'");}
out
}
fn e934_c(&mut self,state:State)->Step {
let mut out=self.b934_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:207,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b934_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r107_c(out.state);
out
}
fn e935_c(&mut self,state:State)->Step {
let mut out=self.b935_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,935,106);
} else {self.display_failures(state.consumed.max(state.matched),&["'}'"]);}
out
}
fn b935_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"}",true,97,false,"}");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'}'");}
out
}
fn e936_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:936,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b936_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b936_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out='choice: {let mark=self.mark();let mut diagnostic=Diag::NONE;let mut best:Option<(Step,[usize;2])>=None;
let ct=self.input.cp_at(self.skip_0::<false>(out.state.begin()).position::<false>()).map(|(c,_)|c);
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,36|40|43|45..=46|48..=57|97|99|101..=102|105|108..=109|112|114..=116))) {self.guard_e937_c(out.state.begin())} else {self.e937_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e938_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e939_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,36|40|43|45..=46|48..=57|97|99|101..=102|105|108..=109|112|114..=116))) {self.guard_e940_c(out.state.begin())} else {self.e940_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e941_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e942_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e943_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,99|105))) {self.guard_e944_c(out.state.begin())} else {self.e944_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);break 'choice if let Some((mut child,effects))=best {self.replay_effects(effects);child.diag=diagnostic;child} else {out.ok=false;out.diag=diagnostic;out};
};
out
}
fn e937_c(&mut self,state:State)->Step {
let mut out=self.b937_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:208,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b937_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r103_c(out.state);
out
}
fn e938_c(&mut self,state:State)->Step {
let mut out=self.b938_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:209,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b938_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r101_c(out.state);
out
}
fn e939_c(&mut self,state:State)->Step {
let mut out=self.b939_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:210,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b939_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r99_c(out.state);
out
}
fn e940_c(&mut self,state:State)->Step {
let mut out=self.b940_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:211,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b940_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r40_c(out.state);
out
}
fn e941_c(&mut self,state:State)->Step {
let mut out=self.b941_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:212,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b941_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r94_c(out.state);
out
}
fn e942_c(&mut self,state:State)->Step {
let mut out=self.b942_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:213,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b942_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r89_c(out.state);
out
}
fn e943_c(&mut self,state:State)->Step {
let mut out=self.b943_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:214,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b943_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r105_c(out.state);
out
}
fn e944_c(&mut self,state:State)->Step {
let mut out=self.b944_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:215,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["'('", "')'"]);}
out
}
fn b944_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r36_c(out.state);
out
}
fn e945_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:945,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b945_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b945_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e946_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e947_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e948_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e949_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e950_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e951_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e952_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e946_c(&mut self,state:State)->Step {
let mut out=self.b946_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,946,108);
} else {self.display_failures(state.consumed.max(state.matched),&["'('"]);}
out
}
fn b946_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"(",true,4,false,"(");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'('");}
out
}
fn e947_c(&mut self,state:State)->Step {
let mut out=self.b947_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:216,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b947_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r94_c(out.state);
out
}
fn e948_c(&mut self,state:State)->Step {
let mut out=self.b948_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,948,108);
} else {self.display_failures(state.consumed.max(state.matched),&["'?'"]);}
out
}
fn b948_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"?",true,29,false,"?");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'?'");}
out
}
fn e949_c(&mut self,state:State)->Step {
let mut out=self.b949_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:217,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b949_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r107_c(out.state);
out
}
fn e950_c(&mut self,state:State)->Step {
let mut out=self.b950_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,950,108);
} else {self.display_failures(state.consumed.max(state.matched),&["':'"]);}
out
}
fn b950_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,":",true,21,false,":");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"':'");}
out
}
fn e951_c(&mut self,state:State)->Step {
let mut out=self.b951_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:218,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b951_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r107_c(out.state);
out
}
fn e952_c(&mut self,state:State)->Step {
let mut out=self.b952_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,952,108);
} else {self.display_failures(state.consumed.max(state.matched),&["')'"]);}
out
}
fn b952_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,")",true,5,false,")");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"')'");}
out
}
fn e953_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:953,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b953_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b953_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e954_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e955_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e956_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e957_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e961_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e962_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e963_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e954_c(&mut self,state:State)->Step {
let mut out=self.b954_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,954,109);
} else {self.display_failures(state.consumed.max(state.matched),&["'match'"]);}
out
}
fn b954_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"match",true,72,false,"match");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'match'");}
out
}
fn e955_c(&mut self,state:State)->Step {
let mut out=self.b955_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,955,109);
} else {self.display_failures(state.consumed.max(state.matched),&["'{'"]);}
out
}
fn b955_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"{",true,95,false,"{");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'{'");}
out
}
fn e956_c(&mut self,state:State)->Step {
let mut out=self.b956_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:219,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["'->'"]);}
out
}
fn b956_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r110_c(out.state);
out
}
fn e957_c(&mut self,state:State)->Step {
let mut out=self.b957_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.display_failures(state.consumed.max(state.matched),&["','"]);}
out
}
fn b957_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e958_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
}
let _=count;
if false {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e958_c(&mut self,state:State)->Step {
let mut out=self.b958_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.display_failures(state.consumed.max(state.matched),&["','"]);}
out
}
fn b958_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e959_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e960_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e959_c(&mut self,state:State)->Step {
let mut out=self.b959_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,959,109);
} else {self.display_failures(state.consumed.max(state.matched),&["','"]);}
out
}
fn b959_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,",",true,8,false,",");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"','");}
out
}
fn e960_c(&mut self,state:State)->Step {
let mut out=self.b960_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:220,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["'->'"]);}
out
}
fn b960_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r110_c(out.state);
out
}
fn e961_c(&mut self,state:State)->Step {
let mut out=self.b961_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,961,109);
} else {self.display_failures(state.consumed.max(state.matched),&["','"]);}
out
}
fn b961_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,",",true,8,false,",");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"','");}
out
}
fn e962_c(&mut self,state:State)->Step {
let mut out=self.b962_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:221,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["'->'", "'default'"]);}
out
}
fn b962_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r111_c(out.state);
out
}
fn e963_c(&mut self,state:State)->Step {
let mut out=self.b963_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,963,109);
} else {self.display_failures(state.consumed.max(state.matched),&["'}'"]);}
out
}
fn b963_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"}",true,97,false,"}");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'}'");}
out
}
fn e964_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:964,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b964_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b964_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e965_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e966_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e967_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e965_c(&mut self,state:State)->Step {
let mut out=self.b965_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:222,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b965_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r94_c(out.state);
out
}
fn e966_c(&mut self,state:State)->Step {
let mut out=self.b966_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,966,110);
} else {self.display_failures(state.consumed.max(state.matched),&["'->'"]);}
out
}
fn b966_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"->",true,10,false,"->");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'->'");}
out
}
fn e967_c(&mut self,state:State)->Step {
let mut out=self.b967_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:223,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b967_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r112_c(out.state);
out
}
fn e968_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:968,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b968_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b968_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e969_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e970_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e971_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e969_c(&mut self,state:State)->Step {
let mut out=self.b969_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,969,111);
} else {self.display_failures(state.consumed.max(state.matched),&["'default'"]);}
out
}
fn b969_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"default",true,53,false,"default");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'default'");}
out
}
fn e970_c(&mut self,state:State)->Step {
let mut out=self.b970_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,970,111);
} else {self.display_failures(state.consumed.max(state.matched),&["'->'"]);}
out
}
fn b970_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"->",true,10,false,"->");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'->'");}
out
}
fn e971_c(&mut self,state:State)->Step {
let mut out=self.b971_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:224,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b971_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r112_c(out.state);
out
}
fn e972_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:972,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b972_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b972_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e973_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e973_c(&mut self,state:State)->Step {
let mut out=self.b973_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:225,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b973_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r40_c(out.state);
out
}
fn e974_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:974,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b974_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b974_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e975_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e976_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e977_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e978_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e982_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e983_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e984_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e975_c(&mut self,state:State)->Step {
let mut out=self.b975_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,975,113);
} else {self.display_failures(state.consumed.max(state.matched),&["'match'"]);}
out
}
fn b975_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"match",true,72,false,"match");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'match'");}
out
}
fn e976_c(&mut self,state:State)->Step {
let mut out=self.b976_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,976,113);
} else {self.display_failures(state.consumed.max(state.matched),&["'{'"]);}
out
}
fn b976_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"{",true,95,false,"{");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'{'");}
out
}
fn e977_c(&mut self,state:State)->Step {
let mut out=self.b977_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:226,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["'->'"]);}
out
}
fn b977_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r114_c(out.state);
out
}
fn e978_c(&mut self,state:State)->Step {
let mut out=self.b978_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.display_failures(state.consumed.max(state.matched),&["','"]);}
out
}
fn b978_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e979_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
}
let _=count;
if false {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e979_c(&mut self,state:State)->Step {
let mut out=self.b979_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.display_failures(state.consumed.max(state.matched),&["','"]);}
out
}
fn b979_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e980_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e981_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e980_c(&mut self,state:State)->Step {
let mut out=self.b980_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,980,113);
} else {self.display_failures(state.consumed.max(state.matched),&["','"]);}
out
}
fn b980_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,",",true,8,false,",");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"','");}
out
}
fn e981_c(&mut self,state:State)->Step {
let mut out=self.b981_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:227,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["'->'"]);}
out
}
fn b981_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r114_c(out.state);
out
}
fn e982_c(&mut self,state:State)->Step {
let mut out=self.b982_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,982,113);
} else {self.display_failures(state.consumed.max(state.matched),&["','"]);}
out
}
fn b982_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,",",true,8,false,",");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"','");}
out
}
fn e983_c(&mut self,state:State)->Step {
let mut out=self.b983_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:228,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["'->'", "'default'"]);}
out
}
fn b983_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r115_c(out.state);
out
}
fn e984_c(&mut self,state:State)->Step {
let mut out=self.b984_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,984,113);
} else {self.display_failures(state.consumed.max(state.matched),&["'}'"]);}
out
}
fn b984_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"}",true,97,false,"}");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'}'");}
out
}
fn e985_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:985,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b985_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b985_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e986_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e987_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e988_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e986_c(&mut self,state:State)->Step {
let mut out=self.b986_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:229,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b986_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r94_c(out.state);
out
}
fn e987_c(&mut self,state:State)->Step {
let mut out=self.b987_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,987,114);
} else {self.display_failures(state.consumed.max(state.matched),&["'->'"]);}
out
}
fn b987_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"->",true,10,false,"->");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'->'");}
out
}
fn e988_c(&mut self,state:State)->Step {
let mut out=self.b988_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:230,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b988_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r116_c(out.state);
out
}
fn e989_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:989,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b989_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b989_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e990_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e991_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e992_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e990_c(&mut self,state:State)->Step {
let mut out=self.b990_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,990,115);
} else {self.display_failures(state.consumed.max(state.matched),&["'default'"]);}
out
}
fn b990_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"default",true,53,false,"default");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'default'");}
out
}
fn e991_c(&mut self,state:State)->Step {
let mut out=self.b991_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,991,115);
} else {self.display_failures(state.consumed.max(state.matched),&["'->'"]);}
out
}
fn b991_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"->",true,10,false,"->");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'->'");}
out
}
fn e992_c(&mut self,state:State)->Step {
let mut out=self.b992_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:231,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b992_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r116_c(out.state);
out
}
fn e993_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:993,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b993_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b993_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e994_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e994_c(&mut self,state:State)->Step {
let mut out=self.b994_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:232,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b994_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r89_c(out.state);
out
}
fn e995_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:995,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b995_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b995_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e996_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e997_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e998_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e999_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e1003_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e1004_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e1005_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e996_c(&mut self,state:State)->Step {
let mut out=self.b996_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,996,117);
} else {self.display_failures(state.consumed.max(state.matched),&["'match'"]);}
out
}
fn b996_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"match",true,72,false,"match");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'match'");}
out
}
fn e997_c(&mut self,state:State)->Step {
let mut out=self.b997_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,997,117);
} else {self.display_failures(state.consumed.max(state.matched),&["'{'"]);}
out
}
fn b997_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"{",true,95,false,"{");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'{'");}
out
}
fn e998_c(&mut self,state:State)->Step {
let mut out=self.b998_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:233,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["'->'"]);}
out
}
fn b998_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r118_c(out.state);
out
}
fn e999_c(&mut self,state:State)->Step {
let mut out=self.b999_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.display_failures(state.consumed.max(state.matched),&["','"]);}
out
}
fn b999_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e1000_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
}
let _=count;
if false {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e1000_c(&mut self,state:State)->Step {
let mut out=self.b1000_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.display_failures(state.consumed.max(state.matched),&["','"]);}
out
}
fn b1000_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e1001_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e1002_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e1001_c(&mut self,state:State)->Step {
let mut out=self.b1001_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,1001,117);
} else {self.display_failures(state.consumed.max(state.matched),&["','"]);}
out
}
fn b1001_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,",",true,8,false,",");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"','");}
out
}
fn e1002_c(&mut self,state:State)->Step {
let mut out=self.b1002_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:234,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["'->'"]);}
out
}
fn b1002_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r118_c(out.state);
out
}
fn e1003_c(&mut self,state:State)->Step {
let mut out=self.b1003_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,1003,117);
} else {self.display_failures(state.consumed.max(state.matched),&["','"]);}
out
}
fn b1003_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,",",true,8,false,",");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"','");}
out
}
fn e1004_c(&mut self,state:State)->Step {
let mut out=self.b1004_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:235,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["'->'", "'default'"]);}
out
}
fn b1004_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r119_c(out.state);
out
}
fn e1005_c(&mut self,state:State)->Step {
let mut out=self.b1005_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,1005,117);
} else {self.display_failures(state.consumed.max(state.matched),&["'}'"]);}
out
}
fn b1005_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"}",true,97,false,"}");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'}'");}
out
}
fn e1006_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:1006,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b1006_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b1006_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e1007_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e1008_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e1009_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e1007_c(&mut self,state:State)->Step {
let mut out=self.b1007_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:236,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b1007_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r94_c(out.state);
out
}
fn e1008_c(&mut self,state:State)->Step {
let mut out=self.b1008_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,1008,118);
} else {self.display_failures(state.consumed.max(state.matched),&["'->'"]);}
out
}
fn b1008_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"->",true,10,false,"->");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'->'");}
out
}
fn e1009_c(&mut self,state:State)->Step {
let mut out=self.b1009_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:237,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b1009_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r120_c(out.state);
out
}
fn e1010_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:1010,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b1010_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b1010_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e1011_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e1012_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e1013_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e1011_c(&mut self,state:State)->Step {
let mut out=self.b1011_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,1011,119);
} else {self.display_failures(state.consumed.max(state.matched),&["'default'"]);}
out
}
fn b1011_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"default",true,53,false,"default");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'default'");}
out
}
fn e1012_c(&mut self,state:State)->Step {
let mut out=self.b1012_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,1012,119);
} else {self.display_failures(state.consumed.max(state.matched),&["'->'"]);}
out
}
fn b1012_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"->",true,10,false,"->");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'->'");}
out
}
fn e1013_c(&mut self,state:State)->Step {
let mut out=self.b1013_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:238,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b1013_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r120_c(out.state);
out
}
fn e1014_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:1014,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b1014_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b1014_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e1015_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e1015_c(&mut self,state:State)->Step {
let mut out=self.b1015_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:239,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b1015_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r94_c(out.state);
out
}
fn e1016_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:1016,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b1016_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b1016_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e1017_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e1018_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e1019_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e1017_c(&mut self,state:State)->Step {
let mut out=self.b1017_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,1017,121);
} else {self.display_failures(state.consumed.max(state.matched),&["'$'"]);}
out
}
fn b1017_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"$",true,2,false,"$");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'$'");}
out
}
fn e1018_c(&mut self,state:State)->Step {
let mut out=self.b1018_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,1018,121);
out.events=self.event(Event::Capture {site:240,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["IdentifierParser", "__CaptureSite"]);}
out
}
fn b1018_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.token_1::<false>(out.state,"IDENTIFIER","IdentifierParser");
out
}
fn e1019_c(&mut self,state:State)->Step {
let mut out=self.b1019_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["Optional"]);}
out
}
fn b1019_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e1020_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
if count>=1 {break;}
}
let _=count;
if count>1 {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e1020_c(&mut self,state:State)->Step {
let mut out=self.b1020_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'as'"]);}
out
}
fn b1020_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e1021_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e1023_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e1021_c(&mut self,state:State)->Step {
let mut out=self.b1021_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'as'"]);}
out
}
fn b1021_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e1022_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
if count>=1 {break;}
}
let _=count;
if count>1 {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e1022_c(&mut self,state:State)->Step {
let mut out=self.b1022_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,1022,121);
} else {self.display_failures(state.consumed.max(state.matched),&["'as'"]);}
out
}
fn b1022_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"as",true,47,false,"as");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'as'");}
out
}
fn e1023_c(&mut self,state:State)->Step {
let mut out=self.b1023_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:241,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["'Boolean'", "'Float'", "'Number'", "'Object'", "'String'", "'boolean'", "'float'", "'number'", "'object'", "'string'"]);}
out
}
fn b1023_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r122_c(out.state);
out
}
fn e1024_c(&mut self,state:State)->Step {
let mut out=self.b1024_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b1024_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out='choice: {let mark=self.mark();let mut diagnostic=Diag::NONE;let mut best:Option<(Step,[usize;2])>=None;
self.restore(mark);let mut child=self.e1025_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e1026_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e1027_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e1028_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e1029_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e1030_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e1031_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e1032_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e1033_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e1034_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);break 'choice if let Some((mut child,effects))=best {self.replay_effects(effects);child.diag=diagnostic;child} else {out.ok=false;out.diag=diagnostic;out};
};
out
}
fn e1025_c(&mut self,state:State)->Step {
let mut out=self.b1025_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,1025,122);
} else {self.display_failures(state.consumed.max(state.matched),&["'number'"]);}
out
}
fn b1025_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"number",true,76,false,"number");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'number'");}
out
}
fn e1026_c(&mut self,state:State)->Step {
let mut out=self.b1026_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,1026,122);
} else {self.display_failures(state.consumed.max(state.matched),&["'Number'"]);}
out
}
fn b1026_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"Number",true,35,false,"Number");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'Number'");}
out
}
fn e1027_c(&mut self,state:State)->Step {
let mut out=self.b1027_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,1027,122);
} else {self.display_failures(state.consumed.max(state.matched),&["'float'"]);}
out
}
fn b1027_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"float",true,61,false,"float");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'float'");}
out
}
fn e1028_c(&mut self,state:State)->Step {
let mut out=self.b1028_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,1028,122);
} else {self.display_failures(state.consumed.max(state.matched),&["'Float'"]);}
out
}
fn b1028_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"Float",true,33,false,"Float");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'Float'");}
out
}
fn e1029_c(&mut self,state:State)->Step {
let mut out=self.b1029_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,1029,122);
} else {self.display_failures(state.consumed.max(state.matched),&["'string'"]);}
out
}
fn b1029_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"string",true,86,false,"string");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'string'");}
out
}
fn e1030_c(&mut self,state:State)->Step {
let mut out=self.b1030_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,1030,122);
} else {self.display_failures(state.consumed.max(state.matched),&["'String'"]);}
out
}
fn b1030_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"String",true,39,false,"String");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'String'");}
out
}
fn e1031_c(&mut self,state:State)->Step {
let mut out=self.b1031_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,1031,122);
} else {self.display_failures(state.consumed.max(state.matched),&["'boolean'"]);}
out
}
fn b1031_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"boolean",true,48,false,"boolean");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'boolean'");}
out
}
fn e1032_c(&mut self,state:State)->Step {
let mut out=self.b1032_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,1032,122);
} else {self.display_failures(state.consumed.max(state.matched),&["'Boolean'"]);}
out
}
fn b1032_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"Boolean",true,31,false,"Boolean");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'Boolean'");}
out
}
fn e1033_c(&mut self,state:State)->Step {
let mut out=self.b1033_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,1033,122);
} else {self.display_failures(state.consumed.max(state.matched),&["'object'"]);}
out
}
fn b1033_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"object",true,77,false,"object");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'object'");}
out
}
fn e1034_c(&mut self,state:State)->Step {
let mut out=self.b1034_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,1034,122);
} else {self.display_failures(state.consumed.max(state.matched),&["'Object'"]);}
out
}
fn b1034_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"Object",true,36,false,"Object");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'Object'");}
out
}
fn e1035_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:1035,state,matched_mode:false,version:self.scope.state_version()};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b1035_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b1035_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out='choice: {let mark=self.mark();let mut diagnostic=Diag::NONE;let mut best:Option<(Step,[usize;2])>=None;
let ct=self.input.cp_at(self.skip_0::<false>(out.state.begin()).position::<false>()).map(|(c,_)|c);
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,36|40|43|45..=46|48..=57|97|99|101..=102|105|108..=109|112|114..=116))) {self.guard_e1036_c(out.state.begin())} else {self.e1036_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e1037_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e1038_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e1039_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,99|105))) {self.guard_e1040_c(out.state.begin())} else {self.e1040_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(ct.is_some_and(|c| matches!(c as u32,40))) {self.guard_e1041_c(out.state.begin())} else {self.e1041_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);break 'choice if let Some((mut child,effects))=best {self.replay_effects(effects);child.diag=diagnostic;child} else {out.ok=false;out.diag=diagnostic;out};
};
out
}
fn e1036_c(&mut self,state:State)->Step {
let mut out=self.b1036_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:242,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b1036_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r40_c(out.state);
out
}
fn e1037_c(&mut self,state:State)->Step {
let mut out=self.b1037_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:243,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b1037_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r94_c(out.state);
out
}
fn e1038_c(&mut self,state:State)->Step {
let mut out=self.b1038_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:244,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b1038_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r89_c(out.state);
out
}
fn e1039_c(&mut self,state:State)->Step {
let mut out=self.b1039_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:245,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b1039_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r105_c(out.state);
out
}
fn e1040_c(&mut self,state:State)->Step {
let mut out=self.b1040_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:246,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["'('", "')'"]);}
out
}
fn b1040_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r36_c(out.state);
out
}
fn e1041_c(&mut self,state:State)->Step {
let mut out=self.b1041_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.display_failures(state.consumed.max(state.matched),&["'('", "')'"]);}
out
}
fn b1041_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e1042_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e1043_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
let child=self.e1044_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let trivia=self.trivia_1::<false>(out.state);out=self.combine(out,trivia);
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e1042_c(&mut self,state:State)->Step {
let mut out=self.b1042_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,1042,123);
} else {self.display_failures(state.consumed.max(state.matched),&["'('"]);}
out
}
fn b1042_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"(",true,4,false,"(");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'('");}
out
}
fn e1043_c(&mut self,state:State)->Step {
let mut out=self.b1043_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:247,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b1043_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r123_c(out.state);
out
}
fn e1044_c(&mut self,state:State)->Step {
let mut out=self.b1044_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,1044,123);
} else {self.display_failures(state.consumed.max(state.matched),&["')'"]);}
out
}
fn b1044_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,")",true,5,false,")");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"')'");}
out
}
fn guard_e36_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e36_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let mut d2=Diag::NONE;
let d3=self.diag_fail(t1.begin().position::<false>(),"variable");
d2=self.diag_join(d2,d3);
let d4=self.diag_fail(t1.begin().position::<false>(),"var");
d2=self.diag_join(d2,d4);
let d5=self.diag_rule(5,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'var'", "'variable'", "'var'", "'variable'"]);
if t1.begin().consumed.max(t1.begin().matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'variable'", "'variable'", "'var'", "'var'"]);}
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'variable'", "'var'", "'$'", "';'"]);}
Step {ok:false,state,events:EventId(0),diag:d5}
}
fn guard_e37_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e37_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let mut d2=Diag::NONE;
let d3=self.diag_fail(t1.begin().position::<false>(),"variable");
d2=self.diag_join(d2,d3);
let d4=self.diag_fail(t1.begin().position::<false>(),"var");
d2=self.diag_join(d2,d4);
let d5=self.diag_rule(6,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'var'", "'variable'", "'var'", "'variable'"]);
if t1.begin().consumed.max(t1.begin().matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'variable'", "'variable'", "'var'", "'var'"]);}
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'variable'", "'var'", "'$'", "';'"]);}
Step {ok:false,state,events:EventId(0),diag:d5}
}
fn guard_e38_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e38_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let mut d2=Diag::NONE;
let d3=self.diag_fail(t1.begin().position::<false>(),"variable");
d2=self.diag_join(d2,d3);
let d4=self.diag_fail(t1.begin().position::<false>(),"var");
d2=self.diag_join(d2,d4);
let d5=self.diag_rule(7,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'var'", "'variable'", "'var'", "'variable'"]);
if t1.begin().consumed.max(t1.begin().matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'variable'", "'variable'", "'var'", "'var'"]);}
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'variable'", "'var'", "'$'", "';'"]);}
Step {ok:false,state,events:EventId(0),diag:d5}
}
fn guard_e39_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e39_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let mut d2=Diag::NONE;
let d3=self.diag_fail(t1.begin().position::<false>(),"variable");
d2=self.diag_join(d2,d3);
let d4=self.diag_fail(t1.begin().position::<false>(),"var");
d2=self.diag_join(d2,d4);
let d5=self.diag_rule(8,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'var'", "'variable'", "'var'", "'variable'"]);
if t1.begin().consumed.max(t1.begin().matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'variable'", "'variable'", "'var'", "'var'"]);}
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'variable'", "'var'", "'$'", "';'"]);}
Step {ok:false,state,events:EventId(0),diag:d5}
}
fn guard_e248_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e248_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let mut d1=Diag::NONE;
let d2=self.diag_fail(state.begin().position::<false>(),"number");
d1=self.diag_join(d1,d2);
let d3=self.diag_fail(state.begin().position::<false>(),"float");
d1=self.diag_join(d1,d3);
self.display_failures(state.begin().consumed.max(state.begin().matched),&["'number'", "'number'", "'float'", "'float'"]);
let d4=self.diag_rule(26,d1);
self.display_failures(state.consumed.max(state.matched),&["'number'", "'float'"]);
Step {ok:false,state,events:EventId(0),diag:d4}
}
fn guard_e249_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e249_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"string");
let d3=self.diag_rule(27,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'string'", "'string'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'string'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e250_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e250_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"boolean");
let d3=self.diag_rule(28,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'boolean'", "'boolean'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'boolean'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e251_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e251_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"object");
let d3=self.diag_rule(29,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'object'", "'object'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'object'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e264_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e264_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.e30_c(t1).diag;
let d3=self.diag_rule(3,d2);
self.display_failures(t1.consumed.max(t1.matched),&["ClassNameParser", "__CaptureSite"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'#'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e290_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e290_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.e30_c(t1).diag;
let d3=self.diag_rule(3,d2);
self.display_failures(t1.consumed.max(t1.matched),&["ClassNameParser", "__CaptureSite"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'#'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e311_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e311_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.e30_c(t1).diag;
let d3=self.diag_rule(3,d2);
self.display_failures(t1.consumed.max(t1.matched),&["ClassNameParser", "__CaptureSite"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'#'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e332_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e332_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.e30_c(t1).diag;
let d3=self.diag_rule(3,d2);
self.display_failures(t1.consumed.max(t1.matched),&["ClassNameParser", "__CaptureSite"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'#'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e342_c(&mut self,state:State)->Step {
if !self.can_replay(0) {return self.e342_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"call");
self.display_failures(t1.consumed.max(t1.matched),&["'call'", "'call'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'call'", "'internal'"]);}
Step {ok:false,state,events:EventId(0),diag:d2}
}
fn guard_e362_c(&mut self,state:State)->Step {
if !self.can_replay(6) {return self.e362_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let mut d2=Diag::NONE;
let d3=self.diag_fail(t1.position::<false>(),"(");
let d4=self.diag_rule(108,d3);
d2=self.diag_join(d2,d4);
let d5=self.diag_fail(t1.position::<false>(),"match");
let d6=self.diag_rule(109,d5);
d2=self.diag_join(d2,d6);
let d7=self.diag_fail(t1.position::<false>(),"if");
let d8=self.diag_rule(106,d7);
d2=self.diag_join(d2,d8);
let mut d9=Diag::NONE;
let d10=self.diag_fail(t1.position::<false>(),"sin");
let d11=self.diag_rule(45,d10);
d9=self.diag_join(d9,d11);
let d12=self.diag_fail(t1.position::<false>(),"cos");
let d13=self.diag_rule(46,d12);
d9=self.diag_join(d9,d13);
let d14=self.diag_fail(t1.position::<false>(),"tan");
let d15=self.diag_rule(47,d14);
d9=self.diag_join(d9,d15);
let d16=self.diag_fail(t1.position::<false>(),"sqrt");
let d17=self.diag_rule(48,d16);
d9=self.diag_join(d9,d17);
let d18=self.diag_fail(t1.position::<false>(),"min");
let d19=self.diag_rule(49,d18);
d9=self.diag_join(d9,d19);
let d20=self.diag_fail(t1.position::<false>(),"max");
let d21=self.diag_rule(50,d20);
d9=self.diag_join(d9,d21);
let d22=self.diag_fail(t1.position::<false>(),"random");
let d23=self.diag_rule(51,d22);
d9=self.diag_join(d9,d23);
let d24=self.diag_fail(t1.position::<false>(),"abs");
let d25=self.diag_rule(52,d24);
d9=self.diag_join(d9,d25);
let d26=self.diag_fail(t1.position::<false>(),"round");
let d27=self.diag_rule(53,d26);
d9=self.diag_join(d9,d27);
let d28=self.diag_fail(t1.position::<false>(),"ceil");
let d29=self.diag_rule(54,d28);
d9=self.diag_join(d9,d29);
let d30=self.diag_fail(t1.position::<false>(),"floor");
let d31=self.diag_rule(55,d30);
d9=self.diag_join(d9,d31);
let d32=self.diag_fail(t1.position::<false>(),"pow");
let d33=self.diag_rule(56,d32);
d9=self.diag_join(d9,d33);
let d34=self.diag_fail(t1.position::<false>(),"log");
let d35=self.diag_rule(57,d34);
d9=self.diag_join(d9,d35);
let d36=self.diag_fail(t1.position::<false>(),"exp");
let d37=self.diag_rule(58,d36);
d9=self.diag_join(d9,d37);
let d38=self.diag_rule(44,d9);
d2=self.diag_join(d2,d38);
let d39=self.diag_fail(t1.position::<false>(),"toNum");
let d40=self.diag_rule(59,d39);
d2=self.diag_join(d2,d40);
let d41=self.diag_fail(t1.position::<false>(),"$");
let d42=self.diag_rule(121,d41);
let d43=self.diag_rule(69,d42);
d2=self.diag_join(d2,d43);
let d44=self.diag_fail(t1.position::<false>(),"len");
let d45=self.diag_rule(65,d44);
d2=self.diag_join(d2,d45);
let d46=self.diag_fail(t1.position::<false>(),"length");
let d47=self.diag_rule(64,d46);
d2=self.diag_join(d2,d47);
let d48=self.diag_fail(t1.position::<false>(),"external");
let d49=self.diag_rule(32,d48);
d2=self.diag_join(d2,d49);
let d50=self.e500_c(t1.begin()).diag;
d2=self.diag_join(d2,d50);
let d51=self.diag_fail(t1.position::<false>(),"$");
let d52=self.diag_rule(121,d51);
d2=self.diag_join(d2,d52);
let mut d53=Diag::NONE;
let d54=self.diag_fail(t1.position::<false>(),"call");
d53=self.diag_join(d53,d54);
let d55=self.diag_fail(t1.begin().position::<false>(),"internal");
d53=self.diag_join(d53,d55);
let d56=self.diag_rule(35,d53);
let d57=self.diag_rule(36,d56);
d2=self.diag_join(d2,d57);
let d58=self.diag_fail(t1.position::<false>(),"(");
d2=self.diag_join(d2,d58);
let d59=self.diag_rule(60,d2);
let d60=self.diag_rule(41,d59);
let d61=self.diag_rule(40,d60);
let d62=self.diag_rule(103,d61);
self.display_failures(t1.consumed.max(t1.matched),&["'('", "'('", "'match'", "'match'", "'if'", "'if'", "'sin'", "'sin'", "'cos'", "'cos'", "'tan'", "'tan'", "'sqrt'", "'sqrt'", "'min'", "'min'", "'max'", "'max'", "'random'", "'random'", "'abs'", "'abs'", "'round'", "'round'", "'ceil'", "'ceil'", "'floor'", "'floor'", "'pow'", "'pow'", "'log'", "'log'", "'exp'", "'exp'", "'toNum'", "'toNum'", "'$'", "'$'", "'$'", "'$'", "'len'", "'len'", "'length'", "'length'", "'external'", "'external'", "'$'", "'$'", "'call'", "'call'", "'call'", "'internal'", "'('", "'('", "'('", "'?'", "':'", "')'", "'match'", "'{'", "','", "'}'", "'if'", "'else'", "'toNum'", "'.length'", "'len'", "'length'", "'external'", "'$'", "__CaptureSite", "NumberTermParser", "__CaptureSite", "NumberExpressionParser", "__CaptureSite"]);
if t1.begin().consumed.max(t1.begin().matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'('", "'?'", "':'", "')'", "'match'", "'{'", "','", "'}'", "'if'", "'('", "')'", "'{'", "'}'", "'else'", "'sin'", "'('", "')'", "'cos'", "'tan'", "'sqrt'", "'min'", "'max'", "'random'", "'abs'", "'round'", "'ceil'", "'floor'", "'pow'", "','", "'log'", "'exp'", "'toNum'", "'('", "','", "')'", "'.length'", "'('", "')'", "'len'", "'('", "')'", "'length'", "'('", "')'", "'external'", "'('", "')'", "'$'", "'call'", "'internal'", "'internal'", "'internal'", "'internal'", "'('", "')'", "'('", "')'"]);}
if t1.begin().begin().consumed.max(t1.begin().begin().matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'sin'", "'('", "')'", "'cos'", "'('", "')'", "'tan'", "'('", "')'", "'sqrt'", "'('", "')'", "'min'", "'('", "')'", "'max'", "'('", "')'", "'random'", "'('", "')'", "'abs'", "'('", "')'", "'round'", "'('", "')'", "'ceil'", "'('", "')'", "'floor'", "'('", "')'", "'pow'", "'('", "','", "')'", "'log'", "'('", "')'", "'exp'", "'('", "')'"]);}
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["ComparisonExpressionParser", "__CaptureSite"]);}
Step {ok:false,state,events:EventId(0),diag:d62}
}
fn guard_e390_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e390_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"sin");
let d3=self.diag_rule(45,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'sin'", "'sin'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'sin'", "'('", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e391_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e391_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"cos");
let d3=self.diag_rule(46,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'cos'", "'cos'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'cos'", "'('", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e392_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e392_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"tan");
let d3=self.diag_rule(47,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'tan'", "'tan'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'tan'", "'('", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e393_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e393_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"sqrt");
let d3=self.diag_rule(48,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'sqrt'", "'sqrt'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'sqrt'", "'('", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e394_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e394_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"min");
let d3=self.diag_rule(49,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'min'", "'min'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'min'", "'('", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e395_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e395_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"max");
let d3=self.diag_rule(50,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'max'", "'max'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'max'", "'('", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e396_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e396_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"random");
let d3=self.diag_rule(51,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'random'", "'random'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'random'", "'('", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e397_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e397_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"abs");
let d3=self.diag_rule(52,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'abs'", "'abs'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'abs'", "'('", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e398_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e398_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"round");
let d3=self.diag_rule(53,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'round'", "'round'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'round'", "'('", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e399_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e399_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"ceil");
let d3=self.diag_rule(54,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'ceil'", "'ceil'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'ceil'", "'('", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e400_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e400_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"floor");
let d3=self.diag_rule(55,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'floor'", "'floor'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'floor'", "'('", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e401_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e401_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"pow");
let d3=self.diag_rule(56,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'pow'", "'pow'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'pow'", "'('", "','", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e402_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e402_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"log");
let d3=self.diag_rule(57,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'log'", "'log'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'log'", "'('", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e403_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e403_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"exp");
let d3=self.diag_rule(58,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'exp'", "'exp'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'exp'", "'('", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e491_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e491_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"(");
let d3=self.diag_rule(108,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'('", "'('"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'('", "'?'", "':'", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e492_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e492_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"match");
let d3=self.diag_rule(109,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'match'", "'match'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'match'", "'{'", "','", "'}'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e493_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e493_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"if");
let d3=self.diag_rule(106,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'if'", "'if'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'if'", "'('", "')'", "'{'", "'}'", "'else'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e494_c(&mut self,state:State)->Step {
if !self.can_replay(2) {return self.e494_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let mut d1=Diag::NONE;
let t2=self.trivia_1::<false>(state.begin().entered(true,true)).state;
let d3=self.diag_fail(t2.position::<false>(),"sin");
let d4=self.diag_rule(45,d3);
d1=self.diag_join(d1,d4);
let d5=self.diag_fail(t2.position::<false>(),"cos");
let d6=self.diag_rule(46,d5);
d1=self.diag_join(d1,d6);
let d7=self.diag_fail(t2.position::<false>(),"tan");
let d8=self.diag_rule(47,d7);
d1=self.diag_join(d1,d8);
let d9=self.diag_fail(t2.position::<false>(),"sqrt");
let d10=self.diag_rule(48,d9);
d1=self.diag_join(d1,d10);
let d11=self.diag_fail(t2.position::<false>(),"min");
let d12=self.diag_rule(49,d11);
d1=self.diag_join(d1,d12);
let d13=self.diag_fail(t2.position::<false>(),"max");
let d14=self.diag_rule(50,d13);
d1=self.diag_join(d1,d14);
let d15=self.diag_fail(t2.position::<false>(),"random");
let d16=self.diag_rule(51,d15);
d1=self.diag_join(d1,d16);
let d17=self.diag_fail(t2.position::<false>(),"abs");
let d18=self.diag_rule(52,d17);
d1=self.diag_join(d1,d18);
let d19=self.diag_fail(t2.position::<false>(),"round");
let d20=self.diag_rule(53,d19);
d1=self.diag_join(d1,d20);
let d21=self.diag_fail(t2.position::<false>(),"ceil");
let d22=self.diag_rule(54,d21);
d1=self.diag_join(d1,d22);
let d23=self.diag_fail(t2.position::<false>(),"floor");
let d24=self.diag_rule(55,d23);
d1=self.diag_join(d1,d24);
let d25=self.diag_fail(t2.position::<false>(),"pow");
let d26=self.diag_rule(56,d25);
d1=self.diag_join(d1,d26);
let d27=self.diag_fail(t2.position::<false>(),"log");
let d28=self.diag_rule(57,d27);
d1=self.diag_join(d1,d28);
let d29=self.diag_fail(t2.position::<false>(),"exp");
let d30=self.diag_rule(58,d29);
d1=self.diag_join(d1,d30);
let d31=self.diag_rule(44,d1);
self.display_failures(t2.consumed.max(t2.matched),&["'sin'", "'sin'", "'cos'", "'cos'", "'tan'", "'tan'", "'sqrt'", "'sqrt'", "'min'", "'min'", "'max'", "'max'", "'random'", "'random'", "'abs'", "'abs'", "'round'", "'round'", "'ceil'", "'ceil'", "'floor'", "'floor'", "'pow'", "'pow'", "'log'", "'log'", "'exp'", "'exp'"]);
if state.begin().consumed.max(state.begin().matched)==t2.consumed.max(t2.matched) {self.display_failures(t2.consumed.max(t2.matched),&["'sin'", "'('", "')'", "'cos'", "'('", "')'", "'tan'", "'('", "')'", "'sqrt'", "'('", "')'", "'min'", "'('", "')'", "'max'", "'('", "')'", "'random'", "'('", "')'", "'abs'", "'('", "')'", "'round'", "'('", "')'", "'ceil'", "'('", "')'", "'floor'", "'('", "')'", "'pow'", "'('", "','", "')'", "'log'", "'('", "')'", "'exp'", "'('", "')'"]);}
if state.consumed.max(state.matched)==t2.consumed.max(t2.matched) {self.display_failures(t2.consumed.max(t2.matched),&["'sin'", "'('", "')'", "'cos'", "'tan'", "'sqrt'", "'min'", "'max'", "'random'", "'abs'", "'round'", "'ceil'", "'floor'", "'pow'", "','", "'log'", "'exp'"]);}
Step {ok:false,state,events:EventId(0),diag:d31}
}
fn guard_e495_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e495_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"toNum");
let d3=self.diag_rule(59,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'toNum'", "'toNum'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'toNum'", "'('", "','", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e496_c(&mut self,state:State)->Step {
if !self.can_replay(2) {return self.e496_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"$");
let d3=self.diag_rule(121,d2);
let d4=self.diag_rule(69,d3);
self.display_failures(t1.consumed.max(t1.matched),&["'$'", "'$'", "'$'", "'$'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'.length'", "'('", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d4}
}
fn guard_e497_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e497_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"len");
let d3=self.diag_rule(65,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'len'", "'len'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'len'", "'('", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e498_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e498_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"length");
let d3=self.diag_rule(64,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'length'", "'length'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'length'", "'('", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e499_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e499_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"external");
let d3=self.diag_rule(32,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'external'", "'external'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'external'", "'('", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e501_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e501_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"$");
let d3=self.diag_rule(121,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'$'", "'$'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'$'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e502_c(&mut self,state:State)->Step {
if !self.can_replay(2) {return self.e502_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let mut d2=Diag::NONE;
let d3=self.diag_fail(t1.position::<false>(),"call");
d2=self.diag_join(d2,d3);
let d4=self.diag_fail(t1.begin().position::<false>(),"internal");
d2=self.diag_join(d2,d4);
let d5=self.diag_rule(35,d2);
let d6=self.diag_rule(36,d5);
self.display_failures(t1.consumed.max(t1.matched),&["'call'", "'call'", "'call'", "'internal'"]);
if t1.begin().consumed.max(t1.begin().matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'call'", "'internal'", "'internal'", "'internal'"]);}
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'internal'", "'('", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d6}
}
fn guard_e503_c(&mut self,state:State)->Step {
if !self.can_replay(0) {return self.e503_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"(");
self.display_failures(t1.consumed.max(t1.matched),&["'('", "'('"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'('", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d2}
}
fn guard_e626_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e626_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"toLowerCase");
let d3=self.diag_rule(62,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'toLowerCase'", "'toLowerCase'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'toLowerCase'", "'('", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e627_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e627_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"toUpperCase");
let d3=self.diag_rule(61,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'toUpperCase'", "'toUpperCase'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'toUpperCase'", "'('", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e628_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e628_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"trim");
let d3=self.diag_rule(63,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'trim'", "'trim'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'trim'", "'('", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e629_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e629_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"$");
let d3=self.diag_rule(121,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'$'", "'$'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'$'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e662_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e662_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"external");
let d3=self.diag_rule(33,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'external'", "'external'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'external'", "'string'", "':'", "'('", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e663_c(&mut self,state:State)->Step {
if !self.can_replay(0) {return self.e663_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"(");
self.display_failures(t1.consumed.max(t1.matched),&["'('", "'('"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'$'", "'('", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d2}
}
fn guard_e667_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e667_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"(");
let d3=self.diag_rule(90,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'('", "'('"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'('", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e668_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e668_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"toUpperCase");
let d3=self.diag_rule(61,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'toUpperCase'", "'toUpperCase'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'toUpperCase'", "'('", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e669_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e669_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"toLowerCase");
let d3=self.diag_rule(62,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'toLowerCase'", "'toLowerCase'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'toLowerCase'", "'('", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e670_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e670_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"trim");
let d3=self.diag_rule(63,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'trim'", "'trim'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'trim'", "'('", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e671_c(&mut self,state:State)->Step {
if !self.can_replay(2) {return self.e671_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"$");
let d3=self.diag_rule(121,d2);
let d4=self.diag_rule(66,d3);
self.display_failures(t1.consumed.max(t1.matched),&["'$'", "'$'", "'$'", "'$'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'.toUpperCase'", "'('", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d4}
}
fn guard_e672_c(&mut self,state:State)->Step {
if !self.can_replay(2) {return self.e672_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"$");
let d3=self.diag_rule(121,d2);
let d4=self.diag_rule(67,d3);
self.display_failures(t1.consumed.max(t1.matched),&["'$'", "'$'", "'$'", "'$'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'.toLowerCase'", "'('", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d4}
}
fn guard_e673_c(&mut self,state:State)->Step {
if !self.can_replay(2) {return self.e673_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"$");
let d3=self.diag_rule(121,d2);
let d4=self.diag_rule(68,d3);
self.display_failures(t1.consumed.max(t1.matched),&["'$'", "'$'", "'$'", "'$'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'.trim'", "'('", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d4}
}
fn guard_e675_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e675_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"$");
let d3=self.diag_rule(121,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'$'", "'$'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'$'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e676_c(&mut self,state:State)->Step {
if !self.can_replay(2) {return self.e676_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let mut d2=Diag::NONE;
let d3=self.diag_fail(t1.position::<false>(),"call");
d2=self.diag_join(d2,d3);
let d4=self.diag_fail(t1.begin().position::<false>(),"internal");
d2=self.diag_join(d2,d4);
let d5=self.diag_rule(35,d2);
let d6=self.diag_rule(36,d5);
self.display_failures(t1.consumed.max(t1.matched),&["'call'", "'call'", "'call'", "'internal'"]);
if t1.begin().consumed.max(t1.begin().matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'call'", "'internal'", "'internal'", "'internal'"]);}
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'internal'", "'('", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d6}
}
fn guard_e684_c(&mut self,state:State)->Step {
if !self.can_replay(3) {return self.e684_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let mut d2=Diag::NONE;
let d3=self.diag_fail(t1.position::<false>(),"external");
let d4=self.diag_rule(33,d3);
d2=self.diag_join(d2,d4);
let d5=self.diag_fail(t1.position::<false>(),"(");
d2=self.diag_join(d2,d5);
let d6=self.diag_fail(t1.position::<false>(),"(");
let d7=self.diag_rule(90,d6);
d2=self.diag_join(d2,d7);
let d8=self.diag_fail(t1.position::<false>(),"toUpperCase");
let d9=self.diag_rule(61,d8);
d2=self.diag_join(d2,d9);
let d10=self.diag_fail(t1.position::<false>(),"toLowerCase");
let d11=self.diag_rule(62,d10);
d2=self.diag_join(d2,d11);
let d12=self.diag_fail(t1.position::<false>(),"trim");
let d13=self.diag_rule(63,d12);
d2=self.diag_join(d2,d13);
let d14=self.diag_fail(t1.position::<false>(),"$");
let d15=self.diag_rule(121,d14);
let d16=self.diag_rule(66,d15);
d2=self.diag_join(d2,d16);
let d17=self.diag_fail(t1.position::<false>(),"$");
let d18=self.diag_rule(121,d17);
let d19=self.diag_rule(67,d18);
d2=self.diag_join(d2,d19);
let d20=self.diag_fail(t1.position::<false>(),"$");
let d21=self.diag_rule(121,d20);
let d22=self.diag_rule(68,d21);
d2=self.diag_join(d2,d22);
let d23=self.e674_c(t1.begin()).diag;
d2=self.diag_join(d2,d23);
let d24=self.diag_fail(t1.position::<false>(),"$");
let d25=self.diag_rule(121,d24);
d2=self.diag_join(d2,d25);
let mut d26=Diag::NONE;
let d27=self.diag_fail(t1.position::<false>(),"call");
d26=self.diag_join(d26,d27);
let d28=self.diag_fail(t1.begin().position::<false>(),"internal");
d26=self.diag_join(d26,d28);
let d29=self.diag_rule(35,d26);
let d30=self.diag_rule(36,d29);
d2=self.diag_join(d2,d30);
let d31=self.diag_rule(82,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'external'", "'external'", "'('", "'('", "'('", "'('", "'toUpperCase'", "'toUpperCase'", "'toLowerCase'", "'toLowerCase'", "'trim'", "'trim'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'call'", "'call'", "'call'", "'internal'", "'external'", "'('", "')'", "'toUpperCase'", "'toLowerCase'", "'trim'", "'.toUpperCase'", "'.toLowerCase'", "'.trim'", "'$'", "__CaptureSite"]);
if t1.begin().consumed.max(t1.begin().matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'external'", "'string'", "':'", "'('", "')'", "'$'", "'('", "')'", "'('", "')'", "'toUpperCase'", "'('", "')'", "'toLowerCase'", "'('", "')'", "'trim'", "'('", "')'", "'.toUpperCase'", "'('", "')'", "'.toLowerCase'", "'('", "')'", "'.trim'", "'('", "')'", "'$'", "'call'", "'internal'", "'internal'", "'internal'", "'internal'", "'('", "')'"]);}
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["':'", "'['", "']'"]);}
Step {ok:false,state,events:EventId(0),diag:d31}
}
fn guard_e685_c(&mut self,state:State)->Step {
if !self.can_replay(3) {return self.e685_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let mut d1=Diag::NONE;
let t2=self.trivia_1::<false>(state.begin().entered(true,true)).state;
let d3=self.diag_fail(t2.position::<false>(),"external");
let d4=self.diag_rule(33,d3);
d1=self.diag_join(d1,d4);
let d5=self.diag_fail(t2.position::<false>(),"(");
d1=self.diag_join(d1,d5);
let d6=self.diag_fail(t2.position::<false>(),"(");
let d7=self.diag_rule(90,d6);
d1=self.diag_join(d1,d7);
let d8=self.diag_fail(t2.position::<false>(),"toUpperCase");
let d9=self.diag_rule(61,d8);
d1=self.diag_join(d1,d9);
let d10=self.diag_fail(t2.position::<false>(),"toLowerCase");
let d11=self.diag_rule(62,d10);
d1=self.diag_join(d1,d11);
let d12=self.diag_fail(t2.position::<false>(),"trim");
let d13=self.diag_rule(63,d12);
d1=self.diag_join(d1,d13);
let d14=self.diag_fail(t2.position::<false>(),"$");
let d15=self.diag_rule(121,d14);
let d16=self.diag_rule(66,d15);
d1=self.diag_join(d1,d16);
let d17=self.diag_fail(t2.position::<false>(),"$");
let d18=self.diag_rule(121,d17);
let d19=self.diag_rule(67,d18);
d1=self.diag_join(d1,d19);
let d20=self.diag_fail(t2.position::<false>(),"$");
let d21=self.diag_rule(121,d20);
let d22=self.diag_rule(68,d21);
d1=self.diag_join(d1,d22);
let d23=self.e674_c(state.begin()).diag;
d1=self.diag_join(d1,d23);
let d24=self.diag_fail(t2.position::<false>(),"$");
let d25=self.diag_rule(121,d24);
d1=self.diag_join(d1,d25);
let mut d26=Diag::NONE;
let d27=self.diag_fail(t2.position::<false>(),"call");
d26=self.diag_join(d26,d27);
let d28=self.diag_fail(t2.begin().position::<false>(),"internal");
d26=self.diag_join(d26,d28);
let d29=self.diag_rule(35,d26);
let d30=self.diag_rule(36,d29);
d1=self.diag_join(d1,d30);
let d31=self.diag_rule(82,d1);
self.display_failures(t2.consumed.max(t2.matched),&["'external'", "'external'", "'('", "'('", "'('", "'('", "'toUpperCase'", "'toUpperCase'", "'toLowerCase'", "'toLowerCase'", "'trim'", "'trim'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'call'", "'call'", "'call'", "'internal'"]);
if state.begin().consumed.max(state.begin().matched)==t2.consumed.max(t2.matched) {self.display_failures(t2.consumed.max(t2.matched),&["'external'", "'string'", "':'", "'('", "')'", "'$'", "'('", "')'", "'('", "')'", "'toUpperCase'", "'('", "')'", "'toLowerCase'", "'('", "')'", "'trim'", "'('", "')'", "'.toUpperCase'", "'('", "')'", "'.toLowerCase'", "'('", "')'", "'.trim'", "'('", "')'", "'$'", "'internal'", "'('", "')'"]);}
if t2.begin().consumed.max(t2.begin().matched)==t2.consumed.max(t2.matched) {self.display_failures(t2.consumed.max(t2.matched),&["'call'", "'internal'", "'internal'", "'internal'"]);}
if state.consumed.max(state.matched)==t2.consumed.max(t2.matched) {self.display_failures(t2.consumed.max(t2.matched),&["'external'", "'('", "')'", "'toUpperCase'", "'toLowerCase'", "'trim'", "'.toUpperCase'", "'.toLowerCase'", "'.trim'", "'$'", "__CaptureSite"]);}
Step {ok:false,state,events:EventId(0),diag:d31}
}
fn guard_e693_c(&mut self,state:State)->Step {
if !self.can_replay(3) {return self.e693_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let mut d2=Diag::NONE;
let d3=self.diag_fail(t1.position::<false>(),"external");
let d4=self.diag_rule(33,d3);
d2=self.diag_join(d2,d4);
let d5=self.diag_fail(t1.position::<false>(),"(");
d2=self.diag_join(d2,d5);
let d6=self.diag_fail(t1.position::<false>(),"(");
let d7=self.diag_rule(90,d6);
d2=self.diag_join(d2,d7);
let d8=self.diag_fail(t1.position::<false>(),"toUpperCase");
let d9=self.diag_rule(61,d8);
d2=self.diag_join(d2,d9);
let d10=self.diag_fail(t1.position::<false>(),"toLowerCase");
let d11=self.diag_rule(62,d10);
d2=self.diag_join(d2,d11);
let d12=self.diag_fail(t1.position::<false>(),"trim");
let d13=self.diag_rule(63,d12);
d2=self.diag_join(d2,d13);
let d14=self.diag_fail(t1.position::<false>(),"$");
let d15=self.diag_rule(121,d14);
let d16=self.diag_rule(66,d15);
d2=self.diag_join(d2,d16);
let d17=self.diag_fail(t1.position::<false>(),"$");
let d18=self.diag_rule(121,d17);
let d19=self.diag_rule(67,d18);
d2=self.diag_join(d2,d19);
let d20=self.diag_fail(t1.position::<false>(),"$");
let d21=self.diag_rule(121,d20);
let d22=self.diag_rule(68,d21);
d2=self.diag_join(d2,d22);
let d23=self.e674_c(t1.begin()).diag;
d2=self.diag_join(d2,d23);
let d24=self.diag_fail(t1.position::<false>(),"$");
let d25=self.diag_rule(121,d24);
d2=self.diag_join(d2,d25);
let mut d26=Diag::NONE;
let d27=self.diag_fail(t1.position::<false>(),"call");
d26=self.diag_join(d26,d27);
let d28=self.diag_fail(t1.begin().position::<false>(),"internal");
d26=self.diag_join(d26,d28);
let d29=self.diag_rule(35,d26);
let d30=self.diag_rule(36,d29);
d2=self.diag_join(d2,d30);
let d31=self.diag_rule(82,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'external'", "'external'", "'('", "'('", "'('", "'('", "'toUpperCase'", "'toUpperCase'", "'toLowerCase'", "'toLowerCase'", "'trim'", "'trim'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'call'", "'call'", "'call'", "'internal'", "'external'", "'('", "')'", "'toUpperCase'", "'toLowerCase'", "'trim'", "'.toUpperCase'", "'.toLowerCase'", "'.trim'", "'$'", "__CaptureSite"]);
if t1.begin().consumed.max(t1.begin().matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'external'", "'string'", "':'", "'('", "')'", "'$'", "'('", "')'", "'('", "')'", "'toUpperCase'", "'('", "')'", "'toLowerCase'", "'('", "')'", "'trim'", "'('", "')'", "'.toUpperCase'", "'('", "')'", "'.toLowerCase'", "'('", "')'", "'.trim'", "'('", "')'", "'$'", "'call'", "'internal'", "'internal'", "'internal'", "'internal'", "'('", "')'"]);}
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["':'", "'['", "']'"]);}
Step {ok:false,state,events:EventId(0),diag:d31}
}
fn guard_e700_c(&mut self,state:State)->Step {
if !self.can_replay(3) {return self.e700_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let mut d2=Diag::NONE;
let d3=self.diag_fail(t1.position::<false>(),"external");
let d4=self.diag_rule(33,d3);
d2=self.diag_join(d2,d4);
let d5=self.diag_fail(t1.position::<false>(),"(");
d2=self.diag_join(d2,d5);
let d6=self.diag_fail(t1.position::<false>(),"(");
let d7=self.diag_rule(90,d6);
d2=self.diag_join(d2,d7);
let d8=self.diag_fail(t1.position::<false>(),"toUpperCase");
let d9=self.diag_rule(61,d8);
d2=self.diag_join(d2,d9);
let d10=self.diag_fail(t1.position::<false>(),"toLowerCase");
let d11=self.diag_rule(62,d10);
d2=self.diag_join(d2,d11);
let d12=self.diag_fail(t1.position::<false>(),"trim");
let d13=self.diag_rule(63,d12);
d2=self.diag_join(d2,d13);
let d14=self.diag_fail(t1.position::<false>(),"$");
let d15=self.diag_rule(121,d14);
let d16=self.diag_rule(66,d15);
d2=self.diag_join(d2,d16);
let d17=self.diag_fail(t1.position::<false>(),"$");
let d18=self.diag_rule(121,d17);
let d19=self.diag_rule(67,d18);
d2=self.diag_join(d2,d19);
let d20=self.diag_fail(t1.position::<false>(),"$");
let d21=self.diag_rule(121,d20);
let d22=self.diag_rule(68,d21);
d2=self.diag_join(d2,d22);
let d23=self.e674_c(t1.begin()).diag;
d2=self.diag_join(d2,d23);
let d24=self.diag_fail(t1.position::<false>(),"$");
let d25=self.diag_rule(121,d24);
d2=self.diag_join(d2,d25);
let mut d26=Diag::NONE;
let d27=self.diag_fail(t1.position::<false>(),"call");
d26=self.diag_join(d26,d27);
let d28=self.diag_fail(t1.begin().position::<false>(),"internal");
d26=self.diag_join(d26,d28);
let d29=self.diag_rule(35,d26);
let d30=self.diag_rule(36,d29);
d2=self.diag_join(d2,d30);
let d31=self.diag_rule(82,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'external'", "'external'", "'('", "'('", "'('", "'('", "'toUpperCase'", "'toUpperCase'", "'toLowerCase'", "'toLowerCase'", "'trim'", "'trim'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'call'", "'call'", "'call'", "'internal'", "'external'", "'('", "')'", "'toUpperCase'", "'toLowerCase'", "'trim'", "'.toUpperCase'", "'.toLowerCase'", "'.trim'", "'$'", "__CaptureSite"]);
if t1.begin().consumed.max(t1.begin().matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'external'", "'string'", "':'", "'('", "')'", "'$'", "'('", "')'", "'('", "')'", "'toUpperCase'", "'('", "')'", "'toLowerCase'", "'('", "')'", "'trim'", "'('", "')'", "'.toUpperCase'", "'('", "')'", "'.toLowerCase'", "'('", "')'", "'.trim'", "'('", "')'", "'$'", "'call'", "'internal'", "'internal'", "'internal'", "'internal'", "'('", "')'"]);}
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["':'", "'['", "']'"]);}
Step {ok:false,state,events:EventId(0),diag:d31}
}
fn guard_e708_c(&mut self,state:State)->Step {
if !self.can_replay(3) {return self.e708_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let mut d2=Diag::NONE;
let d3=self.diag_fail(t1.position::<false>(),"external");
let d4=self.diag_rule(33,d3);
d2=self.diag_join(d2,d4);
let d5=self.diag_fail(t1.position::<false>(),"(");
d2=self.diag_join(d2,d5);
let d6=self.diag_fail(t1.position::<false>(),"(");
let d7=self.diag_rule(90,d6);
d2=self.diag_join(d2,d7);
let d8=self.diag_fail(t1.position::<false>(),"toUpperCase");
let d9=self.diag_rule(61,d8);
d2=self.diag_join(d2,d9);
let d10=self.diag_fail(t1.position::<false>(),"toLowerCase");
let d11=self.diag_rule(62,d10);
d2=self.diag_join(d2,d11);
let d12=self.diag_fail(t1.position::<false>(),"trim");
let d13=self.diag_rule(63,d12);
d2=self.diag_join(d2,d13);
let d14=self.diag_fail(t1.position::<false>(),"$");
let d15=self.diag_rule(121,d14);
let d16=self.diag_rule(66,d15);
d2=self.diag_join(d2,d16);
let d17=self.diag_fail(t1.position::<false>(),"$");
let d18=self.diag_rule(121,d17);
let d19=self.diag_rule(67,d18);
d2=self.diag_join(d2,d19);
let d20=self.diag_fail(t1.position::<false>(),"$");
let d21=self.diag_rule(121,d20);
let d22=self.diag_rule(68,d21);
d2=self.diag_join(d2,d22);
let d23=self.e674_c(t1.begin()).diag;
d2=self.diag_join(d2,d23);
let d24=self.diag_fail(t1.position::<false>(),"$");
let d25=self.diag_rule(121,d24);
d2=self.diag_join(d2,d25);
let mut d26=Diag::NONE;
let d27=self.diag_fail(t1.position::<false>(),"call");
d26=self.diag_join(d26,d27);
let d28=self.diag_fail(t1.begin().position::<false>(),"internal");
d26=self.diag_join(d26,d28);
let d29=self.diag_rule(35,d26);
let d30=self.diag_rule(36,d29);
d2=self.diag_join(d2,d30);
let d31=self.diag_rule(82,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'external'", "'external'", "'('", "'('", "'('", "'('", "'toUpperCase'", "'toUpperCase'", "'toLowerCase'", "'toLowerCase'", "'trim'", "'trim'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'call'", "'call'", "'call'", "'internal'", "'external'", "'('", "')'", "'toUpperCase'", "'toLowerCase'", "'trim'", "'.toUpperCase'", "'.toLowerCase'", "'.trim'", "'$'", "__CaptureSite"]);
if t1.begin().consumed.max(t1.begin().matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'external'", "'string'", "':'", "'('", "')'", "'$'", "'('", "')'", "'('", "')'", "'toUpperCase'", "'('", "')'", "'toLowerCase'", "'('", "')'", "'trim'", "'('", "')'", "'.toUpperCase'", "'('", "')'", "'.toLowerCase'", "'('", "')'", "'.trim'", "'('", "')'", "'$'", "'call'", "'internal'", "'internal'", "'internal'", "'internal'", "'('", "')'"]);}
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["':'", "'['", "']'"]);}
Step {ok:false,state,events:EventId(0),diag:d31}
}
fn guard_e714_c(&mut self,state:State)->Step {
if !self.can_replay(3) {return self.e714_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let mut d2=Diag::NONE;
let d3=self.diag_fail(t1.position::<false>(),"external");
let d4=self.diag_rule(33,d3);
d2=self.diag_join(d2,d4);
let d5=self.diag_fail(t1.position::<false>(),"(");
d2=self.diag_join(d2,d5);
let d6=self.diag_fail(t1.position::<false>(),"(");
let d7=self.diag_rule(90,d6);
d2=self.diag_join(d2,d7);
let d8=self.diag_fail(t1.position::<false>(),"toUpperCase");
let d9=self.diag_rule(61,d8);
d2=self.diag_join(d2,d9);
let d10=self.diag_fail(t1.position::<false>(),"toLowerCase");
let d11=self.diag_rule(62,d10);
d2=self.diag_join(d2,d11);
let d12=self.diag_fail(t1.position::<false>(),"trim");
let d13=self.diag_rule(63,d12);
d2=self.diag_join(d2,d13);
let d14=self.diag_fail(t1.position::<false>(),"$");
let d15=self.diag_rule(121,d14);
let d16=self.diag_rule(66,d15);
d2=self.diag_join(d2,d16);
let d17=self.diag_fail(t1.position::<false>(),"$");
let d18=self.diag_rule(121,d17);
let d19=self.diag_rule(67,d18);
d2=self.diag_join(d2,d19);
let d20=self.diag_fail(t1.position::<false>(),"$");
let d21=self.diag_rule(121,d20);
let d22=self.diag_rule(68,d21);
d2=self.diag_join(d2,d22);
let d23=self.e674_c(t1.begin()).diag;
d2=self.diag_join(d2,d23);
let d24=self.diag_fail(t1.position::<false>(),"$");
let d25=self.diag_rule(121,d24);
d2=self.diag_join(d2,d25);
let mut d26=Diag::NONE;
let d27=self.diag_fail(t1.position::<false>(),"call");
d26=self.diag_join(d26,d27);
let d28=self.diag_fail(t1.begin().position::<false>(),"internal");
d26=self.diag_join(d26,d28);
let d29=self.diag_rule(35,d26);
let d30=self.diag_rule(36,d29);
d2=self.diag_join(d2,d30);
let d31=self.diag_rule(82,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'external'", "'external'", "'('", "'('", "'('", "'('", "'toUpperCase'", "'toUpperCase'", "'toLowerCase'", "'toLowerCase'", "'trim'", "'trim'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'call'", "'call'", "'call'", "'internal'", "'external'", "'('", "')'", "'toUpperCase'", "'toLowerCase'", "'trim'", "'.toUpperCase'", "'.toLowerCase'", "'.trim'", "'$'", "__CaptureSite"]);
if t1.begin().consumed.max(t1.begin().matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'external'", "'string'", "':'", "'('", "')'", "'$'", "'('", "')'", "'('", "')'", "'toUpperCase'", "'('", "')'", "'toLowerCase'", "'('", "')'", "'trim'", "'('", "')'", "'.toUpperCase'", "'('", "')'", "'.toLowerCase'", "'('", "')'", "'.trim'", "'('", "')'", "'$'", "'call'", "'internal'", "'internal'", "'internal'", "'internal'", "'('", "')'"]);}
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["':'", "'['", "']'"]);}
Step {ok:false,state,events:EventId(0),diag:d31}
}
fn guard_e722_c(&mut self,state:State)->Step {
if !self.can_replay(3) {return self.e722_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let mut d2=Diag::NONE;
let d3=self.diag_fail(t1.position::<false>(),"external");
let d4=self.diag_rule(33,d3);
d2=self.diag_join(d2,d4);
let d5=self.diag_fail(t1.position::<false>(),"(");
d2=self.diag_join(d2,d5);
let d6=self.diag_fail(t1.position::<false>(),"(");
let d7=self.diag_rule(90,d6);
d2=self.diag_join(d2,d7);
let d8=self.diag_fail(t1.position::<false>(),"toUpperCase");
let d9=self.diag_rule(61,d8);
d2=self.diag_join(d2,d9);
let d10=self.diag_fail(t1.position::<false>(),"toLowerCase");
let d11=self.diag_rule(62,d10);
d2=self.diag_join(d2,d11);
let d12=self.diag_fail(t1.position::<false>(),"trim");
let d13=self.diag_rule(63,d12);
d2=self.diag_join(d2,d13);
let d14=self.diag_fail(t1.position::<false>(),"$");
let d15=self.diag_rule(121,d14);
let d16=self.diag_rule(66,d15);
d2=self.diag_join(d2,d16);
let d17=self.diag_fail(t1.position::<false>(),"$");
let d18=self.diag_rule(121,d17);
let d19=self.diag_rule(67,d18);
d2=self.diag_join(d2,d19);
let d20=self.diag_fail(t1.position::<false>(),"$");
let d21=self.diag_rule(121,d20);
let d22=self.diag_rule(68,d21);
d2=self.diag_join(d2,d22);
let d23=self.e674_c(t1.begin()).diag;
d2=self.diag_join(d2,d23);
let d24=self.diag_fail(t1.position::<false>(),"$");
let d25=self.diag_rule(121,d24);
d2=self.diag_join(d2,d25);
let mut d26=Diag::NONE;
let d27=self.diag_fail(t1.position::<false>(),"call");
d26=self.diag_join(d26,d27);
let d28=self.diag_fail(t1.begin().position::<false>(),"internal");
d26=self.diag_join(d26,d28);
let d29=self.diag_rule(35,d26);
let d30=self.diag_rule(36,d29);
d2=self.diag_join(d2,d30);
let d31=self.diag_rule(82,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'external'", "'external'", "'('", "'('", "'('", "'('", "'toUpperCase'", "'toUpperCase'", "'toLowerCase'", "'toLowerCase'", "'trim'", "'trim'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'call'", "'call'", "'call'", "'internal'", "'external'", "'('", "')'", "'toUpperCase'", "'toLowerCase'", "'trim'", "'.toUpperCase'", "'.toLowerCase'", "'.trim'", "'$'", "__CaptureSite"]);
if t1.begin().consumed.max(t1.begin().matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'external'", "'string'", "':'", "'('", "')'", "'$'", "'('", "')'", "'('", "')'", "'toUpperCase'", "'('", "')'", "'toLowerCase'", "'('", "')'", "'trim'", "'('", "')'", "'.toUpperCase'", "'('", "')'", "'.toLowerCase'", "'('", "')'", "'.trim'", "'('", "')'", "'$'", "'call'", "'internal'", "'internal'", "'internal'", "'internal'", "'('", "')'"]);}
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["':'", "'['", "']'"]);}
Step {ok:false,state,events:EventId(0),diag:d31}
}
fn guard_e728_c(&mut self,state:State)->Step {
if !self.can_replay(3) {return self.e728_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let mut d2=Diag::NONE;
let d3=self.diag_fail(t1.position::<false>(),"external");
let d4=self.diag_rule(33,d3);
d2=self.diag_join(d2,d4);
let d5=self.diag_fail(t1.position::<false>(),"(");
d2=self.diag_join(d2,d5);
let d6=self.diag_fail(t1.position::<false>(),"(");
let d7=self.diag_rule(90,d6);
d2=self.diag_join(d2,d7);
let d8=self.diag_fail(t1.position::<false>(),"toUpperCase");
let d9=self.diag_rule(61,d8);
d2=self.diag_join(d2,d9);
let d10=self.diag_fail(t1.position::<false>(),"toLowerCase");
let d11=self.diag_rule(62,d10);
d2=self.diag_join(d2,d11);
let d12=self.diag_fail(t1.position::<false>(),"trim");
let d13=self.diag_rule(63,d12);
d2=self.diag_join(d2,d13);
let d14=self.diag_fail(t1.position::<false>(),"$");
let d15=self.diag_rule(121,d14);
let d16=self.diag_rule(66,d15);
d2=self.diag_join(d2,d16);
let d17=self.diag_fail(t1.position::<false>(),"$");
let d18=self.diag_rule(121,d17);
let d19=self.diag_rule(67,d18);
d2=self.diag_join(d2,d19);
let d20=self.diag_fail(t1.position::<false>(),"$");
let d21=self.diag_rule(121,d20);
let d22=self.diag_rule(68,d21);
d2=self.diag_join(d2,d22);
let d23=self.e674_c(t1.begin()).diag;
d2=self.diag_join(d2,d23);
let d24=self.diag_fail(t1.position::<false>(),"$");
let d25=self.diag_rule(121,d24);
d2=self.diag_join(d2,d25);
let mut d26=Diag::NONE;
let d27=self.diag_fail(t1.position::<false>(),"call");
d26=self.diag_join(d26,d27);
let d28=self.diag_fail(t1.begin().position::<false>(),"internal");
d26=self.diag_join(d26,d28);
let d29=self.diag_rule(35,d26);
let d30=self.diag_rule(36,d29);
d2=self.diag_join(d2,d30);
let d31=self.diag_rule(82,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'external'", "'external'", "'('", "'('", "'('", "'('", "'toUpperCase'", "'toUpperCase'", "'toLowerCase'", "'toLowerCase'", "'trim'", "'trim'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'call'", "'call'", "'call'", "'internal'", "'external'", "'('", "')'", "'toUpperCase'", "'toLowerCase'", "'trim'", "'.toUpperCase'", "'.toLowerCase'", "'.trim'", "'$'", "__CaptureSite"]);
if t1.begin().consumed.max(t1.begin().matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'external'", "'string'", "':'", "'('", "')'", "'$'", "'('", "')'", "'('", "')'", "'toUpperCase'", "'('", "')'", "'toLowerCase'", "'('", "')'", "'trim'", "'('", "')'", "'.toUpperCase'", "'('", "')'", "'.toLowerCase'", "'('", "')'", "'.trim'", "'('", "')'", "'$'", "'call'", "'internal'", "'internal'", "'internal'", "'internal'", "'('", "')'"]);}
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["':'", "'['", "']'"]);}
Step {ok:false,state,events:EventId(0),diag:d31}
}
fn guard_e735_c(&mut self,state:State)->Step {
if !self.can_replay(3) {return self.e735_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let mut d2=Diag::NONE;
let d3=self.diag_fail(t1.position::<false>(),"external");
let d4=self.diag_rule(33,d3);
d2=self.diag_join(d2,d4);
let d5=self.diag_fail(t1.position::<false>(),"(");
d2=self.diag_join(d2,d5);
let d6=self.diag_fail(t1.position::<false>(),"(");
let d7=self.diag_rule(90,d6);
d2=self.diag_join(d2,d7);
let d8=self.diag_fail(t1.position::<false>(),"toUpperCase");
let d9=self.diag_rule(61,d8);
d2=self.diag_join(d2,d9);
let d10=self.diag_fail(t1.position::<false>(),"toLowerCase");
let d11=self.diag_rule(62,d10);
d2=self.diag_join(d2,d11);
let d12=self.diag_fail(t1.position::<false>(),"trim");
let d13=self.diag_rule(63,d12);
d2=self.diag_join(d2,d13);
let d14=self.diag_fail(t1.position::<false>(),"$");
let d15=self.diag_rule(121,d14);
let d16=self.diag_rule(66,d15);
d2=self.diag_join(d2,d16);
let d17=self.diag_fail(t1.position::<false>(),"$");
let d18=self.diag_rule(121,d17);
let d19=self.diag_rule(67,d18);
d2=self.diag_join(d2,d19);
let d20=self.diag_fail(t1.position::<false>(),"$");
let d21=self.diag_rule(121,d20);
let d22=self.diag_rule(68,d21);
d2=self.diag_join(d2,d22);
let d23=self.e674_c(t1.begin()).diag;
d2=self.diag_join(d2,d23);
let d24=self.diag_fail(t1.position::<false>(),"$");
let d25=self.diag_rule(121,d24);
d2=self.diag_join(d2,d25);
let mut d26=Diag::NONE;
let d27=self.diag_fail(t1.position::<false>(),"call");
d26=self.diag_join(d26,d27);
let d28=self.diag_fail(t1.begin().position::<false>(),"internal");
d26=self.diag_join(d26,d28);
let d29=self.diag_rule(35,d26);
let d30=self.diag_rule(36,d29);
d2=self.diag_join(d2,d30);
let d31=self.diag_rule(82,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'external'", "'external'", "'('", "'('", "'('", "'('", "'toUpperCase'", "'toUpperCase'", "'toLowerCase'", "'toLowerCase'", "'trim'", "'trim'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'$'", "'call'", "'call'", "'call'", "'internal'", "'external'", "'('", "')'", "'toUpperCase'", "'toLowerCase'", "'trim'", "'.toUpperCase'", "'.toLowerCase'", "'.trim'", "'$'", "__CaptureSite"]);
if t1.begin().consumed.max(t1.begin().matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'external'", "'string'", "':'", "'('", "')'", "'$'", "'('", "')'", "'('", "')'", "'toUpperCase'", "'('", "')'", "'toLowerCase'", "'('", "')'", "'trim'", "'('", "')'", "'.toUpperCase'", "'('", "')'", "'.toLowerCase'", "'('", "')'", "'.trim'", "'('", "')'", "'$'", "'call'", "'internal'", "'internal'", "'internal'", "'internal'", "'('", "')'"]);}
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["':'", "'['", "']'"]);}
Step {ok:false,state,events:EventId(0),diag:d31}
}
fn guard_e811_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e811_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"match");
let d3=self.diag_rule(113,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'match'", "'match'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'match'", "'{'", "','", "'}'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e812_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e812_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"if");
let d3=self.diag_rule(106,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'if'", "'if'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'if'", "'('", "')'", "'{'", "'}'", "'else'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e813_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e813_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"(");
let d3=self.diag_rule(92,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'('", "'('"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'('", "'string'", "'String'", "')'", "'$'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e814_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e814_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"$");
let d3=self.diag_rule(93,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'$'", "'$'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'$'", "'as'", "'string'", "'String'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e816_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e816_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"(");
let d3=self.diag_rule(90,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'('", "'('"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'('", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e817_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e817_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"external");
let d3=self.diag_rule(33,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'external'", "'external'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'external'", "'string'", "':'", "'('", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e818_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e818_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"toUpperCase");
let d3=self.diag_rule(61,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'toUpperCase'", "'toUpperCase'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'toUpperCase'", "'('", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e819_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e819_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"toLowerCase");
let d3=self.diag_rule(62,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'toLowerCase'", "'toLowerCase'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'toLowerCase'", "'('", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e820_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e820_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"trim");
let d3=self.diag_rule(63,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'trim'", "'trim'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'trim'", "'('", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e821_c(&mut self,state:State)->Step {
if !self.can_replay(2) {return self.e821_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"$");
let d3=self.diag_rule(121,d2);
let d4=self.diag_rule(66,d3);
self.display_failures(t1.consumed.max(t1.matched),&["'$'", "'$'", "'$'", "'$'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'.toUpperCase'", "'('", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d4}
}
fn guard_e822_c(&mut self,state:State)->Step {
if !self.can_replay(2) {return self.e822_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"$");
let d3=self.diag_rule(121,d2);
let d4=self.diag_rule(67,d3);
self.display_failures(t1.consumed.max(t1.matched),&["'$'", "'$'", "'$'", "'$'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'.toLowerCase'", "'('", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d4}
}
fn guard_e823_c(&mut self,state:State)->Step {
if !self.can_replay(2) {return self.e823_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"$");
let d3=self.diag_rule(121,d2);
let d4=self.diag_rule(68,d3);
self.display_failures(t1.consumed.max(t1.matched),&["'$'", "'$'", "'$'", "'$'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'.trim'", "'('", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d4}
}
fn guard_e825_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e825_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"$");
let d3=self.diag_rule(121,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'$'", "'$'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'$'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e826_c(&mut self,state:State)->Step {
if !self.can_replay(2) {return self.e826_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let mut d2=Diag::NONE;
let d3=self.diag_fail(t1.position::<false>(),"call");
d2=self.diag_join(d2,d3);
let d4=self.diag_fail(t1.begin().position::<false>(),"internal");
d2=self.diag_join(d2,d4);
let d5=self.diag_rule(35,d2);
let d6=self.diag_rule(36,d5);
self.display_failures(t1.consumed.max(t1.matched),&["'call'", "'call'", "'call'", "'internal'"]);
if t1.begin().consumed.max(t1.begin().matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'call'", "'internal'", "'internal'", "'internal'"]);}
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'internal'", "'('", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d6}
}
fn guard_e868_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e868_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"not");
let d3=self.diag_rule(97,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'not'", "'not'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'not'", "'('", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e869_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e869_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"if");
let d3=self.diag_rule(106,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'if'", "'if'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'if'", "'('", "')'", "'{'", "'}'", "'else'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e870_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e870_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"match");
let d3=self.diag_rule(117,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'match'", "'match'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'match'", "'{'", "','", "'}'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e871_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e871_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"external");
let d3=self.diag_rule(31,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'external'", "'external'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'external'", "'boolean'", "':'", "'('", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e873_c(&mut self,state:State)->Step {
if !self.can_replay(3) {return self.e873_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let mut d2=Diag::NONE;
let d3=self.diag_fail(t1.position::<false>(),"toLowerCase");
let d4=self.diag_rule(62,d3);
d2=self.diag_join(d2,d4);
let d5=self.diag_fail(t1.position::<false>(),"toUpperCase");
let d6=self.diag_rule(61,d5);
d2=self.diag_join(d2,d6);
let d7=self.diag_fail(t1.position::<false>(),"trim");
let d8=self.diag_rule(63,d7);
d2=self.diag_join(d2,d8);
let d9=self.diag_fail(t1.position::<false>(),"$");
let d10=self.diag_rule(121,d9);
d2=self.diag_join(d2,d10);
let d11=self.diag_rule(77,d2);
let d12=self.diag_rule(74,d11);
self.display_failures(t1.consumed.max(t1.matched),&["'toLowerCase'", "'toLowerCase'", "'toUpperCase'", "'toUpperCase'", "'trim'", "'trim'", "'$'", "'$'", "'toLowerCase'", "'('", "')'", "'toUpperCase'", "'trim'", "'$'", "__CaptureSite"]);
if t1.begin().consumed.max(t1.begin().matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'toLowerCase'", "'('", "')'", "'toUpperCase'", "'('", "')'", "'trim'", "'('", "')'", "'$'"]);}
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'.startsWith'", "'('", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d12}
}
fn guard_e874_c(&mut self,state:State)->Step {
if !self.can_replay(3) {return self.e874_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let mut d2=Diag::NONE;
let d3=self.diag_fail(t1.position::<false>(),"toLowerCase");
let d4=self.diag_rule(62,d3);
d2=self.diag_join(d2,d4);
let d5=self.diag_fail(t1.position::<false>(),"toUpperCase");
let d6=self.diag_rule(61,d5);
d2=self.diag_join(d2,d6);
let d7=self.diag_fail(t1.position::<false>(),"trim");
let d8=self.diag_rule(63,d7);
d2=self.diag_join(d2,d8);
let d9=self.diag_fail(t1.position::<false>(),"$");
let d10=self.diag_rule(121,d9);
d2=self.diag_join(d2,d10);
let d11=self.diag_rule(77,d2);
let d12=self.diag_rule(75,d11);
self.display_failures(t1.consumed.max(t1.matched),&["'toLowerCase'", "'toLowerCase'", "'toUpperCase'", "'toUpperCase'", "'trim'", "'trim'", "'$'", "'$'", "'toLowerCase'", "'('", "')'", "'toUpperCase'", "'trim'", "'$'", "__CaptureSite"]);
if t1.begin().consumed.max(t1.begin().matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'toLowerCase'", "'('", "')'", "'toUpperCase'", "'('", "')'", "'trim'", "'('", "')'", "'$'"]);}
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'.endsWith'", "'('", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d12}
}
fn guard_e875_c(&mut self,state:State)->Step {
if !self.can_replay(3) {return self.e875_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let mut d2=Diag::NONE;
let d3=self.diag_fail(t1.position::<false>(),"toLowerCase");
let d4=self.diag_rule(62,d3);
d2=self.diag_join(d2,d4);
let d5=self.diag_fail(t1.position::<false>(),"toUpperCase");
let d6=self.diag_rule(61,d5);
d2=self.diag_join(d2,d6);
let d7=self.diag_fail(t1.position::<false>(),"trim");
let d8=self.diag_rule(63,d7);
d2=self.diag_join(d2,d8);
let d9=self.diag_fail(t1.position::<false>(),"$");
let d10=self.diag_rule(121,d9);
d2=self.diag_join(d2,d10);
let d11=self.diag_rule(77,d2);
let d12=self.diag_rule(76,d11);
self.display_failures(t1.consumed.max(t1.matched),&["'toLowerCase'", "'toLowerCase'", "'toUpperCase'", "'toUpperCase'", "'trim'", "'trim'", "'$'", "'$'", "'toLowerCase'", "'('", "')'", "'toUpperCase'", "'trim'", "'$'", "__CaptureSite"]);
if t1.begin().consumed.max(t1.begin().matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'toLowerCase'", "'('", "')'", "'toUpperCase'", "'('", "')'", "'trim'", "'('", "')'", "'$'"]);}
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'.contains'", "'('", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d12}
}
fn guard_e876_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e876_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"startsWith");
let d3=self.diag_rule(70,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'startsWith'", "'startsWith'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'startsWith'", "'('", "','", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e877_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e877_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"endsWith");
let d3=self.diag_rule(71,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'endsWith'", "'endsWith'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'endsWith'", "'('", "','", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e878_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e878_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"contains");
let d3=self.diag_rule(72,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'contains'", "'contains'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'contains'", "'('", "','", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e879_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e879_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"isPresent");
let d3=self.diag_rule(78,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'isPresent'", "'isPresent'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'isPresent'", "'('", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e880_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e880_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"inTimeRange");
let d3=self.diag_rule(79,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'inTimeRange'", "'inTimeRange'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'inTimeRange'", "'('", "','", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e881_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e881_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"inDayTimeRange");
let d3=self.diag_rule(80,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'inDayTimeRange'", "'inDayTimeRange'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'inDayTimeRange'", "'('", "','", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e884_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e884_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"$");
let d3=self.diag_rule(121,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'$'", "'$'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'$'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e885_c(&mut self,state:State)->Step {
if !self.can_replay(2) {return self.e885_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let mut d2=Diag::NONE;
let d3=self.diag_fail(t1.position::<false>(),"call");
d2=self.diag_join(d2,d3);
let d4=self.diag_fail(t1.begin().position::<false>(),"internal");
d2=self.diag_join(d2,d4);
let d5=self.diag_rule(35,d2);
let d6=self.diag_rule(36,d5);
self.display_failures(t1.consumed.max(t1.matched),&["'call'", "'call'", "'call'", "'internal'"]);
if t1.begin().consumed.max(t1.begin().matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'call'", "'internal'", "'internal'", "'internal'"]);}
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'internal'", "'('", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d6}
}
fn guard_e886_c(&mut self,state:State)->Step {
if !self.can_replay(0) {return self.e886_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"(");
self.display_failures(t1.consumed.max(t1.matched),&["'('", "'('"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'('", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d2}
}
fn guard_e896_c(&mut self,state:State)->Step {
if !self.can_replay(6) {return self.e896_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let mut d2=Diag::NONE;
let d3=self.diag_fail(t1.position::<false>(),"(");
let d4=self.diag_rule(108,d3);
d2=self.diag_join(d2,d4);
let d5=self.diag_fail(t1.position::<false>(),"match");
let d6=self.diag_rule(109,d5);
d2=self.diag_join(d2,d6);
let d7=self.diag_fail(t1.position::<false>(),"if");
let d8=self.diag_rule(106,d7);
d2=self.diag_join(d2,d8);
let mut d9=Diag::NONE;
let d10=self.diag_fail(t1.position::<false>(),"sin");
let d11=self.diag_rule(45,d10);
d9=self.diag_join(d9,d11);
let d12=self.diag_fail(t1.position::<false>(),"cos");
let d13=self.diag_rule(46,d12);
d9=self.diag_join(d9,d13);
let d14=self.diag_fail(t1.position::<false>(),"tan");
let d15=self.diag_rule(47,d14);
d9=self.diag_join(d9,d15);
let d16=self.diag_fail(t1.position::<false>(),"sqrt");
let d17=self.diag_rule(48,d16);
d9=self.diag_join(d9,d17);
let d18=self.diag_fail(t1.position::<false>(),"min");
let d19=self.diag_rule(49,d18);
d9=self.diag_join(d9,d19);
let d20=self.diag_fail(t1.position::<false>(),"max");
let d21=self.diag_rule(50,d20);
d9=self.diag_join(d9,d21);
let d22=self.diag_fail(t1.position::<false>(),"random");
let d23=self.diag_rule(51,d22);
d9=self.diag_join(d9,d23);
let d24=self.diag_fail(t1.position::<false>(),"abs");
let d25=self.diag_rule(52,d24);
d9=self.diag_join(d9,d25);
let d26=self.diag_fail(t1.position::<false>(),"round");
let d27=self.diag_rule(53,d26);
d9=self.diag_join(d9,d27);
let d28=self.diag_fail(t1.position::<false>(),"ceil");
let d29=self.diag_rule(54,d28);
d9=self.diag_join(d9,d29);
let d30=self.diag_fail(t1.position::<false>(),"floor");
let d31=self.diag_rule(55,d30);
d9=self.diag_join(d9,d31);
let d32=self.diag_fail(t1.position::<false>(),"pow");
let d33=self.diag_rule(56,d32);
d9=self.diag_join(d9,d33);
let d34=self.diag_fail(t1.position::<false>(),"log");
let d35=self.diag_rule(57,d34);
d9=self.diag_join(d9,d35);
let d36=self.diag_fail(t1.position::<false>(),"exp");
let d37=self.diag_rule(58,d36);
d9=self.diag_join(d9,d37);
let d38=self.diag_rule(44,d9);
d2=self.diag_join(d2,d38);
let d39=self.diag_fail(t1.position::<false>(),"toNum");
let d40=self.diag_rule(59,d39);
d2=self.diag_join(d2,d40);
let d41=self.diag_fail(t1.position::<false>(),"$");
let d42=self.diag_rule(121,d41);
let d43=self.diag_rule(69,d42);
d2=self.diag_join(d2,d43);
let d44=self.diag_fail(t1.position::<false>(),"len");
let d45=self.diag_rule(65,d44);
d2=self.diag_join(d2,d45);
let d46=self.diag_fail(t1.position::<false>(),"length");
let d47=self.diag_rule(64,d46);
d2=self.diag_join(d2,d47);
let d48=self.diag_fail(t1.position::<false>(),"external");
let d49=self.diag_rule(32,d48);
d2=self.diag_join(d2,d49);
let d50=self.e500_c(t1.begin()).diag;
d2=self.diag_join(d2,d50);
let d51=self.diag_fail(t1.position::<false>(),"$");
let d52=self.diag_rule(121,d51);
d2=self.diag_join(d2,d52);
let mut d53=Diag::NONE;
let d54=self.diag_fail(t1.position::<false>(),"call");
d53=self.diag_join(d53,d54);
let d55=self.diag_fail(t1.begin().position::<false>(),"internal");
d53=self.diag_join(d53,d55);
let d56=self.diag_rule(35,d53);
let d57=self.diag_rule(36,d56);
d2=self.diag_join(d2,d57);
let d58=self.diag_fail(t1.position::<false>(),"(");
d2=self.diag_join(d2,d58);
let d59=self.diag_rule(60,d2);
let d60=self.diag_rule(41,d59);
let d61=self.diag_rule(40,d60);
let d62=self.diag_rule(103,d61);
self.display_failures(t1.consumed.max(t1.matched),&["'('", "'('", "'match'", "'match'", "'if'", "'if'", "'sin'", "'sin'", "'cos'", "'cos'", "'tan'", "'tan'", "'sqrt'", "'sqrt'", "'min'", "'min'", "'max'", "'max'", "'random'", "'random'", "'abs'", "'abs'", "'round'", "'round'", "'ceil'", "'ceil'", "'floor'", "'floor'", "'pow'", "'pow'", "'log'", "'log'", "'exp'", "'exp'", "'toNum'", "'toNum'", "'$'", "'$'", "'$'", "'$'", "'len'", "'len'", "'length'", "'length'", "'external'", "'external'", "'$'", "'$'", "'call'", "'call'", "'call'", "'internal'", "'('", "'('", "'('", "'?'", "':'", "')'", "'match'", "'{'", "','", "'}'", "'if'", "'else'", "'toNum'", "'.length'", "'len'", "'length'", "'external'", "'$'", "__CaptureSite", "NumberTermParser", "__CaptureSite", "NumberExpressionParser", "__CaptureSite"]);
if t1.begin().consumed.max(t1.begin().matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'('", "'?'", "':'", "')'", "'match'", "'{'", "','", "'}'", "'if'", "'('", "')'", "'{'", "'}'", "'else'", "'sin'", "'('", "')'", "'cos'", "'tan'", "'sqrt'", "'min'", "'max'", "'random'", "'abs'", "'round'", "'ceil'", "'floor'", "'pow'", "','", "'log'", "'exp'", "'toNum'", "'('", "','", "')'", "'.length'", "'('", "')'", "'len'", "'('", "')'", "'length'", "'('", "')'", "'external'", "'('", "')'", "'$'", "'call'", "'internal'", "'internal'", "'internal'", "'internal'", "'('", "')'", "'('", "')'"]);}
if t1.begin().begin().consumed.max(t1.begin().begin().matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'sin'", "'('", "')'", "'cos'", "'('", "')'", "'tan'", "'('", "')'", "'sqrt'", "'('", "')'", "'min'", "'('", "')'", "'max'", "'('", "')'", "'random'", "'('", "')'", "'abs'", "'('", "')'", "'round'", "'('", "')'", "'ceil'", "'('", "')'", "'floor'", "'('", "')'", "'pow'", "'('", "','", "')'", "'log'", "'('", "')'", "'exp'", "'('", "')'"]);}
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["ComparisonExpressionParser", "__CaptureSite"]);}
Step {ok:false,state,events:EventId(0),diag:d62}
}
fn guard_e918_c(&mut self,state:State)->Step {
if !self.can_replay(5) {return self.e918_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let mut d2=Diag::NONE;
let d3=self.diag_fail(t1.position::<false>(),"(");
let d4=self.diag_rule(108,d3);
d2=self.diag_join(d2,d4);
let d5=self.diag_fail(t1.position::<false>(),"match");
let d6=self.diag_rule(109,d5);
d2=self.diag_join(d2,d6);
let d7=self.diag_fail(t1.position::<false>(),"if");
let d8=self.diag_rule(106,d7);
d2=self.diag_join(d2,d8);
let mut d9=Diag::NONE;
let d10=self.diag_fail(t1.position::<false>(),"sin");
let d11=self.diag_rule(45,d10);
d9=self.diag_join(d9,d11);
let d12=self.diag_fail(t1.position::<false>(),"cos");
let d13=self.diag_rule(46,d12);
d9=self.diag_join(d9,d13);
let d14=self.diag_fail(t1.position::<false>(),"tan");
let d15=self.diag_rule(47,d14);
d9=self.diag_join(d9,d15);
let d16=self.diag_fail(t1.position::<false>(),"sqrt");
let d17=self.diag_rule(48,d16);
d9=self.diag_join(d9,d17);
let d18=self.diag_fail(t1.position::<false>(),"min");
let d19=self.diag_rule(49,d18);
d9=self.diag_join(d9,d19);
let d20=self.diag_fail(t1.position::<false>(),"max");
let d21=self.diag_rule(50,d20);
d9=self.diag_join(d9,d21);
let d22=self.diag_fail(t1.position::<false>(),"random");
let d23=self.diag_rule(51,d22);
d9=self.diag_join(d9,d23);
let d24=self.diag_fail(t1.position::<false>(),"abs");
let d25=self.diag_rule(52,d24);
d9=self.diag_join(d9,d25);
let d26=self.diag_fail(t1.position::<false>(),"round");
let d27=self.diag_rule(53,d26);
d9=self.diag_join(d9,d27);
let d28=self.diag_fail(t1.position::<false>(),"ceil");
let d29=self.diag_rule(54,d28);
d9=self.diag_join(d9,d29);
let d30=self.diag_fail(t1.position::<false>(),"floor");
let d31=self.diag_rule(55,d30);
d9=self.diag_join(d9,d31);
let d32=self.diag_fail(t1.position::<false>(),"pow");
let d33=self.diag_rule(56,d32);
d9=self.diag_join(d9,d33);
let d34=self.diag_fail(t1.position::<false>(),"log");
let d35=self.diag_rule(57,d34);
d9=self.diag_join(d9,d35);
let d36=self.diag_fail(t1.position::<false>(),"exp");
let d37=self.diag_rule(58,d36);
d9=self.diag_join(d9,d37);
let d38=self.diag_rule(44,d9);
d2=self.diag_join(d2,d38);
let d39=self.diag_fail(t1.position::<false>(),"toNum");
let d40=self.diag_rule(59,d39);
d2=self.diag_join(d2,d40);
let d41=self.diag_fail(t1.position::<false>(),"$");
let d42=self.diag_rule(121,d41);
let d43=self.diag_rule(69,d42);
d2=self.diag_join(d2,d43);
let d44=self.diag_fail(t1.position::<false>(),"len");
let d45=self.diag_rule(65,d44);
d2=self.diag_join(d2,d45);
let d46=self.diag_fail(t1.position::<false>(),"length");
let d47=self.diag_rule(64,d46);
d2=self.diag_join(d2,d47);
let d48=self.diag_fail(t1.position::<false>(),"external");
let d49=self.diag_rule(32,d48);
d2=self.diag_join(d2,d49);
let d50=self.e500_c(t1.begin()).diag;
d2=self.diag_join(d2,d50);
let d51=self.diag_fail(t1.position::<false>(),"$");
let d52=self.diag_rule(121,d51);
d2=self.diag_join(d2,d52);
let mut d53=Diag::NONE;
let d54=self.diag_fail(t1.position::<false>(),"call");
d53=self.diag_join(d53,d54);
let d55=self.diag_fail(t1.begin().position::<false>(),"internal");
d53=self.diag_join(d53,d55);
let d56=self.diag_rule(35,d53);
let d57=self.diag_rule(36,d56);
d2=self.diag_join(d2,d57);
let d58=self.diag_fail(t1.position::<false>(),"(");
d2=self.diag_join(d2,d58);
let d59=self.diag_rule(60,d2);
let d60=self.diag_rule(41,d59);
let d61=self.diag_rule(40,d60);
self.display_failures(t1.consumed.max(t1.matched),&["'('", "'('", "'match'", "'match'", "'if'", "'if'", "'sin'", "'sin'", "'cos'", "'cos'", "'tan'", "'tan'", "'sqrt'", "'sqrt'", "'min'", "'min'", "'max'", "'max'", "'random'", "'random'", "'abs'", "'abs'", "'round'", "'round'", "'ceil'", "'ceil'", "'floor'", "'floor'", "'pow'", "'pow'", "'log'", "'log'", "'exp'", "'exp'", "'toNum'", "'toNum'", "'$'", "'$'", "'$'", "'$'", "'len'", "'len'", "'length'", "'length'", "'external'", "'external'", "'$'", "'$'", "'call'", "'call'", "'call'", "'internal'", "'('", "'('", "'('", "'?'", "':'", "')'", "'match'", "'{'", "','", "'}'", "'if'", "'else'", "'toNum'", "'.length'", "'len'", "'length'", "'external'", "'$'", "__CaptureSite", "NumberTermParser", "__CaptureSite"]);
if t1.begin().consumed.max(t1.begin().matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'('", "'?'", "':'", "')'", "'match'", "'{'", "','", "'}'", "'if'", "'('", "')'", "'{'", "'}'", "'else'", "'sin'", "'('", "')'", "'cos'", "'tan'", "'sqrt'", "'min'", "'max'", "'random'", "'abs'", "'round'", "'ceil'", "'floor'", "'pow'", "','", "'log'", "'exp'", "'toNum'", "'('", "','", "')'", "'.length'", "'('", "')'", "'len'", "'('", "')'", "'length'", "'('", "')'", "'external'", "'('", "')'", "'$'", "'call'", "'internal'", "'internal'", "'internal'", "'internal'", "'('", "')'", "'('", "')'"]);}
if t1.begin().begin().consumed.max(t1.begin().begin().matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'sin'", "'('", "')'", "'cos'", "'('", "')'", "'tan'", "'('", "')'", "'sqrt'", "'('", "')'", "'min'", "'('", "')'", "'max'", "'('", "')'", "'random'", "'('", "')'", "'abs'", "'('", "')'", "'round'", "'('", "')'", "'ceil'", "'('", "')'", "'floor'", "'('", "')'", "'pow'", "'('", "','", "')'", "'log'", "'('", "')'", "'exp'", "'('", "')'"]);}
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["NumberExpressionParser", "__CaptureSite"]);}
Step {ok:false,state,events:EventId(0),diag:d61}
}
fn guard_e921_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e921_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"external");
let d3=self.diag_rule(34,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'external'", "'external'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'external'", "'object'", "':'", "'('", "')'", "'('", "')'", "'external'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e922_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e922_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"$");
let d3=self.diag_rule(121,d2);
self.display_failures(t1.consumed.max(t1.matched),&["'$'", "'$'"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'$'", "'$'"]);}
Step {ok:false,state,events:EventId(0),diag:d3}
}
fn guard_e923_c(&mut self,state:State)->Step {
if !self.can_replay(2) {return self.e923_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let mut d2=Diag::NONE;
let d3=self.diag_fail(t1.position::<false>(),"call");
d2=self.diag_join(d2,d3);
let d4=self.diag_fail(t1.begin().position::<false>(),"internal");
d2=self.diag_join(d2,d4);
let d5=self.diag_rule(35,d2);
let d6=self.diag_rule(36,d5);
self.display_failures(t1.consumed.max(t1.matched),&["'call'", "'call'", "'call'", "'internal'"]);
if t1.begin().consumed.max(t1.begin().matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'call'", "'internal'", "'internal'", "'internal'"]);}
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'internal'", "'('", "')'", "'('", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d6}
}
fn guard_e937_c(&mut self,state:State)->Step {
if !self.can_replay(6) {return self.e937_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let mut d2=Diag::NONE;
let d3=self.diag_fail(t1.position::<false>(),"(");
let d4=self.diag_rule(108,d3);
d2=self.diag_join(d2,d4);
let d5=self.diag_fail(t1.position::<false>(),"match");
let d6=self.diag_rule(109,d5);
d2=self.diag_join(d2,d6);
let d7=self.diag_fail(t1.position::<false>(),"if");
let d8=self.diag_rule(106,d7);
d2=self.diag_join(d2,d8);
let mut d9=Diag::NONE;
let d10=self.diag_fail(t1.position::<false>(),"sin");
let d11=self.diag_rule(45,d10);
d9=self.diag_join(d9,d11);
let d12=self.diag_fail(t1.position::<false>(),"cos");
let d13=self.diag_rule(46,d12);
d9=self.diag_join(d9,d13);
let d14=self.diag_fail(t1.position::<false>(),"tan");
let d15=self.diag_rule(47,d14);
d9=self.diag_join(d9,d15);
let d16=self.diag_fail(t1.position::<false>(),"sqrt");
let d17=self.diag_rule(48,d16);
d9=self.diag_join(d9,d17);
let d18=self.diag_fail(t1.position::<false>(),"min");
let d19=self.diag_rule(49,d18);
d9=self.diag_join(d9,d19);
let d20=self.diag_fail(t1.position::<false>(),"max");
let d21=self.diag_rule(50,d20);
d9=self.diag_join(d9,d21);
let d22=self.diag_fail(t1.position::<false>(),"random");
let d23=self.diag_rule(51,d22);
d9=self.diag_join(d9,d23);
let d24=self.diag_fail(t1.position::<false>(),"abs");
let d25=self.diag_rule(52,d24);
d9=self.diag_join(d9,d25);
let d26=self.diag_fail(t1.position::<false>(),"round");
let d27=self.diag_rule(53,d26);
d9=self.diag_join(d9,d27);
let d28=self.diag_fail(t1.position::<false>(),"ceil");
let d29=self.diag_rule(54,d28);
d9=self.diag_join(d9,d29);
let d30=self.diag_fail(t1.position::<false>(),"floor");
let d31=self.diag_rule(55,d30);
d9=self.diag_join(d9,d31);
let d32=self.diag_fail(t1.position::<false>(),"pow");
let d33=self.diag_rule(56,d32);
d9=self.diag_join(d9,d33);
let d34=self.diag_fail(t1.position::<false>(),"log");
let d35=self.diag_rule(57,d34);
d9=self.diag_join(d9,d35);
let d36=self.diag_fail(t1.position::<false>(),"exp");
let d37=self.diag_rule(58,d36);
d9=self.diag_join(d9,d37);
let d38=self.diag_rule(44,d9);
d2=self.diag_join(d2,d38);
let d39=self.diag_fail(t1.position::<false>(),"toNum");
let d40=self.diag_rule(59,d39);
d2=self.diag_join(d2,d40);
let d41=self.diag_fail(t1.position::<false>(),"$");
let d42=self.diag_rule(121,d41);
let d43=self.diag_rule(69,d42);
d2=self.diag_join(d2,d43);
let d44=self.diag_fail(t1.position::<false>(),"len");
let d45=self.diag_rule(65,d44);
d2=self.diag_join(d2,d45);
let d46=self.diag_fail(t1.position::<false>(),"length");
let d47=self.diag_rule(64,d46);
d2=self.diag_join(d2,d47);
let d48=self.diag_fail(t1.position::<false>(),"external");
let d49=self.diag_rule(32,d48);
d2=self.diag_join(d2,d49);
let d50=self.e500_c(t1.begin()).diag;
d2=self.diag_join(d2,d50);
let d51=self.diag_fail(t1.position::<false>(),"$");
let d52=self.diag_rule(121,d51);
d2=self.diag_join(d2,d52);
let mut d53=Diag::NONE;
let d54=self.diag_fail(t1.position::<false>(),"call");
d53=self.diag_join(d53,d54);
let d55=self.diag_fail(t1.begin().position::<false>(),"internal");
d53=self.diag_join(d53,d55);
let d56=self.diag_rule(35,d53);
let d57=self.diag_rule(36,d56);
d2=self.diag_join(d2,d57);
let d58=self.diag_fail(t1.position::<false>(),"(");
d2=self.diag_join(d2,d58);
let d59=self.diag_rule(60,d2);
let d60=self.diag_rule(41,d59);
let d61=self.diag_rule(40,d60);
let d62=self.diag_rule(103,d61);
self.display_failures(t1.consumed.max(t1.matched),&["'('", "'('", "'match'", "'match'", "'if'", "'if'", "'sin'", "'sin'", "'cos'", "'cos'", "'tan'", "'tan'", "'sqrt'", "'sqrt'", "'min'", "'min'", "'max'", "'max'", "'random'", "'random'", "'abs'", "'abs'", "'round'", "'round'", "'ceil'", "'ceil'", "'floor'", "'floor'", "'pow'", "'pow'", "'log'", "'log'", "'exp'", "'exp'", "'toNum'", "'toNum'", "'$'", "'$'", "'$'", "'$'", "'len'", "'len'", "'length'", "'length'", "'external'", "'external'", "'$'", "'$'", "'call'", "'call'", "'call'", "'internal'", "'('", "'('", "'('", "'?'", "':'", "')'", "'match'", "'{'", "','", "'}'", "'if'", "'else'", "'toNum'", "'.length'", "'len'", "'length'", "'external'", "'$'", "__CaptureSite", "NumberTermParser", "__CaptureSite", "NumberExpressionParser", "__CaptureSite"]);
if t1.begin().consumed.max(t1.begin().matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'('", "'?'", "':'", "')'", "'match'", "'{'", "','", "'}'", "'if'", "'('", "')'", "'{'", "'}'", "'else'", "'sin'", "'('", "')'", "'cos'", "'tan'", "'sqrt'", "'min'", "'max'", "'random'", "'abs'", "'round'", "'ceil'", "'floor'", "'pow'", "','", "'log'", "'exp'", "'toNum'", "'('", "','", "')'", "'.length'", "'('", "')'", "'len'", "'('", "')'", "'length'", "'('", "')'", "'external'", "'('", "')'", "'$'", "'call'", "'internal'", "'internal'", "'internal'", "'internal'", "'('", "')'", "'('", "')'"]);}
if t1.begin().begin().consumed.max(t1.begin().begin().matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'sin'", "'('", "')'", "'cos'", "'('", "')'", "'tan'", "'('", "')'", "'sqrt'", "'('", "')'", "'min'", "'('", "')'", "'max'", "'('", "')'", "'random'", "'('", "')'", "'abs'", "'('", "')'", "'round'", "'('", "')'", "'ceil'", "'('", "')'", "'floor'", "'('", "')'", "'pow'", "'('", "','", "')'", "'log'", "'('", "')'", "'exp'", "'('", "')'"]);}
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["ComparisonExpressionParser", "__CaptureSite"]);}
Step {ok:false,state,events:EventId(0),diag:d62}
}
fn guard_e940_c(&mut self,state:State)->Step {
if !self.can_replay(5) {return self.e940_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let mut d2=Diag::NONE;
let d3=self.diag_fail(t1.position::<false>(),"(");
let d4=self.diag_rule(108,d3);
d2=self.diag_join(d2,d4);
let d5=self.diag_fail(t1.position::<false>(),"match");
let d6=self.diag_rule(109,d5);
d2=self.diag_join(d2,d6);
let d7=self.diag_fail(t1.position::<false>(),"if");
let d8=self.diag_rule(106,d7);
d2=self.diag_join(d2,d8);
let mut d9=Diag::NONE;
let d10=self.diag_fail(t1.position::<false>(),"sin");
let d11=self.diag_rule(45,d10);
d9=self.diag_join(d9,d11);
let d12=self.diag_fail(t1.position::<false>(),"cos");
let d13=self.diag_rule(46,d12);
d9=self.diag_join(d9,d13);
let d14=self.diag_fail(t1.position::<false>(),"tan");
let d15=self.diag_rule(47,d14);
d9=self.diag_join(d9,d15);
let d16=self.diag_fail(t1.position::<false>(),"sqrt");
let d17=self.diag_rule(48,d16);
d9=self.diag_join(d9,d17);
let d18=self.diag_fail(t1.position::<false>(),"min");
let d19=self.diag_rule(49,d18);
d9=self.diag_join(d9,d19);
let d20=self.diag_fail(t1.position::<false>(),"max");
let d21=self.diag_rule(50,d20);
d9=self.diag_join(d9,d21);
let d22=self.diag_fail(t1.position::<false>(),"random");
let d23=self.diag_rule(51,d22);
d9=self.diag_join(d9,d23);
let d24=self.diag_fail(t1.position::<false>(),"abs");
let d25=self.diag_rule(52,d24);
d9=self.diag_join(d9,d25);
let d26=self.diag_fail(t1.position::<false>(),"round");
let d27=self.diag_rule(53,d26);
d9=self.diag_join(d9,d27);
let d28=self.diag_fail(t1.position::<false>(),"ceil");
let d29=self.diag_rule(54,d28);
d9=self.diag_join(d9,d29);
let d30=self.diag_fail(t1.position::<false>(),"floor");
let d31=self.diag_rule(55,d30);
d9=self.diag_join(d9,d31);
let d32=self.diag_fail(t1.position::<false>(),"pow");
let d33=self.diag_rule(56,d32);
d9=self.diag_join(d9,d33);
let d34=self.diag_fail(t1.position::<false>(),"log");
let d35=self.diag_rule(57,d34);
d9=self.diag_join(d9,d35);
let d36=self.diag_fail(t1.position::<false>(),"exp");
let d37=self.diag_rule(58,d36);
d9=self.diag_join(d9,d37);
let d38=self.diag_rule(44,d9);
d2=self.diag_join(d2,d38);
let d39=self.diag_fail(t1.position::<false>(),"toNum");
let d40=self.diag_rule(59,d39);
d2=self.diag_join(d2,d40);
let d41=self.diag_fail(t1.position::<false>(),"$");
let d42=self.diag_rule(121,d41);
let d43=self.diag_rule(69,d42);
d2=self.diag_join(d2,d43);
let d44=self.diag_fail(t1.position::<false>(),"len");
let d45=self.diag_rule(65,d44);
d2=self.diag_join(d2,d45);
let d46=self.diag_fail(t1.position::<false>(),"length");
let d47=self.diag_rule(64,d46);
d2=self.diag_join(d2,d47);
let d48=self.diag_fail(t1.position::<false>(),"external");
let d49=self.diag_rule(32,d48);
d2=self.diag_join(d2,d49);
let d50=self.e500_c(t1.begin()).diag;
d2=self.diag_join(d2,d50);
let d51=self.diag_fail(t1.position::<false>(),"$");
let d52=self.diag_rule(121,d51);
d2=self.diag_join(d2,d52);
let mut d53=Diag::NONE;
let d54=self.diag_fail(t1.position::<false>(),"call");
d53=self.diag_join(d53,d54);
let d55=self.diag_fail(t1.begin().position::<false>(),"internal");
d53=self.diag_join(d53,d55);
let d56=self.diag_rule(35,d53);
let d57=self.diag_rule(36,d56);
d2=self.diag_join(d2,d57);
let d58=self.diag_fail(t1.position::<false>(),"(");
d2=self.diag_join(d2,d58);
let d59=self.diag_rule(60,d2);
let d60=self.diag_rule(41,d59);
let d61=self.diag_rule(40,d60);
self.display_failures(t1.consumed.max(t1.matched),&["'('", "'('", "'match'", "'match'", "'if'", "'if'", "'sin'", "'sin'", "'cos'", "'cos'", "'tan'", "'tan'", "'sqrt'", "'sqrt'", "'min'", "'min'", "'max'", "'max'", "'random'", "'random'", "'abs'", "'abs'", "'round'", "'round'", "'ceil'", "'ceil'", "'floor'", "'floor'", "'pow'", "'pow'", "'log'", "'log'", "'exp'", "'exp'", "'toNum'", "'toNum'", "'$'", "'$'", "'$'", "'$'", "'len'", "'len'", "'length'", "'length'", "'external'", "'external'", "'$'", "'$'", "'call'", "'call'", "'call'", "'internal'", "'('", "'('", "'('", "'?'", "':'", "')'", "'match'", "'{'", "','", "'}'", "'if'", "'else'", "'toNum'", "'.length'", "'len'", "'length'", "'external'", "'$'", "__CaptureSite", "NumberTermParser", "__CaptureSite"]);
if t1.begin().consumed.max(t1.begin().matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'('", "'?'", "':'", "')'", "'match'", "'{'", "','", "'}'", "'if'", "'('", "')'", "'{'", "'}'", "'else'", "'sin'", "'('", "')'", "'cos'", "'tan'", "'sqrt'", "'min'", "'max'", "'random'", "'abs'", "'round'", "'ceil'", "'floor'", "'pow'", "','", "'log'", "'exp'", "'toNum'", "'('", "','", "')'", "'.length'", "'('", "')'", "'len'", "'('", "')'", "'length'", "'('", "')'", "'external'", "'('", "')'", "'$'", "'call'", "'internal'", "'internal'", "'internal'", "'internal'", "'('", "')'", "'('", "')'"]);}
if t1.begin().begin().consumed.max(t1.begin().begin().matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'sin'", "'('", "')'", "'cos'", "'('", "')'", "'tan'", "'('", "')'", "'sqrt'", "'('", "')'", "'min'", "'('", "')'", "'max'", "'('", "')'", "'random'", "'('", "')'", "'abs'", "'('", "')'", "'round'", "'('", "')'", "'ceil'", "'('", "')'", "'floor'", "'('", "')'", "'pow'", "'('", "','", "')'", "'log'", "'('", "')'", "'exp'", "'('", "')'"]);}
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["NumberExpressionParser", "__CaptureSite"]);}
Step {ok:false,state,events:EventId(0),diag:d61}
}
fn guard_e944_c(&mut self,state:State)->Step {
if !self.can_replay(2) {return self.e944_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let mut d2=Diag::NONE;
let d3=self.diag_fail(t1.position::<false>(),"call");
d2=self.diag_join(d2,d3);
let d4=self.diag_fail(t1.begin().position::<false>(),"internal");
d2=self.diag_join(d2,d4);
let d5=self.diag_rule(35,d2);
let d6=self.diag_rule(36,d5);
self.display_failures(t1.consumed.max(t1.matched),&["'call'", "'call'", "'call'", "'internal'"]);
if t1.begin().consumed.max(t1.begin().matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'call'", "'internal'", "'internal'", "'internal'"]);}
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'internal'", "'('", "')'", "'('", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d6}
}
fn guard_e1036_c(&mut self,state:State)->Step {
if !self.can_replay(5) {return self.e1036_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let mut d2=Diag::NONE;
let d3=self.diag_fail(t1.position::<false>(),"(");
let d4=self.diag_rule(108,d3);
d2=self.diag_join(d2,d4);
let d5=self.diag_fail(t1.position::<false>(),"match");
let d6=self.diag_rule(109,d5);
d2=self.diag_join(d2,d6);
let d7=self.diag_fail(t1.position::<false>(),"if");
let d8=self.diag_rule(106,d7);
d2=self.diag_join(d2,d8);
let mut d9=Diag::NONE;
let d10=self.diag_fail(t1.position::<false>(),"sin");
let d11=self.diag_rule(45,d10);
d9=self.diag_join(d9,d11);
let d12=self.diag_fail(t1.position::<false>(),"cos");
let d13=self.diag_rule(46,d12);
d9=self.diag_join(d9,d13);
let d14=self.diag_fail(t1.position::<false>(),"tan");
let d15=self.diag_rule(47,d14);
d9=self.diag_join(d9,d15);
let d16=self.diag_fail(t1.position::<false>(),"sqrt");
let d17=self.diag_rule(48,d16);
d9=self.diag_join(d9,d17);
let d18=self.diag_fail(t1.position::<false>(),"min");
let d19=self.diag_rule(49,d18);
d9=self.diag_join(d9,d19);
let d20=self.diag_fail(t1.position::<false>(),"max");
let d21=self.diag_rule(50,d20);
d9=self.diag_join(d9,d21);
let d22=self.diag_fail(t1.position::<false>(),"random");
let d23=self.diag_rule(51,d22);
d9=self.diag_join(d9,d23);
let d24=self.diag_fail(t1.position::<false>(),"abs");
let d25=self.diag_rule(52,d24);
d9=self.diag_join(d9,d25);
let d26=self.diag_fail(t1.position::<false>(),"round");
let d27=self.diag_rule(53,d26);
d9=self.diag_join(d9,d27);
let d28=self.diag_fail(t1.position::<false>(),"ceil");
let d29=self.diag_rule(54,d28);
d9=self.diag_join(d9,d29);
let d30=self.diag_fail(t1.position::<false>(),"floor");
let d31=self.diag_rule(55,d30);
d9=self.diag_join(d9,d31);
let d32=self.diag_fail(t1.position::<false>(),"pow");
let d33=self.diag_rule(56,d32);
d9=self.diag_join(d9,d33);
let d34=self.diag_fail(t1.position::<false>(),"log");
let d35=self.diag_rule(57,d34);
d9=self.diag_join(d9,d35);
let d36=self.diag_fail(t1.position::<false>(),"exp");
let d37=self.diag_rule(58,d36);
d9=self.diag_join(d9,d37);
let d38=self.diag_rule(44,d9);
d2=self.diag_join(d2,d38);
let d39=self.diag_fail(t1.position::<false>(),"toNum");
let d40=self.diag_rule(59,d39);
d2=self.diag_join(d2,d40);
let d41=self.diag_fail(t1.position::<false>(),"$");
let d42=self.diag_rule(121,d41);
let d43=self.diag_rule(69,d42);
d2=self.diag_join(d2,d43);
let d44=self.diag_fail(t1.position::<false>(),"len");
let d45=self.diag_rule(65,d44);
d2=self.diag_join(d2,d45);
let d46=self.diag_fail(t1.position::<false>(),"length");
let d47=self.diag_rule(64,d46);
d2=self.diag_join(d2,d47);
let d48=self.diag_fail(t1.position::<false>(),"external");
let d49=self.diag_rule(32,d48);
d2=self.diag_join(d2,d49);
let d50=self.e500_c(t1.begin()).diag;
d2=self.diag_join(d2,d50);
let d51=self.diag_fail(t1.position::<false>(),"$");
let d52=self.diag_rule(121,d51);
d2=self.diag_join(d2,d52);
let mut d53=Diag::NONE;
let d54=self.diag_fail(t1.position::<false>(),"call");
d53=self.diag_join(d53,d54);
let d55=self.diag_fail(t1.begin().position::<false>(),"internal");
d53=self.diag_join(d53,d55);
let d56=self.diag_rule(35,d53);
let d57=self.diag_rule(36,d56);
d2=self.diag_join(d2,d57);
let d58=self.diag_fail(t1.position::<false>(),"(");
d2=self.diag_join(d2,d58);
let d59=self.diag_rule(60,d2);
let d60=self.diag_rule(41,d59);
let d61=self.diag_rule(40,d60);
self.display_failures(t1.consumed.max(t1.matched),&["'('", "'('", "'match'", "'match'", "'if'", "'if'", "'sin'", "'sin'", "'cos'", "'cos'", "'tan'", "'tan'", "'sqrt'", "'sqrt'", "'min'", "'min'", "'max'", "'max'", "'random'", "'random'", "'abs'", "'abs'", "'round'", "'round'", "'ceil'", "'ceil'", "'floor'", "'floor'", "'pow'", "'pow'", "'log'", "'log'", "'exp'", "'exp'", "'toNum'", "'toNum'", "'$'", "'$'", "'$'", "'$'", "'len'", "'len'", "'length'", "'length'", "'external'", "'external'", "'$'", "'$'", "'call'", "'call'", "'call'", "'internal'", "'('", "'('", "'('", "'?'", "':'", "')'", "'match'", "'{'", "','", "'}'", "'if'", "'else'", "'toNum'", "'.length'", "'len'", "'length'", "'external'", "'$'", "__CaptureSite", "NumberTermParser", "__CaptureSite"]);
if t1.begin().consumed.max(t1.begin().matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'('", "'?'", "':'", "')'", "'match'", "'{'", "','", "'}'", "'if'", "'('", "')'", "'{'", "'}'", "'else'", "'sin'", "'('", "')'", "'cos'", "'tan'", "'sqrt'", "'min'", "'max'", "'random'", "'abs'", "'round'", "'ceil'", "'floor'", "'pow'", "','", "'log'", "'exp'", "'toNum'", "'('", "','", "')'", "'.length'", "'('", "')'", "'len'", "'('", "')'", "'length'", "'('", "')'", "'external'", "'('", "')'", "'$'", "'call'", "'internal'", "'internal'", "'internal'", "'internal'", "'('", "')'", "'('", "')'"]);}
if t1.begin().begin().consumed.max(t1.begin().begin().matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'sin'", "'('", "')'", "'cos'", "'('", "')'", "'tan'", "'('", "')'", "'sqrt'", "'('", "')'", "'min'", "'('", "')'", "'max'", "'('", "')'", "'random'", "'('", "')'", "'abs'", "'('", "')'", "'round'", "'('", "')'", "'ceil'", "'('", "')'", "'floor'", "'('", "')'", "'pow'", "'('", "','", "')'", "'log'", "'('", "')'", "'exp'", "'('", "')'"]);}
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["NumberExpressionParser", "__CaptureSite"]);}
Step {ok:false,state,events:EventId(0),diag:d61}
}
fn guard_e1040_c(&mut self,state:State)->Step {
if !self.can_replay(2) {return self.e1040_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let mut d2=Diag::NONE;
let d3=self.diag_fail(t1.position::<false>(),"call");
d2=self.diag_join(d2,d3);
let d4=self.diag_fail(t1.begin().position::<false>(),"internal");
d2=self.diag_join(d2,d4);
let d5=self.diag_rule(35,d2);
let d6=self.diag_rule(36,d5);
self.display_failures(t1.consumed.max(t1.matched),&["'call'", "'call'", "'call'", "'internal'"]);
if t1.begin().consumed.max(t1.begin().matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'call'", "'internal'", "'internal'", "'internal'"]);}
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'internal'", "'('", "')'", "'('", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d6}
}
fn guard_e1041_c(&mut self,state:State)->Step {
if !self.can_replay(0) {return self.e1041_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let t1=self.trivia_1::<false>(state.entered(true,true)).state;
let d2=self.diag_fail(t1.position::<false>(),"(");
self.display_failures(t1.consumed.max(t1.matched),&["'('", "'('"]);
if state.consumed.max(state.matched)==t1.consumed.max(t1.matched) {self.display_failures(t1.consumed.max(t1.matched),&["'('", "')'"]);}
Step {ok:false,state,events:EventId(0),diag:d2}
}
fn token_0<const MATCH:bool>(&mut self,state:State,label:&'static str,display:&'static str)->Step {let _=label;let out={
self.number::<MATCH>(state,label)
};if !out.ok {self.display_fail(state.consumed.max(state.matched),display);}out}
fn token_1<const MATCH:bool>(&mut self,state:State,label:&'static str,display:&'static str)->Step {let _=label;let out={
self.identifier::<MATCH>(state,label)
};if !out.ok {self.display_fail(state.consumed.max(state.matched),display);}out}
fn token_2<const MATCH:bool>(&mut self,state:State,label:&'static str,display:&'static str)->Step {let _=label;let out={
self.scan::<MATCH>(state,"TinyExpressionP4::STRING",label)
};let _=display;out}
fn token_3<const MATCH:bool>(&mut self,state:State,label:&'static str,display:&'static str)->Step {let _=label;let out={
self.scan::<MATCH>(state,"TinyExpressionP4::CODE_START",label)
};let _=display;out}
fn token_4<const MATCH:bool>(&mut self,state:State,label:&'static str,display:&'static str)->Step {let _=label;let out={
self.until::<MATCH>(state,"```","'```'")
};if !out.ok {self.display_fail(state.consumed.max(state.matched),display);}out}
fn token_5<const MATCH:bool>(&mut self,state:State,label:&'static str,display:&'static str)->Step {let _=label;let out={
self.scan::<MATCH>(state,"TinyExpressionP4::CODE_END",label)
};let _=display;out}
fn token_6<const MATCH:bool>(&mut self,state:State,label:&'static str,display:&'static str)->Step {let _=label;let out={
self.eof::<MATCH>(state,label)
};if !out.ok {self.display_fail(state.consumed.max(state.matched),display);}out}
fn trivia_0<const MATCH:bool>(&mut self,state:State)->Step {
self.display_reach(state.consumed.max(state.matched));let mut out=Step::yes(state);loop {let before=out.state.position::<MATCH>();
if !matches!(self.text.as_bytes().get(before),Some(9|10|11|12|13|32|47)) {self.display_failures(out.state.consumed.max(out.state.matched),&["' '", "'//'", "'/*'", "'*/'"]);break;}
{let start=out.state.position::<MATCH>();let mut p=start;
while let Some((c,n))=self.input.cp_at(p) {if ![9, 10, 11, 12, 13, 32].contains(&(c as u64)) {break;}p+=n;}
if p>start {out.state.advance::<MATCH>(p-start);continue;}
self.display_fail(out.state.consumed.max(out.state.matched),"' '");
}
{let start=out.state.position::<MATCH>();let mut p=start;
if self.input.starts_with(p,"//") {p+=2;while let Some((c,n))=self.input.cp_at(p) {if matches!(c,'\n'|'\r') {break;}p+=n;}let end=if self.input.starts_with(p,"\r\n") {p+2} else {self.input.cp_at(p).map_or(p,|(_,n)|p+n)};out.state.advance::<MATCH>(p-start);if !MATCH {out.state.matched=end;}continue;}
self.display_fail(out.state.consumed.max(out.state.matched),"'//'");
}
{let start=out.state.position::<MATCH>();let mut p=start;
if self.input.starts_with(p,"/*") {let body=p+2;if let Some(n)=self.text[body..].find("*/") {p=body+n+2;out.state.advance::<MATCH>(p-start);continue;}}
if self.input.starts_with(start,"/*") {self.display_fail(self.text.len(),"'*/'");self.display_fail(self.text.len(),"WildCardStringParser");} else {self.display_fail(out.state.consumed.max(out.state.matched),"'/*'");self.display_fail(out.state.consumed.max(out.state.matched),"'*/'");}
}
if out.state.position::<MATCH>()==before {break;}}
self.display_reach(out.state.consumed.max(out.state.matched));out}
fn skip_0<const MATCH:bool>(&self,state:State)->State {let origin=state.position::<MATCH>();
if self.lex_enabled {if let Some((dc,dm))=self.trivia_skip.get(0,MATCH,origin) {
if dc==u32::MAX {return state;}
let mut hit=state;if MATCH {hit.matched=origin+dm as usize;} else {hit.consumed=origin+dc as usize;hit.matched=origin+dm as usize;}
return hit;}}
let mut out=state;loop {let before=out.position::<MATCH>();
if !matches!(self.text.as_bytes().get(before),Some(9|10|11|12|13|32|47)) {break;}
{let start=out.position::<MATCH>();let mut p=start;
while let Some((c,n))=self.input.cp_at(p) {if ![9, 10, 11, 12, 13, 32].contains(&(c as u64)) {break;}p+=n;}
if p>start {out.advance::<MATCH>(p-start);continue;}
}
{let start=out.position::<MATCH>();let mut p=start;
if self.input.starts_with(p,"//") {p+=2;while let Some((c,n))=self.input.cp_at(p) {if matches!(c,'\n'|'\r') {break;}p+=n;}let end=if self.input.starts_with(p,"\r\n") {p+2} else {self.input.cp_at(p).map_or(p,|(_,n)|p+n)};out.advance::<MATCH>(p-start);if !MATCH {out.matched=end;}continue;}
}
{let start=out.position::<MATCH>();let mut p=start;
if self.input.starts_with(p,"/*") {let body=p+2;if let Some(n)=self.text[body..].find("*/") {p=body+n+2;out.advance::<MATCH>(p-start);continue;}}
}
if out.position::<MATCH>()==before {break;}}
if self.lex_enabled {
if out.position::<MATCH>()==origin {self.trivia_skip.insert(0,MATCH,origin,(u32::MAX,0));}
else if let (Ok(dc),Ok(dm))=(u32::try_from(out.consumed.saturating_sub(origin)),u32::try_from(out.matched.saturating_sub(origin))) {
if dc!=u32::MAX {self.trivia_skip.insert(0,MATCH,origin,(dc,dm));}}}
out}
fn trivia_1<const MATCH:bool>(&mut self,state:State)->Step {
self.display_reach(state.consumed.max(state.matched));let mut out=Step::yes(state);loop {let before=out.state.position::<MATCH>();
if !matches!(self.text.as_bytes().get(before),Some(9|10|11|12|13|32|47)) {self.display_failures(out.state.consumed.max(out.state.matched),&["' '", "'//'", "'/*'", "'*/'"]);break;}
{let start=out.state.position::<MATCH>();let mut p=start;
while let Some((c,n))=self.input.cp_at(p) {if ![9, 10, 11, 12, 13, 32].contains(&(c as u64)) {break;}p+=n;}
if p>start {out.state.advance::<MATCH>(p-start);continue;}
self.display_fail(out.state.consumed.max(out.state.matched),"' '");
}
{let start=out.state.position::<MATCH>();let mut p=start;
if self.input.starts_with(p,"//") {p+=2;while let Some((c,n))=self.input.cp_at(p) {if matches!(c,'\n'|'\r') {break;}p+=n;}let end=if self.input.starts_with(p,"\r\n") {p+2} else {self.input.cp_at(p).map_or(p,|(_,n)|p+n)};out.state.advance::<MATCH>(p-start);if !MATCH {out.state.matched=end;}continue;}
self.display_fail(out.state.consumed.max(out.state.matched),"'//'");
}
{let start=out.state.position::<MATCH>();let mut p=start;
if self.input.starts_with(p,"/*") {let body=p+2;if let Some(n)=self.text[body..].find("*/") {p=body+n+2;out.state.advance::<MATCH>(p-start);continue;}}
if self.input.starts_with(start,"/*") {self.display_fail(self.text.len(),"'*/'");self.display_fail(self.text.len(),"WildCardStringParser");} else {self.display_fail(out.state.consumed.max(out.state.matched),"'/*'");self.display_fail(out.state.consumed.max(out.state.matched),"'*/'");}
}
if out.state.position::<MATCH>()==before {break;}}
self.display_reach(out.state.consumed.max(out.state.matched));out}
fn skip_1<const MATCH:bool>(&self,state:State)->State {let origin=state.position::<MATCH>();
if self.lex_enabled {if let Some((dc,dm))=self.trivia_skip.get(0,MATCH,origin) {
if dc==u32::MAX {return state;}
let mut hit=state;if MATCH {hit.matched=origin+dm as usize;} else {hit.consumed=origin+dc as usize;hit.matched=origin+dm as usize;}
return hit;}}
let mut out=state;loop {let before=out.position::<MATCH>();
if !matches!(self.text.as_bytes().get(before),Some(9|10|11|12|13|32|47)) {break;}
{let start=out.position::<MATCH>();let mut p=start;
while let Some((c,n))=self.input.cp_at(p) {if ![9, 10, 11, 12, 13, 32].contains(&(c as u64)) {break;}p+=n;}
if p>start {out.advance::<MATCH>(p-start);continue;}
}
{let start=out.position::<MATCH>();let mut p=start;
if self.input.starts_with(p,"//") {p+=2;while let Some((c,n))=self.input.cp_at(p) {if matches!(c,'\n'|'\r') {break;}p+=n;}let end=if self.input.starts_with(p,"\r\n") {p+2} else {self.input.cp_at(p).map_or(p,|(_,n)|p+n)};out.advance::<MATCH>(p-start);if !MATCH {out.matched=end;}continue;}
}
{let start=out.position::<MATCH>();let mut p=start;
if self.input.starts_with(p,"/*") {let body=p+2;if let Some(n)=self.text[body..].find("*/") {p=body+n+2;out.advance::<MATCH>(p-start);continue;}}
}
if out.position::<MATCH>()==before {break;}}
if self.lex_enabled {
if out.position::<MATCH>()==origin {self.trivia_skip.insert(0,MATCH,origin,(u32::MAX,0));}
else if let (Ok(dc),Ok(dm))=(u32::try_from(out.consumed.saturating_sub(origin)),u32::try_from(out.matched.saturating_sub(origin))) {
if dc!=u32::MAX {self.trivia_skip.insert(0,MATCH,origin,(dc,dm));}}}
out}
}

// ---- vendored addition: rust/scripts/regenerate-ubnfc.sh ----
// Derived from parse_entry_with_options above by the same textual substitution
// ubnfc examples/p4-rust/build.rs performs. Do not edit by hand.
pub fn parse_entry_with_scanner(grammar:&str,entry:Option<&str>,text:&str,options:ParseOptions,scanner:&mut dyn TokenScanner)->Result<ParseResult,&'static str> {
let (result,stack_exhausted)=parse_entry_budget(grammar,entry,text,options,&mut *scanner,NATIVE_STACK_SENTINEL_BYTES)?;
if result.ok || !stack_exhausted {return Ok(result);}
Ok(result)
}