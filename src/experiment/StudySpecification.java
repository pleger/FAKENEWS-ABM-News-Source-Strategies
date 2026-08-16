package experiment;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Complete, typed study definition while the ABM itself remains independently executable. */
public final class StudySpecification {
    private final String id;
    private final String title;
    private final Path baseWorkbook;
    private final List<ResearchQuestionSpecification> researchQuestions;

    public StudySpecification(String id, String title, Path baseWorkbook,
                              List<ResearchQuestionSpecification> researchQuestions) {
        if (id == null || !id.matches("[a-zA-Z0-9._-]+")) {
            throw new IllegalArgumentException("Study id must be filesystem-safe: " + id);
        }
        if (researchQuestions.isEmpty()) throw new IllegalArgumentException("Study requires research questions");
        this.id = id;
        this.title = title;
        this.baseWorkbook = baseWorkbook;
        this.researchQuestions = Collections.unmodifiableList(new ArrayList<>(researchQuestions));
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public Path getBaseWorkbook() { return baseWorkbook; }
    public List<ResearchQuestionSpecification> getResearchQuestions() { return researchQuestions; }

    /** Selects question identifiers while preserving their declared study order. */
    public List<ResearchQuestionSpecification> select(Set<String> ids) {
        if (ids == null || ids.isEmpty()) return researchQuestions;
        ArrayList<ResearchQuestionSpecification> selected = new ArrayList<>();
        Set<String> missing = new HashSet<>(ids);
        for (ResearchQuestionSpecification question : researchQuestions) {
            if (ids.contains(question.getId())) {
                selected.add(question);
                missing.remove(question.getId());
            }
        }
        if (!missing.isEmpty()) throw new IllegalArgumentException("Unknown research questions: " + missing);
        return selected;
    }
}
