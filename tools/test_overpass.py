import requests

url = "http://overpass-api.de/api/interpreter"
query = """
[out:json][timeout:25];
relation["route"="tram"]["network"~"Euskotren"](43.2,-3.0,43.3,-2.8);
out geom;
"""
headers = {
    'User-Agent': 'AppMovilidadBilbao/1.0',
    'Accept': '*/*'
}
try:
    res = requests.post(url, data={'data': query}, headers=headers)
    print('Status:', res.status_code)
    if res.status_code == 200:
        data = res.json()
        print('Elements found:', len(data.get('elements', [])))
        if len(data.get('elements', [])) > 0:
            print("First element ID:", data['elements'][0]['id'])
    else:
        print('Error:', res.text[:200])
except Exception as e:
    print('Exception:', str(e))
