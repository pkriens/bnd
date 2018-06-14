package aQute.bnd.eclipse;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;

import aQute.bnd.build.Project;
import aQute.bnd.build.Workspace;
import aQute.bnd.build.util.UpdatePaths;
import aQute.bnd.osgi.Constants;
import aQute.lib.io.IO;
import aQute.lib.utf8properties.UTF8Properties;
import junit.framework.TestCase;

public class LibPdeTest extends TestCase {

	File tmp = IO.getFile("generated/tmp");

	public Workspace setup() throws IOException, Exception {
		IO.delete(tmp);
		File tgt = new File(tmp, "ws1");
		IO.copy(IO.getFile("testresources/aQute.bnd.eclipse/ws1"), tgt);
		Workspace ws = Workspace.getWorkspace(tgt);
		return ws;
	}

	public void testSimple() throws Exception {
		Workspace ws = setup();
		File p1File = IO.getFile("testresources/aQute.bnd.eclipse/pdeproj/p1");
		File p2File = IO.getFile("testresources/aQute.bnd.eclipse/pdeproj/p2");
		Project p1;
		Project p2;

		try (LibPde l = new LibPde(ws, p1File)) {
			p1 = l.write();

			assertTrue(l.check());
		}
		try (LibPde l = new LibPde(ws, p2File)) {
			p2 = l.write();

			assertTrue(l.check());
		}

		try (UpdatePaths updater = new UpdatePaths(ws)) {
			updater.updateProject(p1, new HashSet<>());
		}
		try (UpdatePaths updater = new UpdatePaths(ws)) {
			updater.updateProject(p2, new HashSet<>());
		}

	}

	public void testResourcesNotViaPrivatePackage() throws Exception {
		Workspace ws = setup();

		File p1File = IO.getFile("testresources/aQute.bnd.eclipse/pdeproj/p1");

		try (LibPde l = new LibPde(ws, p1File)) {
			Project p1 = l.write();
			assertTrue(l.check());
			UTF8Properties p = new UTF8Properties(p1.getFile("bnd.bnd"));
			String privatePackage = p.getProperty(Constants.PRIVATE_PACKAGE);
			assertThat(privatePackage).isNull();
			assertThat(p1.getFile("src/main/resources/lib/")
				.isDirectory()).isTrue();
		}
	}

	public void testUseManifest() throws Exception {
		Workspace ws = setup();
		ws.setProperty("pde.useManifest", "false");

		File p1File = IO.getFile("testresources/aQute.bnd.eclipse/pdeproj/p1");

		try (LibPde l = new LibPde(ws, p1File)) {
			Project p1 = l.write();
			assertTrue(l.check());

			assertThat(p1.getFile("META-INF/MANIFEST.MF")
				.isFile()).isFalse();
			assertThat(p1.getFile("src/main/resources/META-INF/MANIFEST.MF")
				.isFile()).isFalse();

			String bnd = IO.collect(p1.getFile("bnd.bnd"));
			assertThat(bnd).doesNotContain("-manifest");
		}

		ws = setup();
		ws.setProperty("pde.useManifest", "true");
		try (LibPde l = new LibPde(ws, p1File)) {
			Project p1 = l.write();
			assertTrue(l.check());

			assertThat(p1.getFile("META-INF/MANIFEST.MF")
				.isFile()).isTrue();
			assertThat(p1.getFile("src/main/resources/META-INF/MANIFEST.MF")
				.isFile()).isFalse();
			String bnd = IO.collect(p1.getFile("bnd.bnd"));
			assertThat(bnd).contains("-manifest");
		}
	}

}
