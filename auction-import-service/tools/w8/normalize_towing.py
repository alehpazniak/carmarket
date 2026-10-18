"""Turn raw.csv (from extract_towing.py) into the clean W8 towing price list.

Outputs:
  w8-towing-rates.csv            wide, human-editable: one row per auction yard/sublot, one column per W8 port
  V5__w8_towing_rates.sql        Flyway migration: table + seed rows (long format, one row per yard/port)

yard_key MUST be computed exactly like W8LocationKey.normalize() in Java (the prompt specifies it):
  upper-case -> en/em dash to '-' -> drop apostrophes -> every non [A-Z0-9] char to a space ->
  split on whitespace -> map tokens FT->FORT, ST->SAINT, SO->SOUTH -> join with one space.
"""
import csv
import re
import sys

PORTS = ["SAVANNAH", "LOS_ANGELES", "NEW_YORK", "HOUSTON", "SEATTLE", "NORFOLK"]
PRICE_LIST = "W8-2025-26"
TOKEN_MAP = {"FT": "FORT", "ST": "SAINT", "SO": "SOUTH"}

# Names W8 wrote without a parseable "NAME - ST" shape. Value: (yard, state, sublot).
# A state is only filled where the PDF itself says it elsewhere or the yard name is unambiguous;
# otherwise it stays None and matching falls back to the yard name alone.
SPECIAL = {
    "CRASHEDTOYS ATLANTA": ("CRASHEDTOYS ATLANTA", "GA", None),
    "KANSAS CITY EAST": ("KANSAS CITY EAST", None, None),
    "NORTH CHARLESTON": ("NORTH CHARLESTON", "SC", None),
    "PHILADELPHIA EAST": ("PHILADELPHIA EAST", "PA", None),
    "PHILADELPHIA EAST - SUBLOT - PA": ("PHILADELPHIA EAST", "PA", "Sublot"),
    "AUSTIN - TX / WACO - TX (Taylor Sublot)": ("AUSTIN / WACO", "TX", "Taylor Sublot"),
    "WEST WARREN (MA) (Devil's Bowl Raceway)": ("WEST WARREN", "MA", "Devil's Bowl Raceway"),
    "WEST WARREN (MA) (Stagecoach Sublot)": ("WEST WARREN", "MA", "Stagecoach Sublot"),
    "ACE-PERRIS": ("ACE PERRIS", "CA", None),  # IAAI ACE Perris, CA (sibling row "ACE - PERRIS 2 - CA")
    "HOUSTON SOUTH": ("HOUSTON SOUTH", "TX", None),  # duplicate of "HOUSTON SOUTH - TX", same price
    "NEW ORLEANS AND LOUISIANA FLOOD": ("NEW ORLEANS AND LOUISIANA FLOOD", "LA", None),
    "MANHEIM ATLANTA": ("MANHEIM ATLANTA", "GA", None),
    "MANHEIM METRO ATLANTA": ("MANHEIM METRO ATLANTA", "GA", None),
    "MANHEIM SEATTLE": ("MANHEIM SEATTLE", "WA", None),
    "MANHEIM BALTIMORE- WASHINGTON - M": ("MANHEIM BALTIMORE-WASHINGTON", "MD", None),  # truncated in the PDF
    "MANHEIM KEYSTONE PENNSYLVANIA -": ("MANHEIM KEYSTONE PENNSYLVANIA", "PA", None),  # truncated in the PDF
    "PARK PLACE AUTO AUCTION POWERED BY MANHEIM": ("PARK PLACE AUTO AUCTION", None, None),
    "ADESA Brashers": ("ADESA BRASHERS", None, None),
}

# Spelling fixes (applied to the yard after parsing).
TYPOS = {
    "SEATLE": "SEATTLE",
    "SEATLE SOUTH": "SEATTLE SOUTH",
    "ACRON CANTON": "AKRON CANTON",
    "PITTSBURG SOUTH": "PITTSBURGH SOUTH",
    "FT.PIERCE": "FT. PIERCE",
    "FT.WORTH": "FT. WORTH",
}

SHAPE = re.compile(r"^(?P<yard>.+?)\s*-\s*(?P<state>[A-Z]{2})\s*(?:\((?P<sub>.+)\))?$")


def yard_key(s):
    s = s.upper().replace("–", "-").replace("—", "-").replace("'", "").replace("’", "")
    s = re.sub(r"[^A-Z0-9]", " ", s)
    return " ".join(TOKEN_MAP.get(t, t) for t in s.split())


def parse_name(raw):
    raw = re.sub(r"\s+", " ", raw.replace("–", "-").replace("—", "-")).strip()
    if raw in SPECIAL:
        return SPECIAL[raw]
    m = SHAPE.match(raw)
    if not m:
        raise ValueError(f"unparsed location name: {raw!r}")
    yard = m.group("yard").strip().upper()
    yard = re.sub(r"\s*-\s*$", "", yard)
    yard = TYPOS.get(yard, yard)
    return yard, m.group("state"), (m.group("sub") or None)


