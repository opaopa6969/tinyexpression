package org.unlaxer.tinyexpression.evaluator.javacode;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.function.UnaryOperator;

import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.unlaxer.Name;
import org.unlaxer.Token;
import org.unlaxer.compiler.CustomClassloaderJavaFileManager;
import org.unlaxer.compiler.JavaFileManagerContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.PreConstructedCalculator;
import org.unlaxer.tinyexpression.TokenBaseCalculator;
import org.unlaxer.tinyexpression.TokenBaseOperator;
import org.unlaxer.tinyexpression.parser.FormulaParser;
import org.unlaxer.util.digest.MD5;

import sun.misc.Unsafe;


public class UnsafeJavaCodeCalculatorV2 extends PreConstructedCalculator<Float>
		implements JavaClassCreator, TokenBaseCalculator {

	public final String className;
	public final String javaCode;
	public final String classNameWithHash;

	public final byte[] byteCode;
	final String formulaHash;
	final String byteCodeHash;

	TokenBaseOperator<CalculationContext, Float> operator;
	
	// constructors for source code

	public UnsafeJavaCodeCalculatorV2(Name name, String formula) throws CompileError{
		this(name, formula, (Path) null);
	}

	public UnsafeJavaCodeCalculatorV2(Name name, String formula, Path outputRootDirectory) throws CompileError{
		this(name, formula, Thread.currentThread().getContextClassLoader(), outputRootDirectory, true, new JavaFileManagerContext());
	}

	public UnsafeJavaCodeCalculatorV2(Name name, String formula, ClassLoader classLoader) throws CompileError{
		this(name, formula, classLoader, null, true ,  new JavaFileManagerContext());
	}

	public UnsafeJavaCodeCalculatorV2(Name name, String formula, ClassLoader classLoader, Path outputRootDirectory,
			boolean randomize , JavaFileManagerContext javaFileManagerContext) throws CompileError{
		this(formula, name.getName() + "_CalculatorClass" + (randomize ? Math.abs(new Random().nextLong()) : ""),
				classLoader, outputRootDirectory,javaFileManagerContext);
	}

	public UnsafeJavaCodeCalculatorV2(String formula, String className, ClassLoader classLoader) throws CompileError{
		this(formula, className, classLoader, null , new JavaFileManagerContext());
	}

	static JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
	

	@SuppressWarnings("unchecked")
	public UnsafeJavaCodeCalculatorV2(String formula, String className, ClassLoader classLoader, @Nullable Path outputRootDirectory , 
			JavaFileManagerContext javaFileManagerContext) throws CompileError{
		super(formula, className, true);
		
		StringWriter output = new StringWriter();

		try {

			this.className = className;
			formulaHash = MD5.toHex(formula);

			TinyExpressionTokens tinyExpressionTokens = new TinyExpressionTokens(rootToken);

			classNameWithHash = className + "_" + formulaHash;
			javaCode = createJavaClass(classNameWithHash, tinyExpressionTokens);

			JavaFileObject javaFileObject = new SimpleJavaFileObject(
					URI.create("string:///" + classNameWithHash + ".java"), JavaFileObject.Kind.SOURCE) {
				@Override
				public CharSequence getCharContent(boolean ignoreEncodingErrors) {
					return javaCode;
				}
			};

			if (outputRootDirectory != null) {
				try (BufferedWriter newBufferedWriter = Files
						.newBufferedWriter(outputRootDirectory.resolve(classNameWithHash + ".java"))) {
					newBufferedWriter.write(javaCode);
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			}


			try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, Locale.getDefault(),
					StandardCharsets.UTF_8);
					
				CustomClassloaderJavaFileManager customClassloaderJavaFileManager = 
						new CustomClassloaderJavaFileManager(classLoader, fileManager);
				
				MemoryJavaFileManager memoryFileManager = new MemoryJavaFileManager(customClassloaderJavaFileManager);) {

				JavaCompiler.CompilationTask task = compiler.getTask(new PrintWriter(output), memoryFileManager, null,
						null, null, Arrays.asList(javaFileObject));

				boolean success = task.call();
//				System.out.println("Compilation " + (success ? "succeeded" : "failed"));
//				System.out.println(output.toString());

				if (success) {
					
					MemoryClassLoader memoryClassLoader = new MemoryClassLoader(memoryFileManager.getClassBytes(),classLoader);

					Class<?> clazz = memoryClassLoader.loadClass(classNameWithHash);

					operator = (TokenBaseOperator<CalculationContext, Float>) clazz.getDeclaredConstructor().newInstance();

					byteCode = memoryClassLoader.getBytes(classNameWithHash);
					byteCodeHash = MD5.toHex(byteCode);
				}else {
					throw new CompileError(output.toString());
				}
			}
		} catch (Throwable e) {
			throw new RuntimeException(output.toString(),e);
		}
	}
	
	@SuppressWarnings("serial")
	public static class CompileError extends RuntimeException{

		public CompileError() {
			super();
		}

		public CompileError(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
			super(message, cause, enableSuppression, writableStackTrace);
		}

		public CompileError(String message, Throwable cause) {
			super(message, cause);
		}

		public CompileError(String message) {
			super(message);
		}
		public CompileError(Throwable cause) {
			super(cause);
		}
	}

	// constructor for byte codes;
	
	@SuppressWarnings("unchecked")
	public UnsafeJavaCodeCalculatorV2(String formula, String javaCode, String className, byte[] byteCode, String byteCodeHash,
			ClassLoader classLoader) {
		super(formula, className, false);
		this.className = className;
		this.classNameWithHash = null;
		this.javaCode = javaCode;
		this.byteCode = byteCode;
		this.byteCodeHash = byteCodeHash;

		formulaHash = MD5.toHex(formula);

		Class<TokenBaseOperator<CalculationContext, Float>> calculatorClass = null;

		try {
			try {
				calculatorClass = (Class<TokenBaseOperator<CalculationContext, Float>>) classLoader
						.loadClass(className);

			} catch (ClassNotFoundException e) {

				try {
					calculatorClass = defineClass(classLoader, className, byteCode);
				} catch (Throwable e2) {
					e2.printStackTrace();
					try {
						calculatorClass = defineClass(null, className, byteCode);
					} catch (Throwable e3) {
						e3.printStackTrace();
						calculatorClass = defineClass(null, null, byteCode);
					}
				}
			}
			operator = (TokenBaseOperator<CalculationContext, Float>) calculatorClass.getDeclaredConstructor()
					.newInstance();

		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
				| NoSuchMethodException | SecurityException | NoClassDefFoundError e) {

			throw new RuntimeException(e);
		}
	}

    private static final Method DEFINE_CLASS_METHOD;
    
    static {
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            Unsafe u = (Unsafe) theUnsafe.get(null);
            DEFINE_CLASS_METHOD = ClassLoader.class.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class);
            try {
                Field f = AccessibleObject.class.getDeclaredField("override");
                long offset = u.objectFieldOffset(f);
                u.putBoolean(DEFINE_CLASS_METHOD, offset, true);
            } catch (NoSuchFieldException e) {
                DEFINE_CLASS_METHOD.setAccessible(true);
            }
        } catch (NoSuchMethodException | IllegalAccessException | NoSuchFieldException e) {
            throw new AssertionError(e);
        }
    }
    
    public static void defineClass(@NotNull String className, @NotNull byte[] bytes) {
        defineClass(Thread.currentThread().getContextClassLoader(), className, bytes);
    }

    @SuppressWarnings("rawtypes")
	public static Class defineClass(@Nullable ClassLoader classLoader, @NotNull String className, @NotNull byte[] bytes) {
        try {
            return (Class) DEFINE_CLASS_METHOD.invoke(classLoader, className, bytes, 0, bytes.length);
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        } catch (InvocationTargetException e) {
            //noinspection ThrowInsideCatchBlockWhichIgnoresCaughtException
            throw new AssertionError(e.getCause());
        }
    }

	
	public UnsafeJavaCodeCalculatorV2(String formula, String javaCode, String className, byte[] byteCode, String byteCodeHash,
			Class<TokenBaseOperator<CalculationContext, Float>> calculatorClass, ClassLoader classLoader) {
		super(formula, className, false);
		this.className = className;
		this.javaCode = javaCode;
		this.byteCode = byteCode;
		this.byteCodeHash = byteCodeHash;
		this.classNameWithHash = "";

		formulaHash = MD5.toHex(formula);

		try {
			operator = (TokenBaseOperator<CalculationContext, Float>) calculatorClass.getDeclaredConstructor()
					.newInstance();
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
				| NoSuchMethodException | SecurityException e) {
			throw new RuntimeException(e);
		}

	}

	static boolean loaded(ClassLoader classLoader, String className) {
		try {
			classLoader.loadClass(className);
			return true;
		} catch (ClassNotFoundException e) {
			return false;
		}
	}

	@Override
	public Parser getParser() {
		return Parser.get(FormulaParser.class);
	}

	@Override
	public TokenBaseOperator<CalculationContext, Float> getCalculatorOperator() {
		return operator;
	}

	@Override
	public BigDecimal toBigDecimal(Float value) {
		return new BigDecimal(value);
	}

	@Override
	public float toFloat(Float value) {
		return value;
	}

	@Override
	public UnaryOperator<Token> tokenReduer() {
		return OperatorOperandTreeCreator.SINGLETON;
	}

	@Override
	public String javaCode() {
		return javaCode;
	}

	@Override
	public byte[] byteCode() {
		return byteCode;
	}

	@Override
	public Float evaluate(CalculationContext context, Token token) {
		return getCalculatorOperator().evaluate(context, token);
	}

	@Override
	public String formulaHash() {
		return formulaHash;
	}

	@Override
	public String byteCodeHash() {
		return byteCodeHash;
	}

	class MemoryJavaFileManager extends ForwardingJavaFileManager<JavaFileManager> {
		private final Map<String, ByteArrayJavaFileObject> classFiles = new HashMap<>();

		protected MemoryJavaFileManager(JavaFileManager fileManager) {
			super(fileManager);
		}

		@Override
		public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind,
				FileObject sibling) throws IOException {
			ByteArrayJavaFileObject fileObject = new ByteArrayJavaFileObject(className, kind);
			classFiles.put(className, fileObject);
			return fileObject;
		}

		public Map<String, byte[]> getClassBytes() {
			Map<String, byte[]> classBytes = new HashMap<>();
			for (Map.Entry<String, ByteArrayJavaFileObject> entry : classFiles.entrySet()) {
				classBytes.put(entry.getKey(), entry.getValue().getBytes());
			}
			return classBytes;
		}
	}

	class ByteArrayJavaFileObject extends SimpleJavaFileObject {
		private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

		protected ByteArrayJavaFileObject(String name, Kind kind) {
			super(URI.create("string:///" + name.replace('.', '/') + kind.extension), kind);
		}

		@Override
		public OutputStream openOutputStream() throws IOException {
			return outputStream;
		}

		public byte[] getBytes() {
			return outputStream.toByteArray();
		}
	}

	class MemoryClassLoader extends ClassLoader {
		private final Map<String, byte[]> classBytes;
		
		public MemoryClassLoader(Map<String, byte[]> classBytes , ClassLoader parent) {
			super(parent);
			this.classBytes = classBytes;
		}
		
		public byte[] getBytes(String name) {
			return classBytes.get(name);
		}

		protected Class<?> findClass(String name) throws ClassNotFoundException {
			byte[] bytes = classBytes.get(name);
			if (bytes == null) {
				throw new ClassNotFoundException(name);
			}
			return defineClass(name, bytes, 0, bytes.length);
		}
	}

}
