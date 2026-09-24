//! FormulaInfo documents: the block format of the Java loader
//! (`org.unlaxer.tinyexpression.loader`), read with a parser generated from
//! `grammar/formula-info.ubnf` (issue #180).
//!
//! Two layers, split where the Java loader splits them:
//!
//! - [`parse_document`] is the syntax layer: the vendored ubnfc parser
//!   (`generated::ubnfc_formula_info`) turns the text into blocks of `key:value` entries.
//!   It accepts exactly the documents `FormulaInfoSourceDocument.parse` accepts (the Java
//!   `FormulaInfoBlocksParser` over the whole input).
//! - [`load`] is the thin conversion the Java `FormulaInfoParser.extractFormulaInfo` performs
//!   on top of it: the `KeyValue` value normalisation, the known-key mapping, the
//!   `executionBackend` resolution, the MD5 `hash` refresh, the class name, the "must contain
//!   formula text" check, building the formula (here: [`Program::new`], the P4 semantics of
//!   the `runtime` module) and the `dependsOn` wiring — in the Java order, so the first error
//!   is the one Java raises. [`LoadError::java_exception`] names that Java exception.
//!
//! Deliberate differences from `FormulaInfoList.parse` are listed in the crate README
//! ("FormulaInfo loader"); the parity test is `tests/formula_info.rs`.

use std::fmt::{self, Display, Formatter};

use crate::diagnostic::{ParseDiagnostic, ParseError};
use crate::generated::ubnfc_formula_info::{
    self as generated, ast::Ast as Node, DiagnosticKind, ParseOptions, ParseResult,
};
use crate::runtime::{
    calculator_result, java, Context, EvalError, Host, NumberType, Options, Program, ResultType,
};
use crate::{json_string, Span, Value};

/// The end mark that closes a block (`FormulaInfo.END_MARK`).
pub const END_MARK: &str = "---END_OF_PART---";

// ------------------------------------------------------------------ syntax layer

/// A parsed FormulaInfo document.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Document {
    pub blocks: Vec<Block>,
}

/// One block: the entries before an end mark line (or the end of input).
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Block {
    pub span: Span,
    /// Comment and blank lines before the first entry, as source spans.
    pub leading: Vec<Span>,
    pub entries: Vec<Entry>,
    /// `true` when the block is closed by `---END_OF_PART---`, `false` at the end of input.
    pub terminated: bool,
}

/// One `key:value` entry.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Entry {
    pub key: String,
    pub span: Span,
    /// The raw value slice: the rest of the key line and the continuation lines, trailing line
    /// break included (the Java value token).
    pub value_span: Span,
    pub raw_value: String,
}

impl Entry {
    /// The value as Java's `FormulaInfoElementParser.extract` returns it: trailing whitespace
    /// stripped, lines starting with `#` and blank lines dropped.
    ///
    /// `None` for a zero-length value (a `key:` at the very end of the input), where Java's
    /// extraction fails with `NoSuchElementException`.
    pub fn value(&self) -> Option<String> {
        if self.raw_value.is_empty() {
            return None;
        }
        let stripped = strip_trailing(&self.raw_value);
        let kept: Vec<&str> = java_split(stripped, '\n')
            .into_iter()
            .filter(|line| !line.starts_with('#') && !java::strip(line).is_empty())
            .collect();
        Some(strip_trailing(&kept.join("\n")).to_owned())
    }
}

