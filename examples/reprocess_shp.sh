#!/bin/bash

set -e
set -x

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

PGUSER="gisuser"
PGPASSWORD="something"
PGDB="test_postgis_db"
PGHOST="localhost"

POSTGRES_JDBC_CONNECTION="jdbc:postgresql://${PGHOST}/${PGDB}?user=${PGUSER}&password=${PGPASSWORD}"
POSTGRES_PSQL_CONNECTION="host=${PGHOST} user=${PGUSER} dbname=${PGDB} password=${PGPASSWORD}"

DATA_FOLDER="/path/to/folder"

# Verify both the connection parameters and the installation of postgis on the server
psql "${POSTGRES_PSQL_CONNECTION}" -c "SELECT postgis_version();"

function uploadGisSHP {
  ../../shp-debug/shpdump --input "${1}" --output "temp/" --prefix "${2}" --remove-if-empty "the_geom"
  psql -q "${POSTGRES_PSQL_CONNECTION}" -c "DROP TABLE IF EXISTS \"${2}\";"
  shp2pgsql -s "${3}" -k -c "temp/${2}-${2}-dump/${2}.shp" "public.${2}" | psql -q "${POSTGRES_PSQL_CONNECTION}"
  psql -q "${POSTGRES_PSQL_CONNECTION}" -c "CREATE INDEX \"sidx_${2}_geom\" ON \"${2}\" USING gist (geom);"
}

function uploadGisSHPNoClean {
  psql -q "${POSTGRES_PSQL_CONNECTION}" -c "DROP TABLE IF EXISTS \"${2}\";"
  shp2pgsql -s "${3}" -k -c "${1}" "public.${2}" | psql -q "${POSTGRES_PSQL_CONNECTION}"
  psql -q "${POSTGRES_PSQL_CONNECTION}" -c "CREATE INDEX \"sidx_${2}_geom\" ON \"${2}\" USING gist (geom);"
}

function uploadDBF {
  psql -q "${POSTGRES_PSQL_CONNECTION}" -c "DROP TABLE IF EXISTS \"${2}\";"
  shp2pgsql -k -n -c "${1}" "public.${2}" | psql -q "${POSTGRES_PSQL_CONNECTION}"
}

function uploadStats {
  ../../csvsum/csvsum --input "${1}" --output "temp/${2}-Summary.csv" --output-mapping "temp/${2}-dbmapping.csv"
  ../../csvsum/csvupload --database "${POSTGRES_JDBC_CONNECTION}" --input "${1}" --table "${2}" --drop-existing-table true --mapping "temp/${2}-dbmapping.csv"
  psql -q "${POSTGRES_PSQL_CONNECTION}" -c "ALTER TABLE \"${2}\" ADD COLUMN region_id_number text;"
  psql -q "${POSTGRES_PSQL_CONNECTION}" -c "UPDATE \"${2}\" SET \"region_id_number\" = SUBSTRING(\"region_id\", 5, LENGTH(\"region_id\"));"
  psql -q "${POSTGRES_PSQL_CONNECTION}" -c "DROP INDEX IF EXISTS \"${2}_with_geom_gist\";"
  psql -q "${POSTGRES_PSQL_CONNECTION}" -c "DROP TABLE IF EXISTS \"${2}_with_geom\";"

  psql -q "${POSTGRES_PSQL_CONNECTION}" -c "CREATE TABLE \"${2}_with_geom\" AS SELECT * FROM \"${3}\" AS g, \"${2}\" AS v WHERE g.\"${4}\" = v.region_id_number;"
  psql -q "${POSTGRES_PSQL_CONNECTION}" -c "CREATE INDEX \"${2}_with_geom_gist\" ON \"${2}_with_geom\" USING gist (geom);"
}

function dumpSHP {
  mkdir -p "temp/${1}_phase1/"
  ogr2ogr -f "ESRI Shapefile" "temp/${1}_phase1/${1}.shp" PG:"${POSTGRES_PSQL_CONNECTION}" -sql "SELECT * FROM \"${1}_with_geom\";"

  # The shapefile output by ogr2ogr is not complete in some cases, so cleaning it up by passing it through shpdump
  mkdir -p "temp/${1}/"
  ../../shp-debug/shpdump --input "temp/${1}_phase1/${1}.shp" --output "temp/${1}/" --prefix "${1}"
  rm -r "temp/${1}_phase1/"
}

function mergeWithGeometry {
  left_table="${1}"
  right_table="${2}"
  dest_table="${2}_with_geom"
  join_statement="${3}" # g.\"CE_PID\" = d.\"CE_PID\"
  output_fields="${4}" # g.geom, d.*, g.\"CE_PLY_PID\"
  other_table_refs="${5}"
  psql -q "${POSTGRES_PSQL_CONNECTION}" -c "DROP INDEX IF EXISTS \"${dest_table}_gist\";"
  psql -q "${POSTGRES_PSQL_CONNECTION}" -c "DROP TABLE IF EXISTS \"${dest_table}\";"
  psql -q "${POSTGRES_PSQL_CONNECTION}" -c "CREATE TABLE \"${dest_table}\" AS SELECT DISTINCT ${output_fields} FROM \"${left_table}\" AS g, \"${right_table}\" AS d ${other_table_refs} WHERE ${join_statement};"
  psql -q "${POSTGRES_PSQL_CONNECTION}" -c "CREATE INDEX \"${dest_table}_gist\" ON \"${dest_table}\" USING gist (geom);"

}

