package org.example.school_wallet.operations;

import jakarta.persistence.EntityManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.example.school_wallet.academic.Teacher;
import org.example.school_wallet.common.error.Errors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Automates departments, staff work queues, store inventory and purchasing. */
@Service
@Transactional
public class SchoolOperationsService {

    private final EntityManager em;
    private final OperationsSupport support;

    public SchoolOperationsService(EntityManager em, OperationsSupport support) {
        this.em = em;
        this.support = support;
    }

    public Department assignHead(UUID departmentId, Map<String, Object> body) {
        Department department = find(Department.class, departmentId);
        UUID teacherId = support.uuid(body.get("headTeacherId"));
        if (teacherId == null) {
            throw Errors.badRequest("TEACHER_REQUIRED", "headTeacherId is required");
        }
        Teacher teacher = em.find(Teacher.class, teacherId);
        if (teacher == null) {
            throw Errors.notFound("TEACHER_NOT_FOUND", "Teacher not found");
        }
        department.setHeadTeacherId(teacherId);
        em.merge(department);
        support.audit("DEPARTMENT_HEAD_ASSIGNED", "DEPARTMENTS", departmentId,
                Map.of("teacherId", teacherId.toString()));
        return department;
    }

    public StockMovement recordMovement(Map<String, Object> body) {
        UUID itemId = support.uuid(body.get("itemId"));
        InventoryItem item = find(InventoryItem.class, itemId);
        String rawType = support.text(body.get("type")).toUpperCase();
        StockMovement.MovementType type;
        try {
            type = StockMovement.MovementType.valueOf(rawType);
        } catch (IllegalArgumentException ex) {
            throw Errors.badRequest("INVALID_MOVEMENT", "type must be IN, OUT or ADJUST");
        }
        int quantity = support.integer(body.get("quantity"), 0);
        if (quantity <= 0) {
            throw Errors.badRequest("INVALID_QUANTITY", "Quantity must be greater than zero");
        }
        int next = switch (type) {
            case IN -> item.getQuantity() + quantity;
            case OUT -> item.getQuantity() - quantity;
            case ADJUST -> quantity;
        };
        if (next < 0) {
            throw Errors.conflict("INSUFFICIENT_STOCK", "Stock cannot be negative");
        }
        item.setQuantity(next);
        em.merge(item);

        StockMovement movement = new StockMovement();
        movement.setItemId(itemId);
        movement.setType(type);
        movement.setQuantity(quantity);
        movement.setReference(reference("MOV"));
        movement.setReason(support.text(body.get("reason")));
        movement.setPerformedBy(support.actor());
        em.persist(movement);

        if (item.getQuantity() <= item.getReorderLevel()) {
            ensureReorderRequest(item);
        }
        support.audit("STOCK_MOVEMENT", "STOCK_MOVEMENTS", movement.getId(),
                Map.of("sku", item.getSku(), "quantity", quantity, "type", type.name()));
        return movement;
    }

    public PurchaseRequest approve(UUID requestId, Map<String, Object> body) {
        PurchaseRequest request = find(PurchaseRequest.class, requestId);
        if (request.getStatus() != PurchaseRequest.Status.PENDING) {
            throw Errors.badRequest("NOT_PENDING", "Only pending requests can be approved");
        }
        String decision = support.text(body.get("decision")).toUpperCase();
        if (decision.isEmpty()) {
            decision = Boolean.TRUE.equals(body.get("approved")) ? "APPROVE" : "REJECT";
        }
        if ("APPROVE".equals(decision)) {
            request.setStatus(PurchaseRequest.Status.APPROVED);
        } else if ("REJECT".equals(decision)) {
            request.setStatus(PurchaseRequest.Status.REJECTED);
        } else {
            throw Errors.badRequest("INVALID_DECISION", "decision must be APPROVE or REJECT");
        }
        request.setApprovedBy(support.actor());
        request.setApprovedAt(support.now());
        em.merge(request);

        SchoolTask task = new SchoolTask();
        task.setTitle("Process " + request.getReference());
        task.setDescription("Purchase request " + request.getStatus().name().toLowerCase()
                + " for " + request.getQuantity() + " units. " + support.text(request.getPurpose()));
        task.setDepartmentId(request.getDepartmentId());
        task.setStatus("OPEN");
        task.setPriority("NORMAL");
        task.setSourceResource("purchase-requests");
        task.setSourceId(request.getId());
        em.persist(task);

        support.audit("PURCHASE_REQUEST_DECIDED", "PURCHASE_REQUESTS", requestId,
                Map.of("decision", decision));
        return request;
    }

