package al.pcmania.service;

import al.pcmania.domain.BuildRequest;
import al.pcmania.domain.Enums.BuildStatus;
import al.pcmania.repo.BuildRequestRepository;
import al.pcmania.web.site.BuildRequestForm;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class BuildRequestService {

    private final BuildRequestRepository repo;
    private final ApplicationEventPublisher events;

    @Transactional
    public BuildRequest create(BuildRequestForm form) {
        BuildRequest b = new BuildRequest();
        b.setCustomerName(form.getCustomerName().trim());
        b.setCustomerPhone(form.getCustomerPhone().trim());
        b.setBudgetLek(form.getBudgetLek());
        b.setUseCase(form.getUseCase());
        b.setNotes(StringUtils.hasText(form.getNotes()) ? form.getNotes().trim() : null);
        b.setStatus(BuildStatus.NEW);
        repo.save(b);
        events.publishEvent(new NotificationService.BuildRequested(b.getId()));
        return b;
    }

    @Transactional
    public void update(Long id, BuildStatus status, Integer quotedTotalLek, String adminNotes) {
        BuildRequest b = repo.findById(id).orElseThrow(NotFoundException::new);
        if (quotedTotalLek != null && quotedTotalLek < 0) throw new IllegalArgumentException("Oferta nuk mund të jetë negative.");
        if ((status == BuildStatus.QUOTED || status == BuildStatus.ACCEPTED) && quotedTotalLek == null) {
            throw new IllegalArgumentException("Shkruani totalin e ofertës para se ta shënoni \"" + status.label + "\".");
        }
        b.setStatus(status);
        b.setQuotedTotalLek(quotedTotalLek);
        b.setAdminNotes(StringUtils.hasText(adminNotes) ? adminNotes.trim() : null);
    }
}
