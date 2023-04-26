package consulo.maven.uiDesigner.cache;

import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.utils.io.IOUtil;

import java.io.*;
import java.util.HashSet;

/**
 * @author VISTALL
 * @since 29-May-17
 */
public class CacheLogic
{
	public static final String NAME = "gui-forms.cache";

	private File myFile;

	private HashSet<CacheEntry> myCacheEntries;

	public CacheLogic(MavenProject mavenProject, String fileName)
	{
		myFile = new File(mavenProject.getBuild().getDirectory(), fileName);
	}

	public void read()
	{
		myCacheEntries = readImpl();
	}

	public void write()
	{
		if(myCacheEntries == null)
		{
			throw new IllegalArgumentException("#read() is not called");
		}

		HashSet<CacheEntry> cacheEntries = myCacheEntries;
		myCacheEntries = null;

		File parentFile = myFile.getParentFile();
		parentFile.mkdirs();

		ObjectOutputStream stream = null;
		try
		{
			stream = new ObjectOutputStream(new FileOutputStream(myFile));
			stream.writeObject(cacheEntries);
		}
		catch(Exception ignored)
		{
		}
		finally
		{
			IOUtil.close(stream);
		}
	}

	public boolean isUpToDate(File formFile, File classFile)
	{
		CacheEntry cacheEntry = findCacheEntry(formFile, classFile);
		return cacheEntry != null && cacheEntry.getFormTimestamp() == formFile.lastModified() && cacheEntry.getClassTimestamp() == classFile.lastModified();
	}

	public CacheEntry findCacheEntry(File formFile, File classFile)
	{
		if(myCacheEntries == null)
		{
			throw new IllegalArgumentException("#read() is not called");
		}

		for(CacheEntry cacheEntry : myCacheEntries)
		{
			if(formFile.equals(cacheEntry.getFormFile()) && cacheEntry.getClassFile().equals(classFile))
			{
				return cacheEntry;
			}
		}
		return null;
	}

	private HashSet<CacheEntry> readImpl()
	{
		if(!myFile.exists())
		{
			return new HashSet<CacheEntry>();
		}

		ObjectInputStream stream = null;
		try
		{
			stream = new ObjectInputStream(new FileInputStream(myFile));
			return (HashSet<CacheEntry>) stream.readObject();
		}
		catch(Exception ignored)
		{
		}
		finally
		{
			IOUtil.close(stream);
		}
		return new HashSet<CacheEntry>();
	}

	public boolean delete()
	{
		return myFile.exists() && myFile.delete();
	}

	public File getFile()
	{
		return myFile;
	}

	public void removeCacheEntry(File file, File classFile)
	{
		myCacheEntries.remove(new CacheEntry(file, -1, classFile, -1));
	}

	public void putCacheEntry(File file, File clasFile)
	{
		myCacheEntries.add(new CacheEntry(file, file.lastModified(), clasFile, clasFile.lastModified()));
	}
}
