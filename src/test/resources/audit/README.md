# Audit fixtures

Frozen public Google Sheets downloads from 30 September 2026:
- Schedule, four courses: https://docs.google.com/spreadsheets/d/1IUU0k0st5qsU3sFBfl8wazscE7R6_XHSAlsdJCwUa_4/edit
- Changes for 1 October: https://docs.google.com/spreadsheets/d/1OpDaApQYxlwF9tYmzKbIr_zBt9AO-67aRqKKQXFyU7Q/edit

expected-schedule.json was independently extracted from workbook XML rather than from the Java parser. It covers 953 lesson records across 57 group headers. Changes cover 28 entries in 15 groups. Tests run offline.

Source ambiguity: course 1, AF22, 1-Ю3, Thursday slot 1 has two language teachers and only one room (36-о) for the blue week. Do not invent a subgroup mapping. Changes for 3-МР3 slots 3/4 and 3-ИС3 slot 1 lack room numbers.

с/з, с/л and ч/з are single room labels; 9/25 represents two numbered rooms. New source formats need additional fixtures. Failed XLSX refresh retains the in-memory cache; a cold start requires a successful download.
