package org.unlaxer.tinyexpression.rendering;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.combinator.NoneChildParser;
import org.unlaxer.tinyexpression.parser.NumberExpressionParser;


public class RenderingTest {

	
	public static void main(String[] args) {
		
		Parser expressionParser = Parser.get(NumberExpressionParser.class);
//		expressionParser.initialize();
		
		Set<Class<? extends Parser>> parsers = new LinkedHashSet<>();
		
		build(expressionParser , parsers);
		
		parsers.stream().forEach(clazz->{
			System.out.println(clazz);
		});
			
	}
	
	static void build(Parser parser , Set<Class<? extends Parser>> parsers ) {
		
		List<Parser> children = parser instanceof NoneChildParser ?
				((NoneChildParser) parser).getLazyParser().getChildren() :  
				parser.getChildren();
		
		if(children.isEmpty()) {
			parsers.add(parser.getClass());
			return;
		}
		for (Parser childParser : children) {
			Class<? extends Parser> childParserClazz = childParser.getClass();
			if(parsers.contains(childParserClazz)) {
				continue;
			}
			parsers.add(childParserClazz);
			build(childParser, parsers);
		}
		return;
	}
}
