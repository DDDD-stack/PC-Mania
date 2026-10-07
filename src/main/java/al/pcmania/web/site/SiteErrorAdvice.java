package al.pcmania.web.site;

import al.pcmania.service.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice(basePackages = "al.pcmania.web.site")
@Slf4j
public class SiteErrorAdvice {

    @ExceptionHandler(Exception.class)
    public ModelAndView handle(Exception e, HttpServletRequest request) {
        HttpStatus declared = declaredStatus(e);
        if (declared == HttpStatus.NOT_FOUND) {
            ModelAndView notFound = new ModelAndView("error/404");
            notFound.setStatus(HttpStatus.NOT_FOUND);
            return notFound;
        }
        if (declared != null && declared.is4xxClientError()) {
            ModelAndView expected = new ModelAndView("error");
            expected.setStatus(declared);
            expected.addObject("status", declared.value());
            return expected;
        }
        String code = ErrorCode.next();
        log.error("Error {} on {} {}", code, request.getMethod(), request.getRequestURI(), e);
        ModelAndView page = new ModelAndView("error");
        page.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        page.addObject("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        page.addObject("errorCode", code);
        return page;
    }

    private static HttpStatus declaredStatus(Exception e) {
        if (e instanceof ErrorResponse response) return HttpStatus.resolve(response.getStatusCode().value());
        ResponseStatus annotation = AnnotatedElementUtils.findMergedAnnotation(e.getClass(), ResponseStatus.class);
        return annotation == null ? null : annotation.value();
    }
}
