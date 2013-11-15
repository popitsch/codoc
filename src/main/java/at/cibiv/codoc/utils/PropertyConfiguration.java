package at.cibiv.codoc.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * A configuration class that provides methods for loading and accessing a Java
 * property object (name/value pairs).
 * 
 * @author niko.popitsch@univie.ac.at
 */
public class PropertyConfiguration {

	/**
	 * true when errors should be logged to System.err or false otherwise.
	 */
	protected boolean debug = true;

	/**
	 * The filename of the properties file.
	 */
	protected String filename = null;

	/**
	 * true when the property file was loaded or false otherwise.
	 */
	protected boolean isLoaded = false;

	/**
	 * The properties object.
	 */
	protected Properties properties = new Properties();

	
	/**
	 * Constructor
	 * 
	 * @param filename
	 *            the name of the property file to be loaded.
	 */
	public PropertyConfiguration(File file) throws CodocException {
		this(file.getAbsolutePath());
	}
	
	/**
	 * Constructor.
	 * @param in
	 * @throws CodocException
	 */
	public PropertyConfiguration(InputStream in) throws CodocException {
		this();
		try {
			loadFromStream(in);
		} catch (IOException e) {
			throw new CodocException( e.getMessage() );
		}
	}
	/**
	 * Constructor
	 * 
	 * @param filename
	 *            the name of the property file to be loaded.
	 */
	public PropertyConfiguration(String filename) throws CodocException {
		this.filename = filename;
		File test = new File(filename);
		if (!test.exists())
			throw new CodocException("Configuration file " + filename + " does not exist!");
	}

	/**
	 * Constructor for property configuration without property file (for testing
	 * purposes)
	 */
	public PropertyConfiguration() {
		isLoaded = true;
	}

	/**
	 * Loads the properties from the passed input stream
	 */
	protected void loadFromStream(InputStream in) throws IOException {
		if (in == null)
			throw new IOException("Cannot read properties from NULL stream.");
		properties.load(in);
		isLoaded = true;
	}

	/**
	 * @return the filename of the properties file.
	 */
	public String getFilename() {
		return filename;
	}

	/**
	 * Sets the property filename. Calling this method forces a reload of the
	 * property file (if already loaded).
	 */
	public void setFilename(String filename) {
		this.filename = filename;
		isLoaded = false;
	}

	/**
	 * @return the properties object.
	 */
	public Properties getProperties() {
		return properties;
	}

	public void setProperties(Properties properties) {
		this.properties = properties;
	}

	/**
	 * Returns the value of the passed property or uses the value from
	 * system.properties if the property was not found.
	 * 
	 * @return the value of the property or the passed default value if not
	 *         found.
	 */
	public String getProperty(String key) {
		return getProperty(key, (String) null);
	}

	/**
	 * returns the property as Integer value or the default value.
	 */
	public Integer getIntProperty(String name, Integer defaultValue) {
		String val = getProperty(name);
		Integer ret = defaultValue;
		if (val != null)
			try {
				ret = Integer.parseInt(val);
			} catch (NumberFormatException e) {
				ret = defaultValue;
			}
		return ret;
	}

	/**
	 * returns the property as Boolean value or the default value.
	 */
	public Boolean getBooleanProperty(String name, Boolean defaultValue) {
		String val = getProperty(name);
		Boolean ret = defaultValue;
		if (val != null)
			try {
				ret = Boolean.parseBoolean(val);
			} catch (NumberFormatException e) {
				ret = defaultValue;
			}
		return ret;
	}

	/**
	 * Returns (a trimmed) value of the passed property. If no value was found,
	 * the passed default value is returned if it is not null. If the property
	 * was not set in the properties file, and the passed default value was
	 * null, the method tries to get the value from the system.properties.
	 * 
	 * @param name
	 *            the name of the property to return
	 * @param defaultValue
	 *            the used default value if the property was not found.
	 * @return the value of the property or <b>null</b> if not found.
	 */
	public String getProperty(String name, String defaultValue) {
		if (properties == null)
			properties = new Properties();

		if (!isLoaded) {
			// (1) load property file from current position
			try {
				FileInputStream inputStream = new FileInputStream(filename);
				if (inputStream != null) {
					properties.load(inputStream);
					isLoaded = true;
					if (debug)
						System.err.println("Loaded property file " + filename);
				}
			} catch (Exception e1) {
				if (debug)
					System.err.println("Could not load property file " + filename + " from current position. Trying CLASSPATH ...");
			}
		}
		if (!isLoaded) {
			// try to load property file from CLASSPATH
			try {
				InputStream inputStream = ClassLoader.getSystemClassLoader().getResourceAsStream(filename);
				if (inputStream != null) {
					properties.load(inputStream);
					isLoaded = true;
					if (debug)
						System.err.println("Loaded property file " + filename + " from CLASSPATH");
				}
			} catch (Exception e2) {
				if (debug) {
					System.err.println("Could not load property file " + filename + " from CLASSPATH. No configuration is used.");
				}
			}
		}

		if (properties == null)
			return null;
		String ret = properties.getProperty(name);
		if (ret == null) {
			if (defaultValue == null)
				ret = System.getProperty(name);
			else
				ret = defaultValue;
		}

		if (ret != null)
			return ret.trim();
		else
			return null;
	}

	/**
	 * Gets the passed property and throws the passed exception if null.
	 * 
	 * @param name
	 * @param configurationException
	 * @return
	 * @throws Throwable
	 */
	public String getProperty(String key, Throwable ex) throws Throwable {
		String v = getProperty(key);
		if (v == null)
			throw ex;
		return v;
	}

	/**
	 * Returns a list of the values of all properties that start with the passed
	 * prefix and end with a increasing number. Example: prefix = foo Returns
	 * the values of the properties "foo1", "foo2", ...
	 * 
	 * @param prefix
	 * @return A list of these properties or an empty list if none found.
	 */
	public Collection<String> listProperties(String prefix) {
		int i = 1;
		String name = prefix + i;
		String value = null;
		ArrayList<String> ret = new ArrayList<String>();
		while ((value = getProperty(name)) != null) {
			ret.add(value);
			i++;
			name = prefix + i;
		}
		return ret;
	}

	/**
	 * For testing purposes: allows to set properties without reading them from
	 * the filesystem
	 * 
	 * @param key
	 * @param value
	 */
	public void setProperty(String key, String value) {

		if (properties == null)
			properties = new Properties();

		properties.setProperty(key, value);

	}

	/**
	 * Tests for the property value != null
	 * 
	 * @param test
	 * @return
	 */
	public boolean hasProperty(String test) {
		return getProperty(test, (String) null) != null;
	}

	/**
	 * Create a properties object from the passed string.
	 * 
	 * @param conf
	 * @return
	 * @throws IOException
	 */
	public static PropertyConfiguration fromString(String conf) {
		PropertyConfiguration p = new PropertyConfiguration();
		StringReader in = new StringReader(conf);
		try {
			p.getProperties().load(in);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return p;
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
		List<String> outlist = new ArrayList<String>();
		for (Object k : getProperties().keySet()) {
			outlist.add(k + ":" + getProperties().get(k));
		}
		Collections.sort(outlist);
		for (String k : outlist) {
			sb.append(k + "\n");
		}
		return sb.toString();
	}

}
