package al.pcmania.web;

import al.pcmania.service.ImageStorage;
import al.pcmania.service.ImageStorage.Size;
import org.springframework.stereotype.Component;

/** Image URL helpers exposed to templates as {@code @img}. */
@Component("img")
public class Img {

    public static final String PLACEHOLDER = "/images/placeholder.svg";

    public String thumb(String filename) {
        return filename == null ? PLACEHOLDER : ImageStorage.url(Size.thumb, filename);
    }

    public String medium(String filename) {
        return filename == null ? PLACEHOLDER : ImageStorage.url(Size.medium, filename);
    }

    public String full(String filename) {
        return filename == null ? PLACEHOLDER : ImageStorage.url(Size.full, filename);
    }

    /** srcset listing all variants with their max widths. */
    public String srcset(String filename) {
        if (filename == null) return null;
        return thumb(filename) + " 400w, " + medium(filename) + " 800w, " + full(filename) + " 1600w";
    }
}
