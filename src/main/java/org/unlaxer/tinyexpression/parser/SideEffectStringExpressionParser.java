package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.ascii.LeftParenthesisParser;
import org.unlaxer.parser.ascii.RightParenthesisParser;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedLazyChain;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.parser.JavaClassMethodParser.ClassNameAndIdentifier;
import org.unlaxer.tinyexpression.parser.SideEffectExpressionParser.SideEffectNameParser;

public class SideEffectStringExpressionParser extends WhiteSpaceDelimitedLazyChain
    implements Expression {

  private static final long serialVersionUID = 6172097671148475538L;


  public SideEffectStringExpressionParser() {
    super();
  }



  List<Parser> parsers;


  @Override
  public void initialize() {
    parsers = new Parsers(Parser.get(SideEffectNameParser.class),
        Parser.get(() -> new WordParser(":")), Parser.get(JavaClassMethodParser.class), // 2
        Parser.get(LeftParenthesisParser.class),
        Parser.get(SideEffectStringExpressionParameterParser.class), // 4
        Parser.get(RightParenthesisParser.class));
  }



  @Override
  public List<Parser> getLazyParsers() {
    return parsers;
  }

  public static Token getMethodClause(Token thisParserParsed) {
    return thisParserParsed.filteredChildren.get(2);
  }

  public static Token getParametersClause(Token thisParserParsed) {
    return thisParserParsed.filteredChildren.get(4);
  }

  public static MethodAndParameters extract(Token token) {

    Token classMethod = getMethodClause(token);// TODO
                                                      // token.getChild(JavaClassMethodParser.class);

    ClassNameAndIdentifier extract = Parser.get(JavaClassMethodParser.class).extract(classMethod);

    Token parameter = getParametersClause(token);

    SideEffectStringExpressionParameterParser sideEffectStringExpressionParameterParser =
        Parser.get(SideEffectStringExpressionParameterParser.class);

    List<Token> parameterTokens = sideEffectStringExpressionParameterParser.parameterTokens(parameter);

    return new MethodAndParameters(extract, parameterTokens);
  }

  public static class MethodAndParameters {
    public final ClassNameAndIdentifier classNameAndIdentifier;
    public final List<Token> parameterTokens;
    public final Class<?>[] parameterTypes;

    public MethodAndParameters(ClassNameAndIdentifier classNameAndIdentifier,
        List<Token> parameterTokens) {
      super();
      this.classNameAndIdentifier = classNameAndIdentifier;
      this.parameterTokens = parameterTokens;
      parameterTypes = new Class<?>[parameterTokens.size() + 2];
      parameterTypes[0] = CalculationContext.class;
      parameterTypes[1] = String.class;
      int i = 2;
      for (Token token : parameterTokens) {
        Parser parser = token.parser;
        parameterTypes[i] = parser instanceof Expression ? float.class
            : parser instanceof BooleanExpression ? boolean.class
                : parser instanceof StringExpression ? String.class : null;
        i++;
      }
    }
  }
}
