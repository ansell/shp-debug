/**
 * 
 */
package com.github.ansell.shp;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for SHPDump
 * 
 * @author Peter Ansell p_ansell@yahoo.com
 */
class SHPDumpTest {

	/**
	 * @throws java.lang.Exception
	 */
	@BeforeEach
	void setUp() throws Exception {
	}

	/**
	 * @throws java.lang.Exception
	 */
	@AfterEach
	void tearDown() throws Exception {
	}

	/**
	 * Test method for {@link com.github.ansell.shp.SHPDump#main(java.lang.String[])}.
	 */
	@Test
	final void testMainHelp() throws Exception {
		SHPDump.main("--help");
	}

}
