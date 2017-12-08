package aQute.bnd.build;

import static aQute.bnd.build.model.clauses.VersionedClause.from;
import static aQute.bnd.build.model.clauses.VersionedClause.replaceOrAdd;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Formatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.osgi.framework.VersionRange;
import org.osgi.framework.namespace.BundleNamespace;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;
import org.osgi.service.repository.Repository;

import aQute.bnd.build.model.BndEditModel;
import aQute.bnd.build.model.EE;
import aQute.bnd.build.model.clauses.ExportedPackage;
import aQute.bnd.build.model.clauses.VersionedClause;
import aQute.bnd.header.Attrs;
import aQute.bnd.header.Parameters;
import aQute.bnd.osgi.Analyzer;
import aQute.bnd.osgi.Builder;
import aQute.bnd.osgi.Constants;
import aQute.bnd.osgi.Descriptors.PackageRef;
import aQute.bnd.osgi.Processor;
import aQute.bnd.osgi.Verifier;
import aQute.bnd.osgi.repository.AggregateRepository;
import aQute.bnd.osgi.resource.FilterBuilder;
import aQute.bnd.osgi.resource.RequirementBuilder;
import aQute.bnd.osgi.resource.ResourceUtils;
import aQute.bnd.osgi.resource.ResourceUtils.BundleCap;
import aQute.bnd.properties.Document;
import aQute.bnd.version.MavenVersion;
import aQute.bnd.version.Version;
import aQute.lib.collections.MultiMap;
import aQute.lib.fileset.FileSet;
import aQute.lib.io.IO;
import aQute.lib.strings.Strings;

public class UpdateBuildTestPath extends Processor {
	Pattern										PACKAGE_STATEMENT_P	= Pattern
	        .compile("package\\s+(?<package>" + Verifier.PACKAGE_PATTERN_S + ")");

	private Analyzer							analyzer			= new Analyzer();
	private MultiMap<String,Package>			exportPackages		= new MultiMap<>();
	private Repository							repo;
	private MultiMap<String,VersionedClause>	extensions			= new MultiMap<>();
	private MultiMap<String,VersionedClause>	transitives			= new MultiMap<>();
	private List<VersionedClause>				blacklist			= new ArrayList<>();

	static class Package implements Comparable<Package> {
		String	packageName;
		String	version;
		Attrs	attrs;
		Project	project;

		@Override
		public int compareTo(Package o) {
			int c = packageName.compareTo(o.packageName);
			if (c != 0)
				return c;

			c = version.compareTo(o.version);
			if (c != 0)
				return c;

			return 0;
		}

		public String toString() {
			return packageName + "[" + project + "]";
		}
	}

	public UpdateBuildTestPath(Workspace workspace) throws Exception {
		super(workspace);
		addClose(analyzer);

		repo = new AggregateRepository(workspace.getPlugins(Repository.class));

		Parameters extensions = workspace.getParameters("pde.extensions");
		for (Map.Entry<String,Attrs> e : extensions.entrySet()) {
			String buildpath = e.getValue().get("-buildpath");
			Parameters bp = new Parameters(buildpath);
			Entry<String,Attrs> entry = bp.entrySet().iterator().next();
			VersionedClause vc = new VersionedClause(entry.getKey(), entry.getValue());
			this.extensions.add(e.getKey(), vc);
		}

		if (!this.extensions.containsKey("java"))
			this.extensions.put("java", new ArrayList<VersionedClause>());

		Parameters transitives = workspace.getParameters("pde.transitives");
		for (Map.Entry<String,Attrs> e : transitives.entrySet()) {
			String buildpath = e.getValue().get("-buildpath");
			Parameters bp = new Parameters(buildpath);
			Entry<String,Attrs> entry = bp.entrySet().iterator().next();
			VersionedClause vc = new VersionedClause(entry.getKey(), entry.getValue());
			this.transitives.add(e.getKey(), vc);
		}

		Parameters blacklist = workspace.getParameters("pde.blacklist");
		for (Map.Entry<String,Attrs> e : blacklist.entrySet()) {
			VersionedClause vc = new VersionedClause(e.getKey(), e.getValue());
			this.blacklist.add(vc);
		}

		for (Project project : workspace.getAllProjects()) {
			project.forceRefresh();
			try (ProjectBuilder projectBuilder = project.getBuilder(null)) {
				for (Builder b : projectBuilder.getSubBuilders()) {

					if (b.getBsn().endsWith(".test"))
						continue;

					add(project, b.getExportContents(), exportPackages);
					add(project, b.getExportPackage(), exportPackages);

					Parameters privates = new Parameters();
					for (File path : project.getSourcePath()) {
						FileSet sources = new FileSet(path, "**/*.java");
						for (File source : sources.getFiles()) {
							String content = IO.collect(source);
							Matcher matcher = PACKAGE_STATEMENT_P.matcher(content);
							if (matcher.find()) {
								String p = matcher.group("package");
								privates.put(p, new Attrs());
							}
						}
					}
					add(project, privates, exportPackages);
				}
			}
		}
	}

