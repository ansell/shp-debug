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
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.geotools.data.FileDataStore;
import org.geotools.data.FileDataStoreFinder;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.map.FeatureLayer;
import org.geotools.map.Layer;
import org.geotools.map.MapContent;
import org.geotools.renderer.GTRenderer;
import org.geotools.renderer.lite.StreamingRenderer;
import org.geotools.styling.SLD;
import org.geotools.styling.Style;
import org.opengis.feature.GeometryAttribute;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;

import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.github.ansell.csv.util.CSVUtil;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

/**
 * Tool for dumping/examining the contents of SHP files.
 * 
 * @author Peter Ansell p_ansell@yahoo.com
 */
public class SHPDump {

	public static void main(String... args) throws Exception {
		final OptionParser parser = new OptionParser();

		final OptionSpec<Void> help = parser.accepts("help").forHelp();
		final OptionSpec<File> input = parser.accepts("input").withRequiredArg().ofType(File.class).required()
				.describedAs("The input SHP file");
		final OptionSpec<File> output = parser.accepts("output").withRequiredArg().ofType(File.class).required()
				.describedAs("The output directory to use for debugging files");
		final OptionSpec<String> outputPrefix = parser.accepts("prefix").withRequiredArg().ofType(String.class)
				.defaultsTo("shp-debug").describedAs("The output prefix to use for debugging files");
		final OptionSpec<Integer> resolution = parser.accepts("resolution").withRequiredArg().ofType(Integer.class)
				.defaultsTo(2048).describedAs("The output PNG file resolution");

		OptionSet options = null;

		try {
			options = parser.parse(args);
		} catch (final OptionException e) {
			System.out.println(e.getMessage());
			parser.printHelpOn(System.out);
			throw e;
		}

		if (options.has(help)) {
			parser.printHelpOn(System.out);
			return;
		}

		final Path inputPath = input.value(options).toPath();
		if (!Files.exists(inputPath)) {
			throw new FileNotFoundException("Could not find input SHP file: " + inputPath.toString());
		}

		final Path outputPath = output.value(options).toPath();
		if (!Files.exists(outputPath)) {
			throw new FileNotFoundException("Output directory does not exist: " + outputPath.toString());
		}

		final String prefix = outputPrefix.value(options);

		FileDataStore store = FileDataStoreFinder.getDataStore(inputPath.toFile());

		MapContent map = new MapContent();
		map.setTitle(inputPath.getFileName().toString());

		for (String typeName : store.getTypeNames()) {
			System.out.println("");
			System.out.println("Type: " + typeName);
			SimpleFeatureSource featureSource = store.getFeatureSource(typeName);
			SimpleFeatureType schema = featureSource.getSchema();
			List<String> attributeList = new ArrayList<>();
			for (AttributeDescriptor attribute : schema.getAttributeDescriptors()) {
				System.out.println("Attribute: " + attribute.getName().toString());
				attributeList.add(attribute.getName().toString());
			}
			CsvSchema csvSchema = CSVUtil.buildSchema(attributeList);

			Style style = SLD.createSimpleStyle(featureSource.getSchema());
			Layer layer = new FeatureLayer(featureSource, style);
			map.addLayer(layer);
			SimpleFeatureCollection collection = featureSource.getFeatures();
			int featureCount = 0;
			try (SimpleFeatureIterator iterator = collection.features();) {
				while (iterator.hasNext()) {
					SimpleFeature feature = iterator.next();
					// GeometryAttribute sourceGeometry =
					// feature.getDefaultGeometryProperty();
					// System.out
					// .println("Feature: " + feature.getIdentifier() + "
					// geometry: " + sourceGeometry.getName());
					featureCount++;
					if (featureCount <= 2) {
						System.out.println("");
						System.out.println(feature.getIdentifier());
						for (AttributeDescriptor attribute : schema.getAttributeDescriptors()) {
							String featureString = feature.getAttribute(attribute.getName()).toString();
							System.out.print(attribute.getName() + "=");
							if (featureString.length() > 100) {
								featureString = featureString.substring(0, 100) + "...";
							}
							System.out.println(featureString);
						}
					} else if (featureCount % 100 == 0) {
						System.out.print(".");
					}
				}
			}
			if (featureCount > 100) {
				System.out.println("");
			}
			System.out.println("");
			System.out.println("Feature count: " + featureCount);
		}

		try (final OutputStream outputStream = Files.newOutputStream(outputPath.resolve(prefix + ".png"),
				StandardOpenOption.CREATE_NEW);) {
			saveImage(map, outputStream, resolution.value(options));
		}
	}

	public static void saveImage(final MapContent map, final OutputStream output, final int imageWidth)
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
		ImageIO.write(image, "png", output);
	}
}
