# Independent spreadsheet fixtures

These are synthetic attachment files, never production data. They were exported by independent writers so compatibility tests do not depend only on the small JS fixture writer used elsewhere in this suite:

- `worksheet-openpyxl.xlsx`: openpyxl 3.1.5, normal ZIP/XML workbook, inline Unicode strings, numeric/date cells, booleans and sparse rows.
- `worksheet-xlwt.xls`: Xlwt 1.3.0, BIFF8 in an OLE container, custom date format, RK numeric cells, padded Workbook stream and a 9000-character Unicode shared string split across CONTINUE records.

The single worksheet is named `Actual writer`. Both files have these rows:

```python
['Name', 'Amount', 'When', 'Active']
['Dua & हैलो', 123.5, datetime(2026, 10, 15), True]
['line\nbreak', -42, '', False]
```

The XLS file additionally has `['continued text: ' + 'क' * 9000, 2.5, 'last SST string', '']`. The date cell uses `yyyy-mm-dd` (openpyxl) / `YYYY-MM-DD` (Xlwt). The Python tools were used only to generate these checked-in files; they are not runtime or CI dependencies.
