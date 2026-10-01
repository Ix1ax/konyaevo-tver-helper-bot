package dev.ix1ax.main.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ix1ax.main.model.SharedImage;
import dev.ix1ax.main.repository.SharedImageRepository;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ImageKitStorageTest {
    @Test void uploadsBytesAndDeletesPersistedExpiredFiles() throws Exception {
        var repository = mock(SharedImageRepository.class);
        var body = new AtomicReference<String>();
        var auth = new AtomicReference<String>();
        var deleted = new AtomicReference<String>();
        var http = mock(java.net.http.HttpClient.class);
        var response = mock(java.net.http.HttpResponse.class);
        when(response.statusCode()).thenReturn(200, 204);
        when(response.body()).thenReturn("{\"fileId\":\"file-123\",\"url\":\"https://ik.imagekit.io/test/konyaevo-schedule-share/unique.jpg\"}");
        when(http.send(any(java.net.http.HttpRequest.class), any(java.net.http.HttpResponse.BodyHandler.class))).thenAnswer(invocation -> {
            java.net.http.HttpRequest request = invocation.getArgument(0);
            auth.set(request.headers().firstValue("Authorization").orElseThrow());
            if (request.method().equals("DELETE")) deleted.set(request.uri().toString());
            else {
                var collected = new java.io.ByteArrayOutputStream();
                request.bodyPublisher().orElseThrow().subscribe(new java.util.concurrent.Flow.Subscriber<java.nio.ByteBuffer>() {
                    public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
                    public void onNext(java.nio.ByteBuffer buffer) { byte[] bytes = new byte[buffer.remaining()]; buffer.get(bytes); collected.writeBytes(bytes); }
                    public void onError(Throwable error) { throw new AssertionError(error); }
                    public void onComplete() {}
                });
                body.set(collected.toString(StandardCharsets.UTF_8));
            }
            return response;
        });
        var storage = new ImageKitStorage(new ObjectMapper(), repository, "private-test", "https://ik.imagekit.io/test", "NO_PROXY", "localhost", 10808);
        org.springframework.test.util.ReflectionTestUtils.setField(storage, "http", http);
        var result = storage.upload(new byte[]{1,2,3}, "unique");
        assertEquals("file-123", result.fileId());
        assertTrue(result.expiresAt() > System.currentTimeMillis());
        assertTrue(body.get().contains("AQID"));
        assertTrue(body.get().contains("/konyaevo-schedule-share"));
        assertTrue(auth.get().startsWith("Basic "));
        verify(repository).save(argThat(item -> item.getFileId().equals("file-123") && item.getExpiresAt() == result.expiresAt()));
        when(repository.findTop50ByExpiresAtLessThanOrderByExpiresAtAsc(anyLong())).thenReturn(List.of(new SharedImage("file-123", 0)));
        var restarted = new ImageKitStorage(new ObjectMapper(), repository, "private-test", "https://ik.imagekit.io/test", "NO_PROXY", "localhost", 10808);
        org.springframework.test.util.ReflectionTestUtils.setField(restarted, "http", http);
        restarted.clearExpired();
        assertTrue(deleted.get().endsWith("/v1/files/file-123"));
        verify(repository).deleteById("file-123");
    }
    @Test void failedDeletionRemainsPersistedForRetry() throws Exception {
        var repository = mock(SharedImageRepository.class);
        when(repository.findTop50ByExpiresAtLessThanOrderByExpiresAtAsc(anyLong())).thenReturn(List.of(new SharedImage("retry", 0)));
        var http = mock(java.net.http.HttpClient.class);
        var response = mock(java.net.http.HttpResponse.class);
        when(response.statusCode()).thenReturn(503);
        when(response.body()).thenReturn("");
        when(http.send(any(java.net.http.HttpRequest.class), any(java.net.http.HttpResponse.BodyHandler.class))).thenReturn(response);
        var storage = new ImageKitStorage(new ObjectMapper(), repository, "private-test", "https://ik.imagekit.io/test", "NO_PROXY", "localhost", 10808);
        org.springframework.test.util.ReflectionTestUtils.setField(storage, "http", http);
        storage.clearExpired();
        verify(repository, never()).deleteById(anyString());
    }
    @Test void missingKeyDisablesStorageAndInvalidEndpointFailsClearly() {
        var repository = mock(SharedImageRepository.class);
        var storage = new ImageKitStorage(new ObjectMapper(), repository, "", "", "NO_PROXY", "localhost", 10808);
        assertFalse(storage.enabled());
        storage.clearExpired();
        verifyNoInteractions(repository);
        assertThrows(IllegalArgumentException.class, () -> new ImageKitStorage(new ObjectMapper(), repository, "private-test", "https://wrong.example", "NO_PROXY", "localhost", 10808));
    }
}