def display(yard, state, sublot):
    s = yard + (f" - {state}" if state else "")
    return s + (f" ({sublot})" if sublot else "")


def sql_str(v):
    return "NULL" if v is None else "'" + v.replace("'", "''") + "'"


def main(raw_csv, out_dir):
    rows = list(csv.DictReader(open(raw_csv, encoding="utf-8")))
    clean, seen, dropped_empty, dup = [], {}, [], []
    for r in rows:
        prices = {p: r[p] for p in PORTS}
        if not any(prices.values()):
            dropped_empty.append(f"{r['auction']}: {r['raw_name']}")
            continue
        yard, state, sublot = parse_name(r["raw_name"])
        k = (r["auction"], yard_key(yard), state or "", (sublot or "").upper())
        if k in seen:
            if seen[k]["prices"] != prices:
                raise ValueError(f"conflicting duplicate {k}: {seen[k]['prices']} vs {prices}")
            dup.append(f"{r['auction']}: {r['raw_name']} (same as {seen[k]['raw']})")
            continue
        item = {"auction": r["auction"], "yard": yard, "state": state, "sublot": sublot,
                "key": yard_key(yard), "prices": prices, "raw": r["raw_name"]}
        seen[k] = item
        clean.append(item)

    with open(f"{out_dir}/w8-towing-rates.csv", "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["auction", "location", "yard", "state", "sublot", "yard_key"] + [p.lower() for p in PORTS])
        for c in clean:
            w.writerow([c["auction"], display(c["yard"], c["state"], c["sublot"]), c["yard"], c["state"] or "",
                        c["sublot"] or "", c["key"]] + [c["prices"][p] for p in PORTS])

    values = []
    for c in clean:
        for p in PORTS:
            if c["prices"][p]:
                values.append("    ({}, {}, {}, {}, {}, {}, {}, {}, {})".format(
                    sql_str(PRICE_LIST), sql_str(c["auction"]), sql_str(display(c["yard"], c["state"], c["sublot"])),
                    sql_str(c["yard"]), sql_str(c["state"]), sql_str(c["sublot"]), sql_str(c["key"]),
                    sql_str(p), c["prices"][p]))

    header = f"""-- W8 Shipping towing rates: US auction yard -> W8 warehouse/port, USD per vehicle.
-- Source: W8 Shipping PDFs "Copart / IAAI / Manheim / ADESA towing rates" (price list {PRICE_LIST}).
-- Generated from w8-towing-rates.csv (one row per yard and port). Yards W8 lists without any price
-- (e.g. HONOLULU - HI, TORONTO CANADA, WEBSTER - NH) are left out: no row = no W8 towing offer.
-- A new W8 price list goes into a NEW migration with a new price_list value; never edit this file.
--
-- yard_key is normalized exactly like W8LocationKey.normalize(): upper-case, dashes/punctuation to
-- spaces, apostrophes dropped, tokens FT->FORT, ST->SAINT, SO->SOUTH, single spaces.

CREATE TABLE w8_towing_rate (
    id          BIGSERIAL     PRIMARY KEY,
    price_list  VARCHAR(20)   NOT NULL,
    auction     VARCHAR(10)   NOT NULL CHECK (auction IN ('COPART', 'IAAI', 'MANHEIM', 'ADESA')),
    location    VARCHAR(120)  NOT NULL,
    yard        VARCHAR(80)   NOT NULL,
    state       CHAR(2),
    sublot      VARCHAR(80),
    yard_key    VARCHAR(80)   NOT NULL,
    port        VARCHAR(12)   NOT NULL CHECK (port IN ('SAVANNAH', 'LOS_ANGELES', 'NEW_YORK', 'HOUSTON', 'SEATTLE', 'NORFOLK')),
    price_usd   NUMERIC(8, 2) NOT NULL CHECK (price_usd > 0),
    created_at  TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX ux_w8_towing_rate
    ON w8_towing_rate (price_list, auction, yard_key, COALESCE(state, ''), COALESCE(UPPER(sublot), ''), port);
CREATE INDEX ix_w8_towing_rate_lookup ON w8_towing_rate (price_list, yard_key, state);

INSERT INTO w8_towing_rate (price_list, auction, location, yard, state, sublot, yard_key, port, price_usd) VALUES
"""
    with open(f"{out_dir}/V5__w8_towing_rates.sql", "w", encoding="utf-8") as f:
        f.write(header + ",\n".join(values) + ";\n")

    print(f"yards/sublots: {len(clean)}, price rows: {len(values)}", file=sys.stderr)
    print(f"dropped (no price): {len(dropped_empty)}", *dropped_empty, sep="\n  ", file=sys.stderr)
    print(f"dropped (exact duplicate): {len(dup)}", *dup, sep="\n  ", file=sys.stderr)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
