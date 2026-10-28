package dev.ix1ax.main.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ix1ax.main.bot.MessageSender;
import dev.ix1ax.main.bot.TelegramRoutes;
import dev.ix1ax.main.model.Donation;
import dev.ix1ax.main.repository.DonationRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.payments.SuccessfulPayment;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DonationServiceTest {
    private final TelegramRoutes telegram = mock(TelegramRoutes.class);
    private final DonationRepository repository = mock(DonationRepository.class);
    private final MascotService mascot = mock(MascotService.class);
    private final MessageSender sender = mock(MessageSender.class);
    private final DonationService donations = new DonationService(telegram, repository, mascot, sender, new ObjectMapper());

    @Test void invoiceLinkIsInStarsWithoutProviderToken() throws Exception {
        var body = ArgumentCaptor.forClass(String.class);
        when(telegram.postJson(eq("createInvoiceLink"), body.capture()))
                .thenReturn(new TelegramRoutes.Response(200, "{\"ok\":true,\"result\":\"https://t.me/$invoice123\"}"));
        assertEquals("https://t.me/$invoice123", donations.invoiceLink(42, 50));
        var json = new ObjectMapper().readTree(body.getValue());
        assertEquals("XTR", json.path("currency").asText());
        assertEquals("donate:42:50", json.path("payload").asText());
        assertEquals(50, json.path("prices").get(0).path("amount").asInt());
        assertTrue(json.path("provider_token").isMissingNode());
        assertThrows(IllegalArgumentException.class, () -> donations.invoiceLink(42, 0));
        assertThrows(IllegalArgumentException.class, () -> donations.invoiceLink(42, 10_001));
    }

    @Test void telegramRejectionIsReported() throws Exception {
        when(telegram.postJson(eq("createInvoiceLink"), anyString()))
                .thenReturn(new TelegramRoutes.Response(400, "{\"ok\":false,\"description\":\"Bad Request\"}"));
        assertThrows(IllegalStateException.class, () -> donations.invoiceLink(42, 50));
    }

    @Test void preCheckoutAcceptsOnlyOwnStarInvoices() {
        assertTrue(DonationService.valid("XTR", "donate:42:50", 50));
        assertFalse(DonationService.valid("XTR", "donate:42:50", 25));
        assertFalse(DonationService.valid("RUB", "donate:42:50", 50));
        assertTrue(DonationService.valid("XTR", "donate:42:7", 7));
        assertFalse(DonationService.valid("XTR", "donate:42:0", 0));
        assertFalse(DonationService.valid("XTR", "donate:42:20000", 20000));
        assertFalse(DonationService.valid("XTR", "other", 50));
        assertFalse(DonationService.valid("XTR", null, 50));
    }

    @Test void paymentIsCountedOnceOpensLookAndThanks() {
        Set<String> stored = new HashSet<>();
        when(repository.existsById(anyString())).thenAnswer(call -> stored.contains(call.<String>getArgument(0)));
        when(repository.save(any())).thenAnswer(call -> { Donation d = call.getArgument(0); stored.add(d.getChargeId()); return d; });
        when(mascot.addDonation(42, 100)).thenReturn("star");
        when(mascot.lookName("star")).thenReturn("Звёздный");
        Message message = new Message();
        message.setFrom(new User(42L, "Аня", false));
        message.setChat(new org.telegram.telegrambots.meta.api.objects.Chat(42L, "private"));
        var payment = new SuccessfulPayment();
        payment.setCurrency("XTR"); payment.setTotalAmount(100); payment.setInvoicePayload("donate:42:100");
        payment.setTelegramPaymentChargeId("charge-1");
        message.setSuccessfulPayment(payment);
        donations.paid(message);
        donations.paid(message);
        verify(mascot, times(1)).addDonation(42, 100);
        var text = ArgumentCaptor.forClass(String.class);
        verify(sender, times(1)).sendDirectMessage(eq(42L), text.capture(), any());
        assertTrue(text.getValue().contains("100 ⭐") && text.getValue().contains("«Звёздный»"));
    }

    @Test void customAmountIsReadFromMessage() {
        assertEquals(150, DonationService.parseAmount("150"));
        assertEquals(77, DonationService.parseAmount(" 77 ⭐ "));
        assertEquals(500, DonationService.parseAmount("500 звёзд"));
        assertNull(DonationService.parseAmount("0"));
        assertNull(DonationService.parseAmount("10001"));
        assertNull(DonationService.parseAmount("сто"));
        assertNull(DonationService.parseAmount("1.5"));
    }

    @Test void paymentQuestionIsEscapedForAdmins() {
        String text = DonationService.supportForward(42, "<b>Аня</b>", "оплатил <50> ⭐");
        assertTrue(text.contains("&lt;b&gt;Аня&lt;/b&gt;") && text.contains("&lt;50&gt;"));
    }
}
