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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import javax.imageio.ImageIO;

import org.geotools.data.shapefile.ShapefileDumper;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureImpl;
import org.geotools.feature.simple.SimpleFeatureTypeImpl;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.map.MapContent;
import org.geotools.renderer.GTRenderer;
import org.geotools.renderer.lite.StreamingRenderer;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.Name;
import org.opengis.filter.identity.FeatureId;

/**
 * Utilities for working with SHP files
 * 
 * @author Peter Ansell p_ansell@yahoo.com
 */
public class SHPUtils {

	public static void renderImage(final MapContent map, final OutputStream output, final int imageWidth, String format)
			throws IOException {
		GTRenderer renderer = new StreamingRenderer();
		renderer.setMapContent(map);

		ReferencedEnvelope mapBounds = map.getMaxBounds();
		double heightToWidth = mapBounds.getSpan(1) / mapBounds.getSpan(0);
		Rectangle imageBounds = new Rectangle(0, 0, imageWidth, (int) Math.round(imageWidth * heightToWidth));

		BufferedImage image = new BufferedImage(imageBounds.width, imageBounds.height, BufferedImage.TYPE_INT_RGB);

		Graphics2D gr = image.createGraphics();
		gr.setPaint(Color.WHITE);
		gr.fill(imageBounds);

		renderer.paint(gr, imageBounds, mapBounds);
		ImageIO.write(image, format, output);
	}

	public static void writeShapefile(SimpleFeatureCollection fc, Path outputDir) throws IOException {
		ShapefileDumper dumper = new ShapefileDumper(outputDir.toFile());
		dumper.setCharset(StandardCharsets.UTF_8);
		// split when shp or dbf reaches 1000MB
		int maxSize = 1000 * 1024 * 1024;
		dumper.setMaxDbfSize(maxSize);
		dumper.dump(fc);
	}

	public static SimpleFeatureTypeImpl cloneSchema(SimpleFeatureType schema) {
		return changeSchemaName(schema, schema.getName());
	}

	public static SimpleFeatureTypeImpl changeSchemaName(SimpleFeatureType schema, Name outputSchemaName) {
		List<AttributeDescriptor> attributeDescriptors = schema.getAttributeDescriptors();
		return newFeatureType(schema, outputSchemaName, attributeDescriptors);
	}

	public static SimpleFeatureTypeImpl newFeatureType(SimpleFeatureType schema, Name outputSchemaName,
			List<AttributeDescriptor> attributeDescriptors) {
		return new SimpleFeatureTypeImpl(outputSchemaName, attributeDescriptors, schema.getGeometryDescriptor(),
				schema.isAbstract(), schema.getRestrictions(), schema.getSuper(), schema.getDescription());
	}

	public static SimpleFeatureImpl changeSchemaName(SimpleFeature feature, SimpleFeatureType outputSchema) {
		List<Object> attributes = feature.getAttributes();
		FeatureId identifier = feature.getIdentifier();
		return newFeature(outputSchema, attributes, identifier);
	}

	public static SimpleFeatureImpl newFeature(SimpleFeatureType outputSchema, List<Object> attributes,
			FeatureId identifier) {
		return new SimpleFeatureImpl(attributes, outputSchema, identifier);
	}

}
