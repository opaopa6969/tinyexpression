package org.unlaxer.tinyexpression.p4.ubnfc.generated.api;
import java.util.List;
public record Diagnostic(String kind, int offset, int farthestCp, List<String> expected,
                         String deepestRule, List<String> ruleStack, List<String> farthestExpected,
                         String severity, int length, String origin) {
    public Diagnostic { expected = List.copyOf(expected); ruleStack = List.copyOf(ruleStack); farthestExpected = List.copyOf(farthestExpected); }
    public Diagnostic(String kind, int offset, int farthestCp, List<String> expected,
                      String deepestRule, List<String> ruleStack, List<String> farthestExpected) {
        this(kind, offset, farthestCp, expected, deepestRule, ruleStack, farthestExpected, "ERROR", 0, "parser");
    }
    public Diagnostic(String kind, int offset, int farthestCp, List<String> expected, String deepestRule, List<String> ruleStack) {
        this(kind, offset, farthestCp, expected, deepestRule, ruleStack, expected);
    }
}
