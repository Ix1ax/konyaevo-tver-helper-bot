package dev.ix1ax.main.service;

import dev.ix1ax.main.controller.FeedbackController;
import dev.ix1ax.main.model.FeedbackEntry;
import dev.ix1ax.main.repository.FeedbackRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.*;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FeedbackControllerTest {
    private final TelegramMiniAppAuth auth=mock(TelegramMiniAppAuth.class);
    private final AdminService admins=mock(AdminService.class);
    private final FeedbackRepository repository=mock(FeedbackRepository.class);
    private final org.springframework.test.web.servlet.MockMvc mvc;
    FeedbackControllerTest() {
        when(auth.requireUserId(null)).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        when(auth.requireUserId("user")).thenReturn(42L);
        when(auth.requireUserId("admin")).thenReturn(100L);
        when(admins.isAdmin(100L)).thenReturn(true);
        mvc=MockMvcBuilders.standaloneSetup(new FeedbackController(auth,admins,repository)).build();
    }
    @Test void requiresExplanationForOneToFourAndAllowsFiveWithoutText() throws Exception {
        for (int stars=1; stars<=4; stars++) {
            mvc.perform(post("/api/feedback").header("X-Telegram-Init-Data","user").contentType("application/json").content("{\"stars\":"+stars+",\"comment\":\"   \"}"))
                    .andExpect(status().isBadRequest());
        }
        verify(repository,never()).saveAndFlush(any());
        mvc.perform(post("/api/feedback").header("X-Telegram-Init-Data","user").contentType("application/json").content("{\"stars\":5}"))
                .andExpect(status().isOk());
        var entry=org.mockito.ArgumentCaptor.forClass(FeedbackEntry.class);
        verify(repository).saveAndFlush(entry.capture()); assertEquals(42L,entry.getValue().getUserId()); assertEquals("",entry.getValue().getComment());
    }
    @Test void prohibitsRepeatedVotesAndNeverReadsAnonymousFeedback() throws Exception {
        mvc.perform(post("/api/feedback").contentType("application/json").content("{\"stars\":5}"))
                .andExpect(status().isUnauthorized()); verifyNoInteractions(repository);
        when(repository.existsByUserId(42L)).thenReturn(true);
        mvc.perform(post("/api/feedback").header("X-Telegram-Init-Data","user").contentType("application/json").content("{\"stars\":5}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/feedback/status").header("X-Telegram-Init-Data","user"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.submitted").value(true));
        verify(repository,never()).saveAndFlush(any());
    }
    @Test void adminReviewsArePaginatedAndRequireAdmin() throws Exception {
        mvc.perform(get("/api/admin/feedback").header("X-Telegram-Init-Data","user"))
                .andExpect(status().isForbidden()); verifyNoInteractions(repository);
        when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(new FeedbackEntry(42,3,"Не работает кнопка")),PageRequest.of(1,10),21));
        when(repository.distribution()).thenReturn(List.<Object[]>of(new Object[]{3,2L},new Object[]{5,1L}));
        mvc.perform(get("/api/admin/feedback?page=1&size=10").header("X-Telegram-Init-Data","admin"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.pages").value(3))
                .andExpect(jsonPath("$.items[0].comment").value("Не работает кнопка"));
        var paging=org.mockito.ArgumentCaptor.forClass(Pageable.class); verify(repository).findAll(paging.capture());
        assertEquals(10,paging.getValue().getPageSize()); assertEquals(1,paging.getValue().getPageNumber());
        mvc.perform(get("/api/admin/feedback?page=-1&size=10000").header("X-Telegram-Init-Data","admin"))
                .andExpect(status().isBadRequest());
    }
}
