package edu.campus.lostfound.claim;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Testcontainers
class ClaimApiIntegrationTest {

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
    void welcomesClaimantThroughTheWholeHandoverFlow() throws Exception {
        String ownerToken = loginAs("20260001", "13800000001");
        String applicantToken = registerAndLogin("20261234", "13800001234");

        long imageId = uploadImage(ownerToken);
        String publicId = null;
        try {
            String eventTime = OffsetDateTime.now(ZoneId.of("Asia/Shanghai")).minusHours(1).toString();
            String payload = "{\"type\":\"FOUND\",\"title\":\"图书馆捡到一把伞\",\"description\":\"蓝色长柄伞，伞柄有磨损，已放在图书馆一楼服务台。\","
                    + "\"categoryId\":1,\"locationId\":1,\"eventTime\":\"" + eventTime + "\",\"imageIds\":[" + imageId + "]}";
            MvcResult created = mockMvc.perform(post("/api/v1/posts")
                            .header("Authorization", "Bearer " + ownerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.images.length()").value(1))
                    .andReturn();
            publicId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.publicId");

            String claim = "{\"message\":\"这把伞是我上周丢的，伞柄有磨损，可以描述一下伞骨颜色吗\"}";
            MvcResult applied = mockMvc.perform(post("/api/v1/posts/" + publicId + "/claims")
                            .header("Authorization", "Bearer " + applicantToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(claim))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("PENDING"))
                    .andExpect(jsonPath("$.data.postStatus").value("CLAIMING"))
                    .andReturn();
            String claimId = JsonPath.read(applied.getResponse().getContentAsString(), "$.data.publicId");

            mockMvc.perform(post("/api/v1/posts/" + publicId + "/claims")
                            .header("Authorization", "Bearer " + ownerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(claim))
                    .andExpect(status().isBadRequest());

            mockMvc.perform(get("/api/v1/claims/mine").header("Authorization", "Bearer " + applicantToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].publicId").value(claimId));

            mockMvc.perform(get("/api/v1/claims/received").header("Authorization", "Bearer " + ownerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].publicId").value(claimId));

            mockMvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", "Bearer " + ownerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.count").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

            mockMvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer " + ownerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].category").value("CLAIM"))
                    .andExpect(jsonPath("$.data[0].read").value(false));

            mockMvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", "Bearer " + applicantToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.count").value(0));

            mockMvc.perform(post("/api/v1/notifications/read-all").header("Authorization", "Bearer " + ownerToken))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", "Bearer " + ownerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.count").value(0));

            mockMvc.perform(post("/api/v1/claims/" + claimId + "/reject")
                            .header("Authorization", "Bearer " + ownerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"message\":\"抱歉，线索还不够，麻烦补充一下\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("REJECTED"));

            mockMvc.perform(post("/api/v1/claims/" + claimId + "/reopen")
                            .header("Authorization", "Bearer " + applicantToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"message\":\"伞骨是黑色的，伞套上写着我的名字缩写\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("PENDING"));

            mockMvc.perform(post("/api/v1/claims/" + claimId + "/confirm")
                            .header("Authorization", "Bearer " + ownerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"message\":\"线索对得上，请到图书馆服务台取\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

            mockMvc.perform(post("/api/v1/claims/" + claimId + "/complete")
                            .header("Authorization", "Bearer " + ownerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"message\":\"已交还，感谢\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("COMPLETED"));

            mockMvc.perform(get("/api/v1/posts/" + publicId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("CLAIMED"));
        } finally {
            if (publicId != null) {
                clearClaimsOf(publicId);
                jdbcTemplate.update("DELETE FROM item_image WHERE post_id = (SELECT id FROM item_post WHERE public_id = ?)", publicId);
                jdbcTemplate.update("DELETE FROM item_post WHERE public_id = ?", publicId);
            }
            jdbcTemplate.update("DELETE FROM file_object WHERE id = ?", imageId);
        }
    }

    @Test
    void rejectsClaimOnOwnPostAndWithoutEvidenceText() throws Exception {
        String ownerToken = loginAs("20260001", "13800000001");
        String publicId = createPost(ownerToken);

        try {
            mockMvc.perform(post("/api/v1/posts/" + publicId + "/claims")
                            .header("Authorization", "Bearer " + ownerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"message\":\"我自己认领自己的帖子试试\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(40001));

            String applicantToken = registerAndLogin("20261235", "13800001235");
            mockMvc.perform(post("/api/v1/posts/" + publicId + "/claims")
                            .header("Authorization", "Bearer " + applicantToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"message\":\"短\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(40001));
        } finally {
            clearClaimsOf(publicId);
            jdbcTemplate.update("DELETE FROM item_post WHERE public_id = ?", publicId);
        }
    }

    private void clearClaimsOf(String postPublicId) {
        jdbcTemplate.update("DELETE FROM claim_status_history WHERE claim_id IN"
                + " (SELECT id FROM claim WHERE post_id = (SELECT id FROM item_post WHERE public_id = ?))", postPublicId);
        jdbcTemplate.update("DELETE FROM notification WHERE ref_type = 'CLAIM' AND ref_id IN"
                + " (SELECT id FROM claim WHERE post_id = (SELECT id FROM item_post WHERE public_id = ?))", postPublicId);
        jdbcTemplate.update("DELETE FROM claim WHERE post_id = (SELECT id FROM item_post WHERE public_id = ?)", postPublicId);
    }

    private String createPost(String token) throws Exception {
        String eventTime = OffsetDateTime.now(ZoneId.of("Asia/Shanghai")).minusHours(1).toString();
        String payload = "{\"type\":\"LOST\",\"title\":\"寻找蓝色帆布包\",\"description\":\"包内有一本线性代数课本和一把钥匙，可能在二教丢失。\","
                + "\"categoryId\":1,\"locationId\":1,\"eventTime\":\"" + eventTime + "\"}";
        MvcResult created = mockMvc.perform(post("/api/v1/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(created.getResponse().getContentAsString(), "$.data.publicId");
    }

    private long uploadImage(String token) throws Exception {
        byte[] png = new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
                0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
                0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, (byte) 0xC4,
                (byte) 0x89, 0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41,
                0x54, 0x78, (byte) 0x9C, 0x63, 0x00, 0x01, 0x00, 0x00,
                0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, (byte) 0xB4, 0x00,
                0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, (byte) 0xAE,
                0x42, 0x60, (byte) 0x82
        };
        MockMultipartFile file = new MockMultipartFile(
                "file", "umbrella.png", "image/png", png);
        MvcResult uploaded = mockMvc.perform(multipart("/api/v1/files")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mime").value("image/png"))
                .andReturn();
        Number id = JsonPath.read(uploaded.getResponse().getContentAsString(), "$.data.id");
        return id.longValue();
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

    private String registerAndLogin(String studentNo, String phone) throws Exception {
        mockMvc.perform(post("/api/v1/auth/sms-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentNo\":\"" + studentNo + "\",\"phone\":\"" + phone + "\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentNo\":\"" + studentNo + "\",\"phone\":\"" + phone
                                + "\",\"code\":\"123456\"}"))
                .andExpect(status().isOk());
        return loginAs(studentNo, phone);
    }
}