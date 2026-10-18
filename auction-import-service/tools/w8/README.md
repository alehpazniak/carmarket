# W8 Shipping towing price list

Turns W8 Shipping's yearly towing-rate PDFs (Copart / IAAI / Manheim / ADESA → W8 warehouse, USD per
vehicle) into the CSV and Flyway migration that seed `w8_towing_rate`.

```bash
pip install pdfplumber

# 1. PDFs → raw rows (one AUCTION=path pair per PDF)
python extract_towing.py raw.csv COPART=copart.pdf IAAI=iaai.pdf MANHEIM=manheim.pdf ADESA=adesa.pdf

# 2. raw rows → w8-towing-rates.csv + V5__w8_towing_rates.sql in the output directory
python normalize_towing.py raw.csv out/
```

For a new price list:

1. In `normalize_towing.py`, set `PRICE_LIST` (e.g. `W8-2026-27`) and change the output file name to the
   next free migration number, e.g. `V<N>__w8_towing_rates_2026_27.sql`. It must not create the table
   again: keep only the `INSERT`.
2. Read the script's stderr report: names it couldn't parse stop the run (add them to `SPECIAL` or
   `TYPOS`), and it lists the rows it dropped (no price, exact duplicate).
3. Put the migration in `src/main/resources/db/migration/` and the CSV in `src/main/resources/shipping/`.
   Never edit a migration that has already run.
4. Set `import.w8.price-list` in `application.yml` to the new value. Old rows stay in the table for history.
5. Run `mvn -pl auction-import-service verify`. `W8LocationKeyTest` checks that every CSV `yard_key`
   matches `W8LocationKey.normalize()`, so the Java and Python normalization can't drift apart. Update the
   row counts in that test and in `W8TowingRateRepositoryTest`.
