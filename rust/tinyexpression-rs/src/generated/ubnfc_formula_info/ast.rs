//! 採用結果だけから構築する AST。数値 capture は字句 String。
#![allow(non_camel_case_types, non_snake_case)]
use super::api::Span;
#[derive(Clone,Debug,PartialEq)]
pub enum Ast { Text(String), Null,
g_FormulaInfoAST_2e_FormulaInfoDocument(g_FormulaInfoAST_2e_FormulaInfoDocument),
g_FormulaInfoAST_2e_FormulaInfoBlock(g_FormulaInfoAST_2e_FormulaInfoBlock),
g_FormulaInfoAST_2e_CommentLine(g_FormulaInfoAST_2e_CommentLine),
g_FormulaInfoAST_2e_BlankLine(g_FormulaInfoAST_2e_BlankLine),
g_FormulaInfoAST_2e_FormulaInfoEntry(g_FormulaInfoAST_2e_FormulaInfoEntry),
g_FormulaInfoAST_2e_FormulaInfoValue(g_FormulaInfoAST_2e_FormulaInfoValue),
g_FormulaInfoAST_2e_EndOfPart(g_FormulaInfoAST_2e_EndOfPart),
}
pub type g_FormulaInfoAST = Ast;
#[derive(Clone,Debug,PartialEq)] pub struct g_FormulaInfoAST_2e_FormulaInfoDocument { pub span:Span, pub node_id:usize,
pub g_blocks: Vec<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_FormulaInfoAST_2e_FormulaInfoBlock { pub span:Span, pub node_id:usize,
pub g_leading: Vec<Ast>,
pub g_entries: Vec<Ast>,
pub g_end: Option<Box<Ast>>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_FormulaInfoAST_2e_CommentLine { pub span:Span, pub node_id:usize,
pub g_text: String,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_FormulaInfoAST_2e_BlankLine { pub span:Span, pub node_id:usize,
pub g_text: String,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_FormulaInfoAST_2e_FormulaInfoEntry { pub span:Span, pub node_id:usize,
pub g_key: String,
pub g_value: Box<Ast>,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_FormulaInfoAST_2e_FormulaInfoValue { pub span:Span, pub node_id:usize,
pub g_text: String,
}
#[derive(Clone,Debug,PartialEq)] pub struct g_FormulaInfoAST_2e_EndOfPart { pub span:Span, pub node_id:usize,
pub g_mark: String,
}
impl Ast { pub fn span(&self)->Option<Span> { match self {Self::Text(_)|Self::Null=>None,
Self::g_FormulaInfoAST_2e_FormulaInfoDocument(node)=>Some(node.span),
Self::g_FormulaInfoAST_2e_FormulaInfoBlock(node)=>Some(node.span),
Self::g_FormulaInfoAST_2e_CommentLine(node)=>Some(node.span),
Self::g_FormulaInfoAST_2e_BlankLine(node)=>Some(node.span),
Self::g_FormulaInfoAST_2e_FormulaInfoEntry(node)=>Some(node.span),
Self::g_FormulaInfoAST_2e_FormulaInfoValue(node)=>Some(node.span),
Self::g_FormulaInfoAST_2e_EndOfPart(node)=>Some(node.span),
}}
pub fn type_name(&self)->&'static str { match self {Self::Text(_)=>"text",Self::Null=>"null",
Self::g_FormulaInfoAST_2e_FormulaInfoDocument(_)=>"FormulaInfoDocument",
Self::g_FormulaInfoAST_2e_FormulaInfoBlock(_)=>"FormulaInfoBlock",
Self::g_FormulaInfoAST_2e_CommentLine(_)=>"CommentLine",
Self::g_FormulaInfoAST_2e_BlankLine(_)=>"BlankLine",
Self::g_FormulaInfoAST_2e_FormulaInfoEntry(_)=>"FormulaInfoEntry",
Self::g_FormulaInfoAST_2e_FormulaInfoValue(_)=>"FormulaInfoValue",
Self::g_FormulaInfoAST_2e_EndOfPart(_)=>"EndOfPart",
}}
pub fn type_id(&self)->&'static str {match self {Self::Text(_)=>"text",Self::Null=>"null",
Self::g_FormulaInfoAST_2e_FormulaInfoDocument(_)=>"FormulaInfoAST.FormulaInfoDocument",
Self::g_FormulaInfoAST_2e_FormulaInfoBlock(_)=>"FormulaInfoAST.FormulaInfoBlock",
Self::g_FormulaInfoAST_2e_CommentLine(_)=>"FormulaInfoAST.CommentLine",
Self::g_FormulaInfoAST_2e_BlankLine(_)=>"FormulaInfoAST.BlankLine",
Self::g_FormulaInfoAST_2e_FormulaInfoEntry(_)=>"FormulaInfoAST.FormulaInfoEntry",
Self::g_FormulaInfoAST_2e_FormulaInfoValue(_)=>"FormulaInfoAST.FormulaInfoValue",
Self::g_FormulaInfoAST_2e_EndOfPart(_)=>"FormulaInfoAST.EndOfPart",
}}
#[cfg(feature="json")] pub fn canonical_value(&self)->serde_json::Value { match self { Self::Text(value)=>serde_json::Value::String(value.clone()),Self::Null=>serde_json::Value::Null,
Self::g_FormulaInfoAST_2e_FormulaInfoDocument(node)=>serde_json::json!({"fields":{
"blocks":node.g_blocks.iter().map(Ast::canonical_value).collect::<Vec<_>>(),
},"span":node.span,"type":"FormulaInfoDocument"}),
Self::g_FormulaInfoAST_2e_FormulaInfoBlock(node)=>serde_json::json!({"fields":{
"end":node.g_end.as_ref().map(|v|v.canonical_value()),
"entries":node.g_entries.iter().map(Ast::canonical_value).collect::<Vec<_>>(),
"leading":node.g_leading.iter().map(Ast::canonical_value).collect::<Vec<_>>(),
},"span":node.span,"type":"FormulaInfoBlock"}),
Self::g_FormulaInfoAST_2e_CommentLine(node)=>serde_json::json!({"fields":{
"text":node.g_text,
},"span":node.span,"type":"CommentLine"}),
Self::g_FormulaInfoAST_2e_BlankLine(node)=>serde_json::json!({"fields":{
"text":node.g_text,
},"span":node.span,"type":"BlankLine"}),
Self::g_FormulaInfoAST_2e_FormulaInfoEntry(node)=>serde_json::json!({"fields":{
"key":node.g_key,
"value":node.g_value.canonical_value(),
},"span":node.span,"type":"FormulaInfoEntry"}),
Self::g_FormulaInfoAST_2e_FormulaInfoValue(node)=>serde_json::json!({"fields":{
"text":node.g_text,
},"span":node.span,"type":"FormulaInfoValue"}),
Self::g_FormulaInfoAST_2e_EndOfPart(node)=>serde_json::json!({"fields":{
"mark":node.g_mark,
},"span":node.span,"type":"EndOfPart"}),
}}
#[cfg(feature="json")] pub fn canonical_json(&self)->String {serde_json::to_string(&self.canonical_value()).expect("finite canonical AST")}
}
#[cfg(feature="json")] impl serde::Serialize for Ast {fn serialize<S:serde::Serializer>(&self,serializer:S)->Result<S::Ok,S::Error> {self.canonical_value().serialize(serializer)}}
/// D-077: arena 形の AST。所有 `Ast` はこの木から写す。
pub mod tree {
#![allow(non_camel_case_types, non_snake_case, non_upper_case_globals, dead_code, clippy::all)]
// D-077: 採用結果の AST を arena（SoA）で持つ形。所有 `Ast` は常にこの木から写す。
//
// - 節点は `nodes: Vec<TreeNode>`（24 byte）に作った順で並ぶ。子は `slots: Vec<u32>` の添字で指す。
// - 文字列は持たない。Text 節点は入力の byte 範囲 `[a, b)` で、値は常に入力の部分文字列
//   （semantic text は trim 済み範囲か token の content 範囲で、どちらも入力の中にある）。
// - record の field は宣言順に slot を持つ: scalar / optional は 1 slot（空は `NONE`）、
//   list は 2 slot（`slots` 内の開始位置と個数。要素は record より先に `slots` へ積む）。
// - `#[doc(hidden)] pub` の構築用メソッドは生成 runtime 専用（driver が runtime を別 crate へ
//   `#[path]` で取り込むため crate 内可視にできない）。利用者は呼ばない。
// - 作ったが採用されなかった節点（値の包み直しで捨てた Text 等）も `nodes` に残る。
//   根から辿れないだけで観測には出ない（所有 `Ast` と同じ）。
use super::super::api::{NodeSpan, Span, ValueSpan};
/// 空の slot（optional の無値）と、根の無い木。
pub const NONE: u32 = u32::MAX;
pub const KIND_TEXT: u16 = 0;
pub const KIND_NULL: u16 = 1;
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) struct TreeNode {
    kind: u16,
    /// 節点を作った規則（`RULE_IDS` の添字）。Text / Null は 0。
    rule: u16,
    /// 型付き節点の作成順（`NodeSpan::node_id`）。Text / Null は 0。
    node_id: u32,
    /// code point の範囲。Text は値の出自（extent）。
    span: [u32; 2],
    /// Text: byte 範囲。record: 最初の slot。enum: variant の番号。
    a: u32,
    b: u32,
}
/// 1 回の parse の AST を arena に持つ木（`ParseOptions::ast_tree`）。
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct AstTree {
    source: String,
    nodes: Vec<TreeNode>,
    slots: Vec<u32>,
    typed: u32,
    root: u32,
}
impl Default for AstTree {
    fn default() -> Self {
        Self {
            source: String::new(),
            nodes: Vec::new(),
            slots: Vec::new(),
            typed: 0,
            root: NONE,
        }
    }
}
fn to_u32(value: usize) -> u32 {
    u32::try_from(value).expect("AST tree offsets fit in u32 (input_bytes <= u32::MAX)")
}
fn span_of(node: &TreeNode) -> Span {
    [node.span[0] as usize, node.span[1] as usize]
}
impl AstTree {
    /// 解析した入力（Text 節点の値はこの部分文字列）。
    pub fn source(&self) -> &str {
        &self.source
    }
    pub fn root(&self) -> Option<NodeRef<'_>> {
        (self.root != NONE).then_some(NodeRef {
            tree: self,
            id: self.root,
        })
    }
    /// 任意の節点（`NodeRef::id` の値）。範囲外は `None`。
    pub fn node(&self, id: u32) -> Option<NodeRef<'_>> {
        ((id as usize) < self.nodes.len()).then_some(NodeRef { tree: self, id })
    }
    /// arena の節点数（Text と、根から辿れない節点を含む）。
    pub fn node_count(&self) -> usize {
        self.nodes.len()
    }
    /// 子の参照に使っている slot（u32）の数。
    pub fn slot_count(&self) -> usize {
        self.slots.len()
    }
    /// 型付き節点の数（`Statistics::ast_nodes` と同じ）。
    pub fn typed_node_count(&self) -> usize {
        self.typed as usize
    }
    /// 所有 `Ast` へ写す（`ParseOptions::build_ast` が作るものと同じ値）。
    pub fn to_ast(&self) -> Option<super::Ast> {
        self.root().map(|root| root.to_ast())
    }
    /// `ParseResult::node_spans` と同じ表（作成順）。
    pub fn node_spans(&self) -> Vec<NodeSpan> {
        let mut out = Vec::with_capacity(self.typed as usize);
        self.push_node_spans(&mut out);
        out
    }
    /// `ParseOptions::value_spans` と同じ表（canonical AST の JSON pointer 順）。
    pub fn value_spans(&self) -> Vec<ValueSpan> {
        let mut out = Vec::new();
        if self.root != NONE {
            let mut path = String::with_capacity(128);
            self.collect_value_spans(&self.source, self.root, &mut path, &mut out);
        }
        out
    }
    /// この木が確保している byte 数（容量ベース）。
    pub fn heap_bytes(&self) -> usize {
        self.source.capacity()
            + self.nodes.capacity() * std::mem::size_of::<TreeNode>()
            + self.slots.capacity() * std::mem::size_of::<u32>()
    }
    #[doc(hidden)]
    pub fn push_node_spans(&self, out: &mut Vec<NodeSpan>) {
        for node in &self.nodes {
            if node.kind > KIND_NULL {
                out.push(NodeSpan {
                    node_id: node.node_id as usize,
                    rule_id: RULE_IDS[node.rule as usize],
                    node_type: TYPE_NAMES[node.kind as usize],
                    span: span_of(node),
                });
            }
        }
    }
    #[doc(hidden)]
    pub fn clear(&mut self) {
        self.source.clear();
        self.nodes.clear();
        self.slots.clear();
        self.typed = 0;
        self.root = NONE;
    }
    #[doc(hidden)]
    pub fn reserve(&mut self, nodes: usize, slots: usize) {
        self.nodes.reserve(nodes);
        self.slots.reserve(slots);
    }
    #[doc(hidden)]
    pub fn set_root(&mut self, root: u32) {
        self.root = root;
    }
    #[doc(hidden)]
    pub fn set_source(&mut self, source: &str) {
        self.source.clear();
        self.source.push_str(source);
    }
    #[inline]
    fn push(&mut self, node: TreeNode) -> u32 {
        let id = to_u32(self.nodes.len());
        self.nodes.push(node);
        id
    }
    /// 入力 `[bytes[0], bytes[1])` を値とする Text 節点。`extent` は値の出自（code point）。
    #[inline]
    #[doc(hidden)]
    pub fn text(&mut self, extent: Span, bytes: [usize; 2]) -> u32 {
        self.push(TreeNode {
            kind: KIND_TEXT,
            rule: 0,
            node_id: 0,
            span: [to_u32(extent[0]), to_u32(extent[1])],
            a: to_u32(bytes[0]),
            b: to_u32(bytes[1]),
        })
    }
    /// Text の出自を付け直す（leaf 型へ昇格した Text は capture の範囲を出自にする）。
    #[doc(hidden)]
    pub fn set_extent(&mut self, id: u32, extent: Span) {
        self.nodes[id as usize].span = [to_u32(extent[0]), to_u32(extent[1])];
    }
    /// list field の要素を積み、record の 2 slot（開始, 個数）を返す。
    #[inline]
    #[doc(hidden)]
    pub fn list(&mut self, items: &[u32]) -> [u32; 2] {
        let start = to_u32(self.slots.len());
        self.slots.extend_from_slice(items);
        [start, to_u32(items.len())]
    }
    #[inline]
    #[doc(hidden)]
    pub fn record(&mut self, kind: u16, rule: u16, span: Span, fields: &[u32]) -> u32 {
        let first = to_u32(self.slots.len());
        self.slots.extend_from_slice(fields);
        let node_id = self.typed;
        self.typed += 1;
        self.push(TreeNode {
            kind,
            rule,
            node_id,
            span: [to_u32(span[0]), to_u32(span[1])],
            a: first,
            b: to_u32(fields.len()),
        })
    }
    #[inline]
    #[doc(hidden)]
    pub fn enumeration(&mut self, kind: u16, rule: u16, span: Span, value: u32) -> u32 {
        let node_id = self.typed;
        self.typed += 1;
        self.push(TreeNode {
            kind,
            rule,
            node_id,
            span: [to_u32(span[0]), to_u32(span[1])],
            a: value,
            b: 0,
        })
    }
    #[inline]
    #[doc(hidden)]
    pub fn kind(&self, id: u32) -> u16 {
        self.nodes[id as usize].kind
    }
    #[doc(hidden)]
    pub fn type_id_of(&self, id: u32) -> &'static str {
        TYPE_IDS[self.kind(id) as usize]
    }
    #[doc(hidden)]
    pub fn type_name_of(&self, id: u32) -> &'static str {
        TYPE_NAMES[self.kind(id) as usize]
    }
    #[inline]
    fn slot(&self, id: u32, index: usize) -> u32 {
        self.slots[self.nodes[id as usize].a as usize + index]
    }
    #[inline]
    fn items(&self, id: u32, index: usize) -> &[u32] {
        let first = self.nodes[id as usize].a as usize + index;
        let (start, len) = (self.slots[first] as usize, self.slots[first + 1] as usize);
        &self.slots[start..start + len]
    }
    /// list field の要素（`slots` の開始位置と個数）。
    #[inline]
    fn list_at(&self, start: u32, len: u32) -> &[u32] {
        &self.slots[start as usize..start as usize + len as usize]
    }
    #[inline]
    fn text_in<'s>(&self, source: &'s str, id: u32) -> &'s str {
        let node = &self.nodes[id as usize];
        &source[node.a as usize..node.b as usize]
    }
    fn text_value_span(&self, id: u32, path: &str, out: &mut Vec<ValueSpan>) {
        out.push(ValueSpan {
            path: path.to_owned(),
            span: span_of(&self.nodes[id as usize]),
            text: None,
        });
    }
}
/// 木の 1 節点（Text / Null / 型付き節点）。
#[derive(Clone, Copy)]
pub struct NodeRef<'t> {
    tree: &'t AstTree,
    id: u32,
}
impl PartialEq for NodeRef<'_> {
    fn eq(&self, other: &Self) -> bool {
        std::ptr::eq(self.tree, other.tree) && self.id == other.id
    }
}
impl Eq for NodeRef<'_> {}
impl std::fmt::Debug for NodeRef<'_> {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("NodeRef")
            .field("id", &self.id)
            .field("type", &self.type_name())
            .finish()
    }
}
impl<'t> NodeRef<'t> {
    pub fn id(&self) -> u32 {
        self.id
    }
    pub fn tree(&self) -> &'t AstTree {
        self.tree
    }
    /// 型付き節点の範囲（code point）。Text / Null は `None`（`Ast::span` と同じ）。
    pub fn span(&self) -> Option<Span> {
        let node = &self.tree.nodes[self.id as usize];
        (node.kind > KIND_NULL).then(|| span_of(node))
    }
    pub fn type_name(&self) -> &'static str {
        self.tree.type_name_of(self.id)
    }
    pub fn type_id(&self) -> &'static str {
        self.tree.type_id_of(self.id)
    }
    /// Text 節点の値（入力の部分文字列、複製しない）。
    pub fn text(&self) -> Option<&'t str> {
        (self.tree.kind(self.id) == KIND_TEXT)
            .then(|| self.tree.text_in(&self.tree.source, self.id))
    }
    pub fn is_null(&self) -> bool {
        self.tree.kind(self.id) == KIND_NULL
    }
    pub fn to_ast(&self) -> super::Ast {
        self.tree.project(&self.tree.source, self.id)
    }
}
/// list field（要素は節点）。
#[derive(Clone, Copy)]
pub struct NodeList<'t> {
    tree: &'t AstTree,
    ids: &'t [u32],
}
impl std::fmt::Debug for NodeList<'_> {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_list().entries(self.iter()).finish()
    }
}
impl<'t> NodeList<'t> {
    pub fn len(&self) -> usize {
        self.ids.len()
    }
    pub fn is_empty(&self) -> bool {
        self.ids.is_empty()
    }
    pub fn get(&self, index: usize) -> Option<NodeRef<'t>> {
        let tree = self.tree;
        self.ids.get(index).map(|&id| NodeRef { tree, id })
    }
    pub fn iter(&self) -> impl ExactSizeIterator<Item = NodeRef<'t>> + 't {
        let tree = self.tree;
        self.ids.iter().map(move |&id| NodeRef { tree, id })
    }
}
/// list field（要素は文字列。入力の部分文字列を返す）。
#[derive(Clone, Copy)]
pub struct TextList<'t> {
    tree: &'t AstTree,
    ids: &'t [u32],
}
impl std::fmt::Debug for TextList<'_> {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_list().entries(self.iter()).finish()
    }
}
impl<'t> TextList<'t> {
    pub fn len(&self) -> usize {
        self.ids.len()
    }
    pub fn is_empty(&self) -> bool {
        self.ids.is_empty()
    }
    pub fn get(&self, index: usize) -> Option<&'t str> {
        let tree = self.tree;
        self.ids
            .get(index)
            .map(|&id| tree.text_in(&tree.source, id))
    }
    pub fn iter(&self) -> impl ExactSizeIterator<Item = &'t str> + 't {
        let tree = self.tree;
        self.ids
            .iter()
            .map(move |&id| tree.text_in(&tree.source, id))
    }
}
pub const K_g_FormulaInfoAST_2e_FormulaInfoDocument: u16 = 2;
pub const K_g_FormulaInfoAST_2e_FormulaInfoBlock: u16 = 3;
pub const K_g_FormulaInfoAST_2e_CommentLine: u16 = 4;
pub const K_g_FormulaInfoAST_2e_BlankLine: u16 = 5;
pub const K_g_FormulaInfoAST_2e_FormulaInfoEntry: u16 = 6;
pub const K_g_FormulaInfoAST_2e_FormulaInfoValue: u16 = 7;
pub const K_g_FormulaInfoAST_2e_EndOfPart: u16 = 8;
pub(crate) const TYPE_IDS: &[&str] = &["text","null","FormulaInfoAST.FormulaInfoDocument","FormulaInfoAST.FormulaInfoBlock","FormulaInfoAST.CommentLine","FormulaInfoAST.BlankLine","FormulaInfoAST.FormulaInfoEntry","FormulaInfoAST.FormulaInfoValue","FormulaInfoAST.EndOfPart"];
pub(crate) const TYPE_NAMES: &[&str] = &["text","null","FormulaInfoDocument","FormulaInfoBlock","CommentLine","BlankLine","FormulaInfoEntry","FormulaInfoValue","EndOfPart"];
pub(crate) const RULE_IDS: &[&str] = &["FormulaInfo::Document", "FormulaInfo::Block", "FormulaInfo::Filler", "FormulaInfo::CommentLine", "FormulaInfo::BlankLine", "FormulaInfo::SpaceChar", "FormulaInfo::Entry", "FormulaInfo::Value", "FormulaInfo::ContinuationLine", "FormulaInfo::PlainLine", "FormulaInfo::AtLineEnd", "FormulaInfo::EndOfPart", "FormulaInfo::LineBreak"];
/// `super::Ast` と同じ形の、木への参照。
#[derive(Clone, Copy, Debug)]
pub enum Node<'t> { Text(&'t str), Null,
g_FormulaInfoAST_2e_FormulaInfoDocument(g_FormulaInfoAST_2e_FormulaInfoDocument<'t>),
g_FormulaInfoAST_2e_FormulaInfoBlock(g_FormulaInfoAST_2e_FormulaInfoBlock<'t>),
g_FormulaInfoAST_2e_CommentLine(g_FormulaInfoAST_2e_CommentLine<'t>),
g_FormulaInfoAST_2e_BlankLine(g_FormulaInfoAST_2e_BlankLine<'t>),
g_FormulaInfoAST_2e_FormulaInfoEntry(g_FormulaInfoAST_2e_FormulaInfoEntry<'t>),
g_FormulaInfoAST_2e_FormulaInfoValue(g_FormulaInfoAST_2e_FormulaInfoValue<'t>),
g_FormulaInfoAST_2e_EndOfPart(g_FormulaInfoAST_2e_EndOfPart<'t>),
}
impl<'t> NodeRef<'t> {
/// 型ごとの参照へ分ける。
pub fn get(&self) -> Node<'t> { let (tree, id) = (self.tree, self.id); match tree.kind(id) { KIND_TEXT => Node::Text(tree.text_in(&tree.source, id)), KIND_NULL => Node::Null,
K_g_FormulaInfoAST_2e_FormulaInfoDocument => Node::g_FormulaInfoAST_2e_FormulaInfoDocument(g_FormulaInfoAST_2e_FormulaInfoDocument { tree, id }),
K_g_FormulaInfoAST_2e_FormulaInfoBlock => Node::g_FormulaInfoAST_2e_FormulaInfoBlock(g_FormulaInfoAST_2e_FormulaInfoBlock { tree, id }),
K_g_FormulaInfoAST_2e_CommentLine => Node::g_FormulaInfoAST_2e_CommentLine(g_FormulaInfoAST_2e_CommentLine { tree, id }),
K_g_FormulaInfoAST_2e_BlankLine => Node::g_FormulaInfoAST_2e_BlankLine(g_FormulaInfoAST_2e_BlankLine { tree, id }),
K_g_FormulaInfoAST_2e_FormulaInfoEntry => Node::g_FormulaInfoAST_2e_FormulaInfoEntry(g_FormulaInfoAST_2e_FormulaInfoEntry { tree, id }),
K_g_FormulaInfoAST_2e_FormulaInfoValue => Node::g_FormulaInfoAST_2e_FormulaInfoValue(g_FormulaInfoAST_2e_FormulaInfoValue { tree, id }),
K_g_FormulaInfoAST_2e_EndOfPart => Node::g_FormulaInfoAST_2e_EndOfPart(g_FormulaInfoAST_2e_EndOfPart { tree, id }),
_ => unreachable!("unknown AST node kind"), } }
}
#[derive(Clone, Copy)] pub struct g_FormulaInfoAST_2e_FormulaInfoDocument<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_FormulaInfoAST_2e_FormulaInfoDocument<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_FormulaInfoAST_2e_FormulaInfoDocument").field("id", &self.id).finish() } }
impl<'t> g_FormulaInfoAST_2e_FormulaInfoDocument<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_FormulaInfoAST_2e_FormulaInfoDocument { self.tree.project_g_FormulaInfoAST_2e_FormulaInfoDocument(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_blocks(&self) -> NodeList<'t> { NodeList { tree: self.tree, ids: self.tree.items(self.id, 0) } }
}
#[derive(Clone, Copy)] pub struct g_FormulaInfoAST_2e_FormulaInfoBlock<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_FormulaInfoAST_2e_FormulaInfoBlock<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_FormulaInfoAST_2e_FormulaInfoBlock").field("id", &self.id).finish() } }
impl<'t> g_FormulaInfoAST_2e_FormulaInfoBlock<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_FormulaInfoAST_2e_FormulaInfoBlock { self.tree.project_g_FormulaInfoAST_2e_FormulaInfoBlock(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_leading(&self) -> NodeList<'t> { NodeList { tree: self.tree, ids: self.tree.items(self.id, 0) } }
pub fn g_entries(&self) -> NodeList<'t> { NodeList { tree: self.tree, ids: self.tree.items(self.id, 2) } }
pub fn g_end(&self) -> Option<NodeRef<'t>> { let v = self.tree.slot(self.id, 4); (v != NONE).then_some(NodeRef { tree: self.tree, id: v }) }
}
#[derive(Clone, Copy)] pub struct g_FormulaInfoAST_2e_CommentLine<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_FormulaInfoAST_2e_CommentLine<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_FormulaInfoAST_2e_CommentLine").field("id", &self.id).finish() } }
impl<'t> g_FormulaInfoAST_2e_CommentLine<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_FormulaInfoAST_2e_CommentLine { self.tree.project_g_FormulaInfoAST_2e_CommentLine(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_text(&self) -> &'t str { let tree = self.tree; tree.text_in(&tree.source, tree.slot(self.id, 0)) }
}
#[derive(Clone, Copy)] pub struct g_FormulaInfoAST_2e_BlankLine<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_FormulaInfoAST_2e_BlankLine<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_FormulaInfoAST_2e_BlankLine").field("id", &self.id).finish() } }
impl<'t> g_FormulaInfoAST_2e_BlankLine<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_FormulaInfoAST_2e_BlankLine { self.tree.project_g_FormulaInfoAST_2e_BlankLine(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_text(&self) -> &'t str { let tree = self.tree; tree.text_in(&tree.source, tree.slot(self.id, 0)) }
}
#[derive(Clone, Copy)] pub struct g_FormulaInfoAST_2e_FormulaInfoEntry<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_FormulaInfoAST_2e_FormulaInfoEntry<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_FormulaInfoAST_2e_FormulaInfoEntry").field("id", &self.id).finish() } }
impl<'t> g_FormulaInfoAST_2e_FormulaInfoEntry<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_FormulaInfoAST_2e_FormulaInfoEntry { self.tree.project_g_FormulaInfoAST_2e_FormulaInfoEntry(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_key(&self) -> &'t str { let tree = self.tree; tree.text_in(&tree.source, tree.slot(self.id, 0)) }
pub fn g_value(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.tree.slot(self.id, 1) } }
}
#[derive(Clone, Copy)] pub struct g_FormulaInfoAST_2e_FormulaInfoValue<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_FormulaInfoAST_2e_FormulaInfoValue<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_FormulaInfoAST_2e_FormulaInfoValue").field("id", &self.id).finish() } }
impl<'t> g_FormulaInfoAST_2e_FormulaInfoValue<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_FormulaInfoAST_2e_FormulaInfoValue { self.tree.project_g_FormulaInfoAST_2e_FormulaInfoValue(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_text(&self) -> &'t str { let tree = self.tree; tree.text_in(&tree.source, tree.slot(self.id, 0)) }
}
#[derive(Clone, Copy)] pub struct g_FormulaInfoAST_2e_EndOfPart<'t> { tree: &'t AstTree, id: u32 }
impl std::fmt::Debug for g_FormulaInfoAST_2e_EndOfPart<'_> { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { f.debug_struct("g_FormulaInfoAST_2e_EndOfPart").field("id", &self.id).finish() } }
impl<'t> g_FormulaInfoAST_2e_EndOfPart<'t> {
pub fn node(&self) -> NodeRef<'t> { NodeRef { tree: self.tree, id: self.id } }
pub fn span(&self) -> Span { span_of(&self.tree.nodes[self.id as usize]) }
pub fn node_id(&self) -> usize { self.tree.nodes[self.id as usize].node_id as usize }
pub fn to_ast(&self) -> super::g_FormulaInfoAST_2e_EndOfPart { self.tree.project_g_FormulaInfoAST_2e_EndOfPart(&self.tree.source, &self.tree.nodes[self.id as usize]) }
pub fn g_mark(&self) -> &'t str { let tree = self.tree; tree.text_in(&tree.source, tree.slot(self.id, 0)) }
}
impl AstTree {
/// 節点を所有 `Ast` へ写す。`source` は Text の byte 範囲を読む入力。
#[doc(hidden)] pub fn project(&self, source: &str, id: u32) -> super::Ast { let node = self.nodes[id as usize]; match node.kind { KIND_TEXT => super::Ast::Text(source[node.a as usize..node.b as usize].to_owned()), KIND_NULL => super::Ast::Null,
K_g_FormulaInfoAST_2e_FormulaInfoDocument => super::Ast::g_FormulaInfoAST_2e_FormulaInfoDocument(self.project_g_FormulaInfoAST_2e_FormulaInfoDocument(source, &node)),
K_g_FormulaInfoAST_2e_FormulaInfoBlock => super::Ast::g_FormulaInfoAST_2e_FormulaInfoBlock(self.project_g_FormulaInfoAST_2e_FormulaInfoBlock(source, &node)),
K_g_FormulaInfoAST_2e_CommentLine => super::Ast::g_FormulaInfoAST_2e_CommentLine(self.project_g_FormulaInfoAST_2e_CommentLine(source, &node)),
K_g_FormulaInfoAST_2e_BlankLine => super::Ast::g_FormulaInfoAST_2e_BlankLine(self.project_g_FormulaInfoAST_2e_BlankLine(source, &node)),
K_g_FormulaInfoAST_2e_FormulaInfoEntry => super::Ast::g_FormulaInfoAST_2e_FormulaInfoEntry(self.project_g_FormulaInfoAST_2e_FormulaInfoEntry(source, &node)),
K_g_FormulaInfoAST_2e_FormulaInfoValue => super::Ast::g_FormulaInfoAST_2e_FormulaInfoValue(self.project_g_FormulaInfoAST_2e_FormulaInfoValue(source, &node)),
K_g_FormulaInfoAST_2e_EndOfPart => super::Ast::g_FormulaInfoAST_2e_EndOfPart(self.project_g_FormulaInfoAST_2e_EndOfPart(source, &node)),
_ => unreachable!("unknown AST node kind"), } }
fn project_g_FormulaInfoAST_2e_FormulaInfoDocument(&self, source: &str, node: &TreeNode) -> super::g_FormulaInfoAST_2e_FormulaInfoDocument { let f = &self.slots[node.a as usize..node.a as usize + 2]; super::g_FormulaInfoAST_2e_FormulaInfoDocument { span: span_of(node), node_id: node.node_id as usize,
g_blocks: self.list_at(f[0], f[1]).iter().map(|&v| self.project(source, v)).collect(),
} }
fn project_g_FormulaInfoAST_2e_FormulaInfoBlock(&self, source: &str, node: &TreeNode) -> super::g_FormulaInfoAST_2e_FormulaInfoBlock { let f = &self.slots[node.a as usize..node.a as usize + 5]; super::g_FormulaInfoAST_2e_FormulaInfoBlock { span: span_of(node), node_id: node.node_id as usize,
g_leading: self.list_at(f[0], f[1]).iter().map(|&v| self.project(source, v)).collect(),
g_entries: self.list_at(f[2], f[3]).iter().map(|&v| self.project(source, v)).collect(),
g_end: { let v = f[4]; (v != NONE).then(|| Box::new(self.project(source, v))) },
} }
fn project_g_FormulaInfoAST_2e_CommentLine(&self, source: &str, node: &TreeNode) -> super::g_FormulaInfoAST_2e_CommentLine { let f = &self.slots[node.a as usize..node.a as usize + 1]; super::g_FormulaInfoAST_2e_CommentLine { span: span_of(node), node_id: node.node_id as usize,
g_text: self.text_in(source, f[0]).to_owned(),
} }
fn project_g_FormulaInfoAST_2e_BlankLine(&self, source: &str, node: &TreeNode) -> super::g_FormulaInfoAST_2e_BlankLine { let f = &self.slots[node.a as usize..node.a as usize + 1]; super::g_FormulaInfoAST_2e_BlankLine { span: span_of(node), node_id: node.node_id as usize,
g_text: self.text_in(source, f[0]).to_owned(),
} }
fn project_g_FormulaInfoAST_2e_FormulaInfoEntry(&self, source: &str, node: &TreeNode) -> super::g_FormulaInfoAST_2e_FormulaInfoEntry { let f = &self.slots[node.a as usize..node.a as usize + 2]; super::g_FormulaInfoAST_2e_FormulaInfoEntry { span: span_of(node), node_id: node.node_id as usize,
g_key: self.text_in(source, f[0]).to_owned(),
g_value: Box::new(self.project(source, f[1])),
} }
fn project_g_FormulaInfoAST_2e_FormulaInfoValue(&self, source: &str, node: &TreeNode) -> super::g_FormulaInfoAST_2e_FormulaInfoValue { let f = &self.slots[node.a as usize..node.a as usize + 1]; super::g_FormulaInfoAST_2e_FormulaInfoValue { span: span_of(node), node_id: node.node_id as usize,
g_text: self.text_in(source, f[0]).to_owned(),
} }
fn project_g_FormulaInfoAST_2e_EndOfPart(&self, source: &str, node: &TreeNode) -> super::g_FormulaInfoAST_2e_EndOfPart { let f = &self.slots[node.a as usize..node.a as usize + 1]; super::g_FormulaInfoAST_2e_EndOfPart { span: span_of(node), node_id: node.node_id as usize,
g_mark: self.text_in(source, f[0]).to_owned(),
} }
}
impl AstTree {
#[doc(hidden)] pub fn collect_value_spans(&self, source: &str, id: u32, path: &mut String, out: &mut Vec<ValueSpan>) { let _ = &source; let node = self.nodes[id as usize]; match node.kind { KIND_TEXT => out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: Some(self.text_in(source, id).to_owned()) }), KIND_NULL => {},
K_g_FormulaInfoAST_2e_FormulaInfoDocument => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/blocks"); for (i, &v) in self.items(id, 0).iter().enumerate() { let len = path.len(); let _ = std::fmt::Write::write_fmt(path, format_args!("/{i}")); self.collect_value_spans(source, v, path, out); path.truncate(len); } path.truncate(len); }
},
K_g_FormulaInfoAST_2e_FormulaInfoBlock => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/leading"); for (i, &v) in self.items(id, 0).iter().enumerate() { let len = path.len(); let _ = std::fmt::Write::write_fmt(path, format_args!("/{i}")); self.collect_value_spans(source, v, path, out); path.truncate(len); } path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/entries"); for (i, &v) in self.items(id, 2).iter().enumerate() { let len = path.len(); let _ = std::fmt::Write::write_fmt(path, format_args!("/{i}")); self.collect_value_spans(source, v, path, out); path.truncate(len); } path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/end"); let v = self.slot(id, 4); if v != NONE { self.collect_value_spans(source, v, path, out); } path.truncate(len); }
},
K_g_FormulaInfoAST_2e_CommentLine => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/text"); self.text_value_span(self.slot(id, 0), path, out); path.truncate(len); }
},
K_g_FormulaInfoAST_2e_BlankLine => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/text"); self.text_value_span(self.slot(id, 0), path, out); path.truncate(len); }
},
K_g_FormulaInfoAST_2e_FormulaInfoEntry => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/key"); self.text_value_span(self.slot(id, 0), path, out); path.truncate(len); }
{ let len = path.len(); path.push_str("/fields/value"); self.collect_value_spans(source, self.slot(id, 1), path, out); path.truncate(len); }
},
K_g_FormulaInfoAST_2e_FormulaInfoValue => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/text"); self.text_value_span(self.slot(id, 0), path, out); path.truncate(len); }
},
K_g_FormulaInfoAST_2e_EndOfPart => { out.push(ValueSpan { path: path.clone(), span: span_of(&node), text: None });
{ let len = path.len(); path.push_str("/fields/mark"); self.text_value_span(self.slot(id, 0), path, out); path.truncate(len); }
},
_ => unreachable!("unknown AST node kind"), } }
}
#[cfg(feature="json")] impl AstTree {
/// 根の canonical JSON（所有 `Ast::canonical_json` と byte 一致）。
pub fn canonical_json(&self) -> Option<String> { self.root().map(|root| serde_json::to_string(&root.canonical_value()).expect("finite canonical AST")) }
}
#[cfg(feature="json")] impl<'t> NodeRef<'t> {
pub fn canonical_value(&self) -> serde_json::Value { let (tree, id) = (self.tree, self.id); let node = tree.nodes[id as usize]; let text = |v: u32| tree.text_in(&tree.source, v); let value = |v: u32| NodeRef { tree, id: v }.canonical_value(); let _ = (&text, &value); match node.kind { KIND_TEXT => serde_json::Value::String(text(id).to_owned()), KIND_NULL => serde_json::Value::Null,
K_g_FormulaInfoAST_2e_FormulaInfoDocument => serde_json::json!({"fields":{
"blocks":(tree.items(id, 0).iter().map(|&v| value(v)).collect::<Vec<_>>()),
},"span":span_of(&node),"type":"FormulaInfoDocument"}),
K_g_FormulaInfoAST_2e_FormulaInfoBlock => serde_json::json!({"fields":{
"end":({ let v = tree.slot(id, 4); (v != NONE).then(|| value(v)) }),
"entries":(tree.items(id, 2).iter().map(|&v| value(v)).collect::<Vec<_>>()),
"leading":(tree.items(id, 0).iter().map(|&v| value(v)).collect::<Vec<_>>()),
},"span":span_of(&node),"type":"FormulaInfoBlock"}),
K_g_FormulaInfoAST_2e_CommentLine => serde_json::json!({"fields":{
"text":(text(tree.slot(id, 0))),
},"span":span_of(&node),"type":"CommentLine"}),
K_g_FormulaInfoAST_2e_BlankLine => serde_json::json!({"fields":{
"text":(text(tree.slot(id, 0))),
},"span":span_of(&node),"type":"BlankLine"}),
K_g_FormulaInfoAST_2e_FormulaInfoEntry => serde_json::json!({"fields":{
"key":(text(tree.slot(id, 0))),
"value":(value(tree.slot(id, 1))),
},"span":span_of(&node),"type":"FormulaInfoEntry"}),
K_g_FormulaInfoAST_2e_FormulaInfoValue => serde_json::json!({"fields":{
"text":(text(tree.slot(id, 0))),
},"span":span_of(&node),"type":"FormulaInfoValue"}),
K_g_FormulaInfoAST_2e_EndOfPart => serde_json::json!({"fields":{
"mark":(text(tree.slot(id, 0))),
},"span":span_of(&node),"type":"EndOfPart"}),
_ => unreachable!("unknown AST node kind"), } }
}
}
