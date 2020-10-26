package aQute.bnd.main;

import org.junit.Test;

public class ExperimentalCommandTest {
	@Test
	public void exptest() throws Exception {
		bnd.main(new String[] {
			"experimental", "signatures", "generated/**.jar"
		});
	}
}
