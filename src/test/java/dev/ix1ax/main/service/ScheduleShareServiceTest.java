package dev.ix1ax.main.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.http.*;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ScheduleShareServiceTest {
    private byte[] picture(int width, String format) throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, 100, BufferedImage.TYPE_INT_RGB), format, out);
        return out.toByteArray();
    }
    @Test void acceptsJpegAndRejectsWrongFormatDimensionsAndInvalidInput() throws Exception {
        byte[] jpeg = picture(1080,"jpeg");
        assertArrayEquals(jpeg, ScheduleShareService.validate(Base64.getEncoder().encodeToString(jpeg)));
        for (String input : new String[]{"garbage!", Base64.getEncoder().encodeToString(picture(390,"jpeg")),
                Base64.getEncoder().encodeToString(picture(1080,"png"))})
            assertEquals(400, assertThrows(ResponseStatusException.class, () -> ScheduleShareService.validate(input)).getStatusCode().value());
    }
    @SuppressWarnings("unchecked")
    @Test void preparesOnlyForRequestedUserAndServesImageWithRateLimit() throws Exception {
        var service = new ScheduleShareService(new ObjectMapper(),"test-token","https://api.telegram.org/bot","https://example.test/api","example_bot");
        var http = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"ok\":true,\"result\":{\"id\":\"prepared-123\"}}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        ReflectionTestUtils.setField(service,"http",http);
        byte[] jpeg = picture(1080,"jpeg");
        String encoded = Base64.getEncoder().encodeToString(jpeg);
        var result = service.prepare(123, encoded, "Ваше расписание на 01.10.2026\nГруппа: 2-МР3");
        assertEquals("prepared-123",result.id());
        String imageId = result.imageUrl().substring(result.imageUrl().lastIndexOf('/')+1).replace(".jpg", "");
        assertArrayEquals(jpeg,service.image(imageId));
        assertEquals(429,assertThrows(ResponseStatusException.class, () -> service.prepare(123,encoded,"Подпись")).getStatusCode().value());
        assertEquals(404,assertThrows(ResponseStatusException.class, () -> service.image("unknown")).getStatusCode().value());
        verify(http,times(1)).send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
    }
}
