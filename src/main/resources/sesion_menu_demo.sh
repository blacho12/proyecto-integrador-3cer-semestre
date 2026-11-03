#!/bin/bash
# Sesion interactiva simulada del MenuPrincipal: se le envian por stdin las
# opciones y respuestas que escribiria el usuario, capturando su salida.
# Requiere que la cola de ordenes este vacia para conocer los ids de antemano.
export PGPASSWORD='blachosgres120107'
export DB_URL='jdbc:postgresql://localhost:5432/DB-INTEGRADOR-3S'
export DB_USER='postgres'
export DB_PASSWORD='blachosgres120107'
PSQL="/c/Program Files/PostgreSQL/16/bin/psql.exe"
CP="target/classes;$(cat target/classpath.txt)"

echo "### Datos semilla para la sesion ###"
"$PSQL" -q -U postgres -h localhost -d DB-INTEGRADOR-3S -c "
DELETE FROM orden_mantenimiento WHERE codigo_equipo = 'EQ-MENU';
DELETE FROM equipo WHERE codigo = 'EQ-MENU';
DELETE FROM area WHERE nombre = 'Area de la sesion del menu';"
AID=$("$PSQL" -U postgres -h localhost -d DB-INTEGRADOR-3S -t -A -q \
      -c "INSERT INTO area (nombre, descripcion) VALUES ('Area de la sesion del menu','Temporal') RETURNING id_area;")
echo "Area temporal creada con id_area=$AID"
"$PSQL" -q -U postgres -h localhost -d DB-INTEGRADOR-3S \
      -c "INSERT INTO equipo (codigo, nombre, id_area) VALUES ('EQ-MENU','Equipo del menu',$AID);"

NEXT=$("$PSQL" -U postgres -h localhost -d DB-INTEGRADOR-3S -t -A \
      -c "SELECT COALESCE(pg_sequence_last_value('orden_mantenimiento_id_orden_seq'::regclass),0) + 1;")
echo ">>> El siguiente id_orden que generara la BD sera: $NEXT (y $((NEXT+1)))"

echo
echo "################ SESION COMPLETA DEL MENU ################"
printf '7\n2\nEQ-MENU\nCORRECTIVO\nFuga de vapor critica\n3\n3\nN\n2\nEQ-MENU\nPREVENTIVO\nLubricacion programada\n1\n1\nS\n1\n3\n%s\nCORRECTIVO\nInspeccion urgente ampliada\n3\n3\nN\n1\n6\n5\n1\n6\n4\n%s\n1\n6\n6\n6\n7\n1\n0\n' \
       "$NEXT" "$((NEXT+1))" | java -cp "$CP" app.MenuPrincipal 2>&1

echo
echo "################ Limpieza de los datos temporales ################"
"$PSQL" -q -U postgres -h localhost -d DB-INTEGRADOR-3S -c "
DELETE FROM orden_mantenimiento WHERE codigo_equipo = 'EQ-MENU';
DELETE FROM equipo WHERE codigo = 'EQ-MENU';
DELETE FROM area WHERE id_area = $AID;
SELECT (SELECT count(*) FROM orden_mantenimiento WHERE codigo_equipo = 'EQ-MENU') AS ordenes_menu,
       (SELECT count(*) FROM equipo WHERE codigo = 'EQ-MENU') AS equipos_menu;"
