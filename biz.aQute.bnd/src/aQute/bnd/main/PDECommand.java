package aQute.bnd.main;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import aQute.bnd.build.Project;
import aQute.bnd.build.UpdateBuildTestPath;
import aQute.bnd.build.Workspace;
import aQute.bnd.eclipse.LibPde;
import aQute.bnd.header.Attrs;
import aQute.bnd.osgi.Instruction;
import aQute.bnd.osgi.Instructions;
import aQute.bnd.osgi.Processor;
import aQute.lib.getopt.Arguments;
import aQute.lib.getopt.Description;
import aQute.lib.strings.Strings;
import aQute.lib.utf8properties.UTF8Properties;

public class PDECommand extends Processor {

	private bnd bnd;

	public PDECommand(bnd parent) {
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

	public void _pde(ToBndOptions options) throws Exception {
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
			System.out.println("pde.selection " + selection);
		}

		String optWorkingset = options.set() == null ? null : Strings.join(options.set());
		Set<Instruction> had = new HashSet<>();

		for (String path : arguments) {
			File f = getFile(path);
			processDirectory(options, workspace, projects, selection, optWorkingset, had, f);
		}

		Set<Instruction> missingprojects = new HashSet<>(selection.keySet());
		missingprojects.removeAll(had);

		if (!missingprojects.isEmpty()) {
			error("Not all selections in the instruction file were used %s", missingprojects);
		}

		try (UpdateBuildTestPath updater = new UpdateBuildTestPath(workspace)) {
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

	private void processDirectory(ToBndOptions options, Workspace workspace, Set<Project> projects,
			Instructions selection, String optWorkingset, Set<Instruction> had, File f) throws IOException, Exception {
		if (f.isDirectory()) {
			if (getFile(f, "build.properties").isFile()) {

				Instruction matcher = selection.matcher(f.getName());
				if (matcher != null) {
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
					System.out.println("Skipping " + f);
				}
			} else {
				if (options.recurse()) {
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