function convertNumericToString {
  source_table="${1}"
  source_field="${2}"
  psql -q "${POSTGRES_PSQL_CONNECTION}" -c "ALTER TABLE public.\"${source_table}\" ALTER COLUMN \"${source_field}\" TYPE varchar(64);"
}

function convertSquareMetresToSquareKilometres {
  source_table="${1}"
  source_field="${2}"
  psql -q "${POSTGRES_PSQL_CONNECTION}" -c "UPDATE public.\"${source_table}\" SET \"${source_field}\" = \"${source_field}\" * 0.000001;"
}

function calculateDensity {
  echo "Running calculateDensity for ${1} to ${2}"
  psql "${POSTGRES_PSQL_CONNECTION}" -c "ALTER TABLE \"${3}_with_geom\" ADD COLUMN \"${2}\" numeric;"
  psql "${POSTGRES_PSQL_CONNECTION}" -c "UPDATE \"${3}_with_geom\" SET \"${2}\" = (\"${1}\"/(\"AREA_SQKM\"+1.0));"
}

function rasterise {
  echo "Running rasterise for ${1}"
  psql -q "${POSTGRES_PSQL_CONNECTION}" -c "DROP TABLE IF EXISTS \"${2}_with_raster_${1}\";"
  psql -q "${POSTGRES_PSQL_CONNECTION}" -c "DROP TABLE IF EXISTS \"dummy_rast\";"
  psql -q "${POSTGRES_PSQL_CONNECTION}" -c "CREATE TABLE \"dummy_rast\" ( rid integer PRIMARY KEY , rast raster );"

  echo "Running ST_MakeEmptyRaster for ${1}"
  time psql -q "${POSTGRES_PSQL_CONNECTION}" -c "INSERT INTO \"dummy_rast\" ( rid, rast ) VALUES (1, ST_MakeEmptyRaster(1800, 1800, 0, 0, 0.1, -0.1, 0, 0, 4283));"

  echo "Running ST_ExtractToRaster for ${1}"
  time psql -q "${POSTGRES_PSQL_CONNECTION}" -c "CREATE TABLE \"${2}_with_raster_${1}\" AS SELECT ST_ExtractToRaster(ST_AddBand(rast, '32BF'::text, -9999, -9999), 'public',  '${2}_with_geom', 'geom', '${1}', 'AREA_WEIGHTED_MEAN_OF_VALUES') rast FROM \"dummy_rast\";"

  echo "Running ST_AsGDALRaster for ${1}"
  time psql -q "${POSTGRES_PSQL_CONNECTION}" -c "COPY (SELECT encode(ST_AsGDALRaster(rast, 'GTiff'), 'hex') AS png FROM \"${2}_with_raster_${1}\") TO '/tmp/${2}_with_raster_${1}.hex';"

  cp "/tmp/${2}_with_raster_${1}.hex" "temp/"
  xxd -p -r "temp/${2}_with_raster_${1}.hex" > "temp/${2}_with_raster_${1}.tiff"

  gdal_translate -of EHdr "temp/${2}_with_raster_${1}.tiff" "temp/${2}_with_raster_${1}_export.bil"
  (cd temp/ && zip -r "${2}_with_raster_${1}_export.zip" "${2}_with_raster_${1}_export".*)
}

rm -rf "./temp"
mkdir -p "./temp"

