package com.examforge.config;

import com.examforge.entity.*;
import com.examforge.entity.enums.*;
import com.examforge.repository.ExamCategoryRepository;
import com.examforge.repository.ExamRepository;
import com.examforge.repository.QuestionRepository;
import com.examforge.repository.SubjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * DEVELOPMENT ONLY. When SEED_DEMO_DATA=true and the question bank is empty, adds a few clearly-labelled
 * sample practice questions and three published sample exams so the app has something to show.
 * These are written for practice and are not official exam questions.
 */
@Component
@Order(20)
public class DemoDataSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final String SOURCE = "ExamForge sample practice question (not from an official paper)";

    private final boolean enabled;
    private final QuestionRepository questions;
    private final SubjectRepository subjects;
    private final ExamCategoryRepository categories;
    private final ExamRepository exams;

    public DemoDataSeeder(@Value("${app.seed-demo-data:false}") boolean enabled, QuestionRepository questions,
                          SubjectRepository subjects, ExamCategoryRepository categories, ExamRepository exams) {
        this.enabled = enabled;
        this.questions = questions;
        this.subjects = subjects;
        this.categories = categories;
        this.exams = exams;
    }

    private record Q(String subject, String topic, QuestionType type, Difficulty difficulty, String text,
                     List<String> options, List<Integer> correct, String explanation) {}

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled || questions.count() > 0) return;
        Map<String, Subject> bySlug = subjects.findAll().stream().collect(Collectors.toMap(Subject::getSlug, s -> s));

        List<Question> gs = save(bySlug, List.of(
                new Q("indian-polity", "Fundamental Rights", QuestionType.MCQ, Difficulty.EASY,
                        "Which Article of the Indian Constitution provides the Right to Constitutional Remedies?",
                        List.of("Article 14", "Article 19", "Article 32", "Article 21"), List.of(2),
                        "Article 32 lets a person move the Supreme Court to enforce Fundamental Rights."),
                new Q("indian-polity", "Parliament", QuestionType.MCQ, Difficulty.MEDIUM,
                        "Who presides over a joint sitting of both Houses of Parliament?",
                        List.of("The President", "The Speaker of the Lok Sabha", "The Vice-President", "The Prime Minister"), List.of(1),
                        "Under Article 118(4), the Speaker of the Lok Sabha presides over a joint sitting."),
                new Q("indian-polity", "Amendments", QuestionType.MCQ, Difficulty.MEDIUM,
                        "Fundamental Duties were added to the Constitution by which amendment?",
                        List.of("42nd Amendment", "44th Amendment", "73rd Amendment", "86th Amendment"), List.of(0),
                        "The 42nd Amendment (1976) added Part IV-A with the Fundamental Duties."),
                new Q("history", "Modern India", QuestionType.MCQ, Difficulty.EASY,
                        "In which year was the Battle of Plassey fought?",
                        List.of("1757", "1764", "1857", "1707"), List.of(0),
                        "The Battle of Plassey was fought on 23 June 1757."),
                new Q("geography", "Indian Geography", QuestionType.MCQ, Difficulty.EASY,
                        "Which is the largest state of India by area?",
                        List.of("Madhya Pradesh", "Maharashtra", "Rajasthan", "Uttar Pradesh"), List.of(2),
                        "Rajasthan is the largest state by area (about 3.42 lakh sq km)."),
                new Q("economy", "Banking", QuestionType.MCQ, Difficulty.EASY,
                        "Which institution issues currency notes in India (other than the one-rupee note)?",
                        List.of("Ministry of Finance", "Reserve Bank of India", "State Bank of India", "NITI Aayog"), List.of(1),
                        "The RBI issues banknotes; one-rupee notes and coins are issued by the Government of India."),
                new Q("general-science", "Biology", QuestionType.MCQ, Difficulty.EASY,
                        "Which gas do green plants take in for photosynthesis?",
                        List.of("Oxygen", "Nitrogen", "Carbon dioxide", "Hydrogen"), List.of(2),
                        "Plants use carbon dioxide and water to make glucose, releasing oxygen."),
                new Q("general-science", "Chemistry", QuestionType.TRUE_FALSE, Difficulty.EASY,
                        "The chemical formula of water is H2O.",
                        List.of("True", "False"), List.of(0),
                        "Each water molecule has two hydrogen atoms and one oxygen atom.")));

        List<Question> ssc = save(bySlug, List.of(
                new Q("quantitative-aptitude", "Percentages", QuestionType.MCQ, Difficulty.EASY,
                        "If 20% of a number is 50, what is the number?",
                        List.of("100", "200", "250", "500"), List.of(2), "0.20 × N = 50, so N = 250."),
                new Q("quantitative-aptitude", "Speed & distance", QuestionType.MCQ, Difficulty.EASY,
                        "A train covers 300 km in 5 hours. What is its average speed?",
                        List.of("50 km/h", "60 km/h", "65 km/h", "75 km/h"), List.of(1), "Speed = 300 ÷ 5 = 60 km/h."),
                new Q("quantitative-aptitude", "LCM & HCF", QuestionType.MCQ, Difficulty.EASY,
                        "What is the least common multiple of 12 and 18?",
                        List.of("6", "24", "36", "72"), List.of(2), "12 = 2²·3 and 18 = 2·3², so LCM = 2²·3² = 36."),
                new Q("reasoning", "Number series", QuestionType.MCQ, Difficulty.MEDIUM,
                        "Find the next number: 2, 6, 12, 20, 30, ?",
                        List.of("40", "42", "44", "36"), List.of(1), "The terms are n(n+1): 6 × 7 = 42."),
                new Q("reasoning", "Coding-decoding", QuestionType.MCQ, Difficulty.EASY,
                        "If CAT is written as DBU, how is DOG written in that code?",
                        List.of("EPH", "EOH", "CNF", "EPG"), List.of(0), "Each letter moves one step forward: D→E, O→P, G→H."),
                new Q("english-language", "Vocabulary", QuestionType.MCQ, Difficulty.EASY,
                        "Choose the word closest in meaning to ABUNDANT.",
                        List.of("Scarce", "Plentiful", "Narrow", "Hidden"), List.of(1), "Abundant means existing in large quantities.")));

        List<Question> jee = save(bySlug, List.of(
                new Q("physics", "Units", QuestionType.MCQ, Difficulty.EASY,
                        "What is the SI unit of force?",
                        List.of("Joule", "Newton", "Watt", "Pascal"), List.of(1), "1 newton = 1 kg·m/s²."),
                new Q("physics", "Gravitation", QuestionType.MCQ, Difficulty.EASY,
                        "Approximately what is the acceleration due to gravity at the Earth's surface?",
                        List.of("6.7 m/s²", "8.9 m/s²", "9.8 m/s²", "11.2 m/s²"), List.of(2), "g ≈ 9.8 m/s² (11.2 km/s is the escape velocity)."),
                new Q("physics", "Vectors", QuestionType.MULTI_SELECT, Difficulty.MEDIUM,
                        "Which of the following are vector quantities? (Select all that apply)",
                        List.of("Velocity", "Mass", "Force", "Temperature"), List.of(0, 2),
                        "Velocity and force have both magnitude and direction; mass and temperature are scalars."),
                new Q("physics", "Waves", QuestionType.TRUE_FALSE, Difficulty.EASY,
                        "Sound travels faster in a vacuum than in air.",
                        List.of("True", "False"), List.of(1), "Sound needs a medium, so it cannot travel through a vacuum at all."),
                new Q("chemistry", "Acids & bases", QuestionType.MCQ, Difficulty.EASY,
                        "What is the pH of pure water at 25 °C?",
                        List.of("0", "5", "7", "14"), List.of(2), "Pure water is neutral, with pH 7 at 25 °C.")));

        exam("upsc-civil-services", "UPSC Prelims GS — Sample Practice Test", gs, 2.00, 0.66, 15, Difficulty.MEDIUM,
                "Polity, history, geography, economy and science questions in the Prelims style (sample set).");
        exam("ssc-cgl-chsl", "SSC Quant & Reasoning — Sample Practice Test", ssc, 2.00, 0.50, 10, Difficulty.EASY,
                "Quick tier-1 style practice: percentages, speed, series, coding and vocabulary (sample set).");
        exam("jee-neet", "JEE Physics Basics — Sample Practice Test", jee, 4.00, 1.00, 15, Difficulty.MEDIUM,
                "Units, gravitation, vectors and waves with JEE-style +4/−1 marking (sample set).");

        log.info("Seeded {} sample questions and 3 sample exams (SEED_DEMO_DATA=true)", gs.size() + ssc.size() + jee.size());
    }

    private List<Question> save(Map<String, Subject> bySlug, List<Q> items) {
        List<Question> saved = new ArrayList<>();
        for (Q item : items) {
            Subject subject = bySlug.get(item.subject());
            if (subject == null) continue;
            Question q = new Question();
            q.setSubject(subject);
            q.setTopic(item.topic());
            q.setType(item.type());
            q.setDifficulty(item.difficulty());
            q.setQuestionText(item.text());
            q.setExplanation(item.explanation());
            q.setSource(SOURCE);
            q.setStatus(QuestionStatus.APPROVED);
            q.getTags().add("sample");
            List<QuestionOption> options = new ArrayList<>();
            for (int i = 0; i < item.options().size(); i++) {
                options.add(new QuestionOption(item.options().get(i), item.correct().contains(i)));
            }
            q.replaceOptions(options);
            saved.add(questions.save(q));
        }
        return saved;
    }

    private void exam(String categorySlug, String title, List<Question> qs, double marks, double negative, int minutes,
                      Difficulty difficulty, String description) {
        ExamCategory category = categories.findBySlug(categorySlug).orElse(null);
        if (category == null || qs.isEmpty()) return;
        Exam e = new Exam();
        e.setCategory(category);
        e.setTitle(title);
        e.setSlug(com.examforge.util.Slugs.unique(title, exams::existsBySlug));
        e.setDescription(description);
        e.setInstructions("""
                1. This is a sample practice test with questions written by the ExamForge team.
                2. The timer starts when you begin and cannot be paused.
                3. Each wrong answer carries negative marks as shown on the question.
                4. Use "Mark for review" to come back to a question before submitting.""");
        e.setType(ExamType.MOCK);
        e.setDifficulty(difficulty);
        e.setDurationMinutes(minutes);
        e.setResultRelease(ResultRelease.IMMEDIATE);
        int order = 1;
        for (Question q : qs) {
            e.getQuestions().add(new ExamQuestion(e, q, order++, BigDecimal.valueOf(marks).setScale(2),
                    BigDecimal.valueOf(negative).setScale(2)));
        }
        e.setPassMarks(e.totalMarks().multiply(BigDecimal.valueOf(0.4)).setScale(2, java.math.RoundingMode.HALF_UP));
        e.setStatus(ExamStatus.PUBLISHED);
        e.setPublishedAt(java.time.Instant.now());
        exams.save(e);
    }
}
