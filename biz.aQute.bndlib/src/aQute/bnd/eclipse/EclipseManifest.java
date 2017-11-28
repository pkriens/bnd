package aQute.bnd.eclipse;

import java.io.File;
import java.io.IOException;
import java.util.Formatter;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import aQute.bnd.header.Attrs;
import aQute.bnd.header.Attrs.Type;
import aQute.bnd.header.OSGiHeader;
import aQute.bnd.header.Parameters;
import aQute.bnd.osgi.Constants;
import aQute.bnd.osgi.Domain;
import aQute.bnd.osgi.Processor;

public class EclipseManifest {
	public static final String	HEADER_FORMAT		= "%-40s: %s\n";
	public static String				REMOVE_HEADERS[]	= { "Built-By", "Created-By",
			"Bundle-RequiredExecutionEnvironment",
			"Build-Jdk", "Bundle-ManifestVersion", "ManifestVersion", "Archiver-Version", Constants.BUNDLE_CLASSPATH,
			Constants.EXPORT_PACKAGE, "Manifest-Version", Constants.BUNDLE_SYMBOLICNAME, Constants.SERVICE_COMPONENT };

	static String[]				PARAMETER_HEADERS	= { Constants.BUNDLE_ACTIVATIONPOLICY,
			Constants.BUNDLE_ACTIVATOR,
			Constants.BUNDLE_CATEGORY, Constants.BUNDLE_DEVELOPERS,
			Constants.BUNDLE_LICENSE, Constants.BUNDLE_LOCALIZATION, Constants.BUNDLE_NATIVECODE,
			Constants.EXPORT_SERVICE, Constants.FRAGMENT_HOST, Constants.IMPORT_PACKAGE,
			Constants.IMPORT_SERVICE,
			Constants.REQUIRE_CAPABILITY, Constants.PROVIDE_CAPABILITY,
			Constants.EXPORT_CONTENTS, Constants.EXPORT_PACKAGE };

	private final Processor		properties;
	private Domain				manifest;
	private String bsn;
	
	EclipseManifest(Processor properties, String manifest) throws IOException {
		this.properties = properties;
		File file = properties.getFile(manifest);
		if (!file.isFile())
			this.properties.error("Manifest not found %s", file);

		this.manifest = Domain.domain(file);
		
		bsn = this.manifest.getBundleSymbolicName().getKey();
		if ( bsn == null)
			bsn = properties.getBase().getName();
		assert bsn != null;
	}

	public String toBndFile() throws IOException {
		try (Formatter model = new Formatter()) {
			
			Attrs attrs = manifest.getBundleSymbolicName().getValue();
			if (!attrs.isEmpty()) {
				model.format(HEADER_FORMAT, Constants.BUNDLE_SYMBOLICNAME, manifest.getBundleSymbolicName().toString());
			}

			Parameters bcpin = manifest.getBundleClasspath();
			if (!bcpin.isEmpty()) {
				boolean hasOnlyDefault = bcpin.size() == 1 && bcpin.keySet().iterator().next().equals(".");
				if (!hasOnlyDefault) {
					model.format(HEADER_FORMAT, Constants.BUNDLE_CLASSPATH, format(bcpin));
				}
			}

			Set<String> headers = new HashSet<>();

			for (String key : manifest)
				headers.add(key);

			for (String header : REMOVE_HEADERS) {
				headers.remove(header);
			}

			for (String name : PARAMETER_HEADERS) {
				headers.remove(name);
				String value = manifest.get(name);
				if (value != null) {
					Parameters parameters = new Parameters(value);
					model.format(HEADER_FORMAT, name, format(parameters));
				}
			}

			for (String header : headers) {
				model.format(HEADER_FORMAT, header, manifest.get(header).trim());
			}

			return model.toString();
		}

	}

	private String format(Parameters parameters) throws IOException {
		if (parameters.isEmpty())
			return "";

		if (parameters.size() == 1) {
			return parameters.toString();
		}

		StringBuilder sb = new StringBuilder();

		String del = "\\\n    ";
		for (Map.Entry<String, Attrs> e : parameters.entrySet()) {
			sb.append(del).append(e.getKey());
			Attrs value = e.getValue();
			for (Entry<String, String> a : value.entrySet()) {
				sb.append("; \\\n        ");
				sb.append(a.getKey().trim());
				Type type = value.getType(e.getKey());
				if (type != null && !type.equals(Type.STRING)) {
					sb.append(":").append(type);
				}
				sb.append("=");
				OSGiHeader.quote(sb, a.getValue());
			}
			del = ", \\\n    ";
		}
		sb.append("\n");
		return sb.toString();
	}

	public String getBsn() {
		return bsn;
	}
}
