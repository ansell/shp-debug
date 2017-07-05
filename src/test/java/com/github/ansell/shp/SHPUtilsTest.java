/*
 * Copyright (c) 2016, Peter Ansell
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.ansell.shp;

import static org.junit.Assert.*;

import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.opengis.feature.simple.SimpleFeatureType;

/**
 * Tests for {@link SHPUtils}.
 * 
 * @author Peter Ansell p_ansell@yahoo.com
 */
public class SHPUtilsTest {

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	/**
	 * Test method for
	 * {@link com.github.ansell.shp.SHPUtils#renderImage(org.geotools.map.MapContent, java.io.OutputStream, int, java.lang.String)}.
	 */
	@Ignore("TODO: Implement me")
	@Test
	public final void testRenderImage() {
		fail("Not yet implemented"); // TODO
	}

	/**
	 * Test method for
	 * {@link com.github.ansell.shp.SHPUtils#writeShapefile(org.geotools.data.simple.SimpleFeatureCollection, java.nio.file.Path)}.
	 */
	@Ignore("TODO: Implement me")
	@Test
	public final void testWriteShapefile() {
		fail("Not yet implemented"); // TODO
	}

	/**
	 * Test method for
	 * {@link com.github.ansell.shp.SHPUtils#cloneSchema(org.opengis.feature.simple.SimpleFeatureType)}.
	 */
	@Test
	public final void testCloneSchema() {
		SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
		builder.setName("schemaToBeCloned");
		SimpleFeatureType nextSchema = builder.buildFeatureType();
		SimpleFeatureTypeImpl clonedSchema = SHPUtils.cloneSchema(nextSchema);

		assertEquals(nextSchema.getName(), clonedSchema.getName());
		// Verify the object references are distinct, per clone contract
		assertFalse(nextSchema == clonedSchema);
	}

	/**
	 * Test method for
	 * {@link com.github.ansell.shp.SHPUtils#changeSchemaName(org.opengis.feature.simple.SimpleFeatureType, org.opengis.feature.type.Name)}.
	 */
	@Ignore("TODO: Implement me")
	@Test
	public final void testChangeSchemaNameSimpleFeatureTypeName() {
		fail("Not yet implemented"); // TODO
	}

	/**
	 * Test method for
	 * {@link com.github.ansell.shp.SHPUtils#changeSchemaName(org.opengis.feature.simple.SimpleFeature, org.opengis.feature.simple.SimpleFeatureType)}.
	 */
	@Ignore("TODO: Implement me")
	@Test
	public final void testChangeSchemaNameSimpleFeatureSimpleFeatureType() {
		fail("Not yet implemented"); // TODO
	}

}
