# app-movilidad-campus-bilbao
Aplicación Android para el Reto UNIGO: herramienta de movilidad sostenible basada en datos abiertos para el acceso a los campus universitarios de Bilbao (UPV/EHU, Mondragon Unibertsitatea y Universidad de Deusto).

## Generar base de datos

Debido a que la base de datos completa contiene todos los nodos del GTFS y supera los límites de tamaño de GitHub (~320 MB), es necesario generarla localmente en tu ordenador antes de compilar la aplicación por primera vez.

**Requisitos previos:**
- Tener Python 3 instalado.

**Pasos a seguir:**
1. Abre una terminal en la raíz del proyecto.
2. Ejecuta el siguiente comando para generar la base de datos a partir de los archivos estáticos en `/data`:
   ```bash
   python tools/build_transit_db.py
   ```
   *(Nota: si estás en Windows y da error de codificación, ejecuta primero `$env:PYTHONUTF8=1` o usa `$env:PYTHONIOENCODING="utf-8"; python tools\build_transit_db.py`)*
3. El proceso tardará unos 15 segundos y generará el archivo `unigo_transit.db` automáticamente en `UNIGO/app/src/main/assets/databases/`.

## Configuración de la API Key (Google Maps)

Para que el mapa y el cálculo de rutas funcionen, es necesario configurar una clave válida de la API de Google Maps. Por motivos de seguridad, esta clave no está incluida en el código fuente.

**Pasos a seguir:**
1. En la raíz del proyecto (al mismo nivel que `settings.gradle`), crea un archivo de texto llamado `secrets.properties`.
2. Abre el archivo y añade la siguiente línea, sustituyendo el valor por la clave real (sin comillas):
   ```properties
   MAPS_API_KEY=AIzaSyA_tu_clave_real_aqui
   ```
3. Sincroniza el proyecto en Android Studio.
