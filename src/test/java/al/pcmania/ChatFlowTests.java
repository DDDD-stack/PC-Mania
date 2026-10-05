package al.pcmania;

import al.pcmania.domain.ChatLead;
import al.pcmania.domain.ChatSession;
import al.pcmania.domain.Enums.ChatRole;
import al.pcmania.domain.Enums.LeadSource;
import al.pcmania.domain.Enums.LeadStatus;
import al.pcmania.domain.Enums.ProductStatus;
import al.pcmania.domain.Product;
import al.pcmania.repo.BrandRepository;
import al.pcmania.repo.ChatLeadRepository;
import al.pcmania.repo.ChatMessageRepository;
import al.pcmania.repo.ChatSessionRepository;
import al.pcmania.repo.GpuCatalogRepository;
import al.pcmania.repo.ProductRepository;
import al.pcmania.service.chat.ProviderUsageService;
import al.pcmania.service.chat.StreamingHttp;
import al.pcmania.web.chat.ChatCookie;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The assistant end to end on a real Postgres with Gemini selected and the network scripted: the
 * cookie, the stream the widget reads, what is stored, the fallback to the finder, the contact form,
 * the guardrails and the admin pages.
 */
@SpringBootTest(properties = {"app.chat.provider=gemini", "app.chat.gemini.api-key=test-key", "app.chat.gemini.requests-per-minute=600",
        "app.chat.max-messages-per-session=3"})
@AutoConfigureMockMvc
class ChatFlowTests {

