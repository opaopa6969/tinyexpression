package org.unlaxer.util;

public class StringStartsWith {
	
	public static boolean match(String base , String...inClause) {
		for (String in : inClause) {
			if(base.startsWith(in)) {
				return true;
			}
		}
		return false;
	}
	
}
