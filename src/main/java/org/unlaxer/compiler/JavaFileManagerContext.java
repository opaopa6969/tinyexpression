package org.unlaxer.compiler;

import java.net.URL;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.tools.StandardLocation;
import javax.tools.JavaFileManager.Location;

public class JavaFileManagerContext {
	public final Predicate<Location> matchForStandardFileManager;
	public final Function<URL, String> jarURLStringFromURL;

	public JavaFileManagerContext(Predicate<Location> matchForStandardFileManager,
			Function<URL, String> jarURLStringFromURL) {
		super();
		this.matchForStandardFileManager = matchForStandardFileManager;
		this.jarURLStringFromURL = jarURLStringFromURL;
	}

	public JavaFileManagerContext(Predicate<Location> matchForStandardFileManager) {
		super();
		this.matchForStandardFileManager = matchForStandardFileManager;
		this.jarURLStringFromURL = defaultJarURLStringFromURL;
	}

	public JavaFileManagerContext(Function<URL, String> jarURLStringFromURL) {
		super();
		this.matchForStandardFileManager = defaultMatchForStandardFileManager;
		this.jarURLStringFromURL = jarURLStringFromURL;
	}

	public JavaFileManagerContext() {
		super();
		this.matchForStandardFileManager = defaultMatchForStandardFileManager;
		this.jarURLStringFromURL = defaultJarURLStringFromURL;
	}

	private static final Predicate<Location> defaultMatchForStandardFileManager = //
			location -> //
			location == StandardLocation.PLATFORM_CLASS_PATH || //
//					location.getName().startsWith(StandardLocation.SYSTEM_MODULES.getName());//
					location.getName().equals("SYSTEM_MODULES[java.base]");

	private static final Function<URL, String> defaultJarURLStringFromURL = //
			packageFolderURL -> {//
				String externalForm = packageFolderURL.toExternalForm();//
				String jarUri = externalForm.substring(0, externalForm.lastIndexOf('!'));//
				// String jarUri = packageFolderURL.toExternalForm().split("!")[0];//

				// open liberty provides URL that has scheme 'wsjar'.
				// if openConnection the 'wsjar' URL, we get WSJarURLStreamHandler$WSJarURLConnectionImpl
				jarUri = jarUri.startsWith("wsjar") ? "jar" + jarUri.substring(5) : jarUri;//

				return jarUri;//
			};//

}