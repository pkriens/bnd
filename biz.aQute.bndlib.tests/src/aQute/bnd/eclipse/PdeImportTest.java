package aQute.bnd.eclipse;

import java.io.File;

import aQute.bnd.build.Workspace;
import aQute.lib.io.IO;
import aQute.lib.strings.Strings;
import junit.framework.TestCase;

public class PdeImportTest extends TestCase {

	File tmp = IO.getFile("generated/tmp");

	public void testSimple() throws Exception {
		IO.delete(tmp);
		File tgt = new File(tmp, "ws1");
		File pdeProject = IO.getFile("testresources/aQute.bnd.eclipse/pdeproj/p1");
		IO.copy(IO.getFile("testresources/aQute.bnd.eclipse/ws1"), tgt);
		Workspace ws = Workspace.getWorkspace(tgt);

		;
		try (LibPde l = new LibPde(ws, pdeProject)) {
			l.write();
		}
	}

	public void testMany() throws Exception {
		IO.delete(tmp);
		File tgt = new File(tmp, "ws1");
		for (File pdeProject : IO.getFile("/Users/aqute/Documents/QIVICON/com.qivicon.apps.system").listFiles()) {
			IO.copy(IO.getFile("testresources/aQute.bnd.eclipse/ws1"), tgt);
			Workspace ws = Workspace.getWorkspace(tgt);
			System.out.println("begin: " + pdeProject.getName());
			try (LibPde l = new LibPde(ws, pdeProject)) {
				if (l.getFile("build.properties").isFile()) {
					l.write();
					if (!l.isOk()) {
						System.out.println("Errors\n" + Strings.join("\n", l.getErrors()));
						System.out.println("Warnings\n" + Strings.join("\n", l.getWarnings()));
					}
					System.out.println("end: " + pdeProject.getName());
				}
			}
		}
	}
}
