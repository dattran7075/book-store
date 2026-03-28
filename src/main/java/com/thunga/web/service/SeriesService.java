package com.thunga.web.service;

import com.thunga.web.entity.Book;
import com.thunga.web.entity.Series;
import com.thunga.web.repository.BookRepository;
import com.thunga.web.repository.SeriesRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SeriesService {

    @Autowired
    private SeriesRepository seriesRepository;

    @Autowired
    private BookRepository bookRepository;

    public List<Series> findAll() {
        return seriesRepository.findAll();
    }

    public Series findById(Integer id) {
        return seriesRepository.findById(id).orElse(null);
    }

    public Series findByName(String name) {
        return seriesRepository.findByName(name);
    }

    public Series save(Series series) {
        return seriesRepository.save(series);
    }

    public void delete(Series series) {
        seriesRepository.delete(series);
    }

    public Page<Series> findByLimit(Integer page, Integer limit) {
        Pageable paging = PageRequest.of(page, limit);
        return seriesRepository.findAll(paging);
    }

    public long countBooksBySeriesId(Integer seriesId) {
        return seriesRepository.countBooksBySeriesId(seriesId);
    }

    /**
     * Validate name: not null, not blank, trimmed
     */
    private void validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Series name cannot be empty");
        }
    }

    /**
     * Validate totalVolumes: if provided, must be > 0
     */
    private void validateTotalVolumes(Integer totalVolumes) {
        if (totalVolumes != null && totalVolumes <= 0) {
            throw new IllegalArgumentException("Total volumes must be greater than 0");
        }
    }

    /**
     * Validate new series.
     * Returns Map<fieldName, errorMessage> — empty = valid.
     */
    public Map<String, String> validateNewSeries(String name, Integer totalVolumes) {
        Map<String, String> errors = new LinkedHashMap<>();

        if (name == null || name.trim().isEmpty()) {
            errors.put("name", "Series name cannot be empty");
        } else if (findByName(name.trim()) != null) {
            errors.put("name", "Series already exists");
        }

        if (totalVolumes != null && totalVolumes <= 0) {
            errors.put("totalVolumes", "Total volumes must be greater than 0");
        }

        return errors;
    }

    /**
     * Validate edit series.
     * Returns Map<fieldName, errorMessage> — empty = valid.
     */
    public Map<String, String> validateEditSeries(Integer id, String newName, Integer totalVolumes) {
        Map<String, String> errors = new java.util.LinkedHashMap<>();

        if (newName == null || newName.trim().isEmpty()) {
            errors.put("name", "Series name cannot be empty");
        } else {
            Series existing = findByName(newName.trim());
            if (existing != null && !existing.getId().equals(id)) {
                errors.put("name", "Series already exists");
            }
        }

        if (totalVolumes != null && totalVolumes <= 0) {
            errors.put("totalVolumes", "Total volumes must be greater than 0");
        }

        return errors;
    }

    /**
     * Delete series - cannot delete if it has books
     */
    public void deleteSeries(Integer id) {
        Series series = findById(id);
        if (series == null) {
            throw new IllegalArgumentException("Series not found");
        }

        long bookCount = countBooksBySeriesId(id);
        if (bookCount > 0) {
            throw new IllegalArgumentException(
                    "Cannot delete series with " + bookCount + " book(s).");
        }

        delete(series);
    }

    /**
     * Create new series
     */
    public Series createSeries(String name, String description, Integer totalVolumes, String status) {
        Map<String, String> errors = validateNewSeries(name, totalVolumes);
        if (!errors.isEmpty()) throw new IllegalArgumentException(errors.values().iterator().next());

        Series series = new Series();
        series.setName(name.trim());
        series.setDescription(description != null ? description.trim() : null);
        series.setTotalVolumes(totalVolumes);
        series.setStatus(status != null && !status.trim().isEmpty() ? status.trim() : "ONGOING");
        series.setCreated_at(new Date());
        series.setUpdated_at(new Date());

        return save(series);
    }

    /**
     * Update series
     */
    public Series updateSeries(Integer id, String newName, String description, Integer totalVolumes, String status) {
        Map<String, String> errors = validateEditSeries(id, newName, totalVolumes);
        if (!errors.isEmpty()) throw new IllegalArgumentException(errors.values().iterator().next());

        Series series = findById(id);
        if (series == null) {
            throw new IllegalArgumentException("Series not found");
        }

        series.setName(newName.trim());
        series.setDescription(description != null ? description.trim() : null);
        series.setTotalVolumes(totalVolumes);
        series.setStatus(status != null && !status.trim().isEmpty() ? status.trim() : series.getStatus());
        series.setUpdated_at(new Date());

        return save(series);
    }

    // =====================================================
    // SERIES BUNDLE METHODS (unchanged)
    // =====================================================

    public List<Book> getBooksInSeries(Integer seriesId) {
        List<Book> books = bookRepository.findBySeriesIdAndIdNot(seriesId, -1);
        books.sort((b1, b2) -> {
            if (b1.getVolumeNumber() == null && b2.getVolumeNumber() == null) return 0;
            if (b1.getVolumeNumber() == null) return 1;
            if (b2.getVolumeNumber() == null) return -1;
            return b1.getVolumeNumber().compareTo(b2.getVolumeNumber());
        });
        return books;
    }

    public List<Book> getAvailableBooksInSeries(Integer seriesId) {
        return getBooksInSeries(seriesId).stream()
                .filter(book -> book.getNumber_in_stock() != null && book.getNumber_in_stock() > 0)
                .collect(Collectors.toList());
    }

    public List<Book> getOutOfStockBooksInSeries(Integer seriesId) {
        return getBooksInSeries(seriesId).stream()
                .filter(book -> book.getNumber_in_stock() == null || book.getNumber_in_stock() == 0)
                .collect(Collectors.toList());
    }

    public Double calculateSeriesOriginalPrice(Integer seriesId) {
        return getAvailableBooksInSeries(seriesId).stream()
                .mapToDouble(Book::getPrice)
                .sum();
    }

    public Double calculateSeriesDiscountedPrice(Integer seriesId) {
        return calculateSeriesOriginalPrice(seriesId) * 0.95;
    }

    public Double calculateSeriesDiscountAmount(Integer seriesId) {
        return calculateSeriesOriginalPrice(seriesId) * 0.05;
    }

    public boolean canPurchaseFullSeries(Integer seriesId) {
        Series series = findById(seriesId);
        if (series == null || series.getStatus() == null) return false;

        List<Book> availableBooks = getAvailableBooksInSeries(seriesId);
        List<Book> outOfStockBooks = getOutOfStockBooksInSeries(seriesId);

        switch (series.getStatus()) {
            case "ONGOING":
                return false;
            case "COMPLETED":
            case "DISCONTINUED":
                return outOfStockBooks.isEmpty() && !availableBooks.isEmpty();
            default:
                return false;
        }
    }

    public String getUnavailableReason(Integer seriesId) {
        Series series = findById(seriesId);
        if (series == null) return "Series not found";
        if (canPurchaseFullSeries(seriesId)) return null;

        List<Book> allBooks = getBooksInSeries(seriesId);
        List<Book> availableBooks = getAvailableBooksInSeries(seriesId);
        List<Book> outOfStockBooks = getOutOfStockBooksInSeries(seriesId);

        switch (series.getStatus()) {
            case "ONGOING":
                return String.format("Not Available Yet (%d/%d volumes released)",
                        allBooks.size(), series.getTotalVolumes() != null ? series.getTotalVolumes() : 0);
            case "COMPLETED":
            case "DISCONTINUED":
                if (!outOfStockBooks.isEmpty()) {
                    return String.format("Some volumes are out of stock (%d/%d available)",
                            availableBooks.size(), allBooks.size());
                }
                return "Series not available";
            default:
                return "Unknown series status";
        }
    }

    public void validateSeriesBundlePurchase(Integer seriesId) {
        if (!canPurchaseFullSeries(seriesId)) {
            throw new IllegalStateException(getUnavailableReason(seriesId));
        }
        for (Book book : getAvailableBooksInSeries(seriesId)) {
            if (book.getNumber_in_stock() == null || book.getNumber_in_stock() <= 0) {
                throw new IllegalStateException(
                        String.format("Volume %d '%s' is out of stock", book.getVolumeNumber(), book.getTitle()));
            }
        }
    }
}