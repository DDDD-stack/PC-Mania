package al.pcmania.service.chat;

import al.pcmania.domain.ChatLead;
import al.pcmania.domain.ChatSession;
import al.pcmania.domain.Enums.LeadSource;
import al.pcmania.domain.Enums.LeadStatus;
import al.pcmania.repo.ChatLeadRepository;
import al.pcmania.repo.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeadService {

    @Transactional
    public ChatLead create(ChatSession session, LeadSource source, String name, String phone, String wantedItem,
                           Integer budgetLek, Integer psuWatts, String notes) {
        String cleanName = StringUtils.hasText(name) ? name.trim() : "";
        String cleanPhone = StringUtils.hasText(phone) ? phone.trim() : "";
        String cleanItem = StringUtils.hasText(wantedItem) ? wantedItem.trim() : "";
        if (cleanName.length() < 2 || cleanName.length() > 120) throw new IllegalArgumentException("Shkruani emrin.");
        if (cleanPhone.replaceAll("\\D", "").length() < 8 || cleanPhone.length() > 30) throw new IllegalArgumentException("Shkruani një numër telefoni të plotë.");
        if (cleanItem.length() < 2) throw new IllegalArgumentException("Shkruani çfarë kërkoni.");
        if (cleanItem.length() > 200) cleanItem = cleanItem.substring(0, 200);
        ChatLead lead = new ChatLead();
        lead.setSession(session);
        lead.setSource(source == null ? LeadSource.FORM : source);
        lead.setName(cleanName);
        lead.setPhone(cleanPhone);
        lead.setWantedItem(cleanItem);
        lead.setBudgetLek(budgetLek != null && budgetLek > 0 ? budgetLek : null);
        lead.setPsuWatts(psuWatts != null && psuWatts > 0 ? psuWatts : null);
        lead.setNotes(StringUtils.hasText(notes) ? notes.trim().substring(0, Math.min(notes.trim().length(), 1000)) : null);
        lead.setStatus(LeadStatus.NEW);
        leads.save(lead);
        if (session != null) {
            sessions.findById(session.getId()).ifPresent(s -> s.setLeadCaptured(true));
        }
        log.info("Lead {} from {}: {}", lead.getId(), lead.getSource(), lead.getWantedItem());
        return lead;
    }

    private final ChatLeadRepository leads;
    private final ChatSessionRepository sessions;
}
