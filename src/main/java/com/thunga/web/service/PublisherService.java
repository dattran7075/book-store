package com.thunga.web.service;

import com.thunga.web.entity.Publisher;
import com.thunga.web.repository.PublisherRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PublisherService {

    @Autowired
    private PublisherRepository publisherRepository;

    public List<Publisher> findAll() {
        return publisherRepository.findAll();
    }

    public Publisher findById(Integer id) {
        return publisherRepository.findById(id).orElse(null);
    }

    public Publisher findByName(String name) {
        return publisherRepository.findByName(name);
    }

    public Publisher save(Publisher publisher) {
        return publisherRepository.save(publisher);
    }

    public void delete(Publisher publisher) {
        publisherRepository.delete(publisher);
    }

    public Page<Publisher> findByLimit(Integer page, Integer limit) {
        return publisherRepository.findAll(PageRequest.of(page, limit));
    }

    public long countBooksByPublisherId(Integer publisherId) {
        return publisherRepository.countBooksByPublisherId(publisherId);
    }

    /**
     * Unified validation for both create and edit publisher.
     *
     * @param id      Publisher ID (null for create, non-null for edit)
     * @param name    Publisher name
     * @param country Publisher country
     * @param website Publisher website
     * @return Map<fieldName, errorMessage> — empty = valid
     */
    public Map<String, String> validatePublisher(Integer id, String name, String country, String website) {
        Map<String, String> errors = new LinkedHashMap<>();

        // ========== NAME VALIDATION ==========
        if (name == null || name.trim().isEmpty()) {
            errors.put("name", "Publisher name cannot be empty");
        } else if (!name.trim().matches("[\\p{L}0-9 .,'\\-&]+")) {
            errors.put("name", "Publisher name must contain letters only (no special characters)");
        } else {
            // Check for duplicate name
            Publisher existing = findByName(name.trim());
            if (existing != null) {
                // If creating (id is null) or editing a different publisher (id doesn't match)
                if (id == null || !existing.getId().equals(id)) {
                    errors.put("name", "Publisher already exists");
                }
            }
        }

        // ========== COUNTRY VALIDATION ==========
        // Country is now a dropdown, so validation is optional
        // Empty country is allowed
        if (country != null && !country.trim().isEmpty()) {
            // Optional: Add validation if needed (currently not enforced)
            // For now, we trust the dropdown to only contain valid countries
        }

        // ========== WEBSITE VALIDATION ==========
        if (website != null && !website.trim().isEmpty()) {
            if (!website.trim().matches("^(https?://)([\\w\\-]+\\.)+[\\w]{2,}(/.*)?$")) {
                errors.put("website", "Website must be a valid URL (e.g., https://example.com)");
            }
        }

        return errors;
    }

    /**
     * @deprecated Use validatePublisher(id, name, country, website) instead
     */
    @Deprecated
    public Map<String, String> validateNewPublisher(String name, String country, String website) {
        return validatePublisher(null, name, country, website);
    }

    /**
     * @deprecated Use validatePublisher(id, name, country, website) instead
     */
    @Deprecated
    public Map<String, String> validateEditPublisher(Integer id, String name, String country, String website) {
        return validatePublisher(id, name, country, website);
    }

    public void deletePublisher(Integer id) {
        Publisher publisher = findById(id);
        if (publisher == null) throw new IllegalArgumentException("Publisher not found");
        long bookCount = countBooksByPublisherId(id);
        if (bookCount > 0) throw new IllegalArgumentException(
                "Cannot delete publisher with " + bookCount + " book(s). Please reassign or remove books first.");
        delete(publisher);
    }

    public Publisher createPublisher(String name, String country, String website, String description) {
        Publisher publisher = new Publisher();
        publisher.setName(name.trim());
        publisher.setCountry(country != null && !country.trim().isEmpty() ? country.trim() : null);
        publisher.setWebsite(website != null && !website.trim().isEmpty() ? website.trim() : null);
        publisher.setDescription(description != null ? description.trim() : null);
        publisher.setCreated_at(new Date());
        publisher.setUpdated_at(new Date());
        return save(publisher);
    }

    public Publisher updatePublisher(Integer id, String name, String country, String website, String description) {
        Publisher publisher = findById(id);
        if (publisher == null) throw new IllegalArgumentException("Publisher not found");
        publisher.setName(name.trim());
        publisher.setCountry(country != null && !country.trim().isEmpty() ? country.trim() : null);
        publisher.setWebsite(website != null && !website.trim().isEmpty() ? website.trim() : null);
        publisher.setDescription(description != null ? description.trim() : null);
        publisher.setUpdated_at(new Date());
        return save(publisher);
    }
}