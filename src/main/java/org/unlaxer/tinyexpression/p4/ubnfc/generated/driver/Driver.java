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
    private static final Map<String, String> BINDINGS = Map.ofEntries(Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:900:909:body/0/0/ruleRef/capture/0","Formula:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:926:943:body/1/0/ruleRef/capture/0","Formula:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:957:976:body/2/0/ruleRef/capture/0","Formula:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1012:1022:body/4/ruleRef/capture/0","Formula:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1037:1054:body/5/0/ruleRef/capture/0","Formula:4"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1502:1535:body/0/group/capture/0","CodeBlock:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2000:2009:body/1/ruleRef/capture/0","ImportDeclaration:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2027:2037:body/2/0/1/tokenRef/capture/0","ImportDeclaration:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2053:2063:body/4/tokenRef/capture/0","ImportDeclaration:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2145:2155:body/0/tokenRef/capture/0","ClassName:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2168:2178:body/1/0/1/tokenRef/capture/0","ClassName:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2663:2673:body/2/tokenRef/capture/0","NumberVariableDeclaration:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2720:2732:body/4/0/1/0/ruleRef/capture/0","NumberVariableDeclaration:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2749:2765:body/4/0/2/ruleRef/capture/0","NumberVariableDeclaration:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2781:2792:body/5/0/ruleRef/capture/0","NumberVariableDeclaration:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3012:3022:body/2/tokenRef/capture/0","StringVariableDeclaration:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3069:3081:body/4/0/1/0/ruleRef/capture/0","StringVariableDeclaration:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3098:3114:body/4/0/2/ruleRef/capture/0","StringVariableDeclaration:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3130:3141:body/5/0/ruleRef/capture/0","StringVariableDeclaration:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3363:3373:body/2/tokenRef/capture/0","BooleanVariableDeclaration:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3421:3433:body/4/0/1/0/ruleRef/capture/0","BooleanVariableDeclaration:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3450:3467:body/4/0/2/ruleRef/capture/0","BooleanVariableDeclaration:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3483:3494:body/5/0/ruleRef/capture/0","BooleanVariableDeclaration:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3714:3724:body/2/tokenRef/capture/0","ObjectVariableDeclaration:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3771:3783:body/4/0/1/0/ruleRef/capture/0","ObjectVariableDeclaration:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3800:3816:body/4/0/2/ruleRef/capture/0","ObjectVariableDeclaration:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3832:3843:body/5/0/ruleRef/capture/0","ObjectVariableDeclaration:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4892:4902:body/1/tokenRef/capture/0","NumberMethodDeclaration:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4931:4947:body/3/0/ruleRef/capture/0","NumberMethodDeclaration:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4984:5000:body/6/ruleRef/capture/0","NumberMethodDeclaration:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5218:5228:body/1/tokenRef/capture/0","StringMethodDeclaration:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5257:5273:body/3/0/ruleRef/capture/0","StringMethodDeclaration:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5310:5326:body/6/ruleRef/capture/0","StringMethodDeclaration:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5547:5557:body/1/tokenRef/capture/0","BooleanMethodDeclaration:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5586:5602:body/3/0/ruleRef/capture/0","BooleanMethodDeclaration:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5639:5656:body/6/ruleRef/capture/0","BooleanMethodDeclaration:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5874:5884:body/1/tokenRef/capture/0","ObjectMethodDeclaration:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5913:5929:body/3/0/ruleRef/capture/0","ObjectMethodDeclaration:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5966:5982:body/6/ruleRef/capture/0","ObjectMethodDeclaration:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6079:6094:body/0/ruleRef/capture/0","MethodParameters:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6109:6124:body/1/0/1/ruleRef/capture/0","MethodParameters:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6252:6262:body/1/tokenRef/capture/0","MethodParameter:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6281:6291:body/2/0/1/ruleRef/capture/0","MethodParameter:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6940:6949:body/4/0/0/0/ruleRef/capture/0","ExternalBooleanInvocation:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6965:6975:body/4/0/0/2/tokenRef/capture/0","ExternalBooleanInvocation:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6984:6994:body/4/0/1/tokenRef/capture/0","ExternalBooleanInvocation:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7013:7022:body/6/0/ruleRef/capture/0","ExternalBooleanInvocation:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7231:7240:body/2/0/0/0/ruleRef/capture/0","ExternalNumberInvocation:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7256:7266:body/2/0/0/2/tokenRef/capture/0","ExternalNumberInvocation:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7275:7285:body/2/0/1/tokenRef/capture/0","ExternalNumberInvocation:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7304:7313:body/4/0/ruleRef/capture/0","ExternalNumberInvocation:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7508:7517:body/4/0/0/0/ruleRef/capture/0","ExternalStringInvocation:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7533:7543:body/4/0/0/2/tokenRef/capture/0","ExternalStringInvocation:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7552:7562:body/4/0/1/tokenRef/capture/0","ExternalStringInvocation:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7581:7590:body/6/0/ruleRef/capture/0","ExternalStringInvocation:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7785:7794:body/4/0/0/0/ruleRef/capture/0","ExternalObjectInvocation:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7810:7820:body/4/0/0/2/tokenRef/capture/0","ExternalObjectInvocation:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7829:7839:body/4/0/1/tokenRef/capture/0","ExternalObjectInvocation:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7858:7867:body/6/0/ruleRef/capture/0","ExternalObjectInvocation:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8244:8254:body/1/tokenRef/capture/0","MethodInvocation:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8267:8276:body/3/0/ruleRef/capture/0","MethodInvocation:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8455:8472:body/0/ruleRef/capture/0","ArgumentTernary:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8488:8504:body/2/ruleRef/capture/0","ArgumentTernary:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8519:8535:body/4/ruleRef/capture/0","ArgumentTernary:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8631:8646:body/0/ruleRef/capture/0","ArgumentExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8660:8680:body/1/ruleRef/capture/0","ArgumentExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8694:8720:body/2/ruleRef/capture/0","ArgumentExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8734:8744:body/3/ruleRef/capture/0","ArgumentExpression:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8814:8832:body/0/ruleRef/capture/0","Arguments:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8847:8865:body/1/0/1/ruleRef/capture/0","Arguments:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9070:9080:body/0/ruleRef/capture/0","NumberExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9089:9094:body/1/0/0/ruleRef/capture/0","NumberExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9099:9109:body/1/0/1/ruleRef/capture/0","NumberExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9225:9237:body/0/ruleRef/capture/0","NumberTerm:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9246:9251:body/1/0/0/ruleRef/capture/0","NumberTerm:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9256:9268:body/1/0/1/ruleRef/capture/0","NumberTerm:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9754:9772:body/2/ruleRef/capture/0","SinFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9847:9865:body/2/ruleRef/capture/0","CosFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9940:9958:body/2/ruleRef/capture/0","TanFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10036:10054:body/2/ruleRef/capture/0","SqrtFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10137:10155:body/2/ruleRef/capture/0","MinFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10169:10187:body/3/0/1/ruleRef/capture/0","MinFunction:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10273:10291:body/2/ruleRef/capture/0","MaxFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10305:10323:body/3/0/1/ruleRef/capture/0","MaxFunction:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10465:10483:body/2/ruleRef/capture/0","AbsFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10564:10582:body/2/ruleRef/capture/0","RoundFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10660:10678:body/2/ruleRef/capture/0","CeilFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10759:10777:body/2/ruleRef/capture/0","FloorFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10863:10881:body/2/ruleRef/capture/0","PowFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10892:10910:body/4/ruleRef/capture/0","PowFunction:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10990:11008:body/2/ruleRef/capture/0","LogFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11083:11101:body/2/ruleRef/capture/0","ExpFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11276:11292:body/2/ruleRef/capture/0","ToNumFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11304:11322:body/4/ruleRef/capture/0","ToNumFunction:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11820:11836:body/2/ruleRef/capture/0","ToUpperCaseFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11939:11955:body/2/ruleRef/capture/0","ToLowerCaseFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12037:12053:body/2/ruleRef/capture/0","TrimFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12141:12157:body/2/ruleRef/capture/0","LengthFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12239:12255:body/2/ruleRef/capture/0","LenFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12591:12602:body/0/ruleRef/capture/0","ToUpperCaseDotMethod:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12710:12721:body/0/ruleRef/capture/0","ToLowerCaseDotMethod:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12815:12826:body/0/ruleRef/capture/0","TrimDotMethod:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12917:12928:body/0/ruleRef/capture/0","LengthDotMethod:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13190:13206:body/2/ruleRef/capture/0","StartsWithFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13218:13234:body/4/ruleRef/capture/0","StartsWithFunction:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13255:13271:body/5/0/1/ruleRef/capture/0","StartsWithFunction:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13380:13396:body/2/ruleRef/capture/0","EndsWithFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13408:13424:body/4/ruleRef/capture/0","EndsWithFunction:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13445:13461:body/5/0/1/ruleRef/capture/0","EndsWithFunction:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13570:13586:body/2/ruleRef/capture/0","ContainsFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13598:13614:body/4/ruleRef/capture/0","ContainsFunction:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13635:13651:body/5/0/1/ruleRef/capture/0","ContainsFunction:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13733:13749:body/0/ruleRef/capture/0","InMethod:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13767:13783:body/3/ruleRef/capture/0","InMethod:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13802:13818:body/4/0/1/ruleRef/capture/0","InMethod:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14001:14024:body/0/ruleRef/capture/0","StartsWithDotMethod:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14050:14066:body/3/ruleRef/capture/0","StartsWithDotMethod:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14087:14103:body/4/0/1/ruleRef/capture/0","StartsWithDotMethod:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14201:14224:body/0/ruleRef/capture/0","EndsWithDotMethod:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14248:14264:body/3/ruleRef/capture/0","EndsWithDotMethod:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14285:14301:body/4/0/1/ruleRef/capture/0","EndsWithDotMethod:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14399:14422:body/0/ruleRef/capture/0","ContainsDotMethod:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14446:14462:body/3/ruleRef/capture/0","ContainsDotMethod:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14483:14499:body/4/0/1/ruleRef/capture/0","ContainsDotMethod:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14784:14795:body/2/ruleRef/capture/0","IsPresentFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14988:15004:body/2/ruleRef/capture/0","InTimeRangeFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15020:15036:body/4/ruleRef/capture/0","InTimeRangeFunction:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15181:15190:body/2/ruleRef/capture/0","InDayTimeRangeFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15205:15221:body/4/ruleRef/capture/0","InDayTimeRangeFunction:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15237:15246:body/6/ruleRef/capture/0","InDayTimeRangeFunction:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15259:15275:body/8/ruleRef/capture/0","InDayTimeRangeFunction:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16533:16550:body/0/0/ruleRef/capture/0","SliceBaseExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16562:16577:body/0/2/ruleRef/capture/0","SliceBaseExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16589:16602:body/0/4/ruleRef/capture/0","SliceBaseExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16612:16626:body/0/6/ruleRef/capture/0","SliceBaseExpression:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16643:16660:body/1/0/ruleRef/capture/0","SliceBaseExpression:4"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16672:16687:body/1/2/ruleRef/capture/0","SliceBaseExpression:5"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16699:16712:body/1/4/ruleRef/capture/0","SliceBaseExpression:6"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16728:16745:body/2/0/ruleRef/capture/0","SliceBaseExpression:7"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16757:16772:body/2/2/ruleRef/capture/0","SliceBaseExpression:8"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16788:16802:body/2/5/ruleRef/capture/0","SliceBaseExpression:9"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16819:16836:body/3/0/ruleRef/capture/0","SliceBaseExpression:10"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16848:16863:body/3/2/ruleRef/capture/0","SliceBaseExpression:11"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16885:16902:body/4/0/ruleRef/capture/0","SliceBaseExpression:12"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16918:16931:body/4/3/ruleRef/capture/0","SliceBaseExpression:13"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16941:16955:body/4/5/ruleRef/capture/0","SliceBaseExpression:14"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16972:16989:body/5/0/ruleRef/capture/0","SliceBaseExpression:15"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17005:17018:body/5/3/ruleRef/capture/0","SliceBaseExpression:16"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17034:17051:body/6/0/ruleRef/capture/0","SliceBaseExpression:17"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17071:17085:body/6/4/ruleRef/capture/0","SliceBaseExpression:18"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17102:17119:body/7/0/ruleRef/capture/0","SliceBaseExpression:19"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17232:17251:body/0/0/ruleRef/capture/0","SliceNestedExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17263:17278:body/0/2/ruleRef/capture/0","SliceNestedExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17290:17303:body/0/4/ruleRef/capture/0","SliceNestedExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17313:17327:body/0/6/ruleRef/capture/0","SliceNestedExpression:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17344:17363:body/1/0/ruleRef/capture/0","SliceNestedExpression:4"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17375:17390:body/1/2/ruleRef/capture/0","SliceNestedExpression:5"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17402:17415:body/1/4/ruleRef/capture/0","SliceNestedExpression:6"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17431:17450:body/2/0/ruleRef/capture/0","SliceNestedExpression:7"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17462:17477:body/2/2/ruleRef/capture/0","SliceNestedExpression:8"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17493:17507:body/2/5/ruleRef/capture/0","SliceNestedExpression:9"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17524:17543:body/3/0/ruleRef/capture/0","SliceNestedExpression:10"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17555:17570:body/3/2/ruleRef/capture/0","SliceNestedExpression:11"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17592:17611:body/4/0/ruleRef/capture/0","SliceNestedExpression:12"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17627:17640:body/4/3/ruleRef/capture/0","SliceNestedExpression:13"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17650:17664:body/4/5/ruleRef/capture/0","SliceNestedExpression:14"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17681:17700:body/5/0/ruleRef/capture/0","SliceNestedExpression:15"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17716:17729:body/5/3/ruleRef/capture/0","SliceNestedExpression:16"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17745:17764:body/6/0/ruleRef/capture/0","SliceNestedExpression:17"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17784:17798:body/6/4/ruleRef/capture/0","SliceNestedExpression:18"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17815:17834:body/7/0/ruleRef/capture/0","SliceNestedExpression:19"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18123:18133:body/0/ruleRef/capture/0","StringExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18142:18145:body/1/0/0/literal/capture/0","StringExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18150:18160:body/1/0/1/ruleRef/capture/0","StringExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18921:18931:body/4/tokenRef/capture/0","StringCastVariable:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19025:19035:body/1/tokenRef/capture/0","StringTypedVariable:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19301:19321:body/0/ruleRef/capture/0","BooleanExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19330:19333:body/1/0/0/literal/capture/0","BooleanExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19338:19358:body/1/0/1/ruleRef/capture/0","BooleanExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19504:19524:body/0/ruleRef/capture/0","BooleanAndExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19533:19536:body/1/0/0/literal/capture/0","BooleanAndExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19541:19561:body/1/0/1/ruleRef/capture/0","BooleanAndExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19707:19720:body/0/ruleRef/capture/0","BooleanXorExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19729:19732:body/1/0/0/literal/capture/0","BooleanXorExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19737:19750:body/1/0/1/ruleRef/capture/0","BooleanXorExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19907:19924:body/2/ruleRef/capture/0","NotExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20495:20512:body/0/ruleRef/capture/0","BooleanEqualityExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20519:20529:body/1/ruleRef/capture/0","BooleanEqualityExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20534:20551:body/2/ruleRef/capture/0","BooleanEqualityExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20634:20659:body/0/ruleRef/capture/0","BooleanFactor:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20673:20693:body/1/ruleRef/capture/0","BooleanFactor:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20707:20733:body/2/ruleRef/capture/0","BooleanFactor:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20747:20764:body/3/ruleRef/capture/0","BooleanFactor:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21028:21044:body/0/ruleRef/capture/0","StringComparisonExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21051:21061:body/1/ruleRef/capture/0","StringComparisonExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21066:21082:body/2/ruleRef/capture/0","StringComparisonExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21204:21220:body/0/ruleRef/capture/0","ComparisonExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21227:21236:body/1/ruleRef/capture/0","ComparisonExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21241:21257:body/2/ruleRef/capture/0","ComparisonExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21474:21490:body/0/ruleRef/capture/0","ObjectExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21504:21520:body/1/ruleRef/capture/0","ObjectExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21534:21551:body/2/ruleRef/capture/0","ObjectExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21565:21589:body/3/ruleRef/capture/0","ObjectExpression:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21603:21614:body/4/ruleRef/capture/0","ObjectExpression:4"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21628:21644:body/5/ruleRef/capture/0","ObjectExpression:5"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21828:21845:body/2/ruleRef/capture/0","IfExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21869:21885:body/5/ruleRef/capture/0","IfExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21919:21935:body/9/ruleRef/capture/0","IfExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22031:22051:body/0/ruleRef/capture/0","BranchExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22065:22091:body/1/ruleRef/capture/0","BranchExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22105:22130:body/2/ruleRef/capture/0","BranchExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22144:22160:body/3/ruleRef/capture/0","BranchExpression:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22174:22191:body/4/ruleRef/capture/0","BranchExpression:4"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22205:22221:body/5/ruleRef/capture/0","BranchExpression:5"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22235:22251:body/6/ruleRef/capture/0","BranchExpression:6"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22265:22281:body/7/ruleRef/capture/0","BranchExpression:7"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22460:22477:body/1/ruleRef/capture/0","TernaryExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22493:22509:body/3/ruleRef/capture/0","TernaryExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22524:22540:body/5/ruleRef/capture/0","TernaryExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22762:22772:body/2/ruleRef/capture/0","NumberMatchExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22790:22800:body/3/0/1/ruleRef/capture/0","NumberMatchExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22824:22841:body/5/ruleRef/capture/0","NumberMatchExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22937:22954:body/0/ruleRef/capture/0","NumberCase:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22971:22986:body/2/ruleRef/capture/0","NumberCase:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23085:23100:body/2/ruleRef/capture/0","NumberDefaultCase:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23180:23196:body/0/ruleRef/capture/0","NumberCaseValue:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23329:23339:body/2/ruleRef/capture/0","StringMatchExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23357:23367:body/3/0/1/ruleRef/capture/0","StringMatchExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23391:23408:body/5/ruleRef/capture/0","StringMatchExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23504:23521:body/0/ruleRef/capture/0","StringCase:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23538:23553:body/2/ruleRef/capture/0","StringCase:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23652:23667:body/2/ruleRef/capture/0","StringDefaultCase:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23747:23763:body/0/ruleRef/capture/0","StringCaseValue:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23898:23909:body/2/ruleRef/capture/0","BooleanMatchExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23927:23938:body/3/0/1/ruleRef/capture/0","BooleanMatchExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23962:23980:body/5/ruleRef/capture/0","BooleanMatchExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24078:24095:body/0/ruleRef/capture/0","BooleanCase:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24112:24128:body/2/ruleRef/capture/0","BooleanCase:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24229:24245:body/2/ruleRef/capture/0","BooleanDefaultCase:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24327:24344:body/0/ruleRef/capture/0","BooleanCaseValue:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24561:24571:body/1/tokenRef/capture/0","VariableRef:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24589:24600:body/2/0/1/ruleRef/capture/0","VariableRef:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25060:25076:body/0/ruleRef/capture/0","Expression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25090:25107:body/1/ruleRef/capture/0","Expression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25121:25137:body/2/ruleRef/capture/0","Expression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25151:25167:body/3/ruleRef/capture/0","Expression:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25181:25197:body/4/ruleRef/capture/0","Expression:4"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25215:25225:body/5/1/ruleRef/capture/0","Expression:5"));
    /** D-025: `@catalog` の静的表。入力に依存しない grammar metadata。 */
    private static final List<Object> CATALOGS = List.of(object("rule", "VariableRef", "context", "variable", "captures", List.of("name", "type")));
    /** D-025: 文法が宣言した scope mode（観測されたイベントの mode ではない）。 */
    private static final List<String> SCOPE_MODES = List.of("lexical");
    private static final Pattern NUMBER = Pattern.compile("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?");

    private static final Map<String, String> EXTERN_CLASSES = Map.ofEntries(Map.entry("TinyExpressionP4::STRING","org.unlaxer.tinyexpression.parser.StringLiteralParser"),Map.entry("TinyExpressionP4::CODE_START","org.unlaxer.tinyexpression.parser.javalang.CodeStartParser"),Map.entry("TinyExpressionP4::CODE_END","org.unlaxer.tinyexpression.parser.javalang.CodeEndParser"));
    private static Map<String, TokenScanner> scanners = Map.of();
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
        Map<String, Object> observations = request.get("observations") instanceof Map<?, ?> ? Json.object(request.get("observations")) : Map.of();
        int maxDepth = options.get("maxDepth") instanceof Number number ? new java.math.BigDecimal(number.toString()).intValueExact() : ParseOptions.DEFAULT_MAX_DEPTH;
        var prefix = TinyExpressionP4Parser.parseEntry(grammar, entry, input, new ParseOptions(false, false, lexical, true, scanners, maxDepth));
        ParseResult<?> result;
        String mapping = null, mappingKind = null, mappingMessage = null;
        try {
            result = TinyExpressionP4Parser.parseEntry(grammar, entry, input, new ParseOptions(true, true, lexical, true, scanners, maxDepth));
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
