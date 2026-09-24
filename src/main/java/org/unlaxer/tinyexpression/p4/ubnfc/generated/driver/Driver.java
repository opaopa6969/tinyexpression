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
    private static final Map<String, String> BINDINGS = Map.ofEntries(Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:902:919:body/1/0/ruleRef/capture/0","Formula:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:933:952:body/2/0/ruleRef/capture/0","Formula:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:988:998:body/4/ruleRef/capture/0","Formula:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1013:1030:body/5/0/ruleRef/capture/0","Formula:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1776:1785:body/1/ruleRef/capture/0","ImportDeclaration:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1803:1813:body/2/0/1/tokenRef/capture/0","ImportDeclaration:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1829:1839:body/4/tokenRef/capture/0","ImportDeclaration:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1921:1931:body/0/tokenRef/capture/0","ClassName:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1944:1954:body/1/0/1/tokenRef/capture/0","ClassName:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2439:2449:body/2/tokenRef/capture/0","NumberVariableDeclaration:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2496:2508:body/4/0/1/0/ruleRef/capture/0","NumberVariableDeclaration:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2525:2541:body/4/0/2/ruleRef/capture/0","NumberVariableDeclaration:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2557:2568:body/5/0/ruleRef/capture/0","NumberVariableDeclaration:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2788:2798:body/2/tokenRef/capture/0","StringVariableDeclaration:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2845:2857:body/4/0/1/0/ruleRef/capture/0","StringVariableDeclaration:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2874:2890:body/4/0/2/ruleRef/capture/0","StringVariableDeclaration:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2906:2917:body/5/0/ruleRef/capture/0","StringVariableDeclaration:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3139:3149:body/2/tokenRef/capture/0","BooleanVariableDeclaration:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3197:3209:body/4/0/1/0/ruleRef/capture/0","BooleanVariableDeclaration:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3226:3243:body/4/0/2/ruleRef/capture/0","BooleanVariableDeclaration:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3259:3270:body/5/0/ruleRef/capture/0","BooleanVariableDeclaration:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3490:3500:body/2/tokenRef/capture/0","ObjectVariableDeclaration:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3547:3559:body/4/0/1/0/ruleRef/capture/0","ObjectVariableDeclaration:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3576:3592:body/4/0/2/ruleRef/capture/0","ObjectVariableDeclaration:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3608:3619:body/5/0/ruleRef/capture/0","ObjectVariableDeclaration:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4668:4678:body/1/tokenRef/capture/0","NumberMethodDeclaration:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4707:4723:body/3/0/ruleRef/capture/0","NumberMethodDeclaration:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4760:4776:body/6/ruleRef/capture/0","NumberMethodDeclaration:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4994:5004:body/1/tokenRef/capture/0","StringMethodDeclaration:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5033:5049:body/3/0/ruleRef/capture/0","StringMethodDeclaration:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5086:5102:body/6/ruleRef/capture/0","StringMethodDeclaration:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5323:5333:body/1/tokenRef/capture/0","BooleanMethodDeclaration:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5362:5378:body/3/0/ruleRef/capture/0","BooleanMethodDeclaration:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5415:5432:body/6/ruleRef/capture/0","BooleanMethodDeclaration:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5650:5660:body/1/tokenRef/capture/0","ObjectMethodDeclaration:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5689:5705:body/3/0/ruleRef/capture/0","ObjectMethodDeclaration:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5742:5758:body/6/ruleRef/capture/0","ObjectMethodDeclaration:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5855:5870:body/0/ruleRef/capture/0","MethodParameters:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5885:5900:body/1/0/1/ruleRef/capture/0","MethodParameters:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6028:6038:body/1/tokenRef/capture/0","MethodParameter:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6057:6067:body/2/0/1/ruleRef/capture/0","MethodParameter:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6716:6725:body/4/0/0/0/ruleRef/capture/0","ExternalBooleanInvocation:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6741:6751:body/4/0/0/2/tokenRef/capture/0","ExternalBooleanInvocation:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6760:6770:body/4/0/1/tokenRef/capture/0","ExternalBooleanInvocation:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6789:6798:body/6/0/ruleRef/capture/0","ExternalBooleanInvocation:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7007:7016:body/2/0/0/0/ruleRef/capture/0","ExternalNumberInvocation:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7032:7042:body/2/0/0/2/tokenRef/capture/0","ExternalNumberInvocation:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7051:7061:body/2/0/1/tokenRef/capture/0","ExternalNumberInvocation:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7080:7089:body/4/0/ruleRef/capture/0","ExternalNumberInvocation:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7284:7293:body/4/0/0/0/ruleRef/capture/0","ExternalStringInvocation:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7309:7319:body/4/0/0/2/tokenRef/capture/0","ExternalStringInvocation:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7328:7338:body/4/0/1/tokenRef/capture/0","ExternalStringInvocation:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7357:7366:body/6/0/ruleRef/capture/0","ExternalStringInvocation:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7561:7570:body/4/0/0/0/ruleRef/capture/0","ExternalObjectInvocation:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7586:7596:body/4/0/0/2/tokenRef/capture/0","ExternalObjectInvocation:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7605:7615:body/4/0/1/tokenRef/capture/0","ExternalObjectInvocation:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7634:7643:body/6/0/ruleRef/capture/0","ExternalObjectInvocation:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8020:8030:body/1/tokenRef/capture/0","MethodInvocation:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8043:8052:body/3/0/ruleRef/capture/0","MethodInvocation:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8231:8248:body/0/ruleRef/capture/0","ArgumentTernary:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8264:8280:body/2/ruleRef/capture/0","ArgumentTernary:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8295:8311:body/4/ruleRef/capture/0","ArgumentTernary:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8407:8422:body/0/ruleRef/capture/0","ArgumentExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8436:8456:body/1/ruleRef/capture/0","ArgumentExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8470:8496:body/2/ruleRef/capture/0","ArgumentExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8510:8520:body/3/ruleRef/capture/0","ArgumentExpression:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8590:8608:body/0/ruleRef/capture/0","Arguments:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8623:8641:body/1/0/1/ruleRef/capture/0","Arguments:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8846:8856:body/0/ruleRef/capture/0","NumberExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8865:8870:body/1/0/0/ruleRef/capture/0","NumberExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8875:8885:body/1/0/1/ruleRef/capture/0","NumberExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9001:9013:body/0/ruleRef/capture/0","NumberTerm:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9022:9027:body/1/0/0/ruleRef/capture/0","NumberTerm:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9032:9044:body/1/0/1/ruleRef/capture/0","NumberTerm:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9530:9548:body/2/ruleRef/capture/0","SinFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9623:9641:body/2/ruleRef/capture/0","CosFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9716:9734:body/2/ruleRef/capture/0","TanFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9812:9830:body/2/ruleRef/capture/0","SqrtFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9913:9931:body/2/ruleRef/capture/0","MinFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9945:9963:body/3/0/1/ruleRef/capture/0","MinFunction:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10049:10067:body/2/ruleRef/capture/0","MaxFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10081:10099:body/3/0/1/ruleRef/capture/0","MaxFunction:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10241:10259:body/2/ruleRef/capture/0","AbsFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10340:10358:body/2/ruleRef/capture/0","RoundFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10436:10454:body/2/ruleRef/capture/0","CeilFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10535:10553:body/2/ruleRef/capture/0","FloorFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10639:10657:body/2/ruleRef/capture/0","PowFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10668:10686:body/4/ruleRef/capture/0","PowFunction:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10766:10784:body/2/ruleRef/capture/0","LogFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10859:10877:body/2/ruleRef/capture/0","ExpFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11052:11068:body/2/ruleRef/capture/0","ToNumFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11080:11098:body/4/ruleRef/capture/0","ToNumFunction:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11596:11612:body/2/ruleRef/capture/0","ToUpperCaseFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11715:11731:body/2/ruleRef/capture/0","ToLowerCaseFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11813:11829:body/2/ruleRef/capture/0","TrimFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11917:11933:body/2/ruleRef/capture/0","LengthFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12015:12031:body/2/ruleRef/capture/0","LenFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12367:12378:body/0/ruleRef/capture/0","ToUpperCaseDotMethod:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12486:12497:body/0/ruleRef/capture/0","ToLowerCaseDotMethod:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12591:12602:body/0/ruleRef/capture/0","TrimDotMethod:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12693:12704:body/0/ruleRef/capture/0","LengthDotMethod:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12966:12982:body/2/ruleRef/capture/0","StartsWithFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12994:13010:body/4/ruleRef/capture/0","StartsWithFunction:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13031:13047:body/5/0/1/ruleRef/capture/0","StartsWithFunction:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13156:13172:body/2/ruleRef/capture/0","EndsWithFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13184:13200:body/4/ruleRef/capture/0","EndsWithFunction:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13221:13237:body/5/0/1/ruleRef/capture/0","EndsWithFunction:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13346:13362:body/2/ruleRef/capture/0","ContainsFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13374:13390:body/4/ruleRef/capture/0","ContainsFunction:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13411:13427:body/5/0/1/ruleRef/capture/0","ContainsFunction:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13509:13525:body/0/ruleRef/capture/0","InMethod:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13543:13559:body/3/ruleRef/capture/0","InMethod:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13578:13594:body/4/0/1/ruleRef/capture/0","InMethod:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13777:13800:body/0/ruleRef/capture/0","StartsWithDotMethod:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13826:13842:body/3/ruleRef/capture/0","StartsWithDotMethod:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13863:13879:body/4/0/1/ruleRef/capture/0","StartsWithDotMethod:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13977:14000:body/0/ruleRef/capture/0","EndsWithDotMethod:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14024:14040:body/3/ruleRef/capture/0","EndsWithDotMethod:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14061:14077:body/4/0/1/ruleRef/capture/0","EndsWithDotMethod:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14175:14198:body/0/ruleRef/capture/0","ContainsDotMethod:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14222:14238:body/3/ruleRef/capture/0","ContainsDotMethod:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14259:14275:body/4/0/1/ruleRef/capture/0","ContainsDotMethod:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14560:14571:body/2/ruleRef/capture/0","IsPresentFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14764:14780:body/2/ruleRef/capture/0","InTimeRangeFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14796:14812:body/4/ruleRef/capture/0","InTimeRangeFunction:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14957:14966:body/2/ruleRef/capture/0","InDayTimeRangeFunction:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14981:14997:body/4/ruleRef/capture/0","InDayTimeRangeFunction:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15013:15022:body/6/ruleRef/capture/0","InDayTimeRangeFunction:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15035:15051:body/8/ruleRef/capture/0","InDayTimeRangeFunction:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16309:16326:body/0/0/ruleRef/capture/0","SliceBaseExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16338:16353:body/0/2/ruleRef/capture/0","SliceBaseExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16365:16378:body/0/4/ruleRef/capture/0","SliceBaseExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16388:16402:body/0/6/ruleRef/capture/0","SliceBaseExpression:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16419:16436:body/1/0/ruleRef/capture/0","SliceBaseExpression:4"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16448:16463:body/1/2/ruleRef/capture/0","SliceBaseExpression:5"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16475:16488:body/1/4/ruleRef/capture/0","SliceBaseExpression:6"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16504:16521:body/2/0/ruleRef/capture/0","SliceBaseExpression:7"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16533:16548:body/2/2/ruleRef/capture/0","SliceBaseExpression:8"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16564:16578:body/2/5/ruleRef/capture/0","SliceBaseExpression:9"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16595:16612:body/3/0/ruleRef/capture/0","SliceBaseExpression:10"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16624:16639:body/3/2/ruleRef/capture/0","SliceBaseExpression:11"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16661:16678:body/4/0/ruleRef/capture/0","SliceBaseExpression:12"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16694:16707:body/4/3/ruleRef/capture/0","SliceBaseExpression:13"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16717:16731:body/4/5/ruleRef/capture/0","SliceBaseExpression:14"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16748:16765:body/5/0/ruleRef/capture/0","SliceBaseExpression:15"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16781:16794:body/5/3/ruleRef/capture/0","SliceBaseExpression:16"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16810:16827:body/6/0/ruleRef/capture/0","SliceBaseExpression:17"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16847:16861:body/6/4/ruleRef/capture/0","SliceBaseExpression:18"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16878:16895:body/7/0/ruleRef/capture/0","SliceBaseExpression:19"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17008:17027:body/0/0/ruleRef/capture/0","SliceNestedExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17039:17054:body/0/2/ruleRef/capture/0","SliceNestedExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17066:17079:body/0/4/ruleRef/capture/0","SliceNestedExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17089:17103:body/0/6/ruleRef/capture/0","SliceNestedExpression:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17120:17139:body/1/0/ruleRef/capture/0","SliceNestedExpression:4"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17151:17166:body/1/2/ruleRef/capture/0","SliceNestedExpression:5"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17178:17191:body/1/4/ruleRef/capture/0","SliceNestedExpression:6"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17207:17226:body/2/0/ruleRef/capture/0","SliceNestedExpression:7"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17238:17253:body/2/2/ruleRef/capture/0","SliceNestedExpression:8"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17269:17283:body/2/5/ruleRef/capture/0","SliceNestedExpression:9"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17300:17319:body/3/0/ruleRef/capture/0","SliceNestedExpression:10"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17331:17346:body/3/2/ruleRef/capture/0","SliceNestedExpression:11"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17368:17387:body/4/0/ruleRef/capture/0","SliceNestedExpression:12"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17403:17416:body/4/3/ruleRef/capture/0","SliceNestedExpression:13"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17426:17440:body/4/5/ruleRef/capture/0","SliceNestedExpression:14"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17457:17476:body/5/0/ruleRef/capture/0","SliceNestedExpression:15"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17492:17505:body/5/3/ruleRef/capture/0","SliceNestedExpression:16"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17521:17540:body/6/0/ruleRef/capture/0","SliceNestedExpression:17"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17560:17574:body/6/4/ruleRef/capture/0","SliceNestedExpression:18"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17591:17610:body/7/0/ruleRef/capture/0","SliceNestedExpression:19"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17899:17909:body/0/ruleRef/capture/0","StringExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17918:17921:body/1/0/0/literal/capture/0","StringExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17926:17936:body/1/0/1/ruleRef/capture/0","StringExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18697:18707:body/4/tokenRef/capture/0","StringCastVariable:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18801:18811:body/1/tokenRef/capture/0","StringTypedVariable:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19077:19097:body/0/ruleRef/capture/0","BooleanExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19106:19109:body/1/0/0/literal/capture/0","BooleanExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19114:19134:body/1/0/1/ruleRef/capture/0","BooleanExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19280:19300:body/0/ruleRef/capture/0","BooleanAndExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19309:19312:body/1/0/0/literal/capture/0","BooleanAndExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19317:19337:body/1/0/1/ruleRef/capture/0","BooleanAndExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19483:19496:body/0/ruleRef/capture/0","BooleanXorExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19505:19508:body/1/0/0/literal/capture/0","BooleanXorExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19513:19526:body/1/0/1/ruleRef/capture/0","BooleanXorExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19683:19700:body/2/ruleRef/capture/0","NotExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20271:20288:body/0/ruleRef/capture/0","BooleanEqualityExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20295:20305:body/1/ruleRef/capture/0","BooleanEqualityExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20310:20327:body/2/ruleRef/capture/0","BooleanEqualityExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20410:20435:body/0/ruleRef/capture/0","BooleanFactor:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20449:20469:body/1/ruleRef/capture/0","BooleanFactor:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20483:20509:body/2/ruleRef/capture/0","BooleanFactor:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20523:20540:body/3/ruleRef/capture/0","BooleanFactor:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20804:20820:body/0/ruleRef/capture/0","StringComparisonExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20827:20837:body/1/ruleRef/capture/0","StringComparisonExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20842:20858:body/2/ruleRef/capture/0","StringComparisonExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20980:20996:body/0/ruleRef/capture/0","ComparisonExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21003:21012:body/1/ruleRef/capture/0","ComparisonExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21017:21033:body/2/ruleRef/capture/0","ComparisonExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21250:21266:body/0/ruleRef/capture/0","ObjectExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21280:21296:body/1/ruleRef/capture/0","ObjectExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21310:21327:body/2/ruleRef/capture/0","ObjectExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21341:21365:body/3/ruleRef/capture/0","ObjectExpression:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21379:21390:body/4/ruleRef/capture/0","ObjectExpression:4"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21404:21420:body/5/ruleRef/capture/0","ObjectExpression:5"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21604:21621:body/2/ruleRef/capture/0","IfExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21645:21661:body/5/ruleRef/capture/0","IfExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21695:21711:body/9/ruleRef/capture/0","IfExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21807:21827:body/0/ruleRef/capture/0","BranchExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21841:21867:body/1/ruleRef/capture/0","BranchExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21881:21906:body/2/ruleRef/capture/0","BranchExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21920:21936:body/3/ruleRef/capture/0","BranchExpression:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21950:21967:body/4/ruleRef/capture/0","BranchExpression:4"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21981:21997:body/5/ruleRef/capture/0","BranchExpression:5"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22011:22027:body/6/ruleRef/capture/0","BranchExpression:6"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22041:22057:body/7/ruleRef/capture/0","BranchExpression:7"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22236:22253:body/1/ruleRef/capture/0","TernaryExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22269:22285:body/3/ruleRef/capture/0","TernaryExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22300:22316:body/5/ruleRef/capture/0","TernaryExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22538:22548:body/2/ruleRef/capture/0","NumberMatchExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22566:22576:body/3/0/1/ruleRef/capture/0","NumberMatchExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22600:22617:body/5/ruleRef/capture/0","NumberMatchExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22713:22730:body/0/ruleRef/capture/0","NumberCase:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22747:22762:body/2/ruleRef/capture/0","NumberCase:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22861:22876:body/2/ruleRef/capture/0","NumberDefaultCase:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22956:22972:body/0/ruleRef/capture/0","NumberCaseValue:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23105:23115:body/2/ruleRef/capture/0","StringMatchExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23133:23143:body/3/0/1/ruleRef/capture/0","StringMatchExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23167:23184:body/5/ruleRef/capture/0","StringMatchExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23280:23297:body/0/ruleRef/capture/0","StringCase:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23314:23329:body/2/ruleRef/capture/0","StringCase:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23428:23443:body/2/ruleRef/capture/0","StringDefaultCase:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23523:23539:body/0/ruleRef/capture/0","StringCaseValue:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23674:23685:body/2/ruleRef/capture/0","BooleanMatchExpression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23703:23714:body/3/0/1/ruleRef/capture/0","BooleanMatchExpression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23738:23756:body/5/ruleRef/capture/0","BooleanMatchExpression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23854:23871:body/0/ruleRef/capture/0","BooleanCase:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23888:23904:body/2/ruleRef/capture/0","BooleanCase:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24005:24021:body/2/ruleRef/capture/0","BooleanDefaultCase:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24103:24120:body/0/ruleRef/capture/0","BooleanCaseValue:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24337:24347:body/1/tokenRef/capture/0","VariableRef:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24365:24376:body/2/0/1/ruleRef/capture/0","VariableRef:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24836:24852:body/0/ruleRef/capture/0","Expression:0"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24866:24883:body/1/ruleRef/capture/0","Expression:1"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24897:24913:body/2/ruleRef/capture/0","Expression:2"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24927:24943:body/3/ruleRef/capture/0","Expression:3"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24957:24973:body/4/ruleRef/capture/0","Expression:4"),Map.entry("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24991:25001:body/5/1/ruleRef/capture/0","Expression:5"));
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
        if (value instanceof String text && spans.get(value) instanceof Span span) texts.add(List.of(span.start(), span.end(), text));
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
        if (fields.get("left") == null && rights.isEmpty() && ops.size() == 1) return numeric(ops.getFirst());
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
