package org.unlaxer.tinyexpression;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

import org.unlaxer.Name;
import org.unlaxer.Parsed;
import org.unlaxer.StringSource;
import org.unlaxer.Token;
import org.unlaxer.context.ParseContext;
import org.unlaxer.listener.TransactionListener;
import org.unlaxer.parser.ParseException;
import org.unlaxer.tinyexpression.evaluator.javacode.VariableTypeResolver;

public abstract class PreConstructedCalculator_ implements Calculator {
  
  public final String name;
  public final String formula;
  public final Token rootToken;
  final ParseContext parseContext;
  final Parsed parsed;
  Map<String,Object> objectByKey;

//	public PreConstructedCalculator(String formula , boolean randomize) {
//		this(formula , "_CalculatorClass"  + (randomize ? String.valueOf(Math.abs(new Random().nextLong())) :"" ));
//	}

  public PreConstructedCalculator_(String formula, String name , boolean createToken) {
    super();
    this.formula = formula;
    this.name = name;
    objectByKey = new HashMap<>();
    

    // tokenを作成するのはtokenを捜査して計算する実装もあったため。現在はその実装を無くしたのでparseする意味がない
    // 言語の拡張されてparse時間も無視できなくなったのでparseしないようにした。
    //　互換性のためTokenBaseCalculatorがあるが、ContextCalculatorにした方が良い（現在消してしまったので後で復活させる）
    if(createToken) {
      
      parseContext = new ParseContext(new StringSource(formula));
      transactionListeners().forEach(listenser->{
          parseContext.addTransactionListener(Name.of(listenser.getClass()), listenser);
        }
      );
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
  private PreConstructedCalculator_() {
    super();
    throw new IllegalArgumentException("Default constructor is not supported");
  }

  public UnaryOperator<Token> tokenReduer() {
    return UnaryOperator.identity();
  }

  @Override
  public Object apply(CalculationContext calculateContext) {
    return calculate(calculateContext);
  }

  public Object calculate(CalculationContext calculateContext) {
    return getCalculatorOperator().evaluate(calculateContext, rootToken);
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
  
	public abstract String className();
	public abstract String javaCode();
	public abstract String classNameWithHash();

	public abstract byte[] byteCode();
	public abstract String formulaHash();
	public abstract String byteCodeHash();
	
	public abstract Collection<TransactionListener> transactionListeners();

  @Override
  public CreatedFrom createdFrom() {
    return parsed == null ? CreatedFrom.byteCode : CreatedFrom.formula;
  }
}
