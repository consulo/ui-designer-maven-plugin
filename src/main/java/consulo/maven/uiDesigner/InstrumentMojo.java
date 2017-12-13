package consulo.maven.uiDesigner;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.StringTokenizer;

import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;
import org.jetbrains.org.objectweb.asm.ClassWriter;
import com.intellij.compiler.instrumentation.InstrumentationClassFinder;
import com.intellij.compiler.instrumentation.InstrumenterClassWriter;
import com.intellij.uiDesigner.compiler.AsmCodeGenerator;
import com.intellij.uiDesigner.compiler.FormErrorInfo;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.lw.CompiledClassPropertiesProvider;
import com.intellij.uiDesigner.lw.LwRootContainer;
import consulo.maven.uiDesigner.cache.CacheLogic;

/**
 * @author VISTALL
 * @since 28-May-17
 */
@Mojo(name = "instrument", defaultPhase = LifecyclePhase.PROCESS_CLASSES, requiresOnline = false, requiresDependencyResolution = ResolutionScope.COMPILE, threadSafe = true)
public class InstrumentMojo extends AbstractMojo
{
	@Parameter(property = "project", defaultValue = "${project}")
	private MavenProject myMavenProject;

	@Parameter(property = "useJBScaling", defaultValue = "false")
	private boolean myUseJBScaling;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException
	{
		try
		{
			String sourceDirectory = myMavenProject.getBuild().getSourceDirectory();
			File sourceDirectoryFile = new File(sourceDirectory);
			if(!sourceDirectoryFile.exists())
			{
				getLog().info(sourceDirectory + " is not exists");
				return;
			}

			String outputDirectory = myMavenProject.getBuild().getOutputDirectory();

			List<File> files = FileUtils.getFiles(sourceDirectoryFile, "**/*.form", null);
			if(files.isEmpty())
			{
				return;
			}

			InstrumentationClassFinder finder = buildFinder();

			CacheLogic cacheLogic = new CacheLogic(myMavenProject, CacheLogic.NAME);

			cacheLogic.read();

			boolean changed = false;
			for(File file : files)
			{
				LwRootContainer rootContainer = Utils.getRootContainer(file.toURI().toURL(), new CompiledClassPropertiesProvider(finder.getLoader()));

				final String classToBind = rootContainer.getClassToBind();
				if(classToBind == null)
				{
					continue;
				}

				String name = classToBind.replace('.', '/');
				File classFile = getClassFile(name);
				if(classFile == null)
				{
					getLog().error(file.getAbsolutePath() + ": Class to bind does not exist: " + classToBind);
					continue;
				}

				if(cacheLogic.isUpToDate(file, classFile))
				{
					continue;
				}

				String path = file.getPath();

				cacheLogic.removeCacheEntry(file, classFile);

				final AsmCodeGenerator codeGenerator = new AsmCodeGenerator(rootContainer, finder, new MavenNestedFormLoader(this, files, finder), false, new InstrumenterClassWriter(isJdk6() ?
						ClassWriter.COMPUTE_FRAMES : ClassWriter.COMPUTE_MAXS, finder), myUseJBScaling);

				codeGenerator.patchFile(classFile);

				final FormErrorInfo[] errors = codeGenerator.getErrors();
				final FormErrorInfo[] warnings = codeGenerator.getWarnings();

				for(FormErrorInfo warning : warnings)
				{
					getLog().warn(warning.getErrorMessage(), warning.getThrowable());
				}

				for(FormErrorInfo error : errors)
				{
					getLog().error(error.getErrorMessage(), error.getThrowable());
				}

				changed = true;
				if(errors.length == 0)
				{
					String formRelativePath = path.substring(sourceDirectory.length(), path.length());

					FileUtils.copyFile(file, new File(outputDirectory, formRelativePath));

					cacheLogic.putCacheEntry(file, classFile);

					getLog().debug("Processed: " + path);
				}
			}

			if(!changed)
			{
				getLog().info("Nothing to instrument - all classes are up to date");
			}
			else
			{
				cacheLogic.write();
			}
		}
		catch(Exception e)
		{
			getLog().error(e);
		}
	}

