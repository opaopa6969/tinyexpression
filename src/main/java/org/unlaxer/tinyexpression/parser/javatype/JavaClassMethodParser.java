package org.unlaxer.tinyexpression.parser;

import org.unlaxer.Name;
import org.unlaxer.Token;
import org.unlaxer.parser.ParseException;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.clang.IdentifierParser;
import org.unlaxer.parser.combinator.LazyChain;
import org.unlaxer.tinyexpression.evaluator.javacode.TinyExpressionTokens;
import org.unlaxer.util.annotation.TokenExtractor;

public class JavaClassMethodParser extends LazyChain implements ClassNameAndIdentifierExtractor{

	private static final long serialVersionUID = -7116791586435566841L;

	public JavaClassMethodParser() {
		super();
	}

	public JavaClassMethodParser(Name name) {
		super(name);
	}
	
	@Override
	public org.unlaxer.parser.Parsers getLazyParsers() {
	  return
      new Parsers(
//	        new Optional(
          Parser.get(JavaClassAndHashParser.class),
//	        ),
        Parser.get(IdentifierParser.class)
      );

	}
	
	@TokenExtractor
	static Token javaClassAndHash(Token thisParserParsed) {
	  return thisParserParsed.getChildWithParser(JavaClassAndHashParser.class);
	}
	
  @TokenExtractor
  static Token identifier(Token thisParserParsed) {
    return thisParserParsed.getChildWithParser(IdentifierParser.class);
  }

   @TokenExtractor
   static Token javaClassName(Token javaClassAndHashToken) {
     return JavaClassAndHashParser.getJavaClass(javaClassAndHashToken);
   }
	
	@Override
	public ClassNameAndIdentifier extractClassNameAndIdentifier(Token token , TinyExpressionTokens tinyExpressionTokens) {
		
		if(false == token.parser instanceof JavaClassMethodParser) {
			throw new IllegalArgumentException(
			    "Expected JavaClassMethodParser token but got: "
			        + token.parser.getClass().getName()
			        + " (tokenPath=" + token.getPath() + ")");
		}
		
		Token javaClassAndHash = javaClassAndHash(token);
		String identifier = identifier(token).getToken().orElse("");
		String javaClass = javaClassName(javaClassAndHash).getToken().orElse("");
		
		return new ClassNameAndIdentifier(javaClass, identifier);
	}
	
	public static class JavaMethodParser extends IdentifierParser implements ClassNameAndIdentifierExtractor{

    public JavaMethodParser() {
      super();
    }

    public JavaMethodParser(Name name) {
      super(name);
    }

    @Override
    public ClassNameAndIdentifier extractClassNameAndIdentifier(Token token,
        TinyExpressionTokens tinyExpressionTokens) {
  
      try {
        String methodName = token.tokenString.get();
        String resolveJavaClass = tinyExpressionTokens.resolveJavaClass(methodName);
        String[] split = resolveJavaClass.split("#");
        
        return new ClassNameAndIdentifier(split[0],split[1]);
      }catch (Exception e) {
        throw new ParseException("faild to extract class method from " + token ,e);
      }
    }
	  
	}
}
