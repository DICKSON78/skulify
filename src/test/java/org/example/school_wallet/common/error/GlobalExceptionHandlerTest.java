package org.example.school_wallet.common.error;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.skulify.pocketmoney.common.PocketMoneyErrorCode;
import com.skulify.pocketmoney.common.PocketMoneyException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void missingResourceReturnsNotFoundInsteadOfInternalError() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v3/api-docs");
        NoResourceFoundException ex =
                new NoResourceFoundException(HttpMethod.GET, "/v3/api-docs", "No static resource");

        var problem = handler.handleNotFound(ex, request);

        assertEquals(HttpStatus.NOT_FOUND.value(), problem.getStatus());
        assertEquals("NOT_FOUND", problem.getProperties().get("code"));
        assertEquals("/v3/api-docs", problem.getInstance().getPath());
    }

    @Test
    void pocketMoneyErrorsUseStableCodesAndStatuses() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/pocket-money/topups");

        var problem = handler.handlePocketMoney(
                new PocketMoneyException(PocketMoneyErrorCode.IDEMPOTENCY_REQUIRED, "Key required"),
                request);

        assertEquals(HttpStatus.BAD_REQUEST.value(), problem.getStatus());
        assertEquals("IDEMPOTENCY_REQUIRED", problem.getProperties().get("code"));
    }
}
