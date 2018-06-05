package aQute.bnd.main;

import static aQute.bnd.eclipse.EclipseLifecyclePlugin.toClasspathTag;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import aQute.bnd.build.BuildFacet;
import aQute.bnd.build.Project;
import aQute.bnd.build.Workspace;
import aQute.bnd.build.util.UpdatePaths;
import aQute.bnd.eclipse.LibPde;
import aQute.bnd.header.Attrs;
import aQute.bnd.main.bnd.projectOptions;
import aQute.bnd.osgi.Instruction;
import aQute.bnd.osgi.Instructions;
import aQute.bnd.osgi.Processor;
import aQute.lib.getopt.Arguments;
import aQute.lib.getopt.Description;
import aQute.lib.io.IO;
import aQute.lib.strings.Strings;
import aQute.lib.tag.Tag;
import aQute.lib.utf8properties.UTF8Properties;

public class EclipseCommand extends Processor {
	private final static Logger			logger	= LoggerFactory.getLogger(EclipseCommand.class);
	final static DocumentBuilderFactory	dbf		= DocumentBuilderFactory.newInstance();
	final DocumentBuilder				db		= dbf.newDocumentBuilder();

	private bnd							bnd;

	public EclipseCommand(bnd parent) throws Exception {
		super(parent);
		this.bnd = parent;
		setBase(parent.getBase());
	}

	@Description("Eclipse PDE to bnd project conversion")
	@Arguments(arg = {
			"[pdeProjectDir]", "..."
	})
	interface ToBndOptions extends bnd.workspaceOptions {

		@Description("Ensure no bnd project directory by deleting it if it exists")
		boolean clean();

		@Description("Eclipse Workingset for imported projects")
		String[] set();

		@Description("Used a bnd file to specify the imported files. \n" + " -pde.input ::= part ( ',' part )*\n"
				+ " part       ::= (<filespec>;workingset=\"(ws ( , ws)*)\")")
		String instructions();

		@Description("If a directory is not a project directory (contains build.properties), recursively descent")
		boolean recurse();
	}

	@Description("Eclipse PDE to bnd project conversion")
	public void _pde(ToBndOptions options) throws Exception {
		if (options._arguments().isEmpty() || options._arguments().get(0).equals("help")) {
			bnd.out.println(options._help());
			return;
		}
		Workspace workspace = bnd.getWorkspace(options.workspace());
		if (workspace == null) {
			error("No workspace");
			return;
		}
		List<String> arguments = options._arguments();
		Set<Project> projects = new HashSet<>();

		Instructions selection = new Instructions("*");
		UTF8Properties properties = new UTF8Properties();

		if (options.instructions() != null) {
			File f = getFile(options.instructions());
			if (!f.isFile()) {
				error("Cannot find instructions file %s", f);
				return;
			}
			properties.load(f, this);
			selection = new Instructions(properties.getProperty("pde.selection"));
			logger.debug("pde.selection " + selection);
		}

		String optWorkingset = options.set() == null ? null : Strings.join(options.set());
		Set<Instruction> had = new HashSet<>();

		for (String path : arguments) {
			logger.debug("look in path " + path);
			File f = getFile(path);
			processDirectory(options, workspace, projects, selection, optWorkingset, had, f);
		}

		Set<Instruction> missingprojects = new HashSet<>(selection.keySet());
		missingprojects.removeAll(had);

		if (!missingprojects.isEmpty()) {
			error("Not all selections in the instruction file were used %s", missingprojects);
		}

		try (UpdatePaths updater = new UpdatePaths(workspace)) {
			Set<String> missing = new TreeSet<>();
			for (Project p : projects) {
				updater.updateProject(p, missing);

				getInfo(p, p.getName() + ": ");
			}
			if (!missing.isEmpty()) {
				getParent().error("Missing packages %s", Strings.join("\n", missing));
			}
		}

		getInfo(workspace, "ws: ");
	}

	@Description("Calculate the Eclipse .classpath file from the bnd.bnd settings. "
			+ "By default, the old and the calculated classpath are shown. "
			+ "Specifying the -u option will update the .classpath file")
	@Arguments(arg = {})
	interface ClasspathOptions extends projectOptions {
		@Description("Update the .classpath file")
		boolean update();
	}

	/**
	 * @param options
	 * @throws Exception
	 */
	public void _classpath(ClasspathOptions options) throws Exception {
		Project project = bnd.getProject(options.project());
		if (project == null) {
			error("Not in a project or -p set to an invalid directory");
			return;
		}

		File f = project.getFile(".classpath");
		if (!f.isFile()) {
			error("No .classpath file %s", f);
			return;
		}
		String oldClasspath = IO.collect(f);

		try (InputStream in = new FileInputStream(f)) {
			Document doc = db.parse(in);
			Tag classpathTag = toClasspathTag(project, doc);
			if (options.update())
				IO.store(classpathTag.toString(), f);
			else {
				bnd.out.println("Current version");
				bnd.out.println(oldClasspath);
				bnd.out.println("Proposed version (use -u to copy to this to " + f);
				bnd.out.println(classpathTag.toString());
			}

		}

		BuildFacet[] facets = BuildFacet.getBuildFacets(project);
		for (BuildFacet facet : facets) {
			facet.mkdirs(".gitigore");
		}
	}

	private void processDirectory(ToBndOptions options, Workspace workspace, Set<Project> projects,
			Instructions selection, String optWorkingset, Set<Instruction> had, File f) throws IOException, Exception {
		if (f.isDirectory()) {
			logger.debug("visiting dir  {}", f);
			if (getFile(f, "build.properties").isFile()) {
				logger.debug("found PDE project  {}", f);

				Instruction matcher = selection.matcher(f.getName());
				if (matcher != null) {
					logger.debug("Matched PDE project  {}", f);
					Attrs attrs = selection.get(matcher);
					System.out.println("Matched " + f.getName() + " " + attrs);
					String workingset = attrs.get("-workingset", optWorkingset);
					List<String> imports = Strings.split(attrs.get("-imports"));

					Project p = processFile(workingset, options.clean(), workspace, f);
					if (p != null) {

						for (Map.Entry<String,String> entry : attrs.entrySet()) {
							p.setProperty(entry.getKey(), entry.getValue());
							System.out.println("Set attr " + p + " " + entry);
						}

						projects.add(p);
						had.add(matcher);
					}
				} else {
					logger.debug("Skipped PDE project  {}", f);
				}
			} else {
				if (options.recurse()) {
					logger.debug("Recursing  {}", f);
					for (File sub : f.listFiles()) {
						processDirectory(options, workspace, projects, selection, optWorkingset, had, sub);
					}
				}
			}
		} else {
			// makes it easy to use wildcarding that includes non-project
			// directories or files
		}
	}

	private Project processFile(String workingsets, boolean clean, Workspace workspace, File f)
			throws IOException, Exception {
		System.out.println("Process " + f);
		LibPde pde = new LibPde(workspace, f);

		if (workingsets != null)
			pde.setWorkingset(workingsets);

		if (clean)
			pde.clean();

		pde.verify();
		if (!pde.isOk()) {
			getParent().getInfo(pde, f.getName());
		} else {
			Project p = pde.write();
			getParent().getInfo(p);
			getParent().getLogger().trace("converted " + p);
			return p;
		}
		return null;
	}

}
