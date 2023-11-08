package org.unlaxer.tinyexpression.evaluator.javacode;

import java.util.stream.Stream;

import org.unlaxer.util.SimpleBuilder;


public class SimpleJavaCodeBuilder {

	int functionNumber=0;
	int supplierNumber=0;
	int index;
	SimpleBuilder builder;
	SimpleBuilder functionBuilder = new SimpleBuilder();
	SimpleBuilder calcBuilder = new SimpleBuilder();
	SimpleBuilder fieldBuilder = new SimpleBuilder();
	SimpleBuilder mainBuilder = new SimpleBuilder();
	
	Kind kind = Kind.Main;
	
	public enum Kind{
		Main,Function,Calculation,Field
	}
	
	public SimpleJavaCodeBuilder() {
		this(0);
	}
	
	public SimpleJavaCodeBuilder(int index) {
		super();
		this.index = index;
		setKind(Kind.Main);
	}
	
	public SimpleJavaCodeBuilder setKind(Kind kind) {
		this.kind = kind;
		builder = getBuilder(kind);
		return this;
	}

	public SimpleBuilder getBuilder(Kind kind) {
	  return kind == Kind.Main ?
	      mainBuilder : kind == Kind.Function ?
	          functionBuilder : kind == Kind.Calculation ?
	              calcBuilder : fieldBuilder;
	}

	
	public SimpleJavaCodeBuilder incTab() {
		++index;
		return this;
	}
	public SimpleJavaCodeBuilder decTab() {
		--index;
		return this;
		 
	}
	
	public String currentFunctionName() {
		return "function"+functionNumber;
	}
	
	public SimpleJavaCodeBuilder appendCurrentFunctionName() {
		append(currentFunctionName());
		return this;
	}
	
	public SimpleJavaCodeBuilder incrementFunction() {
		functionNumber++;
		return this;
	}
	
	public SimpleJavaCodeBuilder append(CharSequence word) {
		builder.append(word);
		return this;
	}
	
	public SimpleJavaCodeBuilder withTab(CharSequence word) {
		tab();
		builder.append(word);
		return this;
	}

	
	public SimpleJavaCodeBuilder line(CharSequence word) {
		tab();
		append(word);
		n();
		return this;
	}
	public SimpleJavaCodeBuilder lines(String lines) {
		String[] split = lines.split("\n");
		for (String line : split) {
			tab();
			append(line);
			n();
		}
		return this;
	}
	
	public SimpleJavaCodeBuilder lines(Stream<String> linesStream) {
		linesStream.forEach(this::line);
		return this;
	}


	static byte tabBytes = "\t".getBytes()[0];
	private SimpleJavaCodeBuilder tab() {
		byte[] tabs = new byte[index];
		for(int i = 0 ; i < index ; i++) {
			tabs[i] = tabBytes;
		}
		builder.append(new String(tabs));
		return this;
	}
	
	
	public SimpleJavaCodeBuilder w(String word) {
		word = word == null  ? "" :  word;
		append("\"" + word.replaceAll("\"","\\\"") +  "\"");
		return this;
	}
	
	public SimpleJavaCodeBuilder p(String word) {
		word = word == null  ? "" :  word;
		append("(" + word +  ")");
		return this;
	}
	
//	public SimpleBuilder include(Path path) {
//		try(InputStream input = Files.newInputStream(path)){
//			
//			String text = IOUtils.toString(input, StandardCharsets.UTF_8);
//			append(text);
//			return this;
//		} catch (IOException e) {
//			throw new UncheckedIOException(e);
//		}
//		
//	}
	
	String build() {
		return mainBuilder.toString() + 
		fieldBuilder.toString() + 
		functionBuilder.toString() +
		calcBuilder.toString() + 
		"\n}";
	}
	
	

	@Override
	public String toString() {
		return build() ;
	}


	public SimpleJavaCodeBuilder n() {
	  builder.n();
		return this;
	}
}