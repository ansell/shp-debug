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

import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.opengis.geometry.Geometry;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;

/**
 * Test for converting between UTM and other coordinate reference systems using
 * Geotools.
 * 
 * @author Peter Ansell p_ansell@yahoo.com
 */
public class UTMConversionTest {

    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception {
    }

    /**
     * @throws java.lang.Exception
     */
    @After
    public void tearDown() throws Exception {
    }

    @Ignore("Not sure how to incorporate Zone into Envelope or Coordinate")
    @Test
    public final void testUTM2GDA94GeoTools() throws Exception {
        CoordinateReferenceSystem sourceCRS = CRS.decode("EPSG:32632");
        CoordinateReferenceSystem targetCRS = CRS.decode("EPSG:4326");
        MathTransform transform = CRS.findMathTransform(sourceCRS, targetCRS, false);
        // Not sure how to incorporate Zone into this...
        Envelope sourceGeometry = new Envelope(new Coordinate(280602, 6143294));
        Envelope targetGeometry = JTS.transform(sourceGeometry, transform);
    }

    @Test
    public final void testUTM2WGS84() throws Exception {
        WGS84 result = new WGS84(new UTM(56, 'H', 280602, 6143294));
        assertEquals("34.8290148S 150.6009081E", result.toString());
        assertEquals(-34.8290148, result.getLatitude(), 0.0000001);
        assertEquals(150.6009081, result.getLongitude(), 0.0000001);
    }
}
