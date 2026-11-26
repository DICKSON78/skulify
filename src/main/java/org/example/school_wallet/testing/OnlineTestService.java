package org.example.school_wallet.testing;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.example.school_wallet.academic.Enrolment;
import org.example.school_wallet.academic.SchoolClass;
import org.example.school_wallet.academic.Student;
import org.example.school_wallet.academic.Subject;
import org.example.school_wallet.common.error.Errors;
import org.example.school_wallet.common.tenant.TenantContext;
import org.example.school_wallet.common.time.TimeValues;
import org.example.school_wallet.operations.OperationsSupport;
import org.example.school_wallet.security.AuthenticatedUser;
import org.example.school_wallet.security.CurrentUser;
import org.example.school_wallet.security.JwtService;
import org.example.school_wallet.security.ResourceAccessPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Teacher workflows and anonymous student sessions for computer-based tests. */
@Service
@Transactional
public class OnlineTestService {

    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final EntityManager em;
    private final OnlineTestRepository tests;
    private final TestAttemptRepository attempts;
    private final OperationsSupport support;
    private final ResourceAccessPolicy policy;
    private final JwtService jwtService;
    private final ObjectMapper mapper;

    public OnlineTestService(
            EntityManager em,
            OnlineTestRepository tests,
            TestAttemptRepository attempts,
            OperationsSupport support,
            ResourceAccessPolicy policy,
            JwtService jwtService,
            ObjectMapper mapper) {
        this.em = em;
        this.tests = tests;
        this.attempts = attempts;
        this.support = support;
        this.policy = policy;
        this.jwtService = jwtService;
        this.mapper = mapper;
    }

    // ------------------------------------------------------------------
    // Teacher workflows
    // ------------------------------------------------------------------

    public Map<String, Object> create(Map<String, Object> body) {
        AuthenticatedUser user = CurrentUser.require();
        policy.require("online-tests", ResourceAccessPolicy.Action.CREATE);

        UUID classId = support.uuid(body.get("classId"));
        UUID subjectId = support.uuid(body.get("subjectId"));
        if (em.find(SchoolClass.class, classId) == null || em.find(Subject.class, subjectId) == null) {
            throw Errors.badRequest("INVALID_TEST_TARGET", "Choose an existing class and subject");
        }
        String title = support.text(body.get("title"));
        if (title.isBlank()) {
            throw Errors.badRequest("TITLE_REQUIRED", "Test title is required");
        }
        List<Map<String, Object>> questions = questions(body.get("questions"));
        String accessCode = support.text(body.get("accessCode"));
        if (accessCode.isBlank()) {
            accessCode = digits(6);
        }
        int duration = support.integer(body.get("durationMinutes"), 30);
        if (duration <= 0) {
            throw Errors.badRequest("INVALID_DURATION", "durationMinutes must be greater than zero");
        }

        OnlineTest test = new OnlineTest();
        test.setCode(code(10));
        test.setAccessCode(accessCode);
        test.setTitle(title);
        test.setDescription(support.text(body.get("description")));
        test.setClassId(classId);
        test.setSubjectId(subjectId);
        test.setStartAt(support.text(body.get("startAt")));
        test.setEndAt(support.text(body.get("endAt")));
        test.setDurationMinutes(duration);
        test.setQuestionsJson(json(questions));
        test.setStatus("DRAFT");
        test.setCreatedBy(user.id());
        em.persist(test);
        em.flush();
        support.audit("ONLINE_TEST_CREATED", "ONLINE_TESTS", test.getId(),
                Map.of("questions", questions.size(), "classId", classId.toString()));
        return view(test, false);
    }

    public List<Map<String, Object>> list() {
        AuthenticatedUser user = CurrentUser.require();
        policy.require("online-tests", ResourceAccessPolicy.Action.READ);
        List<OnlineTest> rows = policy.isPrivileged(user)
                ? tests.findAll()
                : tests.findByCreatedBy(user.id());
        List<Map<String, Object>> result = new ArrayList<>();
        for (OnlineTest test : rows) {
            result.add(view(test, true));
        }
        return result;
    }

    public Map<String, Object> setStatus(UUID testId, String status) {
        policy.require("online-tests", ResourceAccessPolicy.Action.UPDATE);
        OnlineTest test = find(testId);
        String next = support.text(status).toUpperCase();
        if (!List.of("DRAFT", "PUBLISHED", "CLOSED").contains(next)) {
            throw Errors.badRequest("INVALID_STATUS", "status must be DRAFT, PUBLISHED or CLOSED");
        }
        test.setStatus(next);
        em.merge(test);
        support.audit("ONLINE_TEST_STATUS", "ONLINE_TESTS", testId, Map.of("status", next));
        return view(test, true);
    }

