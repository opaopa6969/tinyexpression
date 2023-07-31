package org.unlaxer.tinyexpression;

import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

import org.unlaxer.Parsed;
import org.unlaxer.StringSource;
import org.unlaxer.Token;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.ParseException;
import org.unlaxer.tinyexpression.evaluator.javacode.VariableTypeResolver;

public abstract class PreConstructedCalculator<T> implements Calculator<T> {
  
  public final String name;
  public final String formula;
  public final Token rootToken;
  final ParseContext parseContext;
  final Parsed parsed;
  Map<String,Object> objectByKey;

//	public PreConstructedCalculator(String formula , boolean randomize) {
//		this(formula , "_CalculatorClass"  + (randomize ? String.valueOf(Math.abs(new Random().nextLong())) :"" ));
//	}

  public PreConstructedCalculator(String formula, String name , boolean createToken) {
    super();
    this.formula = formula;
    this.name = name;
    objectByKey = new HashMap<>();
    

    // tokenを作成するのはtokenを捜査して計算する実装もあったため。現在はその実装を無くしたのでparseする意味がない
    // 言語の拡張されてparse時間も無視できなくなったのでparseしないようにした。
    //　互換性のためTokenBaseCalculatorがあるが、ContextCalculatorにした方が良い（現在消してしまったので後で復活させる）
    if(createToken) {
      
      parseContext = new ParseContext(new StringSource(formula));
      try (parseContext) {
        parsed = getParser().parse(parseContext);
        if (false == parsed.isSucceeded()) {
          throw new ParseException("failed to parse:" + formula);
        }
        Token parsedToken = parsed.getRootToken(true);
        
//			String parsedTokenOutput = TokenPrinter.get(parsedToken);
//			System.out.println(parsedTokenOutput);
        
        parsedToken = VariableTypeResolver.resolveVariableType(parsedToken);
        
        rootToken = tokenReduer().apply(parsedToken);
//      String rootTokenOutput = TokenPrinter.get(parsedToken);
//      System.out.println(rootTokenOutput);
      } catch (Exception e) {
        e.printStackTrace();
        throw new ParseException("failed to parse:" + formula, e);
      }
    }else {
      parseContext = null;
      parsed = null;
      rootToken = null;
    }
  }

  @SuppressWarnings("unused")
  private PreConstructedCalculator() {
    super();
    throw new IllegalArgumentException();
  }

  public UnaryOperator<Token> tokenReduer() {
    return UnaryOperator.identity();
  }

  @Override
  public Float apply(CalculationContext calculateContext) {
    return calculate(calculateContext);
  }

  public float calculate(CalculationContext calculateContext) {
    return toFloat(getCalculatorOperator().evaluate(calculateContext, rootToken));
  }

  @Override
  public String toString() {
//		String tokenPresentation = TokenPrinter.get(rootToken);
    return formula;
  }

  @Override
  public String formula() {
    return formula;
  }

  @Override
  public void setObject(String key, Object object) {
    objectByKey.put(key, object);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <X> X getObject(String key, Class<X> objectClass) {
    return (X) objectByKey.get(key);
  }
  
  

}