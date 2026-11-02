package edu.campus.lostfound.catalog;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("local")
public class CategoryService {

    private final CategoryMapper categoryMapper;

    public CategoryService(CategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    public List<CategoryView> listEnabled() {
        return categoryMapper.selectList(Wrappers.<CategoryEntity>lambdaQuery()
                        .eq(CategoryEntity::getEnabled, true)
                        .orderByAsc(CategoryEntity::getSort)
                        .orderByAsc(CategoryEntity::getId))
                .stream()
                .map(category -> new CategoryView(
                        category.getId(),
                        category.getName(),
                        category.getSlug(),
                        category.getIcon(),
                        category.getSort()))
                .toList();
    }

    public record CategoryView(Long id, String name, String slug, String icon, Integer sort) {
    }
}
