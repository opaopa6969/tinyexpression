package org.unlaxer.tinyexpression.evaluator.javacode;

import java.util.Optional;
import java.util.function.Function;

import org.unlaxer.tinyexpression.evaluator.javacode.SimpleJavaCodeBuilder.Kind;
import org.unlaxer.util.Either;
import org.unlaxer.util.SimpleBuilder;

public class ExpressionOrLiteral extends Either<String, String> {

  SimpleJavaCodeBuilder returning;
  
	private ExpressionOrLiteral(String raw, String mustWrap) {
		super(raw, mustWrap);
	}

	public static ExpressionOrLiteral literalOf(String literal) {
		if (literal == null) {
			throw new IllegalArgumentException("must be not null");
		}
		return new ExpressionOrLiteral(null, literal);
	}

	public static ExpressionOrLiteral expressionOf(String expresion) {
		if (expresion == null) {
			throw new IllegalArgumentException("must be not null");
		}
		return new ExpressionOrLiteral(expresion, null);
	}

	public String toString() {
		return apply(Function.identity(), word -> "\"" + word + "\"");
//		return apply(Function.identity(),Function.identity());
	}
	
	public ExpressionOrLiteral setReturning(SimpleJavaCodeBuilder returning) {
	  this.returning = returning;
	  return this;
	}
	
	public Optional<SimpleJavaCodeBuilder> getReturning(){
	  return Optional.ofNullable(returning);
	}
	
	public void populateTo(SimpleJavaCodeBuilder destinationBuilder,Kind... kinds ) {
	  if(returning == null) {
	    return;
	  }
	  
	  for (Kind kind : kinds) {
      SimpleBuilder builder = returning.getBuilder(kind);
      if(builder.length()>0) {
        destinationBuilder.getBuilder(kind).append(builder.toString());
      }
    }
	}
}