	private void add(Project project, Parameters exportContents, MultiMap<String,Package> map) throws Exception {
		for (Entry<String,Attrs> e : exportContents.entrySet()) {
			Attrs attrs = new Attrs(e.getValue());

			String version = e.getValue().getVersion();
			if (version == null) {
				version = project.findVersionFromPackageInfo(analyzer.getPackageRef(e.getKey()));
				if (version == null)
					version = "0";
			}

			assert version != null;

			attrs.put("version", version);
			Package ep = new Package();
			ep.packageName = e.getKey();
			ep.attrs = attrs;
			ep.version = version;
			ep.project = project;
			map.add(ep.packageName, ep);
		}
	}

	public void updateProject(Project p, Set<String> missing) throws IOException {
		SourceSets[] sets = SourceSets.getSourceSets(p);
		SourceSets main = sets[0];
		SourceSets test = sets[0];

		System.out.println("Update " + p);
		EE ee = p.getEE();

		Document document = new Document(p);
		BndEditModel model = new BndEditModel(document);
		boolean changes = false;
		List<VersionedClause> buildPath = getPath(p, model.getBuildPath(), Constants.BUILDPATH);
		List<VersionedClause> testPath = getPath(p, model.getTestPath(), Constants.TESTPATH);

		Set<String> ignorePackages = new HashSet<>();
		ignorePackages.addAll(ee.getPackages().keySet());

		if (!isFragment(p)) {
			ignorePackages.addAll(p.getAllSourcePackages());
		} else {
			System.out.println("Fragment, importing my own packages " + p.getFragmentHost());
			Entry<String,Attrs> fragmentHost = p.getFragmentHost();
			VersionedClause vc = new VersionedClause(fragmentHost.getKey(), fragmentHost.getValue());
			addVersionedClause(buildPath, vc);
		}

		if (hasRequiredBundles(p)) {
			for (Entry<String,Attrs> rb : p.getRequireBundle().entrySet()) {
				VersionedClause vc = new VersionedClause(rb.getKey(), rb.getValue());
				addVersionedClause(buildPath, vc);
			}
		}

		if (doExportedPackages(main, model)) {
			changes = true;
			Attrs attrs = new Attrs();
			VersionedClause vc = new VersionedClause("org.osgi.annotation.versioning", attrs);
			addVersionedClause(buildPath, vc);
		}

		Collection<File> sourceFiles = p.getAllSourceFiles(extensions.keySet().toArray(new String[] {}));
		changes = doPath(p, model, buildPath, sourceFiles, ignorePackages, missing);

		Set<File> testFiles = new FileSet(p.getTestSrc(), "**/*.java").getFiles();
		changes |= doPath(p, model, testPath, testFiles, ignorePackages, missing);

		changes |= addBundleclasspathResourcesToBuildpath(p, buildPath);

		if (changes) {
			model.setBuildPath(buildPath);
			model.setTestPath(testPath);
			model.saveChangesTo(document);

			IO.store(document.get(), p.getFile("bnd.bnd"));
		}
	}

	final static Pattern	VERSIONANNOTATION_P	= Pattern
	        .compile("@(org\\.osgi\\.annotation\\.versioning\\.)?Version\\s*\\(\\s*\"[^\"]+\"\\s*\\)");

	final static Pattern	PACKAGE_LINE_P		= Pattern.compile("^package\\s+" + Verifier.PACKAGE_PATTERN_S + "\\s*;",
	        Pattern.MULTILINE);

	private boolean doExportedPackages(SourceSets main, BndEditModel model) throws IOException {
		boolean changes = false;
		List<ExportedPackage> exports = model.getExportedPackages();
		if (exports != null) {
			for (ExportedPackage ep : exports) {
				changes = true;
				Attrs attribs = ep.getAttribs();
				changes |= attribs.remove(Constants.USES_DIRECTIVE) != null;

				String versionString = ep.getVersionString();
				if (versionString == null) {
					versionString = "0.0.0";
				}

				Version v = new Version(MavenVersion.cleanupVersion(versionString));
				versionString = v.getWithoutQualifier().toString();

				PackageRef packageRef = analyzer.getPackageRef(ep.getName());
				Optional<File> file = main.java().getFile(packageRef.getBinary() + "/package-info.java");
				if (!file.isPresent()) {
					changes = true;
					ep.setVersionString(null);
					attribs.remove("version");
					File dir = main.java().getDirectories().get(0);
					File packageInfo = IO.getFile(dir, packageRef.getBinary() + "/package-info.java");
					if (packageInfo.getParentFile().isDirectory()) {
						String content = getPackageWithVersion(versionString, packageRef);
						IO.store(content, packageInfo);
					}
				} else {
					String content = IO.collect(file.get());
					Matcher m = VERSIONANNOTATION_P.matcher(content);
					if (!m.find()) {
						m = PACKAGE_LINE_P.matcher(content);
						String replace = getPackageWithVersion(versionString, packageRef);
						content = m.replaceFirst(replace);
						IO.store(content, file.get());
					}
				}
			}
			if (changes)
				model.setExportedPackages(exports);
		}

		return changes;
	}

