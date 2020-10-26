package aQute.bnd.main;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import aQute.bnd.classfile.ClassFile;
import aQute.bnd.classfile.ConstantPool.EntryVisitor;
import aQute.bnd.classfile.ConstantPool.NameAndTypeInfo;
import aQute.bnd.classfile.ElementInfo;
import aQute.bnd.classfile.MethodInfo;
import aQute.bnd.classfile.SignatureAttribute;
import aQute.bnd.main.bnd.projectOptions;
import aQute.bnd.osgi.Jar;
import aQute.bnd.osgi.Processor;
import aQute.bnd.osgi.Resource;
import aQute.bnd.stream.MapStream;
import aQute.lib.fileset.FileSet;
import aQute.lib.getopt.Options;

public class ExperimentalCommands extends Processor {

	final bnd				bnd;
	final projectOptions	opts;

	public ExperimentalCommands(bnd bnd, projectOptions opts) throws Exception {
		super(bnd);
		this.bnd = bnd;
		this.opts = opts;

		List<String> args = opts._arguments();
		if (args.isEmpty()) {
			String help = opts._command()
				.execute(this, "help", Collections.emptyList());
			bnd.out.println(help);
			return;
		}
		String cmd = args.remove(0);
		String help = opts._command()
			.execute(this, cmd, args);
		if (help != null) {
			bnd.out.print(help);
		}
		bnd.getInfo(this);
	}

	interface SignatureCommand extends Options {
		boolean descriptors();
	}

	public void _signatures(SignatureCommand cmd) {
		for (String arg : cmd._arguments()) {
			FileSet fs = new FileSet(bnd.getBase(), arg);
			for (File f : fs.getFiles()) {
				try {
					if (cmd.descriptors())
						descriptors(f);
					else
						signatures(f);
				} catch (Exception e) {
					e.printStackTrace();
					error("Failed to parse class file %s : %s", f, e);
				}
			}
		}
	}

	private void descriptors(File f) throws IOException {
		try (Jar jar = new Jar(f)) {
			MapStream.of(jar.getResources())
				.filter((k, v) -> k.endsWith(".class"))
				.forEach(this::descriptors);
		}
	}

	private void descriptors(String path, Resource r) {
		try {
			ClassFile cf = ClassFile.parseInputStream(r.openInputStream());

			cf.constant_pool.accept(new EntryVisitor() {
				@Override
				public void visit(int index, NameAndTypeInfo info) {
					String s = cf.constant_pool.utf8(info.descriptor_index);
					bnd.out.println(s);
				}

				@Override
				public void visit(int index, MethodInfo info) {
					bnd.out.println(info.descriptor);
				}
			});

			Stream.concat(Stream.of(cf.methods), Stream.of(cf.fields))
				.forEach(el -> bnd.out.println(el.descriptor));

		} catch (Exception e) {
			e.printStackTrace();
			error("Failed to parse class resource %s : %s", path, e);
		}
	}

	public void signatures(File f) throws IOException {
		try (Jar jar = new Jar(f)) {
			MapStream.of(jar.getResources())
				.filter((k, v) -> k.endsWith(".class"))
				.forEach(this::gather);
		}
	}

	private void gather(String path, Resource r) {
		try {
			ClassFile cf = ClassFile.parseInputStream(r.openInputStream());
			Stream.concat(Stream.concat(Stream.of(cf.methods), Stream.of(cf.fields)), Stream.of(cf))
				.map(ElementInfo.class::cast)
				.flatMap(e -> Stream.of(e.attributes))
				.filter(a -> a instanceof SignatureAttribute)
				.map(SignatureAttribute.class::cast)
				.forEach(this::gather);
		} catch (Exception e) {
			e.printStackTrace();
			error("Failed to parse class resource %s : %s", path, e);
		}
	}

	private void gather(SignatureAttribute attr) {
		bnd.out.println(attr.signature);
	}
}
