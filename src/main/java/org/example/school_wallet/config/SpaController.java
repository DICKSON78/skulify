package org.example.school_wallet.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Forwards every React route, including create/edit deep links, to the bundled SPA. */
@Configuration
public class SpaController implements WebMvcConfigurer {

    private static final String SPA_VIEW = "forward:/index.html";

    private static final String[] PAGES = {
            "/login",
            "/dashboard",
            "/students",
            "/teachers",
            "/classes",
            "/subjects",
            "/attendance",
            "/grades",
            "/fees",
            "/timetable",
            "/reports",
            "/settings",
            "/fee-categories",
            "/fee-plans",
            "/invoices",
            "/invoice-items",
            "/pocket-money",
            "/pocket-cards",
            "/spending-limits",
            "/pocket-transactions",
            "/store-pos",
            "/store-items",
            "/store-terminals",
            "/inventory-items",
            "/stock-movements",
            "/purchase-requests",
            "/departments",
            "/staff-attendance",
            "/school-tasks",
            "/library-desk",
            "/books",
            "/book-copies",
            "/book-loans",
            "/book-reservations",
            "/lab-sessions",
            "/operations-hub",
            "/timetable-generator",
            "/marks-upload",
            "/duty-roster",
            "/results-planning",
            "/parent-portal",
            "/online-tests",
            "/periods",
            "/exams",
            "/exam-sessions",
            "/duty-assignments",
            "/teacher-assignments",
            "/school-documents",
            "/report-cards",
            "/parent-letters",
            "/staff-leaves",
            "/school-events",
            "/test/{token}"
    };

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        for (String page : PAGES) {
            registry.addViewController(page).setViewName(SPA_VIEW);
        }
        // Resource pages are full show/create/edit routes; direct links and refreshes must work too.
        for (String page : PAGES) {
            if (page.equals("/test/{token}")) {
                continue;
            }
            registry.addViewController(page + "/new").setViewName(SPA_VIEW);
            registry.addViewController(page + "/{id}").setViewName(SPA_VIEW);
            registry.addViewController(page + "/{id}/edit").setViewName(SPA_VIEW);
        }
    }
}
