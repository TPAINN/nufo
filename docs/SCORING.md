# Nufo Score

A 0–100 number with every point explained. The rules live in
`android/app/src/main/java/com/nufo/app/data/Scoring.kt` and are pinned by `ScoringTest.kt`.

## Evidence first

A product is scored only when it has a Nutri-Score grade, or at least four of: energy, fat,
saturated fat, sugar, salt (or sodium), protein. Otherwise the app shows "Not enough data to score".
A NOVA group alone is not enough: it says how processed a food is, not how nutritious.

## With a Nutri-Score grade

The grade is the base. It already weighs energy, sugar, saturated fat, salt, fibre, protein and
fruit/vegetables/legumes, so those nutrients are **not** counted again.

| Grade | A | B | C | D | E |
|---|---|---|---|---|---|
| Score | 100 | 85 | 65 | 45 | 25 |

## Without a grade

Start at 100 and judge the nutrients on the UK FSA front-of-pack table, the same table that
colours the dots on the nutrition declaration. Drinks (`en:beverages`, `en:drinks`, `*-beverages`
categories) use the FSA drink thresholds, which are half the food ones, per 100 ml.

| Per 100 g | Medium (amber) | High (red) |
|---|---|---|
| Sugar | −5 | −20 |
| Salt | −5 | −20 |
| Saturated fat | −5 | −20 |
| Fat | — | −10 |
| Protein ≥ 10 g | +5 | |
| Fibre ≥ 6 g (EU "high fibre") | +5 | |

## Processing (both cases)

| NOVA | 1 | 2 | 3 | 4 |
|---|---|---|---|---|
| Adjustment | 0 | −5 | −10 | −15 |

NOVA never moves a product past the next grade down: a grade A ultra-processed product (85)
still ties with a grade B unprocessed one (85).

The result is clamped to 0–100. Verdicts: 80+ excellent, 60+ good, 40+ fair, 20+ poor, else very poor.

## Always computed live

The score is recomputed from the stored facts every time it is shown. Products saved under older
rules follow the current rules, and the reason chips always add up to the number on screen.