    private static final EmbeddedPostgres PG = LocalPostgres.startTemporary();

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        LocalPostgres.properties(PG).forEach((k, v) -> registry.add(k, () -> v));
    }

    @AfterAll
    static void stop() throws IOException {
        PG.close();
    }

    /** Stands in for the network: each call pops the next scripted answer. */
    @MockitoBean StreamingHttp http;

    @Autowired MockMvc mvc;
    @Autowired ChatSessionRepository sessions;
    @Autowired ChatMessageRepository messages;
    @Autowired ChatLeadRepository leads;
    @Autowired ProviderUsageService usage;
    @Autowired ProductRepository products;
    @Autowired BrandRepository brands;
    @Autowired GpuCatalogRepository catalog;
    @Autowired TransactionTemplate tx;

    private final Deque<Object> script = new ArrayDeque<>();
    private final List<String> bodies = new ArrayList<>();

    @BeforeEach
    void scriptTheNetwork() throws IOException {
        script.clear();
        bodies.clear();
        when(http.postStream(any(), any(), any(), any())).thenAnswer(inv -> {
            bodies.add(inv.getArgument(2));
            Object next = script.poll();
            if (next == null) throw new IOException("script exhausted");
            if (next instanceof Integer status) return new StreamingHttp.Response(status, "{\"error\":{\"code\":" + status + "}}");
            Consumer<String> onLine = inv.getArgument(3);
            for (String line : (List<String>) next) onLine.accept(line);
            return new StreamingHttp.Response(200, null);
        });
    }

    private void reply(String text) {
        script.add(List.of("data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":" + quote(text) + "}],\"role\":\"model\"},\"finishReason\":\"STOP\"}],\"usageMetadata\":{\"promptTokenCount\":100,\"candidatesTokenCount\":10}}"));
    }

    private void call(String name, String argsJson) {
        script.add(List.of("data: {\"candidates\":[{\"content\":{\"parts\":[{\"functionCall\":{\"name\":\"" + name + "\",\"args\":" + argsJson + "}}],\"role\":\"model\"}}]}"));
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private Product stock(String title, String gpuSlug, int price) {
        return tx.execute(s -> {
            Product p = new Product();
            p.setTitle(title);
            p.setSlug("test-" + UUID.randomUUID());
            p.setBrand(brands.findBySlug("msi").orElseThrow());
            p.setCategorySlug("karta-grafike");
            p.setPriceLek(price);
            p.setCostLek(price - 7_777);
            p.setQuantity(1);
            p.setGpuModel(catalog.findBySlug(gpuSlug).orElseThrow());
            p.changeStatus(ProductStatus.ACTIVE);
            return products.save(p);
        });
    }

    /** Posts a message; returns the stream body and the cookie set, if any. */
    private record Answer(String body, Cookie cookie) {}

    private Answer ask(Cookie cookie, String message) throws Exception {
        var req = post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":" + quote(message) + "}");
        if (cookie != null) req.cookie(cookie);
        MvcResult started = mvc.perform(req).andExpect(request().asyncStarted()).andReturn();
        MvcResult done = mvc.perform(asyncDispatch(started)).andReturn();
        Cookie set = done.getResponse().getHeaders("Set-Cookie").stream().map(ChatFlowTests::cookieFrom)
                .filter(java.util.Objects::nonNull).findFirst().orElse(null);
        String body = done.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return new Answer(body, set != null ? set : cookie);
    }

    /** The cookie the controller sets as a Set-Cookie header (MockMvc only tracks addCookie). */
    private static Cookie cookieFrom(String header) {
        if (header == null || !header.startsWith(ChatCookie.NAME + "=")) return null;
        String value = header.substring(ChatCookie.NAME.length() + 1, header.indexOf(';'));
        Cookie c = new Cookie(ChatCookie.NAME, value);
        c.setHttpOnly(header.contains("HttpOnly"));
        return c;
    }

    @Test
    void geminiAnswersFromStockWithCardsAndTheCookieKeepsTheConversation() throws Exception {
        Product p = stock("MSI RTX 3060 Ti Ventus 2X " + UUID.randomUUID(), "rtx-3060-ti", 38_000);
        call("searchStock", "{\"budgetMaxLek\":40000,\"maxPsuWatts\":650}");
        reply("Kemi " + p.getTitle() + " për 38.000 Lekë. Ke PSU 650W, mjafton.");

        Answer a = ask(null, "Kam 40 mijë lekë dhe PSU 650W, luaj Fortnite");
        assertNotNull(a.cookie(), "the first message sets the conversation cookie");
        assertTrue(a.cookie().isHttpOnly());
        assertTrue(a.body().contains("event:status") && a.body().contains("Po shikoj stokun"), a.body());
        assertTrue(a.body().contains("event:delta"), a.body());
        assertTrue(a.body().contains("event:products") && a.body().contains(p.getSlug()), a.body());
        assertTrue(a.body().contains("event:done"), a.body());
        assertFalse(bodies.get(1).contains("30223"), "the cost never reaches the model");

        ChatSession s = sessions.findBySessionToken(a.cookie().getValue()).orElseThrow();
        assertEquals("gemini", s.getProviderUsed());
        var turns = messages.findBySessionOrderByIdAsc(s);
        assertEquals(2, turns.size());
        assertEquals(ChatRole.USER, turns.get(0).getRole());
        assertEquals("gemini", turns.get(1).getProviderUsed());
        assertTrue(turns.get(1).getToolCallsJson().contains("searchStock"));
        assertTrue(usage.requestsToday("gemini") >= 2);

        mvc.perform(get("/api/chat/history").cookie(a.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[1].products[0].slug").value(p.getSlug()))
                .andExpect(jsonPath("$.provider").value("gemini"));

        // The next message rides the cookie: the earlier turns go to the model, no new cookie is set.
        reply("Po, 90 ditë garanci.");
        Answer b = ask(a.cookie(), "Sa garanci ka?");
        assertEquals(a.cookie().getValue(), b.cookie().getValue());
        assertTrue(bodies.get(bodies.size() - 1).contains("Sa garanci ka?"));
        assertTrue(bodies.get(bodies.size() - 1).contains("Kemi " + p.getTitle()));
    }

    @Test
    void rateLimitFallsThroughToTheFinderAndIsCounted() throws Exception {
        int fallbacksBefore = usage.requestsToday(ProviderUsageService.FALLBACK);
        script.add(429);
        Answer a = ask(null, "Çfarë keni?");
        assertTrue(a.body().contains("event:notice") && a.body().contains("i zënë"), a.body());
        assertTrue(a.body().contains("event:finder") && a.body().contains("Çfarë luan"), a.body());
        assertTrue(a.body().contains("event:done"), a.body());
        assertEquals(fallbacksBefore + 1, usage.requestsToday(ProviderUsageService.FALLBACK));
        ChatSession s = sessions.findBySessionToken(a.cookie().getValue()).orElseThrow();
        assertEquals("guided", s.getProviderUsed());
    }

    @Test
    void contactFormSignalThenTheFormPostsTheLeadWithoutTheModel() throws Exception {
        call("requestContactForm", "{\"wantedItem\":\"RTX 4070 Super\",\"budgetLek\":80000}");
        reply("Nuk e kemi në stok. Plotëso formularin poshtë dhe të telefonojmë.");
        Answer a = ask(null, "Dua një 4070 super");
        assertTrue(a.body().contains("event:action") && a.body().contains("show_lead_form"), a.body());
        assertTrue(leads.findAll().stream().noneMatch(l -> "RTX 4070 Super".equals(l.getWantedItem())), "the tool stores nothing");

        mvc.perform(post("/api/lead").cookie(a.cookie()).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "Arben Test").param("phone", "069 555 1234").param("wantedItem", "RTX 4070 Super")
                        .param("budgetLek", "80000").param("source", "CHAT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
        ChatLead lead = leads.findAll().stream().filter(l -> l.getPhone().equals("069 555 1234")).findFirst().orElseThrow();
        assertEquals(LeadSource.CHAT, lead.getSource());
        assertEquals(LeadStatus.NEW, lead.getStatus());
        assertEquals(80_000, lead.getBudgetLek());
        Long sessionId = tx.execute(s -> leads.findById(lead.getId()).orElseThrow().getSession().getId());
        assertTrue(sessions.findById(sessionId).orElseThrow().isLeadCaptured());
        // The name and phone never went to the provider.
        for (String body : bodies) assertFalse(body.contains("Arben") || body.contains("555 1234"), body);

        mvc.perform(post("/api/lead").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "A").param("phone", "06").param("wantedItem", "x"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false));
        mvc.perform(post("/api/lead").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "Bot").param("phone", "069 555 0000").param("wantedItem", "x").param("website", "spam"))
                .andExpect(status().isOk());
        assertTrue(leads.findAll().stream().noneMatch(l -> l.getPhone().equals("069 555 0000")));
    }

    @Test
    void phoneNumbersAreStrippedBeforeTheProviderSeesThem() throws Exception {
        reply("Të dhënat e kontaktit shkruhen në formular.");
        Answer a = ask(null, "Më merr në 069 123 4567 kur të keni 3080");
        assertTrue(a.body().contains("event:notice") && a.body().contains("Hoqa numrin"), a.body());
        String sent = bodies.get(bodies.size() - 1);
        assertFalse(sent.contains("069 123 4567"), sent);
        assertTrue(sent.contains("[numër telefoni hequr]"), sent);
        ChatSession s = sessions.findBySessionToken(a.cookie().getValue()).orElseThrow();
        assertFalse(messages.findBySessionOrderByIdAsc(s).get(0).getContent().contains("4567"));
    }

    @Test
    void finderStepsThroughToResults() throws Exception {
        Product p = stock("Sapphire RX 6600 Pulse " + UUID.randomUUID(), "rx-6600", 24_000);
        mvc.perform(get("/api/finder/step")).andExpect(status().isOk())
                .andExpect(jsonPath("$.step.key").value("use"))
                .andExpect(jsonPath("$.step.options.length()").value(3));
        mvc.perform(get("/api/finder/step").param("use", "esports").param("res", "1080p")).andExpect(jsonPath("$.step.key").value("budget"));
        mvc.perform(get("/api/finder/step").param("use", "esports").param("res", "1080p").param("budget", "30000").param("psu", "450"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.step.done").value(true))
                .andExpect(jsonPath("$.cards[0].slug").value(p.getSlug()))
                .andExpect(jsonPath("$.step.showLeadForm").value(false));
        mvc.perform(get("/api/finder/step").param("use", "aaa").param("res", "1440p").param("budget", "20000").param("psu", "450"))
                .andExpect(jsonPath("$.step.showLeadForm").value(true))
                .andExpect(jsonPath("$.step.wantedItem").value(containsString("lojëra AAA")));
    }

    @Test
    void sessionLimitSendsTheCustomerToWhatsApp() throws Exception {
        Cookie cookie = null;
        for (int i = 0; i < 3; i++) {
            reply("Përgjigje " + i);
            Answer a = ask(cookie, "Mesazhi " + i);
            assertTrue(a.body().contains("event:done"), a.body());
            cookie = a.cookie();
        }
        Answer a = ask(cookie, "Edhe një");
        assertTrue(a.body().contains("event:limit"), a.body());
        assertFalse(a.body().contains("event:done"), a.body());
        mvc.perform(get("/api/chat/history").cookie(cookie)).andExpect(jsonPath("$.limitReached").value(true));
    }

    @Test
    void badRequestsAreRejectedAndTheWidgetIsOnEveryPage() throws Exception {
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"  \"}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/chat/history")).andExpect(status().isOk()).andExpect(jsonPath("$.messages.length()").value(0));
        mvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"pmChatFab\"")))
                .andExpect(content().string(containsString("id=\"pmChatFinderBtn\"")))
                .andExpect(content().string(containsString("/js/chat")));
        mvc.perform(get("/js/chat.js")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminShowsProvidersLeadsAndTheDemandReport() throws Exception {
        call("requestContactForm", "{\"wantedItem\":\"rtx 3080\"}");
        reply("Plotëso formularin.");
        Answer a = ask(null, "Kërkoj rtx 3080");
        mvc.perform(post("/api/lead").cookie(a.cookie()).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "Blerta").param("phone", "068 222 3333").param("wantedItem", "rtx 3080").param("source", "CHAT"))
                .andExpect(status().isOk());
        ChatLead lead = leads.findAll().stream().filter(l -> l.getPhone().equals("068 222 3333")).findFirst().orElseThrow();

        mvc.perform(get("/admin/chat")).andExpect(status().isOk())
                .andExpect(content().string(containsString("GeForce RTX 3080 10GB")))
                .andExpect(content().string(containsString("Kërkoj rtx 3080")))
                .andExpect(content().string(containsString("Përdorimi i ofruesve sot")))
                .andExpect(content().string(containsString("1.500")));
        mvc.perform(get("/admin/chat/leads")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Blerta")))
                .andExpect(content().string(containsString("Asistenti")));
        mvc.perform(get("/admin/chat/leads/" + lead.getId())).andExpect(status().isOk())
                .andExpect(content().string(containsString("https://wa.me/355682223333")));
        mvc.perform(post("/admin/chat/leads/" + lead.getId()).with(csrf()).param("status", "CONTACTED")).andExpect(status().is3xxRedirection());
        assertEquals(LeadStatus.CONTACTED, leads.findById(lead.getId()).orElseThrow().getStatus());
        Long sessionId = sessions.findBySessionToken(a.cookie().getValue()).orElseThrow().getId();
        mvc.perform(get("/admin/chat/sessions/" + sessionId)).andExpect(status().isOk())
                .andExpect(content().string(containsString("(gemini)")))
                .andExpect(content().string(containsString("Blerta")));
        mvc.perform(get("/admin/live")).andExpect(content().string(containsString("newLeads")));
    }

    @Test
    void adminChatIsAdminOnly() throws Exception {
        mvc.perform(get("/admin/chat")).andExpect(status().is3xxRedirection());
    }
}
