import csv
import re
from datetime import datetime, timedelta

# Stops configuration with their offsets
# Direction 0: Bolueta -> La Casilla
stops_dir0 = [
    ("BOLUETA", 0),
    ("ABUSU", 2),
    ("ATXURI", 6),
    ("RIBERA", 8),
    ("ARRIAGA", 10),
    ("ABANDO", 12),
    ("PIO BAROJA", 14),
    ("URIBITARTE", 15),
    ("GUGGENHEIM", 17),
    ("ABANDOIBARRA", 18),
    ("EUSKALDUNA", 20),
    ("SABINO ARANA", 22),
    ("SAN MAMES", 24),
    ("HOSPITAL/OSPITALEA", 26),
    ("BASURTO", 29),
    ("LA CASILLA", 31)
]

# Direction 1: La Casilla -> Bolueta
stops_dir1 = [
    ("LA CASILLA", 0),
    ("BASURTO", 1),
    ("HOSPITAL/OSPITALEA", 3),
    ("SAN MAMES", 5),
    ("SABINO ARANA", 7),
    ("EUSKALDUNA", 9),
    ("ABANDOIBARRA", 11),
    ("GUGGENHEIM", 12),
    ("URIBITARTE", 14),
    ("PIO BAROJA", 16),
    ("ABANDO", 18),
    ("ARRIAGA", 20),
    ("RIBERA", 22),
    ("ATXURI", 24),
    ("ABUSU", 28),
    ("BOLUETA", 31)
]

def add_mins(time_str, mins):
    t = datetime.strptime(time_str, "%H:%M")
    t += timedelta(minutes=mins)
    # Handle past midnight
    hours = t.hour
    if hours < 4: # GTFS uses 24+, e.g. 25:15 for 01:15 AM
        hours += 24
    return f"{hours:02d}:{t.minute:02d}:00"

def generate_trips_for_service(service_id, dir_id, base_station, start_times, freqs, end_station_cutoffs=None):
    """
    Generate trips.
    start_times: list of explicit times like ['06:22', '07:22', '21:22', '22:37', '22:52']
    freqs: list of frequencies in minutes between the explicit times. e.g. [15, 12, 15]
    """
    trips = []
    
    # Extract times
    times = []
    for i in range(len(freqs)):
        current = datetime.strptime(start_times[i], "%H:%M")
        end = datetime.strptime(start_times[i+1], "%H:%M")
        if end < current:
            end += timedelta(days=1)
            
        freq = freqs[i]
        while current < end:
            times.append(current.strftime("%H:%M"))
            current += timedelta(minutes=freq)
            
    # Add the remaining explicit times after the last frequency segment
    for t in start_times[len(freqs):]:
        times.append(t)
        
    return times

