package com.thunga.web.repository;

import com.thunga.web.entity.Book;
import com.thunga.web.entity.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookRepository extends JpaRepository<Book, Integer> {

    /**
     * Tìm sách theo title chính xác
     */
    List<Book> findByTitle(String title);

    /**
     * Tìm sách theo title (không phân biệt hoa thường)
     */
    List<Book> findByTitleIgnoreCase(String title);

    /**
     * Tìm sách có chứa từ khóa trong title (không phân biệt hoa thường)
     * Dùng cho TÌM KIẾM TỰ ĐỘNG
     */
    List<Book> findByTitleContainingIgnoreCase(String keyword);

    /**
     * Tìm sách có title bắt đầu bằng từ khóa (không phân biệt hoa thường)
     */
    List<Book> findByTitleStartingWithIgnoreCase(String keyword);

    /**
     * Tìm sách theo tên tác giả có chứa từ khóa (không phân biệt hoa thường)
     * Dùng cho TÌM KIẾM TỰ ĐỘNG
     * Spring tự JOIN với bảng Author
     */
    List<Book> findByAuthor_NameContainingIgnoreCase(String keyword);

    /**
     * Tìm sách theo tên tác giả bắt đầu bằng từ khóa (không phân biệt hoa thường)
     * Spring tự JOIN với bảng Author
     */
    List<Book> findByAuthor_NameStartingWithIgnoreCase(String keyword);

    /**
     * Tìm sách theo category
     * Dùng cho TÌM KIẾM TỰ ĐỘNG
     */
    List<Book> findByCategory(Category category);

    /**
     * Tìm sách theo list category IDs
     */
    List<Book> findByCategoryIdIn(List<Integer> categoryIds);

    /**
     * Tìm sách bán chạy nhất
     * CẦN @Query vì dùng Native SQL (TOP 1)
     */
    @Query(value = "SELECT TOP 1 * FROM book ORDER BY number_sold DESC", nativeQuery = true)
    Book findBestSoldBook();

    List<Book> findBySeriesIdAndIdNot(Integer seriesId, Integer bookId);

    Page<Book> findByPriceBetween(Double min, Double max, Pageable pageable);

    Page<Book> findByPriceGreaterThanEqual(Double min, Pageable pageable);

    Page<Book> findByPriceLessThanEqual(Double max, Pageable pageable);

    // Filter giá + category
    Page<Book> findByCategoryIdAndPriceBetween(Integer catId, Double min, Double max, Pageable pageable);

    Page<Book> findByCategoryIdAndPriceGreaterThanEqual(Integer catId, Double min, Pageable pageable);

    Page<Book> findByCategoryIdAndPriceLessThanEqual(Integer catId, Double max, Pageable pageable);

    Page<Book> findByCategoryId(Integer catId, Pageable pageable);


}