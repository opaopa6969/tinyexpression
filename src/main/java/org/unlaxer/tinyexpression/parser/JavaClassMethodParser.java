package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.Name;
import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.clang.IdentifierParser;
import org.unlaxer.parser.combinator.LazyChain;
import org.unlaxer.util.annotation.TokenExtractor;

public class JavaClassMethodParser extends LazyChain{

	private static final long serialVersionUID = -7116791586435566841L;

	public JavaClassMethodParser() {
		super();
	}

	public JavaClassMethodParser(Name name) {
		super(name);
	}
	
	@Override
	public List<Parser> getLazyParsers() {
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
	
	public ClassNameAndIdentifier extract(Token token) {
		
		if(false == token.parser instanceof JavaClassMethodParser) {
			throw new IllegalArgumentException();
		}
		
		Token javaClassAndHash = javaClassAndHash(token);
		String identifier = identifier(token).getToken().orElse("");
		String javaClass = javaClassName(javaClassAndHash).getToken().orElse("");
		
		return new ClassNameAndIdentifier(javaClass, identifier);
	}
	
	public static class ClassNameAndIdentifier{
		
		final String className;
		final String identifier;
		public ClassNameAndIdentifier(String className, String identifier) {
			super();
			this.className = className;
			this.identifier = identifier;
		}
		
		public String getClassName() {
			return className;
		}

		public String getIdentifier() {
			return identifier;
		}
	}
}