    public String link(UUID testId) {
        policy.require("online-tests", ResourceAccessPolicy.Action.READ);
        return link(find(testId));
    }

    /** Expected students versus actual attempts, for the teacher who set the test. */
    public Map<String, Object> results(UUID testId) {
        policy.require("online-tests", ResourceAccessPolicy.Action.READ);
        OnlineTest test = find(testId);
        List<Enrolment> enrolments = em.createQuery(
                        "select e from Enrolment e where e.classId = :classId and e.status = 'ACTIVE'",
                        Enrolment.class)
                .setParameter("classId", test.getClassId())
                .getResultList();
        List<TestAttempt> submitted = attempts.findByTestId(testId);
        Map<UUID, TestAttempt> byStudent = new LinkedHashMap<>();
        submitted.forEach(attempt -> byStudent.put(attempt.getStudentId(), attempt));

        List<Map<String, Object>> rows = new ArrayList<>();
        int attended = 0;
        double totalScore = 0;
        for (Enrolment enrolment : enrolments) {
            Student student = em.find(Student.class, enrolment.getStudentId());
            if (student == null) {
                continue;
            }
            TestAttempt attempt = byStudent.get(student.getId());
            boolean hasAttempt = attempt != null;
            if (hasAttempt) {
                attended++;
                totalScore += attempt.getScore();
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("studentId", student.getId());
            row.put("name", name(student));
            row.put("registrationNo", student.getStudentCode());
            row.put("attended", hasAttempt);
            row.put("status", attempt == null ? "NOT_ATTENDED" : attempt.getStatus());
            row.put("score", attempt == null ? null : attempt.getScore());
            row.put("maxScore", attempt == null ? null : attempt.getMaxScore());
            row.put("submittedAt", attempt == null ? null : attempt.getSubmittedAt());
            rows.add(row);
        }
        rows.sort((left, right) -> {
            boolean a = Boolean.TRUE.equals(left.get("attended"));
            boolean b = Boolean.TRUE.equals(right.get("attended"));
            if (a != b) {
                return a ? -1 : 1;
            }
            return text(left.get("name")).compareToIgnoreCase(text(right.get("name")));
        });

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("test", view(test, false));
        result.put("expected", enrolments.size());
        result.put("attended", attended);
        result.put("notAttended", enrolments.size() - attended);
        result.put("averageScore", attended == 0 ? 0 : round(totalScore / attended));
        result.put("students", rows);
        return result;
    }

    // ------------------------------------------------------------------
    // Anonymous student workflows (tenant comes from the signed link)
    // ------------------------------------------------------------------

    public Map<String, Object> preview(UUID testId, String accessCode) {
        OnlineTest test = published(testId, accessCode);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("title", test.getTitle());
        result.put("description", test.getDescription());
        result.put("durationMinutes", test.getDurationMinutes());
        result.put("questionCount", questions(test).size());
        result.put("className", em.find(SchoolClass.class, test.getClassId()) == null
                ? "" : em.find(SchoolClass.class, test.getClassId()).getName());
        result.put("subjectName", em.find(Subject.class, test.getSubjectId()) == null
                ? "" : em.find(Subject.class, test.getSubjectId()).getName());
        return result;
    }

    public Map<String, Object> start(UUID testId, String accessCode, String registrationNo) {
        OnlineTest test = published(testId, accessCode);
        Student student = studentByCode(registrationNo);
        requireEnrolled(student.getId(), test.getClassId());

        TestAttempt attempt = attempts.findByTestIdAndStudentId(testId, student.getId())
                .orElse(null);
        if (attempt != null && "SUBMITTED".equals(attempt.getStatus())) {
            throw Errors.conflict("TEST_ALREADY_SUBMITTED", "This test has already been submitted");
        }
        if (attempt == null) {
            attempt = new TestAttempt();
            attempt.setTestId(testId);
            attempt.setStudentId(student.getId());
            attempt.setRegistrationNo(registrationNo);
            attempt.setAnswersJson("{}");
            attempt.setScore(0);
            attempt.setMaxScore(totalPoints(test));
            attempt.setStatus("IN_PROGRESS");
            attempt.setStartedAt(TimeValues.now());
            em.persist(attempt);
            em.flush();
        }

        List<Map<String, Object>> questions = questions(test).stream()
                .map(question -> publicQuestion(question))
                .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("attemptId", attempt.getId());
        result.put("title", test.getTitle());
        result.put("durationMinutes", test.getDurationMinutes());
        result.put("studentName", name(student));
        result.put("questions", questions);
        return result;
    }

    public Map<String, Object> submit(UUID testId, String accessCode, String registrationNo, Object answers) {
        OnlineTest test = published(testId, accessCode);
        Student student = studentByCode(registrationNo);
        TestAttempt attempt = attempts.findByTestIdAndStudentId(testId, student.getId())
                .orElseThrow(() -> Errors.badRequest("TEST_NOT_STARTED", "Start the test before submitting"));

        List<Map<String, Object>> questions = questions(test);
        List<Object> supplied = answers instanceof List<?> list
                ? new ArrayList<>(list) : new ArrayList<>();
        double score = 0;
        int correct = 0;
        List<Map<String, Object>> details = new ArrayList<>();
        for (int index = 0; index < questions.size(); index++) {
            Map<String, Object> question = questions.get(index);
            int expected = ((Number) question.getOrDefault("correctIndex", 0)).intValue();
            int answer = index < supplied.size() ? (int) number(supplied.get(index), -1) : -1;
            double points = number(question.get("points"), 1);
            boolean right = answer == expected;
            if (right) {
                score += points;
                correct++;
            }
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("question", text(question.get("prompt")));
            detail.put("yourAnswer", answer < 0 ? "" : text(question.getOrDefault("answer" + answer, answer)));
            detail.put("correctAnswer", text(question.getOrDefault("answer" + expected, expected)));
            detail.put("correct", right);
            details.add(detail);
        }

        attempt.setScore(round(score));
        attempt.setMaxScore(totalPoints(test));
        attempt.setAnswersJson(json(List.of(supplied)));
        attempt.setStatus("SUBMITTED");
        attempt.setSubmittedAt(TimeValues.now());
        em.merge(attempt);
        support.notifyGuardian(student, "Online test submitted",
                test.getTitle() + ": " + round(score) + " / " + totalPoints(test),
                "online-tests", test.getId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("attemptId", attempt.getId());
        result.put("score", attempt.getScore());
        result.put("maxScore", attempt.getMaxScore());
        result.put("correct", correct);
        result.put("total", questions.size());
        result.put("details", details);
        return result;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Map<String, Object> view(OnlineTest test, boolean includeLink) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", test.getId());
        row.put("code", test.getCode());
        row.put("accessCode", test.getAccessCode());
        row.put("title", test.getTitle());
        row.put("description", test.getDescription());
        row.put("status", test.getStatus());
        row.put("classId", test.getClassId());
        row.put("subjectId", test.getSubjectId());
        row.put("durationMinutes", test.getDurationMinutes());
        row.put("questionCount", questions(test).size());
        row.put("attempts", attempts.countByTestId(test.getId()));
        row.put("createdAt", test.getCreatedAt());
        if (includeLink) {
            row.put("link", link(test));
        }
        return row;
    }

    private String link(OnlineTest test) {
        return "/test/" + jwtService.issueTestLink(
                test.getTenantId(), test.getId(), test.getAccessCode());
    }

    private OnlineTest find(UUID id) {
        if (id == null) {
            throw Errors.badRequest("TEST_REQUIRED", "Test id is required");
        }
        OnlineTest test = tests.findById(id)
                .orElseThrow(() -> Errors.notFound("TEST_NOT_FOUND", "Online test not found"));
        AuthenticatedUser user = CurrentUser.get().orElse(null);
        if (user != null && !policy.isPrivileged(user) && !user.id().equals(test.getCreatedBy())) {
            throw Errors.forbidden("NOT_YOUR_TEST", "You did not prepare this test");
        }
        return test;
    }

    private OnlineTest published(UUID testId, String accessCode) {
        if (testId == null) {
            throw Errors.badRequest("INVALID_TEST_LINK", "The test link is not valid");
        }
        OnlineTest test = tests.findById(testId)
                .orElseThrow(() -> Errors.notFound("TEST_NOT_FOUND", "Online test not found"));
        if (!"PUBLISHED".equals(test.getStatus())) {
            throw Errors.badRequest("TEST_NOT_OPEN", "This test is not open for attempts");
        }
        if (!test.getAccessCode().equalsIgnoreCase(support.text(accessCode))) {
            throw Errors.forbidden("INVALID_ACCESS_CODE", "The teacher code is not correct");
        }
        return test;
    }

    private Student studentByCode(String registrationNo) {
        String code = support.text(registrationNo);
        if (code.isBlank()) {
            throw Errors.badRequest("REGISTRATION_REQUIRED", "Registration number is required");
        }
        List<Student> matches = em.createQuery(
                        "select s from Student s where s.studentCode = :code", Student.class)
                .setParameter("code", code)
                .getResultList();
        if (matches.isEmpty()) {
            throw Errors.badRequest("UNKNOWN_REGISTRATION", "Registration number was not recognised");
        }
        return matches.get(0);
    }

    private void requireEnrolled(UUID studentId, UUID classId) {
        Long count = em.createQuery(
                        """
                        select count(e) from Enrolment e
                        where e.studentId = :studentId and e.classId = :classId and e.status = 'ACTIVE'
                        """,
                        Long.class)
                .setParameter("studentId", studentId)
                .setParameter("classId", classId)
                .getSingleResult();
        if (count == null || count == 0) {
            throw Errors.forbidden("NOT_ENROLLED", "This student is not enrolled for the test class");
        }
    }

    private List<Map<String, Object>> questions(OnlineTest test) {
        try {
            return mapper.readValue(test.getQuestionsJson(), new TypeReference<>() {});
        } catch (Exception ex) {
            throw new IllegalStateException("Stored test questions are not valid JSON", ex);
        }
    }

    private List<Map<String, Object>> questions(Object value) {
        if (!(value instanceof List<?> rows) || rows.isEmpty()) {
            throw Errors.badRequest("NO_QUESTIONS", "Add at least one question");
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object value1 : rows) {
            if (!(value1 instanceof Map<?, ?> raw)) {
                continue;
            }
            String prompt = support.text(raw.get("prompt"));
            if (prompt.isBlank()) {
                prompt = support.text(raw.get("question"));
            }
            if (prompt.isBlank()) {
                throw Errors.badRequest("QUESTION_REQUIRED", "Every question needs a prompt");
            }
            List<String> options = new ArrayList<>();
            Object optionValue = raw.get("options");
            if (optionValue instanceof List<?> list) {
                list.forEach(option -> options.add(support.text(option)));
            } else if (optionValue instanceof String text && !text.isBlank()) {
                Arrays.stream(text.split("\\|")).forEach(option -> options.add(option.trim()));
            }
            if (options.size() < 2) {
                throw Errors.badRequest("INVALID_OPTIONS", "Every question needs at least two options");
            }
            int correct = support.integer(raw.get("correctIndex"), 0);
            if (correct < 0 || correct >= options.size()) {
                throw Errors.badRequest("INVALID_CORRECT_ANSWER", "correctIndex is outside the options");
            }
            Map<String, Object> question = new LinkedHashMap<>();
            question.put("prompt", prompt);
            question.put("options", options);
            question.put("correctIndex", correct);
            question.put("points", Math.max(1, number(raw.get("points"), 1)));
            for (int index = 0; index < options.size(); index++) {
                question.put("answer" + index, options.get(index));
            }
            result.add(question);
        }
        return result;
    }

    private Map<String, Object> publicQuestion(Map<String, Object> question) {
        Map<String, Object> copy = new LinkedHashMap<>(question);
        copy.remove("correctIndex");
        for (String key : List.of(copy.keySet().toArray(new String[0]))) {
            if (key.startsWith("answer")) {
                copy.remove(key);
            }
        }
        return copy;
    }

    private double totalPoints(OnlineTest test) {
        return questions(test).stream()
                .mapToDouble(question -> number(question.get("points"), 1))
                .sum();
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not serialise test data", ex);
        }
    }

    private String code(int length) {
        StringBuilder value = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            value.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return value.toString();
    }

    private String digits(int length) {
        StringBuilder value = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            value.append(RANDOM.nextInt(10));
        }
        return value.toString();
    }

    private double number(Object value, double fallback) {
        if (value == null || support.text(value).isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(support.text(value));
        } catch (NumberFormatException ex) {
            throw Errors.badRequest("INVALID_NUMBER", "Invalid number: " + value);
        }
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String text(Object value) {
        return support.text(value);
    }

    private String name(Student student) {
        return (student.getFirstName() + " " + student.getLastName()).trim();
    }
}
