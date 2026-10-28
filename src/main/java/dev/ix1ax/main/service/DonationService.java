package dev.ix1ax.main.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ix1ax.main.bot.KeyboardFactory;
import dev.ix1ax.main.bot.MessageSender;
import dev.ix1ax.main.bot.TelegramRoutes;
import dev.ix1ax.main.model.Donation;
import dev.ix1ax.main.repository.DonationRepository;
import dev.ix1ax.main.util.HtmlUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.AnswerPreCheckoutQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.payments.PreCheckoutQuery;
import org.telegram.telegrambots.meta.api.objects.payments.SuccessfulPayment;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Добровольная поддержка звёздами Telegram (валюта XTR): платёжный провайдер и кошелёк не нужны,
 * звёзды приходят на баланс бота. За поддержку открываются облики Парыча «Звёздный» и «Корона».
 */
@Service
public class DonationService {
    private static final Logger log = LoggerFactory.getLogger(DonationService.class);
    /** Быстрые суммы на кнопках; своя сумма — любая от MIN до MAX звёзд. */
    public static final List<Integer> AMOUNTS = List.of(25, 50, 100, 250);
    public static final int MIN = 1, MAX = 10_000;
    private static final Pattern PAYLOAD = Pattern.compile("donate:(\\d+):(\\d+)");
    static final String TITLE = "Поддержка Парыча";
    static final String DESCRIPTION = "Добровольная поддержка Парыча и бота расписания. Спасибо! В ответ Парыч станет Звёздным, а от 250 ⭐ — наденет корону.";

    private final TelegramRoutes telegram;
    private final DonationRepository donations;
    private final MascotService mascot;
    private final MessageSender sender;
    private final ObjectMapper json;

    public DonationService(TelegramRoutes telegram, DonationRepository donations, MascotService mascot, MessageSender sender, ObjectMapper json) {
        this.telegram = telegram; this.donations = donations; this.mascot = mascot; this.sender = sender; this.json = json;
    }

    static String payload(long user, int stars) { return "donate:" + user + ":" + stars; }

    private Map<String, Object> invoice(long user, int stars) {
        if (!allowed(stars)) throw new IllegalArgumentException("Сумма — от " + MIN + " до " + MAX + " звёзд");
        var body = new LinkedHashMap<String, Object>();
        body.put("title", TITLE);
        body.put("description", DESCRIPTION);
        body.put("payload", payload(user, stars));
        body.put("currency", "XTR"); // для звёзд provider_token не передаётся
        body.put("prices", List.of(Map.of("label", "Поддержка", "amount", stars)));
        return body;
    }

    /** Ссылка на счёт для Mini App: открывается через Telegram.WebApp.openInvoice. */
    public String invoiceLink(long user, int stars) throws Exception {
        var data = json.readTree(telegram.postJson("createInvoiceLink", json.writeValueAsString(invoice(user, stars))).body());
        String url = data.path("result").asText();
        if (!data.path("ok").asBoolean() || !url.startsWith("https://")) {
            log.warn("[DONATE] Telegram rejected invoice link: {}", data.path("description").asText("no description"));
            throw new IllegalStateException("Telegram не создал счёт");
        }
        return url;
    }

    /** Счёт сообщением в чате бота. */
    public boolean sendInvoice(long chatId, int stars) {
        try {
            var body = invoice(chatId, stars);
            body.put("chat_id", chatId);
            var data = json.readTree(telegram.postJson("sendInvoice", json.writeValueAsString(body)).body());
            if (data.path("ok").asBoolean()) return true;
            log.warn("[DONATE] Telegram rejected invoice for chatId {}: {}", chatId, data.path("description").asText("no description"));
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("[DONATE] Invoice failed for chatId {}: {}", chatId, e.getClass().getSimpleName());
        }
        return false;
    }

    /** Telegram ждёт ответа до 10 секунд: подтверждаем только свои счета в звёздах на известную сумму. */
    public void preCheckout(PreCheckoutQuery query) {
        boolean valid = valid(query.getCurrency(), query.getInvoicePayload(), query.getTotalAmount());
        try {
            telegram.call(s -> s.execute(valid ? new AnswerPreCheckoutQuery(query.getId(), true)
                    : new AnswerPreCheckoutQuery(query.getId(), false, "Этот счёт устарел. Откройте «Поддержать» ещё раз.")));
        } catch (Exception e) {
            log.warn("[DONATE] Pre-checkout answer failed: {}", e.getMessage());
        }
    }

