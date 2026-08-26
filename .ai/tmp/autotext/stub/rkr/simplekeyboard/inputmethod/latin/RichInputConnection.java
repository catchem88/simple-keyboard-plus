package rkr.simplekeyboard.inputmethod.latin;

/*
    Stub standing in for the real RichInputConnection so that AutoText can be compiled and
    exercised without the Android SDK. textBeforeCursorEndsWith is copied verbatim from
    app/src/main/java/rkr/simplekeyboard/inputmethod/latin/RichInputConnection.java
*/
public class RichInputConnection {
    private String mTextBeforeCursor = "";

    public void setTextBeforeCursor(final String text) {
        mTextBeforeCursor = text;
    }

    public boolean textBeforeCursorEndsWith(final String text,final int length) {
        final int offset = mTextBeforeCursor.length() - length;
        return offset >= 0 && mTextBeforeCursor.regionMatches(offset,text,0,length);
    }
}
