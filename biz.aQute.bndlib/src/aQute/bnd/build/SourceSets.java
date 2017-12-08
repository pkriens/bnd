package aQute.bnd.build;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import aQute.bnd.osgi.Constants;
import aQute.lib.io.IO;
import aQute.lib.strings.Strings;

public class SourceSets {
	public final static String	JAVA		= "java";
	public final static String	GROOVY		= "groovy";
	public final static String	RESOURCES	= "resources";
	public static final String	MAIN		= "main";
	public static final String	TEST		= "test";

	public final String			name;
	public final File			home;
	final Map<String,SourceSet>	sets		= new HashMap<>();

	public class SourceSet {
		public final File		output;
		public final String		name;
		final Map<String,File>	directories	= new HashMap<>();

		SourceSet(String name, File output, List<String> src) {

			this.name = name;
			this.output = output;

			for (String s : src) {
				if (!s.endsWith("/"))
					s = s + "/";

				File dir = IO.getFile(home, s);
				directories.put(s, dir);
			}
		}

		public Optional<File> resolve(String resource) {
			return directories.//
			        values().//
			        stream().//
			        map(d -> IO.getFile(d, resource)).//
			        filter(File::isFile).//
			        findAny();
		}

		public Optional<String> resolve(Collection<String> content, String resource) {
			return directories.//
			        keySet().//
			        stream().//
			        map(p -> p + resource).//
			        filter(content::contains).//
			        findAny();
		}

		public List<File> getDirectories() {
			return new ArrayList<>(directories.values());
		}

		public List<String> getRelativePaths() {
			ArrayList<String> list = new ArrayList<>(directories.keySet());
			return list;
		}

		public File getHome() {
			return home;
		}

		public boolean isJava() {
			return is(JAVA);
		}

		public boolean isResources() {
			return is(RESOURCES);
		}

		public boolean is(String name) {
			return this.name.equals(name);
		}

		public Optional<File> getFile(String relativePath) {
			return directories.//
			        values().//
			        stream().//
			        map(dir -> IO.getFile(dir, relativePath)).//
			        filter(File::isFile).//
			        findFirst();

		}
	}

	SourceSets(File home, String name, SourceSet... sets) {
		this.home = home;
		this.name = name;
		for (SourceSet set : sets) {
			this.sets.put(set.name, set);
		}
	}

	private void add(String name, File output, List<String> paths) {
		SourceSet sourceSet = new SourceSet(name, output, paths);
		sets.put(name, sourceSet);
	}

	public SourceSet java() {
		return path(JAVA);
	}

	public SourceSet resources() {
		return path(RESOURCES);
	}

	public SourceSet path(String name) {
		return sets.get(name);
	}

	public static SourceSets[] getSourceSets(Project project) {

		List<String> src = Strings.split(project.getProperty(Constants.DEFAULT_PROP_SRC_DIR));
		List<String> resources = Strings.split(project.getProperty(Constants.DEFAULT_PROP_RESOURCES_DIR));
		String bin = project.getProperty(Constants.DEFAULT_PROP_BIN_DIR);

		SourceSets main = new SourceSets(project.getBase(), MAIN);
		main.add(JAVA, project.getFile(bin), src);
		main.add(RESOURCES, project.getFile(bin), resources);

		List<String> testsrc = Strings.split(project.getProperty(Constants.DEFAULT_PROP_TESTSRC_DIR));
		List<String> testresources = Strings.split(project.getProperty(Constants.DEFAULT_PROP_TESTRESOURCES_DIR));
		String bin_test = project.getProperty(Constants.DEFAULT_PROP_TESTBIN_DIR);

		SourceSets test = new SourceSets(project.getBase(), TEST);
		test.add(JAVA, project.getFile(bin_test), testsrc);
		test.add(RESOURCES, project.getFile(bin_test), resources);

		return new SourceSets[] {
		        main, test
		};
	}

}