	private InstrumentationClassFinder buildFinder() throws MalformedURLException, DependencyResolutionRequiredException
	{
		Collection<URL> classpath = new LinkedHashSet<URL>();
		addParentClasspath(classpath, false);
		addParentClasspath(classpath, true);

		File javaHome = new File(System.getProperty("java.home"));

		File rtJar = new File(javaHome, "lib/rt.jar");
		if(rtJar.exists())
		{
			classpath.add(rtJar.toURI().toURL());
		}

		List<String> compileClasspathElements = myMavenProject.getCompileClasspathElements();
		for(String compileClasspathElement : compileClasspathElements)
		{
			classpath.add(new File(compileClasspathElement).toURI().toURL());
		}

		boolean jdk9 = isJdk9OrHighter();
		URL[] platformUrls = new URL[jdk9 ? 1 : 0];
		if(jdk9)
		{
			platformUrls[0] = InstrumentationClassFinder.createJDKPlatformUrl(javaHome.getPath());
		}
		return new InstrumentationClassFinder(platformUrls, classpath.toArray(new URL[classpath.size()]));
	}

	private boolean isJdk6()
	{
		// FIXME [VISTALL] we need this?
		return true;
	}

	private boolean isJdk9OrHighter()
	{
		try
		{
			Class.forName("java.lang.Module");
			return true;
		}
		catch(ClassNotFoundException e)
		{
			return false;
		}
	}

	protected File getClassFile(String className)
	{
		final String classOrInnerName = getClassOrInnerName(className);
		if(classOrInnerName == null)
		{
			return null;
		}
		return new File(myMavenProject.getBuild().getOutputDirectory(), classOrInnerName + ".class");
	}

	protected String getClassOrInnerName(String className)
	{
		File classFile = new File(myMavenProject.getBuild().getOutputDirectory(), className + ".class");
		if(classFile.exists())
		{
			return className;
		}
		int position = className.lastIndexOf('/');
		if(position == -1)
		{
			return null;
		}
		return getClassOrInnerName(className.substring(0, position) + '$' + className.substring(position + 1));
	}

	private void addParentClasspath(Collection<URL> classpath, boolean ext) throws MalformedURLException
	{
		boolean isJava9 = isJdk9OrHighter();
		if(!isJava9)
		{
			String[] extDirs = System.getProperty("java.ext.dirs", "").split(File.pathSeparator);
			if(ext && extDirs.length == 0)
			{
				return;
			}

			List<URLClassLoader> loaders = new ArrayList<URLClassLoader>(2);
			for(ClassLoader loader = InstrumentMojo.class.getClassLoader(); loader != null; loader = loader.getParent())
			{
				if(loader instanceof URLClassLoader)
				{
					loaders.add(0, (URLClassLoader) loader);
				}
				else
				{
					getLog().warn("Unknown class loader: " + loader.getClass().getName());
				}
			}

			for(URLClassLoader loader : loaders)
			{
				URL[] urls = loader.getURLs();
				for(URL url : urls)
				{
					String path = urlToPath(url);

					boolean isExt = false;
					for(String extDir : extDirs)
					{
						if(path.startsWith(extDir) && path.length() > extDir.length() && path.charAt(extDir.length()) == File.separatorChar)
						{
							isExt = true;
							break;
						}
					}

					if(isExt == ext)
					{
						classpath.add(url);
					}
				}
			}
		}
		else if(!ext)
		{
			parseClassPathString(ManagementFactory.getRuntimeMXBean().getClassPath(), classpath);
		}
	}

	private void parseClassPathString(String pathString, Collection<URL> classpath)
	{
		if(pathString != null && !pathString.isEmpty())
		{
			try
			{
				StringTokenizer tokenizer = new StringTokenizer(pathString, File.pathSeparator + ',', false);
				while(tokenizer.hasMoreTokens())
				{
					String pathItem = tokenizer.nextToken();
					classpath.add(new File(pathItem).toURI().toURL());
				}
			}
			catch(MalformedURLException e)
			{
				getLog().error(e);
			}
		}
	}

	private static String urlToPath(URL url) throws MalformedURLException
	{
		try
		{
			return new File(url.toURI().getSchemeSpecificPart()).getPath();
		}
		catch(URISyntaxException e)
		{
			throw new MalformedURLException(url.toString());
		}
	}
}