/// Parses a FormulaInfo document with the generated parser (syntax only; nothing is compiled).
pub fn parse_document(source: &str) -> Result<Document, ParseDiagnostic> {
    let result = generated::parse_with_options(source, ParseOptions::default());
    if !result.ok {
        return Err(diagnostic(&result, source));
    }
    let chars: Vec<char> = source.chars().collect();
    let text = |span: [usize; 2]| -> String { chars[span[0]..span[1]].iter().collect() };
    let Some(Node::g_FormulaInfoAST_2e_FormulaInfoDocument(document)) = result.ast else {
        return Err(unexpected_shape(source));
    };
    let mut blocks = Vec::with_capacity(document.g_blocks.len());
    for block in &document.g_blocks {
        let Node::g_FormulaInfoAST_2e_FormulaInfoBlock(block) = block else {
            return Err(unexpected_shape(source));
        };
        let mut entries = Vec::with_capacity(block.g_entries.len());
        for entry in &block.g_entries {
            let Node::g_FormulaInfoAST_2e_FormulaInfoEntry(entry) = entry else {
                return Err(unexpected_shape(source));
            };
            let Some(value_span) = entry.g_value.span() else {
                return Err(unexpected_shape(source));
            };
            entries.push(Entry {
                key: entry.g_key.clone(),
                span: span(entry.span),
                value_span: span(value_span),
                raw_value: text(value_span),
            });
        }
        blocks.push(Block {
            span: span(block.span),
            leading: block
                .g_leading
                .iter()
                .filter_map(Node::span)
                .map(span)
                .collect(),
            entries,
            terminated: block.g_end.is_some(),
        });
    }
    Ok(Document { blocks })
}

fn span(span: [usize; 2]) -> Span {
    Span {
        start: span[0],
        end: span[1],
    }
}

fn unexpected_shape(source: &str) -> ParseDiagnostic {
    let end = source.chars().count();
    ParseDiagnostic {
        kind: "mapping",
        offset: end,
        expected: Vec::new(),
        farthest: ParseError {
            offset: end,
            expected: Vec::new(),
        },
    }
}

fn diagnostic(result: &ParseResult, source: &str) -> ParseDiagnostic {
    let primary = result
        .diagnostics
        .iter()
        .find(|entry| {
            matches!(
                entry.kind,
                DiagnosticKind::Syntax | DiagnosticKind::TrailingInput
            )
        })
        .or_else(|| result.diagnostics.first());
    let owned = |values: &[&'static str]| values.iter().map(|v| (*v).to_owned()).collect();
    match primary {
        Some(entry) => ParseDiagnostic {
            kind: match entry.kind {
                DiagnosticKind::TrailingInput => "trailing_input",
                DiagnosticKind::Syntax => "syntax",
                DiagnosticKind::Mapping => "mapping",
                DiagnosticKind::Resource => "resource",
                DiagnosticKind::Recovery => "recovery",
            },
            offset: entry.offset_cp,
            expected: owned(&entry.expected),
            farthest: ParseError {
                offset: entry.farthest_cp,
                expected: owned(&entry.farthest_expected),
            },
        },
        None => ParseDiagnostic {
            kind: if result.consumed_cp < source.chars().count() {
                "trailing_input"
            } else {
                "syntax"
            },
            offset: result.consumed_cp,
            expected: Vec::new(),
            farthest: ParseError {
                offset: result.matched_cp.max(result.consumed_cp),
                expected: Vec::new(),
            },
        },
    }
}

// ------------------------------------------------------------------ loader

/// `org.unlaxer.tinyexpression.runtime.ExecutionBackend`.
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq, Hash)]
pub enum ExecutionBackend {
    #[default]
    JavaCode,
    JavaCodeLegacyAstCreator,
    AstEvaluator,
    DslJavaCode,
    P4AstEvaluator,
    P4DslJavaCode,
}

impl ExecutionBackend {
    /// The Java enum constant name.
    pub fn name(self) -> &'static str {
        match self {
            Self::JavaCode => "JAVA_CODE",
            Self::JavaCodeLegacyAstCreator => "JAVA_CODE_LEGACY_ASTCREATOR",
            Self::AstEvaluator => "AST_EVALUATOR",
            Self::DslJavaCode => "DSL_JAVA_CODE",
            Self::P4AstEvaluator => "P4_AST_EVALUATOR",
            Self::P4DslJavaCode => "P4_DSL_JAVA_CODE",
        }
    }

    /// `ExecutionBackend.parse`: trimmed, `-`/space as `_`, upper-cased, compared without `_`.
    pub fn parse(value: &str) -> Option<Self> {
        let normalized = java::trim(value);
        if normalized.is_empty() {
            return None;
        }
        let compact: String = normalized
            .to_uppercase()
            .chars()
            .filter(|c| !matches!(c, '-' | ' ' | '_'))
            .collect();
        Some(match compact.as_str() {
            "DSLJAVACODE" => Self::DslJavaCode,
            "JAVACODE" => Self::JavaCode,
            "JAVACODELEGACYASTCREATOR" | "LEGACYASTCREATOR" | "OOTCLEGACY" => {
                Self::JavaCodeLegacyAstCreator
            }
            "ASTEVALUATOR" => Self::AstEvaluator,
            "P4ASTEVALUATOR" => Self::P4AstEvaluator,
            "P4DSLJAVACODE" => Self::P4DslJavaCode,
            _ => return None,
        })
    }
}

