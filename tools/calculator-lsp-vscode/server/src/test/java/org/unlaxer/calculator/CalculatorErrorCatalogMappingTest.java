package org.unlaxer.calculator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;

class CalculatorErrorCatalogMappingTest {

    @Test
    void mapsIfMissingClosingParenthesisToTe011() throws Exception {
        String expression = "if(true {1}else{0}";
        assertEquals("TE011", resolveCatalogCode(expression));
    }

    @Test
    void mapsIfConditionMissingParenthesesToTe011() throws Exception {
        String expression = "if true {1}else{0}";
        assertEquals("TE011", resolveCatalogCode(expression));
    }

    @Test
    void mapsIfEmptyConditionToTe011AndVarMissingSemicolonToTe006() throws Exception {
        String expression = "if(){1}else{0}";
        assertEquals("TE011", resolveCatalogCode(expression));
        String missingSemicolon = "var $input as string set if not exists 'n'\nif(true){1}else{0}";
        assertEquals("TE006", resolveCatalogCode(missingSemicolon));
        assertEquals("TE002", resolveCatalogCode("abc"));
        assertEquals("TE003", resolveCatalogCode("\"abc"));
        assertEquals("TE007", resolveCatalogCode("var $x as string set if not exists 'a' description='x"));
        assertEquals("TE008", resolveCatalogCode("var $x as string set if not exists 'a'；"));
        assertEquals("TE016", resolveCatalogCode("import CheckDigits#check;"));
        assertEquals("TE017", resolveCatalogCode("var name as string;"));
        assertEquals("TE018", resolveCatalogCode("as string $name"));
        assertEquals("TE019", resolveCatalogCode("get($x).orElse"));
        String varSetIfNotExistsPlusMatchMissingComma = """
                var $input as string set if not exists 'not number' description='入力値';
                match{
                  true -> 1
                  default -> 0
                }
                """;
        assertEquals("TE014", resolveCatalogCode(varSetIfNotExistsPlusMatchMissingComma));
        String missingCommaBetweenCases = """
                import CheckDigits#check as checkDigits;\r
                var $input as string set if not exists 'not number' description='入力値';\r
                var $didCheck as boolean set if not exists true description='aaa';\r
                var $name as string set if not exists 'value' description='';\r
                \r
                if(external returning as boolean checkDigits($input)){\r
                  1\r
                }else{\r
                  match{\r
                  $name=='肉太郎' -> 1\r
                  $didCheck -> 2,\r
                  default -> 0 }\r
                }\r
                """;
        assertEquals("TE013", resolveCatalogCode(missingCommaBetweenCases));
        String missingIfBlockOpen = """
                if(external returning as boolean checkDigits($input))
                  1
                }else{
                  match{
                    true -> 1,
                    default -> 0
                  }
                }
                """;
        assertEquals("TE010", resolveCatalogCode(missingIfBlockOpen));
        String missingArrowRhs = """
                match{
                  true -> ,
                  default -> 0
                }
                """;
        assertEquals("TE013", resolveCatalogCode(missingArrowRhs));
        String withJavaFenceAndMissingBlockOpen = """
                ```java:CheckDigits
                import org.unlaxer.tinyexpression.CalculationContext;
                public class CheckDigits{
                  public boolean check(CalculationContext c,String target){
                    return target.matches("\\\\d+");
                  }
                }
                ```
                import CheckDigits#check as checkDigits;
                var $input as string set if not exists 'not number' description='入力値';
                if(external returning as boolean checkDigits($input))
                  1
                }else{
                  match{
                    true -> 1,
                    default -> 0
                  }
                }
                """;
        assertEquals("TE010", resolveCatalogCode(withJavaFenceAndMissingBlockOpen));
    }

    @Test
    void parsesIfElseMatchWithStringEquality() {
        CalculatorLanguageServer server = new CalculatorLanguageServer();
        String expression = """
                if(true){
                  1
                }else{
                  match{
                  $name=='肉太郎' -> 1,
                  $didCheck -> 2,
                  default -> 0 }
                }
                """;
        CalculatorLanguageServer.ParseResult result = server.parseExpression(expression);
        assertTrue(result.succeeded, "if/match expression should parse successfully");
    }

