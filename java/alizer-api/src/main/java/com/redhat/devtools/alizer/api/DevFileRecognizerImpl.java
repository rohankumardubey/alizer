package com.redhat.devtools.alizer.api;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public class DevFileRecognizerImpl extends Recognizer implements DevFileRecognizer {

    private RecognizerFactory builder;

    DevFileRecognizerImpl(RecognizerFactory builder) {
        super(builder);
        this.builder = builder;
    }

    /**
     * It tries to detect a component in the root and return a devfile for it. If there is no component in root,
     * it start looking for some components within the sub-folders. If there are none, it uses the outcome of analyze.
     * @param srcPath path (root) where to start the search
     * @param devfileTypes list of all devfileTypes to pick a devfile from
     * @return the devfile that matches the project the most
     * @throws IOException if an error occurred
     */
    public <T extends DevfileType> T selectDevFileFromTypes(String srcPath, List<T> devfileTypes) throws IOException {
        ComponentRecognizer componentRecognizer = builder.createComponentRecognizer();
        List<Component> componentsInRoot = componentRecognizer.analyzeRoot(srcPath);
        if (!componentsInRoot.isEmpty()) {
            return selectDevFileFromTypes(componentsInRoot.get(0).getLanguages(), devfileTypes);
        }

        List<Component> componentsWithinFullProject = componentRecognizer.analyze(srcPath);
        if (!componentsWithinFullProject.isEmpty()) {
            return selectDevFileFromTypes(componentsWithinFullProject.get(0).getLanguages(), devfileTypes);
        }

        LanguageRecognizer languageRecognizer = builder.createLanguageRecognizer();
        List<Language> languages = languageRecognizer.analyze(srcPath);
        return selectDevFileFromTypes(languages, devfileTypes);
    }

    public <T extends DevfileType> T selectDevFileFromTypes(List<Language> languages, List<T> devfileTypes) {
        for (Language language: languages) {
            Optional<LanguageScore> score = devfileTypes.stream().map(devfileType -> new LanguageScore(language, devfileType)).sorted().findFirst();
            if (score.isPresent() && score.get().getScore() > 0) {
                return (T) score.get().getDevfileType();
            }
        }
        return null;
    }
}
