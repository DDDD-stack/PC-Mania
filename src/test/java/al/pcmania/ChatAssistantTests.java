package al.pcmania;

import al.pcmania.domain.ChatLead;
import al.pcmania.domain.ChatSession;
import al.pcmania.domain.Enums.ChatRole;
import al.pcmania.domain.Enums.LeadStatus;
import al.pcmania.domain.Enums.ProductStatus;
import al.pcmania.domain.Product;
import al.pcmania.repo.BrandRepository;
import al.pcmania.repo.ChatLeadRepository;
import al.pcmania.repo.ChatMessageRepository;
import al.pcmania.repo.ChatSessionRepository;
import al.pcmania.repo.GpuCatalogRepository;
import al.pcmania.repo.ProductRepository;
import al.pcmania.service.chat.ChatAssistant;
import al.pcmania.service.chat.ChatModel;
import al.pcmania.service.chat.ChatSpend;
import al.pcmania.service.chat.ChatUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
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
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The assistant end to end on a real Postgres, with the model scripted: the tool loop, the stream the
 * widget reads, what is kept in the database, the guardrails and the admin pages.
 */
@SpringBootTest(properties = {"app.chat.api-key=", "app.chat.max-messages-per-session=3", "app.chat.monthly-cap-usd=1"})
@AutoConfigureMockMvc
class ChatAssistantTests {