    public static boolean allowed(int stars) { return stars >= MIN && stars <= MAX; }

    static boolean valid(String currency, String payload, Integer amount) {
        var match = payload == null ? null : PAYLOAD.matcher(payload);
        return "XTR".equals(currency) && match != null && match.matches() && amount != null
                && match.group(2).length() < 6 && Integer.parseInt(match.group(2)) == amount && allowed(amount);
    }

    /** Своя сумма из сообщения: «150», «150 ⭐», «150 звёзд». null — не число или вне пределов. */
    public static Integer parseAmount(String text) {
        var match = Pattern.compile("^\\s*(\\d{1,5})\\s*(⭐|звезд|звёзд|звезды|звёзды)?\\s*$", Pattern.CASE_INSENSITIVE).matcher(text == null ? "" : text);
        if (!match.matches()) return null;
        int stars = Integer.parseInt(match.group(1));
        return allowed(stars) ? stars : null;
    }

    /** Успешная оплата: сохраняем один раз, открываем облик и благодарим. */
    public synchronized void paid(Message message) {
        SuccessfulPayment payment = message.getSuccessfulPayment();
        if (payment == null || !"XTR".equals(payment.getCurrency()) || message.getFrom() == null) return;
        String charge = payment.getTelegramPaymentChargeId();
        if (charge == null || donations.existsById(charge)) return;
        long user = message.getFrom().getId();
        int stars = payment.getTotalAmount();
        donations.save(new Donation(charge, user, stars, Instant.now()));
        String look = mascot.addDonation(user, stars);
        log.info("[DONATE] chatId {} donated {} stars{}", user, stars, look == null ? "" : ", opened look " + look);
        sender.sendDirectMessage(message.getChatId(), thanks(stars, look == null ? null : mascot.lookName(look)), KeyboardFactory.buildDonateThanksKeyboard());
    }

    static String thanks(int stars, String look) {
        return "💛 <b>Спасибо за поддержку — " + stars + " ⭐!</b>\n\n" +
                (look != null ? "Парыч в восторге: тебе открыт облик «" + look + "», он уже надет. Загляни к Парычу в приложении."
                        : "Парыч в восторге и передаёт привет. Твои облики поддержки уже открыты.") +
                "\n\nБот остаётся бесплатным и без рекламы благодаря таким, как ты.";
    }

    public static String menuText() {
        return "⭐ <b>Поддержать Парыча</b>\n\n" +
                "Бот бесплатный и без рекламы. Если он тебе помогает — поддержи его звёздами Telegram. Нужны только звёзды, карта не нужна.\n\n" +
                "🎁 За любую поддержку Парыч станет <b>Звёздным</b>, а от " + MascotService.CROWN_STARS + " ⭐ всего — наденет <b>корону</b>.\n\n" +
                "Выбери сумму или нажми «Своя сумма».";
    }

    public static String customPrompt() {
        return "✏️ Напиши, сколько звёзд хочешь отправить — числом от " + MIN + " до " + String.format("%,d", MAX).replace(',', ' ') + ".\n\nОтмена: /cancel";
    }

    public static String supportText() {
        return "💬 <b>Вопросы по оплате</b>\n\n" +
                "Поддержка звёздами добровольная: за неё открываются облики Парыча, других услуг нет.\n\n" +
                "Если оплата прошла, а облик не открылся, или вы хотите вернуть звёзды, напишите одним сообщением:\n" +
                "<code>/paysupport ваш вопрос</code>\n\nСообщение получит администратор и ответит здесь же.";
    }

    public static String supportForward(long chatId, String who, String question) {
        return "💬 <b>Вопрос по оплате</b> от " + HtmlUtils.escapeHtml(who) + " (<code>" + chatId + "</code>)\n\n" + HtmlUtils.escapeHtml(question);
    }

    public String statsLine() {
        return "Поддержка: <b>" + donations.totalStars() + " ⭐</b> от " + donations.supporters() + " чел.";
    }
}
