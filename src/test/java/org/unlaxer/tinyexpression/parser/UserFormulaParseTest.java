package org.unlaxer.tinyexpression.parser;

import java.io.PrintStream;

import org.junit.Test;
import org.unlaxer.Parsed;
import org.unlaxer.StringSource;
import org.unlaxer.Token;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;

public class UserFormulaParseTest {

  // The original formula the user reported as a parse error.
  static final String ORIGINAL =
      "import jp.caulis.nimt.customfunction.sideeffect.AccountEventHistoryFilter#filterByEventTypeAndDuration as filterByEventTypeAndDuration;\n"
    + "import jp.caulis.nimt.customfunction.sideeffect.AccountEventHistoryFilter#filterByAnySuspiciousKey as filterByAnySuspiciousKey;\n"
    + "import jp.caulis.nimt.customfunction.sideeffect.AccountEventHistoryFilter#filterByAnyChangedItem as filterByAnyChangedItem;\n"
    + "\n"
    + "if ($endpoint == 'withdrawal'\n"
    + "    // No1\n"
    + "    // 30分以内のログインイベントを抽出\n"
    + "    & external:filterByEventTypeAndDuration('No2_No1', 'login', ($oneMinute * 30)) >= 1\n"
    + "    // 初回プロバイダー,端末IDを抽出\n"
    + "    & external:filterByAnySuspiciousKey('No2_No1', 'FirstProvider', 'FirstLoggedInTerminal') >= 1\n"
    + "\n"
    + "    // No2\n"
    + "    // 30分以内の基本情報変更イベントを抽出\n"
    + "    & external:filterByEventTypeAndDuration('No2_No2', 'informationChange', ($oneMinute * 30)) >= 1\n"
    + "    // 大和ネクスト銀行で登録のメールアドレスの登録／変更 または 大和ネクスト銀行の振込限度額の変更\n"
    + "    & (external:filterByAnyChangedItem('No2_No2', 'email_primary', 'email_sub1', 'email_sub2', 'email_sub3') >= 1\n"
    + "    | $calculated_OriginalSpec_LastTransferLimitIncreased > 0.0)\n"
    + "\n"
    + "    // No3\n"
    + "    // 振込先口座（仕向先口座）の受取人名と預金者名義が異なる ただし口座名義人名と同じ名字が振込先口座に含まれている場合は除外\n"
    + "    & $calculated_OriginalSpec_IsAccountHolderNameMismatchExceptForIncludeSurname > 0.0\n"
    + "  ) {\n"
    + "  1\n"
    + "} else {\n"
    + "  0\n"
    + "}";

  @Test
  public void original() {
    parseAndReport("ORIGINAL", ORIGINAL);
  }

  // Hypothesis 1: add `returning as number :` to every external call.
  @Test
  public void variant_withReturningAs() {
    String formula = ORIGINAL
        .replace("external:filterByEventTypeAndDuration(",
                 "external returning as number : filterByEventTypeAndDuration(")
        .replace("external:filterByAnySuspiciousKey(",
                 "external returning as number : filterByAnySuspiciousKey(")
        .replace("external:filterByAnyChangedItem(",
                 "external returning as number : filterByAnyChangedItem(");
    parseAndReport("WITH returning as number", formula);
  }

  // Hypothesis 2: drop the line comments.
  @Test
  public void variant_noLineComments() {
    String formula = stripLineComments(ORIGINAL);
    parseAndReport("NO line comments", formula);
  }

  // Hypothesis 3: drop the extra parentheses around `($oneMinute * 30)`.
  @Test
  public void variant_noParenAroundMul() {
    String formula = ORIGINAL.replace("($oneMinute * 30)", "$oneMinute * 30");
    parseAndReport("NO outer parens around $oneMinute*30", formula);
  }

