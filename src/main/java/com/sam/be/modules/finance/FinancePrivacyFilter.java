package com.sam.be.modules.finance;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class FinancePrivacyFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.startsWith("/api/v1/finance")
                || path.startsWith("/api/v1/wallets")
                || path.contains("maintenance")) {
            response.setHeader("Cache-Control", "no-store, max-age=0");
            response.setHeader("Pragma", "no-cache");
        }
        if (path.equals("/api/v1/payments/pay2s-ipn")
                || ("POST".equals(request.getMethod())
                        && (path.startsWith("/api/v1/finance") || path.contains("maintenance")))) {
            byte[] body = request.getInputStream().readNBytes(32769);
            if (body.length > 32768) {
                response.sendError(413);
                return;
            }
            HttpServletRequest wrapper =
                    new HttpServletRequestWrapper(request) {
                        @Override
                        public ServletInputStream getInputStream() {
                            var input = new java.io.ByteArrayInputStream(body);
                            return new ServletInputStream() {
                                public int read() {
                                    return input.read();
                                }

                                public boolean isFinished() {
                                    return input.available() == 0;
                                }

                                public boolean isReady() {
                                    return true;
                                }

                                public void setReadListener(ReadListener listener) {
                                    throw new UnsupportedOperationException();
                                }
                            };
                        }

                        @Override
                        public java.io.BufferedReader getReader() {
                            return new java.io.BufferedReader(
                                    new java.io.InputStreamReader(
                                            getInputStream(),
                                            java.nio.charset.StandardCharsets.UTF_8));
                        }
                    };
            chain.doFilter(wrapper, response);
            return;
        }
        chain.doFilter(request, response);
    }
}
