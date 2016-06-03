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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.geotools.data.FileDataStore;
import org.geotools.data.FileDataStoreFinder;
import org.geotools.data.collection.CollectionFeatureSource;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.feature.NameImpl;
import org.geotools.map.FeatureLayer;
import org.geotools.map.Layer;
import org.geotools.map.MapContent;
import org.geotools.styling.SLD;
import org.geotools.styling.Style;
import org.jooq.lambda.Unchecked;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.Name;
import com.fasterxml.jackson.databind.SequenceWriter;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.github.ansell.csv.sum.CSVSummariser;
import com.github.ansell.csv.util.CSVUtil;
import com.github.ansell.csv.util.ValueMapping;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

/**
 * Tool for merging the contents of SHP files with other sources of data.
 * 
 * @author Peter Ansell p_ansell@yahoo.com
 */
public class SHPMerge {

	public static void main(String... args) throws Exception {
		final OptionParser parser = new OptionParser();

		final OptionSpec<Void> help = parser.accepts("help").forHelp();
		final OptionSpec<File> input = parser.accepts("input").withRequiredArg().ofType(File.class).required()
				.describedAs("The input SHP file");
		final OptionSpec<String> inputPrefix = parser.accepts("input-prefix").withRequiredArg().ofType(String.class)
				.defaultsTo("").describedAs("A prefix to be used for the input file.");
		final OptionSpec<File> otherInput = parser.accepts("other-input").withRequiredArg().ofType(File.class)
				.required().describedAs("The other input CSV file to be merged.");
		final OptionSpec<String> otherPrefix = parser.accepts("other-prefix").withRequiredArg().ofType(String.class)
				.defaultsTo("").describedAs("A prefix to be used for the other file.");
		final OptionSpec<File> mapping = parser.accepts("mapping").withRequiredArg().ofType(File.class).required()
				.describedAs("The mapping file.");
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

		final Path otherInputPath = otherInput.value(options).toPath();
		if (!Files.exists(otherInputPath)) {
			throw new FileNotFoundException("Could not find other input CSV file: " + otherInputPath.toString());
		}

		final Path mappingPath = mapping.value(options).toPath();
		if (!Files.exists(mappingPath)) {
			throw new FileNotFoundException("Could not find mappng CSV file: " + mappingPath.toString());
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

		if (!filterFields.isEmpty()) {
			System.out.println("Full set of filter fields: " + filterFields);
		}

		final String prefix = outputPrefix.value(options);

		FileDataStore store = FileDataStoreFinder.getDataStore(inputPath.toFile());

		for (String typeName : new LinkedHashSet<>(Arrays.asList(store.getTypeNames()))) {
			System.out.println("");
			System.out.println("Type: " + typeName);
			SimpleFeatureSource featureSource = store.getFeatureSource(typeName);
			SimpleFeatureType schema = featureSource.getSchema();

			Name outputSchemaName = new NameImpl(schema.getName().getNamespaceURI(),
					schema.getName().getLocalPart().replace(" ", "").replace("%20", ""));
			System.out.println("Replacing name on schema: " + schema.getName() + " with " + outputSchemaName);
			SimpleFeatureType outputSchema = SHPUtils.changeSchemaName(schema, outputSchemaName);

			List<String> attributeList = new ArrayList<>();
			List<AttributeDescriptor> attributeDescriptors = schema.getAttributeDescriptors();
			for (AttributeDescriptor attribute : attributeDescriptors) {
				System.out.println("Attribute: " + attribute.getName().toString());
				attributeList.add(attribute.getName().toString());
			}
			CsvSchema csvSchema = CSVUtil.buildSchema(attributeList);
			SimpleFeatureCollection collection = featureSource.getFeatures();
			int featureCount = 0;
			Path nextCSVFile = outputPath.resolve(prefix + ".csv");
			Path nextMergedCSVFile = outputPath.resolve(prefix + "-merged.csv");
			Path nextSummaryCSVFile = outputPath.resolve(prefix + "-" + outputSchema.getTypeName() + "-Summary.csv");
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
					for (AttributeDescriptor attribute : attributeDescriptors) {
						String featureString = Optional.ofNullable(feature.getAttribute(attribute.getName())).orElse("")
								.toString();
						nextLine.add(featureString);
						if (filterFields.contains(attribute.getName().toString()) && featureString.trim().isEmpty()) {
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
						outputFeatureList.add(SHPUtils.changeSchemaName(feature, outputSchema));
						csv.write(nextLine);
					}
					nextLine.clear();
				}
			}

			try (Reader csvReader = Files.newBufferedReader(nextCSVFile, StandardCharsets.UTF_8);
					Writer summaryOutput = Files.newBufferedWriter(nextSummaryCSVFile, StandardCharsets.UTF_8,
							StandardOpenOption.CREATE_NEW);) {
				CSVSummariser.runSummarise(csvReader, summaryOutput, 50);
			}
			if (featureCount > 100) {
				System.out.println("");
			}
			System.out.println("");
			System.out.println("Feature count: " + featureCount);

			List<String> mergedOutputHeaders;
			try (final BufferedReader readerMapping = Files.newBufferedReader(mappingPath);
					final BufferedReader readerInput = Files.newBufferedReader(nextCSVFile);
					final BufferedReader readerOtherInput = Files.newBufferedReader(otherInputPath);
					final BufferedWriter writer = Files.newBufferedWriter(nextMergedCSVFile);) {
				List<ValueMapping> map = ValueMapping.extractMappings(readerMapping);
				mergedOutputHeaders = CSVUtil.runJoiner(readerInput, readerOtherInput, map, writer, inputPrefix.value(options),
						otherPrefix.value(options), true);
			}

			List<AttributeDescriptor> mergedAttributes = SHPUtils.getAttributeList(mergedOutputHeaders);
			// TODO: Output the merged feature list
			List<SimpleFeature> mergedFeatureList = SHPUtils.buildFeatureCollectionFromCSV(SHPUtils.newFeatureType(outputSchema, outputSchemaName, mergedAttributes), nextMergedCSVFile);
			
			SimpleFeatureCollection outputCollection = new ListFeatureCollection(outputSchema, outputFeatureList);
			Path outputShapefilePath = outputPath.resolve(prefix + "-" + outputSchema.getTypeName() + "-dump");
			if (!Files.exists(outputShapefilePath)) {
				Files.createDirectory(outputShapefilePath);
			}
			SHPUtils.writeShapefile(outputCollection, outputShapefilePath);

			// Create ZIP file from the contents to keep the subfiles together
			Path outputShapefileZipPath = outputPath.resolve(prefix + "-" + outputSchema.getTypeName() + "-dump.zip");
			try (final OutputStream out = Files.newOutputStream(outputShapefileZipPath, StandardOpenOption.CREATE_NEW);
					final ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8);) {
				Files.list(outputShapefilePath).forEachOrdered(Unchecked.consumer(e -> {
					zip.putNextEntry(new ZipEntry(e.getFileName().toString()));
					Files.copy(e, zip);
					zip.closeEntry();
				}));
			}

			try (final OutputStream outputStream = Files.newOutputStream(
					outputPath.resolve(prefix + "." + format.value(options)), StandardOpenOption.CREATE_NEW);) {
				MapContent map = new MapContent();
				map.setTitle(prefix + "-" + outputSchema.getTypeName());
				Style style = SLD.createSimpleStyle(featureSource.getSchema());
				Layer layer = new FeatureLayer(new CollectionFeatureSource(outputCollection), style);
				map.addLayer(layer);
				SHPUtils.renderImage(map, outputStream, resolution.value(options), format.value(options));
			}
		}
	}
}
