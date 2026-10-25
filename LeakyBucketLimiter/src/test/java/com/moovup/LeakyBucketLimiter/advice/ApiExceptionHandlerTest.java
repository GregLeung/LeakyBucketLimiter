package com.moovup.LeakyBucketLimiter.advice;

import com.moovup.LeakyBucketLimiter.exception.BucketOverflowException;
import com.moovup.LeakyBucketLimiter.exception.UserNotFoundException;
import com.moovup.LeakyBucketLimiter.model.AllowRequest;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void handleMethodArgumentNotValid_shouldReturnBadRequest() throws NoSuchMethodException {
        AllowRequest request = new AllowRequest(System.currentTimeMillis() + 5_000L, "user1");
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(request, "allowRequestBody");
        bindingResult.rejectValue("timestamp", "ValidFutureTimestamp", "Timestamp must be in the future.");

        MethodParameter methodParameter = new MethodParameter(
                AllowRequest.class.getDeclaredConstructor(Long.class, String.class), 0);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(methodParameter, bindingResult);

        ResponseEntity<Map<String, Object>> response = handler.handleMethodArgumentNotValid(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(400, response.getBody().get("status"));
        assertEquals("Bad Request", response.getBody().get("error"));
        assertEquals("Timestamp must be in the future.", response.getBody().get("message"));
    }

    @Test
    void handleUserNotFound_shouldReturnNotFound() {
        ResponseEntity<Map<String, Object>> response = handler.handleUserNotFound(new UserNotFoundException("missing user"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(404, response.getBody().get("status"));
        assertEquals("Not Found", response.getBody().get("error"));
        assertEquals("missing user", response.getBody().get("message"));
    }

    @Test
    void handleBucketOverflow_shouldReturnForbidden() {
        ResponseEntity<Map<String, Object>> response = handler.handleBucketOverflow(new BucketOverflowException("overflow"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(403, response.getBody().get("status"));
        assertEquals("Forbidden", response.getBody().get("error"));
        assertEquals("overflow", response.getBody().get("message"));
    }

}
