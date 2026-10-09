package org.unlaxer.tinyexpression.lsp.p4.embedded;

// Port of unlaxer-parser PR #470, d247f34a; shared Java/Rust production fixture contract.
import java.util.*;
import org.unlaxer.*;
import org.unlaxer.context.ParseContext;
import org.unlaxer.tinyexpression.loader.*;
import org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4Parser;
import org.unlaxer.tinyexpression.p4.ubnfc.generated.api.ParseOptions;
import org.unlaxer.source.*;
import org.unlaxer.source.DocumentSnapshot.Span;
import org.unlaxer.source.LanguageRegions.*;

/** Explicit native integration: production TinyExpression parsers, no evaluation or compilation. */
public final class TinyProductionBridge {
    public static final Language FORMULA = new Language("formulainfo", "tinyexpression", "0d84f0dc5c0331c09f46350cceb21cb57ee25d79", "FormulaInfo", "Document");
    public static final Language TINY = new Language("tinyexpression", "tinyexpression", "0d84f0dc5c0331c09f46350cceb21cb57ee25d79", "TinyExpressionP4", "Formula");
    public static final Language JAVA = new Language("java", "lang/java", "0.1.0", "Java21", "CompilationUnit");
    public record Binding(DocumentSnapshot host, List<Region> regions, Map<String,String> javaFiles) {
        public Binding { regions = List.copyOf(regions); javaFiles = Map.copyOf(javaFiles); }
        public LanguageRegions tree() { return new LanguageRegions(host, regions, Set.of()); }
        public LanguageQueries queries(LanguageQueries.Project project, LanguageQueries.Provider javaProvider) {
            return new LanguageQueries(tree(), project, Map.of(JAVA, new LanguageQueries.Provider() {
                public Set<Operation> capabilities() { return javaProvider.capabilities(); }
                public LanguageQueries.Response query(LanguageQueries.Request request) { return javaProvider.query(javaRequest(request)); }
                public LanguageQueries.DiagnosticResponse diagnostics(LanguageQueries.Request request) { return javaProvider.diagnostics(javaRequest(request)); }
            }));
        }
        public LanguageQueries.Request javaRequest(LanguageQueries.Request request) {
            if (!regions.contains(request.region()) || !JAVA.equals(request.region().language())) throw new IllegalArgumentException("foreign region");
            var parameters = new HashMap<>(request.parameters());
            String file = javaFiles.get(request.region().id());
            if (file == null || parameters.containsKey("fileName") && !file.equals(parameters.get("fileName"))) throw new IllegalArgumentException("foreign Java filename");
            parameters.put("fileName", file);
            return new LanguageQueries.Request(request.region(), request.operation(), request.cursor(), request.project(), parameters);
        }
    }
    public static Binding parse(DocumentSnapshot host) {
        if (host.length() > 1_048_576) throw new IllegalArgumentException("production document limit");
        Parsed parsed;
        try (var context = new ParseContext(StringSource.createRootSource(host.text()))) {
            parsed = new FormulaInfoBlocksParser().parse(context);
        }
        if (!parsed.isSucceeded() || parsed.getConsumed() == null || !parsed.getConsumed().source.sourceAsString().equals(host.text()))
            throw new IllegalArgumentException("invalid FormulaInfo document");
        var regions = new ArrayList<Region>(); var files = new LinkedHashMap<String,String>();
        regions.add(region(host, "root", null, FORMULA, new Span(0,host.length()), new Span(0,host.length()), State.COMPLETE));
        int index = 0;
        for (var block : parsed.getRootToken().typed(FormulaInfoBlocksParser.class).getChildrenWithParserAsListTyped(FormulaInfoBlockParser.class)) {
            Token formula = null;
            for (Token token : FormulaInfoElementOrCommentParser.elements(block)) {
                if (!(token.parser instanceof FormulaInfoElementParser)) continue;
                var element = token.typed(FormulaInfoElementParser.class);
                FormulaInfoElementParser.KeyValue pair;
                try { pair = element.getParser().extract(element); }
                catch (FormulaInfoParseException failure) { throw new IllegalArgumentException("invalid FormulaInfo value", failure); }
                if (pair.getKey().equals("formula")) {
                    if (formula != null) throw new IllegalArgumentException("duplicate formula");
                    formula = element.getChild(TokenPredicators.hasTag(FormulaInfoParser.Kind.value.tag()));
                }
            }
            if (formula == null) continue;
            if (++index > 256) throw new IllegalArgumentException("production section limit");
            int start = formula.source.offsetFromRoot().value();
            String raw = formula.source.sourceAsString();
            int skip = raw.startsWith("\r\n") ? 2 : raw.startsWith("\n") || raw.startsWith("\r") ? 1 : 0;
            start += skip; raw = raw.substring(skip);
            Span body = new Span(start, start + raw.codePointCount(0,raw.length()));
            if (!host.slice(body).equals(raw)) throw new IllegalArgumentException("FormulaInfo source mismatch");
            String id = "formula/" + index;
            String input = maskComments(raw);
            var p4 = TinyExpressionP4Parser.parse(input, new ParseOptions(true,true,true,true,org.unlaxer.tinyexpression.p4.ubnfc.P4Scanners.ALL,512));
            regions.add(region(host,id,"root",TINY,body,body,p4.ok() ? State.COMPLETE : State.FAILED));
            if (!p4.ok()) continue; // Never retain children from speculative/failed parses.
            var tokens = p4.lexical().stream().filter(t -> t.token() != null && t.ruleId().equals("TinyExpressionP4::CodeBlock")).toList();
            for (int n=0,number=0;n<tokens.size();number++) {
                var open=tokens.get(n);int at=input.offsetByCodePoints(0,open.span().start());
                if (input.startsWith("````",at)) {
                    var layout=org.unlaxer.tinyexpression.codeblock.LongCodeFence.scan(input,at);
                    if(layout==null || input.codePointCount(0,layout.end())!=open.span().end())
                        throw new IllegalArgumentException("P4 long CodeBlock lexical contract changed");
                    String header=input.substring(at+layout.width(),layout.headerEnd());
                    if(header.startsWith("java:")) {
                        String child=id+"/java/"+(number+1);
                        regions.add(region(host,child,id,JAVA,new Span(start+open.span().start(),start+open.span().end()),
                            new Span(start+input.codePointCount(0,layout.bodyStart()),start+input.codePointCount(0,layout.bodyEnd())),State.COMPLETE));
                        String className=header.substring(5);files.put(child,className.substring(className.lastIndexOf('.')+1)+".java");
                    }
                    n++;continue;
                }
                if(n+2>=tokens.size())throw new IllegalArgumentException("P4 CodeBlock lexical contract changed");
                var close=tokens.get(n+2);n+=3;
                String header=input.substring(at,input.offsetByCodePoints(0,open.span().end())).strip();
                if (!header.startsWith("```java:")) continue;
                String file=header.substring(Math.max(8,header.lastIndexOf('.')+1))+".java";
                String child=id+"/java/"+(number+1);
                Span full=new Span(start+open.span().start(),start+close.span().end());
                Span javaBody=new Span(start+open.span().end(),start+close.span().start());
                regions.add(region(host,child,id,JAVA,full,javaBody,State.COMPLETE)); files.put(child,file);
            }
        }
        if (index==0) throw new IllegalArgumentException("no formula section");
        var result = new Binding(host,regions,files); result.tree(); return result;
    }
    private static String maskComments(String raw) {
        var out = new StringBuilder();int fence=0;
        for (String line : raw.split("(?<=\n)",-1)) {
            if(fence>0) {
                out.append(line);
                if(fence==3 ? line.contains("```") : line.replaceFirst("\\r?\\n$", "").equals("`".repeat(fence)))fence=0;
            } else if(line.startsWith("```")) {
                while(fence<line.length() && line.charAt(fence)=='`')fence++;
                out.append(line);
            } else if (line.stripLeading().startsWith("#")) line.codePoints().forEach(c -> out.appendCodePoint(c=='\r'||c=='\n'?c:' '));
            else out.append(line);
        }
        return out.toString();
    }
    private static Region region(DocumentSnapshot host,String id,String parent,Language language,Span full,Span body,State state) {
        var virtual=new DocumentSnapshot(host.uri()+"#production/"+id,host.version(),host.slice(body));
        var map=new SegmentSourceMap(virtual,List.of(new SegmentSourceMap.Segment(new Span(0,virtual.length()),SegmentSourceMap.Kind.COPY,new SegmentSourceMap.Location(host,body))));
        return new Region(id,parent,language,full,body,map,state);
    }
}
