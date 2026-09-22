package al.pcmania.web.api;

import al.pcmania.service.NotFoundException;
import al.pcmania.service.OrderService;
import al.pcmania.web.api.ApiDtos.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = ApiExceptionHandler.class)
public class ApiExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ErrorResponse notFound() {
        return new ErrorResponse("not_found", "Nuk u gjet.");
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse badRequest(RuntimeException e) {
        return new ErrorResponse("bad_request", e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse unreadable() {
        return new ErrorResponse("bad_request", "Kërkesa nuk është e vlefshme.");
    }

    @ExceptionHandler(OrderService.OutOfStockException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ErrorResponse outOfStock() {
        return new ErrorResponse("out_of_stock", "Sasia në stok nuk mjafton.");
    }
}
