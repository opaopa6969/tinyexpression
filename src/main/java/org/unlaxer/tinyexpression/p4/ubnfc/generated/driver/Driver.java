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
    private static final Map<String, String> BINDINGS = Map.ofEntries(Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1023:1032:body/0/0/ruleRef/capture/0","Formula:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1049:1066:body/1/0/ruleRef/capture/0","Formula:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1080:1099:body/2/0/ruleRef/capture/0","Formula:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1135:1145:body/4/ruleRef/capture/0","Formula:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1160:1177:body/5/0/ruleRef/capture/0","Formula:4"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1625:1676:body/0/group/capture/0","CodeBlock:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2141:2150:body/1/ruleRef/capture/0","ImportDeclaration:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2168:2178:body/2/0/1/tokenRef/capture/0","ImportDeclaration:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2194:2204:body/4/tokenRef/capture/0","ImportDeclaration:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2286:2296:body/0/tokenRef/capture/0","ClassName:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2309:2319:body/1/0/1/tokenRef/capture/0","ClassName:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2804:2814:body/2/tokenRef/capture/0","NumberVariableDeclaration:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2861:2873:body/4/0/1/0/ruleRef/capture/0","NumberVariableDeclaration:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2890:2906:body/4/0/2/ruleRef/capture/0","NumberVariableDeclaration:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2922:2933:body/5/0/ruleRef/capture/0","NumberVariableDeclaration:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3153:3163:body/2/tokenRef/capture/0","StringVariableDeclaration:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3210:3222:body/4/0/1/0/ruleRef/capture/0","StringVariableDeclaration:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3239:3255:body/4/0/2/ruleRef/capture/0","StringVariableDeclaration:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3271:3282:body/5/0/ruleRef/capture/0","StringVariableDeclaration:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3504:3514:body/2/tokenRef/capture/0","BooleanVariableDeclaration:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3562:3574:body/4/0/1/0/ruleRef/capture/0","BooleanVariableDeclaration:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3591:3608:body/4/0/2/ruleRef/capture/0","BooleanVariableDeclaration:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3624:3635:body/5/0/ruleRef/capture/0","BooleanVariableDeclaration:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3855:3865:body/2/tokenRef/capture/0","ObjectVariableDeclaration:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3912:3924:body/4/0/1/0/ruleRef/capture/0","ObjectVariableDeclaration:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3941:3957:body/4/0/2/ruleRef/capture/0","ObjectVariableDeclaration:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3973:3984:body/5/0/ruleRef/capture/0","ObjectVariableDeclaration:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5033:5043:body/1/tokenRef/capture/0","NumberMethodDeclaration:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5072:5088:body/3/0/ruleRef/capture/0","NumberMethodDeclaration:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5125:5141:body/6/ruleRef/capture/0","NumberMethodDeclaration:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5359:5369:body/1/tokenRef/capture/0","StringMethodDeclaration:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5398:5414:body/3/0/ruleRef/capture/0","StringMethodDeclaration:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5451:5467:body/6/ruleRef/capture/0","StringMethodDeclaration:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5688:5698:body/1/tokenRef/capture/0","BooleanMethodDeclaration:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5727:5743:body/3/0/ruleRef/capture/0","BooleanMethodDeclaration:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5780:5797:body/6/ruleRef/capture/0","BooleanMethodDeclaration:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6015:6025:body/1/tokenRef/capture/0","ObjectMethodDeclaration:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6054:6070:body/3/0/ruleRef/capture/0","ObjectMethodDeclaration:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6107:6123:body/6/ruleRef/capture/0","ObjectMethodDeclaration:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6220:6235:body/0/ruleRef/capture/0","MethodParameters:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6250:6265:body/1/0/1/ruleRef/capture/0","MethodParameters:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6393:6403:body/1/tokenRef/capture/0","MethodParameter:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6422:6432:body/2/0/1/ruleRef/capture/0","MethodParameter:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7081:7090:body/4/0/0/0/ruleRef/capture/0","ExternalBooleanInvocation:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7106:7116:body/4/0/0/2/tokenRef/capture/0","ExternalBooleanInvocation:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7125:7135:body/4/0/1/tokenRef/capture/0","ExternalBooleanInvocation:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7154:7163:body/6/0/ruleRef/capture/0","ExternalBooleanInvocation:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7372:7381:body/2/0/0/0/ruleRef/capture/0","ExternalNumberInvocation:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7397:7407:body/2/0/0/2/tokenRef/capture/0","ExternalNumberInvocation:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7416:7426:body/2/0/1/tokenRef/capture/0","ExternalNumberInvocation:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7445:7454:body/4/0/ruleRef/capture/0","ExternalNumberInvocation:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7649:7658:body/4/0/0/0/ruleRef/capture/0","ExternalStringInvocation:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7674:7684:body/4/0/0/2/tokenRef/capture/0","ExternalStringInvocation:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7693:7703:body/4/0/1/tokenRef/capture/0","ExternalStringInvocation:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7722:7731:body/6/0/ruleRef/capture/0","ExternalStringInvocation:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7926:7935:body/4/0/0/0/ruleRef/capture/0","ExternalObjectInvocation:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7951:7961:body/4/0/0/2/tokenRef/capture/0","ExternalObjectInvocation:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7970:7980:body/4/0/1/tokenRef/capture/0","ExternalObjectInvocation:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7999:8008:body/6/0/ruleRef/capture/0","ExternalObjectInvocation:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8385:8395:body/1/tokenRef/capture/0","MethodInvocation:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8408:8417:body/3/0/ruleRef/capture/0","MethodInvocation:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8596:8613:body/0/ruleRef/capture/0","ArgumentTernary:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8629:8645:body/2/ruleRef/capture/0","ArgumentTernary:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8660:8676:body/4/ruleRef/capture/0","ArgumentTernary:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8772:8787:body/0/ruleRef/capture/0","ArgumentExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8801:8821:body/1/ruleRef/capture/0","ArgumentExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8835:8861:body/2/ruleRef/capture/0","ArgumentExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8875:8885:body/3/ruleRef/capture/0","ArgumentExpression:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8955:8973:body/0/ruleRef/capture/0","Arguments:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8988:9006:body/1/0/1/ruleRef/capture/0","Arguments:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9211:9221:body/0/ruleRef/capture/0","NumberExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9230:9235:body/1/0/0/ruleRef/capture/0","NumberExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9240:9250:body/1/0/1/ruleRef/capture/0","NumberExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9366:9378:body/0/ruleRef/capture/0","NumberTerm:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9387:9392:body/1/0/0/ruleRef/capture/0","NumberTerm:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9397:9409:body/1/0/1/ruleRef/capture/0","NumberTerm:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9895:9913:body/2/ruleRef/capture/0","SinFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9988:10006:body/2/ruleRef/capture/0","CosFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10081:10099:body/2/ruleRef/capture/0","TanFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10177:10195:body/2/ruleRef/capture/0","SqrtFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10278:10296:body/2/ruleRef/capture/0","MinFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10310:10328:body/3/0/1/ruleRef/capture/0","MinFunction:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10414:10432:body/2/ruleRef/capture/0","MaxFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10446:10464:body/3/0/1/ruleRef/capture/0","MaxFunction:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10606:10624:body/2/ruleRef/capture/0","AbsFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10705:10723:body/2/ruleRef/capture/0","RoundFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10801:10819:body/2/ruleRef/capture/0","CeilFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10900:10918:body/2/ruleRef/capture/0","FloorFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11004:11022:body/2/ruleRef/capture/0","PowFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11033:11051:body/4/ruleRef/capture/0","PowFunction:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11131:11149:body/2/ruleRef/capture/0","LogFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11224:11242:body/2/ruleRef/capture/0","ExpFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11417:11433:body/2/ruleRef/capture/0","ToNumFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11445:11463:body/4/ruleRef/capture/0","ToNumFunction:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11961:11977:body/2/ruleRef/capture/0","ToUpperCaseFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12080:12096:body/2/ruleRef/capture/0","ToLowerCaseFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12178:12194:body/2/ruleRef/capture/0","TrimFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12282:12298:body/2/ruleRef/capture/0","LengthFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12380:12396:body/2/ruleRef/capture/0","LenFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12732:12743:body/0/ruleRef/capture/0","ToUpperCaseDotMethod:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12851:12862:body/0/ruleRef/capture/0","ToLowerCaseDotMethod:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12956:12967:body/0/ruleRef/capture/0","TrimDotMethod:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13058:13069:body/0/ruleRef/capture/0","LengthDotMethod:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13331:13347:body/2/ruleRef/capture/0","StartsWithFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13359:13375:body/4/ruleRef/capture/0","StartsWithFunction:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13396:13412:body/5/0/1/ruleRef/capture/0","StartsWithFunction:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13521:13537:body/2/ruleRef/capture/0","EndsWithFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13549:13565:body/4/ruleRef/capture/0","EndsWithFunction:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13586:13602:body/5/0/1/ruleRef/capture/0","EndsWithFunction:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13711:13727:body/2/ruleRef/capture/0","ContainsFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13739:13755:body/4/ruleRef/capture/0","ContainsFunction:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13776:13792:body/5/0/1/ruleRef/capture/0","ContainsFunction:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13874:13890:body/0/ruleRef/capture/0","InMethod:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13908:13924:body/3/ruleRef/capture/0","InMethod:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13943:13959:body/4/0/1/ruleRef/capture/0","InMethod:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14142:14165:body/0/ruleRef/capture/0","StartsWithDotMethod:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14191:14207:body/3/ruleRef/capture/0","StartsWithDotMethod:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14228:14244:body/4/0/1/ruleRef/capture/0","StartsWithDotMethod:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14342:14365:body/0/ruleRef/capture/0","EndsWithDotMethod:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14389:14405:body/3/ruleRef/capture/0","EndsWithDotMethod:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14426:14442:body/4/0/1/ruleRef/capture/0","EndsWithDotMethod:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14540:14563:body/0/ruleRef/capture/0","ContainsDotMethod:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14587:14603:body/3/ruleRef/capture/0","ContainsDotMethod:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14624:14640:body/4/0/1/ruleRef/capture/0","ContainsDotMethod:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14925:14936:body/2/ruleRef/capture/0","IsPresentFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15129:15145:body/2/ruleRef/capture/0","InTimeRangeFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15161:15177:body/4/ruleRef/capture/0","InTimeRangeFunction:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15322:15331:body/2/ruleRef/capture/0","InDayTimeRangeFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15346:15362:body/4/ruleRef/capture/0","InDayTimeRangeFunction:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15378:15387:body/6/ruleRef/capture/0","InDayTimeRangeFunction:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15400:15416:body/8/ruleRef/capture/0","InDayTimeRangeFunction:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16674:16691:body/0/0/ruleRef/capture/0","SliceBaseExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16703:16718:body/0/2/ruleRef/capture/0","SliceBaseExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16730:16743:body/0/4/ruleRef/capture/0","SliceBaseExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16753:16767:body/0/6/ruleRef/capture/0","SliceBaseExpression:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16784:16801:body/1/0/ruleRef/capture/0","SliceBaseExpression:4"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16813:16828:body/1/2/ruleRef/capture/0","SliceBaseExpression:5"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16840:16853:body/1/4/ruleRef/capture/0","SliceBaseExpression:6"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16869:16886:body/2/0/ruleRef/capture/0","SliceBaseExpression:7"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16898:16913:body/2/2/ruleRef/capture/0","SliceBaseExpression:8"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16929:16943:body/2/5/ruleRef/capture/0","SliceBaseExpression:9"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16960:16977:body/3/0/ruleRef/capture/0","SliceBaseExpression:10"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16989:17004:body/3/2/ruleRef/capture/0","SliceBaseExpression:11"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17026:17043:body/4/0/ruleRef/capture/0","SliceBaseExpression:12"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17059:17072:body/4/3/ruleRef/capture/0","SliceBaseExpression:13"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17082:17096:body/4/5/ruleRef/capture/0","SliceBaseExpression:14"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17113:17130:body/5/0/ruleRef/capture/0","SliceBaseExpression:15"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17146:17159:body/5/3/ruleRef/capture/0","SliceBaseExpression:16"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17175:17192:body/6/0/ruleRef/capture/0","SliceBaseExpression:17"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17212:17226:body/6/4/ruleRef/capture/0","SliceBaseExpression:18"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17243:17260:body/7/0/ruleRef/capture/0","SliceBaseExpression:19"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17373:17392:body/0/0/ruleRef/capture/0","SliceNestedExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17404:17419:body/0/2/ruleRef/capture/0","SliceNestedExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17431:17444:body/0/4/ruleRef/capture/0","SliceNestedExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17454:17468:body/0/6/ruleRef/capture/0","SliceNestedExpression:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17485:17504:body/1/0/ruleRef/capture/0","SliceNestedExpression:4"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17516:17531:body/1/2/ruleRef/capture/0","SliceNestedExpression:5"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17543:17556:body/1/4/ruleRef/capture/0","SliceNestedExpression:6"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17572:17591:body/2/0/ruleRef/capture/0","SliceNestedExpression:7"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17603:17618:body/2/2/ruleRef/capture/0","SliceNestedExpression:8"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17634:17648:body/2/5/ruleRef/capture/0","SliceNestedExpression:9"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17665:17684:body/3/0/ruleRef/capture/0","SliceNestedExpression:10"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17696:17711:body/3/2/ruleRef/capture/0","SliceNestedExpression:11"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17733:17752:body/4/0/ruleRef/capture/0","SliceNestedExpression:12"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17768:17781:body/4/3/ruleRef/capture/0","SliceNestedExpression:13"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17791:17805:body/4/5/ruleRef/capture/0","SliceNestedExpression:14"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17822:17841:body/5/0/ruleRef/capture/0","SliceNestedExpression:15"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17857:17870:body/5/3/ruleRef/capture/0","SliceNestedExpression:16"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17886:17905:body/6/0/ruleRef/capture/0","SliceNestedExpression:17"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17925:17939:body/6/4/ruleRef/capture/0","SliceNestedExpression:18"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17956:17975:body/7/0/ruleRef/capture/0","SliceNestedExpression:19"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18264:18274:body/0/ruleRef/capture/0","StringExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18283:18286:body/1/0/0/literal/capture/0","StringExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18291:18301:body/1/0/1/ruleRef/capture/0","StringExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19062:19072:body/4/tokenRef/capture/0","StringCastVariable:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19166:19176:body/1/tokenRef/capture/0","StringTypedVariable:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19442:19462:body/0/ruleRef/capture/0","BooleanExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19471:19474:body/1/0/0/literal/capture/0","BooleanExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19479:19499:body/1/0/1/ruleRef/capture/0","BooleanExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19645:19665:body/0/ruleRef/capture/0","BooleanAndExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19674:19677:body/1/0/0/literal/capture/0","BooleanAndExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19682:19702:body/1/0/1/ruleRef/capture/0","BooleanAndExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19848:19861:body/0/ruleRef/capture/0","BooleanXorExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19870:19873:body/1/0/0/literal/capture/0","BooleanXorExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19878:19891:body/1/0/1/ruleRef/capture/0","BooleanXorExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20048:20065:body/2/ruleRef/capture/0","NotExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20636:20653:body/0/ruleRef/capture/0","BooleanEqualityExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20660:20670:body/1/ruleRef/capture/0","BooleanEqualityExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20675:20692:body/2/ruleRef/capture/0","BooleanEqualityExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20775:20800:body/0/ruleRef/capture/0","BooleanFactor:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20814:20834:body/1/ruleRef/capture/0","BooleanFactor:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20848:20874:body/2/ruleRef/capture/0","BooleanFactor:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20888:20905:body/3/ruleRef/capture/0","BooleanFactor:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21169:21185:body/0/ruleRef/capture/0","StringComparisonExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21192:21202:body/1/ruleRef/capture/0","StringComparisonExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21207:21223:body/2/ruleRef/capture/0","StringComparisonExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21345:21361:body/0/ruleRef/capture/0","ComparisonExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21368:21377:body/1/ruleRef/capture/0","ComparisonExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21382:21398:body/2/ruleRef/capture/0","ComparisonExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21615:21631:body/0/ruleRef/capture/0","ObjectExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21645:21661:body/1/ruleRef/capture/0","ObjectExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21675:21692:body/2/ruleRef/capture/0","ObjectExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21706:21730:body/3/ruleRef/capture/0","ObjectExpression:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21744:21755:body/4/ruleRef/capture/0","ObjectExpression:4"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21769:21785:body/5/ruleRef/capture/0","ObjectExpression:5"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21969:21986:body/2/ruleRef/capture/0","IfExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22010:22026:body/5/ruleRef/capture/0","IfExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22060:22076:body/9/ruleRef/capture/0","IfExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22172:22192:body/0/ruleRef/capture/0","BranchExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22206:22232:body/1/ruleRef/capture/0","BranchExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22246:22271:body/2/ruleRef/capture/0","BranchExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22285:22301:body/3/ruleRef/capture/0","BranchExpression:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22315:22332:body/4/ruleRef/capture/0","BranchExpression:4"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22346:22362:body/5/ruleRef/capture/0","BranchExpression:5"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22376:22392:body/6/ruleRef/capture/0","BranchExpression:6"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22406:22422:body/7/ruleRef/capture/0","BranchExpression:7"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22601:22618:body/1/ruleRef/capture/0","TernaryExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22634:22650:body/3/ruleRef/capture/0","TernaryExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22665:22681:body/5/ruleRef/capture/0","TernaryExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22903:22913:body/2/ruleRef/capture/0","NumberMatchExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22931:22941:body/3/0/1/ruleRef/capture/0","NumberMatchExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22965:22982:body/5/ruleRef/capture/0","NumberMatchExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23078:23095:body/0/ruleRef/capture/0","NumberCase:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23112:23127:body/2/ruleRef/capture/0","NumberCase:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23226:23241:body/2/ruleRef/capture/0","NumberDefaultCase:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23321:23337:body/0/ruleRef/capture/0","NumberCaseValue:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23470:23480:body/2/ruleRef/capture/0","StringMatchExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23498:23508:body/3/0/1/ruleRef/capture/0","StringMatchExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23532:23549:body/5/ruleRef/capture/0","StringMatchExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23645:23662:body/0/ruleRef/capture/0","StringCase:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23679:23694:body/2/ruleRef/capture/0","StringCase:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23793:23808:body/2/ruleRef/capture/0","StringDefaultCase:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23888:23904:body/0/ruleRef/capture/0","StringCaseValue:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24039:24050:body/2/ruleRef/capture/0","BooleanMatchExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24068:24079:body/3/0/1/ruleRef/capture/0","BooleanMatchExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24103:24121:body/5/ruleRef/capture/0","BooleanMatchExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24219:24236:body/0/ruleRef/capture/0","BooleanCase:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24253:24269:body/2/ruleRef/capture/0","BooleanCase:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24370:24386:body/2/ruleRef/capture/0","BooleanDefaultCase:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24468:24485:body/0/ruleRef/capture/0","BooleanCaseValue:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24702:24712:body/1/tokenRef/capture/0","VariableRef:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24730:24741:body/2/0/1/ruleRef/capture/0","VariableRef:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25201:25217:body/0/ruleRef/capture/0","Expression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25231:25248:body/1/ruleRef/capture/0","Expression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25262:25278:body/2/ruleRef/capture/0","Expression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25292:25308:body/3/ruleRef/capture/0","Expression:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25322:25338:body/4/ruleRef/capture/0","Expression:4"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25356:25366:body/5/1/ruleRef/capture/0","Expression:5"));
    /** D-025: `@catalog` の静的表。入力に依存しない grammar metadata。 */
    private static final List<Object> CATALOGS = List.of(object("rule", "VariableRef", "context", "variable", "captures", List.of("name", "type")));
    /** D-025: 文法が宣言した scope mode（観測されたイベントの mode ではない）。 */
    private static final List<String> SCOPE_MODES = List.of("lexical");
    private static final Pattern NUMBER = Pattern.compile("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?");

    private static final Map<String, String> EXTERN_CLASSES = Map.ofEntries(Map.entry("TinyExpressionP4::STRING","org.unlaxer.tinyexpression.parser.StringLiteralParser"),Map.entry("TinyExpressionP4::CODE_START","org.unlaxer.tinyexpression.parser.javalang.CodeStartParser"),Map.entry("TinyExpressionP4::CODE_END","org.unlaxer.tinyexpression.parser.javalang.CodeEndParser"),Map.entry("TinyExpressionP4::LONG_CODE_BLOCK","org.unlaxer.tinyexpression.parser.javalang.LongCodeBlockParser"));
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
