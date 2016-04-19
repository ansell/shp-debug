#!/bin/sh

mvn -quiet clean install

./shpdump --input /media/sf_HostDesktop/wetlands/wetlands.shp --output target/ --prefix wetlands-group --remove-if-empty GROUP
./shpdump --input /media/sf_HostDesktop/wetlands/wetlands.shp --output target/ --prefix wetlands-name --remove-if-empty NAME
./shpdump --input /media/sf_HostDesktop/SDL-ResourceUnits-Groundwater-shp/Groundwater_SDL_Resource_Units.shp --output target/ --prefix mdb_groundwater_sdl
./shpdump --input /media/sf_HostDesktop/Surface_Water_SDL_Resource_Units/Surface_Water_SDL_Resource_Units.shp --output target/ --prefix mdb_surfacewater_sdl
./shpdump --input "/media/sf_HostDesktop/MDB-WRPA-Groundwater-shp/Murray-Darling Basin Water Resource Plan Areas - Groundwater.shp" --output target/ --prefix mdb_groundwater_wrpa
./shpdump --input "/media/sf_HostDesktop/MDB-WRPA-SurfaceWater/Murray-Darling Basin Water Resource Plan Areas - Surface Water.shp" --output target/ --prefix mdb_surfacewater_wrpa



