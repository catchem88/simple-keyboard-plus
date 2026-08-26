# Style

Java only. Two conventions coexist in this repo:

- **Inherited AOSP code** (everything from upstream) uses AOSP style: `if (cond) {`, a space after
  every comma, Javadoc `/** */`, `// comment` with a space.
- **Code written for this fork** follows the rules below.

When editing an existing method, keep the surrounding lines as they are and write only the new lines
in fork style. Never reformat inherited code just to match.

## Fork style rules

### Control structures

No space between the keyword and the opening parenthesis. Opening brace on the same line.

```java
if(autoText.isEmpty()) {
    return false;
}
```

Braces are never omitted, even for a single statement.

`else` and `else if` go on a new line after the closing brace. Use `else if`, never a nested
one-liner.

```java
if(keyword == null) {
    titleRes = R.string.auto_text_add;
}
else if(expansion == null) {
    titleRes = R.string.auto_text_edit;
}
else {
    titleRes = R.string.auto_text_view;
}
```

### Methods

No space between the method name and the parenthesis. No space after commas in the parameter list or
in call arguments. Opening brace on the same line.

```java
public boolean textBeforeCursorEndsWith(final String text,final int length) {
    return mTextBeforeCursor.regionMatches(offset,text,0,length);
}
```

Wrapped parameter lists are indented by 8 spaces, matching the existing code:

```java
private boolean performAutoTextExpansion(final InputTransaction inputTransaction,
        final int codePoint) {
```

### Operators

Single space on both sides of assignment and comparison operators.

```java
final int offset = length - 1;
if(triggers[index] != codePoint) {
```

### No ternaries

Use `if` / `else` and assign to a `final` local.

### Comments

- Single line: `//` with no space after the slashes.
- Multi line: plain `/* */`, no leading `*` on continuation lines, no Javadoc decoration.

```java
/*
    Find the entry whose keyword ends with the given code point.
*/
```

- Never write comments that narrate the edit ("original code continues here", "changed this line").
  Comments describe the code as it stands.
- Use `-` in prose, never an em dash.

## Java conventions carried over from the existing code

- Indent 4 spaces, no tabs. Lines wrap at 100 columns.
- Fields are prefixed `m` (`mKeywords`), static finals are `UPPER_SNAKE_CASE`.
- Mark parameters and locals `final` wherever they are not reassigned. The existing code does this
  consistently.
- Classes that are not designed for extension are declared `final`.
- Prefer package-private or private over public. Only widen visibility when a caller needs it.
- Value classes are immutable: `final` fields set in the constructor, mutation returns a new instance
  (see `AutoText.withEntry`).
- Constants for preference keys live in `Settings.java` and the constant name mirrors the string value
  in upper snake case (`PREF_AUTO_TEXT` -> `"pref_auto_text"`).
- Anonymous inner classes for listeners (there is no lambda usage in the settings code; lambdas do
  appear in `RichInputConnection.reloadTextCache`, so either is acceptable in new code - prefer
  anonymous classes in settings fragments to match the neighbours).

## Resources

- New strings go in `app/src/main/res/values/strings.xml` only. Translated `values-*` folders are
  Crowdin-managed.
- Precede each translatable string with an XML comment, and add `[CHAR LIMIT=n]` for titles and
  buttons the way the existing entries do.
- Naming: screen titles `settings_screen_<name>`, dialog-preference titles `prefs_<thing>_settings`,
  summaries `<name>_summary`, buttons bare verbs (`add`, `remove`).
- Strings that are examples or identifiers get `translatable="false"`.
- Layout and menu files carry the Apache licence header like their neighbours.
- No hardcoded strings in layouts; no emoji anywhere unless asked for.
