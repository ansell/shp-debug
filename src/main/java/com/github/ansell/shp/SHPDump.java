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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.geotools.data.FileDataStore;
import org.geotools.data.FileDataStoreFinder;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.feature.NameImpl;
import org.geotools.feature.simple.SimpleFeatureTypeImpl;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.map.FeatureLayer;
import org.geotools.map.Layer;
import org.geotools.map.MapContent;
import org.geotools.styling.SLD;
import org.geotools.styling.Style;
import org.opengis.feature.FeatureVisitor;
import org.opengis.feature.GeometryAttribute;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.Name;
import org.opengis.filter.Filter;
import org.opengis.filter.sort.SortBy;
import org.opengis.util.ProgressListener;

import com.fasterxml.jackson.databind.SequenceWriter;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.github.ansell.csv.sum.CSVSummariser;
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
				.defaultsTo(2048).describedAs("The output image file resolution");
		final OptionSpec<String> format = parser.accepts("format").withRequiredArg().ofType(String.class)
				.defaultsTo("png").describedAs("The output image format");
		final OptionSpec<String> removeIfEmpty = parser.accepts("remove-if-empty").withRequiredArg()
				.ofType(String.class).describedAs(
						"The name of an attribute to remove if its value is empty before outputting the resulting shapefile. Use multiple times to specify multiple fields to check");

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

		final Set<String> filterFields = ConcurrentHashMap.newKeySet();
		if (options.has(removeIfEmpty)) {
			for (String nextFilterField : removeIfEmpty.values(options)) {
				System.out.println("Will filter field if empty value found: " + nextFilterField);
				filterFields.add(nextFilterField);
			}
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
			Path nextCSVFile = outputPath.resolve(prefix + ".csv");
			Path nextSummaryCSVFile = outputPath.resolve(prefix + "-" + typeName + "-Summary.csv");
			List<SimpleFeature> outputFeatureList = new CopyOnWriteArrayList<>();

			try (SimpleFeatureIterator iterator = collection.features();
					Writer bufferedWriter = Files.newBufferedWriter(nextCSVFile, StandardCharsets.UTF_8,
							StandardOpenOption.CREATE_NEW);
					SequenceWriter csv = CSVUtil.newCSVWriter(bufferedWriter, csvSchema);) {
				List<String> nextLine = new ArrayList<>();
				while (iterator.hasNext()) {
					SimpleFeature feature = iterator.next();
					featureCount++;
					if (featureCount <= 2) {
						System.out.println("");
						System.out.println(feature.getIdentifier());
					} else if (featureCount % 100 == 0) {
						System.out.print(".");
					}
					boolean filterThisFeature = false;
					for (AttributeDescriptor attribute : schema.getAttributeDescriptors()) {
						String featureString = feature.getAttribute(attribute.getName()).toString();
						nextLine.add(featureString);
						if (filterFields.contains(attribute.getName()) && featureString.trim().isEmpty()) {
							filterThisFeature = true;
						}
						if (featureString.length() > 100) {
							featureString = featureString.substring(0, 100) + "...";
						}
						if (featureCount <= 2) {
							System.out.print(attribute.getName() + "=");
							System.out.println(featureString);
						}
					}
					if (!filterThisFeature) {
						outputFeatureList.add(feature);
					}

					csv.write(nextLine);
					nextLine.clear();
				}
			}
			try (Reader csvReader = Files.newBufferedReader(nextCSVFile, StandardCharsets.UTF_8);
					Writer summaryOutput = Files.newBufferedWriter(nextSummaryCSVFile, StandardCharsets.UTF_8,
							StandardOpenOption.CREATE_NEW);) {
				CSVSummariser.runSummarise(csvReader, summaryOutput, 6);
			}
			if (featureCount > 100) {
				System.out.println("");
			}
			System.out.println("");
			System.out.println("Feature count: " + featureCount);

			Name outputSchemaName = new NameImpl(schema.getName().getLocalPart().replace(" ", "").replace("%20", ""));
			SimpleFeatureType outputSchema = SHPUtils.changeSchemaName(schema, outputSchemaName);
			
			SimpleFeatureCollection outputCollection = new ListFeatureCollection(outputSchema, outputFeatureList);
			Path outputShapefilePath = outputPath.resolve(typeName + "-dump");
			if (!Files.exists(outputShapefilePath)) {
				Files.createDirectory(outputShapefilePath);
			}
			SHPUtils.writeShapefile(outputCollection, outputShapefilePath);
		}

		try (final OutputStream outputStream = Files.newOutputStream(
				outputPath.resolve(prefix + "." + format.value(options)), StandardOpenOption.CREATE_NEW);) {
			SHPUtils.renderImage(map, outputStream, resolution.value(options), format.value(options));
		}
	}
}
