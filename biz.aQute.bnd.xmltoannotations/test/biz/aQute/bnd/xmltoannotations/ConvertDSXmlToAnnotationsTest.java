package biz.aQute.bnd.xmltoannotations;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.Collections;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import aQute.lib.fileset.FileSet;
import aQute.lib.io.IO;

public class ConvertDSXmlToAnnotationsTest {
	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	public ConvertDSXmlToAnnotationsTest() throws Exception {}

	@Test
	public void test() throws Exception {
		File tmp = temporaryFolder.newFolder();
		IO.copy(IO.getFile("resources/"), tmp);
		File sources = new File(tmp, "ds");
		FileSet xmls = new FileSet(tmp, "**.xml");
		try (ConvertDSXmlToAnnotations cdx = new ConvertDSXmlToAnnotations(Collections.singleton(sources))) {
			for (File xml : xmls.getFiles()) {
				cdx.annotate(xml);
			}

			String result = IO.collect(IO.getFile(tmp, "ds/test/A.java"));
			String expected = IO.collect(IO.getFile("resources/ds/test/A.expected"));
			assertEquals(expected, result);
		}
	}
}
