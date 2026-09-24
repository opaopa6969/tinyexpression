package org.unlaxer.tinyexpression.p4.ubnfc.generated.api;
public record Capture(long occurrenceId, String siteId, String name, Span span, long completionOrder, long ruleCompletionOrder) {
    public Capture(long occurrenceId, String siteId, String name, Span span, long completionOrder) {
        this(occurrenceId, siteId, name, span, completionOrder, completionOrder);
    }
}
