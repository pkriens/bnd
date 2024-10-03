package aQute.bnd.osgi.metainf;

import static aQute.bnd.osgi.Constants.METAINF_SERVICES;
import static aQute.bnd.osgi.Constants.METAINF_SERVICES_STRATEGY_ANNOTATION;
import static aQute.bnd.osgi.Constants.METAINF_SERVICES_STRATEGY_AUTO;
import static aQute.bnd.osgi.Constants.METAINF_SERVICES_STRATEGY_NONE;

import java.lang.annotation.RetentionPolicy;
import java.util.Map;

import aQute.bnd.header.Attrs;
import aQute.bnd.header.Parameters;
import aQute.bnd.osgi.Analyzer;
import aQute.bnd.osgi.Annotation;
import aQute.bnd.osgi.Annotation.ElementType;
import aQute.bnd.osgi.Descriptors.TypeRef;
import aQute.bnd.osgi.Processor;
import aQute.bnd.osgi.metainf.MetaInfService.Implementation;
import aQute.bnd.service.AnalyzerPlugin;

/**
 * process the META-INF/services/* files. These files can contain bnd
 * annotations.
 */

public class MetaInfServiceParser implements AnalyzerPlugin {

	/**
	 * Iterate over the the file in META-INF/services and process them for
	 * annotations.
	 */
	@Override
	public boolean analyzeJar(Analyzer analyzer) throws Exception {

		String strategy = strategy(analyzer);

		if (METAINF_SERVICES_STRATEGY_NONE.equals(strategy)) {
			// do not process META-INF/services files
			return false;
		}

		MetaInfService.getServiceFiles(analyzer.getJar())
			.values()
			.stream()
			.flatMap(mis -> mis.getImplementations()
				.values()
				.stream())
			.forEach(impl -> {
				Parameters annotations = impl.getAnnotations();

				if (annotations.isEmpty() && METAINF_SERVICES_STRATEGY_AUTO.equals(strategy)) {
					// if there are no annotations at the impl
					// we add one artificially to create the capabilities for
					// Service without any attributes in the manifest e.g.
					// Provide-Capability',
					// "osgi.serviceloader;osgi.serviceloader=serviceName
					annotations.add("aQute.bnd.annotation.spi.ServiceProvider", Attrs.EMPTY_ATTRS);
				}

				annotations
					.forEach((annotationName, attrs) -> {
						doAnnotationsforMetaInf(analyzer, impl, Processor.removeDuplicateMarker(annotationName), attrs);
					});
			});
		return false;
	}

	/*
	 * Process 1 annotation
	 */
	private void doAnnotationsforMetaInf(Analyzer analyzer, Implementation impl, String annotationName, Attrs attrs) {
		try {
			Map<String, Object> properties = attrs.toTyped();
			properties.putIfAbsent("value", impl.getServiceName()); // default
			TypeRef implementation = analyzer.getTypeRefFromFQN(impl.getImplementationName());
			assert implementation != null;
			Annotation ann = new Annotation(analyzer.getTypeRefFromFQN(annotationName), properties, ElementType.TYPE,
				RetentionPolicy.CLASS);
			analyzer.addAnnotation(ann, implementation);
		} catch (Exception e) {
			analyzer.exception(e, "failed to process %s=%v due to %s", annotationName, attrs, e);
		}
	}

	private String strategy(Analyzer analyzer) {
		return analyzer.getProperty(METAINF_SERVICES, METAINF_SERVICES_STRATEGY_ANNOTATION);
	}
}
