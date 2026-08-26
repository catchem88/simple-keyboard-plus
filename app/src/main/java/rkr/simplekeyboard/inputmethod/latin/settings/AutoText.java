/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package rkr.simplekeyboard.inputmethod.latin.settings;

import java.util.Arrays;

import rkr.simplekeyboard.inputmethod.latin.RichInputConnection;

/*
    An immutable table of auto-text entries. Each entry maps a keyword to the text that replaces
    it as soon as the last character of the keyword is typed.

    The whole table is stored in a single string preference and parsed once whenever the settings
    change, so typing only ever walks the precomputed arrays. The trigger code point of every
    keyword is kept separately because comparing it rejects virtually every key press with a
    single int comparison and without allocating anything.
*/
public final class AutoText {
    /*
        Control characters that cannot be produced by the keyboard, used to keep serialization
        free of escaping.
    */
    private static final char ENTRY_SEPARATOR = '\u001E';
    private static final char FIELD_SEPARATOR = '\u001F';

    public static final int NOT_A_MATCH = -1;

    private static final String[] NO_ENTRIES = new String[0];

    public static final AutoText EMPTY = new AutoText(NO_ENTRIES,NO_ENTRIES);

    private final String[] mKeywords;
    private final String[] mExpansions;
    //Last code point of each keyword, the character that triggers the expansion
    private final int[] mTriggers;
    //Length in chars of each keyword without its trigger
    private final int[] mPrefixLengths;

    private AutoText(final String[] keywords,final String[] expansions) {
        mKeywords = keywords;
        mExpansions = expansions;
        final int count = keywords.length;
        mTriggers = new int[count];
        mPrefixLengths = new int[count];
        for(int index = 0; index < count; index++) {
            final String keyword = keywords[index];
            final int trigger = keyword.codePointBefore(keyword.length());
            mTriggers[index] = trigger;
            mPrefixLengths[index] = keyword.length() - Character.charCount(trigger);
        }
    }

    public int size() {
        return mKeywords.length;
    }

    public boolean isEmpty() {
        return mKeywords.length == 0;
    }

    public String getKeyword(final int index) {
        return mKeywords[index];
    }

    public String getExpansion(final int index) {
        return mExpansions[index];
    }

    /*
        Number of characters of the keyword that have already been typed when the trigger arrives,
        which is how many characters have to be removed before the expansion is committed.
    */
    public int getPrefixLength(final int index) {
        return mPrefixLengths[index];
    }

    /*
        Find the entry whose keyword ends with the given code point and whose remaining characters
        sit immediately before the cursor. Returns NOT_A_MATCH when nothing matches.
    */
    public int findMatch(final RichInputConnection connection,final int codePoint) {
        final int[] triggers = mTriggers;
        for(int index = 0; index < triggers.length; index++) {
            if(triggers[index] != codePoint) {
                continue;
            }
            if(connection.textBeforeCursorEndsWith(mKeywords[index],mPrefixLengths[index])) {
                return index;
            }
        }
        return NOT_A_MATCH;
    }

    public int indexOfKeyword(final String keyword) {
        for(int index = 0; index < mKeywords.length; index++) {
            if(mKeywords[index].equals(keyword)) {
                return index;
            }
        }
        return NOT_A_MATCH;
    }

    /*
        Return a copy of this table with the given entry added, replacing the expansion of any
        existing entry using the same keyword. Entries with an empty field are rejected and the
        table is returned unchanged.
    */
    public AutoText withEntry(final String keyword,final String expansion) {
        final String newKeyword = strip(keyword);
        final String newExpansion = strip(expansion);
        if(newKeyword.isEmpty() || newExpansion.isEmpty()) {
            return this;
        }

        final int existing = indexOfKeyword(newKeyword);
        if(existing != NOT_A_MATCH) {
            final String[] expansions = Arrays.copyOf(mExpansions,mExpansions.length);
            expansions[existing] = newExpansion;
            return new AutoText(mKeywords,expansions);
        }

        final int count = mKeywords.length;
        final String[] keywords = Arrays.copyOf(mKeywords,count + 1);
        final String[] expansions = Arrays.copyOf(mExpansions,count + 1);
        keywords[count] = newKeyword;
        expansions[count] = newExpansion;
        return new AutoText(keywords,expansions);
    }

    /*
        Return a copy of this table without the entry using the given keyword.
    */
    public AutoText withoutEntry(final String keyword) {
        final int existing = indexOfKeyword(keyword);
        if(existing == NOT_A_MATCH) {
            return this;
        }

        final int count = mKeywords.length - 1;
        if(count == 0) {
            return EMPTY;
        }
        final String[] keywords = new String[count];
        final String[] expansions = new String[count];
        int target = 0;
        for(int index = 0; index <= count; index++) {
            if(index == existing) {
                continue;
            }
            keywords[target] = mKeywords[index];
            expansions[target] = mExpansions[index];
            target++;
        }
        return new AutoText(keywords,expansions);
    }

    public String serialize() {
        final int count = mKeywords.length;
        if(count == 0) {
            return "";
        }
        final StringBuilder builder = new StringBuilder();
        for(int index = 0; index < count; index++) {
            if(index > 0) {
                builder.append(ENTRY_SEPARATOR);
            }
            builder.append(mKeywords[index]).append(FIELD_SEPARATOR).append(mExpansions[index]);
        }
        return builder.toString();
    }

    public static AutoText parse(final String value) {
        if(value == null || value.isEmpty()) {
            return EMPTY;
        }

        final String[] records = value.split(String.valueOf(ENTRY_SEPARATOR));
        final String[] keywords = new String[records.length];
        final String[] expansions = new String[records.length];
        int count = 0;
        for(final String record : records) {
            final int separator = record.indexOf(FIELD_SEPARATOR);
            //Both fields have to be present and non empty for the entry to be usable
            if(separator < 1 || separator == record.length() - 1) {
                continue;
            }
            keywords[count] = record.substring(0,separator);
            expansions[count] = record.substring(separator + 1);
            count++;
        }

        if(count == 0) {
            return EMPTY;
        }
        if(count < records.length) {
            return new AutoText(Arrays.copyOf(keywords,count),Arrays.copyOf(expansions,count));
        }
        return new AutoText(keywords,expansions);
    }

    /*
        Remove the separators from user entered text so that it cannot corrupt the stored table.
    */
    private static String strip(final String text) {
        if(text == null) {
            return "";
        }
        return text.replace(ENTRY_SEPARATOR,' ').replace(FIELD_SEPARATOR,' ');
    }
}
