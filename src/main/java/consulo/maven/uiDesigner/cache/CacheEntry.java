package consulo.maven.uiDesigner.cache;

import java.io.File;
import java.io.Serializable;

/**
 * @author VISTALL
 * @since 29-May-17
 */
public class CacheEntry implements Serializable
{
	private static final long serialVersionUID = 5473318768702243798L;

	private File myFormFile;
	private long myFormTimestamp;
	private File myClassFile;
	private long myClassTimestamp;

	private CacheEntry()
	{
	}

	public CacheEntry(File formFile, long formTimestamp, File classFile, long classTimestamp)
	{
		myFormFile = formFile;
		myFormTimestamp = formTimestamp;
		myClassFile = classFile;
		myClassTimestamp = classTimestamp;
	}

	public static long getSerialVersionUID()
	{
		return serialVersionUID;
	}

	public File getFormFile()
	{
		return myFormFile;
	}

	public long getFormTimestamp()
	{
		return myFormTimestamp;
	}

	public File getClassFile()
	{
		return myClassFile;
	}

	public long getClassTimestamp()
	{
		return myClassTimestamp;
	}

	@Override
	public boolean equals(Object o)
	{
		if(this == o)
		{
			return true;
		}
		if(o == null || getClass() != o.getClass())
		{
			return false;
		}

		CacheEntry that = (CacheEntry) o;

		if(myFormFile != null ? !myFormFile.equals(that.myFormFile) : that.myFormFile != null)
		{
			return false;
		}
		if(myClassFile != null ? !myClassFile.equals(that.myClassFile) : that.myClassFile != null)
		{
			return false;
		}

		return true;
	}

	@Override
	public int hashCode()
	{
		int result = myFormFile != null ? myFormFile.hashCode() : 0;
		result = 31 * result + (myClassFile != null ? myClassFile.hashCode() : 0);
		return result;
	}
}