/// `FormulaInfoAdditionalFields`: what the caller configures the Java loader with.
#[derive(Clone, Debug, Default)]
pub struct LoaderOptions {
    /// The multi-tenancy attribute name (Java tests: `siteId`); its value is
    /// [`FormulaInfo::multi_tenancy_id`].
    pub multi_tenancy_attribute: Option<String>,
    /// `addAttributeName`: keys that are always copied into [`FormulaInfo::extra`].
    pub additional_attributes: Vec<String>,
    /// The `nameExtractor`: the name is this extra attribute when present, else the
    /// `calculatorName` (Java tests: `checkKind`). `None` always uses `calculatorName`.
    pub name_attribute: Option<String>,
    /// The backend of a block without `executionBackend`/`backend` (Java: `JAVA_CODE`).
    pub default_backend: ExecutionBackend,
}

impl LoaderOptions {
    /// The configuration of the Java loader tests (`FormulaInfoParserTest`): `siteId` as the
    /// multi-tenancy attribute and `checkKind`, else `calculatorName`, as the name.
    pub fn java_tests() -> Self {
        Self {
            multi_tenancy_attribute: Some("siteId".to_owned()),
            additional_attributes: Vec::new(),
            name_attribute: Some("checkKind".to_owned()),
            default_backend: ExecutionBackend::JavaCode,
        }
    }
}

/// The fields of one FormulaInfo, as the Java `FormulaInfo` holds them after loading.
///
/// `javaCode`, `byteCode`, `byteCode_<class>` and `hashByByteCode` are not kept: Java rebuilds
/// them from the formula on every load and never executes the stored ones.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct FormulaInfo {
    /// `getName()`: see [`LoaderOptions::name_attribute`].
    pub name: Option<String>,
    pub calculator_name: Option<String>,
    pub description: Option<String>,
    /// Insertion-ordered and unique (`LinkedHashSet`).
    pub tags: Vec<String>,
    pub period_start_inclusive: Option<String>,
    pub period_end_exclusive: Option<String>,
    pub multi_tenancy_id: Option<String>,
    pub depends_on: Option<String>,
    /// The Java class name of `resultType` (default `java.lang.Float`).
    pub result_type: String,
    /// The Java class name of `numberType`, when given.
    pub number_type: Option<String>,
    pub execution_backend: ExecutionBackend,
    pub formula_text: String,
    /// Where the `formula:` value sits in the source (the raw value slice).
    pub formula_span: Span,
    /// The `hash:` the document declared. Java overwrites it with [`FormulaInfo::hash`].
    pub declared_hash: Option<String>,
    /// MD5 of the formula text (UTF-8), upper-case hex (`MD5.toHex`).
    pub hash: String,
    /// `"Formula_" + name`.
    pub class_name: String,
    /// `class_name + "_" + hash`.
    pub class_name_with_hash: String,
    /// `extraValueByKey`: unknown keys and additional attributes, insertion-ordered.
    pub extra: Vec<(String, String)>,
}

impl FormulaInfo {
    pub fn extra(&self, key: &str) -> Option<&str> {
        self.extra
            .iter()
            .find(|(k, _)| k == key)
            .map(|(_, v)| v.as_str())
    }

    fn put_extra(&mut self, key: &str, value: &str) {
        match self.extra.iter_mut().find(|(k, _)| k == key) {
            Some((_, existing)) => *existing = value.to_owned(),
            None => self.extra.push((key.to_owned(), value.to_owned())),
        }
    }

