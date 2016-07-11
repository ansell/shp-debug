#!/bin/bash

set -e
set -x

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

PGUSER="gisuser"
PGPASSWORD="something"
PGDB="test_postgis_db"
PGDB_TEMPLATE="template_postgis2.2"
PGHOST="localhost"

POSTGRES_JDBC_CONNECTION="jdbc:postgresql://${PGHOST}/${PGDB}?user=${PGUSER}&password=${PGPASSWORD}"
POSTGRES_PSQL_CONNECTION="host=${PGHOST} user=${PGUSER} dbname=${PGDB} password=${PGPASSWORD}"
POSTGRES_PSQL_TEMPLATE_CONNECTION="host=${PGHOST} user=${PGUSER} dbname=${PGDB_TEMPLATE} password=${PGPASSWORD}"

sudo -u postgres dropdb --if-exists ${PGDB}
sudo -u postgres dropdb --if-exists ${PGDB_TEMPLATE}
sudo -u postgres dropuser --if-exists ${PGUSER}

sudo -u postgres psql -c "CREATE USER ${PGUSER} WITH PASSWORD '${PGPASSWORD}'"
sudo -u postgres psql -c "ALTER USER ${PGUSER} WITH SUPERUSER"
sudo -u postgres createdb -O ${PGUSER} ${PGDB_TEMPLATE}

psql "${POSTGRES_PSQL_TEMPLATE_CONNECTION}" -c "CREATE EXTENSION postgis;"

cd /tmp 
wget https://raw.githubusercontent.com/pedrogit/postgisaddons/master/postgis_addons.sql
psql "${POSTGRES_PSQL_TEMPLATE_CONNECTION}" -a -f postgis_addons.sql
rm postgis_addons.sql
cd

psql "${POSTGRES_PSQL_TEMPLATE_CONNECTION}" -c "SELECT postgis_version();"

sudo -u postgres createdb -T ${PGDB_TEMPLATE} ${PGDB}

psql "${POSTGRES_PSQL_CONNECTION}" -c "ALTER DATABASE \"${PGDB}\" SET postgis.gdal_enabled_drivers TO 'ENABLE_ALL';"
