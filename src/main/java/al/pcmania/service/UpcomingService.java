package al.pcmania.service;

import al.pcmania.domain.Enums.Condition;
import al.pcmania.domain.Enums.UpcomingStatus;
import al.pcmania.domain.UpcomingInterest;
import al.pcmania.domain.UpcomingProduct;
import al.pcmania.repo.UpcomingInterestRepository;
import al.pcmania.repo.UpcomingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * "Së shpejti" teasers and the people waiting for them. Teasers are never orderable: the only thing a
 * customer can do is leave a phone number, which the operator works through when the stock lands.
 */
@Service
@RequiredArgsConstructor
public class UpcomingService {

    private final UpcomingRepository repo;
    private final UpcomingInterestRepository interests;
    private final ImageStorage images;

    // ---- Reading ----

    /** Teasers for the public site: visible ones, plus arrived ones so a promise is not silently dropped. */
    public List<UpcomingProduct> publicList() {
        List<UpcomingProduct> visible = repo.findByStatusOrderBySortOrderAscCreatedAtDesc(UpcomingStatus.VISIBLE);
        List<UpcomingProduct> arrived = repo.findByStatusOrderBySortOrderAscCreatedAtDesc(UpcomingStatus.ARRIVED);
        return java.util.stream.Stream.concat(visible.stream(), arrived.stream()).toList();
    }

    public List<UpcomingProduct> all() {
        return repo.findAllByOrderBySortOrderAscCreatedAtDesc();
    }

    public UpcomingProduct get(Long id) {
        return repo.findById(id).orElseThrow(NotFoundException::new);
    }

    public UpcomingProduct bySlug(String slug) {
        return repo.findBySlug(slug).orElseThrow(NotFoundException::new);
    }

    public long countByStatus(UpcomingStatus status) {
        return repo.countByStatus(status);
    }

    public Map<Long, Long> interestCounts(List<UpcomingProduct> items) {
        if (items.isEmpty()) return Map.of();
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : repo.interestCounts(items.stream().map(UpcomingProduct::getId).toList())) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    public List<UpcomingInterest> interestsFor(Long id) {
        return interests.findByUpcomingIdOrderByCreatedAtDesc(id);
    }

    public long interestCount(Long id) {
        return interests.countByUpcomingId(id);
    }

    public long waitingCount(Long id) {
        return interests.countByUpcomingIdAndNotifiedFalse(id);
    }

    // ---- Writing ----

    @Transactional
    public UpcomingProduct save(Long id, String title, String teaser, String categorySlug, Integer expectedPriceLek,
                                String expectedLabel, Condition condition, UpcomingStatus status, Integer sortOrder) {
        if (!StringUtils.hasText(title)) throw new IllegalArgumentException("Shkruani titullin.");
        if (expectedPriceLek != null && expectedPriceLek < 0) throw new IllegalArgumentException("Çmimi nuk mund të jetë negativ.");

        UpcomingProduct u = id == null ? new UpcomingProduct() : get(id);
        String wanted = Slugs.of(title);
        if (u.getSlug() == null || !wanted.equals(u.getSlug())) u.setSlug(uniqueSlug(wanted, u.getId()));
        u.setTitle(title.trim());
        u.setTeaser(trimToNull(teaser));
        u.setCategorySlug(trimToNull(categorySlug));
        u.setExpectedPriceLek(expectedPriceLek);
        u.setExpectedLabel(trimToNull(expectedLabel));
        u.setCondition(condition);
        if (status != null) u.setStatus(status);
        if (sortOrder != null) u.setSortOrder(sortOrder);
        else if (id == null) u.setSortOrder(nextSortOrder());
        return repo.save(u);
    }

    @Transactional
    public UpcomingProduct setStatus(Long id, UpcomingStatus status) {
        UpcomingProduct u = get(id);
        u.setStatus(status);
        return u;
    }

    @Transactional
    public UpcomingProduct setImage(Long id, MultipartFile file) {
        UpcomingProduct u = get(id);
        String previous = u.getImageFilename();
        u.setImageFilename(images.store(file));
        if (previous != null) images.delete(previous);
        return u;
    }

    @Transactional
    public void delete(Long id) {
        UpcomingProduct u = get(id);
        String image = u.getImageFilename();
        repo.delete(u);            // interests cascade in the schema
        if (image != null) images.delete(image);
    }

    /**
     * Records a customer's interest. Returns false when that phone number is already on the list, which
     * keeps a double tap on a slow connection from creating two entries to call.
     */
    @Transactional
    public boolean addInterest(Long id, String name, String phone) {
        UpcomingProduct u = get(id);
        if (u.getStatus() == UpcomingStatus.HIDDEN) throw new NotFoundException();
        String cleanPhone = phone.trim();
        if (interests.existsByUpcomingIdAndCustomerPhone(id, cleanPhone)) return false;
        UpcomingInterest i = new UpcomingInterest();
        i.setUpcoming(u);
        i.setCustomerName(name.trim());
        i.setCustomerPhone(cleanPhone);
        interests.save(i);
        return true;
    }

    @Transactional
    public void markNotified(Long interestId) {
        interests.findById(interestId).orElseThrow(NotFoundException::new).setNotified(true);
    }

    // ---- Helpers ----

    private String uniqueSlug(String base, Long selfId) {
        String slug = base;
        for (int i = 2; ; i++) {
            var existing = repo.findBySlug(slug);
            if (existing.isEmpty() || existing.get().getId().equals(selfId)) return slug;
            slug = base + "-" + i;
        }
    }

    private int nextSortOrder() {
        return repo.findAll().stream().mapToInt(UpcomingProduct::getSortOrder).max().orElse(0) + 10;
    }

    private static String trimToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