    @Test
    void providesQuickFixForTe010MissingBlockOpen() throws Exception {
        CalculatorLanguageServer server = new CalculatorLanguageServer();
        String uri = "file:///te010-sample.tinyexp";
        String expression = """
                if(external returning as boolean checkDigits($input))
                  1
                }else{
                  match{
                    true -> 1,
                    default -> 0
                  }
                }
                """;
        server.parseDocument(uri, expression);

        Diagnostic diagnostic = new Diagnostic();
        diagnostic.setCode(Either.forLeft("TE010"));
        diagnostic.setRange(new Range(new Position(1, 0), new Position(7, 0)));
        diagnostic.setMessage("[TE010] 構文の並びが想定と一致しません。 修正: 直前の式と区切り記号を確認");

        CodeActionContext context = new CodeActionContext(List.of(diagnostic));
        CodeActionParams params = new CodeActionParams(new TextDocumentIdentifier(uri), diagnostic.getRange(), context);
        List<?> actions = server.getTextDocumentService().codeAction(params).get();
        assertFalse(actions.isEmpty(), "TE010 should provide at least one quick fix");
    }

    @Test
    void providesQuickFixForTe013MissingComma() throws Exception {
        CalculatorLanguageServer server = new CalculatorLanguageServer();
        String uri = "file:///te013-sample.tinyexp";
        String expression = """
                match{
                  $name=='肉太郎' -> 1
                  $didCheck -> 2,
                  default -> 0
                }
                """;
        server.parseDocument(uri, expression);

        Diagnostic diagnostic = new Diagnostic();
        diagnostic.setCode(Either.forLeft("TE013"));
        diagnostic.setRange(new Range(new Position(2, 2), new Position(5, 0)));
        diagnostic.setMessage("[TE013] match 構文が不正です。 修正: match { cond -> expr, default -> expr } を確認");

        CodeActionContext context = new CodeActionContext(List.of(diagnostic));
        CodeActionParams params = new CodeActionParams(new TextDocumentIdentifier(uri), diagnostic.getRange(), context);
        List<?> actions = server.getTextDocumentService().codeAction(params).get();
        assertFalse(actions.isEmpty(), "TE013 should provide at least one quick fix");
    }

    @Test
    void mapsOperatorNotationErrorsToTe023() throws Exception {
        assertEquals("TE023", resolveCatalogCode("true && false"));
        assertEquals("TE023", resolveCatalogCode("check($x) | )"));
        assertEquals("TE023", resolveCatalogCode("$check($x)"));
    }

    @Test
    void doesNotMisclassifyTe023WhenNotationAppearsOnlyInStringOrComment() throws Exception {
        String withComment = """
                if(true){
                  1
                }else{
                  0
                // && $check($x)
                """;
        assertEquals("TE005", resolveCatalogCode(withComment));

        String withString = """
                if(true){
                  'text && $check($x)'
                }else{
                  0
                """;
        assertEquals("TE005", resolveCatalogCode(withString));
    }

    @Test
    void providesQuickFixForTe023DoubleAnd() throws Exception {
        CalculatorLanguageServer server = new CalculatorLanguageServer();
        String uri = "file:///te023-double-and.tinyexp";
        String expression = "true && false";
        server.parseDocument(uri, expression);

        Diagnostic diagnostic = new Diagnostic();
        diagnostic.setCode(Either.forLeft("TE023"));
        diagnostic.setRange(new Range(new Position(0, 5), new Position(0, 7)));
        diagnostic.setMessage("[TE023] 演算子/記法が不正です。 修正: 条件式の演算子や記法（&/|、$method(...)）を修正");

        CodeActionContext context = new CodeActionContext(List.of(diagnostic));
        CodeActionParams params = new CodeActionParams(new TextDocumentIdentifier(uri), diagnostic.getRange(), context);
        List<?> actions = server.getTextDocumentService().codeAction(params).get();
        assertFalse(actions.isEmpty(), "TE023 should provide quick fix for &&");
    }

