package dev.ix1ax.main.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.InputSource;
import java.io.StringReader;

class TelegramTextTest {
    @Test void retainsEveryCharacterAndBalancedTagsAcrossPages() throws Exception {
        String content = "Преподаватель &amp; группа 🔔\n".repeat(600);
        String input = "<b><i>" + content + "</i></b>";
        var pages = TelegramText.pages(input);
        assertTrue(pages.size() > 1);
        StringBuilder restored = new StringBuilder();
        for (String page : pages) {
            assertTrue(page.length() < 4096);
            var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(new StringReader("<root>" + page + "</root>")));
            restored.append(document.getDocumentElement().getTextContent());
        }
        assertEquals(content.replace("&amp;", "&"), restored.toString());
    }
    @Test void leavesShortTextAndLinksUnchanged() {
        String text = "<a href=\"https://example.org\">Привет</a>";
        assertEquals(java.util.List.of(text), TelegramText.pages(text));
    }
}
