import requests
import csv
import os

def extract_shapes():
    print("Conectando con OpenStreetMap (Overpass API) para descargar trazados...")
    url = "http://overpass-api.de/api/interpreter"
    
    # Consulta para obtener la línea de tranvía de Euskotren en la zona de Bilbao
    # out geom; nos devuelve directamente las coordenadas de todos los puntos de la vía
    query = """
    [out:json][timeout:25];
    relation["route"="tram"]["network"~"Euskotren"](43.24,-3.0,43.28,-2.9);
    out geom;
    """
    
    # El User-Agent es CRUCIAL. Fue lo que falló antes al usar la configuración por defecto de Python.
    headers = {
        'User-Agent': 'AppMovilidadBilbao/1.0',
        'Accept': '*/*'
    }
    
    res = requests.post(url, data={'data': query}, headers=headers)
    if res.status_code != 200:
        print(f"Error de la API: {res.status_code}")
        print(res.text[:200])
        return

    data = res.json()
    relations = [e for e in data.get('elements', []) if e['type'] == 'relation']
    
    if not relations:
        print("No se encontró la relación del tranvía.")
        return

    print(f"Éxito: Se encontraron {len(relations)} relaciones de tranvía.")
    
    shapes_data = []
    
    # GTFS shapes.txt necesita: shape_id, shape_pt_lat, shape_pt_lon, shape_pt_sequence
    # Cada relación la convertiremos en un shape distinto.
    for rel in relations:
        shape_id = f"TR_SHAPE_{rel['id']}"
        seq = 1
        for member in rel.get('members', []):
            if member['type'] == 'way' and 'geometry' in member:
                for pt in member['geometry']:
                    shapes_data.append([shape_id, pt['lat'], pt['lon'], seq])
                    seq += 1

    # Asegurar que el directorio existe
    os.makedirs('../data/tranvia_gtfs', exist_ok=True)
    output_path = "../data/tranvia_gtfs/shapes.csv"
    
    with open(output_path, "w", encoding="utf-8", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["shape_id", "shape_pt_lat", "shape_pt_lon", "shape_pt_sequence"])
        writer.writerows(shapes_data)
        
    print(f"Archivo generado: {output_path} con {len(shapes_data)} puntos de ruta.")

if __name__ == "__main__":
    extract_shapes()
