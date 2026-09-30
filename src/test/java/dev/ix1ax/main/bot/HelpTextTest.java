package dev.ix1ax.main.bot;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HelpTextTest {
    @Test
    void ordinaryHelpContainsPublicCommandsAndNoAdminCommands() {
        String text = HelpText.forUser(false);
        for (String command : new String[]{"/start", "/help", "/cancel"}) assertTrue(text.contains(command));
        for (String command : new String[]{"/admin", "/stats", "/refresh", "/broadcast"}) assertFalse(text.contains(command));
        assertTrue(text.contains("без замен"));
        assertFalse(text.contains("тебе"));
    }

    @Test
    void adminHelpExplainsConfirmationAndMedia() {
        String text = HelpText.forUser(true);
        assertTrue(text.contains("/broadcast"));
        assertTrue(text.contains("после подтверждения"));
        assertTrue(text.contains("/stats"));
    }
}
