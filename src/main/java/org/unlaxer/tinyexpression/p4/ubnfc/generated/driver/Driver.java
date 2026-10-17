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
    private static final Map<String, String> BINDINGS = Map.ofEntries(Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1177:1186:body/0/0/ruleRef/capture/0","Formula:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1203:1220:body/1/0/ruleRef/capture/0","Formula:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1234:1253:body/2/0/ruleRef/capture/0","Formula:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1295:1315:body/4/0/0/0/ruleRef/capture/0","Formula:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1330:1347:body/4/0/0/1/0/ruleRef/capture/0","Formula:4"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1369:1390:body/4/0/1/0/ruleRef/capture/0","Formula:5"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1405:1422:body/4/0/1/1/0/ruleRef/capture/0","Formula:6"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1444:1464:body/4/0/2/0/ruleRef/capture/0","Formula:7"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1479:1496:body/4/0/2/1/0/ruleRef/capture/0","Formula:8"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1518:1538:body/4/0/3/0/ruleRef/capture/0","Formula:9"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1553:1570:body/4/0/3/1/0/ruleRef/capture/0","Formula:10"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1592:1612:body/4/0/4/0/ruleRef/capture/0","Formula:11"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1627:1644:body/4/0/4/1/0/ruleRef/capture/0","Formula:12"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1666:1693:body/4/0/5/0/ruleRef/capture/0","Formula:13"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1708:1725:body/4/0/5/1/0/ruleRef/capture/0","Formula:14"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2175:2226:body/0/group/capture/0","CodeBlock:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2691:2700:body/1/ruleRef/capture/0","ImportDeclaration:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2718:2728:body/2/0/1/tokenRef/capture/0","ImportDeclaration:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2744:2754:body/4/tokenRef/capture/0","ImportDeclaration:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2836:2846:body/0/tokenRef/capture/0","ClassName:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2859:2869:body/1/0/1/tokenRef/capture/0","ClassName:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3354:3364:body/2/tokenRef/capture/0","NumberVariableDeclaration:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3411:3423:body/4/0/1/0/ruleRef/capture/0","NumberVariableDeclaration:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3440:3456:body/4/0/2/ruleRef/capture/0","NumberVariableDeclaration:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3472:3483:body/5/0/ruleRef/capture/0","NumberVariableDeclaration:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3703:3713:body/2/tokenRef/capture/0","StringVariableDeclaration:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3760:3772:body/4/0/1/0/ruleRef/capture/0","StringVariableDeclaration:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3789:3805:body/4/0/2/ruleRef/capture/0","StringVariableDeclaration:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3821:3832:body/5/0/ruleRef/capture/0","StringVariableDeclaration:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4054:4064:body/2/tokenRef/capture/0","BooleanVariableDeclaration:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4112:4124:body/4/0/1/0/ruleRef/capture/0","BooleanVariableDeclaration:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4141:4158:body/4/0/2/ruleRef/capture/0","BooleanVariableDeclaration:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4174:4185:body/5/0/ruleRef/capture/0","BooleanVariableDeclaration:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4405:4415:body/2/tokenRef/capture/0","ObjectVariableDeclaration:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4462:4474:body/4/0/1/0/ruleRef/capture/0","ObjectVariableDeclaration:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4491:4507:body/4/0/2/ruleRef/capture/0","ObjectVariableDeclaration:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4523:4534:body/5/0/ruleRef/capture/0","ObjectVariableDeclaration:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5583:5593:body/1/tokenRef/capture/0","NumberMethodDeclaration:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5622:5638:body/3/0/ruleRef/capture/0","NumberMethodDeclaration:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5675:5691:body/6/ruleRef/capture/0","NumberMethodDeclaration:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5909:5919:body/1/tokenRef/capture/0","StringMethodDeclaration:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5948:5964:body/3/0/ruleRef/capture/0","StringMethodDeclaration:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6001:6017:body/6/ruleRef/capture/0","StringMethodDeclaration:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6238:6248:body/1/tokenRef/capture/0","BooleanMethodDeclaration:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6277:6293:body/3/0/ruleRef/capture/0","BooleanMethodDeclaration:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6330:6347:body/6/ruleRef/capture/0","BooleanMethodDeclaration:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6565:6575:body/1/tokenRef/capture/0","ObjectMethodDeclaration:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6604:6620:body/3/0/ruleRef/capture/0","ObjectMethodDeclaration:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6657:6673:body/6/ruleRef/capture/0","ObjectMethodDeclaration:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6770:6785:body/0/ruleRef/capture/0","MethodParameters:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6800:6815:body/1/0/1/ruleRef/capture/0","MethodParameters:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6943:6953:body/1/tokenRef/capture/0","MethodParameter:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6972:6982:body/2/0/1/ruleRef/capture/0","MethodParameter:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7631:7640:body/4/0/0/0/ruleRef/capture/0","ExternalBooleanInvocation:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7656:7666:body/4/0/0/2/tokenRef/capture/0","ExternalBooleanInvocation:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7675:7685:body/4/0/1/tokenRef/capture/0","ExternalBooleanInvocation:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7704:7713:body/6/0/ruleRef/capture/0","ExternalBooleanInvocation:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7922:7931:body/2/0/0/0/ruleRef/capture/0","ExternalNumberInvocation:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7947:7957:body/2/0/0/2/tokenRef/capture/0","ExternalNumberInvocation:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7966:7976:body/2/0/1/tokenRef/capture/0","ExternalNumberInvocation:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7995:8004:body/4/0/ruleRef/capture/0","ExternalNumberInvocation:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8199:8208:body/4/0/0/0/ruleRef/capture/0","ExternalStringInvocation:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8224:8234:body/4/0/0/2/tokenRef/capture/0","ExternalStringInvocation:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8243:8253:body/4/0/1/tokenRef/capture/0","ExternalStringInvocation:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8272:8281:body/6/0/ruleRef/capture/0","ExternalStringInvocation:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8476:8485:body/4/0/0/0/ruleRef/capture/0","ExternalObjectInvocation:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8501:8511:body/4/0/0/2/tokenRef/capture/0","ExternalObjectInvocation:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8520:8530:body/4/0/1/tokenRef/capture/0","ExternalObjectInvocation:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8549:8558:body/6/0/ruleRef/capture/0","ExternalObjectInvocation:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8935:8945:body/1/tokenRef/capture/0","MethodInvocation:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8958:8967:body/3/0/ruleRef/capture/0","MethodInvocation:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9146:9163:body/0/ruleRef/capture/0","ArgumentTernary:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9179:9195:body/2/ruleRef/capture/0","ArgumentTernary:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9210:9226:body/4/ruleRef/capture/0","ArgumentTernary:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9322:9337:body/0/ruleRef/capture/0","ArgumentExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9351:9371:body/1/ruleRef/capture/0","ArgumentExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9385:9411:body/2/ruleRef/capture/0","ArgumentExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9425:9435:body/3/ruleRef/capture/0","ArgumentExpression:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9505:9523:body/0/ruleRef/capture/0","Arguments:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9538:9556:body/1/0/1/ruleRef/capture/0","Arguments:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9761:9771:body/0/ruleRef/capture/0","NumberExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9780:9785:body/1/0/0/ruleRef/capture/0","NumberExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9790:9800:body/1/0/1/ruleRef/capture/0","NumberExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9916:9928:body/0/ruleRef/capture/0","NumberTerm:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9937:9942:body/1/0/0/ruleRef/capture/0","NumberTerm:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9947:9959:body/1/0/1/ruleRef/capture/0","NumberTerm:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10445:10463:body/2/ruleRef/capture/0","SinFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10538:10556:body/2/ruleRef/capture/0","CosFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10631:10649:body/2/ruleRef/capture/0","TanFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10727:10745:body/2/ruleRef/capture/0","SqrtFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10828:10846:body/2/ruleRef/capture/0","MinFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10860:10878:body/3/0/1/ruleRef/capture/0","MinFunction:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10964:10982:body/2/ruleRef/capture/0","MaxFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10996:11014:body/3/0/1/ruleRef/capture/0","MaxFunction:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11156:11174:body/2/ruleRef/capture/0","AbsFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11255:11273:body/2/ruleRef/capture/0","RoundFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11351:11369:body/2/ruleRef/capture/0","CeilFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11450:11468:body/2/ruleRef/capture/0","FloorFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11554:11572:body/2/ruleRef/capture/0","PowFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11583:11601:body/4/ruleRef/capture/0","PowFunction:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11681:11699:body/2/ruleRef/capture/0","LogFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11774:11792:body/2/ruleRef/capture/0","ExpFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11967:11983:body/2/ruleRef/capture/0","ToNumFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11995:12013:body/4/ruleRef/capture/0","ToNumFunction:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12511:12527:body/2/ruleRef/capture/0","ToUpperCaseFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12630:12646:body/2/ruleRef/capture/0","ToLowerCaseFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12728:12744:body/2/ruleRef/capture/0","TrimFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12832:12848:body/2/ruleRef/capture/0","LengthFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12930:12946:body/2/ruleRef/capture/0","LenFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13282:13293:body/0/ruleRef/capture/0","ToUpperCaseDotMethod:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13401:13412:body/0/ruleRef/capture/0","ToLowerCaseDotMethod:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13506:13517:body/0/ruleRef/capture/0","TrimDotMethod:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13608:13619:body/0/ruleRef/capture/0","LengthDotMethod:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13881:13897:body/2/ruleRef/capture/0","StartsWithFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13909:13925:body/4/ruleRef/capture/0","StartsWithFunction:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13946:13962:body/5/0/1/ruleRef/capture/0","StartsWithFunction:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14071:14087:body/2/ruleRef/capture/0","EndsWithFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14099:14115:body/4/ruleRef/capture/0","EndsWithFunction:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14136:14152:body/5/0/1/ruleRef/capture/0","EndsWithFunction:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14261:14277:body/2/ruleRef/capture/0","ContainsFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14289:14305:body/4/ruleRef/capture/0","ContainsFunction:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14326:14342:body/5/0/1/ruleRef/capture/0","ContainsFunction:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14424:14440:body/0/ruleRef/capture/0","InMethod:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14458:14474:body/3/ruleRef/capture/0","InMethod:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14493:14509:body/4/0/1/ruleRef/capture/0","InMethod:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14692:14715:body/0/ruleRef/capture/0","StartsWithDotMethod:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14741:14757:body/3/ruleRef/capture/0","StartsWithDotMethod:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14778:14794:body/4/0/1/ruleRef/capture/0","StartsWithDotMethod:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14892:14915:body/0/ruleRef/capture/0","EndsWithDotMethod:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14939:14955:body/3/ruleRef/capture/0","EndsWithDotMethod:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14976:14992:body/4/0/1/ruleRef/capture/0","EndsWithDotMethod:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15090:15113:body/0/ruleRef/capture/0","ContainsDotMethod:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15137:15153:body/3/ruleRef/capture/0","ContainsDotMethod:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15174:15190:body/4/0/1/ruleRef/capture/0","ContainsDotMethod:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15475:15486:body/2/ruleRef/capture/0","IsPresentFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15679:15695:body/2/ruleRef/capture/0","InTimeRangeFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15711:15727:body/4/ruleRef/capture/0","InTimeRangeFunction:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15872:15881:body/2/ruleRef/capture/0","InDayTimeRangeFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15896:15912:body/4/ruleRef/capture/0","InDayTimeRangeFunction:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15928:15937:body/6/ruleRef/capture/0","InDayTimeRangeFunction:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15950:15966:body/8/ruleRef/capture/0","InDayTimeRangeFunction:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17224:17241:body/0/0/ruleRef/capture/0","SliceBaseExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17253:17268:body/0/2/ruleRef/capture/0","SliceBaseExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17280:17293:body/0/4/ruleRef/capture/0","SliceBaseExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17303:17317:body/0/6/ruleRef/capture/0","SliceBaseExpression:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17334:17351:body/1/0/ruleRef/capture/0","SliceBaseExpression:4"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17363:17378:body/1/2/ruleRef/capture/0","SliceBaseExpression:5"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17390:17403:body/1/4/ruleRef/capture/0","SliceBaseExpression:6"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17419:17436:body/2/0/ruleRef/capture/0","SliceBaseExpression:7"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17448:17463:body/2/2/ruleRef/capture/0","SliceBaseExpression:8"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17479:17493:body/2/5/ruleRef/capture/0","SliceBaseExpression:9"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17510:17527:body/3/0/ruleRef/capture/0","SliceBaseExpression:10"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17539:17554:body/3/2/ruleRef/capture/0","SliceBaseExpression:11"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17576:17593:body/4/0/ruleRef/capture/0","SliceBaseExpression:12"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17609:17622:body/4/3/ruleRef/capture/0","SliceBaseExpression:13"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17632:17646:body/4/5/ruleRef/capture/0","SliceBaseExpression:14"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17663:17680:body/5/0/ruleRef/capture/0","SliceBaseExpression:15"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17696:17709:body/5/3/ruleRef/capture/0","SliceBaseExpression:16"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17725:17742:body/6/0/ruleRef/capture/0","SliceBaseExpression:17"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17762:17776:body/6/4/ruleRef/capture/0","SliceBaseExpression:18"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17793:17810:body/7/0/ruleRef/capture/0","SliceBaseExpression:19"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17923:17942:body/0/0/ruleRef/capture/0","SliceNestedExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17954:17969:body/0/2/ruleRef/capture/0","SliceNestedExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17981:17994:body/0/4/ruleRef/capture/0","SliceNestedExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18004:18018:body/0/6/ruleRef/capture/0","SliceNestedExpression:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18035:18054:body/1/0/ruleRef/capture/0","SliceNestedExpression:4"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18066:18081:body/1/2/ruleRef/capture/0","SliceNestedExpression:5"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18093:18106:body/1/4/ruleRef/capture/0","SliceNestedExpression:6"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18122:18141:body/2/0/ruleRef/capture/0","SliceNestedExpression:7"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18153:18168:body/2/2/ruleRef/capture/0","SliceNestedExpression:8"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18184:18198:body/2/5/ruleRef/capture/0","SliceNestedExpression:9"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18215:18234:body/3/0/ruleRef/capture/0","SliceNestedExpression:10"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18246:18261:body/3/2/ruleRef/capture/0","SliceNestedExpression:11"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18283:18302:body/4/0/ruleRef/capture/0","SliceNestedExpression:12"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18318:18331:body/4/3/ruleRef/capture/0","SliceNestedExpression:13"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18341:18355:body/4/5/ruleRef/capture/0","SliceNestedExpression:14"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18372:18391:body/5/0/ruleRef/capture/0","SliceNestedExpression:15"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18407:18420:body/5/3/ruleRef/capture/0","SliceNestedExpression:16"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18436:18455:body/6/0/ruleRef/capture/0","SliceNestedExpression:17"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18475:18489:body/6/4/ruleRef/capture/0","SliceNestedExpression:18"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18506:18525:body/7/0/ruleRef/capture/0","SliceNestedExpression:19"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18814:18824:body/0/ruleRef/capture/0","StringExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18833:18836:body/1/0/0/literal/capture/0","StringExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18841:18851:body/1/0/1/ruleRef/capture/0","StringExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19612:19622:body/4/tokenRef/capture/0","StringCastVariable:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19716:19726:body/1/tokenRef/capture/0","StringTypedVariable:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19992:20012:body/0/ruleRef/capture/0","BooleanExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20021:20024:body/1/0/0/literal/capture/0","BooleanExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20029:20049:body/1/0/1/ruleRef/capture/0","BooleanExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20195:20215:body/0/ruleRef/capture/0","BooleanAndExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20224:20227:body/1/0/0/literal/capture/0","BooleanAndExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20232:20252:body/1/0/1/ruleRef/capture/0","BooleanAndExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20398:20411:body/0/ruleRef/capture/0","BooleanXorExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20420:20423:body/1/0/0/literal/capture/0","BooleanXorExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20428:20441:body/1/0/1/ruleRef/capture/0","BooleanXorExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20598:20615:body/2/ruleRef/capture/0","NotExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21186:21203:body/0/ruleRef/capture/0","BooleanEqualityExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21210:21220:body/1/ruleRef/capture/0","BooleanEqualityExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21225:21242:body/2/ruleRef/capture/0","BooleanEqualityExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21325:21350:body/0/ruleRef/capture/0","BooleanFactor:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21364:21384:body/1/ruleRef/capture/0","BooleanFactor:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21398:21424:body/2/ruleRef/capture/0","BooleanFactor:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21438:21455:body/3/ruleRef/capture/0","BooleanFactor:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21719:21735:body/0/ruleRef/capture/0","StringComparisonExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21742:21752:body/1/ruleRef/capture/0","StringComparisonExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21757:21773:body/2/ruleRef/capture/0","StringComparisonExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21895:21911:body/0/ruleRef/capture/0","ComparisonExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21918:21927:body/1/ruleRef/capture/0","ComparisonExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21932:21948:body/2/ruleRef/capture/0","ComparisonExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22165:22181:body/0/ruleRef/capture/0","ObjectExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22195:22211:body/1/ruleRef/capture/0","ObjectExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22225:22242:body/2/ruleRef/capture/0","ObjectExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22256:22280:body/3/ruleRef/capture/0","ObjectExpression:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22294:22305:body/4/ruleRef/capture/0","ObjectExpression:4"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22319:22335:body/5/ruleRef/capture/0","ObjectExpression:5"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22519:22536:body/2/ruleRef/capture/0","IfExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22560:22576:body/5/ruleRef/capture/0","IfExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22610:22626:body/9/ruleRef/capture/0","IfExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22722:22742:body/0/ruleRef/capture/0","BranchExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22756:22782:body/1/ruleRef/capture/0","BranchExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22796:22821:body/2/ruleRef/capture/0","BranchExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22835:22851:body/3/ruleRef/capture/0","BranchExpression:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22865:22882:body/4/ruleRef/capture/0","BranchExpression:4"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22896:22912:body/5/ruleRef/capture/0","BranchExpression:5"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22926:22942:body/6/ruleRef/capture/0","BranchExpression:6"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22956:22972:body/7/ruleRef/capture/0","BranchExpression:7"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23151:23168:body/1/ruleRef/capture/0","TernaryExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23184:23200:body/3/ruleRef/capture/0","TernaryExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23215:23231:body/5/ruleRef/capture/0","TernaryExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23453:23463:body/2/ruleRef/capture/0","NumberMatchExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23481:23491:body/3/0/1/ruleRef/capture/0","NumberMatchExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23515:23532:body/5/ruleRef/capture/0","NumberMatchExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23628:23645:body/0/ruleRef/capture/0","NumberCase:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23662:23677:body/2/ruleRef/capture/0","NumberCase:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23776:23791:body/2/ruleRef/capture/0","NumberDefaultCase:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23871:23887:body/0/ruleRef/capture/0","NumberCaseValue:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24020:24030:body/2/ruleRef/capture/0","StringMatchExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24048:24058:body/3/0/1/ruleRef/capture/0","StringMatchExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24082:24099:body/5/ruleRef/capture/0","StringMatchExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24195:24212:body/0/ruleRef/capture/0","StringCase:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24229:24244:body/2/ruleRef/capture/0","StringCase:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24343:24358:body/2/ruleRef/capture/0","StringDefaultCase:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24438:24454:body/0/ruleRef/capture/0","StringCaseValue:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24589:24600:body/2/ruleRef/capture/0","BooleanMatchExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24618:24629:body/3/0/1/ruleRef/capture/0","BooleanMatchExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24653:24671:body/5/ruleRef/capture/0","BooleanMatchExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24769:24786:body/0/ruleRef/capture/0","BooleanCase:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24803:24819:body/2/ruleRef/capture/0","BooleanCase:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24920:24936:body/2/ruleRef/capture/0","BooleanDefaultCase:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25018:25035:body/0/ruleRef/capture/0","BooleanCaseValue:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25252:25262:body/1/tokenRef/capture/0","VariableRef:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25280:25291:body/2/0/1/ruleRef/capture/0","VariableRef:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25749:25765:body/0/ruleRef/capture/0","Expression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25779:25796:body/1/ruleRef/capture/0","Expression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25810:25826:body/2/ruleRef/capture/0","Expression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25840:25856:body/3/ruleRef/capture/0","Expression:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25870:25886:body/4/ruleRef/capture/0","Expression:4"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25904:25914:body/5/1/ruleRef/capture/0","Expression:5"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:26172:26188:body/0/ruleRef/capture/0","NumberRootExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:26270:26287:body/0/ruleRef/capture/0","BooleanRootExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:26368:26384:body/0/ruleRef/capture/0","StringRootExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:26465:26481:body/0/ruleRef/capture/0","ObjectRootExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:26562:26578:body/0/ruleRef/capture/0","MethodRootExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:26670:26680:body/1/ruleRef/capture/0","ParenthesizedRootExpression:0"));
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
