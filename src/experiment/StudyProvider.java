package experiment;

import java.nio.file.Path;

/** Extension point through which a separate reproducibility repository supplies a study design. */
public interface StudyProvider {
    StudySpecification create(Path baseWorkbook);
}
