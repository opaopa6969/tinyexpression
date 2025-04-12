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
    Parsers parsers = new Parsers();
		flatten(RecursiveMode.childrenOnly , parsers);
		return parsers.stream().filter(predicate);
	}
	
	public default Parsers flatten(){
	  Parsers parsers = new Parsers();
		flatten(RecursiveMode.containsRoot, parsers);
		return parsers;
	}
	
	public default void flatten(RecursiveMode recursiveMode , Parsers parsers){
	  Parser thisParser = getThisParser();
		if(recursiveMode.isContainsRoot()){
      parsers.add(thisParser);
		}
		recursiveMode = RecursiveMode.childrenOnly;

		for(Parser child :getChildren()){
		  if(false == parsers.contains(child)) {
		    parsers.add(child);
		    child.flatten(recursiveMode , parsers);
		  }else {
        parsers.add(child);
		  }
		}
	}
}