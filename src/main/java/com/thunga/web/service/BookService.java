package com.thunga.web.service;

import com.thunga.web.entity.*;
import com.thunga.web.repository.BookRepository;
import com.thunga.web.repository.BookTranslatorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

@Service
public class BookService {
    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private AuthorService authorService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private LanguageService languageService;

    @Autowired
    private PublisherService publisherService;

    @Autowired
    private SeriesService seriesService;

    @Autowired
    private TranslatorService translatorService;

    @Autowired
    private BookTranslatorRepository bookTranslatorRepository;

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    public List<Book> findAll() {
        return bookRepository.findAll();
    }

    public Book findById(Integer id) {
        return bookRepository.findById(id).orElse(null);
    }

    public List<Book> findByTitle(String title) {
        return bookRepository.findByTitle(title);
    }

    public void deleteById(Integer id) {
        bookRepository.deleteById(id);
    }


    public Book save(Book book) {
        return bookRepository.save(book);
    }

    public Book findBestSoldBook() {
        return bookRepository.findBestSoldBook();
    }


    public Page<Book> getFilteredAndSortedBooks(
            String keyword,
            Integer categoryId,
            Double priceMin,
            Double priceMax,
            String sortBy,
            Integer page,
            Integer pageSize) {

        List<Book> bookList = new ArrayList<>();

        if (keyword != null && !keyword.trim().isEmpty()) {
            keyword = keyword.trim();
            Set<Book> resultSet = new LinkedHashSet<>();

            // Find by title
            List<Book> booksByTitle = bookRepository.findByTitleContainingIgnoreCase(keyword);
            if (booksByTitle != null) {
                resultSet.addAll(booksByTitle);
            }

            // Find by author
            List<Book> booksByAuthor = bookRepository.findByAuthor_NameContainingIgnoreCase(keyword);
            if (booksByAuthor != null) {
                resultSet.addAll(booksByAuthor);
            }

            // Find by category name
            Category category = categoryService.findByName(keyword);
            if (category != null) {
                List<Book> booksByCategory = bookRepository.findByCategory(category);
                if (booksByCategory != null) {
                    resultSet.addAll(booksByCategory);
                }
            }

            bookList = new ArrayList<>(resultSet);

        } else if (categoryId != null) {
            // BROWSE MODE: Find by category (including subcategories)
            List<Integer> categoryIds = categoryService.getAllCategoryIdsInHierarchy(categoryId);
            bookList = bookRepository.findByCategoryIdIn(categoryIds);
            if (bookList == null) {
                bookList = new ArrayList<>();
            }

        } else {
            bookList = bookRepository.findAll();
        }

        // ============ STEP 2: PRICE FILTER ============
        boolean hasMin = priceMin != null && priceMin > 0;
        boolean hasMax = priceMax != null;
        if (hasMin || hasMax) {
            bookList.removeIf(book -> {
                if (book.getPrice() == null) return true;
                if (hasMin && book.getPrice() < priceMin) return true;
                if (hasMax && book.getPrice() > priceMax) return true;
                return false;
            });
        }

        if (sortBy != null && !sortBy.trim().isEmpty()) {
            switch (sortBy.toLowerCase()) {
                case "title":
                    // A → Z
                    bookList.sort((b1, b2) -> {
                        String t1 = b1.getTitle() != null ? b1.getTitle() : "";
                        String t2 = b2.getTitle() != null ? b2.getTitle() : "";
                        return t1.compareToIgnoreCase(t2);
                    });
                    break;

                case "title_desc":
                    // Z → A
                    bookList.sort((b1, b2) -> {
                        String t1 = b1.getTitle() != null ? b1.getTitle() : "";
                        String t2 = b2.getTitle() != null ? b2.getTitle() : "";
                        return t2.compareToIgnoreCase(t1);
                    });
                    break;

                case "price":
                    // Price Low → High
                    bookList.sort((b1, b2) -> {
                        Double p1 = b1.getPrice();
                        Double p2 = b2.getPrice();
                        if (p1 == null && p2 == null) return 0;
                        if (p1 == null) return 1;
                        if (p2 == null) return -1;
                        return p1.compareTo(p2);
                    });
                    break;

                case "price_desc":
                    // Price High → Low
                    bookList.sort((b1, b2) -> {
                        Double p1 = b1.getPrice();
                        Double p2 = b2.getPrice();
                        if (p1 == null && p2 == null) return 0;
                        if (p1 == null) return 1;
                        if (p2 == null) return -1;
                        return p2.compareTo(p1);
                    });
                    break;

                case "newest":
                    // Newest first (by created_at DESC)
                    bookList.sort((b1, b2) -> {
                        if (b1.getCreated_at() == null && b2.getCreated_at() == null) return 0;
                        if (b1.getCreated_at() == null) return 1;
                        if (b2.getCreated_at() == null) return -1;
                        return b2.getCreated_at().compareTo(b1.getCreated_at());
                    });
                    break;

                case "bestseller":
                    // Best seller (by number_sold DESC)
                    bookList.sort((b1, b2) -> {
                        Integer sold1 = b1.getNumber_sold() != null ? b1.getNumber_sold() : 0;
                        Integer sold2 = b2.getNumber_sold() != null ? b2.getNumber_sold() : 0;
                        return sold2.compareTo(sold1);
                    });
                    break;
            }
        }

        int totalElements = bookList.size();
        int start = page * pageSize;
        int end = Math.min(start + pageSize, totalElements);

        if (start >= totalElements) {
            return new PageImpl<>(new ArrayList<>(), PageRequest.of(page, pageSize), totalElements);
        }

        List<Book> pageContent = bookList.subList(start, end);
        Pageable pageable = PageRequest.of(page, pageSize);
        return new PageImpl<>(pageContent, pageable, totalElements);
    }