    @Test
    void providesQuickFixForTe023WithWideDiagnosticRange() throws Exception {
        CalculatorLanguageServer server = new CalculatorLanguageServer();
        String uri = "file:///te023-wide-range.tinyexp";
        String expression = "value = true && false";
        server.parseDocument(uri, expression);

        Diagnostic diagnostic = new Diagnostic();
        diagnostic.setCode(Either.forLeft("TE023"));
        diagnostic.setRange(new Range(new Position(0, 0), new Position(0, expression.length())));
        diagnostic.setMessage("[TE023] 演算子/記法が不正です。 修正: 条件式の演算子や記法（&/|、$method(...)）を修正");

        CodeActionContext context = new CodeActionContext(List.of(diagnostic));
        CodeActionParams params = new CodeActionParams(new TextDocumentIdentifier(uri), diagnostic.getRange(), context);
        List<?> actions = server.getTextDocumentService().codeAction(params).get();
        assertFalse(actions.isEmpty(), "TE023 should provide quick fix even when diagnostic range is wide");
    }

    @Test
    void providesMultipleQuickFixesForTe023WhenMultipleIssuesExist() throws Exception {
        CalculatorLanguageServer server = new CalculatorLanguageServer();
        String uri = "file:///te023-multi-issues.tinyexp";
        String expression = "value = $check($x) && false";
        server.parseDocument(uri, expression);

        Diagnostic diagnostic = new Diagnostic();
        diagnostic.setCode(Either.forLeft("TE023"));
        diagnostic.setRange(new Range(new Position(0, 0), new Position(0, expression.length())));
        diagnostic.setMessage("[TE023] 演算子/記法が不正です。 修正: 条件式の演算子や記法（&/|、$method(...)）を修正");

        CodeActionContext context = new CodeActionContext(List.of(diagnostic));
        CodeActionParams params = new CodeActionParams(new TextDocumentIdentifier(uri), diagnostic.getRange(), context);
        List<?> actions = server.getTextDocumentService().codeAction(params).get();
        assertTrue(actions.size() >= 2, "TE023 should provide multiple quick fixes when multiple issues exist");
        Either<?, ?> first = (Either<?, ?>) actions.get(0);
        assertTrue(first.isRight(), "TE023 quick fix result should contain CodeAction entries");
        CodeAction firstAction = (CodeAction) first.getRight();
        assertEquals("&& を & に修正", firstAction.getTitle());
        assertEquals("quickfix.rewrite", firstAction.getKind());
    }

    @Test
    void doesNotDuplicateSameTe023QuickFixKind() throws Exception {
        CalculatorLanguageServer server = new CalculatorLanguageServer();
        String uri = "file:///te023-duplicate-kind.tinyexp";
        String expression = "value = $check($x) && a && b";
        server.parseDocument(uri, expression);

        Diagnostic diagnostic = new Diagnostic();
        diagnostic.setCode(Either.forLeft("TE023"));
        diagnostic.setRange(new Range(new Position(0, 0), new Position(0, expression.length())));
        diagnostic.setMessage("[TE023] 演算子/記法が不正です。 修正: 条件式の演算子や記法（&/|、$method(...)）を修正");

        CodeActionContext context = new CodeActionContext(List.of(diagnostic));
        CodeActionParams params = new CodeActionParams(new TextDocumentIdentifier(uri), diagnostic.getRange(), context);
        List<?> actions = server.getTextDocumentService().codeAction(params).get();
        assertFalse(actions.isEmpty(), "TE023 should provide quick fixes");

        Set<String> titles = new HashSet<>();
        for (Object action : actions) {
            Either<?, ?> entry = (Either<?, ?>) action;
            assertTrue(entry.isRight(), "TE023 quick fix result should contain CodeAction entries");
            CodeAction codeAction = (CodeAction) entry.getRight();
            assertTrue(titles.add(codeAction.getTitle()), "TE023 quick fix titles should not be duplicated");
        }
    }

