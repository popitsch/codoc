package at.cibiv.codoc;

import java.io.File;
import java.util.Arrays;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;

import at.cibiv.codoc.utils.PropertyConfiguration;

/**
 * Main class
 * 
 * @author niko.popitsch@univie.ac.at
 * 
 */
public class Main {

	static String VERSION = null;

	/**
	 * Usage
	 * 
	 * @param options
	 */
	private static void usage(Options options) {
		System.out.println("Welcome to NGC " + VERSION);
		System.out.println();
		System.out.println("Usage:\t\tjava -jar x.jar <command> [options]:\t");
		System.out.println("\t\t" + CoverageCompressor.CMD + "\t" + CoverageCompressor.CMD_INFO);
		System.out.println("\t\t" + CoverageDecompressor.CMD + "\t" + CoverageDecompressor.CMD_INFO);
		System.out.println("\t\t" + CoverageTools.CMD + "\t\t" + CoverageTools.CMD_INFO);
		System.exit(1);
	}

	/**
	 * @param args
	 * @throws Throwable
	 */
	public static void main(String[] args) throws Throwable {

		File vfile = new File("version.properties");
		PropertyConfiguration p = null;
		if (vfile.exists())
			p = new PropertyConfiguration(vfile);
		else if (ClassLoader.getSystemResourceAsStream("version.properties") != null)
			p = new PropertyConfiguration(ClassLoader.getSystemResourceAsStream("version.properties"));

		VERSION = (p != null) ? p.getProperty("version", "?") : "?";

		// create the command line parser
		CommandLineParser parser = new PosixParser();

		// create the Options
		Options options = new Options();
		options.addOption("h", "help", false, "Print this usage information.");

		try {
			// parse the command line arguments
			CommandLine line = parser.parse(options, args, true);

			// validate that block-size has been set
			if (line.getArgs().length == 0 || line.hasOption("h")) {
				usage(options);
			}
			String[] args2 = Arrays.copyOfRange(args, 1, args.length);

			if (line.getArgs()[0].equalsIgnoreCase(CoverageCompressor.CMD)) {
				CoverageCompressor.main(args2);
			} else if (line.getArgs()[0].equalsIgnoreCase(CoverageDecompressor.CMD)) {
				CoverageDecompressor.main(args2);
			} else if (line.getArgs()[0].equalsIgnoreCase(CoverageTools.CMD)) {
				CoverageTools.main(args2);
			} else
				usage(options);

		} catch (ParseException exp) {
			System.out.println("Unexpected exception:" + exp.getMessage());
		}
	}

}