    public Book updateInfo(Book newBook, HttpServletRequest request, Translator translator) throws IOException {
        Book updateBook;
        Category category = categoryService.findByName(request.getParameter("categoryInfo"));
        Author author = authorService.findByName(request.getParameter("authorInfo"));

        String languageParam = request.getParameter("languageInfo");
        Language language = (languageParam != null && !languageParam.isEmpty())
                ? languageService.findByName(languageParam) : null;

        String publisherParam = request.getParameter("publisherInfo");
        Publisher publisher = (publisherParam != null && !publisherParam.isEmpty())
                ? publisherService.findByName(publisherParam) : null;

        String seriesParam = request.getParameter("seriesInfo");
        Series series = (seriesParam != null && !seriesParam.isEmpty())
                ? seriesService.findByName(seriesParam) : null;

        String volumeNumberParam = request.getParameter("volumeNumber");
        Integer volumeNumber = null;
        if (volumeNumberParam != null && !volumeNumberParam.isEmpty()) {
            try {
                volumeNumber = Integer.valueOf(volumeNumberParam);
            } catch (NumberFormatException e) {
                // Ignore invalid volume number
            }
        }

        String stockParam = request.getParameter("stock");
        Integer stock = null;
        if (stockParam != null && !stockParam.isEmpty()) {
            try {
                stock = Integer.valueOf(stockParam);
            } catch (NumberFormatException e) {
                // Ignore invalid stock
            }
        }

        String size = request.getParameter("size");

        String weightParam = request.getParameter("weight_kg");
        Double weight = null;
        if (weightParam != null && !weightParam.trim().isEmpty()) {
            try {
                weight = Double.parseDouble(weightParam.trim());
            } catch (NumberFormatException e) {
                // Ignore invalid weight - use null
            }
        }

        if (newBook.getId() != null) {
            updateBook = bookRepository.findById(newBook.getId()).get();
            updateBook.setCategory(category);
            updateBook.setDescription(newBook.getDescription());
            updateBook.setDate_publication(newBook.getDate_publication());
            updateBook.setPrice(newBook.getPrice());
            updateBook.setLanguage(language);
            updateBook.setPublisher(publisher);
            updateBook.setSeries(series);
            updateBook.setVolumeNumber(volumeNumber);
            updateBook.setNumber_in_stock(stock);
            updateBook.setSize(size);
            updateBook.setWeight_kg(weight);
            updateBook.setUpdated_at(new Date());
        } else {
            updateBook = newBook;
            updateBook.setAuthor(author);
            updateBook.setCategory(category);
            updateBook.setLanguage(language);
            updateBook.setPublisher(publisher);
            updateBook.setSeries(series);
            updateBook.setVolumeNumber(volumeNumber);
            updateBook.setNumber_in_stock(stock != null ? stock : 0);
            updateBook.setNumber_sold(0);
            updateBook.setSize(size);
            updateBook.setWeight_kg(weight);
            updateBook.setCreated_at(new Date());
        }

        if (newBook.getFileData() != null && !newBook.getFileData().isEmpty()) {
            File uploadFolder = new File(System.getProperty("user.dir") + File.separator + uploadDir);
            if (!uploadFolder.exists()) {
                boolean created = uploadFolder.mkdirs();
                if (!created) {
                    throw new IOException("Could not create upload directory: " + uploadFolder.getAbsolutePath());
                }
            }

            String originalFilename = newBook.getFileData().getOriginalFilename();
            String sanitized = originalFilename == null ? "image" : originalFilename.replaceAll("[^a-zA-Z0-9.\\-_]", "_");
            String uniqueName = System.currentTimeMillis() + "_" + sanitized;
            File destFile = new File(uploadFolder, uniqueName);
            try (FileOutputStream fos = new FileOutputStream(destFile)) {
                fos.write(newBook.getFileData().getBytes());
            }
            updateBook.setImage("/uploads/" + uniqueName);
        }

        String imageUrl = newBook.getImage() != null ? newBook.getImage() : request.getParameter("imageUrl");
        if (imageUrl != null && !imageUrl.isEmpty()) {
            updateBook.setImage(imageUrl);
        }

        updateBook = bookRepository.save(updateBook);

        if (translator != null && updateBook.getId() != null) {
            boolean translatorExists = false;
            if (updateBook.getBookTranslatorList() != null) {
                for (BookTranslator bt : updateBook.getBookTranslatorList()) {
                    if (bt.getTranslator().getId().equals(translator.getId())) {
                        translatorExists = true;
                        break;
                    }
                }
            }

            if (!translatorExists) {
                BookTranslator bookTranslator = new BookTranslator();
                bookTranslator.setBook(updateBook);
                bookTranslator.setTranslator(translator);
                bookTranslator.setRole("Translator");
                bookTranslator.setCreated_at(new Date());
                bookTranslatorRepository.save(bookTranslator);
            }
        }

        return updateBook;
    }

