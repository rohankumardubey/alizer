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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LanguageFileHandler {
    private static final Logger logger = LoggerFactory.getLogger(LanguageFileHandler.class);

    private static final String LANGUAGES_YAML_PATH = "languages.yml";
    private static final String LANGUAGES_CUSTOMIZATION_YAML_PATH = "languages-customization.yml";
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
    private static LanguageFileHandler INSTANCE;
    private Map<String, LanguageFileItem> languages = new HashMap<>();
    private Map<String, List<LanguageFileItem>> extensionXLanguage = new HashMap<>();

    private LanguageFileHandler(){
        initLanguages();
    }

    public static LanguageFileHandler get() {
        if (INSTANCE == null) {
            INSTANCE = new LanguageFileHandler();
        }
        return INSTANCE;
    }

    private void initLanguages() {
        try {
            String languagesYamlAsString = IOUtils.toString(LanguageFileHandler.class.getResourceAsStream("/" + LANGUAGES_YAML_PATH), Charset.defaultCharset());
            JsonNode languagesAsJsonNode = YAML_MAPPER.readTree(languagesYamlAsString);

            String customizationYamlAsString = IOUtils.toString(LanguageFileHandler.class.getResourceAsStream("/" + LANGUAGES_CUSTOMIZATION_YAML_PATH), Charset.defaultCharset());
            JsonNode customizationAsJsonNode = YAML_MAPPER.readTree(customizationYamlAsString);
            for (Iterator<Map.Entry<String, JsonNode>> it = languagesAsJsonNode.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> entry = it.next();
                String nameLanguage = entry.getKey();
                JsonNode languageAttributes = entry.getValue();
                String type = languageAttributes.get("type").asText();
                String group = languageAttributes.has("group") ? languageAttributes.get("group").asText() : "";
                List<String> aliases = getValueAsList(languageAttributes, "aliases");
                LanguageFileItem languageFileItem = new LanguageFileItem(nameLanguage, aliases, type, group);
                customizeLanguage(customizationAsJsonNode, languageFileItem);
                if (!languageFileItem.isDisabled()) {
                    languages.put(nameLanguage, languageFileItem);
                    populateLanguageList(extensionXLanguage, languageAttributes, "extensions", languageFileItem);
                }
            }
        } catch (IOException e) {
            logger.warn(e.getLocalizedMessage(), e);
        }
    }

    private void customizeLanguage(JsonNode customizationsNode, LanguageFileItem languageFileItem) {
        JsonNode languageCustomization = customizationsNode.findValue(languageFileItem.getName());
        if (languageCustomization != null) {
            List<String> configurationFiles = getValueAsList(languageCustomization, "configuration_files");
            List<String> excludeFolders = getValueAsList(languageCustomization, "exclude_folders");
            List<String> aliases = getValueAsList(languageCustomization, "aliases");
            boolean canBeComponent = languageCustomization.has("component") && languageCustomization.get("component").asBoolean();
            boolean disabled = languageCustomization.has("disable_detection") && languageCustomization.get("disable_detection").asBoolean();

            languageFileItem.setConfigurationFiles(configurationFiles);
            languageFileItem.setExcludeFolders(excludeFolders);
            languageFileItem.setCanBeComponent(canBeComponent);
            languageFileItem.addAliases(aliases);
            languageFileItem.setDisabled(disabled);
        }
    }

    private List<String> getValueAsList(JsonNode languageAttributes, String field) {
        List<String> values = new ArrayList<>();
        if (languageAttributes.has(field)) {
            JsonNode fieldValues = languageAttributes.get(field);
            for (JsonNode node : fieldValues) {
                values.add(node.asText());
            }
        }
        return values;
    }

    private void populateLanguageList(Map<String, List<LanguageFileItem>> languageMap, JsonNode languageAttributes, String field, LanguageFileItem language) {
        if (languageAttributes.has(field)) {
            JsonNode fieldValues = languageAttributes.get(field);
            for (JsonNode value: fieldValues) {
                if (!value.asText("").isEmpty()) {
                    List<LanguageFileItem> languageMapValue = languageMap.getOrDefault(value.asText(), new ArrayList<>());
                    languageMapValue.add(language);
                    languageMap.put(value.asText(), languageMapValue);
                }
            }
        }
    }



    public List<LanguageFileItem> getLanguagesByExtension(String extension) {
        return extensionXLanguage.getOrDefault(extension, Collections.emptyList());
    }

    public LanguageFileItem getLanguageByName(String name) {
        Optional<LanguageFileItem> languageFileItem = languages.entrySet().stream()
                .filter(item -> item.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .findFirst();
        return languageFileItem.orElse(null);
    }

    public LanguageFileItem getLanguageByNameOrAlias(String name) {
        LanguageFileItem languageFileItem = getLanguageByName(name);
        if (languageFileItem == null) {
            return getLanguageByAlias(name);
        }
        return languageFileItem;
    }

    public LanguageFileItem getLanguageByAlias(String alias) {
        String finalAlias = alias.toLowerCase();
        Optional<LanguageFileItem> languageFileItem = languages.values().stream()
                .filter(item -> item.getAliases().contains(finalAlias))
                .findFirst();
        return languageFileItem.orElse(null);
    }

    public Map<String, List<String>> getConfigurationPerLanguageMapping() {
        Map<String, List<String>> configurationPerLanguage = new HashMap<>();
        for (LanguageFileItem fileItem: languages.values()) {
            List<String> configurationFiles = fileItem.getConfigurationFiles();
            if (!configurationFiles.isEmpty()) {
                for (String configFile: configurationFiles) {
                    configurationPerLanguage.compute(configFile, (k, v) -> {
                        if (v == null) {
                            v = new ArrayList<>();
                        }
                        v.add(fileItem.getName());
                        return v;
                    });
                }
            }
        }
        return configurationPerLanguage;
    }
}
