"""Extract W8 Shipping towing-rate tables (Copart / IAAI / Manheim / ADESA PDFs) into raw rows.

Rows are delimited by the horizontal rules drawn between table rows; the location name sits in
the left column (x < 210pt), prices in six columns by x position. Output: raw.csv with
auction, raw_name, SAVANNAH, LOS_ANGELES, NEW_YORK, HOUSTON, SEATTLE, NORFOLK ('' = no rate).
"""
import csv
import sys

import pdfplumber

COLS = [  # (column key, x-start) — header x0: Savannah 220, CA 305, NY 353, TX 409, WA 464, VA 519
    ("SAVANNAH", 205),
    ("LOS_ANGELES", 290),
    ("NEW_YORK", 340),
    ("HOUSTON", 395),
    ("SEATTLE", 450),
    ("NORFOLK", 505),
]
NAME_MAX_X = 205


def col_for(x0):
    key = None
    for k, start in COLS:
        if x0 >= start:
            key = k
    return key


def parse(path, auction):
    rows = []
    with pdfplumber.open(path) as pdf:
        for pg in pdf.pages:
            hs = sorted({round(l["top"], 1) for l in pg.lines if abs(l["top"] - l["bottom"]) < 1})
            words = pg.extract_words(keep_blank_chars=False, x_tolerance=1.5)
            if not hs:
                continue
            # Some pages draw no rule under their last row: read one more band below the last line.
            hs = hs + [hs[-1] + 45]
            for top, bottom in zip(hs, hs[1:]):
                band = [w for w in words if w["top"] >= top - 0.5 and w["bottom"] <= bottom + 0.5]
                if not band:
                    continue
                name_words = sorted((w for w in band if w["x0"] < NAME_MAX_X), key=lambda w: (round(w["top"]), w["x0"]))
                name = " ".join(w["text"] for w in name_words).strip()
                if name == "City":
                    continue  # header row
                vals = {k: "" for k, _ in COLS}
                for w in band:
                    if w["x0"] >= NAME_MAX_X:
                        k = col_for(w["x0"])
                        t = w["text"].strip()
                        if t in ("-", "–", "—"):
                            continue
                        vals[k] = (vals[k] + t) if vals[k] else t
                rows.append({"auction": auction, "raw_name": name, **vals})
    return rows


if __name__ == "__main__":
    out = sys.argv[1]
    files = sys.argv[2:]  # pairs: AUCTION=path
    all_rows = []
    for spec in files:
        auction, path = spec.split("=", 1)
        r = parse(path, auction)
        print(f"{auction}: {len(r)} rows", file=sys.stderr)
        all_rows.extend(r)
    with open(out, "w", newline="", encoding="utf-8") as f:
        wr = csv.DictWriter(f, fieldnames=["auction", "raw_name"] + [k for k, _ in COLS])
        wr.writeheader()
        wr.writerows(all_rows)