function combineAllRegions {
  local -n states=$1
  local -n fields_to_process=$2
  region="${3}"
  geometry_type="${4}"
  folder="${5}"
  join_statement="${6}" 
  output_fields="${7}"
  other_table_refs="${8:-""}"

  merged_name="COMPLETE_${region}"
  merged_table="${merged_name}_with_geom"

  psql -q "${POSTGRES_PSQL_CONNECTION}" -c "DROP TABLE IF EXISTS \"${merged_table}\";"

  #for i in ${!states[@]}; do
  #  echo "${i}: ${states[${i}]}"
  #done

  for i in ${!states[@]}; do
    echo "${i}: ${states[${i}]}"
    next_state="${states[${i}]}"
    uploadDBF "${folder}${next_state}_${region}_shp.dbf" "${next_state}_${region}"
    next_geometry_file="${folder}${next_state}_${region}_${geometry_type}_shp.shp"
    if [[ -e "${next_geometry_file}" ]]; then
      uploadGisSHPNoClean "${next_geometry_file}" "${next_state}_${region}_${geometry_type}" "4283"
    fi
    # Special case for SEIFA that does not have geometries included, only links to SA1 regions
    if [[ ${region} == "SEIFA_2011" ]]; then
      mergeWithGeometry "COMPLETE_SA1_2011_with_geom" "${next_state}_${region}" "${join_statement}" "${output_fields}" "${other_table_refs}"
    else
      mergeWithGeometry "${next_state}_${region}_${geometry_type}" "${next_state}_${region}" "${join_statement}" "${output_fields}" "${other_table_refs}"
    fi
    next_source="${next_state}_${region}_with_geom"
    for n in ${!fields_to_process[@]}; do
      next_process_field=${fields_to_process[${n}]}
      echo "${n}: ${next_source} ${next_process_field}"
      if [[ ${next_process_field} -eq "AREA_SQKM" ]]; then
        # AREA_SQM is useless for mathematical operations as it is, but even more useless for textual operations
        # Hence, we divide the values by 1,000,000 instead of converting them to text
        # This also fits with the ABS practice of using AREA_SQKM
        convertSquareMetresToSquareKilometres "${next_source}" "${next_process_field}"
      else
        convertNumericToString "${next_source}" "${next_process_field}"
      fi
    done
    dumpSHP "${next_state}_${region}"
    if [[ ${i} -eq 0 ]]; then
      echo "First state found"
      psql -q "${POSTGRES_PSQL_CONNECTION}" -c "CREATE TABLE \"${merged_table}\" AS SELECT * FROM \"${next_source}\";"
    else
      echo "Other state found"
      psql -q "${POSTGRES_PSQL_CONNECTION}" -c "INSERT INTO \"${merged_table}\" SELECT * FROM \"${next_source}\";"
    fi
  done

  psql -q "${POSTGRES_PSQL_CONNECTION}" -c "CREATE INDEX \"${merged_table}_gist\" ON \"${merged_table}\" USING gist (geom);"
  dumpSHP "${merged_name}"
}

function identifyStatePIDs {
  dest_table="STATE_PIDS"
  source_table="COMPLETE_STATE_with_geom"
  output_fields=" s.\"STATE_PID\", s.\"STATE_NAME\", s.\"ST_ABBREV\" "
  psql -q "${POSTGRES_PSQL_CONNECTION}" -c "DROP TABLE IF EXISTS \"${dest_table}\";"
  psql -q "${POSTGRES_PSQL_CONNECTION}" -c "CREATE TABLE \"${dest_table}\" AS SELECT DISTINCT ${output_fields} FROM \"${source_table}\" AS s;"
}

uploadDBF "${DATA_FOLDER}example.dbf" "EXAMPLE_DBF"

state_boundaries=("ACT" "NSW" "NT" "QLD" "SA" "TAS" "VIC" "WA" "OT")
state_process_fields=()
combineAllRegions state_boundaries \
                  state_process_fields \
                  "STATE" \
                  "POLYGON" \
                  "${STATE_BOUNDARIES_FOLDER}" \
                  "g.\"STATE_PID\" = d.\"STATE_PID\"" \
                  "g.geom, d.*, g.\"ST_PLY_PID\""

identifyStatePIDs

iare_states=("ACT" "NSW" "NT" "QLD" "SA" "TAS" "VIC" "WA" "OT")
iare_process_fields=("AREA_SQKM" "IREG_11COD")
combineAllRegions iare_states \
                  iare_fields \
                  "IARE_2011" \
                  "POLYGON" \
                  "${INDIGENOUS_AREAS_FOLDER}" \
                  "g.\"IARE_11PID\" = d.\"IARE_11PID\" AND d.\"STATE_PID\" = s.\"STATE_PID\" " \
                  "g.geom, d.\"DT_CREATE\", d.\"DT_RETIRE\", d.\"IARE_11COD\", d.\"IARE_11NAM\", d.\"IREG_11COD\", d.\"IREG_11NAM\", g.\"IAR_11PPID\", s.\"STATE_NAME\", s.\"ST_ABBREV\", d.\"AREA_SQKM\" AS \"AREA_SQKM\" " \
                  " , \"STATE_PIDS\" AS s "

uploadStats "${DATAPACK_FOLDER_SEQUENTIAL}IARE/AUST/2011Census_I01A_AUST_IARE_sequential.csv" "2011Census_I01A_AUST_IARE_seq" "COMPLETE_IARE_2011_with_geom" "IARE_11COD"
calculateDensity "I3" "Den_I3" "2011Census_I01A_AUST_IARE_seq"
calculateDensity "I6" "Den_I6" "2011Census_I01A_AUST_IARE_seq"
calculateDensity "I12" "Den_I12" "2011Census_I01A_AUST_IARE_seq"
dumpSHP "2011Census_I01A_AUST_IARE_seq"

uploadStats "${DATAPACK_FOLDER_SEQUENTIAL}IARE/AUST/2011Census_I01B_AUST_IARE_sequential.csv" "2011Census_I01B_AUST_IARE_seq" "COMPLETE_IARE_2011_with_geom" "IARE_11COD"
calculateDensity "I276" "Den_I276" "2011Census_I01B_AUST_IARE_seq"
dumpSHP "2011Census_I01B_AUST_IARE_seq"



