#!/usr/bin/env python3
"""ubnfc 生成 AST -> tinyexpression 生成 AST の変換器（UbnfcAstConverter）を機械生成する。

移植元: ubnfc examples/p4-java-facade/scripts/generate-converter.py（UBNFC_PIN の commit）。
tinyexpression では生成パーサを org.unlaxer.tinyexpression.p4.ubnfc.generated に置くので、
その package と、mvn compile 後の target/classes を読む形に変えてある。

両者は同じ P4 文法（同じ IR）から生成されており、record 名 86 件・component 名・
順序が完全に一致する（差は SliceExpr の start/end/step の宣言型が
tinyexpression 側で Optional<Object> に広がっているだけ）。よって変換は 1:1 で、
手書きせず生成する。

使い方:
  scripts/generate-ubnfc-converter.py --write|--check [--te-classes PATH] [--ub-ast PATH]
  （先に mvn compile で target/classes を作っておく。CI は --check を走らせる）
"""
import argparse, os, re, subprocess, sys, tempfile

UB = "org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST"
TE = "org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST"

def split_params(s):
    out, depth, cur = [], 0, ""
    for ch in s:
        if ch == "<": depth += 1
        if ch == ">": depth -= 1
        if ch == "," and depth == 0:
            out.append(cur.strip()); cur = ""
        else:
            cur += ch
    if cur.strip(): out.append(cur.strip())
    return out

def normalize_ub(t):
    """ubnfc 生成ソースの短縮表記を完全修飾へ揃える。"""
    return re.sub(r"(?<![\w.])TinyExpressionP4AST(?![\w])", UB, t)


def ub_records(path):
    src = open(path, encoding="utf-8").read()
    recs = {}
    for m in re.finditer(r"record (\w+)\(([^)]*)\)", src):
        params = split_params(m.group(2))
        recs[m.group(1)] = [(normalize_ub(p.rsplit(" ", 1)[0].strip()), p.rsplit(" ", 1)[1].strip())
                            for p in params]
    return recs

def te_records(jar, names):
    out = subprocess.run(["javap", "-cp", jar] + [TE + "$" + n for n in names],
                         capture_output=True, text=True, check=True).stdout
    recs, cur = {}, None
    for line in out.splitlines():
        m = re.match(r"public final class .*TinyExpressionP4AST\$(\w+) extends", line)
        if m:
            cur = m.group(1); recs[cur] = []
            continue
        m = re.match(r"\s+public (?!final)([\w.$<>,? ]+) (\w+)\(\);", line)
        if m and cur and m.group(2) not in ("toString", "hashCode", "equals"):
            recs[cur].append((m.group(1).strip(), m.group(2)))
    return recs

def te_type(t):
    """javap の型を TE のソース表記へ。"""
    return t.replace(TE + "$", TE + ".")

def convert_expr(ut, tt, accessor):
    """ubnfc の component 型 ut（TE の宣言型 tt）を変換する式。"""
    ut = ut.strip()
    if ut in ("java.lang.String", "java.util.Optional<java.lang.String>",
              "java.util.List<java.lang.String>"):
        return accessor
    if ut == "java.lang.Object":
        return "convertAny(%s)" % accessor
    if ut == "java.util.List<java.lang.Object>":
        return "%s.stream().map(this::convertAny).toList()" % accessor
    m = re.fullmatch(r"java\.util\.Optional<(.+)>", ut)
    if m:
        inner = m.group(1)
        tinner = re.fullmatch(r"java\.util\.Optional<(.+)>", te_type(tt))
        target = tinner.group(1) if tinner else te_type(inner)
        if target == "java.lang.Object":
            return "%s.map(v -> (java.lang.Object) convert(v))" % accessor
        return "%s.<%s>map(v -> (%s) convert(v))" % (accessor, target, target)
    m = re.fullmatch(r"java\.util\.List<(.+)>", ut)
    if m:
        inner = m.group(1)
        target = te_type(inner).replace(UB + ".", TE + ".")
        if inner == UB:
            return "%s.stream().map(this::convert).toList()" % accessor
        return "%s.stream().map(v -> (%s) convert(v)).toList()" % (accessor, target)
    if ut == UB:
        return "convert(%s)" % accessor
    if ut.startswith(UB + "."):
        target = ut.replace(UB + ".", TE + ".")
        return "(%s) convert(%s)" % (target, accessor)
    raise SystemExit("未対応の component 型: " + ut)