  // Hypothesis 4: combine 1+2+3.
  @Test
  public void variant_allFixes() {
    String formula = ORIGINAL
        .replace("external:filterByEventTypeAndDuration(",
                 "external returning as number : filterByEventTypeAndDuration(")
        .replace("external:filterByAnySuspiciousKey(",
                 "external returning as number : filterByAnySuspiciousKey(")
        .replace("external:filterByAnyChangedItem(",
                 "external returning as number : filterByAnyChangedItem(");
    formula = stripLineComments(formula);
    formula = formula.replace("($oneMinute * 30)", "$oneMinute * 30");
    parseAndReport("ALL fixes", formula);
  }

  // Tiny isolated cases.
  @Test
  public void minimal_externalAlias_noReturning() {
    String formula =
        "import a.b.C#m as m;\n"
      + "external:m(1)";
    parseAndReport("MINIMAL external:alias(...)", formula);
  }

  @Test
  public void minimal_externalAlias_withReturning() {
    String formula =
        "import a.b.C#m as m;\n"
      + "external returning as number : m(1)";
    parseAndReport("MINIMAL external returning as number : alias(...)", formula);
  }

  @Test
  public void minimal_externalFqcn_noReturning() {
    String formula = "external:a.b.C#m(1)";
    parseAndReport("MINIMAL external:Class#method(...)", formula);
  }

  static String stripLineComments(String input) {
    StringBuilder sb = new StringBuilder();
    boolean inLine = false;
    boolean inSingle = false;
    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);
      char n = i + 1 < input.length() ? input.charAt(i + 1) : '\0';
      if (inLine) {
        if (c == '\n') {
          inLine = false;
          sb.append(c);
        }
        continue;
      }
      if (!inSingle && c == '/' && n == '/') {
        inLine = true;
        i++;
        continue;
      }
      if (c == '\'' && (i == 0 || input.charAt(i - 1) != '\\')) inSingle = !inSingle;
      sb.append(c);
    }
    return sb.toString();
  }

  static void parseAndReport(String label, String formula) {
    System.out.println("============================================================");
    System.out.println("CASE: " + label);
    System.out.println("------------------------------------------------------------");
    System.out.println(formula);
    System.out.println("------------------------------------------------------------");

    try (ParseContext parseContext = new ParseContext(StringSource.createRootSource(formula))) {
      FormulaParser formulaParser = Parser.get(FormulaParser.class);
      Parsed parsed;
      try {
        parsed = formulaParser.parse(parseContext);
      } catch (Throwable t) {
        System.out.println("RESULT: THROWN " + t.getClass().getSimpleName() + ": " + t.getMessage());
        return;
      }

      boolean ok = parsed.isSucceeded() && parseContext.allConsumed();
      System.out.println("RESULT: " + (ok ? "OK" : "FAIL")
          + "  succeeded=" + parsed.isSucceeded()
          + "  allConsumed=" + parseContext.allConsumed());
      if (!ok) {
        Token root = parsed.getRootToken();
        if (root != null) {
          int consumed = root.tokenRange == null ? -1 : root.tokenRange.endIndexExclusive;
          System.out.println("Consumed up to index: " + consumed + " (of " + formula.length() + ")");
          if (consumed >= 0 && consumed < formula.length()) {
            int from = Math.max(0, consumed - 20);
            int to = Math.min(formula.length(), consumed + 40);
            System.out.println("Around failure: ..." + formula.substring(from, to).replace("\n", "\\n") + "...");
          }
          System.out.println("--- token tree ---");
          output(root, System.out, 0);
        }
      }
    }
  }

  static void output(Token token, PrintStream out, int level) {
    if (!token.tokenString.isPresent()) return;
    for (int i = 0; i < level; i++) out.print(" ");
    String s = token.tokenString.get();
    if (s.length() > 60) s = s.substring(0, 57) + "...";
    out.format("%s : %s%n", s.replace("\n", "\\n"), token.parser.getClass().getSimpleName());
    if (!token.filteredChildren.isEmpty()) {
      for (Token child : token.filteredChildren) output(child, out, level + 1);
    }
  }
}