    private static final EmbeddedPostgres PG = LocalPostgres.startTemporary();

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        LocalPostgres.properties(PG).forEach((k, v) -> registry.add(k, () -> v));
    }

    @AfterAll
    static void stop() throws IOException {
        PG.close();
    }

    /** Stands in for the API: each call pops the next scripted turn. */
    @MockitoBean ChatModel model;

    @Autowired MockMvc mvc;
    @Autowired ChatAssistant assistant;
    @Autowired ChatSessionRepository sessions;
    @Autowired ChatMessageRepository messages;
    @Autowired ChatLeadRepository leads;
    @Autowired ChatSpend spend;
    @Autowired ProductRepository products;
    @Autowired BrandRepository brands;
    @Autowired GpuCatalogRepository catalog;
    @Autowired TransactionTemplate tx;
    @Autowired ObjectMapper json;

    private final Deque<ChatModel.Turn> script = new ArrayDeque<>();
    /** What the scripted model was sent, call by call. */
    private final Deque<List<ChatModel.Msg>> sent = new ArrayDeque<>();

    @BeforeEach
    void scriptTheModel() {
        script.clear();
        sent.clear();
        when(model.complete(anyString(), anyList(), anyList(), anyInt(), any())).thenAnswer(inv -> {
            sent.add(inv.getArgument(2));
            ChatModel.Turn next = script.poll();
            if (next == null) throw new ChatUnavailableException("script exhausted", true, null);
            Consumer<String> onText = inv.getArgument(4);
            onText.accept(next.text());
            return next;
        });
    }

    private static ChatModel.Turn text(String t) {
        return new ChatModel.Turn(List.of(new ChatModel.Text(t)), "end_turn", new ChatModel.Usage(1000, 100, 0, 0));
    }

    private ChatModel.Turn toolCall(String id, String name, String inputJson) throws Exception {
        return new ChatModel.Turn(List.of(new ChatModel.ToolUse(id, name, json.readTree(inputJson))), "tool_use", new ChatModel.Usage(1000, 50, 0, 0));
    }

    /** A product the tools can find, stamped with a catalogue row. */
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
            p.setTestNotes("FurMark 15 min, 68°C");
            p.setMiningFree(true);
            p.changeStatus(ProductStatus.ACTIVE);
            return products.save(p);
        });
    }

    private String ask(String token, String message) throws Exception {
        MvcResult started = mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new ChatApiBody(token, message))))
                .andExpect(request().asyncStarted())
                .andReturn();
        return mvc.perform(asyncDispatch(started)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    record ChatApiBody(String token, String message) {}

    @Test
    void toolLoopAnswersFromStockAndShowsCards() throws Exception {
        Product p = stock("MSI RTX 3060 Ti Ventus 2X " + UUID.randomUUID(), "rtx-3060-ti", 38_000);
        script.add(toolCall("t1", "searchStock", "{\"budgetMaxLek\": 40000, \"maxPsuWatts\": 650}"));
        script.add(text("Kemi " + p.getTitle() + " për 38.000 Lekë, e testuar. Ke PSU 650W, mjafton."));

        String body = ask(null, "Kam 40 mijë lekë dhe PSU 650W, luaj Fortnite");
        assertTrue(body.contains("event:session"), body);
        assertTrue(body.contains("event:status") && body.contains("Po shikoj stokun"), body);
        assertTrue(body.contains("event:delta"), body);
        assertTrue(body.contains("event:products") && body.contains(p.getSlug()), body);
        assertTrue(body.contains("38.000 Lekë"), body);
        assertTrue(body.contains("event:done"), body);
        // The tool's result went back to the model, and never with the shop's cost in it.
        List<ChatModel.Msg> second = sent.getLast();
        assertEquals(3, second.size());
        String toolResult = ((ChatModel.ToolResult) second.get(2).parts().get(0)).content();
        assertTrue(toolResult.contains(p.getSlug()) && toolResult.contains("\"psuMinWatts\":600"), toolResult);
        assertFalse(toolResult.contains("30223") || toolResult.toLowerCase().contains("cost"), toolResult);

        // Persisted: the session, both turns, the surfaced product on the assistant's turn.
        String token = body.replaceAll("(?s).*\"token\":\"([^\"]+)\".*", "$1");
        ChatSession s = sessions.findBySessionToken(token).orElseThrow();
        assertEquals(1, s.getMessageCount());
        var turns = messages.findBySessionOrderByIdAsc(s);
        assertEquals(2, turns.size());
        assertEquals(ChatRole.USER, turns.get(0).getRole());
        assertTrue(turns.get(1).getToolCallsJson().contains("searchStock"), turns.get(1).getToolCallsJson());
        assertEquals(1, assistant.cardsOf(turns.get(1)).size());
        assertTrue(spend.thisMonth().getReplies() >= 1);

        // The history endpoint replays it for the next page load, with the cards.
        mvc.perform(get("/api/chat/history").param("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[1].products[0].slug").value(p.getSlug()))
                .andExpect(jsonPath("$.limitReached").value(false));

        // A second message carries the earlier turns to the model, as plain text.
        script.add(text("Po, garancia është 90 ditë."));
        ask(token, "Sa garanci ka?");
        List<ChatModel.Msg> third = sent.getLast();
        assertEquals(3, third.size());
        assertEquals(ChatRole.USER, third.get(0).role());
        assertEquals(ChatRole.ASSISTANT, third.get(1).role());
        assertEquals("Sa garanci ka?", ((ChatModel.Text) third.get(2).parts().get(0)).text());
    }

    @Test
    void leadIsSavedAndSurfacesInTheAdminReport() throws Exception {
        script.add(toolCall("t1", "createLead", "{\"name\":\"Arben Test\",\"phone\":\"069 555 1234\",\"wantedItem\":\"RTX 4070 Super\",\"budgetLek\":80000}"));
        script.add(text("Faleminderit Arben, do të telefonojmë."));
        String body = ask(null, "Dua një 4070 super, më telefononi: Arben, 069 555 1234");
        assertTrue(body.contains("event:done"), body);
        ChatLead lead = leads.findAll().stream().filter(l -> l.getPhone().equals("069 555 1234")).findFirst().orElseThrow();
        assertEquals(LeadStatus.NEW, lead.getStatus());
        assertEquals(80_000, lead.getBudgetLek());
        assertTrue(sessions.findById(tx.execute(s -> leads.findById(lead.getId()).orElseThrow().getSession().getId())).orElseThrow().isLeadCaptured());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminPagesShowTranscriptsLeadsAndDemand() throws Exception {
        script.add(toolCall("t1", "createLead", "{\"name\":\"Blerta\",\"phone\":\"068 222 3333\",\"wantedItem\":\"rtx 3080\"}"));
        script.add(text("U regjistrua."));
        ask(null, "Kërkoj rtx 3080");
        ChatLead lead = leads.findAll().stream().filter(l -> l.getPhone().equals("068 222 3333")).findFirst().orElseThrow();

        mvc.perform(get("/admin/chat")).andExpect(status().isOk())
                .andExpect(content().string(containsString("GeForce RTX 3080 10GB")))   // resolved to the catalogue row
                .andExpect(content().string(containsString("Kërkoj rtx 3080")));
        mvc.perform(get("/admin/chat/leads")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Blerta")));
        mvc.perform(get("/admin/chat/leads/" + lead.getId())).andExpect(status().isOk())
                .andExpect(content().string(containsString("https://wa.me/355682223333")));
        mvc.perform(post("/admin/chat/leads/" + lead.getId()).with(csrf()).param("status", "CONTACTED"))
                .andExpect(status().is3xxRedirection());
        assertEquals(LeadStatus.CONTACTED, leads.findById(lead.getId()).orElseThrow().getStatus());
        Long sessionId = tx.execute(s -> leads.findById(lead.getId()).orElseThrow().getSession().getId());
        mvc.perform(get("/admin/chat/sessions/" + sessionId)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Kërkoj rtx 3080")))
                .andExpect(content().string(containsString("Blerta")));
        mvc.perform(get("/admin/live")).andExpect(content().string(containsString("newLeads")));
    }

    @Test
    void adminChatIsAdminOnly() throws Exception {
        mvc.perform(get("/admin/chat")).andExpect(status().is3xxRedirection());
    }

    @Test
    void sessionLimitSendsTheCustomerToWhatsApp() throws Exception {
        String token = null;
        for (int i = 0; i < 3; i++) {
            script.add(text("Përgjigje " + i));
            String body = ask(token, "Mesazhi " + i);
            assertTrue(body.contains("event:done"), body);
            token = body.replaceAll("(?s).*\"token\":\"([^\"]+)\".*", "$1");
        }
        String body = ask(token, "Edhe një");
        assertTrue(body.contains("event:limit"), body);
        assertTrue(body.contains("wa.me/355688343551"), body);
        assertFalse(body.contains("event:done"), body);
        mvc.perform(get("/api/chat/history").param("token", token)).andExpect(jsonPath("$.limitReached").value(true));
    }

    @Test
    void toolRoundsAreCapped() throws Exception {
        // Five tool rounds asked for, four allowed: the fifth gets an error result and the model must answer.
        for (int i = 0; i < 5; i++) script.add(toolCall("t" + i, "searchStock", "{}"));
        script.add(text("Ja çfarë kemi."));
        String body = ask(null, "Çfarë keni?");
        assertTrue(body.contains("event:done"), body);
        assertEquals(6, sent.size());
        String refused = ((ChatModel.ToolResult) sent.getLast().get(sent.getLast().size() - 1).parts().get(0)).content();
        assertTrue(refused.contains("Kufiri"), refused);
    }

    @Test
    void modelFailureIsReportedNotSwallowed() throws Exception {
        String body = ask(null, "Përshëndetje");   // empty script: the model "fails"
        assertTrue(body.contains("event:error"), body);
        assertTrue(body.contains("WhatsApp"), body);
        // The customer's message is still on record.
        String token = body.replaceAll("(?s).*\"token\":\"([^\"]+)\".*", "$1");
        assertEquals(1, messages.findBySessionOrderByIdAsc(sessions.findBySessionToken(token).orElseThrow()).size());
    }

    @Test
    void badRequestsAreRejected() throws Exception {
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"  \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/chat/history")).andExpect(status().isOk()).andExpect(jsonPath("$.messages.length()").value(0));
    }

    /** The widget is on every public page while the assistant is under its cap, and the WhatsApp bubble after. */
    @Test
    void widgetFollowsTheSpendCap() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"pmChatFab\"")))
                .andExpect(content().string(containsString("/js/chat")));
        mvc.perform(get("/js/chat.js")).andExpect(status().isOk());

        // 1 USD cap: 300k output tokens at 5 USD/M is 1.5 USD.
        spend.record(new ChatModel.Usage(0, 300_000, 0, 0));
        try {
            assertTrue(spend.overCap());
            mvc.perform(get("/")).andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("id=\"pmChatFab\""))))
                    .andExpect(content().string(containsString("pm-chat-fab-wa")));
            String body = mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"hej\"}"))
                    .andExpect(status().isServiceUnavailable()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertTrue(body.contains("event:error"), body);
        } finally {
            tx.executeWithoutResult(s -> {
                var u = spend.thisMonth();
                u.setCostMicroUsd(0);
                u.setOutputTokens(0);
            });
            spend.record(new ChatModel.Usage(0, 0, 0, 0));
            assertFalse(spend.overCap());
        }
    }
}
