/*******************************************************************************
 * Copyright (c) 2021 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc.
 ******************************************************************************/
package com.redhat.devtools.alizer.api;

import com.redhat.devtools.alizer.api.spi.LanguageEnricherProvider;
import com.redhat.devtools.alizer.api.utils.Utils;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

public class LanguageRecognizerImpl extends Recognizer implements LanguageRecognizer {

    private static final Logger logger = LoggerFactory.getLogger(LanguageRecognizerImpl.class);

    LanguageRecognizerImpl(RecognizerFactory builder) {
        super(builder);
    }

    public List<Language> analyze(String path) throws IOException {
        Map<LanguageFileItem, Integer> languagesDetected = new HashMap<>();
        // init dictionary with languages file
        LanguageFileHandler handler = LanguageFileHandler.get();

        List<File> files = getFiles(Paths.get(path));

        // save all extensions extracted from files + their occurrences
        Map<String, Long> extensions = files.stream().collect(groupingBy(file -> "." + FilenameUtils.getExtension(file.getName()), counting()));

        // get languages belonging to extensions found
        extensions.keySet().forEach(extension -> {
            List<LanguageFileItem> languages = handler.getLanguagesByExtension(extension);
            if (languages.isEmpty()) return;
            languages.forEach(language -> {
                LanguageFileItem tmpLanguage = language.getGroup().isEmpty() ? language : handler.getLanguageByName(language.getGroup());
                long percentage = languagesDetected.getOrDefault(tmpLanguage, 0) + extensions.get(extension);
                languagesDetected.put(tmpLanguage, (int) percentage);
            });
        });

        // get only programming language and calculate percentage
        int totalProgrammingOccurences = (int) languagesDetected.keySet().stream().
                filter(lang -> lang.getType().equalsIgnoreCase("programming")).
                mapToLong(languagesDetected::get).sum();

        // only keep programming language which consists of atleast the 2% of the project
        return languagesDetected.keySet().stream().
                filter(lang -> lang.getType().equalsIgnoreCase("programming")).
                filter(lang -> (double)languagesDetected.get(lang) / totalProgrammingOccurences > 0.02).
                map(lang -> new Language(lang.getName(), lang.getAliases(), (double)languagesDetected.get(lang) / totalProgrammingOccurences * 100, lang.canBeComponent())).
                map(lang -> getDetailedLanguage(lang, files)).
                sorted(Comparator.comparingDouble(Language::getUsageInPercentage).reversed()).
                collect(Collectors.toList());
    }

    private Language getDetailedLanguage(Language language, List<File> files) {
        LanguageEnricherProvider enricher = Utils.getEnricherByLanguage(language.getName());
        if (enricher != null) {
            try {
                return enricher.create().getEnrichedLanguage(language, files);
            } catch (IOException e) {
                logger.warn(e.getLocalizedMessage(), e);
            }
        }
        return language;
    }
}
