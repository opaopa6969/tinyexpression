package org.unlaxer.tinyexpression.evaluator.javacode;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.Test;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.NormalCalculationContext;

import net.openhft.compiler.CompilerUtils;

public class SampleConstructedTest {

	@Test
	public void test() {
		SampleConstructed sampleConstructed = new SampleConstructed();
		
		CalculationContext calculateContext = new NormalCalculationContext();
		
		System.out.println(sampleConstructed.evaluate(calculateContext, null));
	}
	
	public static void main(String[] args) throws ClassNotFoundException, IOException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
		try(
		InputStream resourceAsStream = new FileInputStream("src/test/resources/SampleConstructed.java");
		BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(resourceAsStream));){
			
			String javaCode = bufferedReader.lines()
				.collect(Collectors.joining("\n"));
			
			Class<?> loadFromJava = CompilerUtils.CACHED_COMPILER.loadFromJava("SampleConstructed", javaCode);
			@SuppressWarnings("unchecked")
			Function<CalculationContext , Float> newInstance = (Function<CalculationContext, Float>) loadFromJava.getDeclaredConstructor().newInstance();

			CalculationContext calculateContext = new NormalCalculationContext();
			
			System.out.println(newInstance.apply(calculateContext));
			
			calculateContext.set("calculated_BlackIPAddressInThisSite", 1f);
			System.out.println(newInstance.apply(calculateContext));
		}
	}
}