    /// The fields as a JSON object (field names as in Java).
    pub fn canonical_json(&self) -> String {
        let opt = |value: &Option<String>| match value {
            Some(value) => json_string(value),
            None => "null".to_owned(),
        };
        let tags: Vec<String> = self.tags.iter().map(|t| json_string(t)).collect();
        let extra: Vec<String> = self
            .extra
            .iter()
            .map(|(k, v)| format!("{}:{}", json_string(k), json_string(v)))
            .collect();
        format!(
            concat!(
                "{{\"name\":{},\"calculatorName\":{},\"description\":{},\"tags\":[{}],",
                "\"periodStartInclusive\":{},\"periodEndExclusive\":{},\"multiTenancyId\":{},",
                "\"dependsOn\":{},\"resultType\":{},\"numberType\":{},\"executionBackend\":{},",
                "\"formulaText\":{},\"formulaSpan\":[{},{}],\"declaredHash\":{},\"hash\":{},",
                "\"className\":{},\"classNameWithHash\":{},\"extraValueByKey\":{{{}}}}}"
            ),
            opt(&self.name),
            opt(&self.calculator_name),
            opt(&self.description),
            tags.join(","),
            opt(&self.period_start_inclusive),
            opt(&self.period_end_exclusive),
            opt(&self.multi_tenancy_id),
            opt(&self.depends_on),
            json_string(&self.result_type),
            opt(&self.number_type),
            json_string(self.execution_backend.name()),
            json_string(&self.formula_text),
            self.formula_span.start,
            self.formula_span.end,
            opt(&self.declared_hash),
            json_string(&self.hash),
            json_string(&self.class_name),
            json_string(&self.class_name_with_hash),
            extra.join(",")
        )
    }
}

/// A loaded FormulaInfo with its formula built for evaluation.
#[derive(Clone, Debug)]
pub struct LoadedFormula {
    pub info: FormulaInfo,
    pub program: Program,
}

impl LoadedFormula {
    /// Evaluates the formula once (tree walker), as the Java calculator's `apply` would.
    pub fn evaluate(&self, context: &mut Context, host: &mut Host<'_>) -> Result<Value, EvalError> {
        calculator_result(self.program.eval_tree(context, host))
    }
}

/// Why a document did not load. Each variant corresponds to the exception the Java loader
/// throws at the same point ([`LoadError::java_exception`]), except
/// [`LoadError::UnsupportedType`], which Java accepts.
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum LoadError {
    /// The document is not a FormulaInfo document.
    Syntax(Box<ParseDiagnostic>),
    /// `key:` at the very end of the input (Java: `NoSuchElementException`).
    EmptyValueAtEnd { key: String },
    /// `executionBackend` / `backend` names no backend.
    UnknownExecutionBackend { value: String },
    /// `resultType` / `numberType` names no type (Java: `ClassNotFoundException` in a
    /// `RuntimeException`).
    UnknownType { key: String, value: String },
    /// A `byteCode` value of odd length (Java `HexFormat.parseHex`).
    OddHexLength { key: String, length: usize },
    /// A `byteCode` value with a non-hex digit (Java `HexFormat.parseHex`).
    InvalidHexDigit { key: String, digit: char },
    /// No (or a blank) `formula:` (Java: `CompileError`).
    MissingFormula { calculator_name: Option<String> },
    /// A type Java supports but this runtime does not (BigDecimal, BigInteger, Timestamp, or a
    /// non-numeric `numberType`). No Java counterpart.
    UnsupportedType { key: String, java_type: String },
    /// Building the formula failed (the Java calculator constructor's exception).
    Formula {
        calculator_name: Option<String>,
        error: Box<EvalError>,
    },
    /// `dependsOn` names a calculator the document does not define (Java: a
    /// `NullPointerException` while wiring the dependency).
    UnknownDependsOn {
        calculator_name: Option<String>,
        depends_on: String,
    },
}

