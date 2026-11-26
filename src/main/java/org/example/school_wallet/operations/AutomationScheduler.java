package org.example.school_wallet.operations;

import java.util.function.Consumer;
import org.example.school_wallet.academic.NotificationService;
import org.example.school_wallet.common.tenant.TenantContext;
import org.example.school_wallet.tenant.Tenant;
import org.example.school_wallet.tenant.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * School-wide jobs that replace manual follow-up work. Each job runs once per
 * tenant with the tenant bound to the thread, so Hibernate's discriminator and
 * the audit trail stay correct outside an HTTP request.
 */
@Component
@ConditionalOnProperty(prefix = "app.automation", name = "enabled", havingValue = "true")
public class AutomationScheduler {

    private static final Logger log = LoggerFactory.getLogger(AutomationScheduler.class);

    private final TenantRepository tenants;
    private final FeeManagementService fees;
    private final LibraryService library;
    private final SchoolOperationsService operations;
    private final AcademicAutomationService academics;
    private final NotificationService notifications;

    public AutomationScheduler(
            TenantRepository tenants,
            FeeManagementService fees,
            LibraryService library,
            SchoolOperationsService operations,
            AcademicAutomationService academics,
            NotificationService notifications) {
        this.tenants = tenants;
        this.fees = fees;
        this.library = library;
        this.operations = operations;
        this.academics = academics;
        this.notifications = notifications;
    }

    /** Flags overdue invoices and sends guardian reminders. */
    @Scheduled(cron = "${app.automation.fee-reminder-cron:0 0 6 * * *}", zone = "UTC")
    public void feeReminders() {
        forEachTenant(tenantId -> {
            int count = fees.markOverdueAndRemind();
            if (count > 0) {
                log.info("Fee reminders: {} invoice(s) flagged for tenant {}", count, tenantId);
            }
        });
    }

    /** Marks expired loans and computes fines without librarian intervention. */
    @Scheduled(cron = "${app.automation.library-expiry-cron:0 5 6 * * *}", zone = "UTC")
    public void libraryExpiry() {
        forEachTenant(tenantId -> {
            int count = library.expireLoans();
            if (count > 0) {
                log.info("Library expiry: {} loan(s) flagged for tenant {}", count, tenantId);
            }
        });
    }

    /** Creates purchase requests and tasks when stock reaches reorder level. */
    @Scheduled(cron = "${app.automation.reorder-cron:0 10 6 * * *}", zone = "UTC")
    public void stockReorder() {
        forEachTenant(tenantId -> {
            int count = operations.generateReorderTasks();
            if (count > 0) {
                log.info("Stock reorder: {} item(s) below level for tenant {}", count, tenantId);
            }
        });
    }

    /** Publishes result cards whose exam has ended and alerts guardians. */
    @Scheduled(cron = "${app.automation.result-publish-cron:0 15 6 * * *}", zone = "UTC")
    public void resultPublishing() {
        forEachTenant(tenantId -> {
            int count = academics.publishDueResults();
            if (count > 0) {
                log.info("Result publishing: {} card(s) for tenant {}", count, tenantId);
            }
        });
    }

    /** Reminds teachers about tomorrow's duties and invigilation. */
    @Scheduled(cron = "${app.automation.duty-reminder-cron:0 20 6 * * *}", zone = "UTC")
    public void academicReminders() {
        forEachTenant(tenantId -> {
            int duties = academics.dutyReminders();
            int exams = academics.examReminders();
            if (duties + exams > 0) {
                log.info("Academic reminders: {} duty + {} exam(s) for tenant {}", duties, exams, tenantId);
            }
        });
    }

    /** Prepares the staff roll call, escalates late work and runs all academic jobs. */
    @Scheduled(cron = "${app.automation.daily-cron:0 25 6 * * *}", zone = "UTC")
    public void dailySchoolRun() {
        forEachTenant(tenantId -> {
            academics.runDaily();
            log.info("Daily school automation completed for tenant {}", tenantId);
        });
    }

    /** Drains the durable notification outbox through the configured adapter. */
    @Scheduled(
            fixedDelayString = "${app.automation.notification-dispatch-ms:900000}",
            initialDelayString = "${app.automation.notification-dispatch-ms:900000}")
    public void dispatchNotifications() {
        forEachTenant(tenantId -> {
            int count = notifications.dispatchPending();
            if (count > 0) {
                log.info("Notifications dispatched: {} for tenant {}", count, tenantId);
            }
        });
    }

    private void forEachTenant(Consumer<java.util.UUID> job) {
        for (Tenant tenant : tenants.findAll()) {
            if (TenantContext.SYSTEM_TENANT_ID.equals(tenant.getId())) {
                continue;
            }
            TenantContext.runAs(tenant.getId(), () -> {
                try {
                    job.accept(tenant.getId());
                } catch (Exception ex) {
                    log.error(
                            "Automation job failed for tenant {}",
                            tenant.getId(),
                            ex);
                }
            });
        }
    }
}
