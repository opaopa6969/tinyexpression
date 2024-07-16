package org.unlaxer.parser.elementary;

import java.util.Optional;
import java.util.function.Supplier;

import org.unlaxer.Name;
import org.unlaxer.Parsed;
import org.unlaxer.Token;
import org.unlaxer.TokenKind;
import org.unlaxer.TokenPredicators;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Choice;
import org.unlaxer.parser.combinator.LazyChain;
import org.unlaxer.parser.combinator.LazyZeroOrMore;
import org.unlaxer.parser.combinator.NotPropagatableSource;
import org.unlaxer.parser.combinator.ParserWrapper;
import org.unlaxer.parser.combinator.ZeroOrMore;
import org.unlaxer.util.annotation.TokenExtractor;

/*
 * sample->
 * 
 * startQuoted:   ```java:org.unlaxer.Dummy
 *    contents:   public class Dummy{
 *    contents:     public static void main(String[] args){System.out.println("hello");}
 *    contents:   }
 *   enfQuoted:   ```
 */
public class StartAndEndQuotedParser extends LazyChain {
	
	public enum Parts{
		leftQuote,
		contents,
		rightQuote,
		;
		Name nameInstance;
		
		private Parts(){
			nameInstance = Name.of(this);
		}
		public Name get(){
			return nameInstance;
		}
	}
	
  Parser endQuoteParser;
  Parser startQuoteParser;
  Parser quoteParser;
	
	public StartAndEndQuotedParser(Parser startQuoteParser , Parser endQuoteParser , Parser quoteParser) {
		super();
		this.endQuoteParser = endQuoteParser;
		this.startQuoteParser = startQuoteParser;
		this.quoteParser = quoteParser;
	}

  public static final Name contents = Name.of(StartAndEndQuotedParser.class, Parts.contents.get());
  public static final Name leftQuote = Name.of(StartAndEndQuotedParser.class, Parts.leftQuote.get());
	
	@Override
  public Parsed parse(ParseContext parseContext, TokenKind tokenKind, boolean invertMatch) {
    return super.parse(parseContext, tokenKind, invertMatch);
  }

	@Override
	public Parsers getLazyParsers() {
		return Parsers.of(
			new ParserWrapper(
			  leftQuote,
				startQuoteParser
			),
			new ZeroOrMore(contents,
				new Choice(
					new EscapeInQuotedParser(),
					new NotPropagatableSource(quoteParser)
				)
			),
			new ParserWrapper(
				Name.of(StartAndEndQuotedParser.class, Parts.rightQuote.get()),
				endQuoteParser
			)
		);
	}
	
	public static class SchemeAndIdentifier{
	  public final String scheme;
	  public final String idenitifier;
    public SchemeAndIdentifier(String scheme, String idenitifier) {
      super();
      this.scheme = scheme;
      this.idenitifier = idenitifier;
    }
	}
  
  @TokenExtractor
  public static String extractContents(Token thisParserParsed) {
      String string = thisParserParsed.flatten().stream()
        .filter(token->token.parser.getName().equals(contents))
        .findFirst()
        .get().getToken().get();
      return string;
  }
	
	public static class QuotedContentsParser extends LazyZeroOrMore{


		public QuotedContentsParser(Parser quoteParser) {
			super(QuotedParser.contentsName);
			this.quoteParser = quoteParser;
		}
		
		@Override
    public Parsed parse(ParseContext parseContext, TokenKind tokenKind, boolean invertMatch) {
      return super.parse(parseContext, tokenKind, invertMatch);
    }
		
		Parser quoteParser;

		@Override
		public Supplier<Parser> getLazyParser() {
			return ()-> new Choice(
				new EscapeInQuotedParser(),
				new NotPropagatableSource(quoteParser)
			);
		}

		@Override
		public Optional<Parser> getLazyTerminatorParser() {
			return Optional.empty();
		}
	}
	
	public static String contents(Token thisParsersToken) {
//		Name target = Parts.contents.get();
		Optional<Token> collect = thisParsersToken.flatten().stream()
//			.peek(token->System.out.println(TokenPrinter.get(token)))
			.filter(token->token.parser.getName().equals(contents))
			.findFirst();
//		String collect = thisParsersToken.children.stream()
//			.filter(token->token.getParser().findFirstToParent(parser->parser.getName() == target).isPresent())
//			.map(Token::getToken)
//			.filter(Optional::isPresent)
//			.map(Optional::get)
//			.collect(Collectors.joining());
		
		String contents = collect
				.flatMap(Token::getToken)
				//FIXME! this is work around for BUG...
				.orElseGet(
					()->thisParsersToken.tokenString
						.map(quoted->quoted.substring(1, quoted.length()-1))
						.orElse("")
				);
		return contents;
		
	}
}