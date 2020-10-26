package org.bndtools.builder;

import java.io.File;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.bndtools.api.ILogger;
import org.bndtools.api.Logger;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;

import aQute.bnd.build.Project;
import aQute.libg.tuple.Pair;

class EclipseProjectsSynchronizer {

	private static final ILogger	logger	= Logger.getLogger(BndtoolsBuilder.class);
	private static final Set<File>	seenProjects	= new HashSet<>();
	private Project model;
	private IProgressMonitor		monitor;

	public EclipseProjectsSynchronizer(Project model, IProgressMonitor monitor) {
		this.model = model;
		this.monitor = monitor;
	}

	public void synchronizeAll() {
		Set<Pair<String, File>> bndNameProjectDirs = model.getWorkspace()
			.getAllProjects()
			.stream()
			.map(p -> new Pair<String, File>(p.getName(), new File(p.getBaseURI())))
			.collect(Collectors.toSet());

		Set<File> bndProjectDirs = bndNameProjectDirs.stream()
			.map(Pair::getSecond)
			.peek(seenProjects::add)
			.collect(Collectors.toSet());

		Set<IProject> eclipseProjects = Stream.of(ResourcesPlugin.getWorkspace()
			.getRoot()
			.getProjects())
			.collect(Collectors.toSet());

		Set<File> eclipseProjectDirs = eclipseProjects.stream()
			.map(IResource::getLocationURI)
			.map(File::new)
			.filter(projectDir -> seenProjects.contains(projectDir))
			.collect(Collectors.toSet());

		Set<IProject> toRemove = eclipseProjects.stream()
			.filter(eclipseProject -> {
				URI projectURI = eclipseProject.getLocationURI();
				if (projectURI == null)
					return false;

				File projectDir = new File(projectURI);
				return !bndProjectDirs.contains(projectDir) && seenProjects.contains(projectDir);
			})
			.collect(Collectors.toSet());

		Set<Pair<String, File>> toAdd = bndNameProjectDirs.stream()
			.filter(pair -> !eclipseProjectDirs.contains(pair.getSecond()))
			.collect(Collectors.toSet());

		toRemove.forEach(this::remove);
		toAdd.forEach(this::add);
	}

	private void remove(IProject project) {
		try {
			project.delete(false, true, monitor);
		} catch (CoreException e) {
			logger.logError("Unable to remove project", e);
		}
	}

	private void add(Pair<String, File> pair) {
		String projectName = pair.getFirst();
		IPath location = new Path(pair.getSecond()
			.getAbsolutePath());

		try {
			IWorkspace workspace = ResourcesPlugin.getWorkspace();
			IProject project = workspace.getRoot()
				.getProject(projectName);

			if (project.exists()) {
				if (!project.isOpen()) {
					project.open(monitor);
				}

				return;
			}

			IProjectDescription description = workspace.newProjectDescription(projectName);

			description.setLocation(location);

			project.create(description, monitor);
			project.open(monitor);
		} catch (CoreException e) {
			logger.logError("Unable to add project " + projectName + " " + location, e);
		}
	}
}
