package aQute.bnd.eclipse;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Formatter;

import aQute.bnd.build.Project;
import aQute.bnd.build.Workspace;
import aQute.bnd.eclipse.EclipseBuildProperties.Library;
import aQute.bnd.osgi.Constants;
import aQute.bnd.osgi.EmbeddedResource;
import aQute.bnd.osgi.Jar;
import aQute.bnd.osgi.Processor;
import aQute.bnd.plugin.git.GitPlugin;
import aQute.lib.io.IO;

public class LibPde extends Processor {

	final BndConversionPaths	mainSources;
	final BndConversionPaths	mainResources;
	final BndConversionPaths	testSources;
	final BndConversionPaths	testResources;
	final Workspace				workspace;
	private boolean				clean;

	public LibPde(Workspace ws, File pdeProject) throws IOException {
		super(ws);
		File file = getFile(pdeProject, "build.properties");
		setProperties(file);
		ws.addBasicPlugin( new GitPlugin());
		
		this.workspace = ws;

		mainSources = new BndConversionPaths(ws, Constants.DEFAULT_PROP_SRC_DIR, "src/main/java", "src=src/main/java");
		mainResources = new BndConversionPaths(ws, Constants.DEFAULT_PROP_RESOURCES_DIR, "src/main/resources", null);
		testSources = new BndConversionPaths(ws, Constants.DEFAULT_PROP_TESTSRC_DIR, "src/test/java",
				"test=src/test/java");
		testResources = new BndConversionPaths(ws, Constants.DEFAULT_PROP_TESTRESOURCES_DIR, "src/test/resources",
				null);
	}

	public String convert(Jar content) throws Exception {
		EclipseBuildProperties ebp = new EclipseBuildProperties(this);

		Library lib = ebp.getLibraries().iterator().next();
		EclipseManifest manifest = lib.getManifest();

		lib.move(content, mainSources, mainResources, testSources, testResources);
		String bnd = manifest.toBndFile();

		try (Formatter model = new Formatter()) {
			model.format("%s\n", bnd);
			mainSources.update(model);
			testSources.update(model);
		}

		content.putResource("bnd.bnd",
				new EmbeddedResource(bnd.toString().getBytes(StandardCharsets.UTF_8), 0));

		mainResources.remove(content, "META-INF/MANIFEST.MF");

		lib.removeOutputs(content);

		return manifest.getBsn();
	}

	public Project write() throws Exception {
		try (Jar content = new Jar(getBase())) {

			content.setReporter(this);

			String bsn = convert(content);
			if (clean) {
				File projectDir = workspace.getFile(bsn);
				IO.delete(projectDir);
			}

			Project p = workspace.createProject(bsn);
			new EclipseLifecyclePlugin().created(p);
			content.expand(p.getBase());
			p.forceRefresh();
			EclipseLifecyclePlugin.updateSettingsJDT(p);
			p.getInfo(this, bsn + ": ");
			return p;
		}
	}

	public void verify() {
		
	}

	public void clean() {
		this.clean = true;
	}
}