impl LoadError {
    /// The simple name of the exception the Java loader throws for the same document.
    pub fn java_exception(&self) -> &'static str {
        match self {
            Self::Syntax(_) => "(none: FormulaInfoList.parse drops the unparsed rest)",
            Self::EmptyValueAtEnd { .. } => "NoSuchElementException",
            Self::UnknownExecutionBackend { .. } | Self::OddHexLength { .. } => {
                "IllegalArgumentException"
            }
            Self::UnknownType { .. } => "RuntimeException",
            Self::InvalidHexDigit { .. } => "NumberFormatException",
            Self::MissingFormula { .. } => "CompileError",
            Self::UnsupportedType { .. } => "(none: Java supports this type)",
            Self::Formula { error, .. } => error.kind.java_name(),
            Self::UnknownDependsOn { .. } => "NullPointerException",
        }
    }

    /// A stable machine-readable category.
    pub fn kind(&self) -> &'static str {
        match self {
            Self::Syntax(_) => "syntax",
            Self::EmptyValueAtEnd { .. } => "empty_value_at_end",
            Self::UnknownExecutionBackend { .. } => "unknown_execution_backend",
            Self::UnknownType { .. } => "unknown_type",
            Self::OddHexLength { .. } => "odd_hex_length",
            Self::InvalidHexDigit { .. } => "invalid_hex_digit",
            Self::MissingFormula { .. } => "missing_formula",
            Self::UnsupportedType { .. } => "unsupported_type",
            Self::Formula { .. } => "formula",
            Self::UnknownDependsOn { .. } => "unknown_depends_on",
        }
    }

    pub fn canonical_json(&self) -> String {
        let detail = match self {
            Self::Syntax(diagnostic) => format!(",\"diagnostic\":{}", diagnostic.canonical_json()),
            Self::Formula { error, .. } => format!(",\"error\":{}", error.canonical_json()),
            _ => String::new(),
        };
        format!(
            "{{\"kind\":{},\"javaException\":{},\"message\":{}{}}}",
            json_string(self.kind()),
            json_string(self.java_exception()),
            json_string(&self.to_string()),
            detail
        )
    }
}

impl Display for LoadError {
    fn fmt(&self, f: &mut Formatter<'_>) -> fmt::Result {
        let name = |name: &Option<String>| name.clone().unwrap_or_else(|| "null".to_owned());
        match self {
            Self::Syntax(diagnostic) => write!(f, "not a FormulaInfo document: {diagnostic}"),
            Self::EmptyValueAtEnd { key } => {
                write!(f, "'{key}:' has an empty value at the end of input")
            }
            Self::UnknownExecutionBackend { value } => {
                write!(f, "unknown executionBackend: {value}")
            }
            Self::UnknownType { key, value } => write!(f, "{key}: unknown type {value}"),
            Self::OddHexLength { key, length } => {
                write!(f, "{key}: string length not even: {length}")
            }
            Self::InvalidHexDigit { key, digit } => {
                write!(f, "{key}: not a hexadecimal digit: {digit:?}")
            }
            Self::MissingFormula { .. } => f.write_str(
                "FormulaInfo must contain formula text. Stored bytecode is not executed without its source formula.",
            ),
            Self::UnsupportedType { key, java_type } => {
                write!(f, "{key}: {java_type} is not supported by tinyexpression-rs")
            }
            Self::Formula {
                calculator_name,
                error,
            } => write!(f, "formula of {}: {error}", name(calculator_name)),
            Self::UnknownDependsOn {
                calculator_name,
                depends_on,
            } => write!(
                f,
                "{} dependsOn unknown calculator '{depends_on}'",
                name(calculator_name)
            ),
        }
    }
}

impl std::error::Error for LoadError {}

/// Loads every FormulaInfo of a document and builds its formula (Java:
/// `FormulaInfoList.parse`). Blocks without entries (comment-only blocks) are skipped.
pub fn load(source: &str, options: &LoaderOptions) -> Result<Vec<LoadedFormula>, LoadError> {
    let document =
        parse_document(source).map_err(|diagnostic| LoadError::Syntax(Box::new(diagnostic)))?;
    let mut loaded = Vec::new();
    for block in document.blocks.iter().filter(|b| !b.entries.is_empty()) {
        loaded.push(load_block(block, options)?);
    }
    // FormulaInfoBlocksParser.extract: wire dependsOn by calculatorName.
    for formula in &loaded {
        let Some(depends_on) = &formula.info.depends_on else {
            continue;
        };
        for name in java_split(depends_on, ',') {
            if java::strip(name).is_empty() {
                continue;
            }
            let known = loaded
                .iter()
                .any(|other| other.info.calculator_name.as_deref() == Some(name));
            if !known {
                return Err(LoadError::UnknownDependsOn {
                    calculator_name: formula.info.calculator_name.clone(),
                    depends_on: name.to_owned(),
                });
            }
        }
    }
    Ok(loaded)
}

