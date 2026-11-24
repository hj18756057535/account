package com.hypers.account.integration.web;

import com.hypers.account.integration.AccountProviderException;
import com.hypers.account.integration.menu.web.AccountIntegrationErrorResponse;
import com.hypers.account.integration.menu.web.AccountIntegrationMessages;
import javax.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 服务接口只返回受控错误码，调用方按自身语言展示。 */
@RestControllerAdvice(assignableTypes = AccountProviderController.class)
public class AccountProviderExceptionHandler {
    @ExceptionHandler(AccountProviderException.class)
    public ResponseEntity<AccountIntegrationErrorResponse> known(AccountProviderException error, HttpServletRequest request) {
        return result(error.getStatus(), error.getCode(), request);
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class, MissingRequestHeaderException.class})
    public ResponseEntity<AccountIntegrationErrorResponse> invalid(Exception error, HttpServletRequest request) {
        return result(422, "PROVIDER_REQUEST_INVALID", request);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<AccountIntegrationErrorResponse> unavailable(RuntimeException error, HttpServletRequest request) {
        return result(503, "PROVIDER_UNAVAILABLE", request);
    }

    private ResponseEntity<AccountIntegrationErrorResponse> result(int status, String code, HttpServletRequest request) {
        AccountIntegrationErrorResponse response = new AccountIntegrationErrorResponse();
        response.setCode(code);
        response.setMessage(AccountIntegrationMessages.text(request, code));
        response.setTraceId(UUID.randomUUID().toString());
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .header("Content-Language", AccountIntegrationMessages.language(request))
                .header("Vary", "Accept-Language")
                .body(response);
    }
}
