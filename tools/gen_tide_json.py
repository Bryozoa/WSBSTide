#!/usr/bin/env python3
"""
WHAT THIS IS
------------
A one-time data preparation tool, not part of the Android app.
Run it on your PC whenever the tide input data changes.
The app itself never runs this script — it only reads the JSON this script produces.

WHAT IT DOES
------------
Combines two source files into wsbs-tide-data.json:

  1. app/src/main/assets/wsbs-station.txt
     Station-specific measurements: datum, timezone, and the 37 harmonic constituents
     (amplitude and Greenwich phase lag / kappa) for White Sea Biological Station.
     Update this file when you receive fresh measurements from the station.

  2. app/src/main/assets/congen_output.txt
     Modern XTide astronomical tables covering years 1700-2100.
     Contains constituent speeds, equilibrium arguments, and node factors.
     These correct the 18.6-year nodal cycle drift that made the old prediction
     drift by several hours after a few years.

Output:
  app/src/main/assets/wsbs-tide-data.json
  Checked into the repo and baked into the app at build time.

WHEN TO RUN IT
--------------
- After updating wsbs-station.txt with new station measurements.
- After replacing congen_output.txt with a newer XTide release.

HOW TO RUN
----------
From the project root:
  python tools/gen_tide_json.py
"""

import json
import math
import os
import sys

STATION_FILE = "app/src/main/assets/wsbs-station.txt"
CONGEN_FILE  = "app/src/main/assets/congen_output.txt"
OUTPUT_FILE  = "app/src/main/assets/wsbs-tide-data.json"

# Year range to include in the output JSON.
# congen tables start at 1700, so index 0 = year 1700.
FIRST_YEAR = 1970
LAST_YEAR  = 2100
CONGEN_START_YEAR = 1700
IDX_START  = FIRST_YEAR - CONGEN_START_YEAR   # 270
IDX_END    = LAST_YEAR  - CONGEN_START_YEAR   # 400 (inclusive)
N_YEARS    = IDX_END - IDX_START + 1          # 131

# harm_msc uses LAMBDA2; congen_output.txt uses LDA2 for the same constituent.
STATION_TO_CONGEN = {"LAMBDA2": "LDA2"}


# ── Parse station file ────────────────────────────────────────────────────────

def parse_station(path):
    meta = {}
    constituents = []
    with open(path, encoding="utf-8") as f:
        for raw in f:
            line = raw.strip()
            if not line or line.startswith("#"):
                continue
            if ":" in line:
                key, _, val = line.partition(":")
                meta[key.strip()] = val.strip()
            else:
                parts = line.split()
                if len(parts) == 3:
                    name, amp_s, kappa_s = parts
                    amp   = float(amp_s)
                    kappa = float(kappa_s) % 360.0   # normalize (handles L2's 1966.7°)
                    constituents.append({"name": name, "amplitude_m": amp, "kappa_deg": kappa})
    return meta, constituents


# ── Parse congen_output.txt ───────────────────────────────────────────────────

def parse_congen(path):
    """
    Returns:
      speeds_by_name    : {name: speed_deg_per_hr}
      eq_args_by_name   : {name: [401 floats]}   — degrees at Greenwich, start of year
      node_factors_by_name: {name: [401 floats]} — for middle of each year
    """
    with open(path, encoding="utf-8") as f:
        lines = f.readlines()

    # ── Section 1: speeds (lines after the "176" count line, before the lone "#") ──
    # Format: "NAME  speed_deg_per_hr"
    # Comment lines between "176" and the first entry are skipped;
    # the lone "#" separator after the last entry terminates the section.
    speeds = {}
    in_speeds = False
    for line in lines:
        s = line.strip()
        if s == "176":
            in_speeds = True
            continue
        if in_speeds:
            if s.startswith("#"):
                if speeds:   # section separator only meaningful after first entry
                    break
                continue     # skip leading comments before first entry
            parts = s.split()
            if len(parts) == 2:
                try:
                    speeds[parts[0]] = float(parts[1])
                except ValueError:
                    pass

    # ── Sections 2 & 3: eq-args then node-factors ──
    # Each section starts with a "401" line, then constituent blocks,
    # then "*END*". Two such sections exist in the file.
    tables = []           # will hold [eq_args_dict, node_factors_dict]
    i = 0
    while i < len(lines) and len(tables) < 2:
        s = lines[i].strip()
        if s == "401":
            i += 1
            current_table = {}
            current_name  = None
            current_vals  = []
            while i < len(lines):
                s = lines[i].strip()
                i += 1
                if s == "*END*":
                    if current_name:
                        current_table[current_name] = current_vals
                    tables.append(current_table)
                    break
                if not s or s.startswith("#"):
                    continue
                # Is this line a constituent name or data values?
                parts = s.split()
                try:
                    float(parts[0])   # data line
                    if current_name is not None:
                        current_vals.extend(float(x) for x in parts)
                except ValueError:
                    # It's a name line — save previous constituent and start new
                    if current_name:
                        current_table[current_name] = current_vals
                    current_name = parts[0]
                    current_vals = []
        else:
            i += 1

    if len(tables) < 2:
        sys.exit(f"ERROR: expected 2 yearly tables in {path}, found {len(tables)}")

    eq_args, node_factors = tables[0], tables[1]

    # Validate
    for name, vals in eq_args.items():
        if len(vals) != 401:
            print(f"WARNING: eq_args[{name}] has {len(vals)} values (expected 401)")

    return speeds, eq_args, node_factors


