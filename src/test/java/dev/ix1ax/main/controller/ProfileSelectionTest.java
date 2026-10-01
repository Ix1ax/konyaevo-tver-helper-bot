package dev.ix1ax.main.controller;

import dev.ix1ax.main.model.UserSettings;
import dev.ix1ax.main.service.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class ProfileSelectionTest {
    @Test void choosingTeacherThenGroupClearsOppositeSelection() {
        var auth = mock(TelegramMiniAppAuth.class);
        var users = mock(ScheduleService.class);
        var parser = mock(ScheduleParserService.class);
        var user = new UserSettings();
        user.setRole("student"); user.setGroupName("3-ИС3");
        user.setNotifyEnabled(false); user.setNotifyTomorrow(false);
        when(auth.requireUserId("signed")).thenReturn(123L);
        when(users.getOrCreateUser(123L)).thenReturn(user);
        when(parser.getGroupsByCourse()).thenReturn(Map.of("3 курс",List.of("3-ИС3")));
        when(parser.getAllTeachers()).thenReturn(Set.of("Авдоян Д.Т."));
        var controller = new ProfileApiController(auth,users,parser);
        controller.put("signed",new ProfileApiController.Update("teacher",null,"Авдоян Д.Т.",false,false,null,null));
        assertEquals("teacher",user.getRole()); assertEquals("Авдоян Д.Т.",user.getTeacherName()); assertNull(user.getGroupName());
        controller.put("signed",new ProfileApiController.Update("student","3-ИС3",null,false,false,null,null));
        assertEquals("student",user.getRole()); assertEquals("3-ИС3",user.getGroupName()); assertNull(user.getTeacherName());
    }
}
