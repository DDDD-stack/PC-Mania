package al.pcmania.web.site;

import al.pcmania.service.ComboService;
import al.pcmania.service.NotFoundException;
import al.pcmania.service.SeoService;
import al.pcmania.web.view.ComboView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@RequiredArgsConstructor
public class ComboController {

    private final ComboService combos;
    private final SeoService seo;

    @GetMapping("/paketa/{slug}")
    String detail(@PathVariable String slug, Model model) {
        ComboView c = combos.bySlug(slug).orElseThrow(NotFoundException::new);
        model.addAttribute("seo", c.visible() ? seo.combo(c) : seo.combo(c).withNoindex());
        model.addAttribute("c", c);
        model.addAttribute("whatsappLink", seo.whatsappLink("Përshëndetje! Jam i interesuar për paketën: " + c.title()
                + "\n" + seo.abs("/paketa/" + c.slug())));
        return "site/combo";
    }
}
