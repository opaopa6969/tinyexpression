package org.unlaxer.tinyexpression.evaluator.javacode.ast;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.ast.annotation.TinyAstField;
import org.unlaxer.tinyexpression.ast.annotation.TinyAstFieldSource;
import org.unlaxer.tinyexpression.ast.annotation.TinyAstNode;
import org.unlaxer.tinyexpression.ast.annotation.TinyAstNodeKind;
import org.unlaxer.tinyexpression.ast.annotation.TinyAstOperator;

public class NumberGeneratedAstAdapter {

  public static final NumberGeneratedAstAdapter SINGLETON = new NumberGeneratedAstAdapter();

  public Optional<NumberGeneratedAstNode> tryGenerate(Token token) {
    if (token == null) {
      throw new IllegalArgumentException("token must not be null");
    }
    return tryGenerateInternal(token);
  }

  public NumberGeneratedAstNode generateOrThrow(Token token) {
    Optional<NumberGeneratedAstNode> generated = tryGenerate(token);
    if (generated.isPresent()) {
      return generated.get();
    }
    throw new IllegalArgumentException(
        "Unsupported parser for annotation-generated number AST: "
            + token.parser.getClass().getName()
            + " (tokenPath=" + token.getPath() + ")");
  }

  private Optional<NumberGeneratedAstNode> tryGenerateInternal(Token token) {
    Parser parser = token.parser;
    Class<?> parserClass = parser.getClass();

    TinyAstNode nodeSpec = parserClass.getAnnotation(TinyAstNode.class);
    if (nodeSpec == null) {
      return Optional.empty();
    }

    return switch (nodeSpec.kind()) {
      case NUMBER_LITERAL -> Optional.of(generateLiteral(token, parserClass));
      case NUMBER_BINARY -> generateBinary(token, parserClass);
    };
  }

  private NumberGeneratedLiteralAstNode generateLiteral(Token token, Class<?> parserClass) {
    Map<String, TinyAstField> fields = fieldsByName(parserClass);
    TinyAstField literal = fields.get("literal");
    if (literal == null || literal.source() != TinyAstFieldSource.TOKEN_TEXT) {
      throw new IllegalArgumentException(
          "NUMBER_LITERAL requires @TinyAstField(name=\"literal\", source=TOKEN_TEXT): "
              + parserClass.getName());
    }

    String literalToken = token.getToken().orElse("");
    if (literalToken.isEmpty()) {
      throw new IllegalArgumentException(
          "NUMBER_LITERAL token text is empty: " + parserClass.getName()
              + " (tokenPath=" + token.getPath() + ")");
    }
    return new NumberGeneratedLiteralAstNode(literalToken);
  }

  private Optional<NumberGeneratedAstNode> generateBinary(Token token, Class<?> parserClass) {
    TinyAstOperator operatorSpec = parserClass.getAnnotation(TinyAstOperator.class);
    if (operatorSpec == null || operatorSpec.symbol().isBlank()) {
      throw new IllegalArgumentException(
          "NUMBER_BINARY requires non-empty @TinyAstOperator: " + parserClass.getName());
    }

    Map<String, TinyAstField> fields = fieldsByName(parserClass);
    TinyAstField leftSpec = requireChildField(parserClass, fields, "left");
    TinyAstField rightSpec = requireChildField(parserClass, fields, "right");

    Token leftToken = childAt(token, leftSpec.childIndex(), "left", parserClass);
    Token rightToken = childAt(token, rightSpec.childIndex(), "right", parserClass);

    Optional<NumberGeneratedAstNode> left = tryGenerateInternal(leftToken);
    if (left.isEmpty()) {
      return Optional.empty();
    }
    Optional<NumberGeneratedAstNode> right = tryGenerateInternal(rightToken);
    if (right.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(new NumberGeneratedBinaryAstNode(operatorSpec.symbol(), left.get(), right.get()));
  }

  private TinyAstField requireChildField(
      Class<?> parserClass, Map<String, TinyAstField> fields, String fieldName) {
    TinyAstField field = fields.get(fieldName);
    if (field == null) {
      throw new IllegalArgumentException(
          "Missing @TinyAstField(name=\"" + fieldName + "\") on " + parserClass.getName());
    }
    if (field.source() != TinyAstFieldSource.CHILD) {
      throw new IllegalArgumentException(
          fieldName + " field must use source=CHILD: " + parserClass.getName());
    }
    if (field.childIndex() < 0) {
      throw new IllegalArgumentException(
          fieldName + " field requires non-negative childIndex: " + parserClass.getName());
    }
    return field;
  }

  private Token childAt(Token token, int index, String fieldName, Class<?> parserClass) {
    if (index >= token.filteredChildren.size()) {
      throw new IllegalArgumentException(
          "Invalid childIndex for " + fieldName + ": " + index + " on " + parserClass.getName()
              + " (children=" + token.filteredChildren.size() + ", tokenPath=" + token.getPath() + ")");
    }
    return token.filteredChildren.get(index);
  }

  private Map<String, TinyAstField> fieldsByName(Class<?> parserClass) {
    TinyAstField[] fields = parserClass.getAnnotationsByType(TinyAstField.class);
    return Arrays.stream(fields).collect(Collectors.toMap(
        TinyAstField::name,
        field -> field,
        (first, ignored) -> first));
  }
}
