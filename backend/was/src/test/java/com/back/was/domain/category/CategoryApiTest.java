package com.back.was.domain.category;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CAT-01 카테고리 목록. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CategoryApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    CategoryRepository categoryRepository;

    private String accessToken;

    @BeforeEach
    void signUp() throws Exception {
        String body = mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "홍길동", "email": "cat@email.com", "password": "password123!"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        accessToken = JsonPath.read(body, "$.data.accessToken");
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
    }

    @Test
    @DisplayName("시드: ERD 4장 표의 9개가 그 순서(= ID 1~9)로 들어가 있다 — 월세는 ID 5")
    void seedMatchesErdTable() {
        var categories = categoryRepository.findAllByOrderByIdAsc();

        assertThat(categories).extracting(Category::getName)
                .containsExactly("식비", "배달", "카페", "교통", "월세", "기타", "급여", "용돈", "기타");
        assertThat(categories).extracting(Category::getId)
                .containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L);
        assertThat(categories.get(4).getName()).isEqualTo("월세");
    }

    @Test
    @DisplayName("CAT-01: type 없으면 수입·지출 전체를 ID 순으로 준다")
    void listAll() throws Exception {
        mockMvc.perform(authed(get("/categories")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(9))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].name").value("식비"))
                .andExpect(jsonPath("$.data[0].type").value("EXPENSE"))
                .andExpect(jsonPath("$.data[1].name").value("배달"))
                .andExpect(jsonPath("$.data[8].name").value("기타"))
                .andExpect(jsonPath("$.data[8].type").value("INCOME"));
    }

    @Test
    @DisplayName("CAT-01: 응답 원소의 키는 id·name·type 뿐이다 (createdAt 등 노출 금지)")
    void itemHasOnlySpecKeys() throws Exception {
        String body = mockMvc.perform(authed(get("/categories")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].createdAt").doesNotExist())
                .andExpect(jsonPath("$.data[0].updatedAt").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("createdAt").doesNotContain("updatedAt");
    }

    @Test
    @DisplayName("CAT-01: type=EXPENSE 는 지출 6개, type=INCOME 은 수입 3개")
    void filterByType() throws Exception {
        mockMvc.perform(authed(get("/categories").param("type", "EXPENSE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(6))
                .andExpect(jsonPath("$.data[*].type").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is("EXPENSE"))));

        mockMvc.perform(authed(get("/categories").param("type", "INCOME")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].name").value("급여"));
    }

    @Test
    @DisplayName("CAT-01: 명세에 없는 type 값은 400 VALIDATION_ERROR")
    void unknownTypeIsRejected() throws Exception {
        mockMvc.perform(authed(get("/categories").param("type", "SAVINGS")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("CAT-01: 토큰 없이 호출하면 401 UNAUTHORIZED (명세 1장 인증 ○)")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/categories"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }
}
