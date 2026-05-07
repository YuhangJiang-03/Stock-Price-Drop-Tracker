package com.stocktracker.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Two related concerns wired up in one place so production deployments can
 * package the React build inside the Spring Boot jar and serve everything
 * from a single port:
 *
 * <ol>
 *   <li><b>API prefix.</b> Every {@code @RestController} mapping is rewritten
 *       to live under {@code /api/...} without touching the individual
 *       {@code @RequestMapping} annotations. The frontend already targets
 *       {@code /api/*} (see {@code services/api.js}), so this lets the same
 *       URLs work in dev (via Vite proxy) and in prod (single origin).</li>
 *
 *   <li><b>SPA fallback.</b> Static assets are served from
 *       {@code classpath:/static/} (where Maven copies {@code frontend/dist}
 *       at package time). For any path that doesn't match a real file <i>and</i>
 *       isn't an API call, we forward to {@code index.html} so React Router
 *       can take over — that's what makes a hard refresh on
 *       {@code /profile} or {@code /stocks/42} actually work.</li>
 * </ol>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix("/api",
            handlerType -> handlerType.isAnnotationPresent(RestController.class));
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
            .addResourceLocations("classpath:/static/")
            .resourceChain(true)
            .addResolver(new SpaResourceResolver());
    }

    /**
     * Resolves a request to either (a) the literal static file under
     * {@code classpath:/static/}, or (b) {@code index.html} so React Router
     * can render the SPA route. API calls (already prefixed {@code /api/})
     * never hit this resolver — Spring's controller handler matches first.
     * As a belt-and-braces guard we still return {@code null} for the
     * {@code api/} prefix so a typo'd API URL produces a clean 404 instead
     * of a confusing HTML page.
     */
    private static final class SpaResourceResolver extends PathResourceResolver {
        private static final ClassPathResource INDEX = new ClassPathResource("/static/index.html");

        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            if (resourcePath.startsWith("api/")) {
                return null;
            }
            Resource requested = location.createRelative(resourcePath);
            if (requested.exists() && requested.isReadable()) {
                return requested;
            }
            return INDEX.exists() ? INDEX : null;
        }
    }
}
