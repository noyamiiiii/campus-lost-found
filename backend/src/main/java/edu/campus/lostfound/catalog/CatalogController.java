package edu.campus.lostfound.catalog;

import edu.campus.lostfound.common.ApiResponse;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Profile("local")
public class CatalogController {

    private final CategoryService categoryService;

    public CatalogController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping("/categories")
    public ApiResponse<CategoryListData> listCategories() {
        return ApiResponse.ok(new CategoryListData(categoryService.listEnabled()));
    }

    public record CategoryListData(List<CategoryService.CategoryView> items) {
    }
}
