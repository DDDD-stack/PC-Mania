package al.pcmania.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.time.Duration;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AppProperties props;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Uploaded filenames are random and never reused, so they can be cached for a long time.
        String location = Path.of(props.uploadDir()).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/img/p/**")
                .addResourceLocations(location)
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
    }
}
