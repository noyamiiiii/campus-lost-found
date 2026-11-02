package edu.campus.lostfound.post;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Testcontainers
class PostApiIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.3")
            .withDatabaseName("lostfound")
            .withUsername("lostfound_test")
            .withPassword("lostfound_test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/dev");
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listsCategoriesFromDatabase() throws Exception {
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.data.items.length()").value(20))
                .andExpect(jsonPath("$.data.items[0].slug").value("campus-card"));
    }

    @Test
    void pagesPostsWithStableCursorAndNgramSearch() throws Exception {
        MvcResult firstPage = mockMvc.perform(get("/api/v1/posts").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list.length()").value(2))
                .andExpect(jsonPath("$.data.hasMore").value(true))
                .andExpect(jsonPath("$.data.list[0].publicId").value("LF-2026-1031-01"))
                .andReturn();

        String cursor = JsonPath.read(firstPage.getResponse().getContentAsString(), "$.data.nextCursor");
        mockMvc.perform(get("/api/v1/posts").param("size", "2").param("cursor", cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].publicId").value("LF-2026-1030-02"));

        mockMvc.perform(get("/api/v1/posts").param("keyword", "保温杯"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list.length()").value(1))
                .andExpect(jsonPath("$.data.list[0].publicId").value("LF-2026-1029-03"));
    }

    @Test
    void returnsPostDetailAndValidationError() throws Exception {
        mockMvc.perform(get("/api/v1/posts/LF-2026-1031-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categoryName").value("校园卡"))
                .andExpect(jsonPath("$.data.publisherName").value("校园用户 0001"))
                .andExpect(jsonPath("$.data.eventTime").value("2026-11-01T09:20:00+08:00"));

        mockMvc.perform(get("/api/v1/posts/LF-HIDDEN-PRIVATE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));

        mockMvc.perform(get("/api/v1/posts").param("size", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void filtersByTypeAndCategoryAndRejectsInvalidCursor() throws Exception {
        mockMvc.perform(get("/api/v1/posts").param("type", "lost"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list.length()").value(3))
                .andExpect(jsonPath("$.data.list[0].type").value("LOST"))
                .andExpect(jsonPath("$.data.list[0].id").doesNotExist());

        mockMvc.perform(get("/api/v1/posts").param("categoryId", "9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list.length()").value(1))
                .andExpect(jsonPath("$.data.list[0].publicId").value("LF-2026-1029-07"));

        mockMvc.perform(get("/api/v1/posts").param("cursor", "not-a-cursor"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        mockMvc.perform(get("/api/v1/posts").param("type", "invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }
}
