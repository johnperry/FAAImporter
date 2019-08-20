package org.jp.importer;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.zip.*;

/**
 * A generic Zip File class.
 */
public class ZipObject {

	ZipFile zipFile;

	/**
	 * Class constructor; verify that the zip file parses.
	 * @param file the zip file.
	 * @throws Exception if the zip file does not parse.
	 */
	public ZipObject(File file) throws Exception {
		zipFile = null;
		try { zipFile = new ZipFile(file); }
		catch (Exception ex) {
			if (zipFile != null) zipFile.close();
			throw ex;
		}
	}

	/**
	 * Close the ZipFile.
	 * @throws Exception on any error.
	 */
	public void close() throws Exception {
		zipFile.close();
	}

	/**
	 * Get the ZipEntry corresponding to a name
	 * in the ZipObject, or null if the ZipObject cannot be read.
	 * @param name the entry name
	 * @return the entry corresponding to the name
	 */
	public ZipEntry getEntry(String name) {
		try { return zipFile.getEntry(name); }
		catch (Exception ex) { return null; }
	}

	/**
	 * Unpack the file corresponding to a ZipEntry and place it in a specified directory.
	 * If the directory does not exist, it is created.
	 * @param entry the entry pointing to the desired file in the zip file. This can be
	 * one of the entries in the array returned by getEntries().
	 * @param dir the directory into which to unpack the zip file. This becomes
	 * root directory of the file tree of the object. Files with no path information
	 * are placed in this directory; files with path information are placed in child
	 * directories with this directory as the root. Directories are created when necessary.
	 * @return the file if the operation succeeded; null otherwise.
	 */
	public File extractFile(ZipEntry entry, File dir) {
		BufferedOutputStream out = null;
		BufferedInputStream in = null;
		if (!dir.exists()) dir.mkdirs();
		try {
			File outFile = new File(entry.getName().replace('/',File.separatorChar));
			outFile = new File(dir,outFile.getName());
			if (!entry.isDirectory()) {
				outFile.getParentFile().mkdirs();
				out = new BufferedOutputStream(new FileOutputStream(outFile));
				in = new BufferedInputStream(zipFile.getInputStream(entry));
				FileUtil.copy(in, out, -1);
				return outFile;
			}
		}
		catch (Exception e) { }
		FileUtil.close(in);
		FileUtil.close(out);
		FileUtil.close(zipFile);
		return null;
	}
}
