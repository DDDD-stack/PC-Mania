package al.pcmania.web.site;

import al.pcmania.domain.Category;
import al.pcmania.domain.Enums.UpcomingStatus;
import al.pcmania.domain.Product;
import al.pcmania.service.CatalogService;
import al.pcmania.service.SeoService;
import al.pcmania.service.UpcomingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class SeoController {

    private final CatalogService catalog;
    private final UpcomingService upcoming;
    private final SeoService seo;

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    ResponseEntity<String> robots() {
        String body = """
                User-agent: *
                Disallow: /admin
                Disallow: /porosit/
                Allow: /

                Sitemap: %s
                """.formatted(seo.abs("/sitemap.xml"));
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofHours(12))).body(body);
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    ResponseEntity<String> sitemap() {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        url(xml, "/", null);
        for (Category c : catalog.categories()) url(xml, "/kategori/" + c.getSlug(), null);
        for (Product p : catalog.sitemapProducts()) url(xml, "/produkt/" + p.getSlug(), p.getListedAt());
        for (String page : List.of("/pc-me-porosi", "/rreth-nesh", "/kontakt", "/transporti-dhe-pagesa", "/kushtet-e-perdorimit")) {
            url(xml, page, null);
        }
        // Only worth indexing while something is actually listed there.
        if (upcoming.countByStatus(UpcomingStatus.VISIBLE) > 0) url(xml, "/se-shpejti", null);
        xml.append("</urlset>\n");
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofHours(1))).body(xml.toString());
    }

    private void url(StringBuilder xml, String path, LocalDateTime lastmod) {
        xml.append("  <url><loc>").append(HtmlUtils.htmlEscape(seo.abs(path))).append("</loc>");
        if (lastmod != null) xml.append("<lastmod>").append(lastmod.format(DateTimeFormatter.ISO_LOCAL_DATE)).append("</lastmod>");
        xml.append("</url>\n");
    }
}
