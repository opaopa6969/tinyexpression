package org.unlaxer.tinyexpression.evaluator.javacode.ast;

public record NumberGeneratedBinaryAstNode(
    String operator,
    NumberGeneratedAstNode left,
    NumberGeneratedAstNode right) implements NumberGeneratedAstNode {
}
