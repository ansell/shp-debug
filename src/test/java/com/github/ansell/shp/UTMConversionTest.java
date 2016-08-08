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
import org.junit.Test;
import org.opengis.geometry.Geometry;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;

import com.vividsolutions.jts.geom.Envelope;

/**
 * Test for converting between UTM and other coordinate reference systems using Geotools.
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

    @Test
    public final void testUTM2GDA94() throws Exception {
        CoordinateReferenceSystem targetCRS = CRS.decode("EPSG:4326");
        CoordinateReferenceSystem sourceCRS = CRS.decode("");
        MathTransform transform = CRS.findMathTransform(sourceCRS, targetCRS, false);
        Envelope sourceGeometry = null;
        Envelope targetGeometry = JTS.transform( sourceGeometry, transform);
    }

}
