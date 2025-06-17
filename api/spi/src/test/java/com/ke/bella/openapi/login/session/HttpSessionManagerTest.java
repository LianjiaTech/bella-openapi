package com.ke.bella.openapi.login.session;

import com.ke.bella.openapi.Operator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class HttpSessionManagerTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private SessionProperty sessionProperty;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private HttpSessionManager httpSessionManager;

    private final String BASE_URL = "http://localhost:8080";
    private final String COOKIE_NAME = "test_session_cookie";
    private final String VALID_TOKEN = "valid-token-123";

    @BeforeEach
    void setUp() {
        // Instantiate HttpSessionManager with mocks.
        // Note: @InjectMocks won't work here because BASE_URL is not a mock.
        // We need to manually instantiate.
        httpSessionManager = new HttpSessionManager(restTemplate, BASE_URL, sessionProperty);
        when(sessionProperty.getCookieName()).thenReturn(COOKIE_NAME);
    }

    // --- getSession Tests ---

    @Test
    void getSession_success() {
        Cookie sessionCookie = new Cookie(COOKIE_NAME, VALID_TOKEN);
        when(request.getCookies()).thenReturn(new Cookie[]{sessionCookie});

        Operator expectedOperator = new Operator(); // Populate if needed
        ResponseEntity<Operator> responseEntity = new ResponseEntity<>(expectedOperator, HttpStatus.OK);
        String expectedUrl = BASE_URL + "/openapi/login?token=" + VALID_TOKEN;
        when(restTemplate.exchange(expectedUrl, HttpMethod.GET, null, Operator.class))
                .thenReturn(responseEntity);

        Operator actualOperator = httpSessionManager.getSession(request);

        assertNotNull(actualOperator);
        assertEquals(expectedOperator, actualOperator);
        verify(restTemplate).exchange(expectedUrl, HttpMethod.GET, null, Operator.class);
    }

    @Test
    void getSession_noCookie() {
        when(request.getCookies()).thenReturn(null);
        Operator actualOperator = httpSessionManager.getSession(request);
        assertNull(actualOperator);
        verify(restTemplate, never()).exchange(anyString(), any(), any(), eq(Operator.class));
    }

    @Test
    void getSession_noCookieNameInProperties() {
        when(sessionProperty.getCookieName()).thenReturn(null);
        // Cookies may or may not exist, but without cookie name, token extraction should fail
        Cookie sessionCookie = new Cookie("some_other_cookie", VALID_TOKEN);
        when(request.getCookies()).thenReturn(new Cookie[]{sessionCookie});

        Operator actualOperator = httpSessionManager.getSession(request);
        assertNull(actualOperator);
        verify(restTemplate, never()).exchange(anyString(), any(), any(), eq(Operator.class));
    }


    @Test
    void getSession_tokenInvalid_401() {
        Cookie sessionCookie = new Cookie(COOKIE_NAME, "invalid-token");
        when(request.getCookies()).thenReturn(new Cookie[]{sessionCookie});

        String expectedUrl = BASE_URL + "/openapi/login?token=invalid-token";
        when(restTemplate.exchange(expectedUrl, HttpMethod.GET, null, Operator.class))
                .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED, "Invalid token"));

        Operator actualOperator = httpSessionManager.getSession(request);

        assertNull(actualOperator);
        verify(restTemplate).exchange(expectedUrl, HttpMethod.GET, null, Operator.class);
    }

    @Test
    void getSession_serverError_500() {
        Cookie sessionCookie = new Cookie(COOKIE_NAME, VALID_TOKEN);
        when(request.getCookies()).thenReturn(new Cookie[]{sessionCookie});

        String expectedUrl = BASE_URL + "/openapi/login?token=" + VALID_TOKEN;
        when(restTemplate.exchange(expectedUrl, HttpMethod.GET, null, Operator.class))
                .thenThrow(new HttpClientErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "Server down"));

        Operator actualOperator = httpSessionManager.getSession(request);
        assertNull(actualOperator);
    }

    // --- destroySession Tests ---

    @Test
    void destroySession_success() {
        Cookie sessionCookie = new Cookie(COOKIE_NAME, VALID_TOKEN);
        when(request.getCookies()).thenReturn(new Cookie[]{sessionCookie});

        String expectedUrl = BASE_URL + "/openapi/logout?token=" + VALID_TOKEN;
        ResponseEntity<Void> responseEntity = new ResponseEntity<>(HttpStatus.OK);
        when(restTemplate.exchange(expectedUrl, HttpMethod.POST, null, Void.class))
                .thenReturn(responseEntity);

        httpSessionManager.destroySession(request, response);

        verify(restTemplate).exchange(expectedUrl, HttpMethod.POST, null, Void.class);
    }

    @Test
    void destroySession_noCookie() {
        when(request.getCookies()).thenReturn(null);
        httpSessionManager.destroySession(request, response);
        verify(restTemplate, never()).exchange(anyString(), any(), any(), eq(Void.class));
    }

    @Test
    void destroySession_callFails_shouldNotThrow() {
        Cookie sessionCookie = new Cookie(COOKIE_NAME, VALID_TOKEN);
        when(request.getCookies()).thenReturn(new Cookie[]{sessionCookie});
        String expectedUrl = BASE_URL + "/openapi/logout?token=" + VALID_TOKEN;
        when(restTemplate.exchange(expectedUrl, HttpMethod.POST, null, Void.class))
            .thenThrow(new HttpClientErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        assertDoesNotThrow(() -> httpSessionManager.destroySession(request, response));
        verify(restTemplate).exchange(expectedUrl, HttpMethod.POST, null, Void.class);
    }

    // --- Other Method Tests ---

    @Test
    void renew_doesNothing() {
        // Just call it, ensure no exceptions and no RestTemplate interactions
        assertDoesNotThrow(() -> httpSessionManager.renew(request));
        verifyNoInteractions(restTemplate); // After setup, no new interactions
    }

    @Test
    void createOperator_throwsNotImplemented() {
        assertThrows(NotImplementedException.class, () -> {
            httpSessionManager.create(new Operator(), request, response);
        });
    }

    @Test
    void createSecret_throwsNotImplemented() {
        assertThrows(NotImplementedException.class, () -> {
            httpSessionManager.create("secret", request, response);
        });
    }

    @Test
    void saveTicket_throwsNotImplemented() {
        assertThrows(NotImplementedException.class, () -> httpSessionManager.saveTicket("ticket"));
    }

    @Test
    void isValidTicket_throwsNotImplemented() {
        assertThrows(NotImplementedException.class, () -> httpSessionManager.isValidTicket("ticket"));
    }

    @Test
    void removeTicket_throwsNotImplemented() {
        assertThrows(NotImplementedException.class, () -> httpSessionManager.removeTicket("ticket"));
    }

    @Test
    void userRepoInitialized_returnsFalse() {
        assertFalse(httpSessionManager.userRepoInitialized());
    }
}
