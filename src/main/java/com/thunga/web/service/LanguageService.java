package com.thunga.web.service;

import com.thunga.web.entity.Language;
import com.thunga.web.repository.LanguageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
public class LanguageService {

    @Autowired
    private LanguageRepository languageRepository;

    public List<Language> findAll() {
        return languageRepository.findAll();
    }

    public Language findById(Integer id) {
        return languageRepository.findById(id).orElse(null);
    }

    public Language findByName(String name) {
        return languageRepository.findByName(name);
    }

    public Language findByCode(String code) {
        return languageRepository.findByCode(code);
    }

    public Language save(Language language) {
        return languageRepository.save(language);
    }

    public void delete(Language language) {
        languageRepository.delete(language);
    }

    public Page<Language> findByLimit(Integer page, Integer limit) {
        Pageable paging = PageRequest.of(page, limit);
        return languageRepository.findAll(paging);
    }

    public long countBooksByLanguageId(Integer languageId) {
        return languageRepository.countBooksByLanguageId(languageId);
    }

    /**
     * Validate name:
     * - Not null, not blank, trimmed
     * - Letters only (Unicode letters supported, e.g. Vietnamese, English, Japanese)
     */
    private void validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Language name cannot be empty");
        }
        // Allow Unicode letters and spaces only (e.g., "Vietnamese", "Brazilian Portuguese")
        if (!name.trim().matches("[\\p{L} ]+")) {
            throw new IllegalArgumentException("Language name must contain letters only");
        }
    }

    /**
     * Validate code:
     * - Not null, not blank, trimmed
     */
    private void validateCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("Language code cannot be empty");
        }
    }

    /**
     * Validate new language.
     * Returns Map<fieldName, errorMessage> — empty = valid.
     */
    public Map<String, String> validateNewLanguage(String name, String code) {
        Map<String, String> errors = new java.util.LinkedHashMap<>();

        if (name == null || name.trim().isEmpty()) {
            errors.put("name", "Language name cannot be empty");
        } else if (!name.trim().matches("[\\p{L} ]+")) {
            errors.put("name", "Language name must contain letters only (e.g., Vietnamese, English)");
        } else if (findByName(name.trim()) != null) {
            errors.put("name", "Language with this name already exists");
        }

        if (code == null || code.trim().isEmpty()) {
            errors.put("code", "Language code cannot be empty");
        } else if (findByCode(code.trim().toUpperCase()) != null) {
            errors.put("code", "Language with this code already exists");
        }

        return errors;
    }

    /**
     * Validate edit language.
     * Returns Map<fieldName, errorMessage> — empty = valid.
     */
    public Map<String, String> validateEditLanguage(Integer id, String newName) {
        Map<String, String> errors = new java.util.LinkedHashMap<>();

        if (newName == null || newName.trim().isEmpty()) {
            errors.put("name", "Language name cannot be empty");
        } else if (!newName.trim().matches("[\\p{L} ]+")) {
            errors.put("name", "Language name must contain letters only (e.g., Vietnamese, English)");
        } else {
            Language existing = findByName(newName.trim());
            if (existing != null && !existing.getId().equals(id)) {
                errors.put("name", "Language with this name already exists");
            }
        }

        return errors;
    }

    /**
     * Delete language - cannot delete if it has books
     */
    public void deleteLanguage(Integer id) {
        Language language = findById(id);
        if (language == null) {
            throw new IllegalArgumentException("Language not found");
        }

        long bookCount = countBooksByLanguageId(id);
        if (bookCount > 0) {
            throw new IllegalArgumentException(
                    "Cannot delete language with " + bookCount + " book(s). Please reassign or remove books first.");
        }

        delete(language);
    }

    /**
     * Create new language
     */
    public Language createLanguage(String name, String code, String description) {
        Map<String, String> errors = validateNewLanguage(name, code);
        if (!errors.isEmpty()) throw new IllegalArgumentException(errors.values().iterator().next());

        Language language = new Language();
        language.setName(name.trim());
        language.setCode(code.trim());
        language.setCreated_at(new Date());
        language.setUpdated_at(new Date());

        return save(language);
    }

    /**
     * Update language
     */
    public Language updateLanguage(Integer id, String newName) {
        Map<String, String> errors = validateEditLanguage(id, newName);
        if (!errors.isEmpty()) throw new IllegalArgumentException(errors.values().iterator().next());

        Language language = findById(id);
        if (language == null) {
            throw new IllegalArgumentException("Language not found");
        }

        language.setName(newName.trim());
        language.setUpdated_at(new Date());

        return save(language);
    }
}