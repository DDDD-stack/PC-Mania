package al.pcmania.service;

import al.pcmania.domain.Enums.WishStatus;
import al.pcmania.domain.WishRequest;
import al.pcmania.repo.WishRequestRepository;
import al.pcmania.web.site.WishForm;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Customers' requests for products the shop should bring in ("Kërko një produkt"). */
@Service
@RequiredArgsConstructor
public class WishService {

    private final WishRequestRepository repo;
    private final ApplicationEventPublisher events;

    @Transactional
    public WishRequest create(WishForm form) {
        WishRequest w = new WishRequest();
        w.setCustomerName(form.getCustomerName().trim());
        w.setCustomerPhone(form.getCustomerPhone().trim());
        w.setItem(form.getItem().trim());
        w.setMaxPriceLek(form.getMaxPriceLek());
        w.setCondition(form.getCondition());
        w.setNotes(StringUtils.hasText(form.getNotes()) ? form.getNotes().trim() : null);
        w.setStatus(WishStatus.NEW);
        repo.save(w);
        events.publishEvent(new NotificationService.WishRequested(w.getId()));
        return w;
    }

    @Transactional
    public void update(Long id, WishStatus status, String adminNotes) {
        WishRequest w = repo.findById(id).orElseThrow(NotFoundException::new);
        w.setStatus(status);
        w.setAdminNotes(StringUtils.hasText(adminNotes) ? adminNotes.trim() : null);
    }

    @Transactional
    public void delete(Long id) {
        repo.delete(repo.findById(id).orElseThrow(NotFoundException::new));
    }
}
