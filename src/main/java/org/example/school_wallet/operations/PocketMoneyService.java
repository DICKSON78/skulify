package org.example.school_wallet.operations;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.example.school_wallet.academic.Student;
import org.example.school_wallet.common.error.Errors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Parent-controlled pocket money on a school card.
 *
 * <p>The card UID is read by an NFC/RFID/QR terminal at the school store. Every
 * top-up and purchase is an immutable ledger row; the card balance is derived
 * from those postings and can always be reconciled against terminal references.
 */
@Service
@Transactional
public class PocketMoneyService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final EntityManager em;
    private final OperationsSupport support;

    public PocketMoneyService(EntityManager em, OperationsSupport support) {
        this.em = em;
        this.support = support;
    }

    public PocketCard issueCard(Map<String, Object> body) {
        UUID studentId = support.uuid(body.get("studentId"));
        String uid = support.text(body.get("cardUid"));
        if (uid.isEmpty()) {
            uid = "CARD-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();
        }
        if (findCardByUid(uid) != null) {
            throw Errors.conflict("DUPLICATE_CARD", "Card UID already exists");
        }
        Student student = support.student(studentId);

        PocketCard card = new PocketCard();
        card.setCardUid(uid);
        card.setStudentId(studentId);
        card.setBalance(support.amount(body.get("balance"), ZERO));
        card.setDailyLimit(support.amount(body.get("dailyLimit"), ZERO));
        card.setWeeklyLimit(support.amount(body.get("weeklyLimit"), ZERO));
        card.setMonthlyLimit(support.amount(body.get("monthlyLimit"), ZERO));
        card.setStatus(PocketCard.Status.ACTIVE);
        card.setIssuedAt(support.now());
        em.persist(card);

        support.audit("POCKET_CARD_ISSUED", "POCKET_CARDS", card.getId(),
                Map.of("studentCode", student.getStudentCode(), "cardUid", uid));
        support.notifyGuardian(
                student,
                "Pocket money card issued",
                "Card " + uid + " is ready for use at the school store.",
                "pocket-cards",
                card.getId());
        return card;
    }

    public PocketCard card(UUID cardId) {
        PocketCard card = find(PocketCard.class, cardId);
        support.requireVisibleStudent(card.getStudentId());
        return card;
    }

    public Map<String, Object> balance(UUID cardId) {
        PocketCard card = card(cardId);
        Instant now = Instant.now();
        Map<String, Object> result = new HashMap<>();
        result.put("cardId", card.getId());
        result.put("studentId", card.getStudentId());
        result.put("balance", card.getBalance());
        result.put("dailyLimit", card.getDailyLimit());
        result.put("weeklyLimit", card.getWeeklyLimit());
        result.put("monthlyLimit", card.getMonthlyLimit());
        result.put("spentToday", spent(card.getId(), startOfDay(now), null));
        result.put("spentThisWeek", spent(card.getId(), startOfWeek(now), null));
        result.put("spentThisMonth", spent(card.getId(), startOfMonth(now), null));
        result.put("limits", limits(card.getId()));
        return result;
    }

    public PocketTransaction topUp(UUID cardId, Map<String, Object> body) {
        PocketCard card = card(cardId);
        support.requireVisibleStudent(card.getStudentId());
        if (card.getStatus() == PocketCard.Status.LOST
                || card.getStatus() == PocketCard.Status.CLOSED) {
            throw Errors.badRequest("CARD_NOT_ACTIVE", "Card cannot be topped up");
        }
        BigDecimal amount = support.amount(body.get("amount"), ZERO);
        if (amount.compareTo(ZERO) <= 0) {
            throw Errors.badRequest("INVALID_AMOUNT", "Top-up amount must be greater than zero");
        }
        String providerReference = support.text(body.get("providerReference"));
        if (!providerReference.isEmpty()) {
            PocketTransaction existing = findByProviderReference(providerReference);
            if (existing != null) {
                return existing;
            }
        }

        PocketTransaction txn = newTransaction(
                card,
                PocketTransaction.Type.TOPUP,
                amount,
                support.text(body.get("category")).isEmpty() ? "TOPUP" : support.text(body.get("category")),
                card.getBalance().add(amount),
                null,
                null,
                1,
                providerReference,
                "Parent/office top-up");
        em.persist(txn);
        card.setBalance(card.getBalance().add(amount));
        em.merge(card);

        support.audit("POCKET_TOPUP", "POCKET_TRANSACTIONS", txn.getId(), Map.of("amount", amount));
        support.notifyGuardian(
                support.student(card.getStudentId()),
                "Pocket money top-up",
                amount + " TZS was added to card " + card.getCardUid() + ". New balance: "
                        + card.getBalance() + " TZS.",
                "pocket-cards",
                card.getId());
        return txn;
    }

    public Map<String, Object> setLimits(UUID cardId, Map<String, Object> body) {
        PocketCard card = card(cardId);
        support.requireVisibleStudent(card.getStudentId());
        String category = support.text(body.get("category"));
        if (category.isEmpty()) {
            if (body.containsKey("dailyLimit")) {
                card.setDailyLimit(support.amount(body.get("dailyLimit"), ZERO));
            }
            if (body.containsKey("weeklyLimit")) {
                card.setWeeklyLimit(support.amount(body.get("weeklyLimit"), ZERO));
            }
            if (body.containsKey("monthlyLimit")) {
                card.setMonthlyLimit(support.amount(body.get("monthlyLimit"), ZERO));
            }
            em.merge(card);
        } else {
            SpendingLimit limit = em.createQuery(
                            """
                            select l from SpendingLimit l
                            where l.cardId = :cardId and l.category = :category
                            """,
                            SpendingLimit.class)
                    .setParameter("cardId", cardId)
                    .setParameter("category", category)
                    .getResultStream()
                    .findFirst()
                    .orElseGet(SpendingLimit::new);
            if (limit.getId() == null) {
                limit.setCardId(cardId);
                limit.setStudentId(card.getStudentId());
                limit.setCategory(category);
                em.persist(limit);
            }
            if (body.containsKey("dailyLimit")) {
                limit.setDailyLimit(support.amount(body.get("dailyLimit"), ZERO));
            }
            if (body.containsKey("weeklyLimit")) {
                limit.setWeeklyLimit(support.amount(body.get("weeklyLimit"), ZERO));
            }
            if (body.containsKey("monthlyLimit")) {
                limit.setMonthlyLimit(support.amount(body.get("monthlyLimit"), ZERO));
            }
            limit.setActive(!Boolean.FALSE.equals(body.get("active")));
        }
        support.audit("POCKET_LIMITS_UPDATED", "POCKET_CARDS", cardId, body);
        return balance(cardId);
    }

    public PocketTransaction charge(Map<String, Object> body) {
        String uid = support.text(body.get("cardUid"));
        PocketCard card = uid.isEmpty()
                ? find(PocketCard.class, support.uuid(body.get("cardId")))
                : findCardByUid(uid);
        if (card == null) {
            throw Errors.notFound("CARD_NOT_FOUND", "Card not found");
        }
        if (card.getStatus() != PocketCard.Status.ACTIVE) {
            throw Errors.badRequest("CARD_NOT_ACTIVE", "Card is not active");
        }
        String providerReference = support.text(body.get("providerReference"));
        if (!providerReference.isEmpty()) {
            PocketTransaction existing = findByProviderReference(providerReference);
            if (existing != null) {
                return existing;
            }
        }
        UUID terminalId = null;
        String terminalCode = support.text(body.get("terminalCode"));
        if (!terminalCode.isEmpty()) {
            StoreTerminal terminal = em.createQuery(
                            "select t from StoreTerminal t where t.code = :code", StoreTerminal.class)
                    .setParameter("code", terminalCode)
                    .getResultStream()
                    .findFirst()
                    .orElseThrow(() -> Errors.notFound("TERMINAL_NOT_FOUND", "Terminal not found"));
            if (terminal.getStatus() != StoreTerminal.Status.ACTIVE) {
                throw Errors.badRequest("TERMINAL_INACTIVE", "Terminal is not active");
            }
            terminal.setLastSeenAt(support.now());
            terminalId = terminal.getId();
        }

        UUID itemId = support.uuid(body.get("storeItemId"));
        StoreItem item = find(StoreItem.class, itemId);
        if (!item.isActive()) {
            throw Errors.badRequest("ITEM_INACTIVE", "Store item is not active");
        }
        int quantity = support.integer(body.get("quantity"), 1);
        if (quantity <= 0) {
            throw Errors.badRequest("INVALID_QUANTITY", "Quantity must be greater than zero");
        }
        if (item.getStockQuantity() < quantity) {
            throw Errors.conflict("OUT_OF_STOCK", "Not enough stock for " + item.getName());
        }
        BigDecimal amount = item.getPrice().multiply(BigDecimal.valueOf(quantity));
        String category = item.getCategory() == null || item.getCategory().isBlank()
                ? "GENERAL"
                : item.getCategory();

        BigDecimal balance = card.getBalance() == null ? ZERO : card.getBalance();
        if (amount.compareTo(balance) > 0) {
            throw Errors.conflict("INSUFFICIENT_BALANCE", "Insufficient pocket money balance");
        }
        Instant now = Instant.now();
        checkLimit("daily", card.getDailyLimit(), spent(card.getId(), startOfDay(now), category), amount);
        checkLimit("weekly", card.getWeeklyLimit(), spent(card.getId(), startOfWeek(now), category), amount);
        checkLimit("monthly", card.getMonthlyLimit(), spent(card.getId(), startOfMonth(now), category), amount);
        SpendingLimit categoryLimit = em.createQuery(
                        """
                        select l from SpendingLimit l
                        where l.cardId = :cardId and l.category = :category and l.active = true
                        """,
                        SpendingLimit.class)
                .setParameter("cardId", card.getId())
                .setParameter("category", category)
                .getResultStream()
                .findFirst()
                .orElse(null);
        if (categoryLimit != null) {
            checkLimit("category daily", categoryLimit.getDailyLimit(),
                    spent(card.getId(), startOfDay(now), category), amount);
            checkLimit("category weekly", categoryLimit.getWeeklyLimit(),
                    spent(card.getId(), startOfWeek(now), category), amount);
        }

        PocketTransaction txn = newTransaction(
                card,
                PocketTransaction.Type.PURCHASE,
                amount,
                category,
                balance.subtract(amount),
                terminalId,
                item.getId(),
                quantity,
                providerReference,
                "Store purchase: " + item.getName());
        em.persist(txn);
        card.setBalance(balance.subtract(amount));
        em.merge(card);
        item.setStockQuantity(item.getStockQuantity() - quantity);
        em.merge(item);

        Student student = support.student(card.getStudentId());
        BigDecimal warningLevel = card.getDailyLimit() == null
                ? ZERO
                : card.getDailyLimit().multiply(BigDecimal.valueOf(0.2));
        if (card.getBalance().compareTo(warningLevel) <= 0) {
            support.notifyGuardian(
                    student,
                    "Low pocket money balance",
                    "Card " + card.getCardUid() + " has " + card.getBalance()
                            + " TZS left after a purchase of " + amount + " TZS.",
                    "pocket-cards",
                    card.getId());
        }
        support.audit("POCKET_PURCHASE", "POCKET_TRANSACTIONS", txn.getId(),
                Map.of("amount", amount, "item", item.getName(), "terminal", terminalCode));
        return txn;
    }

    public PocketTransaction refund(UUID transactionId, String reason) {
        PocketTransaction original = find(PocketTransaction.class, transactionId);
        if (original.getType() != PocketTransaction.Type.PURCHASE
                || original.getStatus() != PocketTransaction.Status.POSTED) {
            throw Errors.badRequest("NOT_REFUNDABLE", "Only posted purchases can be refunded");
        }
        original.setStatus(PocketTransaction.Status.REVERSED);
        em.merge(original);

        PocketCard card = find(PocketCard.class, original.getCardId());
        BigDecimal balance = card.getBalance() == null ? ZERO : card.getBalance();
        PocketTransaction refund = newTransaction(
                card,
                PocketTransaction.Type.REFUND,
                original.getAmount(),
                original.getCategory(),
                balance.add(original.getAmount()),
                original.getTerminalId(),
                original.getStoreItemId(),
                original.getQuantity(),
                "REFUND-" + original.getReference(),
                "Refund: " + (reason == null ? "" : reason));
        em.persist(refund);
        card.setBalance(balance.add(original.getAmount()));
        em.merge(card);

        if (original.getStoreItemId() != null) {
            StoreItem item = em.find(StoreItem.class, original.getStoreItemId());
            if (item != null) {
                item.setStockQuantity(item.getStockQuantity() + original.getQuantity());
                em.merge(item);
            }
        }
        support.audit("POCKET_REFUND", "POCKET_TRANSACTIONS", refund.getId(),
                Map.of("reason", reason == null ? "" : reason));
        return refund;
    }

    public PocketCard setStatus(UUID cardId, PocketCard.Status status) {
        PocketCard card = card(cardId);
        card.setStatus(status);
        em.merge(card);
        support.audit("POCKET_CARD_STATUS", "POCKET_CARDS", cardId, Map.of("status", status.name()));
        return card;
    }

    public List<PocketTransaction> history(UUID cardId) {
        PocketCard card = card(cardId);
        return em.createQuery(
                        "select t from PocketTransaction t where t.cardId = :cardId order by t.createdAt desc",
                        PocketTransaction.class)
                .setParameter("cardId", card.getId())
                .getResultList();
    }

    public List<SpendingLimit> limits(UUID cardId) {
        return em.createQuery(
                        "select l from SpendingLimit l where l.cardId = :cardId order by l.category",
                        SpendingLimit.class)
                .setParameter("cardId", cardId)
                .getResultList();
    }

    private PocketTransaction newTransaction(
            PocketCard card,
            PocketTransaction.Type type,
            BigDecimal amount,
            String category,
            BigDecimal balanceAfter,
            UUID terminalId,
            UUID storeItemId,
            int quantity,
            String providerReference,
            String description) {
        PocketTransaction txn = new PocketTransaction();
        txn.setReference("PTX-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase());
        txn.setCardId(card.getId());
        txn.setStudentId(card.getStudentId());
        txn.setType(type);
        txn.setAmount(amount);
        txn.setCategory(category);
        txn.setBalanceAfter(balanceAfter);
        txn.setTerminalId(terminalId);
        txn.setStoreItemId(storeItemId);
        txn.setQuantity(quantity);
        txn.setProviderReference(providerReference == null || providerReference.isBlank()
                ? txn.getReference()
                : providerReference);
        txn.setStatus(PocketTransaction.Status.POSTED);
        txn.setDescription(description);
        return txn;
    }

    private void checkLimit(String label, BigDecimal limit, BigDecimal spent, BigDecimal amount) {
        if (limit == null || limit.compareTo(ZERO) <= 0) {
            return;
        }
        if (spent.add(amount).compareTo(limit) > 0) {
            throw Errors.conflict(
                    "SPENDING_LIMIT_EXCEEDED",
                    "Purchase exceeds the " + label + " spending limit");
        }
    }

    private BigDecimal spent(UUID cardId, Instant since, String category) {
        BigDecimal value = em.createQuery(
                        """
                        select sum(t.amount) from PocketTransaction t
                        where t.cardId = :cardId and t.type = 'PURCHASE'
                          and t.status = 'POSTED' and t.createdAt >= :since
                          and (:category is null or t.category = :category)
                        """,
                        BigDecimal.class)
                .setParameter("cardId", cardId)
                .setParameter("since", since)
                .setParameter("category", category)
                .getSingleResult();
        return value == null ? ZERO : value;
    }

    private PocketCard findCardByUid(String uid) {
        return em.createQuery("select c from PocketCard c where c.cardUid = :uid", PocketCard.class)
                .setParameter("uid", uid)
                .getResultStream()
                .findFirst()
                .orElse(null);
    }

    private PocketTransaction findByProviderReference(String reference) {
        return em.createQuery(
                        "select t from PocketTransaction t where t.providerReference = :reference",
                        PocketTransaction.class)
                .setParameter("reference", reference)
                .getResultStream()
                .findFirst()
                .orElse(null);
    }

    private <T> T find(Class<T> type, UUID id) {
        if (id == null) {
            return null;
        }
        return em.find(type, id);
    }

    private Instant startOfDay(Instant now) {
        return LocalDate.ofInstant(now, ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private Instant startOfWeek(Instant now) {
        return LocalDate.ofInstant(now, ZoneOffset.UTC)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();
    }

    private Instant startOfMonth(Instant now) {
        return LocalDate.ofInstant(now, ZoneOffset.UTC)
                .withDayOfMonth(1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();
    }
}
