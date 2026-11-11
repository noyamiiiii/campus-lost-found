package edu.campus.lostfound.post;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

    @Test
    void createsPostAndExposesItInFeedAndMyList() throws Exception {
        String token = loginAs("20260001", "13800000001");
        String eventTime = OffsetDateTime.now(ZoneId.of("Asia/Shanghai")).minusHours(2).toString();
        String payload = """
                {"type":"FOUND","title":"教学楼捡到一只灰色水壶","description":"水壶放在二教 205 讲台旁，壶身有白色贴纸，已交到值班室。",
                 "categoryId":10,"locationId":2,"detail":"205 教室讲台","eventTime":"%s","color":"灰色","contactMode":"IN_APP_ONLY"}
                """.formatted(eventTime);

        String publicId = null;
        try {
            MvcResult created = mockMvc.perform(post("/api/v1/posts")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.type").value("FOUND"))
                    .andExpect(jsonPath("$.data.locationName").value("第二教学楼"))
                    .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                    .andReturn();

            publicId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.publicId");

            mockMvc.perform(get("/api/v1/posts/mine").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                    .andExpect(jsonPath("$.data[0].publicId").value(publicId));

            mockMvc.perform(get("/api/v1/posts").param("keyword", "水壶"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.list[0].publicId").value(publicId));
        } finally {
            if (publicId != null) {
                jdbcTemplate.update("DELETE FROM item_post WHERE public_id = ?", publicId);
            }
        }
    }

    @Test
    void rejectsCreateWithoutLoginAndWithInvalidFields() throws Exception {
        mockMvc.perform(get("/api/v1/posts/mine"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));

        String token = loginAs("20260002", "13800000002");
        mockMvc.perform(post("/api/v1/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"FOUND\",\"title\":\"短\",\"description\":\"太短\",\"categoryId\":1,"
                                + "\"eventTime\":\"2026-11-01T09:00:00+08:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        mockMvc.perform(post("/api/v1/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"FOUND\",\"title\":\"正常的标题\",\"description\":\"正常的描述内容\","
                                + "\"categoryId\":9999,\"eventTime\":\"2026-11-01T09:00:00+08:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("物品分类不存在或已停用"));
    }

    private String loginAs(String studentNo, String phone) throws Exception {
        mockMvc.perform(post("/api/v1/auth/sms-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentNo\":\"" + studentNo + "\",\"phone\":\"" + phone + "\"}"))
                .andExpect(status().isOk());

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentNo\":\"" + studentNo + "\",\"phone\":\"" + phone + "\",\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(login.getResponse().getContentAsString(), "$.data.accessToken");
    }

    @Test
    void returnsMatchCandidatesAndRejectsUnknownPost() throws Exception {
        MvcResult detail = mockMvc.perform(get("/api/v1/posts/{publicId}", "LF-2026-1031-01"))
                .andExpect(status().isOk())
                .andReturn();
        String sourceType = JsonPath.read(detail.getResponse().getContentAsString(), "$.data.type");
        String opposite = "LOST".equals(sourceType) ? "FOUND" : "LOST";

        MvcResult matches = mockMvc.perform(get("/api/v1/posts/{publicId}/matches", "LF-2026-1031-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andReturn();

        // 候选只包含“相反类型且仍在寻物/招领中”的记录，因此结果可能为空。
        java.util.List<String> types = JsonPath.read(matches.getResponse().getContentAsString(), "$.data[*].type");
        org.junit.jupiter.api.Assertions.assertTrue(types.stream().allMatch(opposite::equals),
                "候选类型应全部为相反类型 " + opposite);

        mockMvc.perform(get("/api/v1/posts/{publicId}/matches", "LF-NOT-EXIST"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));
    }
}
