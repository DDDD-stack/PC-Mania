package al.pcmania.service.chat;

import al.pcmania.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.expression.ThymeleafEvaluationContext;

import java.util.List;
import java.util.Set;

@Component
@Slf4j
public class ShopPolicyText {

    record Source(String title, String path, String template, Set<String> selectors) {}

    static final List<Source> SOURCES = List.of(
            new Source("Kushtet e përdorimit", "/kushtet-e-perdorimit", "site/pages/terms", Set.of("div.prose")),
            new Source("Transporti dhe pagesa", "/transporti-dhe-pagesa", "site/pages/shipping", Set.of("div.prose")),
            new Source("Rreth nesh", "/rreth-nesh", "site/pages/about", Set.of("div.prose")),
            new Source("Nderro (këmbimi)", "/nderro", "site/trade", Set.of("p.lead-sm", "ol.steps")));

    private final ITemplateEngine templates;
    private final ApplicationContext context;
    private final AppProperties props;
    private volatile String text;

    public ShopPolicyText(ITemplateEngine templates, ApplicationContext context, AppProperties props) {
        this.templates = templates;
        this.context = context;
        this.props = props;
    }

    public String text() {
        String t = text;
        if (t == null) {
            t = render();
            text = t;
        }
        return t;
    }

    private String render() {
        StringBuilder out = new StringBuilder();
        for (Source s : SOURCES) {
            try {
                Context ctx = new Context();
                ctx.setVariable("site", props);
                ctx.setVariable(ThymeleafEvaluationContext.THYMELEAF_EVALUATION_CONTEXT_CONTEXT_VARIABLE_NAME,
                        new ThymeleafEvaluationContext(context, null));
                String html = templates.process(s.template(), s.selectors(), ctx);
                out.append("### ").append(s.title()).append(" (").append(s.path()).append(")\n").append(plain(html)).append('\n');
            } catch (RuntimeException e) {
                log.warn("Could not read {} for the assistant: {}", s.template(), e.getMessage());
            }
        }
        return out.toString().strip();
    }

    static String plain(String html) {
        StringBuilder b = new StringBuilder();
        for (Element e : Jsoup.parseBodyFragment(html).select("h2, h3, p, li")) {
            String line = e.text().replaceAll("\\s+", " ").strip();
            if (line.isEmpty()) continue;
            if (e.is("h2, h3")) b.append("## ").append(line);
            else if (e.is("li")) b.append("- ").append(line);
            else b.append(line);
            b.append('\n');
        }
        return b.toString();
    }
}