    public PurchaseRequest receive(UUID requestId, Map<String, Object> body) {
        PurchaseRequest request = find(PurchaseRequest.class, requestId);
        if (request.getStatus() != PurchaseRequest.Status.APPROVED) {
            throw Errors.badRequest("NOT_APPROVED", "Only approved requests can be received");
        }
        int quantity = support.integer(body.get("quantity"), request.getQuantity());
        if (quantity <= 0) {
            throw Errors.badRequest("INVALID_QUANTITY", "Quantity must be greater than zero");
        }
        if (request.getItemId() != null) {
            recordMovement(Map.of(
                    "itemId", request.getItemId().toString(),
                    "type", "IN",
                    "quantity", quantity,
                    "reason", "Received " + request.getReference()));
        }
        request.setStatus(PurchaseRequest.Status.RECEIVED);
        em.merge(request);
        support.audit("PURCHASE_REQUEST_RECEIVED", "PURCHASE_REQUESTS", requestId,
                Map.of("quantity", quantity));
        return request;
    }

    public SchoolTask completeTask(UUID taskId) {
        SchoolTask task = find(SchoolTask.class, taskId);
        task.setStatus("COMPLETED");
        task.setCompletedAt(support.now());
        em.merge(task);
        support.audit("SCHOOL_TASK_COMPLETED", "SCHOOL_TASKS", taskId, Map.of());
        return task;
    }

    public int generateReorderTasks() {
        List<InventoryItem> low = em.createQuery(
                        "select i from InventoryItem i where i.active = true and i.quantity <= i.reorderLevel",
                        InventoryItem.class)
                .getResultList();
        low.forEach(this::ensureReorderRequest);
        return low.size();
    }

    public Map<String, Object> summary() {
        Map<String, Object> result = new HashMap<>();
        result.put("departments", count("Department"));
        result.put("lowStock", em.createQuery(
                        "select count(i) from InventoryItem i where i.active = true and i.quantity <= i.reorderLevel",
                        Long.class)
                .getSingleResult());
        result.put("pendingRequests", em.createQuery(
                        "select count(r) from PurchaseRequest r where r.status = 'PENDING'", Long.class)
                .getSingleResult());
        result.put("openTasks", em.createQuery(
                        "select count(t) from SchoolTask t where t.status = 'OPEN'", Long.class)
                .getSingleResult());
        result.put("staffToday", em.createQuery(
                        "select count(a) from StaffAttendance a where a.attendanceDate = :today",
                        Long.class)
                .setParameter("today", java.time.LocalDate.now().toString())
                .getSingleResult());
        return result;
    }

    private void ensureReorderRequest(InventoryItem item) {
        long existing = em.createQuery(
                        """
                        select count(r) from PurchaseRequest r
                        where r.itemId = :itemId and r.status in ('PENDING','APPROVED','RECEIVED')
                        """,
                        Long.class)
                .setParameter("itemId", item.getId())
                .getSingleResult();
        if (existing > 0) {
            return;
        }
        PurchaseRequest request = new PurchaseRequest();
        request.setReference(reference("PR"));
        request.setItemId(item.getId());
        request.setQuantity(Math.max(1, item.getReorderLevel() * 2 - item.getQuantity()));
        request.setPurpose("Automatic reorder for " + item.getName());
        request.setStatus(PurchaseRequest.Status.PENDING);
        request.setRequestedBy("SYSTEM");
        em.persist(request);

        SchoolTask task = new SchoolTask();
        task.setTitle("Review reorder: " + item.getName());
        task.setDescription("Stock is " + item.getQuantity() + " with reorder level "
                + item.getReorderLevel() + ". Generated request " + request.getReference());
        task.setStatus("OPEN");
        task.setPriority("HIGH");
        task.setSourceResource("purchase-requests");
        task.setSourceId(request.getId());
        em.persist(task);
    }

    private long count(String entity) {
        return em.createQuery("select count(e) from " + entity + " e", Long.class).getSingleResult();
    }

    private String reference(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private <T> T find(Class<T> type, UUID id) {
        T entity = id == null ? null : em.find(type, id);
        if (entity == null) {
            throw Errors.notFound(type.getSimpleName().toUpperCase() + "_NOT_FOUND", "Record not found");
        }
        return entity;
    }
}
