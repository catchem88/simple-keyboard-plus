import rkr.simplekeyboard.inputmethod.latin.RichInputConnection;
import rkr.simplekeyboard.inputmethod.latin.settings.AutoText;

/*
    Exercises the real AutoText source without the Android SDK. Run from .ai/tmp/autotext:
        javac -d out ..\..\..\app\src\main\java\rkr\simplekeyboard\inputmethod\latin\settings\AutoText.java stub\rkr\simplekeyboard\inputmethod\latin\RichInputConnection.java AutoTextCheck.java
        java -cp out AutoTextCheck
*/
public class AutoTextCheck {
    private static int sFailures = 0;

    public static void main(final String[] args) {
        userScenario();
        noFalsePositives();
        singleCharKeyword();
        roundTrip();
        editing();
        removal();
        separatorStripping();
        malformedInput();
        supplementaryTrigger();
        multipleEntries();

        if(sFailures == 0) {
            System.out.println("\nALL CHECKS PASSED");
        }
        else {
            System.out.println("\n" + sFailures + " CHECK(S) FAILED");
            System.exit(1);
        }
    }

    /*
        The scenario from the request: /lipsum expands once the m is typed.
    */
    private static void userScenario() {
        final AutoText table = AutoText.EMPTY.withEntry("/lipsum","Lorem ipsum dolor sit amet");
        final RichInputConnection connection = new RichInputConnection();

        //Nothing should fire while the keyword is still incomplete
        connection.setTextBeforeCursor("/lipsu");
        check("no match on l",AutoText.NOT_A_MATCH,table.findMatch(connection,'l'));
        check("no match on u",AutoText.NOT_A_MATCH,table.findMatch(connection,'u'));

        //The m completes it
        final int index = table.findMatch(connection,'m');
        check("match on m",0,index);
        check("prefix length",6,table.getPrefixLength(index));
        check("expansion","Lorem ipsum dolor sit amet",table.getExpansion(index));

        //Simulate what InputLogic does: delete the prefix, commit the expansion
        final String before = "Hello /lipsu";
        connection.setTextBeforeCursor(before);
        final int matched = table.findMatch(connection,'m');
        check("match mid sentence",0,matched);
        final String result = before.substring(0,before.length() - table.getPrefixLength(matched))
                + table.getExpansion(matched);
        check("resulting text","Hello Lorem ipsum dolor sit amet",result);
    }

    private static void noFalsePositives() {
        final AutoText table = AutoText.EMPTY.withEntry("/lipsum","Lorem ipsum");
        final RichInputConnection connection = new RichInputConnection();

        connection.setTextBeforeCursor("xyz");
        check("wrong prefix",AutoText.NOT_A_MATCH,table.findMatch(connection,'m'));

        connection.setTextBeforeCursor("");
        check("empty field",AutoText.NOT_A_MATCH,table.findMatch(connection,'m'));

        connection.setTextBeforeCursor("/lips");
        check("prefix too short",AutoText.NOT_A_MATCH,table.findMatch(connection,'m'));

        connection.setTextBeforeCursor("/lipsu");
        check("empty table",AutoText.NOT_A_MATCH,AutoText.EMPTY.findMatch(connection,'m'));
        check("empty table isEmpty",true,AutoText.EMPTY.isEmpty());
    }

    private static void singleCharKeyword() {
        final AutoText table = AutoText.EMPTY.withEntry("@","me@example.com");
        final RichInputConnection connection = new RichInputConnection();

        connection.setTextBeforeCursor("");
        final int index = table.findMatch(connection,'@');
        check("single char match",0,index);
        check("single char prefix length",0,table.getPrefixLength(index));
        check("single char no match",AutoText.NOT_A_MATCH,table.findMatch(connection,'#'));
    }

    private static void roundTrip() {
        final AutoText table = AutoText.EMPTY
                .withEntry("/lipsum","Lorem ipsum dolor sit amet")
                .withEntry("/sig","Best regards,\nAdrian ")
                .withEntry("brb ","be right back ");

        final String serialized = table.serialize();
        final AutoText parsed = AutoText.parse(serialized);

        check("round trip size",3,parsed.size());
        for(int index = 0; index < table.size(); index++) {
            check("round trip keyword " + index,table.getKeyword(index),parsed.getKeyword(index));
            check("round trip expansion " + index,table.getExpansion(index),
                    parsed.getExpansion(index));
        }
        check("round trip serialize is stable",serialized,parsed.serialize());

        //Trailing space in a keyword survives and still triggers on space
        final RichInputConnection connection = new RichInputConnection();
        connection.setTextBeforeCursor("brb");
        final int index = parsed.findMatch(connection,' ');
        check("space triggered match",2,index);
        check("space triggered prefix",3,parsed.getPrefixLength(index));
        check("newline preserved","Best regards,\nAdrian ",parsed.getExpansion(1));
    }

