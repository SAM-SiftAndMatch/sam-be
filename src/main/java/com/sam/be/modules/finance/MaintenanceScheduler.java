package com.sam.be.modules.finance;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MaintenanceScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(MaintenanceScheduler.class);
    private final FinanceStore store;
    private final MaintenanceService service;

    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void escalate() {
        for (var row :
                store.rows(
                        "SELECT id FROM maintenance_tickets WHERE kind='WARRANTY' AND closed_at IS NULL AND overdue_at IS NULL AND (response_due_at<=? AND status='OPEN' AND NOT EXISTS(SELECT 1 FROM maintenance_ticket_events e JOIN finance_payables p ON p.id=maintenance_tickets.payable_id WHERE e.ticket_id=maintenance_tickets.id AND e.actor_id=p.owner_id) OR resolution_due_at<=? AND status<>'FIX_SUBMITTED' OR (status='FIX_SUBMITTED' AND fix_submitted_at<=?)) ORDER BY resolution_due_at LIMIT 100",
                        store.time(store.now()),
                        store.time(store.now()),
                        store.time(store.now().minus(java.time.Duration.ofHours(48))))) {
            UUID id = FinanceStore.id(row, "id");
            try {
                service.escalateOverdue(id);
            } catch (Exception ex) {
                LOG.warn(
                        "Maintenance escalation failed for {} ({})",
                        id,
                        ex.getClass().getSimpleName());
            }
        }
    }
}