# 公開 unlaxer-dsl 3.0.15 の生成器と、それ以降の生成器（unlaxer-parser の source pin）とで宣言型が
# 違う component。tinyexpression はどちらの jar でもビルドされる（CI の mapper-compatibility が
# published / source の両方を回す）ので、ここは宣言型に依らない形で出し、実行時に
# VariantShapes が canonical constructor の型を見て合わせる。
#   optionalNode: Optional<X>（新）/ X（公開 3.0.15、無ければ null）
#   sliceIndex:   Optional<Object>（新）/ String（公開 3.0.15: 添字の字面を strip、無ければ ""）
VARIANT = {
    ("ExternalBooleanInvocationExpr", "className"): "optionalNode",
    ("ExternalNumberInvocationExpr", "className"): "optionalNode",
    ("ExternalStringInvocationExpr", "className"): "optionalNode",
    ("ExternalObjectInvocationExpr", "className"): "optionalNode",
    ("SliceExpr", "start"): "sliceIndex",
    ("SliceExpr", "end"): "sliceIndex",
    ("SliceExpr", "step"): "sliceIndex",
}

def variant_expr(kind, accessor):
    if kind == "optionalNode":
        return "new VariantShapes.OptionalNode(%s.map(v -> (java.lang.Object) convert(v)))" % accessor
    return ("new VariantShapes.SliceIndex(%s.map(v -> (java.lang.Object) convert(v)), %s.map(this::sourceText))"
            % (accessor, accessor))

def generate(ub, te):
    lines = []
    a = lines.append
    a("package org.unlaxer.tinyexpression.p4.ubnfc;")
    a("")
    a("import java.util.HashMap;")
    a("import java.util.IdentityHashMap;")
    a("import java.util.Map;")
    a("import org.unlaxer.tinyexpression.p4.ubnfc.generated.api.Span;")
    a("import %s;" % TE)
    a("")
    a("/**")
    a(" * ubnfc 生成 typed AST -> tinyexpression 生成 AST の 1:1 変換。")
    a(" *")
    a(" * <p>{@code scripts/generate-ubnfc-converter.py} が両側の record 定義から機械生成する。")
    a(" * 手で編集しない。86 record すべてで名前・component 名・順序が一致しており、")
    a(" * 変換は構造を変えない（span も code point 半開区間のまま引き継ぐ）。")
    a(" */")
    a("final class UbnfcAstConverter {")
    a("    private final Map<Object, Span> sourceSpans;")
    a("    private final String source;")
    a("    private final Map<Object, int[]> spans = new IdentityHashMap<>();")
    a("    private final Map<String, %s> bestByName = new HashMap<>();" % TE)
    a("    private final Map<String, int[]> bestRank = new HashMap<>();")
    a("    private int depth;")
    a("")
    a("    UbnfcAstConverter(Map<Object, Span> sourceSpans, String source) {")
    a("        this.sourceSpans = sourceSpans;")
    a("        this.source = source;")
    a("    }")
    a("")
    a("    /** 節点の字面（code point 区間、strip 済み）。公開 3.0.15 の {@code firstTokenText} + {@code stripQuotes} と同じ。 */")
    a("    private String sourceText(%s node) {" % UB)
    a("        Span span = sourceSpans.get(node);")
    a("        if (span == null) {")
    a("            throw new IllegalStateException(\"no source span for \" + node.getClass().getSimpleName());")
    a("        }")
    a("        String text = source.substring(source.offsetByCodePoints(0, span.start()),")
    a("            source.offsetByCodePoints(0, span.end())).strip();")
    a("        return text.length() >= 2 && text.charAt(0) == '\\'' && text.charAt(text.length() - 1) == '\\''")
    a("            ? text.substring(1, text.length() - 1) : text;")
    a("    }")
    a("")
    a("    /** 変換後ノード -> code point 半開区間。identity で引く。 */")
    a("    Map<Object, int[]> spans() {")
    a("        return spans;")
    a("    }")
    a("")
    a("    /**")
    a("     * クラス単純名 -> その名前の最良の節点。基準は旧生成 mapper の")
    a("     * {@code findBestMappedToken} と同じ「最小深さ -> 開始位置が大きい方 -> 後に見た方」。")
    a("     * 変換の走査に相乗りするので、候補ごとに木を歩き直さない。")
    a("     */")
    a("    Map<String, %s> bestByName() {" % TE)
    a("        return bestByName;")
    a("    }")
    a("")
    a("    java.lang.Object convertAny(java.lang.Object value) {")
    a("        if (value instanceof %s node) {" % UB)
    a("            return convert(node);")
    a("        }")
    a("        return value;")
    a("    }")
    a("")
    a("    %s convert(%s node) {" % (TE, UB))
    a("        if (node == null) {")
    a("            return null;")
    a("        }")
    a("        int nodeDepth = depth++;")
    # tinyexpression #220: the Java 17 build (tinyExpression-jdk17) compiles this file with
    # --release 17, so dispatch with an ordered instanceof chain instead of a pattern switch.
    a("        final %s converted;" % TE)
    first = True
    for name in ub:
        comps = ub[name]
        tcomps = {n: t for t, n in te[name]}
        variant = any((name, un) in VARIANT for _, un in comps)
        args = ", ".join(
            variant_expr(VARIANT[(name, un)], "n." + un + "()") if (name, un) in VARIANT
            else convert_expr(ut, tcomps[un], "n." + un + "()")
            for ut, un in comps)
        test = ("        if (node instanceof %s.%s n)" if first else "        else if (node instanceof %s.%s n)") % (UB, name)
        first = False
        if variant:
            a("%s converted = (%s.%s) VariantShapes.construct(%s.%s.class, %s);"
              % (test, TE, name, TE, name, args))
        else:
            a("%s converted = new %s.%s(%s);" % (test, TE, name, args))
    a("        else throw new IllegalStateException(\"unhandled node: \" + node);")
    a("        depth = nodeDepth;")
    a("        Span span = sourceSpans.get(node);")
    a("        int start = span == null ? Integer.MIN_VALUE : span.start();")
    a("        if (span != null) {")
    a("            spans.put(converted, new int[] {span.start(), span.end()});")
    a("        }")
    a("        String name = converted.getClass().getSimpleName();")
    a("        int[] rank = bestRank.get(name);")
    a("        if (rank == null || nodeDepth < rank[0]")
    a("            || (nodeDepth == rank[0] && start >= rank[1])) {")
    a("            bestByName.put(name, converted);")
    a("            bestRank.put(name, new int[] {nodeDepth, start});")
    a("        }")
    a("        return converted;")
    a("    }")
    a("}")
    return ("\n".join(lines) + "\n").replace(TE + ".", "TinyExpressionP4AST.")\
        .replace("%s converted" % TE, "TinyExpressionP4AST converted")\
        .replace("    %s convert(" % TE, "    TinyExpressionP4AST convert(")\
        .replace("Map<String, %s>" % TE, "Map<String, TinyExpressionP4AST>")

