# Nufo shared API contract

Both apps implement exactly this contract. Parsers and scoring are mirrored line for line
(`android/app/src/main/java/com/nufo/app/data/` and `ios/Nufo/Data/`) and are tested against the
same real API responses (`android/app/src/test/resources/`, `ios/NufoTests/Fixtures/`).

## Rules that apply everywhere

- **Never invent data.** A nutrient the source doesn't report stays `null` and renders as "Not available".
  `0` is only shown when the source says `0`.
- Every request sends `User-Agent: Nufo/1.0 (contact@nufo.app)` (Open Food Facts requires it).
- Only a barcode or the user's search text ever leaves the device. No account, no identifiers.
- Amounts are stored in **grams per 100 g**; the UI formats g / mg / µg.

## Barcode lookup

1. `GET https://world.openfoodfacts.org/api/v2/product/{barcode}.json?fields={FIELDS}`
   - `status != 1` means not found.
2. If not found: `GET https://api.nal.usda.gov/fdc/v1/foods/search?api_key={KEY}&query={barcode}&pageSize=5`
   and accept a food whose `gtinUpc` equals the barcode (leading zeros ignored).
3. If still not found: `GET https://api.upcitemdb.com/prod/trial/lookup?upc={barcode}` (8–14 digits only).
   A hit gives name, brand and an `https` image only — shown as "Identified, but no nutrition data" with a
   search-by-name button. Otherwise "Product not found".
4. Network failure: answer from the on-device product cache (with the date it was saved), else history,
   else an offline error. A server error (non-2xx) is reported as the database being down, not the user.

## Prices

`GET https://prices.openfoodfacts.org/api/v1/prices?product_code={barcode}&order_by=-date&size=20`. Keep
reports with `price > 0`, `currency` and `date`; store is `location.osm_name`, city `location.osm_address_city`,
country `location.osm_address_country`. The API ignores country filters, so Greek stores are sorted first
client-side and the latest three are shown.

## Product photos

`image_url` ends in `.400.jpg`. Open Food Facts also serves `.100`, `.200` and `.full`. Thumbnails use `.400`;
the product page loads `.full` over it. Non-OFF URLs are used as-is.

`FIELDS = code,product_name,product_name_el,product_name_en,brands,quantity,serving_size,serving_quantity,nutriments,nutriscore_grade,nova_group,ecoscore_grade,ingredients_text,ingredients_text_el,ingredients_text_en,allergens_tags,additives_tags,image_url,categories_tags,labels_tags,countries_tags,completeness,last_modified_t`

`quantity` loses a trailing EU "℮" estimated-quantity mark (often stored as " e").

## Search

Greek handling (see GreekSearch.kt / GreekSearch.swift): the query is expanded to up to three variants — as typed,
without accents, and English via the Greek food dictionary when every Greek word is known. Each variant runs with
`countries_tags:"en:greece"`; one worldwide query runs alongside. Results are merged Greek-first, then whole-word
matches before stemmed ones, then entries with a photo, calories and a Nutri-Score before sparse duplicates.
Search requests send `langs=el,en`. When every source fails for lack of a connection, search runs over saved
and cached products instead (all query words must appear in name or brand, accents ignored).

Run both in parallel; if one fails the other's results still show.

- **Open Food Facts (Search-a-licious)** — `GET https://search.openfoodfacts.org/search?q={q}&page_size=20&fields=code,product_name,brands,image_url,nutriscore_grade,nova_group,nutriments`
  - Results are in `hits`; `brands` is an array.
  - User text is stripped of Lucene syntax `+ - & | ! ( ) { } [ ] ^ " ~ * ? : \ /` (a stray `:` makes the server return 500).
  - Zero hits: retry once with fuzzy brand matching, `(brands:{word}~2 OR ...)` for words of 3+ letters.
    This rescues OCR typos ("nutelld" finds Nutella).
  - Filters are appended as Lucene clauses:
    `categories_tags:"en:dairies"`, `labels_tags:"en:vegan"` / `"en:vegetarian"`, `-allergens_tags:"en:milk"`.
  - The legacy `cgi/search.pl` endpoint is not used: it returned HTTP 503 to the app during testing.
- **USDA** — `GET https://api.nal.usda.gov/fdc/v1/foods/search?api_key={KEY}&query={q}&pageSize=15`
  - Hidden while any Open Food Facts tag filter (category, diet, allergens) is active, since USDA can't apply them.
- Nutri-Score and NOVA filters apply client-side to both sources.

## Field mapping

| Nufo | Open Food Facts | USDA (nutrient number, unit) |
|---|---|---|
| calories (kcal) | `energy-kcal_100g`, else `energy_100g` ÷ 4.184; values above 900 kcal/100 g are rejected | 208 kcal (fallback 957, 958) |
| protein | `proteins_100g` | 203 g |
| carbs | `carbohydrates_100g` | 205 g |
| fat | `fat_100g` | 204 g |
| saturatedFat | `saturated-fat_100g` | 606 g |
| fiber | `fiber_100g` | 291 g |
| sugar | `sugars_100g` | 269 g |
| sodium (g) | `sodium_100g` | 307 mg ÷ 1000 |
| salt (g) | `salt_100g` | sodium × 2.5 (EU labelling convention) |
| vitamins / minerals | `vitamin-*_100g`, `calcium_100g`, `iron_100g`, … | 320, 401, 328, 323, 415, 418 / 301, 303, 304, 306, 309 |
| per serving | `*_serving`, else per-100 g × `serving_quantity` / 100 | per-100 g × `servingSize` / 100 when unit is g |
| nutriscoreGrade | `nutriscore_grade` (only `a`–`e`; `unknown` → null) | — |
| novaGroup | `nova_group` (only 1–4) | — |
| ecoscoreGrade | `ecoscore_grade` (only `a`–`e`; `unknown` → null) | — |
| ingredientsText | `ingredients_text` with `_` allergen markers removed | `ingredients` |
| allergens / additives / labels | `*_tags`, `en:sesame-seeds` → "Sesame seeds" | — |
| lastUpdated | `last_modified_t` × 1000 | `publishedDate` |

## Nufo Score (0–100)

Start at 100, apply every rule that has data, clamp to 0–100. Each applied rule is shown as a reason.

| Rule | Δ |
|---|---|
| Nutri-Score A / B / C / D / E | 0 / −5 / −15 / −30 / −45 |
| NOVA 1 / 2 / 3 / 4 | 0 / −5 / −15 / −30 |
| protein ≥ 10 g | +10 |
| fiber ≥ 5 g | +10 |
| sugar > 10 g | −15 |
| salt > 1 g | −15 |
| saturated fat > 5 g | −10 |

Official Nutri-Score, NOVA and Eco-Score are always shown as-is when the source provides them.

## Attribution

- Open Food Facts data is © Open Food Facts contributors, under the Open Database License (ODbL).
- USDA FoodData Central data is public domain.
- The result screen always shows the source and last-updated date.

## Localisation

- Product fields: ``product_name_{lang}`` and ``ingredients_text_{lang}`` are preferred, then the main-language field.
- Tags (allergens, labels, last 4 categories, additives) are translated with
  ``GET /api/v2/taxonomy?tagtype={type}&tags={a,b}&lc={lang}&fields=name``; untranslated tags fall back to
  the built-in Greek/English table, then to a prettified id. Categories without a translation are hidden.
- ``completeness`` (0–1) and ``countries_tags`` (for "Sold in Greece") are requested with every product.