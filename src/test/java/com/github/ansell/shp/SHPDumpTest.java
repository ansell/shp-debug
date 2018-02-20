/**
 * 
 */
package com.github.ansell.shp;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;

import org.junit.Rule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.migrationsupport.rules.EnableRuleMigrationSupport;
import org.junit.rules.TemporaryFolder;

/**
 * Tests for SHPDump
 * 
 * @author Peter Ansell p_ansell@yahoo.com
 */
@EnableRuleMigrationSupport
class SHPDumpTest {

	@Rule
	public TemporaryFolder tempDir = new TemporaryFolder();

	private Path testDir;

	/**
	 * @throws java.lang.Exception
	 */
	@BeforeEach
	void setUp() throws Exception {
		testDir = tempDir.newFolder("shpdump-test").toPath();
	}

	/**
	 * @throws java.lang.Exception
	 */
	@AfterEach
	void tearDown() throws Exception {
	}

	/**
	 * Test method for
	 * {@link com.github.ansell.shp.SHPDump#main(java.lang.String[])}.
	 */
	@Test
	final void testMainHelp() throws Exception {
		SHPDump.main("--help");
	}

	/**
	 * Test method for
	 * {@link com.github.ansell.shp.SHPDump#main(java.lang.String[])}.
	 */
	@Disabled("Temporary test to diagnose an issue with an invalid shapefile")
	@Test
	final void testMainExternalFile() throws Exception {
		SHPDump.main("--input",
				"/media/sf_HostDesktop/ticket-14213/records-2018-02-19(2)/records-2018-02-19/records-2018-02-19.shp",
				"--output", testDir.toAbsolutePath().toString());

	}

}
