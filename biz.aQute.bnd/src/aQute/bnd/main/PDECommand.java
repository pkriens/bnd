package aQute.bnd.main;

import java.io.File;

import aQute.bnd.build.Project;
import aQute.bnd.build.Workspace;
import aQute.bnd.eclipse.LibPde;
import aQute.bnd.osgi.Processor;
import aQute.lib.getopt.Arguments;
import aQute.lib.getopt.Description;

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
	}
	
	public void _pde(ToBndOptions options) throws Exception {
		Workspace workspace = bnd.getWorkspace(options.workspace());
		if (workspace == null) {
			error("No workspace");
			return;
		}

		for(String path: options._arguments()) {
			File f = getFile(path);
			if ( f.isDirectory()) {
				LibPde pde = new LibPde(workspace, f);
				if (options.clean())
					pde.clean();

				pde.verify();
				if ( !pde.isOk()) {
					getParent().getInfo(pde, f.getName());
				} else {
					Project p = pde.write();
					getParent().getInfo(p);
					getParent().getLogger().trace("converted " + p);
				}
			}
		}
	}
}
