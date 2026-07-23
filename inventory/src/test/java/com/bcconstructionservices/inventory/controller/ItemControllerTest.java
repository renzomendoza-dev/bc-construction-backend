package com.bcconstructionservices.inventory.controller;

import com.bcconstructionservices.inventory.dto.ItemCreateRequest;
import com.bcconstructionservices.inventory.dto.ItemImageRequest;
import com.bcconstructionservices.inventory.dto.ItemResponse;
import com.bcconstructionservices.inventory.dto.PageResponse;
import com.bcconstructionservices.inventory.exception.DuplicateResourceException;
import com.bcconstructionservices.inventory.exception.ResourceNotFoundException;
import com.bcconstructionservices.inventory.service.FileStorageService;
import com.bcconstructionservices.inventory.service.ItemService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @WebMvcTest slice tests for ItemController.
 *
 * <p>ASSUMPTIONS (no ItemController, ItemService, or GlobalExceptionHandler
 * source was provided):
 * <ul>
 *   <li>ItemController depends on a single {@code ItemService} bean, with
 *       methods createItem(ItemCreateRequest), getItemById(Long),
 *       getItemBySku(String), listItems(String category, Boolean active,
 *       String search), deactivateItem(Long), and
 *       addItemImage(Long, ItemImageRequest) — inferred directly from the
 *       endpoint list, not confirmed against real source.</li>
 *   <li>GlobalExceptionHandler's exact JSON shape for validation errors is
 *       unknown, so field-error assertions check the HTTP status precisely
 *       (400) and only assert that the invalid field's name appears
 *       somewhere in the raw response body
 *       ({@code content().string(containsString("sku"))}) rather than
 *       pinning an exact JSONPath structure that might not match. Tighten
 *       these to exact jsonPath assertions once the real response shape is
 *       confirmed.</li>
 *   <li>ItemImageRequest.imageUrl is assumed to carry @NotBlank — this
 *       wasn't listed in this turn's validation-rules CONTEXT, but scenario
 *       11 requires it to exist for the test to be meaningful.</li>
 *   <li>listItems is assumed non-paginated (returns List&lt;ItemResponse&gt;)
 *       since no Pageable/paging parameters were mentioned for this
 *       endpoint; adjust the stub/verify calls if it actually returns a
 *       Page or PageResponse.</li>
 *   <li>No Spring Security is assumed to be configured (nothing in this
 *       codebase's prior context mentioned auth) — if it is, add
 *       {@code @AutoConfigureMockMvc(addFilters = false)} or appropriate
 *       {@code @WithMockUser} setup.</li>
 * </ul>
 */
@WebMvcTest(ItemController.class)
class ItemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private ItemService itemService;

    @MockitoBean
    private FileStorageService fileStorageService;

    // ---------------------------------------------------------------
    // Test data helpers
    // ---------------------------------------------------------------

    private ItemCreateRequest validCreateRequest() {
        ItemCreateRequest request = new ItemCreateRequest();
        request.setSku("SKU-001");
        request.setName("Portland Cement 40kg");
        request.setCategory("Cement");
        request.setUnitOfMeasure("bag");
        request.setSellingPrice(new BigDecimal("289.50"));
        request.setDefaultCostPrice(new BigDecimal("245.00"));
        return request;
    }

    private ItemResponse sampleItemResponse() {
        ItemResponse response = new ItemResponse();
        response.setId(42L);
        response.setSku("SKU-001");
        response.setName("Portland Cement 40kg");
        response.setCategory("Cement");
        response.setUnitOfMeasure("bag");
        response.setSellingPrice(new BigDecimal("289.50"));
        response.setDefaultCostPrice(new BigDecimal("245.00"));
        response.setActive(true);
        response.setImages(List.of());
        response.setCreatedAt(Instant.parse("2026-07-10T08:00:00Z"));
        response.setUpdatedAt(Instant.parse("2026-07-10T08:00:00Z"));
        return response;
    }

    // ---------------------------------------------------------------
    // POST /api/items
    // ---------------------------------------------------------------

    @Nested
    class CreateItemTests {

        @Test
        void shouldReturn201WithCreatedItemForValidRequest() throws Exception {
            when(itemService.createItem(any(ItemCreateRequest.class))).thenReturn(sampleItemResponse());

            mockMvc.perform(post("/api/items")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCreateRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(42))
                    .andExpect(jsonPath("$.sku").value("SKU-001"))
                    .andExpect(jsonPath("$.name").value("Portland Cement 40kg"))
                    .andExpect(jsonPath("$.active").value(true));
        }

        @Test
        void shouldReturn400WhenSkuIsBlank() throws Exception {
            ItemCreateRequest request = validCreateRequest();
            request.setSku("");

            mockMvc.perform(post("/api/items")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(containsString("sku")));
        }

        @Test
        void shouldReturn400WhenNameIsBlank() throws Exception {
            ItemCreateRequest request = validCreateRequest();
            request.setName("");

            mockMvc.perform(post("/api/items")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(containsString("name")));
        }

        @Test
        void shouldReturn400WhenSellingPriceIsNegative() throws Exception {
            ItemCreateRequest request = validCreateRequest();
            request.setSellingPrice(new BigDecimal("-10.00"));

            mockMvc.perform(post("/api/items")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(containsString("sellingPrice")));
        }

        @Test
        void shouldReturn409WhenServiceThrowsDuplicateResourceException() throws Exception {
            when(itemService.createItem(any(ItemCreateRequest.class)))
                    .thenThrow(new DuplicateResourceException("Item with SKU 'SKU-001' already exists"));

            mockMvc.perform(post("/api/items")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCreateRequest())))
                    .andExpect(status().isConflict());
        }
    }

    // ---------------------------------------------------------------
    // GET /api/items/{itemId}
    // ---------------------------------------------------------------

    @Nested
    class GetItemByIdTests {

        @Test
        void shouldReturn200WithItemWhenFound() throws Exception {
            when(itemService.getItemById(42L)).thenReturn(sampleItemResponse());

            mockMvc.perform(get("/api/items/{itemId}", 42L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(42))
                    .andExpect(jsonPath("$.sku").value("SKU-001"));
        }

        @Test
        void shouldReturn404WhenItemNotFound() throws Exception {
            when(itemService.getItemById(999L))
                    .thenThrow(new ResourceNotFoundException("Item not found: 999"));

            mockMvc.perform(get("/api/items/{itemId}", 999L))
                    .andExpect(status().isNotFound());
        }
    }

    // ---------------------------------------------------------------
    // GET /api/items/sku/{sku}
    // ---------------------------------------------------------------

    @Nested
    class GetItemBySkuTests {

        @Test
        void shouldReturn200WithItemWhenSkuFound() throws Exception {
            when(itemService.getItemBySku("SKU-001")).thenReturn(sampleItemResponse());

            mockMvc.perform(get("/api/items/sku/{sku}", "SKU-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sku").value("SKU-001"));
        }

        @Test
        void shouldReturn404WhenSkuNotFound() throws Exception {
            when(itemService.getItemBySku("SKU-DOES-NOT-EXIST"))
                    .thenThrow(new ResourceNotFoundException("Item not found for SKU: SKU-DOES-NOT-EXIST"));

            mockMvc.perform(get("/api/items/sku/{sku}", "SKU-DOES-NOT-EXIST"))
                    .andExpect(status().isNotFound());
        }
    }

    // ---------------------------------------------------------------
    // GET /api/items (filters)
    // ---------------------------------------------------------------

    @Nested
    class ListItemsTests {

        @Test
        void shouldCallServiceWithParsedQueryParameters() throws Exception {
            when(itemService.listItems(any(), any(), any(), any()))
                    .thenReturn(PageResponse.of(Page.empty(), item -> null));

            mockMvc.perform(get("/api/items")
                            .param("category", "Cement")
                            .param("active", "true")
                            .param("search", "portland"))
                    .andExpect(status().isOk());

            verify(itemService).listItems(eq("Cement"), eq(true), eq("portland"), any(Pageable.class));
        }
    }

    // ---------------------------------------------------------------
    // PATCH /api/items/{itemId}/deactivate
    // ---------------------------------------------------------------

    @Nested
    class DeactivateItemTests {

        @Test
        void shouldReturn204WhenDeactivatingItem() throws Exception {
            doNothing().when(itemService).deactivateItem(42L);

            mockMvc.perform(patch("/api/items/{itemId}/deactivate", 42L))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));

            verify(itemService).deactivateItem(42L);
        }
    }

    // ---------------------------------------------------------------
    // POST /api/items/{itemId}/images
    // ---------------------------------------------------------------

    @Nested
    class AddItemImageTests {

        @Test
        void shouldReturn400WhenImageUrlIsBlank() throws Exception {
            ItemImageRequest request = new ItemImageRequest();
            request.setImageUrl("");
            request.setSortOrder(0);

            mockMvc.perform(post("/api/items/{itemId}/images", 42L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(containsString("imageUrl")));
        }
    }
}