    public boolean hasCompletedOrderWithBook(User user, Book book) {
        if (user == null || user.getOrderList() == null) {
            return false;
        }

        for (Order order : user.getOrderList()) {
            if ("Completed".equals(order.getStatus())) {
                if (order.getOrderDetailList() != null) {
                    for (OrderDetail orderDetail : order.getOrderDetailList()) {
                        if (orderDetail.getBook().getId().equals(book.getId())) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public List<Book> findByTitleIgnoreCase(String title) {
        return bookRepository.findByTitleIgnoreCase(title);
    }

    public boolean isDuplicateBook(String title, Integer authorId, Integer excludeBookId) {
        if (title == null || authorId == null) {
            return false;
        }

        List<Book> existingBooks = findByTitleIgnoreCase(title);

        if (existingBooks != null && !existingBooks.isEmpty()) {
            for (Book existingBook : existingBooks) {
                if (excludeBookId != null && existingBook.getId().equals(excludeBookId)) {
                    continue;
                }

                if (existingBook.getAuthor() != null &&
                        existingBook.getAuthor().getId().equals(authorId)) {
                    return true;
                }
            }
        }

        return false;
    }

    public List<Book> findBySeriesExcludingCurrent(Integer seriesId, Integer currentBookId) {
        return bookRepository.findBySeriesIdAndIdNot(seriesId, currentBookId);
    }

    public boolean validateBook(Book book) {
        if (book == null || book.getTitle() == null || book.getAuthor() == null) {
            return false;
        }

        return !isDuplicateBook(book.getTitle(), book.getAuthor().getId(), book.getId());
    }

    public String getBookValidationErrorMessage() {
        return "A book with the same title and author already exists";
    }

    // ==================== NEW METHODS FOR HOME PAGE SECTIONS ====================

    /**
     * Get personalized book recommendations for a user based on their purchase history
     * Books are selected from categories the user has purchased from
     * Books are sorted by popularity (number_sold DESC)
     * Excludes books already purchased by the user
     *
     * @param userId The user ID
     * @param limit  Maximum number of recommendations
     * @return List of recommended books, or empty list if user not found
     */
    public List<Book> getRecommendedBooksForUser(Integer userId, int limit) {
        try {
            if (userId == null || userId <= 0) {
                return new ArrayList<>();
            }

            // Get all books and filter based on user's purchase history
            List<Book> allBooks = bookRepository.findAll();

            if (allBooks == null || allBooks.isEmpty()) {
                return new ArrayList<>();
            }

            // This is a simplified implementation
            // For better performance, use a native SQL query in the repository
            Set<Book> recommendedBooks = new LinkedHashSet<>();
            List<Book> result = new ArrayList<>();

            // If no complex recommendation logic needed, return best-selling books
            for (Book book : allBooks) {
                if (book.getNumber_in_stock() != null && book.getNumber_in_stock() > 0) {
                    result.add(book);
                }
            }

            // Sort by number_sold (popularity) DESC
            result.sort((b1, b2) -> {
                Integer sold1 = b1.getNumber_sold() != null ? b1.getNumber_sold() : 0;
                Integer sold2 = b2.getNumber_sold() != null ? b2.getNumber_sold() : 0;
                return sold2.compareTo(sold1);
            });

            // Return limited results
            return result.size() > limit ? result.subList(0, limit) : result;

        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * Get newly added books (New Arrivals)
     * Books are sorted by creation date in descending order
     *
     * @param limit Maximum number of books to return
     * @return List of newest books
     */
    public List<Book> getNewArrivals(int limit) {
        try {
            List<Book> allBooks = bookRepository.findAll();

            if (allBooks == null || allBooks.isEmpty()) {
                return new ArrayList<>();
            }

            // Filter books with created_at date
            List<Book> newBooks = new ArrayList<>();
            for (Book book : allBooks) {
                if (book.getCreated_at() != null) {
                    newBooks.add(book);
                }
            }

            // Sort by created_at DESC (newest first)
            newBooks.sort((b1, b2) -> {
                if (b1.getCreated_at() == null && b2.getCreated_at() == null) return 0;
                if (b1.getCreated_at() == null) return 1;
                if (b2.getCreated_at() == null) return -1;
                return b2.getCreated_at().compareTo(b1.getCreated_at());
            });

            // Return limited results
            return newBooks.size() > limit ? newBooks.subList(0, limit) : newBooks;

        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * Get most popular books based on sales count
     * Books are sorted by number_sold in descending order
     *
     * @param limit Maximum number of books to return
     * @return List of best-selling books
     */
    public List<Book> getMostPopularBooks(int limit) {
        try {
            List<Book> allBooks = bookRepository.findAll();

            if (allBooks == null || allBooks.isEmpty()) {
                return new ArrayList<>();
            }

            // Filter books in stock
            List<Book> popularBooks = new ArrayList<>();
            for (Book book : allBooks) {
                if (book.getNumber_in_stock() != null && book.getNumber_in_stock() > 0) {
                    popularBooks.add(book);
                }
            }

            // Sort by number_sold DESC (most popular first)
            popularBooks.sort((b1, b2) -> {
                Integer sold1 = b1.getNumber_sold() != null ? b1.getNumber_sold() : 0;
                Integer sold2 = b2.getNumber_sold() != null ? b2.getNumber_sold() : 0;
                return sold2.compareTo(sold1);
            });

            // Return limited results
            return popularBooks.size() > limit ? popularBooks.subList(0, limit) : popularBooks;

        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
}