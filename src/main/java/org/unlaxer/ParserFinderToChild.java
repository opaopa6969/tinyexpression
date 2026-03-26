package org.unlaxer;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;

public interface ParserFinderToChild extends ParserHierarchy{

	public default Optional<Parser> findFirstToChild(Predicate<Parser> predicate) {
		return findToChild(predicate).findFirst();
	}

	public default Stream<Parser> findToChild(Predicate<Parser> predicate) {
		Parsers flatten = flatten(RecursiveMode.childrenOnly);
		return flatten.stream().filter(predicate);
	}

	public default Parsers flatten(){
		return flatten(RecursiveMode.containsRoot);
	}

	public default Parsers flatten(RecursiveMode recursiveMode){
		Parsers list = new Parsers();
		flatten(recursiveMode, list);
		return list;
	}

	public default void flatten(RecursiveMode recursiveMode, Parsers parsers){
		if(recursiveMode.isContainsRoot()){
			parsers.add(getThisParser());
		}
		for(Parser child : getChildren()){
			child.flatten(recursiveMode, parsers);
		}
	}
}
