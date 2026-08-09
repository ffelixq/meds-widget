from pathlib import Path

path = Path("app/src/main/java/io/github/ffelixq/medswidget/widget/WidgetSnapshot.kt")
text = path.read_text()

old_string = '''    private fun JSONObject.optNullableString(key: String): String? =
        if (isNull(key) || !has(key)) null else optString(key).takeIf(String::isNotBlank)

'''
old_int = '''    private fun JSONObject.optNullableInt(key: String): Int? = if (isNull(key) || !has(key)) null else optInt(key)

'''
if old_string not in text or old_int not in text:
    raise SystemExit("Expected codec helpers not found")

text = text.replace(old_string, "", 1).replace(old_int, "", 1)
append = '''

private fun JSONObject.optNullableString(key: String): String? =
    if (isNull(key) || !has(key)) null else optString(key).takeIf(String::isNotBlank)

private fun JSONObject.optNullableInt(key: String): Int? =
    if (isNull(key) || !has(key)) null else optInt(key)
'''
text = text.rstrip() + append + "\n"
path.write_text(text)
