# WSBSTide

Tide prediction app for [White Sea Biological Station (ББС МГУ)](https://wsbs.ru).

Tells you when the water is coming and going, with a graph. Accurate enough for planning fieldwork.
Possibly accurate enough for everything else.

## What it does

- 24-hour tide height chart with day/night background and a "you are here" marker
- High/low water times and sunrise/sunset listed below the chart
- Drag the chart horizontally to consult adjacent days
- Works offline. All prediction data is baked into the APK

## How it works

Classic harmonic tide prediction, as used in XTide since 1997:

```
H(t) = datum + Σ [ amplitude × node_factor(year) × cos( speed × t + eq_arg(year) − kappa ) ]
```

37 tidal constituents measured at WSBS feed into the sum. Astronomical tables from XTide's `congen`
tool cover 1700–2100, handling the 18.6-year nodal cycle that would otherwise let predictions drift
by several hours after a decade. This was fixed. Eventually.

## Data

- **Station harmonics**: measured at WSBS in 2004. If the sea has revised its opinion since then,
  the predictions will not reflect that.
- **Astronomical tables**: `congen_output.txt` from XTide — authoritative, deterministic,
  indifferent to when the measurements were taken.

## Data pipeline

The JSON bundled into the APK is produced by a one-time desktop script:

```sh
python tools/gen_tide_json.py
```

Run it after updating `wsbs-station.txt` with new station measurements, or after swapping in a
newer `congen_output.txt`. The Android app never runs Python.

## Layout

```
app/src/main/assets/
  wsbs-station.txt       Station harmonics — datum, timezone, 37 constituents
  congen_output.txt      XTide astronomical tables 1700–2100
  wsbs-tide-data.json    Merged output. Edit the sources, not this file.
tools/
  gen_tide_json.py       Data preparation script (run on PC, not on phone)
Legacy_WSBSTide/         Original WXTide32 C source, kept for reference
```

## Credits

*Ancient tidal wisdom, still current.*

**Algorithm**: [XTide](https://flaterco.com/xtide/) by David Flater (© 1997), with contributions
from Dale DePriest, Dean Pentcheff, Jeff Dairiki, Jef Poskanzer, Rob Miracle, Geoff Kuenning,
Jack Greenbaum, Stan Uno, Andrew Davidson, Karl Hahn, Bob Kenney, Alex Jones, Greg Seidman, and
C. Jeffery Small, who wrote the original manual page and therefore shares some of the blame.

**Windows port**: WXTide32 (© 1998–2007) by Michael Hopper, built on WinTide (1996) by Paul Roberts —
whose help file Mike "shamelessly lifted," per his own readme.

**libtcd binary format**: Jan Depner, originally written for the U.S. Naval Oceanographic Office,
which is a very authoritative reason to write a file format.

**Subordinate station format**: Hans Pieper (DOS Tide 2.4).

**Station harmonics**: White Sea Biological Station (ББС МГУ), 2004.

These people wrote their code before Android existed. Some of it before Google existed.
We stand on the shoulders of giants who were perfectly happy writing C.
