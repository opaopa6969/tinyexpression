package org.unlaxer.tinyexpression.lsp.p4;

/**
 * A slice of a document that contains only the parseable TinyExpression formula.
 *
 * @param content    formula text to pass to the P4 parser
 * @param lineOffset 0-based line number in the <em>original</em> document where
 *                   {@code content} starts — used to shift diagnostic and
 *                   semantic-token positions back into the full document
 */
public record FormulaSection(String content, int lineOffset) {}