    private static void editing() {
        AutoText table = AutoText.EMPTY.withEntry("/a","first").withEntry("/b","second");
        check("two entries",2,table.size());

        //Same keyword replaces the expansion in place instead of adding a duplicate
        table = table.withEntry("/a","replaced");
        check("replace keeps size",2,table.size());
        check("replace keeps order","/a",table.getKeyword(0));
        check("replace value","replaced",table.getExpansion(0));
        check("replace leaves sibling","second",table.getExpansion(1));

        //Empty fields are rejected
        check("empty keyword rejected",2,table.withEntry("","x").size());
        check("empty expansion rejected",2,table.withEntry("/c","").size());
        check("null keyword rejected",2,table.withEntry(null,"x").size());
        check("null expansion rejected",2,table.withEntry("/c",null).size());
    }

    private static void removal() {
        final AutoText table = AutoText.EMPTY
                .withEntry("/a","first")
                .withEntry("/b","second")
                .withEntry("/c","third");

        final AutoText withoutMiddle = table.withoutEntry("/b");
        check("removal size",2,withoutMiddle.size());
        check("removal kept first","/a",withoutMiddle.getKeyword(0));
        check("removal kept last","/c",withoutMiddle.getKeyword(1));
        check("removal kept last value","third",withoutMiddle.getExpansion(1));
        check("original untouched",3,table.size());

        check("removing unknown is a no-op",3,table.withoutEntry("/zz").size());

        final AutoText single = AutoText.EMPTY.withEntry("/a","first");
        check("removing the only entry empties",true,single.withoutEntry("/a").isEmpty());
        check("empty serializes to empty string","",single.withoutEntry("/a").serialize());
    }

    private static void separatorStripping() {
        //The two control characters used by the format must never reach the stored table
        final AutoText table = AutoText.EMPTY.withEntry("/a\u001Eb","x\u001Fy");
        check("entry separator stripped","/a b",table.getKeyword(0));
        check("field separator stripped","x y",table.getExpansion(0));
        check("still round trips",1,AutoText.parse(table.serialize()).size());
    }

    private static void malformedInput() {
        check("null parses empty",true,AutoText.parse(null).isEmpty());
        check("empty parses empty",true,AutoText.parse("").isEmpty());
        check("no separator dropped",true,AutoText.parse("nofieldseparator").isEmpty());
        check("empty keyword dropped",true,AutoText.parse("\u001Fexpansion").isEmpty());
        check("empty expansion dropped",true,AutoText.parse("keyword\u001F").isEmpty());
        check("good entry survives bad ones",1,
                AutoText.parse("bad\u001E/a\u001Fgood\u001Ealsobad").size());
        check("surviving keyword","/a",
                AutoText.parse("bad\u001E/a\u001Fgood\u001Ealsobad").getKeyword(0));
    }

    private static void supplementaryTrigger() {
        //A keyword ending in a surrogate pair must report the prefix length in chars
        final String rocket = new String(Character.toChars(0x1F680));
        final AutoText table = AutoText.EMPTY.withEntry("go" + rocket,"launched");
        final RichInputConnection connection = new RichInputConnection();

        connection.setTextBeforeCursor("go");
        final int index = table.findMatch(connection,0x1F680);
        check("supplementary match",0,index);
        check("supplementary prefix length",2,table.getPrefixLength(index));
        check("supplementary keyword length",4,table.getKeyword(0).length());
    }

    private static void multipleEntries() {
        //Entries sharing a trigger must both be reachable
        final AutoText table = AutoText.EMPTY
                .withEntry("/am","morning")
                .withEntry("/pm","evening");
        final RichInputConnection connection = new RichInputConnection();

        connection.setTextBeforeCursor("/a");
        check("first of shared trigger",0,table.findMatch(connection,'m'));
        connection.setTextBeforeCursor("/p");
        check("second of shared trigger",1,table.findMatch(connection,'m'));
        connection.setTextBeforeCursor("/x");
        check("neither of shared trigger",AutoText.NOT_A_MATCH,table.findMatch(connection,'m'));

        check("indexOfKeyword found",1,table.indexOfKeyword("/pm"));
        check("indexOfKeyword missing",AutoText.NOT_A_MATCH,table.indexOfKeyword("/nope"));
    }

    private static void check(final String name,final Object expected,final Object actual) {
        if(expected.equals(actual)) {
            System.out.println("  ok   " + name);
        }
        else {
            System.out.println("  FAIL " + name + " expected <" + expected + "> got <" + actual + ">");
            sFailures++;
        }
    }
}