    @Test
    void classifiesTe023QuickFixKindsAsRewriteAndInsert() throws Exception {
        CalculatorLanguageServer server = new CalculatorLanguageServer();
        String uri = "file:///te023-kind-classification.tinyexp";
        String expression = "value = $check($x) && a | )";
        server.parseDocument(uri, expression);

        Diagnostic diagnostic = new Diagnostic();
        diagnostic.setCode(Either.forLeft("TE023"));
        diagnostic.setRange(new Range(new Position(0, 0), new Position(0, expression.length())));
        diagnostic.setMessage("[TE023] 演算子/記法が不正です。 修正: 条件式の演算子や記法（&/|、$method(...)）を修正");

        CodeActionContext context = new CodeActionContext(List.of(diagnostic));
        CodeActionParams params = new CodeActionParams(new TextDocumentIdentifier(uri), diagnostic.getRange(), context);
        List<?> actions = server.getTextDocumentService().codeAction(params).get();
        assertFalse(actions.isEmpty(), "TE023 should provide quick fixes");

        Set<String> kinds = new HashSet<>();
        for (Object action : actions) {
            Either<?, ?> entry = (Either<?, ?>) action;
            assertTrue(entry.isRight(), "TE023 quick fix result should contain CodeAction entries");
            CodeAction codeAction = (CodeAction) entry.getRight();
            if (codeAction.getKind() != null) {
                kinds.add(codeAction.getKind());
            }
        }
        assertTrue(kinds.contains("quickfix.rewrite"), "TE023 should include rewrite quick fix kind");
        assertTrue(kinds.contains("quickfix.insert"), "TE023 should include insert quick fix kind");
    }

    @Test
    void classifiesTe024QuickFixKindAsInsert() throws Exception {
        CalculatorLanguageServer server = new CalculatorLanguageServer();
        String uri = "file:///te024-kind-classification.tinyexp";
        String expression = "$kind";
        server.parseDocument(uri, expression);

        Diagnostic diagnostic = new Diagnostic();
        diagnostic.setCode(Either.forLeft("TE024"));
        diagnostic.setRange(new Range(new Position(0, 0), new Position(0, 5)));
        diagnostic.setMessage("[TE024] partialKey 変数の形式が不正です: $kind");

        CodeActionContext context = new CodeActionContext(List.of(diagnostic));
        CodeActionParams params = new CodeActionParams(new TextDocumentIdentifier(uri), diagnostic.getRange(), context);
        List<?> actions = server.getTextDocumentService().codeAction(params).get();
        assertFalse(actions.isEmpty(), "TE024 should provide quick fixes");

        Either<?, ?> first = (Either<?, ?>) actions.get(0);
        assertTrue(first.isRight(), "TE024 quick fix result should contain CodeAction entries");
        CodeAction action = (CodeAction) first.getRight();
        assertEquals("partialKey 変数に suffix を追加", action.getTitle());
        assertEquals("quickfix.insert", action.getKind());
    }

    @Test
    void classifiesTe021QuickFixKindAsRewrite() throws Exception {
        CalculatorLanguageServer server = new CalculatorLanguageServer();
        String uri = "file:///te021-kind-classification.tinyexp";
        String expression = "cosh(1)";
        server.parseDocument(uri, expression);

        Diagnostic diagnostic = new Diagnostic();
        diagnostic.setCode(Either.forLeft("TE021"));
        diagnostic.setRange(new Range(new Position(0, 0), new Position(0, 4)));
        diagnostic.setMessage("[TE021] 未定義メソッド呼び出し: cosh 修正: 候補: cos");

        CodeActionContext context = new CodeActionContext(List.of(diagnostic));
        CodeActionParams params = new CodeActionParams(new TextDocumentIdentifier(uri), diagnostic.getRange(), context);
        List<?> actions = server.getTextDocumentService().codeAction(params).get();
        assertFalse(actions.isEmpty(), "TE021 should provide quick fixes");

        Either<?, ?> first = (Either<?, ?>) actions.get(0);
        assertTrue(first.isRight(), "TE021 quick fix result should contain CodeAction entries");
        CodeAction action = (CodeAction) first.getRight();
        assertEquals("メソッド名を 'cos' に修正", action.getTitle());
        assertEquals("quickfix.rewrite", action.getKind());
    }

