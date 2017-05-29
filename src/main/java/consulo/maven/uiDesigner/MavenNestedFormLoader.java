package consulo.maven.uiDesigner;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import com.intellij.compiler.instrumentation.InstrumentationClassFinder;
import com.intellij.uiDesigner.compiler.NestedFormLoader;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.lw.LwRootContainer;

/**
 * @author VISTALL
 * @since 28-May-17
 */
class MavenNestedFormLoader implements NestedFormLoader
{
	private final InstrumentMojo myMojo;
	private final List<File> myForms;
	private final InstrumentationClassFinder myFinder;
	private final HashMap myFormCache = new HashMap();

	public MavenNestedFormLoader(InstrumentMojo mojo, List<File> forms, final InstrumentationClassFinder finder)
	{
		myMojo = mojo;
		myForms = forms;
		myFinder = finder;
	}

	public LwRootContainer loadForm(String formFilePath) throws Exception
	{
		if(myFormCache.containsKey(formFilePath))
		{
			return (LwRootContainer) myFormCache.get(formFilePath);
		}

		String lowerFormFilePath = formFilePath.toLowerCase();
		myMojo.getLog().debug("Searching for form " + lowerFormFilePath);
		for(Iterator iterator = myForms.iterator(); iterator.hasNext(); )
		{
			File file = (File) iterator.next();
			String name = file.getAbsolutePath().replace(File.separatorChar, '/').toLowerCase();
			myMojo.getLog().debug("Comparing with " + name);
			if(name.endsWith(lowerFormFilePath))
			{
				return loadForm(formFilePath, new FileInputStream(file));
			}
		}

		InputStream resourceStream = myFinder.getResourceAsStream(formFilePath);
		if(resourceStream != null)
		{
			return loadForm(formFilePath, resourceStream);
		}
		throw new Exception("Cannot find nested form file " + formFilePath);
	}

	private LwRootContainer loadForm(String formFileName, InputStream resourceStream) throws Exception
	{
		final LwRootContainer container = Utils.getRootContainer(resourceStream, null);
		myFormCache.put(formFileName, container);
		return container;
	}

	public String getClassToBindName(LwRootContainer container)
	{
		final String className = container.getClassToBind();
		String result = myMojo.getClassOrInnerName(className.replace('.', '/'));
		if(result != null)
		{
			return result.replace('/', '.');
		}
		return className;
	}
}