fn is_backend_key(key: &str) -> bool {
    key.eq_ignore_ascii_case("executionBackend") || key.eq_ignore_ascii_case("backend")
}

fn backend(value: &str) -> Result<ExecutionBackend, LoadError> {
    ExecutionBackend::parse(value).ok_or_else(|| LoadError::UnknownExecutionBackend {
        value: value.to_owned(),
    })
}

/// `FormulaInfoParser.extractFormulaInfo` for one block, in the Java order.
fn load_block(block: &Block, options: &LoaderOptions) -> Result<LoadedFormula, LoadError> {
    // resolveExecutionBackend: extracts every entry (a zero-length value fails here) and lets
    // the last backend key win.
    let mut values = Vec::with_capacity(block.entries.len());
    let mut execution_backend = options.default_backend;
    for entry in &block.entries {
        let value = entry.value().ok_or_else(|| LoadError::EmptyValueAtEnd {
            key: entry.key.clone(),
        })?;
        if is_backend_key(&entry.key) {
            execution_backend = backend(&value)?;
        }
        values.push(value);
    }
    let mut info = FormulaInfo {
        name: None,
        calculator_name: None,
        description: None,
        tags: Vec::new(),
        period_start_inclusive: None,
        period_end_exclusive: None,
        multi_tenancy_id: None,
        depends_on: None,
        result_type: String::new(),
        number_type: None,
        execution_backend,
        formula_text: String::new(),
        formula_span: Span { start: 0, end: 0 },
        declared_hash: None,
        hash: String::new(),
        class_name: String::new(),
        class_name_with_hash: String::new(),
        extra: Vec::new(),
    };
    let mut result_type = None;
    let mut formula = None;
    for (entry, value) in block.entries.iter().zip(&values) {
        let key = entry.key.as_str();
        let value = value.as_str();
        let mut matched = true;
        match key {
            "tags" => {
                for tag in java_split(value, ',') {
                    if !info.tags.iter().any(|t| t == tag) {
                        info.tags.push(tag.to_owned());
                    }
                }
            }
            "description" => info.description = Some(value.to_owned()),
            "periodStartInclusive" => info.period_start_inclusive = Some(value.to_owned()),
            "periodEndExclusive" => info.period_end_exclusive = Some(value.to_owned()),
            "calculatorName" => info.calculator_name = Some(value.to_owned()),
            "dependsOn" => info.depends_on = Some(value.to_owned()),
            "resultType" => result_type = Some(java_type(key, value)?),
            "numberType" => info.number_type = Some(java_type(key, value)?.to_owned()),
            _ => matched = false,
        }
        if is_backend_key(key) {
            info.execution_backend = backend(value)?;
            matched = true;
        }
        if options.multi_tenancy_attribute.as_deref() == Some(key) {
            info.multi_tenancy_id = Some(value.to_owned());
            matched = true;
        }
        if options.additional_attributes.iter().any(|a| a == key) {
            info.put_extra(key, value);
            matched = true;
        }
        match key {
            "hash" => info.declared_hash = Some(value.to_owned()),
            "formula" => formula = Some((value.to_owned(), entry.value_span)),
            // Regenerated from the formula on every load; never trusted (see the Java loader).
            "hashByByteCode" | "javaCode" => {}
            "byteCode" => check_hex(key, value)?,
            _ if key.starts_with("byteCode_") => check_hex(key, value)?,
            _ => {
                if !matched {
                    info.put_extra(key, value);
                }
            }
        }
    }
    info.result_type = result_type.unwrap_or("java.lang.Float").to_owned();
    let Some((formula_text, formula_span)) =
        formula.filter(|(text, _)| !text.chars().all(java::is_whitespace))
    else {
        return Err(LoadError::MissingFormula {
            calculator_name: info.calculator_name,
        });
    };
    info.hash = md5_upper_hex(formula_text.as_bytes());
    info.name = match &options.name_attribute {
        Some(attribute) => info
            .extra(attribute)
            .map(str::to_owned)
            .or_else(|| info.calculator_name.clone()),
        None => info.calculator_name.clone(),
    };
    info.class_name = format!("Formula_{}", info.name.as_deref().unwrap_or("null"));
    info.class_name_with_hash = format!("{}_{}", info.class_name, info.hash);
    info.formula_text = formula_text;
    info.formula_span = formula_span;
    let program = Program::new(&info.formula_text, runtime_options(&info)?).map_err(|error| {
        LoadError::Formula {
            calculator_name: info.calculator_name.clone(),
            error: Box::new(error),
        }
    })?;
    Ok(LoadedFormula { info, program })
}