def main():
    argv = ["mode=" + a.lstrip("-") if a in ("--write", "--check") else a for a in sys.argv[1:]]
    argv = [a.split("=", 1)[1] if a.startswith("mode=") else a for a in argv]
    p = argparse.ArgumentParser()
    p.add_argument("mode", nargs="?", default="write", choices=["write", "check"])
    p.add_argument("--te-classes", "--te-jar", dest="te_classes")
    p.add_argument("--ub-ast")
    p.add_argument("--out")
    args = p.parse_args(argv)
    module = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    te_jar = args.te_classes or os.path.join(module, "target/classes")
    if not os.path.exists(os.path.join(te_jar, "org/unlaxer/tinyexpression/generated/p4")) \
            and not te_jar.endswith(".jar"):
        raise SystemExit("tinyexpression の生成 AST クラスが無い（先に mvn compile）: " + te_jar)
    ub_ast = args.ub_ast or os.path.join(
        module, "src/main/java/org/unlaxer/tinyexpression/p4/ubnfc/generated/TinyExpressionP4AST.java")
    out = args.out or os.path.join(
        module, "src/main/java/org/unlaxer/tinyexpression/p4/ubnfc/UbnfcAstConverter.java")
    ub = ub_records(ub_ast)
    te = te_records(te_jar, list(ub))
    missing = [n for n in ub if n not in te]
    if missing:
        raise SystemExit("tinyexpression 側に無い record: " + ", ".join(missing))
    for n in ub:
        if [c[1] for c in ub[n]] != [c[1] for c in te[n]]:
            raise SystemExit("component が一致しない record: " + n)
    text = generate(ub, te)
    if args.mode == "write":
        open(out, "w", encoding="utf-8").write(text)
        print("書き出し: %s （%d record）" % (out, len(ub)))
    else:
        current = open(out, encoding="utf-8").read() if os.path.exists(out) else ""
        if current != text:
            with tempfile.NamedTemporaryFile("w", suffix=".java", delete=False) as f:
                f.write(text); tmp = f.name
            subprocess.run(["diff", "-u", out, tmp])
            raise SystemExit("生成物が最新ではない: " + out)
        print("一致: %s （%d record）" % (out, len(ub)))

main()
