package com.fantasy.db.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static com.fantasy.db.config.InternalApiKeyFilter.API_KEY_HEADER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class InternalApiKeyFilterTest {

    private static final String TEST_KEY = "secret-key";

    private InternalApiKeyFilter filterWithKey;
    private InternalApiKeyFilter filterWithoutKey;

    @BeforeEach
    void setUp() {
        filterWithKey = new InternalApiKeyFilter(TEST_KEY);
        filterWithoutKey = new InternalApiKeyFilter("");
    }

    @Test
    void shouldNotFilter_whenApiKeyNotConfigured() {
        assertThat(filterWithoutKey.shouldNotFilter(new MockHttpServletRequest())).isTrue();
    }

    @Test
    void shouldNotFilter_forActuatorPaths() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/actuator/health");
        assertThat(filterWithKey.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldFilter_forApiPaths() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/users");
        assertThat(filterWithKey.shouldNotFilter(request)).isFalse();
    }

    @Test
    void rejects_requestWithoutHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filterWithKey.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(chain);
    }

    @Test
    void rejects_requestWithWrongKey() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(API_KEY_HEADER, "wrong-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filterWithKey.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(chain);
    }

    @Test
    void allows_requestWithCorrectKey() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(API_KEY_HEADER, TEST_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filterWithKey.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(request, response);
    }
}