/// `new ResultType(value)`: the loader's name table, else a class name. Only the classes the
/// table maps to are known here (Java would `Class.forName` any class on its class path).
fn java_type(key: &str, value: &str) -> Result<&'static str, LoadError> {
    Ok(match value {
        "float" | "Float" | "java.lang.Float" => "java.lang.Float",
        "byte" | "Byte" | "java.lang.Byte" => "java.lang.Byte",
        "short" | "Short" | "java.lang.Short" => "java.lang.Short",
        "int" | "Integer" | "java.lang.Integer" => "java.lang.Integer",
        "long" | "Long" | "java.lang.Long" => "java.lang.Long",
        "double" | "java.lang.Double" => "java.lang.Double",
        "BigDecimal" | "bigDecimal" | "java.math.BigDecimal" => "java.math.BigDecimal",
        "java.math.BigInteger" => "java.math.BigInteger",
        "boolean" | "Boolean" | "java.lang.Boolean" => "java.lang.Boolean",
        "string" | "String" | "java.lang.String" => "java.lang.String",
        "timestamp" | "Timestamp" | "java.sql.Timestamp" => "java.sql.Timestamp",
        "object" | "Object" | "java.lang.Object" => "java.lang.Object",
        _ => {
            return Err(LoadError::UnknownType {
                key: key.to_owned(),
                value: value.to_owned(),
            })
        }
    })
}

fn runtime_options(info: &FormulaInfo) -> Result<Options, LoadError> {
    let unsupported = |key: &str, java_type: &str| LoadError::UnsupportedType {
        key: key.to_owned(),
        java_type: java_type.to_owned(),
    };
    let result_type = ResultType::parse(&info.result_type)
        .ok_or_else(|| unsupported("resultType", &info.result_type))?;
    let number_type = match &info.number_type {
        Some(number_type) => {
            NumberType::parse(number_type).ok_or_else(|| unsupported("numberType", number_type))?
        }
        // P4TypedAstEvaluator.resolveNumberType: without a numberType a numeric resultType is
        // also the number type, anything else computes in float.
        None => NumberType::parse(&info.result_type).unwrap_or(NumberType::Float),
    };
    Ok(Options::new(result_type).with_number_type(number_type))
}

/// `HexFormat.of().parseHex(value)`: even UTF-16 length, then every digit hexadecimal.
fn check_hex(key: &str, value: &str) -> Result<(), LoadError> {
    let length = java::utf16_len(value);
    if length % 2 != 0 {
        return Err(LoadError::OddHexLength {
            key: key.to_owned(),
            length,
        });
    }
    match value.chars().find(|c| !c.is_ascii_hexdigit()) {
        Some(digit) => Err(LoadError::InvalidHexDigit {
            key: key.to_owned(),
            digit,
        }),
        None => Ok(()),
    }
}

/// Evaluates every loaded formula once on its own copy of `context` (document order).
pub fn evaluate_all(
    formulas: &[LoadedFormula],
    context: &Context,
    host: &mut Host<'_>,
) -> Vec<Result<Value, EvalError>> {
    formulas
        .iter()
        .map(|formula| formula.evaluate(&mut context.clone(), host))
        .collect()
}

// ------------------------------------------------------------------ Java string helpers

/// `String.stripTrailing()`.
fn strip_trailing(s: &str) -> &str {
    s.trim_end_matches(java::is_whitespace)
}

/// `String.split(String.valueOf(separator))` for a single literal character: no match gives
/// the whole string, otherwise trailing empty strings are removed.
fn java_split(s: &str, separator: char) -> Vec<&str> {
    if !s.contains(separator) {
        return vec![s];
    }
    let mut parts: Vec<&str> = s.split(separator).collect();
    while parts.last() == Some(&"") {
        parts.pop();
    }
    parts
}

