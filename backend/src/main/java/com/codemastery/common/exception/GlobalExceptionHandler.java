package com.codemastery.common.exception;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * CT18.minimum MVC errors using Spring's RFC 9457 support. Security filter-chain
 * entry points/access-denied handlers are owned by Auth and do not run through MVC advice.
 * Public details are fixed English text: exception/constraint messages and rejected values
 * are deliberately not used, even when a constraint interpolates its validated value.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Set<String> VALIDATION_CODES = Set.of("NotNull", "NotBlank", "NotEmpty",
            "Size", "Min", "Max", "DecimalMin", "DecimalMax", "Positive", "PositiveOrZero",
            "Negative", "NegativeOrZero", "Email", "Pattern", "typeMismatch");

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Object> handleNotFound(ResourceNotFoundException exception, WebRequest request) {
        return response(HttpStatus.NOT_FOUND, HttpHeaders.EMPTY, request, List.of());
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<Object> handleBusinessRule(BusinessRuleException exception, WebRequest request) {
        return response(exception.getStatus(), HttpHeaders.EMPTY, request, List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception exception, WebRequest request) throws Exception {
        if (exception instanceof AuthenticationException || exception instanceof AccessDeniedException) {
            // Let Auth's filter-chain handling retain ownership, including method-security failures.
            throw exception;
        }
        ResponseStatus declared = AnnotatedElementUtils.findMergedAnnotation(exception.getClass(), ResponseStatus.class);
        if (declared != null) {
            return response(declared.code(), HttpHeaders.EMPTY, request, List.of());
        }
        // Do not log arbitrary messages/causes: they can contain SQL, credentials or input.
        logger.error("Unhandled MVC failure of type " + exception.getClass().getName());
        return response(HttpStatus.INTERNAL_SERVER_ERROR, HttpHeaders.EMPTY, request, List.of());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return response(status, headers, request, bindingErrors(exception.getBindingResult().getAllErrors()));
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception, HttpHeaders headers, HttpStatusCode status,
            WebRequest request) {
        // A violated return-value contract is a server error, not bad client input.
        if (exception.isForReturnValue()) {
            return response(status, headers, request, List.of());
        }
        List<ValidationError> errors = new ArrayList<>();
        for (var result : exception.getParameterValidationResults()) {
            if (result instanceof ParameterErrors parameterErrors) {
                errors.addAll(bindingErrors(parameterErrors.getAllErrors()));
            } else {
                String field = parameterName(result.getMethodParameter());
                for (var error : result.getResolvableErrors()) {
                    String[] codes = error.getCodes();
                    errors.add(validationError(field, codes == null || codes.length == 0
                            ? "Invalid" : codes[codes.length - 1]));
                }
            }
        }
        if (!exception.getCrossParameterValidationResults().isEmpty()) {
            errors.add(validationError("request", "Invalid"));
        }
        return response(status, headers, request, errors);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // Preserve framework statuses and semantic headers (e.g. Allow for 405).
        // Never forward ErrorResponseException.reason or framework diagnostic bodies.
        return response(status, headers, request, List.of());
    }

    private ResponseEntity<Object> response(HttpStatusCode status, HttpHeaders originalHeaders,
            WebRequest request, List<ValidationError> errors) {
        if (request instanceof ServletWebRequest servletRequest
                && servletRequest.getResponse() != null && servletRequest.getResponse().isCommitted()) {
            return null;
        }
        HttpStatus standardStatus = HttpStatus.resolve(status.value());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, safeDetail(status.value()));
        problem.setType(URI.create("about:blank"));
        problem.setTitle(standardStatus == null ? "HTTP Error" : standardStatus.getReasonPhrase());
        if (request instanceof ServletWebRequest servletRequest) {
            // getRequestURI excludes the query; preserve existing percent encoding.
            try {
                problem.setInstance(URI.create(servletRequest.getRequest().getRequestURI()));
            } catch (IllegalArgumentException exception) {
                problem.setInstance(URI.create("/"));
            }
        }
        if (!errors.isEmpty() && status.is4xxClientError()) {
            problem.setProperty("errors", errors.stream()
                    .sorted(Comparator.comparing(ValidationError::field).thenComparing(ValidationError::code))
                    .distinct().toList());
        }
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(originalHeaders);
        headers.remove(HttpHeaders.CONTENT_LENGTH);
        headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return new ResponseEntity<>(problem, headers, status);
    }

    private static List<ValidationError> bindingErrors(List<ObjectError> errors) {
        return errors.stream().map(error -> validationError(
                error instanceof FieldError fieldError ? fieldError.getField() : "request", error.getCode()))
                .toList();
    }

    private static ValidationError validationError(String field, String code) {
        String safeCode = VALIDATION_CODES.contains(code == null ? "" : code) ? code : "Invalid";
        String message = switch (safeCode) {
            case "NotNull", "NotBlank", "NotEmpty" -> "This field is required.";
            case "Size" -> "This field has an invalid length.";
            case "Email" -> "Enter a valid email address.";
            case "Pattern", "typeMismatch" -> "This field has an invalid format.";
            case "Min", "Max", "DecimalMin", "DecimalMax", "Positive", "PositiveOrZero",
                    "Negative", "NegativeOrZero" -> "This field is outside the allowed range.";
            default -> "This field is invalid.";
        };
        return new ValidationError(field, safeCode, message);
    }

    private static String parameterName(MethodParameter parameter) {
        RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
        if (requestParam != null && (!requestParam.name().isEmpty() || !requestParam.value().isEmpty())) {
            return requestParam.name().isEmpty() ? requestParam.value() : requestParam.name();
        }
        PathVariable pathVariable = parameter.getParameterAnnotation(PathVariable.class);
        if (pathVariable != null && (!pathVariable.name().isEmpty() || !pathVariable.value().isEmpty())) {
            return pathVariable.name().isEmpty() ? pathVariable.value() : pathVariable.name();
        }
        return parameter.getParameterName() == null ? "arg" + parameter.getParameterIndex()
                : parameter.getParameterName();
    }

    private static String safeDetail(int status) {
        return switch (status) {
            case 400 -> "The request is invalid.";
            case 401 -> "Authentication failed.";
            case 403 -> "Access is denied.";
            case 404 -> "The requested resource is unavailable.";
            case 405 -> "The HTTP method is not supported for this resource.";
            case 406 -> "The requested response format is not supported.";
            case 409 -> "The request conflicts with an existing resource.";
            case 413 -> "The request is too large.";
            case 415 -> "The request media type is not supported.";
            default -> status >= 500 ? "An unexpected error occurred." : "The request could not be completed.";
        };
    }

    public record ValidationError(String field, String code, String message) { }
}
