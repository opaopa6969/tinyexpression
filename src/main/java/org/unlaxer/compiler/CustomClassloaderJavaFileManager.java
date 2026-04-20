package org.unlaxer.compiler;

import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;

/*
 * modified and repackage below sources for java9 and j2ee container
 * https://atamur.blogspot.com/2009/10/using-built-in-javacompiler-with-custom.html
 * 
 * @author atamur
 * @author opa
 * @since 15-Oct-2009
 */
public class CustomClassloaderJavaFileManager implements JavaFileManager {
	private final ClassLoader classLoader;
	private final StandardJavaFileManager standardFileManager;
	private final PackageInternalsFinder finder;
	private final JavaFileManagerContext javaFileManagerContext;

	public CustomClassloaderJavaFileManager(ClassLoader classLoader, StandardJavaFileManager standardFileManager,
			JavaFileManagerContext javaFileManagerContext) {
		this.classLoader = classLoader;
		this.standardFileManager = standardFileManager;
		this.javaFileManagerContext = javaFileManagerContext;
		finder = new PackageInternalsFinder(classLoader,javaFileManagerContext.jarURLStringFromURL);
	}
	
	@Override
	public ClassLoader getClassLoader(Location location) {
		return classLoader;
	}

	@Override
	public String inferBinaryName(Location location, JavaFileObject file) {
		if (file instanceof CustomJavaFileObject) {
			return ((CustomJavaFileObject) file).binaryName();
		}
		// standard expects PathFileObject
		return standardFileManager.inferBinaryName(location, file);
	}

	@Override
	public boolean isSameFile(FileObject a, FileObject b) {
		return standardFileManager.isSameFile(a, b);
	}

	@Override
	public boolean handleOption(String current, Iterator<String> remaining) {
		return standardFileManager.handleOption(current, remaining);
	}

	@Override
	public boolean hasLocation(Location location) {
		return location == StandardLocation.CLASS_PATH || location == StandardLocation.PLATFORM_CLASS_PATH
				|| standardFileManager.hasLocation(location);
	}

	@Override
	public JavaFileObject getJavaFileForInput(Location location, String className, JavaFileObject.Kind kind)
			throws IOException {
		return standardFileManager.getJavaFileForInput(location, className, kind);
	}

	@Override
	public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind,
			FileObject sibling) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public FileObject getFileForInput(Location location, String packageName, String relativeName) throws IOException {
		return standardFileManager.getFileForInput(location, packageName, relativeName);
	}

	@Override
	public FileObject getFileForOutput(Location location, String packageName, String relativeName, FileObject sibling)
			throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public void flush() throws IOException {
	}

	@Override
	public void close() throws IOException {
	}

	@Override
	public Iterable<JavaFileObject> list(Location location, String packageName, Set<JavaFileObject.Kind> kinds,
			boolean recurse) throws IOException {
	  
//    System.out.println(location.getName());

		if (javaFileManagerContext.matchForStandardFileManager.test(location)) {

			Iterable<JavaFileObject> list = standardFileManager.list(location, packageName, kinds, recurse);
			
			return list;

		} else if (location == StandardLocation.CLASS_PATH && kinds.contains(JavaFileObject.Kind.CLASS)) {
			Map<String, JavaFileObject> merged = new LinkedHashMap<>();
			Iterable<JavaFileObject> standardList = standardFileManager.list(location, packageName, kinds, recurse);
			standardList.forEach(file -> merged.put(file.getName(), file));
			if (false == packageName.startsWith("java.")) {
//			  System.out.println("pacakge:" + packageName + "→" + location);
				List<JavaFileObject> list = finder.find(packageName);
				list.forEach(file -> merged.putIfAbsent(file.getName(), file));
			}
			return merged.values();
		}
		return Collections.emptyList();

	}

	@Override
	public int isSupportedOption(String option) {
		return -1;
	}

	public Location getLocationForModule(Location location, String moduleName) throws IOException {
		return standardFileManager.getLocationForModule(location, moduleName);
	}

	public Location getLocationForModule(Location location, JavaFileObject fo) throws IOException {
		return standardFileManager.getLocationForModule(location, fo);
	}

	public Iterable<Set<Location>> listLocationsForModules(Location location) throws IOException {
		return standardFileManager.listLocationsForModules(location);
	}

	public String inferModuleName(Location location) throws IOException {
		return standardFileManager.inferModuleName(location);
	}
	
	static String toString(List<JavaFileObject> javaFiles) {
	  String collect = javaFiles.stream()
	    .map(JavaFileObject::getName)
	    .collect(Collectors.joining("\n"));
	  return collect;
	}
}
