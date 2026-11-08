package dev.paulo.payments_service.common;

import dev.paulo.payments_service.payment.exception.OrderNotFoundException;
import dev.paulo.payments_service.payment.exception.OrderNotPayableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail handle(AuthenticationException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
        problem.setTitle("Authentication required");
        return problem;
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handle(AccessDeniedException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        problem.setTitle("Access denied");
        return problem;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handle(Exception ex) {
        LOG.error("Unexpected error", ex);
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setTitle("Internal error");
        return problem;
    }

    @ExceptionHandler(OrderNotFoundException.class)
    ProblemDetail handle(OrderNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setTitle("order not found");
        return problem;
    }

    @ExceptionHandler(OrderNotPayableException.class)
    ProblemDetail handle(OrderNotPayableException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("order cannot be paid");
        return problem;
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            problem.setDetail(null);
        }
        return response;
    }
}