# ── Main ──────────────────────────────────────────────────────────────────────

def main():
    for f in (STATION_FILE, CONGEN_FILE):
        if not os.path.isfile(f):
            sys.exit(f"ERROR: file not found: {f}")

    print(f"Parsing {STATION_FILE} ...")
    meta, station_constituents = parse_station(STATION_FILE)

    print(f"Parsing {CONGEN_FILE} ...")
    speeds, eq_args, node_factors = parse_congen(CONGEN_FILE)

    print(f"Building JSON for {FIRST_YEAR}-{LAST_YEAR} ({N_YEARS} years) ...")

    output_constituents = []
    missing = []
    for sc in station_constituents:
        sname    = sc["name"]
        cname    = STATION_TO_CONGEN.get(sname, sname)   # e.g. LAMBDA2 → LDA2

        if cname not in speeds:
            missing.append(sname)
            continue
        if cname not in eq_args:
            missing.append(sname)
            continue
        if cname not in node_factors:
            missing.append(sname)
            continue

        speed_deg_hr = speeds[cname]
        speed_rad_s  = speed_deg_hr * math.pi / 180.0 / 3600.0

        amp_m         = sc["amplitude_m"]
        kappa_rad     = math.radians(sc["kappa_deg"])

        # Slice the 401-year arrays down to FIRST_YEAR..LAST_YEAR
        full_eq   = eq_args[cname]
        full_nf   = node_factors[cname]
        eq_slice  = [math.radians(v) for v in full_eq[IDX_START : IDX_END + 1]]
        nf_slice  = full_nf[IDX_START : IDX_END + 1]

        if len(eq_slice) != N_YEARS or len(nf_slice) != N_YEARS:
            print(f"WARNING: {sname}/{cname}: slice length mismatch "
                  f"eq={len(eq_slice)} nf={len(nf_slice)} expected={N_YEARS}")

        output_constituents.append({
            "name":                         sname,
            "speedRadiansPerSecond":        speed_rad_s,
            "stationAmplitude":             amp_m,
            "stationPhaseRadians":          kappa_rad,
            "nodeFactors":                  nf_slice,
            "equilibriumArgumentsRadians":  eq_slice,
        })

    if missing:
        print(f"WARNING: constituents not found in congen: {missing}")

    output = {
        "station": {
            "name":             meta.get("name", "WhiteSeaBioStation"),
            "units":            meta.get("units", "meters"),
            "datum":            float(meta.get("datum_m", "1.17")),
            "meridianSeconds":  int(meta.get("meridianSeconds", "10800")),
            "displayTimeZone":  meta.get("timezone", "Europe/Moscow"),
            "firstYear":        FIRST_YEAR,
            "numberOfYears":    N_YEARS,
        },
        "constituents": output_constituents,
    }

    with open(OUTPUT_FILE, "w", encoding="utf-8") as f:
        json.dump(output, f, indent=2, ensure_ascii=False)

    print(f"Written: {OUTPUT_FILE}")
    print(f"  station: {output['station']['name']}")
    print(f"  constituents: {len(output_constituents)}")
    print(f"  years: {FIRST_YEAR}-{LAST_YEAR} ({N_YEARS} years)")
    print(f"  node-factor array length check: {len(output_constituents[0]['nodeFactors'])} (expect {N_YEARS})")


if __name__ == "__main__":
    main()