	private String getPackageWithVersion(String versionString, PackageRef packageRef) {
		try (Formatter f = new Formatter()) {
			f.format("@org.osgi.annotation.versioning.Version(\"%s\")\n", versionString);
			f.format("package %s;\n", packageRef.getFQN());
			return f.toString();
		}
	}

	//
	// Add the BCP resources to the -buildpath
	//
	private boolean addBundleclasspathResourcesToBuildpath(Project p, List<VersionedClause> buildPath) {
		boolean changes = false;
		try {
			Parameters bcp = p.getBundleClasspath();
			for (String resource : bcp.keySet()) {
				changes = true;
				String resourcePath = p.getResourcePath(resource);
				if (resourcePath != null) {
					Attrs attrs = new Attrs();
					attrs.put("version", "file");
					VersionedClause vc = new VersionedClause(resourcePath, attrs);
					vc.setVersionRange("file");
					addVersionedClause(buildPath, vc);
				} else if (!resource.equals(".")) {
					p.error("Cannot find resource on BCP: %s in %s", resource,
					        p.getProperty(Constants.DEFAULT_PROP_RESOURCES_DIR));
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return changes;
	}

	private boolean hasRequiredBundles(Project p) {
		return p.getRequireBundle() != null;
	}

	private boolean isFragment(Project p) {
		return p.getFragmentHost() != null;
	}

	private List<VersionedClause> getPath(Project p, List<VersionedClause> vcs, String name) {
		Parameters spec = p.getParameters(name);
		List<VersionedClause> path = from(spec);

		if (vcs != null)
			for (VersionedClause vc : vcs) {
				addVersionedClause(path, vc);
			}
		return path;
	}

	private boolean doPath(Project project, BndEditModel model, List<VersionedClause> path, Collection<File> files,
	        Set<String> ignorePackages, Set<String> missing) throws IOException {
		Set<String> imports = new HashSet<>();
		boolean changed = false;

		imports.addAll(new Parameters(project.getProperty("-imports")).keySet());

		Set<String> extensions = new HashSet<>();

		for (File sourceFile : files) {

			String pathAndExtension[] = Strings.extension(sourceFile.getName());
			extensions.add(pathAndExtension[1]);

			String sourceContent = IO.collect(sourceFile);
			Set<String> sourceImports = parseImportsFromSource(sourceContent);
			imports.addAll(sourceImports);
		}

		imports.removeAll(ignorePackages);
		imports = imports.stream().filter(s -> !s.startsWith("java.")).collect(Collectors.toSet());

		for (String pack : imports) {

			List<Package> projectsThatExportCurrentPackage = exportPackages.get(pack);
			if (projectsThatExportCurrentPackage == null) {
				projectsThatExportCurrentPackage = new ArrayList<>();
			} else
				projectsThatExportCurrentPackage = projectsThatExportCurrentPackage.stream()
				        .filter(pck -> pck.project != project)
				        .collect(Collectors.toList());

			boolean tmpChanged;
			if (projectsThatExportCurrentPackage.isEmpty()) {
				tmpChanged = doRepositoryDependency(path, pack);

			} else {
				tmpChanged = doWorkspaceProjectDependency(path, projectsThatExportCurrentPackage);
			}

			if (!tmpChanged) {
				missing.add(pack);
			} else
				changed = true;
		}

		for (String extension : extensions) {
			List<VersionedClause> list = this.extensions.get(extension);
			if (list != null) {
				for (VersionedClause vc : list) {
					addVersionedClause(path, vc);
				}
			}
		}

		Set<String> libs = path.stream().map(vc -> vc.getName()).collect(Collectors.toSet());

		for (String lib : libs) {
			List<VersionedClause> list = this.transitives.get(lib);
			if (list != null) {
				for (VersionedClause vc : list) {
					addVersionedClause(path, vc);
				}
			}
		}

		changed |= VersionedClause.removeFromPath(path, project.getName()) > 0;
		return changed;
	}

	private boolean addVersionedClause(List<VersionedClause> path, VersionedClause vc) {
		if (!vc.getName().endsWith(".test"))
			return VersionedClause.replaceOrAdd(path, vc);
		else
			return false;
	}

	private boolean doRepositoryDependency(List<VersionedClause> path, String pack) {

		RequirementBuilder rb = new RequirementBuilder(PackageNamespace.PACKAGE_NAMESPACE);
		FilterBuilder fb = new FilterBuilder();
		fb.eq(PackageNamespace.PACKAGE_NAMESPACE, pack);
		rb.addFilter(fb);
		Requirement packageRequirement = rb.synthetic();
		Map<Requirement,Collection<Capability>> result = repo.findProviders(Collections.singleton(packageRequirement));

		Set<Resource> resources = ResourceUtils.getResources(result.get(packageRequirement));
		if (!resources.isEmpty()) {
			List<VersionedClause> set = new ArrayList<VersionedClause>();
			nextResource: for (Resource resource : resources) {

				BundleCap bc = ResourceUtils.getBundleCapability(resource);
				if (bc != null) {
					String bsn = bc.osgi_wiring_bundle();
					Version version = bc.bundle_version();
					if (version == null) {
						version = Version.LOWEST;
						System.out.println(resource.getCapabilities(BundleNamespace.BUNDLE_NAMESPACE));
					} else {
						version = version.getWithoutQualifier();
					}

					Attrs attrs = new Attrs();
					attrs.put("version", version.toString());
					VersionedClause target = new VersionedClause(bc.osgi_wiring_bundle(), attrs);
					target.setVersionRange(version.toString());

					for (VersionedClause black : blacklist) {
						if (isInRange(target, black)) {
							System.out.println("Rejected " + target);
							continue nextResource;
						}
					}

					set.add(target);
				} else {
					error("Dependency on %s has no bundle capability", resource);
				}
			}

			Collections.sort(set, VersionedClause.COMPARATOR);
			switch (set.size()) {
				case 0 :
					return false;

				case 1 :
					VersionedClause only = set.get(0);
					only.setVersionRange(null);
					only.getAttribs().remove("version");
					replaceOrAdd(path, only);
					return true;

				default :
					VersionedClause best = set.get(set.size() - 1);
					replaceOrAdd(path, best);
					return true;

			}
		}
		return false;
	}

	private boolean isInRange(VersionedClause target, VersionedClause blacklist) {
		try {
			if (target.getName().equals(blacklist.getName())) {
				VersionRange blacklistRange = getVersionRange(blacklist);
				VersionRange targetVersion = getVersionRange(target);
				boolean reject = blacklistRange.includes(targetVersion.getLeft());
				return reject;

			} else
				return false;
		} catch (Exception e) {
			return false;
		}
	}

	private VersionRange getVersionRange(VersionedClause range) {
		String versionRange = range.getVersionRange();
		if (versionRange == null)
			return new VersionRange("0");
		else
			return new VersionRange(versionRange);
	}

	private boolean doWorkspaceProjectDependency(List<VersionedClause> path, List<Package> list) {
		boolean changed;
		Collections.sort(list);

		Package package2 = list.get(0);

		Attrs attrs = new Attrs();
		attrs.put("version", "latest");
		VersionedClause vc = new VersionedClause(package2.project.getName(), attrs);

		replaceOrAdd(path, vc);
		changed = true;
		return changed;
	}

	public static Pattern IMPORTLINE_P = Pattern
	        .compile("($|\n|\r)\\s*import\\s+(?<static>static\\s+)?(?<import>[.\\p{javaJavaIdentifierPart}]+\\*?)");

	public static Set<String> parseImportsFromSource(String source) {
		Matcher m = IMPORTLINE_P.matcher(source);
		Set<String> set = new HashSet<String>();
		while (m.find()) {
			String import_ = m.group("import");
			String static_ = m.group("static");
			int n = import_.lastIndexOf('.');
			String pack = import_.substring(0, n);

			if (static_ != null) {
				n = pack.lastIndexOf('.');
				pack = pack.substring(0, n);
			}

			pack = cleanup(pack);
			set.add(pack);
		}
		m = Verifier.FQNPATTERN.matcher(source);
		while (m.find()) {
			String pack = m.group();

			int n = pack.lastIndexOf('.');
			String simple = pack.substring(n + 1);
			if (Character.isUpperCase(simple.charAt(0)) && Character.isLowerCase(pack.charAt(0))
			        && pack.indexOf('.') != n) {
				pack = cleanup(pack);
				set.add(pack);
			}
		}
		return set;
	}

	private static String cleanup(String pack) {
		int n;
		while (true) {
			n = pack.lastIndexOf('.');
			if (n <= 0 || n >= pack.length() - 1)
				break;

			char c = pack.charAt(n + 1);
			if (!Character.isUpperCase(c))
				break;

			pack = pack.substring(0, n);
		}
		return pack;
	}
}
