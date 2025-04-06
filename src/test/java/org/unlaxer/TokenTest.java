package org.unlaxer;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.function.Function;

import org.junit.Ignore;
import org.junit.Test;
import org.unlaxer.Token.ChildrenKind;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChain;
import org.unlaxer.parser.combinator.ZeroOrMore;
import org.unlaxer.parser.posix.AlphabetParser;
import org.unlaxer.tinyexpression.parser.numbertype.NumberVariableParser;

public class TokenTest {

  @Test
  @Ignore
  public void test() {

//    // 匿名クラスを使って Function<String, Integer> を定義
//    Function<TypedToken<NumberVariableParser>, Integer> func = new Function<TypedToken<NumberVariableParser>, Integer>() {
//        @Override
//        public Integer apply(TypedToken<NumberVariableParser> s) {
//            return 1;
//        }
//    };
//
//    // Function インターフェースの型情報を取得
//    Type[] genericInterfaces = func.getClass().getGenericInterfaces();
//    for (Type genericInterface : genericInterfaces) {
//        if (genericInterface instanceof ParameterizedType) {
//            ParameterizedType pt = (ParameterizedType) genericInterface;
//            System.out.println("Raw Type: " + pt.getRawType());
//            for (Type typeArg : pt.getActualTypeArguments()) {
//                System.out.println("Type Argument: " + typeArg);
//            }
//        }
//    }

    Function<TypedToken<NumberVariableParser>, String> function = this::out;

    Type[] genericInterfaces = function.getClass().getGenericInterfaces();
    for (Type genericInterface : genericInterfaces) {
      if (genericInterface instanceof ParameterizedType) {
        ParameterizedType pt = (ParameterizedType) genericInterface;
        System.out.println("Raw Type: " + pt.getRawType());
        for (Type typeArg : pt.getActualTypeArguments()) {
          System.out.println("Type Argument: " + typeArg);
        }
      }
    }
  }

  String out(TypedToken<NumberVariableParser> p) {
    return "";
  }

  public static void main(String[] args) {

    AParser aParser = new AParser();
    StringSource stringSource = new StringSource("ABCDEFGH");
    ParseContext parseContext = new ParseContext(stringSource);
    Parsed parsed = aParser.parse(parseContext);
    Token rootToken = parsed.getRootToken();
    String string = TokenPrinter.get(rootToken);
    System.out.println(string);

    List<Token> flatten = rootToken.flattenBreadth(ChildrenKind.astNodes, 4);
    for (Token token : flatten) {
      System.out.println(token.getParser() + "->" + token.getToken().orElse("-"));

    }

    System.out.println("reduce basic combinator");

    Token reduceBasicCombinator = Token.reduceBasicCombinator(rootToken);
    System.out.println(TokenPrinter.get(reduceBasicCombinator));

  }

  public static class AParser extends LazyChain {

    @Override
    public Parsers getLazyParsers() {
      return new Parsers(
          new AlphabetParser(),
//          new AlphabetParser(),
          new ZeroOrMore(this));
    }

  }
}
