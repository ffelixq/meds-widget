from pathlib import Path

path = Path("app/src/main/java/io/github/ffelixq/medswidget/widget/WidgetSnapshot.kt")
text = path.read_text()

old_int = '''    private fun JSONObject.optNullableInt(key: String): Int? = if (isNull(key) || !has(key)) null else optInt(key)\n'''
new_int = '''    private fun JSONObject.optNullableInt(key: String): Int? =\n        if (isNull(key) || !has(key)) null else optInt(key)\n'''
if old_int not in text:
    raise SystemExit("optNullableInt pattern not found")
text = text.replace(old_int, new_int, 1)

old_double = '''\n    private fun JSONObject.optNullableDouble(key: String): Double? = if (isNull(key) || !has(key)) null else optDouble(key)\n'''
if old_double not in text:
    raise SystemExit("optNullableDouble pattern not found")
text = text.replace(old_double, "", 1)

text = text.rstrip() + '''\n\nprivate fun JSONObject.optNullableDouble(key: String): Double? =\n    if (isNull(key) || !has(key)) null else optDouble(key)\n'''
path.write_text(text)
