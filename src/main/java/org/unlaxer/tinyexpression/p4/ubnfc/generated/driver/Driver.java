package org.unlaxer.tinyexpression.p4.ubnfc.generated.driver;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST;
import org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4Parser;
import org.unlaxer.tinyexpression.p4.ubnfc.generated.api.*;

/** Standalone UTF-8 JSON Lines harness driver. stdout contains only responses. */
public final class Driver {
    private Driver() {}
    private static final Set<Class<?>> OPERATOR_TYPES = Set.of(TinyExpressionP4AST.BinaryExpr.class);
    private static final Map<String, String> BINDINGS = Map.ofEntries(Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1009:1018:body/0/0/ruleRef/capture/0","Formula:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1035:1052:body/1/0/ruleRef/capture/0","Formula:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1066:1085:body/2/0/ruleRef/capture/0","Formula:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1121:1131:body/4/ruleRef/capture/0","Formula:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1146:1163:body/5/0/ruleRef/capture/0","Formula:4"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1611:1662:body/0/group/capture/0","CodeBlock:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2127:2136:body/1/ruleRef/capture/0","ImportDeclaration:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2154:2164:body/2/0/1/tokenRef/capture/0","ImportDeclaration:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2180:2190:body/4/tokenRef/capture/0","ImportDeclaration:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2272:2282:body/0/tokenRef/capture/0","ClassName:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2295:2305:body/1/0/1/tokenRef/capture/0","ClassName:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2790:2800:body/2/tokenRef/capture/0","NumberVariableDeclaration:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2847:2859:body/4/0/1/0/ruleRef/capture/0","NumberVariableDeclaration:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2876:2892:body/4/0/2/ruleRef/capture/0","NumberVariableDeclaration:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2908:2919:body/5/0/ruleRef/capture/0","NumberVariableDeclaration:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3139:3149:body/2/tokenRef/capture/0","StringVariableDeclaration:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3196:3208:body/4/0/1/0/ruleRef/capture/0","StringVariableDeclaration:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3225:3241:body/4/0/2/ruleRef/capture/0","StringVariableDeclaration:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3257:3268:body/5/0/ruleRef/capture/0","StringVariableDeclaration:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3490:3500:body/2/tokenRef/capture/0","BooleanVariableDeclaration:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3548:3560:body/4/0/1/0/ruleRef/capture/0","BooleanVariableDeclaration:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3577:3594:body/4/0/2/ruleRef/capture/0","BooleanVariableDeclaration:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3610:3621:body/5/0/ruleRef/capture/0","BooleanVariableDeclaration:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3841:3851:body/2/tokenRef/capture/0","ObjectVariableDeclaration:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3898:3910:body/4/0/1/0/ruleRef/capture/0","ObjectVariableDeclaration:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3927:3943:body/4/0/2/ruleRef/capture/0","ObjectVariableDeclaration:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3959:3970:body/5/0/ruleRef/capture/0","ObjectVariableDeclaration:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5019:5029:body/1/tokenRef/capture/0","NumberMethodDeclaration:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5058:5074:body/3/0/ruleRef/capture/0","NumberMethodDeclaration:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5111:5127:body/6/ruleRef/capture/0","NumberMethodDeclaration:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5345:5355:body/1/tokenRef/capture/0","StringMethodDeclaration:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5384:5400:body/3/0/ruleRef/capture/0","StringMethodDeclaration:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5437:5453:body/6/ruleRef/capture/0","StringMethodDeclaration:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5674:5684:body/1/tokenRef/capture/0","BooleanMethodDeclaration:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5713:5729:body/3/0/ruleRef/capture/0","BooleanMethodDeclaration:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5766:5783:body/6/ruleRef/capture/0","BooleanMethodDeclaration:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6001:6011:body/1/tokenRef/capture/0","ObjectMethodDeclaration:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6040:6056:body/3/0/ruleRef/capture/0","ObjectMethodDeclaration:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6093:6109:body/6/ruleRef/capture/0","ObjectMethodDeclaration:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6206:6221:body/0/ruleRef/capture/0","MethodParameters:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6236:6251:body/1/0/1/ruleRef/capture/0","MethodParameters:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6379:6389:body/1/tokenRef/capture/0","MethodParameter:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6408:6418:body/2/0/1/ruleRef/capture/0","MethodParameter:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7067:7076:body/4/0/0/0/ruleRef/capture/0","ExternalBooleanInvocation:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7092:7102:body/4/0/0/2/tokenRef/capture/0","ExternalBooleanInvocation:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7111:7121:body/4/0/1/tokenRef/capture/0","ExternalBooleanInvocation:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7140:7149:body/6/0/ruleRef/capture/0","ExternalBooleanInvocation:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7358:7367:body/2/0/0/0/ruleRef/capture/0","ExternalNumberInvocation:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7383:7393:body/2/0/0/2/tokenRef/capture/0","ExternalNumberInvocation:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7402:7412:body/2/0/1/tokenRef/capture/0","ExternalNumberInvocation:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7431:7440:body/4/0/ruleRef/capture/0","ExternalNumberInvocation:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7635:7644:body/4/0/0/0/ruleRef/capture/0","ExternalStringInvocation:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7660:7670:body/4/0/0/2/tokenRef/capture/0","ExternalStringInvocation:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7679:7689:body/4/0/1/tokenRef/capture/0","ExternalStringInvocation:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7708:7717:body/6/0/ruleRef/capture/0","ExternalStringInvocation:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7912:7921:body/4/0/0/0/ruleRef/capture/0","ExternalObjectInvocation:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7937:7947:body/4/0/0/2/tokenRef/capture/0","ExternalObjectInvocation:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7956:7966:body/4/0/1/tokenRef/capture/0","ExternalObjectInvocation:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7985:7994:body/6/0/ruleRef/capture/0","ExternalObjectInvocation:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8371:8381:body/1/tokenRef/capture/0","MethodInvocation:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8394:8403:body/3/0/ruleRef/capture/0","MethodInvocation:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8582:8599:body/0/ruleRef/capture/0","ArgumentTernary:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8615:8631:body/2/ruleRef/capture/0","ArgumentTernary:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8646:8662:body/4/ruleRef/capture/0","ArgumentTernary:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8758:8773:body/0/ruleRef/capture/0","ArgumentExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8787:8807:body/1/ruleRef/capture/0","ArgumentExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8821:8847:body/2/ruleRef/capture/0","ArgumentExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8861:8871:body/3/ruleRef/capture/0","ArgumentExpression:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8941:8959:body/0/ruleRef/capture/0","Arguments:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8974:8992:body/1/0/1/ruleRef/capture/0","Arguments:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9197:9207:body/0/ruleRef/capture/0","NumberExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9216:9221:body/1/0/0/ruleRef/capture/0","NumberExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9226:9236:body/1/0/1/ruleRef/capture/0","NumberExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9352:9364:body/0/ruleRef/capture/0","NumberTerm:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9373:9378:body/1/0/0/ruleRef/capture/0","NumberTerm:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9383:9395:body/1/0/1/ruleRef/capture/0","NumberTerm:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9881:9899:body/2/ruleRef/capture/0","SinFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9974:9992:body/2/ruleRef/capture/0","CosFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10067:10085:body/2/ruleRef/capture/0","TanFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10163:10181:body/2/ruleRef/capture/0","SqrtFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10264:10282:body/2/ruleRef/capture/0","MinFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10296:10314:body/3/0/1/ruleRef/capture/0","MinFunction:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10400:10418:body/2/ruleRef/capture/0","MaxFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10432:10450:body/3/0/1/ruleRef/capture/0","MaxFunction:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10592:10610:body/2/ruleRef/capture/0","AbsFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10691:10709:body/2/ruleRef/capture/0","RoundFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10787:10805:body/2/ruleRef/capture/0","CeilFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10886:10904:body/2/ruleRef/capture/0","FloorFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10990:11008:body/2/ruleRef/capture/0","PowFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11019:11037:body/4/ruleRef/capture/0","PowFunction:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11117:11135:body/2/ruleRef/capture/0","LogFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11210:11228:body/2/ruleRef/capture/0","ExpFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11403:11419:body/2/ruleRef/capture/0","ToNumFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11431:11449:body/4/ruleRef/capture/0","ToNumFunction:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11947:11963:body/2/ruleRef/capture/0","ToUpperCaseFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12066:12082:body/2/ruleRef/capture/0","ToLowerCaseFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12164:12180:body/2/ruleRef/capture/0","TrimFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12268:12284:body/2/ruleRef/capture/0","LengthFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12366:12382:body/2/ruleRef/capture/0","LenFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12718:12729:body/0/ruleRef/capture/0","ToUpperCaseDotMethod:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12837:12848:body/0/ruleRef/capture/0","ToLowerCaseDotMethod:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12942:12953:body/0/ruleRef/capture/0","TrimDotMethod:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13044:13055:body/0/ruleRef/capture/0","LengthDotMethod:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13317:13333:body/2/ruleRef/capture/0","StartsWithFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13345:13361:body/4/ruleRef/capture/0","StartsWithFunction:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13382:13398:body/5/0/1/ruleRef/capture/0","StartsWithFunction:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13507:13523:body/2/ruleRef/capture/0","EndsWithFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13535:13551:body/4/ruleRef/capture/0","EndsWithFunction:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13572:13588:body/5/0/1/ruleRef/capture/0","EndsWithFunction:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13697:13713:body/2/ruleRef/capture/0","ContainsFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13725:13741:body/4/ruleRef/capture/0","ContainsFunction:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13762:13778:body/5/0/1/ruleRef/capture/0","ContainsFunction:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13860:13876:body/0/ruleRef/capture/0","InMethod:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13894:13910:body/3/ruleRef/capture/0","InMethod:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13929:13945:body/4/0/1/ruleRef/capture/0","InMethod:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14128:14151:body/0/ruleRef/capture/0","StartsWithDotMethod:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14177:14193:body/3/ruleRef/capture/0","StartsWithDotMethod:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14214:14230:body/4/0/1/ruleRef/capture/0","StartsWithDotMethod:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14328:14351:body/0/ruleRef/capture/0","EndsWithDotMethod:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14375:14391:body/3/ruleRef/capture/0","EndsWithDotMethod:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14412:14428:body/4/0/1/ruleRef/capture/0","EndsWithDotMethod:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14526:14549:body/0/ruleRef/capture/0","ContainsDotMethod:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14573:14589:body/3/ruleRef/capture/0","ContainsDotMethod:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14610:14626:body/4/0/1/ruleRef/capture/0","ContainsDotMethod:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14911:14922:body/2/ruleRef/capture/0","IsPresentFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15115:15131:body/2/ruleRef/capture/0","InTimeRangeFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15147:15163:body/4/ruleRef/capture/0","InTimeRangeFunction:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15308:15317:body/2/ruleRef/capture/0","InDayTimeRangeFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15332:15348:body/4/ruleRef/capture/0","InDayTimeRangeFunction:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15364:15373:body/6/ruleRef/capture/0","InDayTimeRangeFunction:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15386:15402:body/8/ruleRef/capture/0","InDayTimeRangeFunction:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16660:16677:body/0/0/ruleRef/capture/0","SliceBaseExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16689:16704:body/0/2/ruleRef/capture/0","SliceBaseExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16716:16729:body/0/4/ruleRef/capture/0","SliceBaseExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16739:16753:body/0/6/ruleRef/capture/0","SliceBaseExpression:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16770:16787:body/1/0/ruleRef/capture/0","SliceBaseExpression:4"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16799:16814:body/1/2/ruleRef/capture/0","SliceBaseExpression:5"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16826:16839:body/1/4/ruleRef/capture/0","SliceBaseExpression:6"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16855:16872:body/2/0/ruleRef/capture/0","SliceBaseExpression:7"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16884:16899:body/2/2/ruleRef/capture/0","SliceBaseExpression:8"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16915:16929:body/2/5/ruleRef/capture/0","SliceBaseExpression:9"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16946:16963:body/3/0/ruleRef/capture/0","SliceBaseExpression:10"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16975:16990:body/3/2/ruleRef/capture/0","SliceBaseExpression:11"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17012:17029:body/4/0/ruleRef/capture/0","SliceBaseExpression:12"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17045:17058:body/4/3/ruleRef/capture/0","SliceBaseExpression:13"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17068:17082:body/4/5/ruleRef/capture/0","SliceBaseExpression:14"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17099:17116:body/5/0/ruleRef/capture/0","SliceBaseExpression:15"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17132:17145:body/5/3/ruleRef/capture/0","SliceBaseExpression:16"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17161:17178:body/6/0/ruleRef/capture/0","SliceBaseExpression:17"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17198:17212:body/6/4/ruleRef/capture/0","SliceBaseExpression:18"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17229:17246:body/7/0/ruleRef/capture/0","SliceBaseExpression:19"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17359:17378:body/0/0/ruleRef/capture/0","SliceNestedExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17390:17405:body/0/2/ruleRef/capture/0","SliceNestedExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17417:17430:body/0/4/ruleRef/capture/0","SliceNestedExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17440:17454:body/0/6/ruleRef/capture/0","SliceNestedExpression:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17471:17490:body/1/0/ruleRef/capture/0","SliceNestedExpression:4"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17502:17517:body/1/2/ruleRef/capture/0","SliceNestedExpression:5"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17529:17542:body/1/4/ruleRef/capture/0","SliceNestedExpression:6"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17558:17577:body/2/0/ruleRef/capture/0","SliceNestedExpression:7"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17589:17604:body/2/2/ruleRef/capture/0","SliceNestedExpression:8"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17620:17634:body/2/5/ruleRef/capture/0","SliceNestedExpression:9"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17651:17670:body/3/0/ruleRef/capture/0","SliceNestedExpression:10"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17682:17697:body/3/2/ruleRef/capture/0","SliceNestedExpression:11"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17719:17738:body/4/0/ruleRef/capture/0","SliceNestedExpression:12"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17754:17767:body/4/3/ruleRef/capture/0","SliceNestedExpression:13"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17777:17791:body/4/5/ruleRef/capture/0","SliceNestedExpression:14"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17808:17827:body/5/0/ruleRef/capture/0","SliceNestedExpression:15"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17843:17856:body/5/3/ruleRef/capture/0","SliceNestedExpression:16"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17872:17891:body/6/0/ruleRef/capture/0","SliceNestedExpression:17"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17911:17925:body/6/4/ruleRef/capture/0","SliceNestedExpression:18"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17942:17961:body/7/0/ruleRef/capture/0","SliceNestedExpression:19"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18250:18260:body/0/ruleRef/capture/0","StringExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18269:18272:body/1/0/0/literal/capture/0","StringExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18277:18287:body/1/0/1/ruleRef/capture/0","StringExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19048:19058:body/4/tokenRef/capture/0","StringCastVariable:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19152:19162:body/1/tokenRef/capture/0","StringTypedVariable:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19428:19448:body/0/ruleRef/capture/0","BooleanExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19457:19460:body/1/0/0/literal/capture/0","BooleanExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19465:19485:body/1/0/1/ruleRef/capture/0","BooleanExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19631:19651:body/0/ruleRef/capture/0","BooleanAndExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19660:19663:body/1/0/0/literal/capture/0","BooleanAndExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19668:19688:body/1/0/1/ruleRef/capture/0","BooleanAndExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19834:19847:body/0/ruleRef/capture/0","BooleanXorExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19856:19859:body/1/0/0/literal/capture/0","BooleanXorExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19864:19877:body/1/0/1/ruleRef/capture/0","BooleanXorExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20034:20051:body/2/ruleRef/capture/0","NotExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20622:20639:body/0/ruleRef/capture/0","BooleanEqualityExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20646:20656:body/1/ruleRef/capture/0","BooleanEqualityExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20661:20678:body/2/ruleRef/capture/0","BooleanEqualityExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20761:20786:body/0/ruleRef/capture/0","BooleanFactor:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20800:20820:body/1/ruleRef/capture/0","BooleanFactor:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20834:20860:body/2/ruleRef/capture/0","BooleanFactor:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20874:20891:body/3/ruleRef/capture/0","BooleanFactor:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21155:21171:body/0/ruleRef/capture/0","StringComparisonExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21178:21188:body/1/ruleRef/capture/0","StringComparisonExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21193:21209:body/2/ruleRef/capture/0","StringComparisonExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21331:21347:body/0/ruleRef/capture/0","ComparisonExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21354:21363:body/1/ruleRef/capture/0","ComparisonExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21368:21384:body/2/ruleRef/capture/0","ComparisonExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21601:21617:body/0/ruleRef/capture/0","ObjectExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21631:21647:body/1/ruleRef/capture/0","ObjectExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21661:21678:body/2/ruleRef/capture/0","ObjectExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21692:21716:body/3/ruleRef/capture/0","ObjectExpression:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21730:21741:body/4/ruleRef/capture/0","ObjectExpression:4"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21755:21771:body/5/ruleRef/capture/0","ObjectExpression:5"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21955:21972:body/2/ruleRef/capture/0","IfExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21996:22012:body/5/ruleRef/capture/0","IfExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22046:22062:body/9/ruleRef/capture/0","IfExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22158:22178:body/0/ruleRef/capture/0","BranchExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22192:22218:body/1/ruleRef/capture/0","BranchExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22232:22257:body/2/ruleRef/capture/0","BranchExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22271:22287:body/3/ruleRef/capture/0","BranchExpression:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22301:22318:body/4/ruleRef/capture/0","BranchExpression:4"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22332:22348:body/5/ruleRef/capture/0","BranchExpression:5"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22362:22378:body/6/ruleRef/capture/0","BranchExpression:6"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22392:22408:body/7/ruleRef/capture/0","BranchExpression:7"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22587:22604:body/1/ruleRef/capture/0","TernaryExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22620:22636:body/3/ruleRef/capture/0","TernaryExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22651:22667:body/5/ruleRef/capture/0","TernaryExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22889:22899:body/2/ruleRef/capture/0","NumberMatchExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22917:22927:body/3/0/1/ruleRef/capture/0","NumberMatchExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22951:22968:body/5/ruleRef/capture/0","NumberMatchExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23064:23081:body/0/ruleRef/capture/0","NumberCase:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23098:23113:body/2/ruleRef/capture/0","NumberCase:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23212:23227:body/2/ruleRef/capture/0","NumberDefaultCase:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23307:23323:body/0/ruleRef/capture/0","NumberCaseValue:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23456:23466:body/2/ruleRef/capture/0","StringMatchExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23484:23494:body/3/0/1/ruleRef/capture/0","StringMatchExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23518:23535:body/5/ruleRef/capture/0","StringMatchExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23631:23648:body/0/ruleRef/capture/0","StringCase:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23665:23680:body/2/ruleRef/capture/0","StringCase:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23779:23794:body/2/ruleRef/capture/0","StringDefaultCase:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23874:23890:body/0/ruleRef/capture/0","StringCaseValue:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24025:24036:body/2/ruleRef/capture/0","BooleanMatchExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24054:24065:body/3/0/1/ruleRef/capture/0","BooleanMatchExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24089:24107:body/5/ruleRef/capture/0","BooleanMatchExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24205:24222:body/0/ruleRef/capture/0","BooleanCase:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24239:24255:body/2/ruleRef/capture/0","BooleanCase:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24356:24372:body/2/ruleRef/capture/0","BooleanDefaultCase:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24454:24471:body/0/ruleRef/capture/0","BooleanCaseValue:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24688:24698:body/1/tokenRef/capture/0","VariableRef:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24716:24727:body/2/0/1/ruleRef/capture/0","VariableRef:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25187:25203:body/0/ruleRef/capture/0","Expression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25217:25234:body/1/ruleRef/capture/0","Expression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25248:25264:body/2/ruleRef/capture/0","Expression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25278:25294:body/3/ruleRef/capture/0","Expression:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25308:25324:body/4/ruleRef/capture/0","Expression:4"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25342:25352:body/5/1/ruleRef/capture/0","Expression:5"));
    /** D-025: `@catalog` の静的表。入力に依存しない grammar metadata。 */
    private static final List<Object> CATALOGS = List.of(object("rule", "VariableRef", "context", "variable", "captures", List.of("name", "type")));
    /** D-025: 文法が宣言した scope mode（観測されたイベントの mode ではない）。 */
    private static final List<String> SCOPE_MODES = List.of("lexical");
    private static final Pattern NUMBER = Pattern.compile("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?");

    private static final Map<String, String> EXTERN_CLASSES = Map.ofEntries();
    private static Map<String, TokenScanner> scanners = Map.of();
    /** D-080: 起動時の走査スイッチ（{@code -Dubnfc.scan}）。{@code options.scan} の無い要求はこれに戻す。 */
    private static final boolean SCAN_DEFAULT = org.unlaxer.tinyexpression.p4.ubnfc.generated.internal.Session.scanning;
    private static java.net.URLClassLoader scannerLoader;

    // registry は対象生成物の api.TokenScanner でコンパイルする。クラス名から論理 ID に結び直す。
    private static Map<String, TokenScanner> loadScanners(String[] args) throws java.io.IOException {
        String path = System.getenv("UBNFC_SCANNERS");
        for (int i = 0; i < args.length; i++) {
            if (!args[i].equals("--scanners") || ++i == args.length)
                throw new IllegalArgumentException("--scanners <dir|jar> が必要です");
            path = args[i];
        }
        if (path == null || path.isBlank()) return Map.of();
        try {
            scannerLoader = new java.net.URLClassLoader(new java.net.URL[]{java.nio.file.Path.of(path).toUri().toURL()}, Driver.class.getClassLoader());
            Object registry = scannerLoader.loadClass("ubnfc.scanners.Registry").getMethod("scanners").invoke(null);
            if (!(registry instanceof Map<?, ?> entries)) throw new IllegalArgumentException("scanner registry は Map が必要です");
            var result = new LinkedHashMap<String, TokenScanner>();
            for (var binding : EXTERN_CLASSES.entrySet()) {
                Object scanner = entries.get(binding.getValue());
                if (scanner == null) throw new IllegalArgumentException("未登録の Extern クラス: " + binding.getValue());
                if (!(scanner instanceof TokenScanner typed)) throw new IllegalArgumentException("不正な TokenScanner: " + binding.getValue());
                result.put(binding.getKey(), typed);
            }
            return Map.copyOf(result);
        } catch (ReflectiveOperationException error) {
            throw new java.io.IOException("scanner registry をロードできません", error);
        }
    }
    public static void main(String[] args) throws java.io.IOException {
        scanners = loadScanners(args);
        var input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        var output = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8));
        for (String line; (line = input.readLine()) != null;) {
            Object id = null;
            String response;
            try {
                Map<String, Object> request = Json.object(Json.read(line));
                id = request.get("id");
                if (!request.containsKey("id")) throw new IllegalArgumentException("Missing id");
                response = Json.write(execute(request));
            } catch (RuntimeException error) {
                response = Json.write(object("id", id, "error", error.toString()));
            }
            output.println(response);
            output.flush();
            if (output.checkError()) throw new java.io.IOException("Cannot write stdout");
        }
    }
    private static String string(Map<?, ?> request, String key) {
        if (request.get(key) instanceof String value) return value;
        throw new IllegalArgumentException(key + " must be a string");
    }
    private static boolean flag(Map<?, ?> options, String key) {
        if (options.get(key) instanceof Boolean value) return value;
        throw new IllegalArgumentException(key + " must be a boolean");
    }
    private static Map<String, Object> object(Object... fields) {
        var map = new LinkedHashMap<String, Object>();
        for (int i = 0; i < fields.length; i += 2) map.put((String) fields[i], fields[i + 1]);
        return map;
    }
    private static Object execute(Map<String, Object> request) {
        String grammar = string(request, "grammar"), input = string(request, "input");
        if (!request.containsKey("entry")) throw new IllegalArgumentException("Missing entry");
        String entry = request.get("entry") == null ? null : string(request, "entry");
        Map<String, Object> options = Json.object(request.get("options"));
        boolean lexical = flag(options, "lexical"), scope = flag(options, "scope");
        // D-072 / D-080 の A/B 用の任意キー（Rust 生成 driver と同じ意味）。`options.scan` があるときは
        // 「fast mode（診断・出現表なし、lexical は false 扱い）で走らせて一括走査だけを切り替える」。
        // 走査は fast mode でしか効かないので、同じ条件で 2 度測る。キーが無ければ従来どおり。
        boolean scanAb = options.containsKey("scan");
        org.unlaxer.tinyexpression.p4.ubnfc.generated.internal.Session.scanning = scanAb ? flag(options, "scan") : SCAN_DEFAULT;
        if (scanAb) lexical = false;
        Map<String, Object> observations = request.get("observations") instanceof Map<?, ?> ? Json.object(request.get("observations")) : Map.of();
        int maxDepth = options.get("maxDepth") instanceof Number number ? new java.math.BigDecimal(number.toString()).intValueExact() : ParseOptions.DEFAULT_MAX_DEPTH;
        var prefixOptions = new ParseOptions(false, false, lexical, true, scanners, maxDepth);
        var fullOptions = new ParseOptions(true, true, lexical, true, scanners, maxDepth);
        if (scanAb) { prefixOptions = prefixOptions.withObservations(false, false); fullOptions = fullOptions.withObservations(false, false); }
        var prefix = TinyExpressionP4Parser.parseEntry(grammar, entry, input, prefixOptions);
        ParseResult<?> result;
        String mapping = null, mappingKind = null, mappingMessage = null;
        try {
            result = TinyExpressionP4Parser.parseEntry(grammar, entry, input, fullOptions);
        } catch (MappingException error) {
            result = error.syntax();
            mapping = error.getCause() == null ? error.getMessage() : error.getCause().toString();
            mappingMessage = error.getMessage();
            mappingKind = (error.getCause() == null ? error : error.getCause()).getClass().getName();
        }
        Object ast = Json.canonicalValue(result);
        Object value;
        boolean evaluationError = false;
        try { value = evaluate(ast, observations.get("family"), result.ast().isPresent()
            && OPERATOR_TYPES.contains(result.ast().get().getClass())); }
        catch (IllegalArgumentException error) {
            if (!observations.containsKey("family")) throw error;
            value = UNOBSERVED; evaluationError = true;
        }
        if (ast == null && result.ok() && mapping == null) value = UNOBSERVED;
        var diagnostics = new ArrayList<Object>();
        for (var recovery : result.recoveries()) {
            Diagnostic d = recovery.diagnostic();
            diagnostics.add(object("kind", d.kind(), "offset", d.offset(), "length", d.length(), "severity", d.severity(),
                "origin", d.origin(), "ruleId", recovery.ruleId(), "expected", d.expected(),
                "farthestOffset", d.farthestCp(), "farthestExpected", d.farthestExpected()));
        }
        Diagnostic diagnostic = result.diagnostics();
        String primaryKind = diagnostic.kind().equals("none") ? "syntax" : diagnostic.kind();
        // D-026: 全入力解析が成功したなら、成功経路内の投機的失敗は診断ではない（hints の領分）。
        boolean speculative = result.ok() && (primaryKind.equals("syntax") || primaryKind.equals("trailing_input"));
        if (!speculative
            && (!diagnostic.kind().equals("none") || result.recoveries().isEmpty() && !diagnostic.expected().isEmpty())
            && (diagnostic.farthestCp() >= 0 || !diagnostic.expected().isEmpty()))
            diagnostics.add(object("kind", primaryKind,
                "offset", diagnostic.offset() < 0 ? diagnostic.farthestCp() : diagnostic.offset(),
                "expected", diagnostic.expected(), "farthestOffset", diagnostic.farthestCp(), "farthestExpected", diagnostic.farthestExpected(), "origin", "Mapper.diagnose"));
        for (var diagnosticEvent : result.scope().diagnostics())
            diagnostics.add(object("kind", diagnosticEvent.severity().name(), "offset", diagnosticEvent.offset(), "expected", List.of(),
                "message", diagnosticEvent.msg(), "length", diagnosticEvent.len(), "severity", diagnosticEvent.severity().name(), "origin", "ScopeStore"));
        var response = object("id", request.get("id"), "accepted", prefix.ok(), "allConsumed", result.ok(),
            "consumed", prefix.consumedCp(), "matched", prefix.matchedCp(),
            // D-024: allConsumed は全入力解析の成否。prefix 側の consumed からは導けない。
            "fullConsumed", result.consumedCp(), "fullMatched", result.matchedCp(), "ast", ast,
            "diagnostics", diagnostics, "mappingError", mapping, "parsedStatus", prefix.ok() ? "succeeded" : "failed",
            "catalogs", CATALOGS);
        if (value != UNOBSERVED) response.put("value", value);
        if (evaluationError) response.put("evaluationError", true);
        if (mappingKind != null) { response.put("mappingErrorKind", mappingKind); response.put("mappingMessage", mappingMessage); }
        var failure = prefix.nativeFailure();
        if (failure != null) response.put("nativeFailure", object("offset", failure.offset(), "consumed", failure.consumed(),
            "matched", failure.matched(), "expected", failure.expected()));
        response.put("recoveries", result.recoveries().stream().map(r -> object("ruleId", r.ruleId(), "mode", r.mode(),
            "span", List.of(r.span().start(), r.span().end()), "syncPattern", r.syncPattern(),
            "skippedSpan", List.of(r.skippedSpan().start(), r.skippedSpan().end()),
            "syncSpan", r.syncSpan() == null ? null : List.of(r.syncSpan().start(), r.syncSpan().end()),
            "captureOccurrences", r.captureOccurrences())).toList());
        if (lexical) {
            response.put("captures", captures(result, input, observations.containsKey("captureNames"), "oracle".equals(observations.get("family"))));
            response.put("prefixCaptures", captures(prefix, input, false, true));
            response.put("lexical", result.lexical().stream().map(item -> object("occurrenceId", item.occurrenceId(),
                "parentOccurrenceId", item.parentOccurrenceId(), "ruleId", item.ruleId(), "exprId", item.exprId(),
                "span", List.of(item.span().start(), item.span().end()), "completionOrder", item.completionOrder(), "token", item.token())).toList());
        }
        if (scope) {
            Map<String, Object> state = scope(result, observations);
            response.put("declarations", state.get("declarations"));
            response.put("references", state.get("references"));
            response.put("scope", state);
            response.put("scopeDepth", result.scope().depth());
            if (Boolean.TRUE.equals(observations.get("parentRollback"))) {
                var rolled = TinyExpressionP4Parser.parseEntryRollback(grammar, entry, input, new ParseOptions(false, false, lexical, true, scanners, maxDepth));
                var rollbackScope = scope(rolled, observations); rollbackScope.remove("events"); rollbackScope.remove("mode");
                response.put("rollback", object("cursor", List.of(rolled.consumedCp(), rolled.matchedCp()), "scope", rollbackScope));
            }
        }
        if (ast != null) {
            var nodes = new ArrayList<Object>();
            collectNodes(ast, nodes);
            response.put("nodes", nodes);
            var texts = new ArrayList<Object>();
            collectTexts(result.ast().orElse(null), result.nodeSpans(), texts);
            response.put("texts", texts);
            if (observations.containsKey("captureNames")) { response.put("valueSpans", List.of()); response.put("retainedValueSpans", List.of()); }
            if (observations.get("valueSpanField") instanceof String field && ast instanceof Map<?, ?> node && node.get("fields") instanceof Map<?, ?> fields) {
                Object root = result.ast().orElse(null);
                var selected = new ArrayList<Object>(values(field(root, field)));
                if (observations.get("valueSpanListField") instanceof String listField) selected.addAll(values(field(root, listField)));
                var valueSpans = new ArrayList<Object>();
                for (Object selectedValue : selected) {
                    Span span = result.nodeSpans().get(selectedValue);
                    if (span != null) valueSpans.add(List.of(span.start(), span.end()));
                }
                response.put("valueSpans", valueSpans); response.put("retainedValueSpans", valueSpans);
            }
        }
        return response;
    }
    private static List<Object> captures(ParseResult<?> result, String input, boolean ruleCompletion, boolean bindings) {
        var captures = new ArrayList<Object>();
        var selected = ruleCompletion ? result.captures().stream().sorted(java.util.Comparator.comparingLong(Capture::ruleCompletionOrder)).toList() : result.captures();
        for (Capture capture : selected) {
            var item = object("name", capture.name(), "span", List.of(capture.span().start(), capture.span().end()),
                "text", input.substring(input.offsetByCodePoints(0, capture.span().start()), input.offsetByCodePoints(0, capture.span().end())));
            if (bindings) item.put("binding", BINDINGS.get(capture.siteId()));
            captures.add(item);
        }
        return captures;
    }
    private static Map<String, Object> scope(ParseResult<?> result, Map<String, Object> observations) {
        var scope = result.scope();
        var state = object("depth", scope.depth(), "mode", SCOPE_MODES,
            "events", scope.events().stream().filter(e -> Set.of("enter", "leave", "declare", "use").contains(e.action()))
                .map(e -> object("order", e.order(), "action", e.action().equals("use") ? "reference" : e.action(), "mode", e.mode(),
                    "name", e.name(), "offset", e.offsetCp(), "length", e.lengthCp())).toList(),
            "declarations", scope.declarations().stream().map(d -> object("name", d.name(), "sourceOffset", d.offsetCp())).toList(),
            "references", scope.references().stream().map(r -> object("name", r.name(), "offset", r.offset(), "length", r.len())).toList(),
            "diagnostics", scope.diagnostics().stream().map(d -> object("message", d.msg(), "offset", d.offset(), "length", d.len(), "severity", d.severity().name())).toList());
        if (observations.get("queries") instanceof List<?> queries) state.put("resolved", queries.stream().map(q -> {
            var declaration = scope.resolved().get(q);
            return object("query", q, "symbol", declaration == null ? null : object("name", declaration.name(), "sourceOffset", declaration.offsetCp()));
        }).toList());
        return state;
    }
    private static final Object UNOBSERVED = new Object();
    private static List<?> values(Object value) { return value == null ? List.of() : value instanceof List<?> list ? list : List.of(value); }
    private static String tagged(Object value) {
        if (value instanceof String text) return "T:" + text;
        if (value instanceof Map<?, ?> node && node.get("fields") instanceof Map<?, ?> fields) {
            Object text = fields.containsKey("text") ? fields.get("text") : fields.get("value");
            return switch (String.valueOf(node.get("type"))) {
                case "Leaf" -> "L:" + text;
                case "Other", "OtherLeaf" -> "O:" + text;
                case "Value", "Shared" -> tagged(text);
                default -> throw new IllegalArgumentException("Unsupported semantic value: " + node.get("type"));
            };
        }
        throw new IllegalArgumentException("Unsupported semantic value");
    }
    private static String tags(Object value) { return values(value).stream().map(Driver::tagged).collect(java.util.stream.Collectors.joining(",")); }
    private static Object evaluate(Object ast, Object family, boolean operator) {
        if (ast == null) return null;
        if (!(ast instanceof Map<?, ?> node) || !(node.get("fields") instanceof Map<?, ?> fields)) return UNOBSERVED;
        if ("semantic-cardinality".equals(family) && fields.containsKey("values")) return tags(fields.get("values"));
        if ("mixed-values".equals(family)) {
            if (fields.containsKey("head") && fields.containsKey("maybe") && fields.containsKey("items"))
                return tagged(fields.get("head")) + "|" + (fields.get("maybe") == null ? "-" : tagged(fields.get("maybe"))) + "|" + tags(fields.get("items"));
            if (fields.containsKey("value")) return tagged(fields.get("value"));
        }
        if ("cardinality".equals(family) && fields.containsKey("flags")) {
            double total = fields.get("head") == null ? 0 : numeric(fields.get("head"));
            for (Object item : values(fields.get("values"))) total += numeric(item);
            return total + (fields.get("tail") == null ? 0 : 1) + values(fields.get("flags")).size();
        }
        if (operator || Set.of("Number", "Literal", "Binary", "Power", "Negation", "Conditional").contains(node.get("type")) || "right-associative".equals(family) || "associative".equals(family) || "evolution".equals(family)) {
            Double value = numeric(ast); return value == null ? UNOBSERVED : value;
        }
        return UNOBSERVED;
    }
    private static Object field(Object node, String name) {
        if (node == null || !node.getClass().isRecord()) return null;
        for (var component : node.getClass().getRecordComponents()) if (component.getName().equals(name)) {
            try { Object value = component.getAccessor().invoke(node); return value instanceof java.util.Optional<?> optional ? optional.orElse(null) : value; }
            catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
        }
        return null;
    }
    private static void collectTexts(Object value, Map<Object, Span> spans, List<Object> texts) {
        if (value instanceof String text) {
            Span span = spans.get(value);
            if (span != null) texts.add(List.of(span.start(), span.end(), text));
        }
        else if (value instanceof java.util.Optional<?> optional) optional.ifPresent(v -> collectTexts(v, spans, texts));
        else if (value instanceof List<?> list) { for (Object child : list) collectTexts(child, spans, texts); }
        else if (value != null && value.getClass().isRecord()) {
            try { for (var component : value.getClass().getRecordComponents()) collectTexts(component.getAccessor().invoke(value), spans, texts); }
            catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
        }
    }
    private static void collectNodes(Object value, List<Object> nodes) {
        if (value instanceof List<?> list) { for (Object child : list) collectNodes(child, nodes); }
        else if (value instanceof Map<?, ?> node && node.get("fields") instanceof Map<?, ?> fields) {
            if (Set.of("Leaf", "Other", "OtherLeaf").contains(node.get("type"))) {
                var span = (List<?>) node.get("span");
                Object text = fields.containsKey("text") ? fields.get("text") : fields.get("value");
                if (text instanceof String) nodes.add(List.of(span.get(0), span.get(1), text));
            }
            for (Object child : fields.values()) collectNodes(child, nodes);
        }
    }
    /** Fold only numeric leaves and the canonical left/op/right lists, never source input. */
    private static Double numeric(Object value) {
        if (value instanceof Number number) return finite(number.doubleValue());
        if (value instanceof String text) {
            var matcher = NUMBER.matcher(text.strip());
            if (!matcher.lookingAt()) return null;
            String remainder = text.strip().substring(matcher.end()).stripLeading();
            // Captured text can include trailing trivia. Do not accept arbitrary suffixes.
            while (!remainder.isEmpty()) {
                if (remainder.startsWith("//")) {
                    int end = remainder.indexOf('\n');
                    remainder = end < 0 ? "" : remainder.substring(end + 1).stripLeading();
                } else if (remainder.startsWith("/*")) {
                    int end = remainder.indexOf("*/", 2);
                    if (end < 0) return null;
                    remainder = remainder.substring(end + 2).stripLeading();
                } else return null;
            }
            return finite(Double.parseDouble(matcher.group()));
        }
        if (!(value instanceof Map<?, ?> node) || !(node.get("fields") instanceof Map<?, ?> fields)) return null;
        if (node.get("type").equals("Negation")) { Double child = numeric(fields.get("value")); return child == null ? null : -child; }
        if (node.get("type").equals("Conditional")) {
            Double condition = numeric(fields.get("condition"));
            return condition == null ? null : numeric(fields.get(condition != 0 ? "thenExpr" : "elseExpr"));
        }
        if (fields.size() == 1 && fields.containsKey("value")) return numeric(fields.get("value"));
        if (fields.size() != 3 || !fields.containsKey("left") || !fields.containsKey("op") || !fields.containsKey("right")) return null;
        List<?> ops = values(fields.get("op")), rights = values(fields.get("right"));
        // assocLists leafFallback stores its numeric leaf in the single op slot.
        if (fields.get("left") == null && rights.isEmpty() && ops.size() == 1) return numeric(ops.get(0));
        if (ops.size() != rights.size()) return null;
        Double left = numeric(fields.get("left"));
        if (left == null) return null;
        for (int i = 0; i < ops.size(); i++) {
            Double right = numeric(rights.get(i));
            if (right == null || !(ops.get(i) instanceof String op)) return null;
            left = switch (op.strip()) {
                case "+" -> left + right; case "-" -> left - right;
                case "*" -> left * right; case "/" -> left / right;
                case "^" -> Math.pow(left, right);
                default -> null;
            };
            if (left == null) return null;
            finite(left);
        }
        return left;
    }
    private static double finite(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite evaluation value");
        return value;
    }
}
