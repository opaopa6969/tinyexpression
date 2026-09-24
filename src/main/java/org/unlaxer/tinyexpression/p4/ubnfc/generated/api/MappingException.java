package org.unlaxer.tinyexpression.p4.ubnfc.generated.api;
public final class MappingException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;
    private final transient ParseResult<?> syntax;
    private final transient Span span;
    public MappingException(ParseResult<?> syntax, Span span, String message, Throwable cause) {
        super(message + " at " + span, cause); this.syntax = syntax; this.span = span;
    }
    public ParseResult<?> syntax() { return syntax; }
    public Span span() { return span; }
}
