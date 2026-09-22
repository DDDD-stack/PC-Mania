package al.pcmania.web.site;

import al.pcmania.service.SeoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Static information pages. */
@Controller
@RequiredArgsConstructor
public class PageController {

    private final SeoService seo;

    @GetMapping("/rreth-nesh")
    String about(Model model) {
        model.addAttribute("seo", seo.page("Rreth nesh",
                "PCMania është një dyqan i vogël në Tiranë për karta grafike dhe pjesë kompjuteri, të testuara me kujdes para shitjes.",
                "/rreth-nesh"));
        return "site/pages/about";
    }

    @GetMapping("/kontakt")
    String contact(Model model) {
        model.addAttribute("seo", seo.page("Kontakt",
                "Na kontaktoni në telefon, WhatsApp ose Facebook për pyetje rreth produkteve, porosive ose ndërtimit të një PC.",
                "/kontakt"));
        return "site/pages/contact";
    }

    @GetMapping("/transporti-dhe-pagesa")
    String shipping(Model model) {
        model.addAttribute("seo", seo.page("Transporti dhe pagesa",
                "Marrje falas në Tiranë ose dërgesë me korrier në gjithë Shqipërinë. Pagesë në dorëzim ose me transfertë bankare.",
                "/transporti-dhe-pagesa"));
        return "site/pages/shipping";
    }

    @GetMapping("/kushtet-e-perdorimit")
    String terms(Model model) {
        model.addAttribute("seo", seo.page("Kushtet e përdorimit",
                "Kushtet e blerjes në PCMania: porositë, çmimet, garancia, kthimet dhe privatësia e të dhënave.",
                "/kushtet-e-perdorimit"));
        return "site/pages/terms";
    }
}