// ------------------------------------------------------------------ MD5 (RFC 1321)

/// `MD5.toHex`: the MD5 digest as upper-case hex.
pub fn md5_upper_hex(bytes: &[u8]) -> String {
    const HEX: &[u8; 16] = b"0123456789ABCDEF";
    let mut out = String::with_capacity(32);
    for byte in md5(bytes) {
        out.push(char::from(HEX[usize::from(byte >> 4)]));
        out.push(char::from(HEX[usize::from(byte & 0xF)]));
    }
    out
}

fn md5(input: &[u8]) -> [u8; 16] {
    const S: [u32; 64] = [
        7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 5, 9, 14, 20, 5, 9, 14, 20, 5,
        9, 14, 20, 5, 9, 14, 20, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 6, 10,
        15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21,
    ];
    let k: Vec<u32> = (0..64)
        .map(|i| ((i as f64 + 1.0).sin().abs() * 4_294_967_296.0) as u32)
        .collect();
    let mut state: [u32; 4] = [0x6745_2301, 0xefcd_ab89, 0x98ba_dcfe, 0x1032_5476];
    let mut message = input.to_vec();
    let bit_length = (input.len() as u64).wrapping_mul(8);
    message.push(0x80);
    while message.len() % 64 != 56 {
        message.push(0);
    }
    message.extend_from_slice(&bit_length.to_le_bytes());
    for chunk in message.chunks_exact(64) {
        let words: Vec<u32> = chunk
            .chunks_exact(4)
            .map(|w| u32::from_le_bytes([w[0], w[1], w[2], w[3]]))
            .collect();
        let [mut a, mut b, mut c, mut d] = state;
        for i in 0..64 {
            let (f, g) = match i / 16 {
                0 => ((b & c) | (!b & d), i),
                1 => ((d & b) | (!d & c), (5 * i + 1) % 16),
                2 => (b ^ c ^ d, (3 * i + 5) % 16),
                _ => (c ^ (b | !d), (7 * i) % 16),
            };
            let rotated = a
                .wrapping_add(f)
                .wrapping_add(k[i])
                .wrapping_add(words[g])
                .rotate_left(S[i]);
            a = d;
            d = c;
            c = b;
            b = b.wrapping_add(rotated);
        }
        state[0] = state[0].wrapping_add(a);
        state[1] = state[1].wrapping_add(b);
        state[2] = state[2].wrapping_add(c);
        state[3] = state[3].wrapping_add(d);
    }
    let mut digest = [0u8; 16];
    for (i, word) in state.iter().enumerate() {
        digest[i * 4..i * 4 + 4].copy_from_slice(&word.to_le_bytes());
    }
    digest
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn md5_matches_rfc_1321_vectors() {
        assert_eq!(md5_upper_hex(b""), "D41D8CD98F00B204E9800998ECF8427E");
        assert_eq!(md5_upper_hex(b"abc"), "900150983CD24FB0D6963F7D28E17F72");
        assert_eq!(
            md5_upper_hex(
                b"12345678901234567890123456789012345678901234567890123456789012345678901234567890"
            ),
            "57EDF4A22BE3C955AC49DA2E2107B67A"
        );
    }

    #[test]
    fn java_split_drops_trailing_empty_strings_only() {
        assert_eq!(java_split("", ','), vec![""]);
        assert_eq!(java_split(",", ','), Vec::<&str>::new());
        assert_eq!(java_split(",a,,b,,", ','), vec!["", "a", "", "b"]);
    }

    #[test]
    fn backend_spellings() {
        assert_eq!(
            ExecutionBackend::parse(" p4-ast_evaluator "),
            Some(ExecutionBackend::P4AstEvaluator)
        );
        assert_eq!(
            ExecutionBackend::parse("ootc legacy"),
            Some(ExecutionBackend::JavaCodeLegacyAstCreator)
        );
        assert_eq!(ExecutionBackend::parse("P4_MAGIC"), None);
        assert_eq!(ExecutionBackend::parse("  "), None);
    }
}