    @Test
    void classifiesTe022QuickFixKindAsRewrite() throws Exception {
        CalculatorLanguageServer server = new CalculatorLanguageServer();
        String uri = "file:///te022-kind-classification.tinyexp";
        String expression = "$ag";
        server.parseDocument(uri, expression);

        Diagnostic diagnostic = new Diagnostic();
        diagnostic.setCode(Either.forLeft("TE022"));
        diagnostic.setRange(new Range(new Position(0, 0), new Position(0, 3)));
        diagnostic.setMessage("[TE022] 利用可能な変数名ではありません: $ag 修正: 候補: $age");

        CodeActionContext context = new CodeActionContext(List.of(diagnostic));
        CodeActionParams params = new CodeActionParams(new TextDocumentIdentifier(uri), diagnostic.getRange(), context);
        List<?> actions = server.getTextDocumentService().codeAction(params).get();
        assertFalse(actions.isEmpty(), "TE022 should provide quick fixes");

        Either<?, ?> first = (Either<?, ?>) actions.get(0);
        assertTrue(first.isRight(), "TE022 quick fix result should contain CodeAction entries");
        CodeAction action = (CodeAction) first.getRight();
        assertEquals("変数名を '$age' に修正", action.getTitle());
        assertEquals("quickfix.rewrite", action.getKind());
    }

    @Test
    void parsesFormulaSectionsInsideFormulaInfoLikeFile() {
        CalculatorLanguageServer server = new CalculatorLanguageServer();
        String uri = "file:///default";
        String content = """
                tags:NORMAL
                description:test
                formula:
                if(true){
                  1
                }else{
                  0
                }
                ---END_OF_PART---
                tags:NORMAL
                formula:
                match{
                  true -> 1,
                  default -> 0
                }
                ---END_OF_PART---
                """;
        server.parseDocument(uri, content);
        CalculatorLanguageServer.DocumentState state = server.getDocuments().get(uri);
        assertTrue(state.sections.size() >= 2);
        assertTrue(state.sections.get(0).parseResult().succeeded);
        assertTrue(state.sections.get(1).parseResult().succeeded);
    }

    @Test
    void providesQuickFixForMissingCloseBraceBeforeElse() throws Exception {
        CalculatorLanguageServer server = new CalculatorLanguageServer();
        String uri = "file:///te009-else-closebrace.tinyexp";
        String expression = """
                if(true){
                  1
                else{
                  0
                }
                """;
        server.parseDocument(uri, expression);

        Diagnostic diagnostic = new Diagnostic();
        diagnostic.setCode(Either.forLeft("TE009"));
        diagnostic.setRange(new Range(new Position(2, 0), new Position(5, 0)));
        diagnostic.setMessage("[TE009] ここに不要なトークンがあります。 修正: 余分な語句を削除");

        CodeActionContext context = new CodeActionContext(List.of(diagnostic));
        CodeActionParams params = new CodeActionParams(new TextDocumentIdentifier(uri), diagnostic.getRange(), context);
        List<?> actions = server.getTextDocumentService().codeAction(params).get();
        assertFalse(actions.isEmpty(), "missing '}' before else should provide quick fix");
    }

    private String resolveCatalogCode(String content) throws Exception {
        CalculatorLanguageServer server = new CalculatorLanguageServer();
        CalculatorLanguageServer.ParseResult result = server.parseExpression(content);
        assertFalse(result.succeeded, "expression should fail parsing in this test");

        Method describe = CalculatorLanguageServer.class.getDeclaredMethod(
                "describeParseFailure", String.class, int.class, Object.class);
        describe.setAccessible(true);
        Object failure = describe.invoke(
                server,
                content,
                Math.max(0, result.consumedLength),
                result.failureDiagnostics);

        Class<?> failureClass = Class.forName(
                "org.unlaxer.calculator.CalculatorLanguageServer$ParseFailureDescription");
        Method resolve = CalculatorLanguageServer.class.getDeclaredMethod(
                "resolveErrorCatalogEntry", String.class, failureClass);
        resolve.setAccessible(true);
        Object entry = resolve.invoke(server, content, failure);

        Method code = entry.getClass().getDeclaredMethod("code");
        code.setAccessible(true);
        return String.valueOf(code.invoke(entry));
    }
}