# Define the schedules manually based on the markdown analysis
schedules = [
    # WEEKDAY DIR 0
    {
        "service_id": "WEEKDAY",
        "direction_id": 0,
        "segments": [
            # Partial trips from Atxuri
            {"start_stop": "ATXURI", "times": ["05:58", "06:13"]},
            # Full trips from Bolueta
            {"start_stop": "BOLUETA", "times": generate_trips_for_service("WEEKDAY", 0, "BOLUETA", ["06:22", "07:22", "21:22"], [15, 12]) + generate_trips_for_service("WEEKDAY", 0, "BOLUETA", ["21:22", "22:37"], [15]) + ["22:52"]}
        ]
    },
    # WEEKDAY DIR 1
    {
        "service_id": "WEEKDAY",
        "direction_id": 1,
        "segments": [
            {"start_stop": "LA CASILLA", "times": generate_trips_for_service("WEEKDAY", 1, "LA CASILLA", ["06:26", "07:11", "21:11", "22:41"], [15, 12, 15])},
            # Last partial trips terminating at Atxuri
            {"start_stop": "LA CASILLA", "end_stop": "ATXURI", "times": ["22:56", "23:11", "23:26"]}
        ]
    },
    # SATURDAY DIR 0
    {
        "service_id": "SATURDAY",
        "direction_id": 0,
        "segments": [
            {"start_stop": "ATXURI", "times": ["06:58", "07:13"]},
            {"start_stop": "BOLUETA", "times": generate_trips_for_service("SATURDAY", 0, "BOLUETA", ["07:22", "15:22", "21:22", "22:22"], [15, 12, 15]) + ["22:37", "22:52"]}
        ]
    },
    # SATURDAY DIR 1
    {
        "service_id": "SATURDAY",
        "direction_id": 1,
        "segments": [
            {"start_stop": "LA CASILLA", "times": ["07:26"] + generate_trips_for_service("SATURDAY", 1, "LA CASILLA", ["07:41", "15:11", "21:11", "22:41"], [15, 12, 15])},
            {"start_stop": "LA CASILLA", "end_stop": "ATXURI", "times": ["22:56", "23:11", "23:26"]}
        ]
    },
    # SUNDAY DIR 0
    {
        "service_id": "SUNDAY",
        "direction_id": 0,
        "segments": [
            {"start_stop": "ATXURI", "times": ["06:58", "07:13"]},
            {"start_stop": "BOLUETA", "times": generate_trips_for_service("SUNDAY", 0, "BOLUETA", ["07:22", "22:07"], [15]) + ["22:22", "22:37", "22:52"]}
        ]
    },
    # SUNDAY DIR 1
    {
        "service_id": "SUNDAY",
        "direction_id": 1,
        "segments": [
            {"start_stop": "LA CASILLA", "times": ["07:26"] + generate_trips_for_service("SUNDAY", 1, "LA CASILLA", ["07:41", "22:41"], [15])},
            {"start_stop": "LA CASILLA", "end_stop": "ATXURI", "times": ["22:56", "23:11", "23:26"]}
        ]
    }
]

# Load stop IDs
stop_ids = {}
with open('data/tranvia_gtfs/stops.csv', 'r', encoding='utf-8') as f:
    reader = csv.DictReader(f)
    for row in reader:
        stop_ids[row['stop_name']] = row['stop_id']

# Generate files
trips_data = []
stop_times_data = []

trip_counter = 1

for sched in schedules:
    service_id = sched['service_id']
    dir_id = sched['direction_id']
    stops_config = stops_dir0 if dir_id == 0 else stops_dir1
    
    # Asignar shape_id según la dirección
    shape_id = "TR_SHAPE_8753520" if dir_id == 0 else "TR_SHAPE_8753521"
    
    for seg in sched['segments']:
        start_stop = seg['start_stop']
        end_stop = seg.get('end_stop', None)
        
        # Find offset of start_stop to adjust base times
        start_offset = 0
        for s, off in stops_config:
            if s == start_stop:
                start_offset = off
                break
                
        for t in seg['times']:
            trip_id = f"TR_{service_id}_{dir_id}_{trip_counter}"
            headsign = end_stop if end_stop else stops_config[-1][0]
            
            trips_data.append([
                "TR", service_id, trip_id, headsign, dir_id, shape_id
            ])
            
            # Generate stop times
            seq = 1
            started = False
            for s, off in stops_config:
                if s == start_stop:
                    started = True
                if started:
                    # Time at this stop is (t) + (off - start_offset)
                    arr_time = add_mins(t, off - start_offset)
                    
                    stop_times_data.append([
                        trip_id, arr_time, arr_time, stop_ids[s], seq
                    ])
                    seq += 1
                
                if end_stop and s == end_stop:
                    break
                    
            trip_counter += 1

import os
os.makedirs('data/tranvia_gtfs', exist_ok=True)

# Write trips.csv
with open('data/tranvia_gtfs/trips.csv', 'w', encoding='utf-8', newline='') as f:
    writer = csv.writer(f)
    writer.writerow(["route_id", "service_id", "trip_id", "trip_headsign", "direction_id", "shape_id"])
    writer.writerows(trips_data)

# Write stop_times.csv
with open('data/tranvia_gtfs/stop_times.csv', 'w', encoding='utf-8', newline='') as f:
    writer = csv.writer(f)
    writer.writerow(["trip_id", "arrival_time", "departure_time", "stop_id", "stop_sequence"])
    writer.writerows(stop_times_data)

print(f"Generated {len(trips_data)} trips and {len(stop_times_data)} stop times.")
