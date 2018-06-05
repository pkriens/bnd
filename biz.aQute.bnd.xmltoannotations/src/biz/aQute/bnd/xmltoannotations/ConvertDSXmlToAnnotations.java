package biz.aQute.bnd.xmltoannotations;

import java.io.File;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;

import aQute.bnd.osgi.Descriptors;
import aQute.bnd.osgi.Processor;
import aQute.lib.io.IO;
import aQute.lib.xmldtoparser.DomDTOParser;
import biz.aQute.bnd.xmltoannotations.ComponentDescriptor.ComponentAnnotations;
import biz.aQute.bnd.xmltoannotations.ComponentDescriptor.DS;

public class ConvertDSXmlToAnnotations extends Processor {
	Logger				logger	= LoggerFactory.getLogger(ConvertDSXmlToAnnotations.class);
	final DomDTOParser	p		= new DomDTOParser();
	final List<File>	sources;
	private boolean		dryrun;
	private boolean		backup = true;

	public ConvertDSXmlToAnnotations(List<File> sources) {
		this.sources = sources;
	}

	public void annotate(File xml) throws Exception {
		DS definition = DomDTOParser.parse(ComponentDescriptor.DS.class, xml);
		for (ComponentDescriptor cd : definition.component) {

			cd.validate(this);

			ComponentAnnotations ann = cd.toAnnotations();

			String fqClassName = ann.fqClassName;
			File sourcePath = find(fqClassName);
			if (sourcePath == null) {
				error("No such file %s", fqClassName);
				continue;
			}

			CompilationUnit cu = JavaParser.parse(sourcePath);
			String simpleClassName = Descriptors.getShortName(fqClassName);
			ClassOrInterfaceDeclaration target = cu.getClassByName(simpleClassName).orElse(null);
			if (target == null) {
				error("Source file %s does not contain class %s", sourcePath, fqClassName);
				continue;
			}

			if (addAnnotations(ann, target)) {
				if (!dryrun) {
					if (backup) {
						File bak = new File(sourcePath.getParentFile(), sourcePath.getName() + "~");
						sourcePath.renameTo(bak);
						progress("backup source %s", bak);
						bak = new File(xml.getParentFile(), xml.getName() + "~");
						xml.renameTo(bak);
						progress("backup %s", bak);
					} else {
						progress("no backup");
					}
					String source = cu.toString();
					IO.store(source, sourcePath);
					xml.delete();
				} else {
					progress("dryrun, not writing changed sources");
				}
			} else {
				error("Could not annotate");
			}

		}
	}

	private boolean addAnnotations(ComponentAnnotations ann, ClassOrInterfaceDeclaration target) {
		boolean ok = true;
		ok &= annotate(target, ann.component, "type " + ann.fqClassName);
		progress("type %s -> %s", ann.fqClassName, ann.component);

		for (Entry<String, ? extends Annotation> e : ann.fields.entrySet()) {
			FieldDeclaration field = target.getFieldByName(e.getKey()).orElse(null);
			if (field != null) {
				progress("field %s.%s -> %s", ann.fqClassName, e.getKey(), e.getValue());
				ok &= annotate(field, e.getValue(), "field " + e.getKey());
			} else {
				error("no such field %s.%s", ann.fqClassName, e.getKey());
				ok = false;
			}
		}

		for (Entry<String, ? extends Annotation> e : ann.methods.entrySet()) {
			String name = deduplicate(e.getKey());

			MethodDeclaration method = getMethod(target, name);
			if (method != null) {
				progress("method %s.%s -> %s", ann.fqClassName, e.getKey(), e.getValue());
				ok &= annotate(method, e.getValue(), "method " + e.getKey());
			} else {
				error("no such method %s.%s", ann.fqClassName,e.getKey());
				ok = false;
			}
		}
		return ok;
	}

	private String deduplicate(String name) {
		while (name.endsWith("~"))
			name.subSequence(0, name.length() - 1);
		return name;
	}

	private MethodDeclaration getMethod(ClassOrInterfaceDeclaration target, String name) {
		List<MethodDeclaration> methods = target.getMethodsByName(name);
		if (methods.isEmpty())
			return null;

		if (methods.size() > 1) {
			error("Multiple methods with the same name %s", name);
		}

		return methods.get(0);
	}

	private boolean annotate(NodeWithAnnotations<?> target, Annotation ann, String diag) {
		String string = ann.toString();
		AnnotationExpr annotationExpr = JavaParser.parseAnnotation(string);
		for ( AnnotationExpr x : target.getAnnotations()) {
			if ( x.equals(annotationExpr.getName())) {
				error("Annotation %s already applied to %s", x.getName(), diag);
			}
		}
		target.addAnnotation(annotationExpr);
		return true;
	}

	private File find(String fqn) {
		String fqnToPath = Descriptors.fqnToPath(fqn).replaceAll("\\.class$", ".java");

		for (File dir : sources) {
			File trial = IO.getFile(dir, fqnToPath);
			if (trial.isFile()) {
				return trial;
			}
		}
		return null;
	}

	public void setDryRun(boolean dryrun) {
		this.dryrun = dryrun;
	}

	public void setBackup(boolean backup) {
		this.backup = backup;
	}
}
