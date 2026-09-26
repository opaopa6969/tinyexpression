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
("FormulaInfo",None)=>parser.r0_c(State::default()),
("FormulaInfo",Some("Document"))=>parser.r0_c(State::default()),
("FormulaInfo",Some("Block"))=>parser.r1_c(State::default()),
("FormulaInfo",Some("Filler"))=>parser.r2_c(State::default()),
("FormulaInfo",Some("CommentLine"))=>parser.r3_c(State::default()),
("FormulaInfo",Some("BlankLine"))=>parser.r4_c(State::default()),
("FormulaInfo",Some("SpaceChar"))=>parser.r5_c(State::default()),
("FormulaInfo",Some("Entry"))=>parser.r6_c(State::default()),
("FormulaInfo",Some("Value"))=>parser.r7_c(State::default()),
("FormulaInfo",Some("ContinuationLine"))=>parser.r8_c(State::default()),
("FormulaInfo",Some("PlainLine"))=>parser.r9_c(State::default()),
("FormulaInfo",Some("AtLineEnd"))=>parser.r10_c(State::default()),
("FormulaInfo",Some("EndOfPart"))=>parser.r11_c(State::default()),
("FormulaInfo",Some("BlankChar"))=>parser.r12_c(State::default()),
("FormulaInfo",Some("LineBreak"))=>parser.r13_c(State::default()),
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
/// D-072: 観測を残さない終端領域の走査。消費 byte 数を返す。
#[allow(unused_mut)]
fn s26(&self,mut p:usize)->Option<usize> {
let start=p;
p+=self.s27(p)?;
p+=self.s28(p)?;
Some(p-start)
}
/// D-072: 観測を残さない終端領域の走査。消費 byte 数を返す。
#[allow(unused_mut)]
fn s27(&self,mut p:usize)->Option<usize> {
if self.input.starts_with(p,"#") {Some(1)} else {None}
}
/// D-072: 観測を残さない終端領域の走査。消費 byte 数を返す。
#[allow(unused_mut)]
fn s28(&self,mut p:usize)->Option<usize> {
let start=p;
let bytes=self.text.as_bytes();while let Some(&b)=bytes.get(p) {
if b<0x80 {if (if b<64 {0xffffffffffffdbffu64>>b} else {0xffffffffffffffffu64>>(b-64)})&1==0 {break;}p+=1;}
else {let Some((c,n))=self.input.cp_at(p) else {break;};if !{let cp=c as u32;if cp<64 {(0xffffffffffffdbffu64>>cp)&1!=0} else if cp<128 {(0xffffffffffffffffu64>>(cp-64))&1!=0} else {matches!(cp,128..=1114111)}} {break;}p+=n;}
}
Some(p-start)
}
/// D-072: 観測を残さない終端領域の走査。消費 byte 数を返す。
#[allow(unused_mut)]
fn s29(&self,mut p:usize)->Option<usize> {
match self.input.cp_at(p) {Some((c,n)) if {!"\r\n".contains(c)}=>Some(n),_=>None}
}
/// D-072: 観測を残さない終端領域の走査。消費 byte 数を返す。
#[allow(unused_mut)]
fn s37(&self,mut p:usize)->Option<usize> {
let start=p;
p+=self.s38(p)?;
Some(p-start)
}
/// D-072: 観測を残さない終端領域の走査。消費 byte 数を返す。
#[allow(unused_mut)]
fn s38(&self,mut p:usize)->Option<usize> {
let start=p;
let bytes=self.text.as_bytes();while let Some(&b)=bytes.get(p) {
if b<0x80 {if (if b<64 {0x100001a00u64>>b} else {0x0u64>>(b-64)})&1==0 {break;}p+=1;}
else {let Some((c,n))=self.input.cp_at(p) else {break;};if !{let cp=c as u32;if cp<64 {(0x100001a00u64>>cp)&1!=0} else if cp<128 {(0x0u64>>(cp-64))&1!=0} else {false}} {break;}p+=n;}
}
Some(p-start)
}
/// D-072: 観測を残さない終端領域の走査。消費 byte 数を返す。
#[allow(unused_mut)]
fn s39(&self,mut p:usize)->Option<usize> {
self.s48(p)
}
/// D-072: 観測を残さない終端領域の走査。消費 byte 数を返す。
#[allow(unused_mut)]
fn s43(&self,mut p:usize)->Option<usize> {
let start=p;
p+=self.s44(p)?;
p+=self.s45(p)?;
Some(p-start)
}
/// D-072: 観測を残さない終端領域の走査。消費 byte 数を返す。
#[allow(unused_mut)]
fn s44(&self,mut p:usize)->Option<usize> {
self.s48(p)
}
/// D-072: 観測を残さない終端領域の走査。消費 byte 数を返す。
#[allow(unused_mut)]
fn s45(&self,mut p:usize)->Option<usize> {
let start=p;
let bytes=self.text.as_bytes();while let Some(&b)=bytes.get(p) {
if b<0x80 {if (if b<64 {0x100001a00u64>>b} else {0x0u64>>(b-64)})&1==0 {break;}p+=1;}
else {let Some((c,n))=self.input.cp_at(p) else {break;};if !{let cp=c as u32;if cp<64 {(0x100001a00u64>>cp)&1!=0} else if cp<128 {(0x0u64>>(cp-64))&1!=0} else {false}} {break;}p+=n;}
}
Some(p-start)
}
/// D-072: 観測を残さない終端領域の走査。消費 byte 数を返す。
#[allow(unused_mut)]
fn s46(&self,mut p:usize)->Option<usize> {
self.s48(p)
}
/// D-072: 観測を残さない終端領域の走査。消費 byte 数を返す。
#[allow(unused_mut)]
fn s48(&self,mut p:usize)->Option<usize> {
if let Some(n)=self.s49(p) {return Some(n);}
if let Some(n)=self.s50(p) {return Some(n);}
if let Some(n)=self.s51(p) {return Some(n);}
None
}
/// D-072: 観測を残さない終端領域の走査。消費 byte 数を返す。
#[allow(unused_mut)]
fn s49(&self,mut p:usize)->Option<usize> {
if self.input.starts_with(p," ") {Some(1)} else {None}
}
/// D-072: 観測を残さない終端領域の走査。消費 byte 数を返す。
#[allow(unused_mut)]
fn s50(&self,mut p:usize)->Option<usize> {
if self.input.starts_with(p,"\t") {Some(1)} else {None}
}
/// D-072: 観測を残さない終端領域の走査。消費 byte 数を返す。
#[allow(unused_mut)]
fn s51(&self,mut p:usize)->Option<usize> {
match self.input.cp_at(p) {Some((c,n)) if {(11..=12).contains(&(c as u64))}=>Some(n),_=>None}
}
/// D-072: 観測を残さない終端領域の走査。消費 byte 数を返す。
#[allow(unused_mut)]
fn s65(&self,mut p:usize)->Option<usize> {
let start=p;
let bytes=self.text.as_bytes();while let Some(&b)=bytes.get(p) {
if b<0x80 {if (if b<64 {0xffffffffffffdbffu64>>b} else {0xffffffffffffffffu64>>(b-64)})&1==0 {break;}p+=1;}
else {let Some((c,n))=self.input.cp_at(p) else {break;};if !{let cp=c as u32;if cp<64 {(0xffffffffffffdbffu64>>cp)&1!=0} else if cp<128 {(0xffffffffffffffffu64>>(cp-64))&1!=0} else {matches!(cp,128..=1114111)}} {break;}p+=n;}
}
Some(p-start)
}
/// D-072: 観測を残さない終端領域の走査。消費 byte 数を返す。
#[allow(unused_mut)]
fn s66(&self,mut p:usize)->Option<usize> {
match self.input.cp_at(p) {Some((c,n)) if {!"\r\n".contains(c)}=>Some(n),_=>None}
}
/// D-072: 観測を残さない終端領域の走査。消費 byte 数を返す。
#[allow(unused_mut)]
fn s77(&self,mut p:usize)->Option<usize> {
let start=p;
p+=self.s78(p)?;
p+=self.s79(p)?;
Some(p-start)
}
/// D-072: 観測を残さない終端領域の走査。消費 byte 数を返す。
#[allow(unused_mut)]
fn s78(&self,mut p:usize)->Option<usize> {
match self.input.cp_at(p) {Some((c,n)) if {!"\r\nABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz_".contains(c)}=>Some(n),_=>None}
}
/// D-072: 観測を残さない終端領域の走査。消費 byte 数を返す。
#[allow(unused_mut)]
fn s79(&self,mut p:usize)->Option<usize> {
let start=p;
let bytes=self.text.as_bytes();while let Some(&b)=bytes.get(p) {
if b<0x80 {if (if b<64 {0xffffffffffffdbffu64>>b} else {0xffffffffffffffffu64>>(b-64)})&1==0 {break;}p+=1;}
else {let Some((c,n))=self.input.cp_at(p) else {break;};if !{let cp=c as u32;if cp<64 {(0xffffffffffffdbffu64>>cp)&1!=0} else if cp<128 {(0xffffffffffffffffu64>>(cp-64))&1!=0} else {matches!(cp,128..=1114111)}} {break;}p+=n;}
}
Some(p-start)
}
/// D-072: 観測を残さない終端領域の走査。消費 byte 数を返す。
#[allow(unused_mut)]
fn s80(&self,mut p:usize)->Option<usize> {
match self.input.cp_at(p) {Some((c,n)) if {!"\r\n".contains(c)}=>Some(n),_=>None}
}
/// D-072: 観測を残さない終端領域の走査。消費 byte 数を返す。
#[allow(unused_mut)]
fn s89(&self,mut p:usize)->Option<usize> {
let start=p;
p+=self.s90(p)?;
p+=self.s91(p)?;
Some(p-start)
}
/// D-072: 観測を残さない終端領域の走査。消費 byte 数を返す。
#[allow(unused_mut)]
fn s90(&self,mut p:usize)->Option<usize> {
match self.input.cp_at(p) {Some((c,n)) if {!":\r\n".contains(c)}=>Some(n),_=>None}
}
/// D-072: 観測を残さない終端領域の走査。消費 byte 数を返す。
#[allow(unused_mut)]
fn s91(&self,mut p:usize)->Option<usize> {
let start=p;
let bytes=self.text.as_bytes();while let Some(&b)=bytes.get(p) {
if b<0x80 {if (if b<64 {0xffffffffffffdbffu64>>b} else {0xffffffffffffffffu64>>(b-64)})&1==0 {break;}p+=1;}
else {let Some((c,n))=self.input.cp_at(p) else {break;};if !{let cp=c as u32;if cp<64 {(0xffffffffffffdbffu64>>cp)&1!=0} else if cp<128 {(0xffffffffffffffffu64>>(cp-64))&1!=0} else {matches!(cp,128..=1114111)}} {break;}p+=n;}
}
Some(p-start)
}
/// D-072: 観測を残さない終端領域の走査。消費 byte 数を返す。
#[allow(unused_mut)]
fn s92(&self,mut p:usize)->Option<usize> {
match self.input.cp_at(p) {Some((c,n)) if {!"\r\n".contains(c)}=>Some(n),_=>None}
}
/// D-072: 観測を残さない終端領域の走査。消費 byte 数を返す。
#[allow(unused_mut)]
fn s101(&self,mut p:usize)->Option<usize> {
let start=p;
let bytes=self.text.as_bytes();while let Some(&b)=bytes.get(p) {
if b<0x80 {if (if b<64 {0x100000200u64>>b} else {0x0u64>>(b-64)})&1==0 {break;}p+=1;}
else {let Some((c,n))=self.input.cp_at(p) else {break;};if !{let cp=c as u32;if cp<64 {(0x100000200u64>>cp)&1!=0} else if cp<128 {(0x0u64>>(cp-64))&1!=0} else {false}} {break;}p+=n;}
}
Some(p-start)
}
/// D-072: 観測を残さない終端領域の走査。消費 byte 数を返す。
#[allow(unused_mut)]
fn s102(&self,mut p:usize)->Option<usize> {
self.s107(p)
}
/// D-072: 観測を残さない終端領域の走査。消費 byte 数を返す。
#[allow(unused_mut)]
fn s107(&self,mut p:usize)->Option<usize> {
if let Some(n)=self.s108(p) {return Some(n);}
if let Some(n)=self.s109(p) {return Some(n);}
None
}
/// D-072: 観測を残さない終端領域の走査。消費 byte 数を返す。
#[allow(unused_mut)]
fn s108(&self,mut p:usize)->Option<usize> {
if self.input.starts_with(p," ") {Some(1)} else {None}
}
/// D-072: 観測を残さない終端領域の走査。消費 byte 数を返す。
#[allow(unused_mut)]
fn s109(&self,mut p:usize)->Option<usize> {
if self.input.starts_with(p,"\t") {Some(1)} else {None}
}
fn r0_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e0_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:0,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["DocumentParser"]);}
self.depth-=1;out.diag=self.diag_rule(0,out.diag);out
}
fn r1_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e4_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:1,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["BlockParser"]);}
self.depth-=1;out.diag=self.diag_rule(1,out.diag);out
}
fn r2_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e21_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:2,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["FillerParser"]);}
self.depth-=1;out.diag=self.diag_rule(2,out.diag);out
}
fn r3_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e24_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:3,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["CommentLineParser"]);}
self.depth-=1;out.diag=self.diag_rule(3,out.diag);out
}
fn r4_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e34_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:4,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["BlankLineParser"]);}
self.depth-=1;out.diag=self.diag_rule(4,out.diag);out
}
fn r5_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e48_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:5,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["' '", "'\t'"]);}
self.depth-=1;out.diag=self.diag_rule(5,out.diag);out
}
fn r6_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e52_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:6,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["':'"]);}
self.depth-=1;out.diag=self.diag_rule(6,out.diag);out
}
fn r7_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e62_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:7,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["ValueParser"]);}
self.depth-=1;out.diag=self.diag_rule(7,out.diag);out
}
fn r8_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e73_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:8,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'---END_OF_PART---'"]);}
self.depth-=1;out.diag=self.diag_rule(8,out.diag);out
}
fn r9_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e76_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:9,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["PlainLineParser"]);}
self.depth-=1;out.diag=self.diag_rule(9,out.diag);out
}
fn r10_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e95_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:10,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'\r'", "'\n'"]);}
self.depth-=1;out.diag=self.diag_rule(10,out.diag);out
}
fn r11_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e99_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:11,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'---END_OF_PART---'"]);}
self.depth-=1;out.diag=self.diag_rule(11,out.diag);out
}
fn r12_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e107_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:12,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["' '", "'\t'"]);}
self.depth-=1;out.diag=self.diag_rule(12,out.diag);out
}
fn r13_c(&mut self,state:State)->Step { if let Some(limit)=self.enter_rule(state) {return limit;}self.statistics.rule_evaluations+=1;let mark=self.mark();
let mut out=self.e110_c(state);
if out.ok {
let end=out.state.consumed;
out.events=self.event(Event::Rule {rule:13,span:[state.consumed,end],child:out.events,caps:(0,0)});
} else { self.restore(mark); }
if !out.ok {self.display_failures(state.consumed.max(state.matched),&["'\r\n'", "'\r'", "'\n'"]);}
self.depth-=1;out.diag=self.diag_rule(13,out.diag);out
}
fn e0_c(&mut self,state:State)->Step {
let mut out=self.b0_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b0_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let child=self.e1_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let child=self.e3_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
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
let mut out=self.b3_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,3,0);
} else {self.display_failures(state.consumed.max(state.matched),&["EndOfSourceParser"]);}
out
}
fn b3_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.token_0::<false>(out.state,"EOF","EndOfSourceParser");
out
}
fn e4_c(&mut self,state:State)->Step {
let mut out=self.b4_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b4_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let child=self.e5_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let child=self.e17_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e5_c(&mut self,state:State)->Step {
let mut out=self.b5_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.display_failures(state.consumed.max(state.matched),&["BlockGroup0Parser"]);}
out
}
fn b5_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.e6_c(out.state);
out
}
fn e6_c(&mut self,state:State)->Step {
let mut out=self.b6_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["BlockGroup0Parser"]);}
out
}
fn b6_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out='choice: {let mark=self.mark();let mut diagnostic=Diag::NONE;let mut best:Option<(Step,[usize;2])>=None;
let c0=self.input.cp_at(out.state.begin().position::<false>()).map(|(c,_)|c);
self.restore(mark);let mut child=self.e7_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(c0.is_some_and(|c| matches!(c as u32,65..=90|95|97..=122))) {self.guard_e13_c(out.state.begin())} else {self.e13_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);break 'choice if let Some((mut child,effects))=best {self.replay_effects(effects);child.diag=diagnostic;child} else {out.ok=false;out.diag=diagnostic;out};
};
out
}
fn e7_c(&mut self,state:State)->Step {
let mut out=self.b7_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b7_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let child=self.e8_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let child=self.e9_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let child=self.e11_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e8_c(&mut self,state:State)->Step {
let mut out=self.b8_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:1,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b8_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r2_c(out.state);
out
}
fn e9_c(&mut self,state:State)->Step {
let mut out=self.b9_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.display_failures(state.consumed.max(state.matched),&["Repeat"]);}
out
}
fn b9_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e10_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
}
let _=count;
if false {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e10_c(&mut self,state:State)->Step {
let mut out=self.b10_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:2,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b10_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r2_c(out.state);
out
}
fn e11_c(&mut self,state:State)->Step {
let mut out=self.b11_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.display_failures(state.consumed.max(state.matched),&["Repeat"]);}
out
}
fn b11_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e12_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
}
let _=count;
if false {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e12_c(&mut self,state:State)->Step {
let mut out=self.b12_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:3,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["':'"]);}
out
}
fn b12_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r6_c(out.state);
out
}
fn e13_c(&mut self,state:State)->Step {
let mut out=self.b13_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b13_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let child=self.e14_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let child=self.e15_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e14_c(&mut self,state:State)->Step {
let mut out=self.b14_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:4,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["':'"]);}
out
}
fn b14_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r6_c(out.state);
out
}
fn e15_c(&mut self,state:State)->Step {
let mut out=self.b15_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:false});
} else {self.display_failures(state.consumed.max(state.matched),&["Repeat"]);}
out
}
fn b15_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e16_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
}
let _=count;
if false {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e16_c(&mut self,state:State)->Step {
let mut out=self.b16_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:5,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["':'"]);}
out
}
fn b16_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r6_c(out.state);
out
}
fn e17_c(&mut self,state:State)->Step {
let mut out=self.b17_c(state);
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.display_failures(state.consumed.max(state.matched),&["BlockGroup1Parser"]);}
out
}
fn b17_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.e18_c(out.state);
out
}
fn e18_c(&mut self,state:State)->Step {
let mut out=self.b18_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["BlockGroup1Parser"]);}
out
}
fn b18_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out='choice: {let mark=self.mark();let mut diagnostic=Diag::NONE;let mut best:Option<(Step,[usize;2])>=None;
let c0=self.input.cp_at(out.state.begin().position::<false>()).map(|(c,_)|c);
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(c0.is_some_and(|c| matches!(c as u32,45))) {self.guard_e19_c(out.state.begin())} else {self.e19_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e20_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);break 'choice if let Some((mut child,effects))=best {self.replay_effects(effects);child.diag=diagnostic;child} else {out.ok=false;out.diag=diagnostic;out};
};
out
}
fn e19_c(&mut self,state:State)->Step {
let mut out=self.b19_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:6,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b19_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r11_c(out.state);
out
}
fn e20_c(&mut self,state:State)->Step {
let mut out=self.b20_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,20,1);
} else {self.display_failures(state.consumed.max(state.matched),&["EndOfSourceParser"]);}
out
}
fn b20_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.token_0::<false>(out.state,"EOF","EndOfSourceParser");
out
}
fn e21_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:21,state,matched_mode:false,version:0};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b21_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b21_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out='choice: {let mark=self.mark();let mut diagnostic=Diag::NONE;let mut best:Option<(Step,[usize;2])>=None;
let c0=self.input.cp_at(out.state.begin().position::<false>()).map(|(c,_)|c);
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(c0.is_some_and(|c| matches!(c as u32,35))) {self.guard_e22_c(out.state.begin())} else {self.e22_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e23_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);break 'choice if let Some((mut child,effects))=best {self.replay_effects(effects);child.diag=diagnostic;child} else {out.ok=false;out.diag=diagnostic;out};
};
out
}
fn e22_c(&mut self,state:State)->Step {
let mut out=self.b22_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b22_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r3_c(out.state);
out
}
fn e23_c(&mut self,state:State)->Step {
let mut out=self.b23_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b23_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r4_c(out.state);
out
}
fn e24_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:24,state,matched_mode:false,version:0};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b24_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b24_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let child=self.e25_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let child=self.e30_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e25_c(&mut self,state:State)->Step {
let mut out=self.b25_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:7,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["'#'"]);}
out
}
fn b25_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.e26_c(out.state);
out
}
fn e26_c(&mut self,state:State)->Step {
let mut out=self.b26_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'#'"]);}
out
}
fn b26_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
if self.options.scan && !DIAG && !self.options.lexical && !self.options.occurrences && !out.state.invert {
let start=out.state.consumed;
match self.s26(start) {
Some(n)=>{out.state.advance::<false>(n);if n>0 {out.events=self.event(Event::Token{text_span:None,content_span:None,expr:usize::MAX,rule:usize::MAX,span:[start,start+n]});}}
None=>{out.ok=false;out.state=state;out.diag=self.diag_fail(start,"expression");}
}
} else {
let child=self.e27_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let child=self.e28_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e27_c(&mut self,state:State)->Step {
let mut out=self.b27_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,27,3);
} else {self.display_failures(state.consumed.max(state.matched),&["'#'"]);}
out
}
fn b27_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"#",true,5,false,"#");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'#'");}
out
}
fn e28_c(&mut self,state:State)->Step {
let mut out=self.b28_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["Repeat"]);}
out
}
fn b28_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e29_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
}
let _=count;
if false {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e29_c(&mut self,state:State)->Step {
let mut out=self.b29_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,29,3);
} else {self.display_failures(state.consumed.max(state.matched),&["LINE_CHARParser"]);}
out
}
fn b29_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.token_2::<false>(out.state,"LINE_CHAR","LINE_CHARParser");
out
}
fn e30_c(&mut self,state:State)->Step {
let mut out=self.b30_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'\n'", "'\r\n'", "'\r'"]);}
out
}
fn b30_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.e31_c(out.state);
out
}
fn e31_c(&mut self,state:State)->Step {
let mut out=self.b31_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'\n'", "'\r\n'", "'\r'"]);}
out
}
fn b31_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out='choice: {let mark=self.mark();let mut diagnostic=Diag::NONE;let mut best:Option<(Step,[usize;2])>=None;
let c0=self.input.cp_at(out.state.begin().position::<false>()).map(|(c,_)|c);
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(c0.is_some_and(|c| matches!(c as u32,10|13))) {self.guard_e32_c(out.state.begin())} else {self.e32_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e33_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);break 'choice if let Some((mut child,effects))=best {self.replay_effects(effects);child.diag=diagnostic;child} else {out.ok=false;out.diag=diagnostic;out};
};
out
}
fn e32_c(&mut self,state:State)->Step {
let mut out=self.b32_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b32_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r13_c(out.state);
out
}
fn e33_c(&mut self,state:State)->Step {
let mut out=self.b33_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,33,3);
} else {self.display_failures(state.consumed.max(state.matched),&["EndOfSourceParser"]);}
out
}
fn b33_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.token_0::<false>(out.state,"EOF","EndOfSourceParser");
out
}
fn e34_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:34,state,matched_mode:false,version:0};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b34_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b34_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out='choice: {let mark=self.mark();let mut diagnostic=Diag::NONE;let mut best:Option<(Step,[usize;2])>=None;
let c0=self.input.cp_at(out.state.begin().position::<false>()).map(|(c,_)|c);
self.restore(mark);let mut child=self.e35_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(c0.is_some_and(|c| matches!(c as u32,9|11..=12|32))) {self.guard_e41_c(out.state.begin())} else {self.e41_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);break 'choice if let Some((mut child,effects))=best {self.replay_effects(effects);child.diag=diagnostic;child} else {out.ok=false;out.diag=diagnostic;out};
};
out
}
fn e35_c(&mut self,state:State)->Step {
let mut out=self.b35_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'\n'", "'\r\n'", "'\r'"]);}
out
}
fn b35_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let child=self.e36_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let child=self.e40_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e36_c(&mut self,state:State)->Step {
let mut out=self.b36_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:8,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["BlankLineGroup0Parser", "__CaptureSite"]);}
out
}
fn b36_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.e37_c(out.state);
out
}
fn e37_c(&mut self,state:State)->Step {
let mut out=self.b37_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["BlankLineGroup0Parser"]);}
out
}
fn b37_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
if self.options.scan && !DIAG && !self.options.lexical && !self.options.occurrences && !out.state.invert {
let start=out.state.consumed;
match self.s37(start) {
Some(n)=>{out.state.advance::<false>(n);if n>0 {out.events=self.event(Event::Token{text_span:None,content_span:None,expr:usize::MAX,rule:usize::MAX,span:[start,start+n]});}}
None=>{out.ok=false;out.state=state;out.diag=self.diag_fail(start,"expression");}
}
} else {
let child=self.e38_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e38_c(&mut self,state:State)->Step {
let mut out=self.b38_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'\t'", "' '"]);}
out
}
fn b38_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e39_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
}
let _=count;
if false {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
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
out=self.r5_c(out.state);
out
}
fn e40_c(&mut self,state:State)->Step {
let mut out=self.b40_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b40_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r13_c(out.state);
out
}
fn e41_c(&mut self,state:State)->Step {
let mut out=self.b41_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b41_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let child=self.e42_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let child=self.e47_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e42_c(&mut self,state:State)->Step {
let mut out=self.b42_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:9,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["'\t'", "' '", "__CaptureSite"]);}
out
}
fn b42_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.e43_c(out.state);
out
}
fn e43_c(&mut self,state:State)->Step {
let mut out=self.b43_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'\t'", "' '"]);}
out
}
fn b43_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
if self.options.scan && !DIAG && !self.options.lexical && !self.options.occurrences && !out.state.invert {
let start=out.state.consumed;
match self.s43(start) {
Some(n)=>{out.state.advance::<false>(n);if n>0 {out.events=self.event(Event::Token{text_span:None,content_span:None,expr:usize::MAX,rule:usize::MAX,span:[start,start+n]});}}
None=>{out.ok=false;out.state=state;out.diag=self.diag_fail(start,"expression");}
}
} else {
let child=self.e44_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let child=self.e45_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e44_c(&mut self,state:State)->Step {
let mut out=self.b44_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b44_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r5_c(out.state);
out
}
fn e45_c(&mut self,state:State)->Step {
let mut out=self.b45_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'\t'", "' '"]);}
out
}
fn b45_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e46_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
}
let _=count;
if false {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e46_c(&mut self,state:State)->Step {
let mut out=self.b46_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b46_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r5_c(out.state);
out
}
fn e47_c(&mut self,state:State)->Step {
let mut out=self.b47_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,47,4);
} else {self.display_failures(state.consumed.max(state.matched),&["EndOfSourceParser"]);}
out
}
fn b47_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.token_0::<false>(out.state,"EOF","EndOfSourceParser");
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
out='choice: {let mark=self.mark();let mut diagnostic=Diag::NONE;let mut best:Option<(Step,[usize;2])>=None;
self.restore(mark);let mut child=self.e49_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e50_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e51_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);break 'choice if let Some((mut child,effects))=best {self.replay_effects(effects);child.diag=diagnostic;child} else {out.ok=false;out.diag=diagnostic;out};
};
out
}
fn e49_c(&mut self,state:State)->Step {
let mut out=self.b49_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,49,5);
} else {self.display_failures(state.consumed.max(state.matched),&["' '"]);}
out
}
fn b49_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state," ",true,4,false," ");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"' '");}
out
}
fn e50_c(&mut self,state:State)->Step {
let mut out=self.b50_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,50,5);
} else {self.display_failures(state.consumed.max(state.matched),&["'\t'"]);}
out
}
fn b50_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"\t",true,0,false,"\t");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'\t'");}
out
}
fn e51_c(&mut self,state:State)->Step {
let mut out=self.b51_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,51,5);
} else {self.display_failures(state.consumed.max(state.matched),&["VT_FFParser"]);}
out
}
fn b51_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.token_5::<false>(out.state,"VT_FF","VT_FFParser");
out
}
fn e52_c(&mut self,state:State)->Step {
let mark=self.mark();
let key=Key {expression:52,state,matched_mode:false,version:0};if let Some(hit)=self.lookup(key) {return hit;}
self.display_enter(state.consumed.max(state.matched));let mut out=self.b52_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.event(Event::Values{span:[state.consumed,out.state.consumed],child:out.events,wrap:true});
} else {self.restore(mark);self.display_failures(state.consumed.max(state.matched),&[]);}
if (out.ok && true) || (!out.ok && true) {self.store(key,out,mark);}self.display_leave();
out
}
fn b52_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let child=self.e53_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let child=self.e60_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let child=self.e61_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e53_c(&mut self,state:State)->Step {
let mut out=self.b53_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:10,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["EntryGroup0Parser", "__CaptureSite"]);}
out
}
fn b53_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.e54_c(out.state);
out
}
fn e54_c(&mut self,state:State)->Step {
let mut out=self.b54_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["EntryGroup0Parser"]);}
out
}
fn b54_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let child=self.e55_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let child=self.e56_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e55_c(&mut self,state:State)->Step {
let mut out=self.b55_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,55,6);
} else {self.display_failures(state.consumed.max(state.matched),&["IdentifierParser"]);}
out
}
fn b55_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.token_1::<false>(out.state,"IDENT","IdentifierParser");
out
}
fn e56_c(&mut self,state:State)->Step {
let mut out=self.b56_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'.'"]);}
out
}
fn b56_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e57_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
}
let _=count;
if false {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e57_c(&mut self,state:State)->Step {
let mut out=self.b57_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'.'"]);}
out
}
fn b57_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let child=self.e58_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let child=self.e59_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e58_c(&mut self,state:State)->Step {
let mut out=self.b58_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,58,6);
} else {self.display_failures(state.consumed.max(state.matched),&["'.'"]);}
out
}
fn b58_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,".",true,7,false,".");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'.'");}
out
}
fn e59_c(&mut self,state:State)->Step {
let mut out=self.b59_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,59,6);
} else {self.display_failures(state.consumed.max(state.matched),&["IdentifierParser"]);}
out
}
fn b59_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.token_1::<false>(out.state,"IDENT","IdentifierParser");
out
}
fn e60_c(&mut self,state:State)->Step {
let mut out=self.b60_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,60,6);
} else {self.display_failures(state.consumed.max(state.matched),&["':'"]);}
out
}
fn b60_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,":",true,8,false,":");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"':'");}
out
}
fn e61_c(&mut self,state:State)->Step {
let mut out=self.b61_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:11,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["__CaptureSite"]);}
out
}
fn b61_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r7_c(out.state);
out
}
fn e62_c(&mut self,state:State)->Step {
let mut out=self.b62_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b62_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let child=self.e63_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e63_c(&mut self,state:State)->Step {
let mut out=self.b63_c(state);
if out.ok {
out.events=self.event(Event::Capture {site:12,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["ValueGroup0Parser", "__CaptureSite"]);}
out
}
fn b63_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.e64_c(out.state);
out
}
fn e64_c(&mut self,state:State)->Step {
let mut out=self.b64_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["ValueGroup0Parser"]);}
out
}
fn b64_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let child=self.e65_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let child=self.e67_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let child=self.e71_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e65_c(&mut self,state:State)->Step {
let mut out=self.b65_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["Repeat"]);}
out
}
fn b65_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
if self.options.scan && !DIAG && !self.options.lexical && !self.options.occurrences && !out.state.invert {
let start=out.state.consumed;
match self.s65(start) {
Some(n)=>{out.state.advance::<false>(n);if n>0 {out.events=self.event(Event::Token{text_span:None,content_span:None,expr:usize::MAX,rule:usize::MAX,span:[start,start+n]});}}
None=>{out.ok=false;out.state=state;out.diag=self.diag_fail(start,"expression");}
}
} else {
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e66_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
}
let _=count;
if false {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e66_c(&mut self,state:State)->Step {
let mut out=self.b66_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,66,7);
} else {self.display_failures(state.consumed.max(state.matched),&["LINE_CHARParser"]);}
out
}
fn b66_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.token_2::<false>(out.state,"LINE_CHAR","LINE_CHARParser");
out
}
fn e67_c(&mut self,state:State)->Step {
let mut out=self.b67_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["Repeat"]);}
out
}
fn b67_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e68_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
}
let _=count;
if false {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e68_c(&mut self,state:State)->Step {
let mut out=self.b68_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'\n'", "'\r\n'", "'\r'"]);}
out
}
fn b68_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let child=self.e69_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let child=self.e70_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e69_c(&mut self,state:State)->Step {
let mut out=self.b69_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b69_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r13_c(out.state);
out
}
fn e70_c(&mut self,state:State)->Step {
let mut out=self.b70_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b70_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r8_c(out.state);
out
}
fn e71_c(&mut self,state:State)->Step {
let mut out=self.b71_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'\n'", "'\r\n'", "'\r'"]);}
out
}
fn b71_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e72_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
if count>=1 {break;}
}
let _=count;
if count>1 {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e72_c(&mut self,state:State)->Step {
let mut out=self.b72_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b72_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r13_c(out.state);
out
}
fn e73_c(&mut self,state:State)->Step {
let mut out=self.b73_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b73_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let child=self.e74_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let child=self.e75_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e74_c(&mut self,state:State)->Step {
let mut out=self.b74_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,74,8);
} else {self.display_failures(state.consumed.max(state.matched),&["'---END_OF_PART---'"]);}
out
}
fn b74_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.token_8::<false>(out.state,"NOT_END","'---END_OF_PART---'");
out
}
fn e75_c(&mut self,state:State)->Step {
let mut out=self.b75_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b75_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r9_c(out.state);
out
}
fn e76_c(&mut self,state:State)->Step {
let mut out=self.b76_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b76_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out='choice: {let mark=self.mark();let mut diagnostic=Diag::NONE;let mut best:Option<(Step,[usize;2])>=None;
let c0=self.input.cp_at(out.state.begin().position::<false>()).map(|(c,_)|c);
self.restore(mark);let mut child=self.e77_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(c0.is_some_and(|c| matches!(c as u32,65..=90|95|97..=122))) {self.guard_e81_c(out.state.begin())} else {self.e81_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e94_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);break 'choice if let Some((mut child,effects))=best {self.replay_effects(effects);child.diag=diagnostic;child} else {out.ok=false;out.diag=diagnostic;out};
};
out
}
fn e77_c(&mut self,state:State)->Step {
let mut out=self.b77_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b77_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
if self.options.scan && !DIAG && !self.options.lexical && !self.options.occurrences && !out.state.invert {
let start=out.state.consumed;
match self.s77(start) {
Some(n)=>{out.state.advance::<false>(n);if n>0 {out.events=self.event(Event::Token{text_span:None,content_span:None,expr:usize::MAX,rule:usize::MAX,span:[start,start+n]});}}
None=>{out.ok=false;out.state=state;out.diag=self.diag_fail(start,"expression");}
}
} else {
let child=self.e78_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let child=self.e79_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e78_c(&mut self,state:State)->Step {
let mut out=self.b78_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,78,9);
} else {self.display_failures(state.consumed.max(state.matched),&["NOT_ID_HEADParser"]);}
out
}
fn b78_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.token_4::<false>(out.state,"NOT_ID_HEAD","NOT_ID_HEADParser");
out
}
fn e79_c(&mut self,state:State)->Step {
let mut out=self.b79_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["Repeat"]);}
out
}
fn b79_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e80_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
}
let _=count;
if false {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e80_c(&mut self,state:State)->Step {
let mut out=self.b80_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,80,9);
} else {self.display_failures(state.consumed.max(state.matched),&["LINE_CHARParser"]);}
out
}
fn b80_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.token_2::<false>(out.state,"LINE_CHAR","LINE_CHARParser");
out
}
fn e81_c(&mut self,state:State)->Step {
let mut out=self.b81_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b81_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let child=self.e82_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let child=self.e83_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let child=self.e87_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e82_c(&mut self,state:State)->Step {
let mut out=self.b82_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,82,9);
} else {self.display_failures(state.consumed.max(state.matched),&["IdentifierParser"]);}
out
}
fn b82_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.token_1::<false>(out.state,"IDENT","IdentifierParser");
out
}
fn e83_c(&mut self,state:State)->Step {
let mut out=self.b83_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'.'"]);}
out
}
fn b83_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e84_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
}
let _=count;
if false {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e84_c(&mut self,state:State)->Step {
let mut out=self.b84_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'.'"]);}
out
}
fn b84_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let child=self.e85_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let child=self.e86_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e85_c(&mut self,state:State)->Step {
let mut out=self.b85_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,85,9);
} else {self.display_failures(state.consumed.max(state.matched),&["'.'"]);}
out
}
fn b85_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,".",true,7,false,".");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'.'");}
out
}
fn e86_c(&mut self,state:State)->Step {
let mut out=self.b86_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,86,9);
} else {self.display_failures(state.consumed.max(state.matched),&["IdentifierParser"]);}
out
}
fn b86_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.token_1::<false>(out.state,"IDENT","IdentifierParser");
out
}
fn e87_c(&mut self,state:State)->Step {
let mut out=self.b87_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["PlainLineGroup0Parser"]);}
out
}
fn b87_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.e88_c(out.state);
out
}
fn e88_c(&mut self,state:State)->Step {
let mut out=self.b88_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["PlainLineGroup0Parser"]);}
out
}
fn b88_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out='choice: {let mark=self.mark();let mut diagnostic=Diag::NONE;let mut best:Option<(Step,[usize;2])>=None;
self.restore(mark);let mut child=self.e89_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e93_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);break 'choice if let Some((mut child,effects))=best {self.replay_effects(effects);child.diag=diagnostic;child} else {out.ok=false;out.diag=diagnostic;out};
};
out
}
fn e89_c(&mut self,state:State)->Step {
let mut out=self.b89_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b89_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
if self.options.scan && !DIAG && !self.options.lexical && !self.options.occurrences && !out.state.invert {
let start=out.state.consumed;
match self.s89(start) {
Some(n)=>{out.state.advance::<false>(n);if n>0 {out.events=self.event(Event::Token{text_span:None,content_span:None,expr:usize::MAX,rule:usize::MAX,span:[start,start+n]});}}
None=>{out.ok=false;out.state=state;out.diag=self.diag_fail(start,"expression");}
}
} else {
let child=self.e90_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let child=self.e91_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e90_c(&mut self,state:State)->Step {
let mut out=self.b90_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,90,9);
} else {self.display_failures(state.consumed.max(state.matched),&["NOT_COLONParser"]);}
out
}
fn b90_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.token_3::<false>(out.state,"NOT_COLON","NOT_COLONParser");
out
}
fn e91_c(&mut self,state:State)->Step {
let mut out=self.b91_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["Repeat"]);}
out
}
fn b91_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e92_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
}
let _=count;
if false {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e92_c(&mut self,state:State)->Step {
let mut out=self.b92_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,92,9);
} else {self.display_failures(state.consumed.max(state.matched),&["LINE_CHARParser"]);}
out
}
fn b92_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.token_2::<false>(out.state,"LINE_CHAR","LINE_CHARParser");
out
}
fn e93_c(&mut self,state:State)->Step {
let mut out=self.b93_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b93_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r10_c(out.state);
out
}
fn e94_c(&mut self,state:State)->Step {
let mut out=self.b94_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b94_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r10_c(out.state);
out
}
fn e95_c(&mut self,state:State)->Step {
let mut out=self.b95_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b95_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out='choice: {let mark=self.mark();let mut diagnostic=Diag::NONE;let mut best:Option<(Step,[usize;2])>=None;
self.restore(mark);let mut child=self.e96_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e97_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e98_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);break 'choice if let Some((mut child,effects))=best {self.replay_effects(effects);child.diag=diagnostic;child} else {out.ok=false;out.diag=diagnostic;out};
};
out
}
fn e96_c(&mut self,state:State)->Step {
let mut out=self.b96_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,96,10);
} else {self.display_failures(state.consumed.max(state.matched),&["'\r'"]);}
out
}
fn b96_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.token_6::<false>(out.state,"AT_CR","'\r'");
out
}
fn e97_c(&mut self,state:State)->Step {
let mut out=self.b97_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,97,10);
} else {self.display_failures(state.consumed.max(state.matched),&["'\n'"]);}
out
}
fn b97_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.token_7::<false>(out.state,"AT_LF","'\n'");
out
}
fn e98_c(&mut self,state:State)->Step {
let mut out=self.b98_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,98,10);
} else {self.display_failures(state.consumed.max(state.matched),&["EndOfSourceParser"]);}
out
}
fn b98_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.token_0::<false>(out.state,"EOF","EndOfSourceParser");
out
}
fn e99_c(&mut self,state:State)->Step {
let mut out=self.b99_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b99_c(&mut self,mut state:State)->Step {
state.reset=false;
let mut out=Step::yes(state);
out.state=state.begin();
let child=self.e100_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let child=self.e101_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
let child=self.e103_c(out.state);out=self.combine(out,child);if !out.ok {out.state=state;return out;}
out.state=if out.ok {state.commit(out.state)} else {state};
out
}
fn e100_c(&mut self,state:State)->Step {
let mut out=self.b100_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,100,11);
out.events=self.event(Event::Capture {site:13,span:[state.consumed,out.state.consumed],child:out.events,token_extent:false});
} else {self.display_failures(state.consumed.max(state.matched),&["'---END_OF_PART---'"]);}
out
}
fn b100_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"---END_OF_PART---",true,6,false,"---END_OF_PART---");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'---END_OF_PART---'");}
out
}
fn e101_c(&mut self,state:State)->Step {
let mut out=self.b101_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'\t'", "' '"]);}
out
}
fn b101_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out.state=state.begin();
if self.options.scan && !DIAG && !self.options.lexical && !self.options.occurrences && !out.state.invert {
let start=out.state.consumed;
match self.s101(start) {
Some(n)=>{out.state.advance::<false>(n);if n>0 {out.events=self.event(Event::Token{text_span:None,content_span:None,expr:usize::MAX,rule:usize::MAX,span:[start,start+n]});}}
None=>{out.ok=false;out.state=state;out.diag=self.diag_fail(start,"expression");}
}
} else {
let mut count=0usize;loop {let mark=self.mark();let before=out.state;
let mut child=self.e102_c(out.state);out.diag=self.diag_join(out.diag,child.diag);out.state=child.state;if !child.ok {self.restore(mark);break;}
out.events=self.join(out.events,child.events);count+=1;
if before.position::<false>()==out.state.position::<false>() {break;}
}
let _=count;
if false {out.ok=false;out.state=state;let d=self.diag_fail(state.position::<false>(),"expression");out.diag=self.diag_join(out.diag,d);}
}
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
out=self.r12_c(out.state);
out
}
fn e103_c(&mut self,state:State)->Step {
let mut out=self.b103_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'\n'", "'\r\n'", "'\r'"]);}
out
}
fn b103_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.e104_c(out.state);
out
}
fn e104_c(&mut self,state:State)->Step {
let mut out=self.b104_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&["'\n'", "'\r\n'", "'\r'"]);}
out
}
fn b104_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out='choice: {let mark=self.mark();let mut diagnostic=Diag::NONE;let mut best:Option<(Step,[usize;2])>=None;
let c0=self.input.cp_at(out.state.begin().position::<false>()).map(|(c,_)|c);
self.restore(mark);let mut child=if self.options.predict && !out.state.invert && !(c0.is_some_and(|c| matches!(c as u32,10|13))) {self.guard_e105_c(out.state.begin())} else {self.e105_c(out.state.begin())};diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e106_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);break 'choice if let Some((mut child,effects))=best {self.replay_effects(effects);child.diag=diagnostic;child} else {out.ok=false;out.diag=diagnostic;out};
};
out
}
fn e105_c(&mut self,state:State)->Step {
let mut out=self.b105_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b105_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.r13_c(out.state);
out
}
fn e106_c(&mut self,state:State)->Step {
let mut out=self.b106_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,106,11);
} else {self.display_failures(state.consumed.max(state.matched),&["EndOfSourceParser"]);}
out
}
fn b106_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.token_0::<false>(out.state,"EOF","EndOfSourceParser");
out
}
fn e107_c(&mut self,state:State)->Step {
let mut out=self.b107_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b107_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out='choice: {let mark=self.mark();let mut diagnostic=Diag::NONE;let mut best:Option<(Step,[usize;2])>=None;
if !out.state.invert {let predicted=match self.input.cp_at(out.state.begin().position::<false>()).map(|(c,_)|c) {
Some(' ')=>0usize,
Some('\t')=>1usize,
_=>usize::MAX,};let candidate=match predicted {
0=>Some(self.e108_c(out.state.begin())),
1=>Some(self.e109_c(out.state.begin())),
_=>None,};
if !DIAG && self.options.predict && predicted==usize::MAX {self.restore(mark);out.ok=false;out.diag=diagnostic;break 'choice out;}
if let Some(mut child)=candidate {if child.ok {
if predicted>0 {let d=self.diag_fail(out.state.begin().position::<false>()," ");diagnostic=self.diag_join(diagnostic,d);}
if predicted>1 {let d=self.diag_fail(out.state.begin().position::<false>(),"\t");diagnostic=self.diag_join(diagnostic,d);}
child.state=out.state.commit(child.state);child.diag=self.diag_join(diagnostic,child.diag);break 'choice child;}}self.restore(mark); }
self.restore(mark);let mut child=self.e108_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e109_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);break 'choice if let Some((mut child,effects))=best {self.replay_effects(effects);child.diag=diagnostic;child} else {out.ok=false;out.diag=diagnostic;out};
};
out
}
fn e108_c(&mut self,state:State)->Step {
let mut out=self.b108_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,108,12);
} else {self.display_failures(state.consumed.max(state.matched),&["' '"]);}
out
}
fn b108_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state," ",true,4,false," ");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"' '");}
out
}
fn e109_c(&mut self,state:State)->Step {
let mut out=self.b109_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,109,12);
} else {self.display_failures(state.consumed.max(state.matched),&["'\t'"]);}
out
}
fn b109_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"\t",true,0,false,"\t");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'\t'");}
out
}
fn e110_c(&mut self,state:State)->Step {
let mut out=self.b110_c(state);
if out.ok {
} else {self.display_failures(state.consumed.max(state.matched),&[]);}
out
}
fn b110_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out='choice: {let mark=self.mark();let mut diagnostic=Diag::NONE;let mut best:Option<(Step,[usize;2])>=None;
self.restore(mark);let mut child=self.e111_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e112_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);let mut child=self.e113_c(out.state.begin());diagnostic=self.diag_join(diagnostic,child.diag);
if child.ok {child.state=out.state.commit(child.state);child.diag=diagnostic;break 'choice child;}
self.restore(mark);break 'choice if let Some((mut child,effects))=best {self.replay_effects(effects);child.diag=diagnostic;child} else {out.ok=false;out.diag=diagnostic;out};
};
out
}
fn e111_c(&mut self,state:State)->Step {
let mut out=self.b111_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,111,13);
} else {self.display_failures(state.consumed.max(state.matched),&["'\r\n'"]);}
out
}
fn b111_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"\r\n",true,3,false,"\r\n");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'\r\n'");}
out
}
fn e112_c(&mut self,state:State)->Step {
let mut out=self.b112_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,112,13);
} else {self.display_failures(state.consumed.max(state.matched),&["'\r'"]);}
out
}
fn b112_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"\r",true,2,false,"\r");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'\r'");}
out
}
fn e113_c(&mut self,state:State)->Step {
let mut out=self.b113_c(state);self.display_reach(out.state.consumed.max(out.state.matched));
if out.ok {
out.events=self.relabel_token(out.events,113,13);
} else {self.display_failures(state.consumed.max(state.matched),&["'\n'"]);}
out
}
fn b113_c(&mut self,mut state:State)->Step {
let mut out=Step::yes(state);
out=self.literal::<false>(out.state,"\n",true,1,false,"\n");
if !out.ok {self.display_fail(state.consumed.max(state.matched),"'\n'");}
out
}
fn guard_e13_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e13_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let d1=self.e55_c(state.entered(true,true).entered(true,true).entered(true,true)).diag;
self.display_failures(state.entered(true,true).entered(true,true).consumed.max(state.entered(true,true).entered(true,true).matched),&["EntryGroup0Parser", "EntryGroup0Parser", "__CaptureSite"]);
let d2=self.diag_rule(6,d1);
self.display_failures(state.entered(true,true).consumed.max(state.entered(true,true).matched),&["':'", "':'"]);
Step {ok:false,state,events:EventId(0),diag:d2}
}
fn guard_e19_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e19_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let d1=self.diag_fail(state.entered(true,true).position::<false>(),"---END_OF_PART---");
self.display_failures(state.entered(true,true).consumed.max(state.entered(true,true).matched),&["'---END_OF_PART---'", "'---END_OF_PART---'"]);
let d2=self.diag_rule(11,d1);
self.display_failures(state.consumed.max(state.matched),&["'---END_OF_PART---'", "__CaptureSite"]);
Step {ok:false,state,events:EventId(0),diag:d2}
}
fn guard_e22_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e22_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let d1=self.diag_fail(state.entered(true,true).entered(true,true).position::<false>(),"#");
self.display_failures(state.entered(true,true).entered(true,true).consumed.max(state.entered(true,true).entered(true,true).matched),&["'#'", "'#'"]);
self.display_failures(state.entered(true,true).consumed.max(state.entered(true,true).matched),&["'#'", "'#'"]);
let d2=self.diag_rule(3,d1);
self.display_failures(state.consumed.max(state.matched),&["CommentLineParser"]);
Step {ok:false,state,events:EventId(0),diag:d2}
}
fn guard_e32_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e32_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let mut d1=Diag::NONE;
let d2=self.diag_fail(state.begin().position::<false>(),"\r\n");
d1=self.diag_join(d1,d2);
let d3=self.diag_fail(state.begin().position::<false>(),"\r");
d1=self.diag_join(d1,d3);
let d4=self.diag_fail(state.begin().position::<false>(),"\n");
d1=self.diag_join(d1,d4);
self.display_failures(state.begin().consumed.max(state.begin().matched),&["'\r\n'", "'\r\n'", "'\r'", "'\r'", "'\n'", "'\n'"]);
let d5=self.diag_rule(13,d1);
self.display_failures(state.consumed.max(state.matched),&["'\r\n'", "'\r'", "'\n'"]);
Step {ok:false,state,events:EventId(0),diag:d5}
}
fn guard_e41_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e41_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let mut d1=Diag::NONE;
let d2=self.diag_fail(state.entered(true,true).entered(true,true).begin().position::<false>()," ");
d1=self.diag_join(d1,d2);
let d3=self.diag_fail(state.entered(true,true).entered(true,true).begin().position::<false>(),"\t");
d1=self.diag_join(d1,d3);
let d4=self.e51_c(state.entered(true,true).entered(true,true).begin()).diag;
d1=self.diag_join(d1,d4);
self.display_failures(state.entered(true,true).entered(true,true).begin().consumed.max(state.entered(true,true).entered(true,true).begin().matched),&["' '", "' '", "'\t'", "'\t'"]);
let d5=self.diag_rule(5,d1);
self.display_failures(state.entered(true,true).entered(true,true).consumed.max(state.entered(true,true).entered(true,true).matched),&["' '", "'\t'"]);
self.display_failures(state.entered(true,true).consumed.max(state.entered(true,true).matched),&["'\t'", "' '", "'\t'", "' '", "__CaptureSite"]);
Step {ok:false,state,events:EventId(0),diag:d5}
}
fn guard_e81_c(&mut self,state:State)->Step {
if !self.can_replay(0) {return self.e81_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let d1=self.e82_c(state.entered(true,true)).diag;
Step {ok:false,state,events:EventId(0),diag:d1}
}
fn guard_e105_c(&mut self,state:State)->Step {
if !self.can_replay(1) {return self.e105_c(state);}
if !DIAG {return Step {ok:false,state,events:EventId(0),diag:Diag::NONE};}
self.display_reach(state.consumed.max(state.matched));
let mut d1=Diag::NONE;
let d2=self.diag_fail(state.begin().position::<false>(),"\r\n");
d1=self.diag_join(d1,d2);
let d3=self.diag_fail(state.begin().position::<false>(),"\r");
d1=self.diag_join(d1,d3);
let d4=self.diag_fail(state.begin().position::<false>(),"\n");
d1=self.diag_join(d1,d4);
self.display_failures(state.begin().consumed.max(state.begin().matched),&["'\r\n'", "'\r\n'", "'\r'", "'\r'", "'\n'", "'\n'"]);
let d5=self.diag_rule(13,d1);
self.display_failures(state.consumed.max(state.matched),&["'\r\n'", "'\r'", "'\n'"]);
Step {ok:false,state,events:EventId(0),diag:d5}
}
fn token_0<const MATCH:bool>(&mut self,state:State,label:&'static str,display:&'static str)->Step {let _=label;let out={
self.eof::<MATCH>(state,label)
};if !out.ok {self.display_fail(state.consumed.max(state.matched),display);}out}
fn token_1<const MATCH:bool>(&mut self,state:State,label:&'static str,display:&'static str)->Step {let _=label;let out={
self.identifier::<MATCH>(state,label)
};if !out.ok {self.display_fail(state.consumed.max(state.matched),display);}out}
fn token_2<const MATCH:bool>(&mut self,state:State,label:&'static str,display:&'static str)->Step {let _=label;let out={
let length=self.input.cp_at(state.position::<MATCH>()).filter(|(c,_)|!"\r\n".contains(*c)).map(|(_,n)|n);self.primitive::<MATCH>(state,length,label)
};if !out.ok {self.display_fail(state.consumed.max(state.matched),display);}out}
fn token_3<const MATCH:bool>(&mut self,state:State,label:&'static str,display:&'static str)->Step {let _=label;let out={
let length=self.input.cp_at(state.position::<MATCH>()).filter(|(c,_)|!":\r\n".contains(*c)).map(|(_,n)|n);self.primitive::<MATCH>(state,length,label)
};if !out.ok {self.display_fail(state.consumed.max(state.matched),display);}out}
fn token_4<const MATCH:bool>(&mut self,state:State,label:&'static str,display:&'static str)->Step {let _=label;let out={
let length=self.input.cp_at(state.position::<MATCH>()).filter(|(c,_)|!"\r\nABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz_".contains(*c)).map(|(_,n)|n);self.primitive::<MATCH>(state,length,label)
};if !out.ok {self.display_fail(state.consumed.max(state.matched),display);}out}
fn token_5<const MATCH:bool>(&mut self,state:State,label:&'static str,display:&'static str)->Step {let _=label;let out={
let length=self.input.cp_at(state.position::<MATCH>()).filter(|(c,_)|(11..=12).contains(&(*c as u64))).map(|(_,n)|n);self.primitive::<MATCH>(state,length,label)
};if !out.ok {self.display_fail(state.consumed.max(state.matched),display);}out}
fn token_6<const MATCH:bool>(&mut self,state:State,label:&'static str,display:&'static str)->Step {let _=label;let out={
let child=self.literal::<true>(state.begin(),"\r",true,2,false,label);
Step {state:if child.ok {state.commit(child.state)} else {state},events:EventId(0),..child}
};if !out.ok {self.display_fail(state.consumed.max(state.matched),display);}out}
fn token_7<const MATCH:bool>(&mut self,state:State,label:&'static str,display:&'static str)->Step {let _=label;let out={
let child=self.literal::<true>(state.begin(),"\n",true,1,false,label);
Step {state:if child.ok {state.commit(child.state)} else {state},events:EventId(0),..child}
};if !out.ok {self.display_fail(state.consumed.max(state.matched),display);}out}
fn token_8<const MATCH:bool>(&mut self,state:State,label:&'static str,display:&'static str)->Step {let _=label;let out={
let child=self.literal::<true>(state.begin(),"---END_OF_PART---",true,6,false,label);
if child.ok {self.fail(state,state.matched,label)} else {Step::yes(state.commit(state.begin()))}
};if !out.ok {self.display_fail(state.consumed.max(state.matched),display);}out}
fn trivia_0<const MATCH:bool>(&mut self,state:State)->Step {
self.display_reach(state.consumed.max(state.matched));let mut out=Step::yes(state);loop {let before=out.state.position::<MATCH>();
if true {self.display_failures(out.state.consumed.max(out.state.matched),&[]);break;}
if out.state.position::<MATCH>()==before {break;}}
self.display_reach(out.state.consumed.max(out.state.matched));out}
fn skip_0<const MATCH:bool>(&self,state:State)->State {state}
}
